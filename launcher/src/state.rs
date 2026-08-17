use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};

use crate::modrinth::client::{ModrinthProject, ModrinthSearchHit};
use crate::modrinth::types::ModrinthType;

/// Per-content-type live state held in the app.
#[derive(Default)]
pub struct ContentState {
    pub query: String,
    pub results: Vec<ModrinthProject>,
    pub loading: bool,
    pub error: Option<String>,
    pub loaded_once: bool,
}

impl From<ModrinthSearchHit> for ModrinthProject {
    fn from(h: ModrinthSearchHit) -> Self {
        Self {
            id: h.project_id,
            slug: h.slug,
            title: h.title,
            description: h.description,
            project_type: h.project_type,
            body: String::new(),
            icon_url: h.icon_url,
            downloads: h.downloads,
            follows: 0,
            versions: h.versions,
            game_versions: Vec::new(),
            loaders: Vec::new(),
            categories: h.categories,
            source_url: None,
        }
    }
}

/// A completed Modrinth search, sent from the background task.
pub enum SearchResult {
    Ok(ModrinthType, Vec<ModrinthProject>),
    Err(ModrinthType, String),
}

/// A completed install, sent from the background task.
pub enum InstallOutcome {
    /// Resolved version ready to install; the UI thread applies it (needs &mut instance).
    Ready(
        ModrinthProject,
        crate::modrinth::client::ModrinthVersion,
        ModrinthType,
    ),
    Err(String),
}

/// Channels bridging background async work and the UI thread.
pub struct UiChannels {
    pub search_tx: UnboundedSender<SearchResult>,
    pub search_rx: Mutex<UnboundedReceiver<SearchResult>>,
    pub install_tx: UnboundedSender<InstallOutcome>,
    pub install_rx: Mutex<UnboundedReceiver<InstallOutcome>>,
}

impl UiChannels {
    pub fn new() -> Self {
        let (search_tx, search_rx) = unbounded_channel();
        let (install_tx, install_rx) = unbounded_channel();
        Self {
            search_tx,
            search_rx: Mutex::new(search_rx),
            install_tx,
            install_rx: Mutex::new(install_rx),
        }
    }
}

/// Fields shared between the app and background tasks, grouped for clarity.
pub struct AppState {
    pub channels: Arc<UiChannels>,
    pub content_states: HashMap<String, ContentState>,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            channels: Arc::new(UiChannels::new()),
            content_states: HashMap::new(),
        }
    }

    pub fn state_mut(&mut self, mtype: ModrinthType) -> &mut ContentState {
        self.content_states
            .entry(mtype.api_string().to_string())
            .or_default()
    }

    pub fn state(&self, mtype: ModrinthType) -> Option<&ContentState> {
        self.content_states.get(mtype.api_string())
    }
}

impl Default for AppState {
    fn default() -> Self {
        Self::new()
    }
}
