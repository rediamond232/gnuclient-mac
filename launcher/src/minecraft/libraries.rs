use std::path::{Path, PathBuf};

use anyhow::Result;
use serde::Deserialize;

use crate::net::download::DownloadRequest;

/// Base Mojang libraries URL (vanilla + launchwrapper live here).
const MOJANG_LIBS: &str = "https://libraries.minecraft.net";

/// The Mojang 1.8.9 version manifest (authoritative vanilla library list).
const MOJANG_VERSION_JSON: &str =
    "https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json";

/// A resolved library: its download URL and the local path it maps to.
#[derive(Debug, Clone)]
pub struct Library {
    pub name: String,
    pub url: String,
    pub local_path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    /// Native (`.dylib`/`.so`/`.dll`) archive. Extracted to a natives dir and
    /// put on `java.library.path`, not on the classpath.
    pub is_native: bool,
}

#[derive(Debug, Deserialize)]
pub struct MojangVersionManifest {
    #[serde(default)]
    pub libraries: Vec<ForgeLibrary>,
    #[serde(default)]
    pub downloads: Option<ManifestDownloads>,
    /// Points at the version's asset index (`1.8.json`). Mojang's JSON key is
    /// `assetIndex` (camelCase).
    #[serde(rename = "assetIndex", default)]
    pub asset_index: Option<AssetIndexInfo>,
}

#[derive(Debug, Deserialize)]
pub struct AssetIndexInfo {
    pub id: String,
    pub sha1: String,
    pub url: String,
}

#[derive(Debug, Deserialize)]
pub struct ManifestDownloads {
    /// The vanilla client jar (contains the `net.minecraft.*` classes Forge
    /// patches). Must be on the classpath or Forge's patcher fails.
    #[serde(default)]
    pub client: Option<ClientDownload>,
}

#[derive(Debug, Deserialize)]
pub struct ClientDownload {
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
}

#[derive(Debug, Deserialize)]
pub struct ForgeLibrary {
    pub name: String,
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub downloads: Option<Downloads>,
    #[serde(default)]
    pub serverreq: Option<bool>,
    /// OS allow/disallow rules (e.g. lwjgl 2.9.4 is disallowed on macOS).
    #[serde(default)]
    pub rules: Vec<LibraryRule>,
    /// OS -> natives classifier name (e.g. `osx` -> `natives-osx`).
    #[serde(default)]
    pub natives: std::collections::HashMap<String, String>,
}

#[derive(Debug, Deserialize)]
pub struct LibraryRule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
}

#[derive(Debug, Deserialize)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct Downloads {
    #[serde(default)]
    pub artifact: Option<Artifact>,
    /// OS/arch-specific native archives (e.g. `natives-osx`).
    #[serde(default)]
    pub classifiers: std::collections::HashMap<String, Artifact>,
}

#[derive(Debug, Deserialize)]
pub struct Artifact {
    pub path: String,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
}

/// The Forge universal jar itself.
pub fn forge_universal(forge_version: &str) -> Library {
    let mc = "1.8.9";
    let artifact =
        format!("net/minecraftforge/forge/{mc}-{forge_version}/forge-{mc}-{forge_version}-universal.jar");
    Library {
        name: format!("forge:{forge_version}:universal"),
        url: format!("https://maven.minecraftforge.net/releases/{artifact}"),
        local_path: PathBuf::from(&artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// Launchwrapper, required by the `net.minecraft.launchwrapper.Launch` main class.
fn launchwrapper() -> Library {
    let artifact = "net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar";
    Library {
        name: "net.minecraft:launchwrapper:1.12".to_string(),
        url: format!("{MOJANG_LIBS}/{artifact}"),
        local_path: PathBuf::from(artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// lzma: Forge's `FMLSanityChecker` (via `PatchingTransformer`) decodes its
/// sanity-check payload with `LZMA.LzmaInputStream`. The `LZMA` package is NOT
/// bundled in the Forge universal jar or any vanilla library, so it must be
/// added explicitly. Hosted on libraries.minecraft.net.
fn lzma() -> Library {
    let artifact = "lzma/lzma/0.0.1/lzma-0.0.1.jar";
    Library {
        name: "lzma:lzma:0.0.1".to_string(),
        url: format!("{MOJANG_LIBS}/{artifact}"),
        local_path: PathBuf::from(artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// vecmath: Forge 1.8.9's rendering (SplashProgress/Tessellator) uses
/// `javax.vecmath.Tuple3f`. Not in the vanilla manifest, so added explicitly.
fn vecmath() -> Library {
    let artifact = "java3d/vecmath/1.5.2/vecmath-1.5.2.jar";
    Library {
        name: "java3d:vecmath:1.5.2".to_string(),
        url: format!("{MOJANG_LIBS}/{artifact}"),
        local_path: PathBuf::from(artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// trove4j: Forge's `FMLIndexedMessageToMessageCodec` uses
/// `gnu.trove.map.hash.TByteObjectHashMap`. Not part of the vanilla Mojang
/// manifest, so it must be added explicitly.
fn trove4j() -> Library {
    let artifact = "net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar";
    Library {
        name: "net.sf.trove4j:trove4j:3.0.3".to_string(),
        url: format!("{MOJANG_LIBS}/{artifact}"),
        local_path: PathBuf::from(artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// asm-all: required by Forge 1.8.9 FML for coremod bytecode transforms
/// (`org.objectweb.asm.ClassVisitor`). Not part of the vanilla Mojang manifest,
/// so it must be added explicitly.
fn asm_all() -> Library {
    let artifact = "org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar";
    Library {
        name: "org.ow2.asm:asm-all:5.0.3".to_string(),
        url: format!("{MOJANG_LIBS}/{artifact}"),
        local_path: PathBuf::from(artifact),
        sha1: None,
        size: None,
        is_native: false,
    }
}

/// The current OS name as the Mojang manifest spells it.
pub fn os_name() -> &'static str {
    if cfg!(target_os = "macos") {
        "osx"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else {
        "linux"
    }
}

/// Evaluate a library's `rules` against the current platform. An empty rule
/// list means the library always applies. Otherwise the last matching rule's
/// action wins.
fn library_allowed(lib: &ForgeLibrary) -> bool {
    if lib.rules.is_empty() {
        return true;
    }
    let os = os_name();
    let mut allowed = false;
    for r in &lib.rules {
        let matches = r.os.as_ref().map_or(true, |o| {
            o.name.as_deref().map_or(true, |n| n == os)
        });
        if matches {
            allowed = r.action == "allow";
        }
    }
    allowed
}

/// The natives classifier key for this platform, if the library ships one
/// (e.g. `natives-osx`). Prefers the manifest's `natives` map; falls back to
/// the conventional `natives-<os>` name.
fn natives_classifier(lib: &ForgeLibrary) -> Option<String> {
    let Some(dls) = &lib.downloads else {
        return None;
    };
    let os = os_name();
    if let Some(template) = lib.natives.get(os) {
        let arch = std::env::consts::ARCH;
        let key = template.replace("${arch}", if arch.contains("64") { "64" } else { "32" });
        if dls.classifiers.contains_key(&key) {
            return Some(key);
        }
    }
    let fallback = format!("natives-{os}");
    if dls.classifiers.contains_key(&fallback) {
        Some(fallback)
    } else {
        None
    }
}

/// Fetch and parse the Mojang 1.8.9 version manifest (shared by library and
/// asset resolution).
fn fetch_manifest() -> Result<MojangVersionManifest> {
    let resp = reqwest::blocking::get(MOJANG_VERSION_JSON)?;
    if !resp.status().is_success() {
        anyhow::bail!("Mojang version manifest request failed: {}", resp.status());
    }
    Ok(resp.json()?)
}

/// Resolve all libraries needed to launch Forge 1.8.9:
/// vanilla libraries (Mojang 1.8.9 manifest) + asm-all + lzma + the Forge
/// universal + launchwrapper + the vanilla client jar. The Forge maven no
/// longer hosts a version manifest JSON for 1.8.9, so the vanilla set comes
/// from Mojang, and Forge's extra libs are added explicitly.
pub fn resolve_libraries(forge_version: &str) -> Result<Vec<Library>> {
    let manifest = fetch_manifest()?;

    let mut libs = Vec::new();
    for lib in manifest.libraries {
        // Respect per-OS allow/disallow rules (e.g. lwjgl 2.9.4 is disallowed
        // on macOS, which uses 2.9.2 instead).
        if !library_allowed(&lib) {
            continue;
        }
        if let Some(downloads) = &lib.downloads {
            if let Some(artifact) = &downloads.artifact {
                let path = PathBuf::from(&artifact.path);
                libs.push(Library {
                    name: lib.name.clone(),
                    url: artifact.url.clone(),
                    local_path: path,
                    sha1: artifact.sha1.clone(),
                    size: artifact.size,
                    is_native: false,
                });
            }
            // Provision this platform's natives classifier (e.g. lwjgl-...-natives-osx).
            if let Some(classifier) = natives_classifier(&lib) {
                if let Some(native) = downloads.classifiers.get(&classifier) {
                    libs.push(Library {
                        name: format!("{}:{classifier}", lib.name),
                        url: native.url.clone(),
                        local_path: PathBuf::from(&native.path),
                        sha1: native.sha1.clone(),
                        size: native.size,
                        is_native: true,
                    });
                }
            }
        }
    }

    // Forge extras + universal + launchwrapper.
    libs.push(asm_all());
    libs.push(lzma());
    libs.push(trove4j());
    libs.push(vecmath());
    libs.push(forge_universal(forge_version));
    libs.push(launchwrapper());

    // Vanilla client jar (net.minecraft.* classes Forge patches). Provision it
    // as a library so it's downloaded and placed on the classpath too.
    if let Some(client) = manifest.downloads.as_ref().and_then(|d| d.client.as_ref()) {
        libs.push(Library {
            name: "net.minecraft:client:1.8.9".to_string(),
            url: client.url.clone(),
            local_path: PathBuf::from("net/minecraft/client/1.8.9/client-1.8.9.jar"),
            sha1: client.sha1.clone(),
            size: client.size,
            is_native: false,
        });
    }

    Ok(libs)
}

/// Copy/refresh the bundled gnuclient jar into the instance mods dir.
pub fn install_gnuclient(gnuclient_jar: &Path, dest_dir: &Path) -> Result<()> {
    std::fs::create_dir_all(dest_dir)?;
    let dest = dest_dir.join("gnuclient.jar");
    std::fs::copy(gnuclient_jar, &dest)?;
    Ok(())
}

/// Convert a Maven coordinate to a maven-style local path and URL.
pub fn maven_to_path(maven_coord: &str) -> PathBuf {
    // e.g. "group:artifact:version" or "group:artifact:version:classifier"
    let parts: Vec<&str> = maven_coord.split(':').collect();
    if parts.len() < 3 {
        return PathBuf::from(&maven_coord.replace(':', "/"));
    }
    let group: Vec<&str> = parts[0].split('.').collect();
    let artifact = parts[1];
    let version = parts[2];
    let classifier = if parts.len() > 3 { parts[3] } else { "" };
    let mut p = PathBuf::new();
    for g in &group {
        p.push(g);
    }
    p.push(artifact);
    p.push(version);
    let fname = if classifier.is_empty() {
        format!("{artifact}-{version}.jar")
    } else {
        format!("{artifact}-{version}-{classifier}.jar")
    };
    p.push(fname);
    p
}

/// Ensure all given libraries are downloaded into a libs dir.
pub fn ensure_libraries(
    libs: &[Library],
    libs_dir: &Path,
) -> Vec<DownloadRequest> {
    let mut reqs = Vec::new();
    for lib in libs {
        let dest = libs_dir.join(&lib.local_path);
        if dest.exists() {
            continue;
        }
        reqs.push(DownloadRequest {
            url: lib.url.clone(),
            dest,
            expected_sha1: lib.sha1.clone(),
            expected_size: lib.size,
            overwrite: false,
        });
    }
    reqs
}

/// Build the classpath string for Forge launch: all non-native libs + the
/// forge universal. Natives are extracted to a natives dir instead.
pub fn classpath(libs: &[Library], libs_dir: &Path, forge_version: &str) -> String {
    let mut parts = Vec::new();
    for lib in libs {
        if lib.is_native {
            continue;
        }
        parts.push(libs_dir.join(&lib.local_path).to_string_lossy().to_string());
    }
    let universal = libs_dir.join(&forge_universal(forge_version).local_path);
    parts.push(universal.to_string_lossy().to_string());
    // `java -cp` separates entries with `;` on Windows and `:` elsewhere.
    let sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    parts.join(sep)
}

/// Extract native binaries (`.dylib`/`.so`/`.dll`) from every native library
/// into `natives_dir`, so they're loadable via `java.library.path`.
pub fn extract_natives(libs: &[Library], libs_dir: &Path, natives_dir: &Path) -> Result<()> {
    std::fs::create_dir_all(natives_dir)?;
    for lib in libs {
        if !lib.is_native {
            continue;
        }
        let jar = libs_dir.join(&lib.local_path);
        if !jar.exists() {
            continue;
        }
        let file = std::fs::File::open(&jar)?;
        let mut zip = zip::ZipArchive::new(file)?;
        for i in 0..zip.len() {
            let mut entry = zip.by_index(i)?;
            let name = entry.name().to_string();
            if name.ends_with('/') || name.starts_with("META-INF/") {
                continue;
            }
            // Only extract actual native binaries.
            if !(name.ends_with(".dylib")
                || name.ends_with(".jnilib")
                || name.ends_with(".so")
                || name.ends_with(".dll"))
            {
                continue;
            }
            let out_name = std::path::Path::new(&name)
                .file_name()
                .map(|s| s.to_os_string())
                .unwrap_or_default();
            let dest = natives_dir.join(out_name);
            let mut out = std::io::BufWriter::new(std::fs::File::create(&dest)?);
            std::io::copy(&mut entry, &mut out)?;
        }
    }
    Ok(())
}

/// Fetch the 1.8.9 asset index and return download requests for the index file
/// plus every asset object not yet on disk. Assets are shared across instances
/// under `data_dir/assets`.
pub fn resolve_asset_requests(assets_dir: &Path) -> Result<Vec<DownloadRequest>> {
    let manifest = fetch_manifest()?;
    let Some(ai) = manifest.asset_index else {
        anyhow::bail!("no asset index in version manifest");
    };
    let resp = reqwest::blocking::get(&ai.url)?;
    if !resp.status().is_success() {
        anyhow::bail!("asset index request failed: {}", resp.status());
    }
    let index: crate::minecraft::assets::AssetIndex = resp.json()?;

    let mut reqs = Vec::new();
    // The index file itself (read by the game at `assets/indexes/<id>.json`).
    let index_dest = assets_dir.join("indexes").join(format!("{}.json", ai.id));
    if !index_dest.exists() {
        reqs.push(DownloadRequest {
            url: ai.url.clone(),
            dest: index_dest,
            expected_sha1: Some(ai.sha1.clone()),
            expected_size: None,
            overwrite: false,
        });
    }
    reqs.extend(crate::minecraft::assets::ensure_requests(&index, assets_dir));
    Ok(reqs)
}
