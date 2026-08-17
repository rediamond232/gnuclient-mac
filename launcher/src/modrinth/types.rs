use serde::{Deserialize, Serialize};

use crate::minecraft::instance::ContentType;

/// The category of content as shown in the launcher UI.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ModrinthType {
    Mod,
    Shader,
    ResourcePack,
}

impl ModrinthType {
    pub fn api_string(self) -> &'static str {
        match self {
            ModrinthType::Mod => "mod",
            ModrinthType::Shader => "shader",
            ModrinthType::ResourcePack => "resourcepack",
        }
    }

    pub fn loader(self) -> &'static str {
        match self {
            ModrinthType::Mod => "forge",
            ModrinthType::Shader => "optifine",
            ModrinthType::ResourcePack => "",
        }
    }

    pub fn content_type(self) -> ContentType {
        match self {
            ModrinthType::Mod => ContentType::Mod,
            ModrinthType::Shader => ContentType::Shader,
            ModrinthType::ResourcePack => ContentType::ResourcePack,
        }
    }

    pub fn title(self) -> &'static str {
        match self {
            ModrinthType::Mod => "Mods",
            ModrinthType::Shader => "Shaders",
            ModrinthType::ResourcePack => "Resource Packs",
        }
    }
}
