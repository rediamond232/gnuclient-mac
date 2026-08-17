use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::config::app_config::default_data_dir;

pub const MC_VERSION: &str = "1.8.9";
// Note: some Forge 1.8.9 builds (e.g. 11.15.1.2318) were purged from the Forge
// maven and 404. Use a build whose artifacts are still hosted.
pub const FORGE_VERSION: &str = "11.15.1.1855";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum ContentType {
    Mod,
    Shader,
    ResourcePack,
    Optifine,
}

/// A content item installed into an instance (mod, shader, or resource pack).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstalledContent {
    pub id: String,
    pub name: String,
    pub version: String,
    pub version_id: String,
    pub file_name: String,
    pub content_type: ContentType,
    /// Modrinth project slug/id for updates.
    pub project_id: Option<String>,
    /// Track whether an update is available.
    pub update_available: bool,
    /// Modrinth project icon URL (used for the UI avatar).
    #[serde(default)]
    pub icon_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GameInstanceConfig {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    pub forge_version: String,
    pub jvm_args: String,
    pub installed_mods: Vec<InstalledContent>,
    pub installed_shaders: Vec<InstalledContent>,
    pub installed_packs: Vec<InstalledContent>,
    /// Absolute path to a gnuclient jar to bundle, if any.
    pub gnuclient_jar: Option<PathBuf>,
    /// Whether gnuclient is installed in this instance.
    pub has_gnuclient: bool,
    /// Whether OptiFine is installed (required for 1.8.9 shaders).
    pub has_optifine: bool,
    pub created: String,
    pub last_launched: Option<String>,
}

impl GameInstanceConfig {
    pub fn new(name: String, data_dir: &Path) -> Self {
        let id = slug(&name);
        Self {
            id,
            name,
            mc_version: MC_VERSION.to_string(),
            forge_version: FORGE_VERSION.to_string(),
            jvm_args: crate::config::app_config::DEFAULT_JVM_ARGS.to_string(),
            installed_mods: Vec::new(),
            installed_shaders: Vec::new(),
            installed_packs: Vec::new(),
            gnuclient_jar: None,
            has_gnuclient: false,
            has_optifine: false,
            created: now(),
            last_launched: None,
        }
        .with_default_jar(data_dir)
    }

    fn with_default_jar(mut self, data_dir: &Path) -> Self {
        for cand in [
            data_dir.join("gnuclient.jar"),
            data_dir.join("mods").join("gnuclient.jar"),
            data_dir.join("bin").join("gnuclient.jar"),
        ] {
            if cand.exists() {
                self.gnuclient_jar = Some(cand);
                self.has_gnuclient = true;
                break;
            }
        }
        self
    }

    /// The instance folder on disk.
    pub fn dir(&self, data_dir: &Path) -> PathBuf {
        data_dir.join("instances").join(&self.id)
    }

    /// The game's run directory. Forge is launched with `--gameDir` set here, so
    /// it reads mods/shaderpacks/resourcepacks from subfolders of this dir.
    pub fn run_dir(&self, data_dir: &Path) -> PathBuf {
        self.dir(data_dir).join("minecraft")
    }

    pub fn mods_dir(&self, data_dir: &Path) -> PathBuf {
        self.run_dir(data_dir).join("mods")
    }

    pub fn shaderpacks_dir(&self, data_dir: &Path) -> PathBuf {
        self.run_dir(data_dir).join("shaderpacks")
    }

    pub fn resourcepacks_dir(&self, data_dir: &Path) -> PathBuf {
        self.run_dir(data_dir).join("resourcepacks")
    }

    pub fn add_content(&mut self, item: InstalledContent) {
        let list = match item.content_type {
            ContentType::Mod => &mut self.installed_mods,
            ContentType::Shader => &mut self.installed_shaders,
            ContentType::ResourcePack => &mut self.installed_packs,
            ContentType::Optifine => &mut self.installed_mods,
        };
        list.retain(|x| x.id != item.id);
        list.push(item);
    }

    pub fn remove_content(&mut self, id: &str, content_type: ContentType) {
        let list = match content_type {
            ContentType::Mod => &mut self.installed_mods,
            ContentType::Shader => &mut self.installed_shaders,
            ContentType::ResourcePack => &mut self.installed_packs,
            ContentType::Optifine => &mut self.installed_mods,
        };
        list.retain(|x| x.id != id);
    }
}

fn slug(name: &str) -> String {
    let mut s: String = name
        .chars()
        .map(|c| {
            if c.is_alphanumeric() || c == '-' || c == '_' {
                c
            } else {
                '-'
            }
        })
        .collect();
    while s.contains("--") {
        s = s.replace("--", "-");
    }
    if s.is_empty() {
        s = "instance".to_string();
    }
    if let Some(c) = s.chars().next() {
        if c.is_ascii_digit() {
            s = format!("i{s}");
        }
    }
    s
}

fn now() -> String {
    chrono::Local::now().to_rfc3339_opts(chrono::SecondsFormat::Secs, true)
}

pub fn default_data_dir_for() -> PathBuf {
    default_data_dir()
}
