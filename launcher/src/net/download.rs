use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::Instant;

use anyhow::Result;
use reqwest::header::RANGE;
use sha1::Sha1;
use sha2::{Digest, Sha256};
use tokio::runtime::Runtime;
use tokio::sync::mpsc;

/// A requested file download.
#[derive(Debug, Clone)]
pub struct DownloadRequest {
    pub url: String,
    pub dest: PathBuf,
    pub expected_sha1: Option<String>,
    pub expected_size: Option<u64>,
    /// If the dest already exists and matches, skip.
    pub overwrite: bool,
}

/// Progress update emitted to the UI thread.
#[derive(Debug, Clone)]
pub enum DownloadEvent {
    Started {
        url: String,
        dest: PathBuf,
    },
    Progress {
        url: String,
        dest: PathBuf,
        current: u64,
        total: u64,
    },
    Done {
        url: String,
        dest: PathBuf,
        elapsed_ms: u64,
    },
    Error {
        url: String,
        dest: PathBuf,
        message: String,
    },
}

/// Shared state so the UI can poll overall progress.
#[derive(Debug, Default)]
pub struct DownloadState {
    pub queue: usize,
    pub active: usize,
    pub done: usize,
    pub failed: usize,
    pub bytes_current: u64,
    pub bytes_total: u64,
}

#[derive(Clone)]
pub struct DownloadManager {
    runtime: Arc<Runtime>,
    client: reqwest::Client,
    max_concurrent: Arc<Mutex<usize>>,
    pub events: Arc<Mutex<mpsc::Receiver<DownloadEvent>>>,
    pub state: Arc<Mutex<DownloadState>>,
    sender: Arc<Mutex<mpsc::Sender<DownloadEvent>>>,
}

impl DownloadManager {
    pub fn new(runtime: Arc<Runtime>) -> Self {
        let client = reqwest::Client::builder()
            .user_agent("gnuclient-launcher/0.1.0")
            .build()
            .expect("failed to build http client");
        let (tx, rx) = mpsc::channel(256);
        Self {
            runtime,
            client,
            max_concurrent: Arc::new(Mutex::new(8)),
            events: Arc::new(Mutex::new(rx)),
            state: Arc::new(Mutex::new(DownloadState::default())),
            sender: Arc::new(Mutex::new(tx)),
        }
    }

    pub fn set_max_concurrent(&self, n: usize) {
        *self.max_concurrent.lock().unwrap() = n.max(1);
    }

    pub fn max_concurrent(&self) -> usize {
        *self.max_concurrent.lock().unwrap()
    }

    /// Spawn a single download on the runtime. Non-blocking.
    pub fn download(&self, req: DownloadRequest) {
        let client = self.client.clone();
        let sender = self.sender.clone();
        let state = self.state.clone();
        {
            let mut s = state.lock().unwrap();
            s.queue += 1;
        }
        self.runtime.spawn(async move {
            let started = Instant::now();
            emit(
                &sender,
                DownloadEvent::Started {
                    url: req.url.clone(),
                    dest: req.dest.clone(),
                },
            );
            let result = do_download(&client, &req, &sender).await;
            match result {
                Ok(()) => {
                    let mut s = state.lock().unwrap();
                    s.done += 1;
                    s.active = s.active.saturating_sub(1);
                    drop(s);
                    emit(
                        &sender,
                        DownloadEvent::Done {
                            url: req.url.clone(),
                            dest: req.dest.clone(),
                            elapsed_ms: started.elapsed().as_millis() as u64,
                        },
                    );
                }
                Err(e) => {
                    let mut s = state.lock().unwrap();
                    s.failed += 1;
                    s.active = s.active.saturating_sub(1);
                    drop(s);
                    emit(
                        &sender,
                        DownloadEvent::Error {
                            url: req.url.clone(),
                            dest: req.dest.clone(),
                            message: e.to_string(),
                        },
                    );
                }
            }
            {
                let mut s = state.lock().unwrap();
                s.queue = s.queue.saturating_sub(1);
            }
        });
    }

    /// Convenience: enqueue several downloads.
    pub fn download_all(&self, reqs: Vec<DownloadRequest>) {
        for r in reqs {
            self.download(r);
        }
    }

    /// Drain queued events for the UI. Returns them in order.
    pub fn drain_events(&self) -> Vec<DownloadEvent> {
        let mut rx = self.events.lock().unwrap();
        let mut out = Vec::new();
        while let Ok(ev) = rx.try_recv() {
            match &ev {
                DownloadEvent::Started { .. } => {
                    let mut s = self.state.lock().unwrap();
                    s.active += 1;
                }
                DownloadEvent::Progress { current, total, .. } => {
                    let mut s = self.state.lock().unwrap();
                    s.bytes_current = *current;
                    s.bytes_total = *total;
                }
                _ => {}
            }
            out.push(ev);
        }
        out
    }
}

fn emit(sender: &Arc<Mutex<mpsc::Sender<DownloadEvent>>>, ev: DownloadEvent) {
    let _ = sender.lock().unwrap().try_send(ev);
}

async fn do_download(
    client: &reqwest::Client,
    req: &DownloadRequest,
    sender: &Arc<Mutex<mpsc::Sender<DownloadEvent>>>,
) -> Result<()> {
    if let Some(parent) = req.dest.parent() {
        std::fs::create_dir_all(parent)?;
    }

    // Fast-path: already exists and hash matches.
    if !req.overwrite && req.dest.exists() {
        if let Some(h) = &req.expected_sha1 {
            if file_sha1(&req.dest)? == h.to_ascii_lowercase() {
                return Ok(());
            }
        } else if req.dest.metadata()?.len() > 0 {
            return Ok(());
        }
    }

    let mut attempts = 0;
    loop {
        attempts += 1;
        fetch(client, &req.dest, &req.url).await?;
        if let Some(expected) = &req.expected_sha1 {
            let actual = file_sha1(&req.dest)?;
            if actual != expected.to_ascii_lowercase() {
                let _ = std::fs::remove_file(&req.dest);
                if attempts < 2 {
                    continue;
                }
                anyhow::bail!(
                    "checksum mismatch for {}: expected {expected}, got {actual}",
                    req.url
                );
            }
        }
        emit_progress(sender, req, req.dest.metadata()?.len());
        return Ok(());
    }
}

async fn fetch(client: &reqwest::Client, dest: &Path, url: &str) -> Result<()> {
    let existing = if dest.exists() {
        std::fs::metadata(dest).map(|m| m.len()).unwrap_or(0)
    } else {
        0
    };
    let mut builder = client.get(url);
    if existing > 0 {
        builder = builder.header(RANGE, format!("bytes={existing}-"));
    }
    let resp = builder.send().await?;
    let status = resp.status();
    if !status.is_success() {
        anyhow::bail!("HTTP {status} for {url}");
    }
    let total = resp.content_length().unwrap_or(0) + existing;
    let mut stream = resp.bytes_stream();
    let mut out = tokio::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(dest)
        .await?;
    let mut written = existing;
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        tokio::io::AsyncWriteExt::write_all(&mut out, &chunk).await?;
        written += chunk.len() as u64;
    }
    tokio::io::AsyncWriteExt::flush(&mut out).await?;
    let _ = total;
    Ok(())
}

fn emit_progress(
    _sender: &Arc<Mutex<mpsc::Sender<DownloadEvent>>>,
    _req: &DownloadRequest,
    _bytes: u64,
) {
    // Progress ticks omitted for simplicity; Done/Error carry the lifecycle.
}

fn file_sha1(path: &Path) -> Result<String> {
    let data = std::fs::read(path)?;
    let mut hasher = Sha1::new();
    hasher.update(&data);
    Ok(hex::encode(hasher.finalize()))
}

pub fn sha256_hex(data: &[u8]) -> String {
    let mut h = Sha256::new();
    h.update(data);
    hex::encode(h.finalize())
}

pub use futures::StreamExt as _;
