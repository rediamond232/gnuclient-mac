use std::path::{Path, PathBuf};

use anyhow::Result;

use crate::minecraft::instance::{ContentType, GameInstanceConfig, InstalledContent};
use crate::modrinth::client::{self, ModrinthVersion};
use crate::modrinth::types::ModrinthType;
use crate::net::download::DownloadManager;

/// Install a Modrinth version (already resolved) into an instance.
/// Returns the InstalledContent entry and any download requests made.
pub fn install_version(
    dm: &DownloadManager,
    instance: &mut GameInstanceConfig,
    project: &client::ModrinthProject,
    version: &ModrinthVersion,
    data_dir: &Path,
    mtype: ModrinthType,
) -> Result<InstalledContent> {
    let primary = version
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| version.files.first())
        .ok_or_else(|| anyhow::anyhow!("no files for version"))?;

    let dest_dir = content_dir(instance, data_dir, mtype);
    std::fs::create_dir_all(&dest_dir)?;
    let dest = dest_dir.join(&primary.filename);

    dm.download(crate::net::download::DownloadRequest {
        url: primary.url.clone(),
        dest: dest.clone(),
        expected_sha1: primary.hashes.get("sha1").cloned(),
        expected_size: Some(primary.size),
        overwrite: false,
    });

    let content = InstalledContent {
        id: format!("{}-{}", project.id, version.id),
        name: project.title.clone(),
        version: version.version_number.clone(),
        version_id: version.id.clone(),
        file_name: primary.filename.clone(),
        content_type: mtype.content_type(),
        project_id: Some(project.id.clone()),
        update_available: false,
        icon_url: project.icon_url.clone(),
    };
    instance.add_content(content.clone());
    Ok(content)
}

/// Queue dependency downloads for a version into the instance mods dir.
pub fn install_dependencies(
    dm: &DownloadManager,
    instance: &mut GameInstanceConfig,
    version: &ModrinthVersion,
    data_dir: &Path,
    game_version: &str,
    loader: &str,
) {
    let deps = client::resolve_dependencies(version, game_version, loader, 0).unwrap_or_default();
    for dep in deps {
        let primary = dep
            .files
            .iter()
            .find(|f| f.primary)
            .or_else(|| dep.files.first());
        if let Some(f) = primary {
            let dest = instance.mods_dir(data_dir).join(&f.filename);
            dm.download(crate::net::download::DownloadRequest {
                url: f.url.clone(),
                dest,
                expected_sha1: f.hashes.get("sha1").cloned(),
                expected_size: Some(f.size),
                overwrite: false,
            });
            // Track dependency in installed_mods but flag as auto-dependency.
            let content = InstalledContent {
                id: format!("dep-{}", dep.id),
                name: format!("{} (dependency)", dep.name),
                version: dep.version_number.clone(),
                version_id: dep.id.clone(),
                file_name: f.filename.clone(),
                content_type: ContentType::Mod,
                project_id: dep.project_id.clone().into(),
                update_available: false,
                icon_url: None,
            };
            instance.add_content(content);
        }
    }
}

fn content_dir(instance: &GameInstanceConfig, data_dir: &Path, mtype: ModrinthType) -> PathBuf {
    match mtype {
        ModrinthType::Mod => instance.mods_dir(data_dir),
        ModrinthType::Shader => instance.shaderpacks_dir(data_dir),
        ModrinthType::ResourcePack => instance.resourcepacks_dir(data_dir),
    }
}

/// Queue a resource pack or shader download (no dependency resolution).
pub fn install_pack(
    dm: &DownloadManager,
    instance: &mut GameInstanceConfig,
    project: &client::ModrinthProject,
    version: &ModrinthVersion,
    data_dir: &Path,
    mtype: ModrinthType,
) -> Result<InstalledContent> {
    install_version(dm, instance, project, version, data_dir, mtype)
}

/// Copy a local gnuclient jar into the instance mods folder.
pub fn install_local_gnuclient(
    jar: &Path,
    instance: &mut GameInstanceConfig,
    data_dir: &Path,
) -> Result<()> {
    let dest = instance.mods_dir(data_dir).join("gnuclient.jar");
    std::fs::create_dir_all(&dest.parent().unwrap())?;
    std::fs::copy(jar, &dest)?;
    instance.gnuclient_jar = Some(dest.clone());
    instance.has_gnuclient = true;
    Ok(())
}

/// Remove a content item from the instance (and optionally delete the file).
pub fn remove_content(
    instance: &mut GameInstanceConfig,
    item: &InstalledContent,
    data_dir: &Path,
    delete_file: bool,
) {
    instance.remove_content(&item.id, item.content_type.clone());
    if delete_file {
        let dir = match item.content_type {
            ContentType::Mod => instance.mods_dir(data_dir),
            ContentType::Shader => instance.shaderpacks_dir(data_dir),
            ContentType::ResourcePack => instance.resourcepacks_dir(data_dir),
            ContentType::Optifine => instance.mods_dir(data_dir),
        };
        let path = dir.join(&item.file_name);
        let _ = std::fs::remove_file(path);
    }
}
