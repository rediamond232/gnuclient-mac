use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use anyhow::{Context, Result};
use crate::app::GameStatus;

use crate::config::accounts::Account;
use crate::minecraft::instance::{ContentType, GameInstanceConfig};
use crate::minecraft::libraries;

/// Result of a launched game process, so the UI can monitor it.
pub struct LaunchedGame {
    pub child: Child,
    pub java_bin: PathBuf,
    pub pid: u32,
    pub log: Arc<Mutex<Vec<String>>>,
}

#[derive(Clone)]
pub struct LaunchConfig {
    pub java_bin: PathBuf,
    pub account: Account,
    pub instance: GameInstanceConfig,
    pub data_dir: PathBuf,
    pub libs_dir: PathBuf,
    pub forge_version: String,
    /// Additional VM args already validated (the raw string from settings).
    pub jvm_args: Vec<String>,
    /// Resolved forge libraries.
    pub libraries: Vec<libraries::Library>,
    /// Window size override (None = default).
    pub window_size: Option<(u32, u32)>,
}

/// Build the full JVM command for a Forge 1.8.9 launch.
pub fn build_command(cfg: &LaunchConfig) -> Vec<String> {
    let inst_dir = cfg.instance.dir(&cfg.data_dir);
    let mc_dir = inst_dir.join("minecraft");

    let mut cmd = Vec::new();
    cmd.push(cfg.java_bin.to_string_lossy().to_string());

    // Memory + GC defaults if the user hasn't overridden them.
    cmd.extend(cfg.jvm_args.iter().cloned());

    // macOS 1.8.9: LWJGL 2.x wants -XstartOnFirstThread on Intel so the display
    // thread is the process's first thread. On Apple Silicon (aarch64 host, JRE
    // runs under Rosetta) the flag instead makes glCheckFramebufferStatus return
    // 0 and the game crashes in Framebuffer.<init>; there it must be omitted.
    #[cfg(target_os = "macos")]
    {
        if !cfg!(target_arch = "aarch64")
            && !cfg.jvm_args.iter().any(|a| a == "-XstartOnFirstThread")
        {
            cmd.push("-XstartOnFirstThread".to_string());
        }
    }

    cmd.push(format!("-Duser.dir={}", mc_dir.display()));

    // Expose extracted native libs (lwjgl, jinput, twitch) on java.library.path.
    cmd.push(format!(
        "-Djava.library.path={}",
        natives_dir(&cfg.data_dir).display()
    ));

    let classpath = libraries::classpath(&cfg.libraries, &cfg.libs_dir, &cfg.forge_version);
    cmd.push("-cp".to_string());
    cmd.push(classpath);

    // Main class for Forge.
    cmd.push("net.minecraft.launchwrapper.Launch".to_string());
    cmd.push("--version".to_string());
    cmd.push("1.8.9".to_string());
    cmd.push("--gameDir".to_string());
    cmd.push(mc_dir.to_string_lossy().to_string());
    cmd.push("--assetsDir".to_string());
    cmd.push(assets_dir(&cfg.data_dir).to_string_lossy().to_string());
    cmd.push("--assetIndex".to_string());
    cmd.push("1.8".to_string());
    cmd.push("--uuid".to_string());
    cmd.push(cfg.account.mc_uuid.clone());
    cmd.push("--accessToken".to_string());
    cmd.push(cfg.account.mc_access_token.clone());
    cmd.push("--userType".to_string());
    cmd.push("mojang".to_string());
    cmd.push("--username".to_string());
    cmd.push(cfg.account.username.clone());
    cmd.push("--tweakClass".to_string());
    cmd.push("net.minecraftforge.fml.common.launcher.FMLTweaker".to_string());

    cmd
}

/// Spawn the game process and capture stdout/stderr into the caller-provided
/// log buffer (the same one the UI reads for the Console panel).
pub fn launch_game(cfg: LaunchConfig, log: Arc<Mutex<Vec<String>>>) -> Result<LaunchedGame> {
    let cmd = build_command(&cfg);
    let mc_dir = cfg.instance.dir(&cfg.data_dir).join("minecraft");
    std::fs::create_dir_all(&mc_dir)?;

    let mut command = Command::new(&cmd[0]);
    command
        .args(&cmd[1..])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null())
        .current_dir(&mc_dir);

    let mut child = command.spawn().context("failed to spawn java")?;
    let pid = child.id();

    // Drain output in background threads into the caller's log buffer.
    relay_output(&mut child, log.clone());

    Ok(LaunchedGame {
        child,
        java_bin: cfg.java_bin.clone(),
        pid,
        log,
    })
}

/// Where game assets live (shared across instances).
pub fn assets_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("assets")
}

/// Where extracted native libs (lwjgl/jinput/twitch) live.
pub fn natives_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("bin").join("natives")
}

/// Split a raw JVM args string into tokens.
pub fn normalize_jvm_args(raw: &str) -> Vec<String> {
    raw.split_whitespace().map(|s| s.to_string()).collect()
}

/// Validate JVM args: reject anything that could break launch safety.
pub fn validate_jvm_args(raw: &str) -> Result<()> {
    for tok in raw.split_whitespace() {
        let lower = tok.to_ascii_lowercase();
        if lower == "-jar" || lower.starts_with("-jar") || lower == "-cp" || lower == "-classpath" {
            anyhow::bail!("disallowed token: {tok}");
        }
        if tok.starts_with('-') && tok.contains('@') {
            anyhow::bail!("argument files (@) are not allowed: {tok}");
        }
    }
    Ok(())
}

/// Drain child output in background threads, relaying lines to a shared buffer.
pub fn relay_output(child: &mut Child, log: Arc<Mutex<Vec<String>>>) {
    use std::io::Read;
    use std::thread;

    let mut stdout = child.stdout.take();
    let mut stderr = child.stderr.take();

    if let Some(mut so) = stdout {
        let log2 = log.clone();
        thread::spawn(move || {
            let mut buf = [0u8; 4096];
            loop {
                match so.read(&mut buf) {
                    Ok(0) | Err(_) => break,
                    Ok(n) => {
                        let s = String::from_utf8_lossy(&buf[..n]).to_string();
                        append_log(&log2, &s);
                    }
                }
            }
        });
    }
    if let Some(mut se) = stderr {
        let log3 = log.clone();
        thread::spawn(move || {
            let mut buf = [0u8; 4096];
            loop {
                match se.read(&mut buf) {
                    Ok(0) | Err(_) => break,
                    Ok(n) => {
                        let s = String::from_utf8_lossy(&buf[..n]).to_string();
                        append_log(&log3, &s);
                    }
                }
            }
        });
    }
}

fn append_log(log: &Arc<Mutex<Vec<String>>>, s: &str) {
    if let Ok(mut l) = log.lock() {
        l.push(s.trim_end().to_string());
        let overflow = l.len().saturating_sub(2000);
        if overflow > 0 {
            l.drain(0..overflow);
        }
    }
}

/// Ensure the instance's content directories exist.
pub fn ensure_instance_dirs(inst: &GameInstanceConfig, data_dir: &Path) -> Result<()> {
    for dir in [
        inst.dir(data_dir),
        inst.mods_dir(data_dir),
        inst.shaderpacks_dir(data_dir),
        inst.resourcepacks_dir(data_dir),
        inst.dir(data_dir).join("minecraft"),
    ] {
        std::fs::create_dir_all(&dir)?;
    }
    Ok(())
}

/// Whether a content item is installed on disk for the instance.
pub fn content_on_disk(
    inst: &GameInstanceConfig,
    data_dir: &Path,
    item: &crate::minecraft::instance::InstalledContent,
) -> bool {
    let dir = match item.content_type {
        ContentType::Mod => inst.mods_dir(data_dir),
        ContentType::Shader => inst.shaderpacks_dir(data_dir),
        ContentType::ResourcePack => inst.resourcepacks_dir(data_dir),
        ContentType::Optifine => inst.mods_dir(data_dir),
    };
    dir.join(&item.file_name).exists()
}

/// Kick off an asynchronous launch: ensure Java, resolve libs, then spawn.
/// Status transitions are pushed to the shared `status` handle.
pub fn begin_launch(app: &mut crate::app::LauncherApp) {
    use crate::app::GameStatus;

    if app.launch_busy {
        return;
    }
    let Some(inst) = app.active_instance() else {
        app.show_notice("No active instance", crate::app::NoticeKind::Error);
        return;
    };
    let Some(account) = app.selected_account().cloned() else {
        app.show_notice("Add an account first", crate::app::NoticeKind::Error);
        return;
    };
    let data_dir = app.config.data_dir.clone();
    let jvm_args = inst.jvm_args.clone();
    let forge_version = inst.forge_version.clone();
    let instance_snapshot = inst.clone();

    app.game_status = GameStatus::Launching;
    app.launch_busy = true;
    app.game_log.lock().unwrap().clear();

    let runtime = app.runtime.clone();
    let dm = app.download_manager.clone();
    let game_log = app.game_log.clone();
    let status = app.status_handle.clone();
    let process = app.game_process.clone();

    runtime.spawn(async move {
        let result = provision_and_launch(
            dm,
            data_dir,
            instance_snapshot,
            account,
            jvm_args,
            forge_version,
            game_log,
            status.clone(),
            process,
        )
        .await;
        if let Err(e) = result {
            *status.lock().unwrap() = GameStatus::Failed(e.to_string());
        }
    });
}

/// Polls the running game process and flips status to `Exited` once it ends,
/// so the UI can switch the Play button back to Play automatically. The process
/// handle is kept in the shared `Arc<Mutex>` so the Stop button can also take it.
fn spawn_exit_monitor(
    process: Arc<Mutex<Option<LaunchedGame>>>,
    status: Arc<Mutex<GameStatus>>,
) {
    enum Outcome {
        Running,
        Exited(i32),
        Gone,
        Err(String),
    }

    thread::spawn(move || loop {
        thread::sleep(Duration::from_millis(300));
        let outcome = {
            let mut guard = process.lock().unwrap();
            match guard.as_mut() {
                Some(mut launched) => match launched.child.try_wait() {
                    Ok(Some(code)) => Outcome::Exited(code.code().unwrap_or(0)),
                    Ok(None) => Outcome::Running,
                    Err(e) => Outcome::Err(e.to_string()),
                },
                None => Outcome::Gone,
            }
        };
        match outcome {
            Outcome::Running => continue,
            Outcome::Gone => break,
            Outcome::Exited(c) => {
                *process.lock().unwrap() = None;
                *status.lock().unwrap() = GameStatus::Exited(c);
                break;
            }
            Outcome::Err(e) => {
                *process.lock().unwrap() = None;
                *status.lock().unwrap() = GameStatus::Failed(format!("process error: {e}"));
                break;
            }
        }
    });
}

async fn provision_and_launch(
    dm: std::sync::Arc<crate::net::download::DownloadManager>,
    data_dir: std::path::PathBuf,
    instance: GameInstanceConfig,
    account: crate::config::accounts::Account,
    jvm_args: String,
    forge_version: String,
    game_log: Arc<Mutex<Vec<String>>>,
    status: Arc<Mutex<crate::app::GameStatus>>,
    process: Arc<Mutex<Option<LaunchedGame>>>,
) -> anyhow::Result<()> {
    use crate::app::GameStatus;

    // 1. Ensure Java 8.
    *status.lock().unwrap() = GameStatus::Launching;
    let java = match crate::net::java8::ensure_java8(&*dm).await {
        Ok(j) => j,
        Err(e) => {
            *status.lock().unwrap() = GameStatus::Failed(format!("Java 8 install failed: {e}"));
            return Ok(());
        }
    };

    // 2. Resolve Forge libraries (downloads them).
    let libs = match crate::minecraft::libraries::resolve_libraries(&forge_version) {
        Ok(l) => l,
        Err(e) => {
            *status.lock().unwrap() = GameStatus::Failed(format!("lib resolve failed: {e}"));
            return Ok(());
        }
    };

    // 3. Ensure instance dirs + download libs.
    ensure_instance_dirs(&instance, &data_dir)?;
    let libs_dir = data_dir.join("libraries");
    let reqs = crate::minecraft::libraries::ensure_libraries(&libs, &libs_dir);
    if !reqs.is_empty() {
        dm.download_all(reqs);
        // Wait for the queue to drain.
        loop {
            let queued = dm.state.lock().unwrap().queue;
            if queued == 0 {
                break;
            }
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        }
    }

    // 3. Extract native libraries (lwjgl/jinput/twitch) for java.library.path.
    crate::minecraft::libraries::extract_natives(&libs, &libs_dir, &natives_dir(&data_dir))?;

    // 3.5. Download game assets (shared across instances).
    {
        let assets_dir = assets_dir(&data_dir);
        let asset_reqs = crate::minecraft::libraries::resolve_asset_requests(&assets_dir)?;
        if !asset_reqs.is_empty() {
            dm.download_all(asset_reqs);
            loop {
                let queued = dm.state.lock().unwrap().queue;
                if queued == 0 {
                    break;
                }
                tokio::time::sleep(std::time::Duration::from_millis(100)).await;
            }
        }
    }

    // 4. Spawn the game, streaming its output into the launcher's Console.
    let cfg = LaunchConfig {
        java_bin: java.java_bin,
        account,
        instance,
        data_dir,
        libs_dir,
        forge_version,
        jvm_args: normalize_jvm_args(&jvm_args),
        libraries: libs,
        window_size: None,
    };
    let launched = launch_game(cfg, game_log)?;
    *process.lock().unwrap() = Some(launched);
    *status.lock().unwrap() = GameStatus::Running;
    spawn_exit_monitor(process.clone(), status.clone());
    Ok(())
}
