//! Development tab: recompile GNUClient from source and swap the jar in the
//! active instance.

use std::io::BufRead;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::SystemTime;

use eframe::egui::{self, Align2, RichText, ScrollArea, Sense, Ui, Vec2};

use crate::app::{LauncherApp, NoticeKind};
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;
use crate::util::theme;

/// Live status of the most recent recompile.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DevStatus {
    Idle,
    Running,
    Success,
    Failed,
}

/// Mutable state backing the Dev tab, shared between the UI thread and the
/// background gradle build thread via `Arc<Mutex<…>>`.
pub struct DevState {
    pub log: Arc<Mutex<Vec<String>>>,
    pub status: Arc<Mutex<DevStatus>>,
    pub built_jar: Arc<Mutex<Option<PathBuf>>>,
    /// The jar the user has chosen to install (defaults to the newest build).
    pub selected_jar: Arc<Mutex<Option<PathBuf>>>,
}

impl DevState {
    pub fn new() -> Self {
        Self {
            log: Arc::new(Mutex::new(Vec::new())),
            status: Arc::new(Mutex::new(DevStatus::Idle)),
            built_jar: Arc::new(Mutex::new(None)),
            selected_jar: Arc::new(Mutex::new(None)),
        }
    }
}

/// Walk up from the launcher executable looking for the gradle root that
/// contains the GNUClient source (`build.gradle.kts` + `src/main/java/gnu/client`).
pub fn detect_gnuclient_root() -> Option<PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let mut dir = exe.parent()?;
    for _ in 0..8 {
        let marker = dir
            .join("src")
            .join("main")
            .join("java")
            .join("gnu")
            .join("client");
        if dir.join("build.gradle.kts").exists() && marker.exists() {
            return Some(dir.to_path_buf());
        }
        dir = dir.parent()?;
    }
    None
}

/// The source directory to build from: the user-configured path if present and
/// valid, otherwise an auto-detected location.
fn resolve_source(app: &LauncherApp) -> Option<PathBuf> {
    if let Some(d) = &app.config.dev_source_dir {
        if d.exists() {
            return Some(d.clone());
        }
    }
    detect_gnuclient_root()
}

/// Run `gradlew build` in `source_dir`, streaming output to the dev log and,
/// on success, selecting the newest `gnuclient-*.jar` from `build/libs`.
pub fn recompile(source_dir: &Path, state: &DevState) {
    {
        let mut log = state.log.lock().unwrap();
        log.clear();
        log.push("Recompiling gnuclient…".to_string());
    }
    *state.status.lock().unwrap() = DevStatus::Running;
    *state.built_jar.lock().unwrap() = None;
    *state.selected_jar.lock().unwrap() = None;

    let src = source_dir.to_path_buf();
    let log = state.log.clone();
    let status = state.status.clone();
    let built_jar = state.built_jar.clone();
    let selected_jar = state.selected_jar.clone();

    thread::spawn(move || {
        let result: anyhow::Result<PathBuf> = (|| {
            let mut child = Command::new("bash")
                .arg("-c")
                .arg("bash gradlew build 2>&1")
                .current_dir(&src)
                .stdout(Stdio::piped())
                .stderr(Stdio::null())
                .spawn()
                .map_err(|e| anyhow::anyhow!("Failed to start gradle: {e}"))?;

            let mut reader = std::io::BufReader::new(
                child
                    .stdout
                    .take()
                    .ok_or_else(|| anyhow::anyhow!("No gradle stdout"))?,
            );
            let mut line = String::new();
            loop {
                line.clear();
                let n = reader.read_line(&mut line)?;
                if n == 0 {
                    break;
                }
                let trimmed = line.trim_end().to_string();
                if !trimmed.is_empty() {
                    log.lock().unwrap().push(trimmed);
                }
            }

            let code = child.wait()?;
            if !code.success() {
                anyhow::bail!("Gradle build failed (exit {code})");
            }

            // Pick the newest gnuclient-*.jar in build/libs.
            let libs = src.join("build").join("libs");
            let mut best: Option<(SystemTime, PathBuf)> = None;
            if let Ok(entries) = std::fs::read_dir(&libs) {
                for entry in entries.flatten() {
                    let p = entry.path();
                    if let Some(name) = p.file_name().and_then(|n| n.to_str()) {
                        if name.starts_with("gnuclient-") && name.ends_with(".jar") {
                            if let Ok(meta) = entry.metadata() {
                                if let Ok(t) = meta.modified() {
                                    if best.as_ref().map_or(true, |(bt, _)| t > *bt) {
                                        best = Some((t, p));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            best.map(|(_, p)| p)
                .ok_or_else(|| anyhow::anyhow!("No gnuclient jar found in build/libs"))
        })();

        match result {
            Ok(jar) => {
                *built_jar.lock().unwrap() = Some(jar.clone());
                *selected_jar.lock().unwrap() = Some(jar);
                log.lock().unwrap().push("Build complete.".to_string());
                *status.lock().unwrap() = DevStatus::Success;
            }
            Err(e) => {
                log.lock().unwrap().push(format!("Build failed: {e}"));
                *status.lock().unwrap() = DevStatus::Failed;
            }
        }
    });
}

/// Open a native folder picker (off the UI thread) to choose the source dir.
pub fn select_source_dir(app: &mut LauncherApp) {
    if app.dir_pick_rx.is_some() {
        return;
    }
    let (tx, rx) = std::sync::mpsc::channel();
    app.dir_pick_rx = Some(rx);
    thread::spawn(move || {
        let picked = rfd::FileDialog::new()
            .set_title("Select GNUClient source directory")
            .pick_folder()
            .map(|p| p.to_path_buf());
        let _ = tx.send(picked);
    });
}

/// Render the Dev tab.
pub fn show(app: &mut LauncherApp, ui: &mut Ui, inst: Option<&GameInstanceConfig>) {
    let pal = theme::palette_for(&app.config.theme);

    ui.add_space(2.0);
    ui.label(RichText::new("Dev").font(theme::display(24.0)).color(pal.text));
    ui.label(
        RichText::new("Recompile GNUClient from source and swap the jar in the active instance.")
            .font(theme::body(13.0))
            .color(pal.text_dim),
    );
    ui.add_space(16.0);

    // --- GNUClient source directory ---
    let source = resolve_source(app);
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "GNUClient source");
        let src_str = source
            .as_ref()
            .map(|p| p.display().to_string())
            .unwrap_or_else(|| "Not set — browse or auto-detect".to_string());
        ui.label(RichText::new(src_str).font(theme::mono(12.0)).color(pal.text));
        ui.add_space(8.0);
        ui.horizontal(|ui| {
            if widgets::ghost_button(ui, "Browse…", pal, 92.0) {
                select_source_dir(app);
            }
            if widgets::ghost_button(ui, "Auto-detect", pal, 104.0) {
                match detect_gnuclient_root() {
                    Some(p) => {
                        app.config.dev_source_dir = Some(p);
                        app.show_notice("GNUClient source detected", NoticeKind::Success);
                        app.persist();
                    }
                    None => app.show_notice("Could not auto-detect source", NoticeKind::Info),
                }
            }
        });
    });
    ui.add_space(14.0);


    // --- Build ---
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Build");
        let running = matches!(*app.dev.status.lock().unwrap(), DevStatus::Running);
        ui.horizontal(|ui| {
            let _guard = ui.set_enabled(source.is_some() && !running);
            let label = if running { "Compiling…" } else { "Recompile gnuclient" };
            if widgets::primary_button(ui, label, pal.accent, pal.accent2, Vec2::new(220.0, 42.0)) {
                if let Some(src) = &source {
                    recompile(src, &app.dev);
                }
            }
        });
        ui.add_space(4.0);
        let status = *app.dev.status.lock().unwrap();
        let (label, color) = match status {
            DevStatus::Idle => ("Idle", pal.text_dim),
            DevStatus::Running => ("Compiling…", pal.accent2),
            DevStatus::Success => ("Build succeeded", pal.success),
            DevStatus::Failed => ("Build failed", pal.danger),
        };
        widgets::badge(ui, "STATUS", label, color, pal);
        ui.add_space(8.0);

        let log = app.dev.log.lock().unwrap();
        ScrollArea::vertical()
            .max_height(220.0)
            .auto_shrink([false, false])
            .show(ui, |ui| {
                for line in log.iter() {
                    let low = line.to_lowercase();
                    let is_err = low.contains("error")
                        || low.contains("fail")
                        || low.contains("exception");
                    let c = if is_err { pal.danger } else { pal.text_dim };
                    ui.label(RichText::new(line).font(theme::mono(11.0)).color(c));
                }
            });
    });
    ui.add_space(14.0);


    // --- Swap jar ---
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Swap jar");

        let libs = source.as_ref().map(|s| s.join("build").join("libs"));
        let mut jars: Vec<PathBuf> = Vec::new();
        if let Some(libs) = &libs {
            if let Ok(entries) = std::fs::read_dir(libs) {
                for entry in entries.flatten() {
                    let p = entry.path();
                    if let Some(name) = p.file_name().and_then(|n| n.to_str()) {
                        if name.starts_with("gnuclient-") && name.ends_with(".jar") {
                            jars.push(p);
                        }
                    }
                }
            }
        }

        if jars.is_empty() {
            ui.label(
                RichText::new("No built jars in build/libs. Recompile first.")
                    .font(theme::body(12.0))
                    .color(pal.text_dim),
            );
        } else {
            let selected = app.dev.selected_jar.lock().unwrap().clone();
            for j in jars.iter() {
                let name = j.file_name().unwrap().to_string_lossy().to_string();
                let is_sel = selected.as_ref().map_or(false, |s| s == j);
                let (rect, resp) =
                    ui.allocate_exact_size(Vec2::new(ui.available_width(), 30.0), Sense::click());
                if ui.is_rect_visible(rect) {
                    let bg = if is_sel { pal.accent_soft } else { pal.card_hover };
                    ui.painter().rect_filled(rect, 7.0, bg);
                    ui.painter().text(
                        rect.left_center(),
                        Align2::LEFT_CENTER,
                        name,
                        theme::mono(12.0),
                        if is_sel { pal.text } else { pal.text_dim },
                    );
                }
                if resp.clicked() {
                    *app.dev.selected_jar.lock().unwrap() = Some(j.clone());
                }
            }
            ui.add_space(4.0);
        }

        ui.add_space(10.0);
        let selected = app.dev.selected_jar.lock().unwrap().clone();
        let running = matches!(*app.dev.status.lock().unwrap(), DevStatus::Running);
        let can_install = selected.is_some() && inst.is_some() && !running;
        ui.horizontal(|ui| {
            let _guard = ui.set_enabled(can_install);
            if widgets::primary_button(
                ui,
                "Replace current gnu jar",
                pal.accent2,
                pal.accent,
                Vec2::new(240.0, 42.0),
            ) {
                if let Some(jar) = selected {
                    let data = app.config.data_dir.clone();
                    if let Some(inst_mut) = app.active_instance_mut() {
                        match crate::modrinth::install::install_local_gnuclient(
                            &jar, inst_mut, &data,
                        ) {
                            Ok(_) => {
                                app.show_notice("GNUClient jar replaced", NoticeKind::Success);
                                app.persist();
                            }
                            Err(e) => app.show_notice(
                                format!("Replace failed: {e}"),
                                NoticeKind::Error,
                            ),
                        }
                    } else {
                        app.show_notice("No active instance", NoticeKind::Error);
                    }
                }
            }
        });
        if inst.is_none() {
            ui.add_space(4.0);
            ui.label(
                RichText::new("Select an instance to enable replacement.")
                    .font(theme::body(11.0))
                    .color(pal.text_dim),
            );
        }
    });
}

