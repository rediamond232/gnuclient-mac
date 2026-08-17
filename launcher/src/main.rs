#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app;
mod config;
mod minecraft;
mod modrinth;
mod net;
mod state;
mod ui;
mod util;

use std::sync::Arc;

use anyhow::Result;
use config::app_config::Config;
use net::download::DownloadManager;
use tokio::runtime::Runtime;

fn main() -> Result<()> {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();

    let options = eframe::NativeOptions {
        viewport: eframe::egui::ViewportBuilder::default()
            .with_inner_size([1280.0, 800.0])
            .with_min_inner_size([960.0, 640.0])
            .with_title("GNUClient Launcher"),
        ..Default::default()
    };

    let runtime = Arc::new(Runtime::new()?);
    let config = Config::load()?;
    let download_manager = Arc::new(DownloadManager::new(runtime.clone()));

    let app = app::LauncherApp::new(config, download_manager, runtime);
    eframe::run_native(
        "GNUClient Launcher",
        options,
        Box::new(move |_cc| Ok(Box::new(app))),
    )
    .map_err(|e| anyhow::anyhow!("Failed to run GUI: {e}"))
}
