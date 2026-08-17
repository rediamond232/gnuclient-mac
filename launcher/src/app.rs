use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::mpsc;
use std::sync::{Arc, Mutex};

use eframe::egui;
use egui::Context;

use crate::config::accounts::{self, Account};
use crate::config::app_config::Config;
use crate::dev::DevState;
use crate::minecraft::auth;
use crate::minecraft::instance::GameInstanceConfig;
use crate::minecraft::launch::LaunchedGame;
use crate::net::download::DownloadManager;
use crate::ui::{self, Screen};

pub struct LauncherApp {
    pub config: Config,
    pub download_manager: Arc<DownloadManager>,
    pub runtime: Arc<tokio::runtime::Runtime>,
    pub screen: Screen,
    pub accounts: Vec<Account>,
    /// Active localhost browser-login session, if one is in progress.
    pub device_login: Option<auth::LocalhostLogin>,
    /// Live game log buffer (shared with the launch thread).
    pub game_log: Arc<Mutex<Vec<String>>>,
    /// Current game status (updated from the launch thread via this handle).
    pub game_status: GameStatus,
    /// Shared handle the async launch task writes status to.
    pub status_handle: Arc<Mutex<GameStatus>>,
    /// Handle to the running game process, if any (used to stop / monitor it).
    pub game_process: Arc<Mutex<Option<LaunchedGame>>>,
    /// Pending gnuclient jar path to install into the active instance.
    pub pending_gnuclient_jar: Option<PathBuf>,
    /// Receiver for the async "select gnuclient jar" file-picker result.
    pub jar_pick_rx: Option<mpsc::Receiver<Option<PathBuf>>>,
    /// Receiver for the async "select gnuclient source dir" folder-picker result.
    pub dir_pick_rx: Option<mpsc::Receiver<Option<PathBuf>>>,
    /// Dev tab state (recompile log + status + selected built jar).
    pub dev: DevState,
    /// Search state shared across content tabs.
    pub search_query: String,
    /// Shared channels + per-tab content state.
    pub state: crate::state::AppState,
    pub active_instance_id: String,
    /// Whether a launch is in progress (async provisioning).
    pub launch_busy: bool,
    /// Toast/notice message.
    pub notice: Option<Notice>,
    /// When the current notice was shown (for auto-dismiss).
    pub notice_show_time: Option<f32>,
    /// Sender for async mod-icon loads.
    icon_tx: mpsc::Sender<IconPayload>,
    /// Receiver that carries decoded icons back to the UI thread.
    icon_rx: mpsc::Receiver<IconPayload>,
    /// Cached decoded mod icons, keyed by icon URL.
    pub icons: HashMap<String, egui::TextureHandle>,
    /// Icon URLs currently being fetched (avoid duplicate work).
    loading_icons: HashSet<String>,
}

/// A decoded mod icon, sent from a background thread to the UI thread.
pub enum IconPayload {
    Loaded {
        url: String,
        image: egui::ColorImage,
    },
    Failed {
        url: String,
    },
}

#[derive(Clone, PartialEq)]
pub enum GameStatus {
    Idle,
    Launching,
    Running,
    Exited(i32),
    Failed(String),
}

#[derive(Clone)]
pub struct Notice {
    pub text: String,
    pub kind: NoticeKind,
}

#[derive(Clone, PartialEq)]
pub enum NoticeKind {
    Info,
    Success,
    Error,
}

impl LauncherApp {
    pub fn new(
        mut config: Config,
        download_manager: Arc<DownloadManager>,
        runtime: Arc<tokio::runtime::Runtime>,
    ) -> Self {
        download_manager.set_max_concurrent(config.max_concurrent_downloads);

        // Ensure at least one instance exists.
        if config.instances.is_empty() {
            let data = config.data_dir.clone();
            let inst = GameInstanceConfig::new("GNUClient".to_string(), &data);
            config.instances.push(inst);
            config.active_instance = config.instances.first().map(|i| i.id.clone());
        }
        if config.active_instance.is_none() {
            config.active_instance = config.instances.first().map(|i| i.id.clone());
        }

        let accounts = accounts::load_accounts().unwrap_or_default();
        let active_instance_id = config
            .active_instance
            .clone()
            .unwrap_or_else(|| config.instances[0].id.clone());

        let (icon_tx, icon_rx) = mpsc::channel();

        Self {
            config,
            download_manager,
            runtime,
            screen: Screen::Dashboard,
            accounts,
            device_login: None,
            game_log: Arc::new(Mutex::new(Vec::new())),
            game_status: GameStatus::Idle,
            status_handle: Arc::new(Mutex::new(GameStatus::Idle)),
            game_process: Arc::new(Mutex::new(None)),
            pending_gnuclient_jar: None,
            jar_pick_rx: None,
            dir_pick_rx: None,
            dev: DevState::new(),
            search_query: String::new(),
            state: crate::state::AppState::new(),
            active_instance_id,
            launch_busy: false,
            notice: None,
            notice_show_time: None,
            icon_tx,
            icon_rx,
            icons: HashMap::new(),
            loading_icons: HashSet::new(),
        }
    }

    pub fn active_instance(&self) -> Option<&GameInstanceConfig> {
        self.config.instance_by_id(&self.active_instance_id)
    }

    pub fn active_instance_mut(&mut self) -> Option<&mut GameInstanceConfig> {
        self.config
            .instances
            .iter_mut()
            .find(|i| i.id == self.active_instance_id)
    }

    pub fn selected_account(&self) -> Option<&Account> {
        self.accounts
            .iter()
            .find(|a| a.is_online())
            .or(self.accounts.first())
    }

    /// Save config, preserving the active selection.
    pub fn persist(&mut self) {
        self.config.active_instance = Some(self.active_instance_id.clone());
        let _ = self.config.save();
    }

    pub fn show_notice(&mut self, text: impl Into<String>, kind: NoticeKind) {
        self.notice = Some(Notice {
            text: text.into(),
            kind,
        });
        self.notice_show_time = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs_f32())
                .unwrap_or(0.0),
        );
    }

    pub fn clear_notice(&mut self) {
        self.notice = None;
    }

    /// Stop the running game instance (if any) and reset status to idle.
    /// Used by the Play/Stop button once the game is `Running`.
    pub fn stop_game(&mut self) {
        let mut guard = self.game_process.lock().unwrap();
        if let Some(mut launched) = guard.take() {
            let _ = launched.child.kill();
            let _ = launched.child.wait();
        }
        drop(guard);
        *self.status_handle.lock().unwrap() = GameStatus::Exited(0);
        self.launch_busy = false;
    }

    /// Look up a cached icon texture for a Modrinth icon URL.
    pub fn icon_texture(&self, url: &str) -> Option<&egui::TextureHandle> {
        self.icons.get(url)
    }

    /// Kick off an async fetch + decode for an icon URL (deduped by URL).
    pub fn request_icon(&mut self, url: &str) {
        if url.is_empty() || self.icons.contains_key(url) || self.loading_icons.contains(url) {
            return;
        }
        self.loading_icons.insert(url.to_string());
        let tx = self.icon_tx.clone();
        let url = url.to_string();
        std::thread::spawn(move || {
            let payload = load_icon_payload(&url);
            let _ = tx.send(payload);
        });
    }

    /// Turn any completed icon loads into GPU textures.
    fn drain_icons(&mut self, ctx: &Context) {
        let mut msgs = Vec::new();
        while let Ok(m) = self.icon_rx.try_recv() {
            msgs.push(m);
        }
        for m in msgs {
            match m {
                IconPayload::Loaded { url, image } => {
                    let tex = ctx.load_texture(
                        format!("icon:{url}"),
                        image,
                        egui::TextureOptions::LINEAR,
                    );
                    self.icons.insert(url.clone(), tex);
                    self.loading_icons.remove(&url);
                }
                IconPayload::Failed { url } => {
                    self.loading_icons.remove(&url);
                }
            }
        }
    }

    /// Main per-frame update.
    pub fn update(&mut self, ctx: &Context) {
        self.drain_download_events();
        self.drain_channels();
        self.poll_device_login();
        self.drain_jar_pick();
        self.drain_dir_pick();
        self.drain_icons(ctx);
        // Sync status from the async launch task.
        let new_status = self.status_handle.lock().unwrap().clone();
        if new_status != self.game_status {
            match &new_status {
                GameStatus::Failed(_) => self.launch_busy = false,
                GameStatus::Running => {
                    self.launch_busy = false;
                    self.persist();
                }
                _ => {}
            }
            self.game_status = new_status;
        }
        ui::render(self, ctx);
    }

    fn drain_download_events(&mut self) {
        let events = self.download_manager.drain_events();
        for ev in events {
            match ev {
                crate::net::download::DownloadEvent::Done { url, dest, .. } => {
                    log::info!("Downloaded {url} -> {}", dest.display());
                }
                crate::net::download::DownloadEvent::Error { url, message, .. } => {
                    log::error!("Download failed {url}: {message}");
                    self.show_notice(format!("Download failed: {message}"), NoticeKind::Error);
                }
                _ => {}
            }
        }
    }

    /// Drain background channel messages (search results + install readiness).
    fn drain_channels(&mut self) {
        // Collect messages first (releasing the channel locks), then process.
        let mut search_msgs = Vec::new();
        {
            let mut rx = self.state.channels.search_rx.lock().unwrap();
            while let Ok(msg) = rx.try_recv() {
                search_msgs.push(msg);
            }
        }
        for msg in search_msgs {
            match msg {
                crate::state::SearchResult::Ok(mtype, projects) => {
                    let s = self.state.state_mut(mtype);
                    s.results = projects;
                    s.loading = false;
                    s.loaded_once = true;
                    s.error = None;
                }
                crate::state::SearchResult::Err(mtype, e) => {
                    let s = self.state.state_mut(mtype);
                    s.loading = false;
                    s.error = Some(e);
                }
            }
        }

        let mut install_msgs = Vec::new();
        {
            let mut rx = self.state.channels.install_rx.lock().unwrap();
            while let Ok(msg) = rx.try_recv() {
                install_msgs.push(msg);
            }
        }
        for msg in install_msgs {
            match msg {
                crate::state::InstallOutcome::Ready(project, version, mtype) => {
                    let data = self.config.data_dir.clone();
                    let dm = self.download_manager.clone();
                    if let Some(inst) = self.active_instance_mut() {
                        match crate::modrinth::install::install_version(
                            &dm, inst, &project, &version, &data, mtype,
                        ) {
                            Ok(_) => {
                                self.show_notice(
                                    format!("Installed {}", project.title),
                                    NoticeKind::Success,
                                );
                                self.persist();
                            }
                            Err(e) => {
                                self.show_notice(format!("Install failed: {e}"), NoticeKind::Error);
                            }
                        }
                    }
                }
                crate::state::InstallOutcome::Err(e) => {
                    self.show_notice(format!("Install failed: {e}"), NoticeKind::Error);
                }
            }
        }
    }

    /// Drain the async file-picker result and install the chosen gnuclient jar.
    fn drain_jar_pick(&mut self) {
        let picked = match &mut self.jar_pick_rx {
            Some(rx) => match rx.try_recv() {
                Ok(p) => Some(p),
                Err(std::sync::mpsc::TryRecvError::Empty) => None,
                Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                    self.jar_pick_rx = None;
                    None
                }
            },
            None => None,
        };
        if let Some(path) = picked {
            self.jar_pick_rx = None;
            let Some(path) = path else {
                self.show_notice("Jar selection cancelled", crate::app::NoticeKind::Info);
                return;
            };
            let data = self.config.data_dir.clone();
            if let Some(inst) = self.active_instance_mut() {
                match crate::modrinth::install::install_local_gnuclient(
                    std::path::Path::new(&path),
                    inst,
                    &data,
                ) {
                    Ok(_) => {
                        self.show_notice("GNUClient installed", crate::app::NoticeKind::Success);
                        self.persist();
                    }
                    Err(e) => {
                        self.show_notice(
                            format!("Install failed: {e}"),
                            crate::app::NoticeKind::Error,
                        );
                    }
                }
            }
        }
    }

    /// Drain the async folder-picker result and remember the chosen source dir.
    fn drain_dir_pick(&mut self) {
        let picked = match &mut self.dir_pick_rx {
            Some(rx) => match rx.try_recv() {
                Ok(p) => Some(p),
                Err(std::sync::mpsc::TryRecvError::Empty) => None,
                Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                    self.dir_pick_rx = None;
                    None
                }
            },
            None => None,
        };
        if let Some(path) = picked {
            self.dir_pick_rx = None;
            match path {
                Some(p) => {
                    self.config.dev_source_dir = Some(p);
                    self.show_notice("GNUClient source set", NoticeKind::Success);
                    self.persist();
                }
                None => {
                    self.show_notice("Folder selection cancelled", NoticeKind::Info);
                }
            }
        }
    }

    fn poll_device_login(&mut self) {
        if self.device_login.is_none() {
            return;
        }
        let session = self.device_login.as_ref().unwrap();
        match session.poll() {
            Ok(Some(result)) => {
                let acc = Account {
                    id: result.mc_uuid.clone(),
                    username: result.username.clone(),
                    msa_refresh_token: result.msa_refresh_token.clone(),
                    mc_uuid: result.mc_uuid.clone(),
                    mc_access_token: result.mc_access_token.clone(),
                    last_login: Some(chrono::Utc::now().to_rfc3339()),
                };
                let _ = accounts::store_secret(&acc.id, &result.msa_refresh_token);
                self.accounts.push(acc);
                let _ = accounts::save_accounts(&self.accounts);
                self.device_login = None;
                self.show_notice("Signed in successfully", NoticeKind::Success);
            }
            Ok(None) => {}
            Err(e) => {
                self.device_login = None;
                self.show_notice(format!("Login failed: {e}"), NoticeKind::Error);
            }
        }
    }
}

impl eframe::App for LauncherApp {
    fn update(&mut self, ctx: &Context, _frame: &mut eframe::Frame) {
        let theme = self.config.theme.clone();
        let pal = crate::util::theme::palette_for(&theme);
        crate::util::theme::install_visuals(ctx, pal, ctx.pixels_per_point());
        self.update(ctx);
    }
}

/// Fetch and decode a Modrinth icon on a background thread. Returns a payload
/// the UI thread turns into a GPU texture.
fn load_icon_payload(url: &str) -> IconPayload {
    let bytes = match crate::modrinth::client::fetch_icon(url) {
        Ok(b) => b,
        Err(_) => return IconPayload::Failed { url: url.to_string() },
    };
    let rgba = match image::load_from_memory(&bytes) {
        Ok(img) => img.to_rgba8(),
        Err(_) => return IconPayload::Failed { url: url.to_string() },
    };
    let (w, h) = (rgba.width() as usize, rgba.height() as usize);
    if w == 0 || h == 0 {
        return IconPayload::Failed { url: url.to_string() };
    }
    let color_image = egui::ColorImage::from_rgba_unmultiplied([w, h], &rgba.into_raw());
    IconPayload::Loaded {
        url: url.to_string(),
        image: color_image,
    }
}
