use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::Deserialize;
use sha2::{Digest, Sha256};

use super::download::DownloadManager;
use crate::config::app_config::default_data_dir;

pub const JAVA8_MAJOR: u32 = 8;

/// Point us at a known-good Temurin 8 JRE (Adoptium) for the host arch.
/// Zulu's JRE uses Azul's "caulk" allocator, which SIGILL-crashes under Rosetta
/// on Apple Silicon during OpenAL init (nalSourcei); Temurin uses the stock
/// allocator and is stable. We use the Adoptium API to resolve the current
/// latest 8u JRE for the host arch.
const ADOPTIUM_API: &str = "https://api.adoptium.net/v3/assets/latest/8/hotspot";

#[derive(Debug, Deserialize)]
struct AdoptiumAsset {
    binary: AdoptiumBinary,
}

#[derive(Debug, Deserialize)]
struct AdoptiumBinary {
    #[serde(rename = "package")]
    package: AdoptiumPackage,
}

#[derive(Debug, Deserialize)]
struct AdoptiumPackage {
    link: String,
}

#[derive(Debug, Clone)]
pub struct JavaInstall {
    /// Path to the resolved `bin/java` executable.
    pub java_bin: PathBuf,
    pub version: String,
    pub home: PathBuf,
}

impl JavaInstall {
    pub fn exists(&self) -> bool {
        self.java_bin.exists()
    }
}

/// The `java` launcher executable name for the current OS.
fn java_exe_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "java.exe"
    } else {
        "java"
    }
}

/// Given an entry under `runtime/`, return the JRE home dir holding
/// `bin/java[.exe]` and `lib/rt.jar`, if that entry is a complete install.
/// macOS bundles unpack to `<jre>/Contents/Home`; Linux/Windows unpack the
/// JRE directly to `<jre>/`.
fn jre_home_from(entry: &Path) -> Option<PathBuf> {
    let candidates: Vec<PathBuf> = if cfg!(target_os = "macos") {
        vec![entry.join("Contents").join("Home")]
    } else {
        vec![entry.to_path_buf()]
    };
    for home in candidates {
        let bin = home.join("bin").join(java_exe_name());
        if !bin.exists() {
            continue;
        }
        // rt.jar appears late in the archive; a truncated extraction that
        // stopped mid-`lib/` won't have it. Treat as incomplete.
        let rt = home.join("lib").join("rt.jar");
        if rt.exists() {
            return Some(home);
        }
    }
    None
}

/// Locate an already-installed bundled Java 8 in the launcher runtime dir.
/// Returns the JRE home (the dir holding `bin/java[.exe]`).
/// Rejects partial/incomplete extractions (e.g. a run interrupted mid-unpack),
/// which would otherwise be picked up as a usable runtime.
pub fn bundled_java_home() -> Option<PathBuf> {
    let runtime_dir = default_data_dir().join("runtime");
    if let Ok(entries) = std::fs::read_dir(&runtime_dir) {
        for entry in entries.flatten() {
            let p = entry.path();
            if !p.is_dir() {
                continue;
            }
            // 1.8.9 ships x86_64-only LWJGL natives, so whenever we're not
            // targeting aarch64 (macOS, Windows, or a non-aarch64 host) skip
            // any aarch64 bundle that happens to be present.
            if java8_arch() != "aarch64" {
                let name = p
                    .file_name()
                    .map(|s| s.to_string_lossy().to_lowercase())
                    .unwrap_or_default();
                if name.contains("aarch64") || name.contains("arm64") {
                    continue;
                }
            }
            if let Some(home) = jre_home_from(&p) {
                return Some(home);
            }
        }
    }
    None
}

/// Path where Java 8 will be installed.
pub fn java8_install_dir() -> PathBuf {
    default_data_dir().join("runtime")
}

/// Build a resolved JavaInstall from an existing home.
pub fn resolve_from_home(home: PathBuf) -> JavaInstall {
    let java_bin = home.join("bin").join(java_exe_name());
    JavaInstall {
        java_bin,
        version: "8".to_string(),
        home,
    }
}

/// Ensure a usable Java 8 runtime is present, installing it if needed.
/// Returns the resolved java executable path.
pub async fn ensure_java8(dm: &DownloadManager) -> Result<JavaInstall> {
    if let Some(home) = bundled_java_home() {
        return Ok(resolve_from_home(home));
    }

    let pkg = resolve_jre_package().await?;
    let url = pkg.binary.package.link;
    let name = url
        .rsplit('/')
        .next()
        .unwrap_or("temurin8.tar.gz")
        .to_string();
    let dest = java8_install_dir().join(&name);
    let runtime_dir = java8_install_dir();

    std::fs::create_dir_all(&runtime_dir)?;
    if !dest.exists() {
        dm.download(crate::net::download::DownloadRequest {
            url: url.clone(),
            dest: dest.clone(),
            expected_sha1: None,
            expected_size: None,
            overwrite: true,
        });
        // Wait for the download to actually finish (size stops growing), not
        // just for the file to appear with a few bytes.
        wait_for_file(&dest).await?;
    }

    // Extract into a temp dir so a partial extraction is never mistaken for a
    // complete install by bundled_java_home(). Only move it into place once
    // the whole archive has been unpacked.
    let tmp_dir = runtime_dir.join(format!(".tmp-{}", std::process::id()));
    if tmp_dir.exists() {
        let _ = std::fs::remove_dir_all(&tmp_dir);
    }
    std::fs::create_dir_all(&tmp_dir)?;
    extract_archive(&dest, &tmp_dir)?;
    let _ = std::fs::remove_file(&dest);

    // Move each extracted top-level entry into the runtime dir.
    for entry in std::fs::read_dir(&tmp_dir)? {
        let entry = entry?;
        let target = runtime_dir.join(entry.file_name());
        if target.exists() {
            let _ = std::fs::remove_dir_all(&target);
        }
        std::fs::rename(entry.path(), &target)?;
    }
    let _ = std::fs::remove_dir_all(&tmp_dir);

    let home = bundled_java_home().ok_or_else(|| {
        anyhow::anyhow!("Java 8 installed but no runtime found in {:?}", runtime_dir)
    })?;
    Ok(resolve_from_home(home))
}

/// The Java 8 arch to fetch. 1.8.9 ships x86_64-only LWJGL natives, so macOS
/// and Windows must run an x86_64 JRE (under Rosetta on Apple Silicon on macOS).
/// Linux uses the host arch.
fn java8_arch() -> &'static str {
    // 1.8.9 ships x86_64-only LWJGL natives. On macOS that means running the
    // x86_64 JRE under Rosetta on Apple Silicon; Windows targets x86_64.
    // Linux uses the host arch.
    if cfg!(target_os = "macos") || cfg!(target_os = "windows") {
        "x86_64"
    } else if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else {
        "x86_64"
    }
}

async fn resolve_jre_package() -> Result<AdoptiumAsset> {
    // Adoptium's arch names differ from ours (x86_64 -> x64).
    let arch = match java8_arch() {
        "x86_64" => "x64",
        a => a,
    };
    let os = if cfg!(target_os = "macos") {
        "mac"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else {
        "linux"
    };
    // /v3/assets/latest/{feature}/{jvm_impl}?os=&architecture=&image_type=&vendor=
    let query = format!(
        "{ADOPTIUM_API}?architecture={arch}&image_type=jre&os={os}&vendor=eclipse"
    );
    let resp = reqwest::get(&query)
        .await
        .context("querying Adoptium for Java 8 runtime")?;
    if !resp.status().is_success() {
        bail!("Adoptium API returned {}", resp.status());
    }
    let pkgs: Vec<AdoptiumAsset> = resp.json().await?;
    pkgs.into_iter()
        .next()
        .ok_or_else(|| anyhow::anyhow!("No Java 8 JRE available for {os}/{arch}"))
}

/// Wait for the download to finish by polling until the file stops growing.
/// Waiting for `len > 0` alone is insufficient: the download is async, so the
/// file can be partially written when it first appears. Extracting too early
/// reads a truncated gzip stream and fails partway through a member.
async fn wait_for_file(path: &Path) -> Result<()> {
    let mut last_len = 0u64;
    let mut stable_rounds = 0u32;
    for _ in 0..600 {
        let len = path
            .metadata()
            .map(|m| m.len())
            .unwrap_or(0);
        if len > 0 {
            if len == last_len {
                stable_rounds += 1;
                if stable_rounds >= 4 {
                    // 2s of no growth => transfer finished.
                    return Ok(());
                }
            } else {
                stable_rounds = 0;
            }
            last_len = len;
        }
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
    }
    bail!("Timed out waiting for {}", path.display())
}

fn extract_tar_gz(archive: &Path, dest: &Path) -> Result<()> {
    let f = std::fs::File::open(archive)?;
    let buf = std::io::BufReader::new(f);
    let gz = flate2::read::GzDecoder::new(buf);
    let mut tar = tar::Archive::new(gz);
    tar.unpack(dest)?;
    Ok(())
}

fn extract_zip(archive: &Path, dest: &Path) -> Result<()> {
    let file = std::fs::File::open(archive)?;
    let mut zip = zip::ZipArchive::new(file)?;
    zip.extract(dest)?;
    Ok(())
}

/// Unpack a downloaded JRE archive. Adoptium ships Linux/macOS as `.tar.gz`
/// and Windows as `.zip`.
fn extract_archive(archive: &Path, dest: &Path) -> Result<()> {
    let ext = archive
        .extension()
        .map(|s| s.to_string_lossy().to_ascii_lowercase())
        .unwrap_or_default();
    match ext.as_str() {
        "zip" => extract_zip(archive, dest),
        _ => extract_tar_gz(archive, dest),
    }
}

/// Verify a downloaded file's sha256.
pub fn verify_sha256(path: &Path, expected: &str) -> Result<bool> {
    let data = std::fs::read(path)?;
    let mut h = Sha256::new();
    h.update(&data);
    Ok(hex::encode(h.finalize()).eq_ignore_ascii_case(expected))
}
