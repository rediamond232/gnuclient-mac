use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::net::download::DownloadRequest;

/// Base URL for Minecraft asset objects (`resources.download.minecraft.net`).
const ASSET_RESOURCES: &str = "https://resources.download.minecraft.net";

/// The asset index JSON (`1.8.json`): maps virtual asset paths to hashed objects.
#[derive(Debug, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    #[serde(default)]
    pub size: u64,
}

/// Build a download request for every asset object not yet present on disk.
/// Objects are stored at `assets/objects/<first-two-of-hash>/<hash>`.
pub fn ensure_requests(index: &AssetIndex, assets_dir: &Path) -> Vec<DownloadRequest> {
    let objects_dir = assets_dir.join("objects");
    let mut reqs = Vec::new();
    for obj in index.objects.values() {
        let prefix = &obj.hash[..2];
        let dest = objects_dir.join(prefix).join(&obj.hash);
        if dest.exists() {
            continue;
        }
        reqs.push(DownloadRequest {
            url: format!("{ASSET_RESOURCES}/{prefix}/{hash}", hash = obj.hash),
            dest,
            expected_sha1: Some(obj.hash.clone()),
            expected_size: Some(obj.size),
            overwrite: false,
        });
    }
    reqs
}

/// Convenience for the object directory (used by diagnostics/tests).
pub fn objects_dir(assets_dir: &Path) -> PathBuf {
    assets_dir.join("objects")
}