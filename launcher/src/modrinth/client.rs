use std::collections::HashMap;

use anyhow::{bail, Result};
use serde::Deserialize;

/// Facets / filter helpers for Modrinth API v2.

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthProject {
    pub id: String,
    pub slug: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub project_type: String,
    #[serde(default)]
    pub body: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub downloads: u64,
    #[serde(default)]
    pub follows: u64,
    #[serde(default)]
    pub versions: Vec<String>,
    #[serde(default)]
    pub game_versions: Vec<String>,
    #[serde(default)]
    pub loaders: Vec<String>,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub source_url: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthVersion {
    pub id: String,
    #[serde(default)]
    pub project_id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub version_number: String,
    #[serde(default)]
    pub game_versions: Vec<String>,
    #[serde(default)]
    pub loaders: Vec<String>,
    #[serde(default)]
    pub files: Vec<ModrinthFile>,
    #[serde(default)]
    pub dependencies: Vec<ModrinthDependency>,
    #[serde(default)]
    pub date_published: String,
    #[serde(default)]
    pub downloads: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthFile {
    pub url: String,
    #[serde(default)]
    pub filename: String,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub primary: bool,
    #[serde(default)]
    pub hashes: HashMap<String, String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthDependency {
    #[serde(default)]
    pub version_id: Option<String>,
    #[serde(default)]
    pub project_id: Option<String>,
    #[serde(default)]
    pub dependency_type: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthSearch {
    pub hits: Vec<ModrinthSearchHit>,
    #[serde(default)]
    pub total_hits: u64,
    pub offset: u64,
    pub limit: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthSearchHit {
    pub project_id: String,
    pub slug: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub project_type: String,
    #[serde(default)]
    pub downloads: u64,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub versions: Vec<String>,
}

const BASE: &str = "https://api.modrinth.com/v2";

/// Search Modrinth for a project of a given type and filter.
pub fn search(
    query: &str,
    project_type: &str,
    game_version: &str,
    loader: &str,
    limit: u32,
) -> Result<ModrinthSearch> {
    let mut facets: Vec<String> = Vec::new();
    facets.push(format!(
        "[[\"project_type:{project_type}\"],[\"versions:{game_version}\"],[\"categories:{loader}\"]]"
    ));

    let mut url = format!(
        "{BASE}/search?query={}&facets={}&limit={}",
        urlencode(query),
        urlencode(&facets[0]),
        limit
    );
    url.push_str("&index=relevance");

    let resp = reqwest::blocking::get(&url)?;
    if !resp.status().is_success() {
        bail!("Modrinth search failed: {}", resp.status());
    }
    let s: ModrinthSearch = resp.json()?;
    Ok(s)
}

/// Get a project by id or slug.
pub fn get_project(project_id: &str) -> Result<ModrinthProject> {
    let url = format!("{BASE}/project/{project_id}");
    let resp = reqwest::blocking::get(&url)?;
    if !resp.status().is_success() {
        bail!("Modrinth get project failed: {}", resp.status());
    }
    let p: ModrinthProject = resp.json()?;
    Ok(p)
}

/// List all versions of a project.
pub fn list_versions(project_id: &str) -> Result<Vec<ModrinthVersion>> {
    let url = format!("{BASE}/project/{project_id}/version");
    let resp = reqwest::blocking::get(&url)?;
    if !resp.status().is_success() {
        bail!("Modrinth list versions failed: {}", resp.status());
    }
    let v: Vec<ModrinthVersion> = resp.json()?;
    Ok(v)
}

/// Find the best version for our game version + loader + type.
pub fn best_version(
    project_id: &str,
    game_version: &str,
    loader: &str,
    project_type: &str,
) -> Result<ModrinthVersion> {
    let versions = list_versions(project_id)?;
    let compatible: Vec<&ModrinthVersion> = versions
        .iter()
        .filter(|v| {
            v.game_versions.iter().any(|g| g == game_version)
                && (loader.is_empty() || v.loaders.iter().any(|l| l == loader))
        })
        .collect();
    let candidates: Vec<&ModrinthVersion> = if project_type == "mod" {
        compatible
            .iter()
            .filter(|v| v.loaders.iter().any(|l| l == "forge"))
            .copied()
            .collect()
    } else {
        compatible.clone()
    };
    let pool = if candidates.is_empty() {
        compatible
    } else {
        candidates
    };
    let Some(best) = pool
        .into_iter()
        .max_by_key(|v| (v.downloads, v.game_versions.len(), v.date_published.clone()))
    else {
        bail!("no compatible version of {project_id} for {game_version}/{loader}");
    };
    Ok(best.clone())
}

/// Resolve dependencies for a given version (follows project/version ids).
pub fn resolve_dependencies(
    version: &ModrinthVersion,
    game_version: &str,
    loader: &str,
    depth: u32,
) -> Result<Vec<ModrinthVersion>> {
    if depth > 4 {
        return Ok(Vec::new());
    }
    let mut out = Vec::new();
    for dep in &version.dependencies {
        if dep.dependency_type != "required" {
            continue;
        }
        let resolved = if let Some(vid) = &dep.version_id {
            get_version(vid).ok()
        } else if let Some(pid) = &dep.project_id {
            best_version(pid, game_version, loader, "mod").ok()
        } else {
            None
        };
        if let Some(v) = resolved {
            if !out.iter().any(|x: &ModrinthVersion| x.id == v.id) {
                out.push(v.clone());
                let transitive = resolve_dependencies(&v, game_version, loader, depth + 1)?;
                out.extend(transitive);
            }
        }
    }
    Ok(out)
}

pub fn get_version(version_id: &str) -> Result<ModrinthVersion> {
    let url = format!("{BASE}/version/{version_id}");
    let resp = reqwest::blocking::get(&url)?;
    if !resp.status().is_success() {
        bail!("Modrinth get version failed: {}", resp.status());
    }
    let v: ModrinthVersion = resp.json()?;
    Ok(v)
}

fn urlencode(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            b' ' => out.push_str("%20"),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// Fetch project icon (returns raw bytes if available).
pub fn fetch_icon(icon_url: &str) -> Result<Vec<u8>> {
    let resp = reqwest::blocking::get(icon_url)?;
    if !resp.status().is_success() {
        bail!("icon fetch failed: {}", resp.status());
    }
    Ok(resp.bytes()?.to_vec())
}
