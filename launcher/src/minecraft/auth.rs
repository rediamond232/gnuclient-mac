use std::net::{TcpListener, TcpStream};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};

/// Microsoft Azure AD client used by GNUClient's alt manager (localhost redirect).
const AZURE_CLIENT_ID: &str = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
/// Alias used by refresh flows (same Azure AD app as the browser login).
const MSA_CLIENT_ID: &str = AZURE_CLIENT_ID;
const SCOPE: &str = "XboxLive.SignIn XboxLive.offline_access";

const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBOX_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

#[derive(Debug, Clone)]
pub struct MsAuthResult {
    pub msa_refresh_token: String,
    pub mc_access_token: String,
    pub mc_uuid: String,
    pub username: String,
}

/// A localhost-browser login session, matching GNUClient's alt manager.
/// A background thread owns the callback listener and stores the final result.
pub struct LocalhostLogin {
    /// The authorize URL opened in the user's browser.
    pub authorize_url: String,
    /// Shared slot for the completed (or failed) auth.
    result: Arc<Mutex<Option<Result<MsAuthResult, String>>>>,
    deadline: u64,
}

/// Start the alt-manager-style flow: bind a localhost listener, build the
/// authorize URL, and return a session to poll. The caller opens the browser.
pub fn start_localhost_login() -> Result<LocalhostLogin> {
    let listener = TcpListener::bind(("127.0.0.1", 0))?;
    let port = listener.local_addr()?.port();
    let redirect_uri = format!("http://127.0.0.1:{port}");
    let authorize_url = format!(
        "{AUTHORIZE_URL}?client_id={}&response_type=code&redirect_uri={}&scope={}\
         &prompt=select_account&response_mode=query",
        urlencoding(AZURE_CLIENT_ID),
        urlencoding(&redirect_uri),
        urlencoding(SCOPE),
    );

    let result: Arc<Mutex<Option<Result<MsAuthResult, String>>>> =
        Arc::new(Mutex::new(None));
    let slot = result.clone();
    std::thread::spawn(move || {
        let out = run_oauth_listener(listener, redirect_uri);
        if let Ok(mut g) = slot.lock() {
            *g = Some(out);
        }
    });

    Ok(LocalhostLogin {
        authorize_url,
        result,
        deadline: now_unix() + 5 * 60,
    })
}

impl LocalhostLogin {
    /// Poll for the completed auth. Call repeatedly until Ok(Some).
    pub fn poll(&self) -> Result<Option<MsAuthResult>> {
        if now_unix() > self.deadline {
            bail!("browser login timed out after 5 minutes");
        }
        if let Ok(mut g) = self.result.lock() {
            if let Some(res) = g.take() {
                return res
                    .map(Some)
                    .map_err(|e| anyhow!("login failed: {e}"));
            }
        }
        Ok(None)
    }
}

/// Blocking listener that waits for the OAuth callback, exchanges the code,
/// and completes the Minecraft auth chain.
fn run_oauth_listener(
    listener: TcpListener,
    redirect_uri: String,
) -> Result<MsAuthResult, String> {
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let deadline = now_unix() + 5 * 60;
    let code = loop {
        if now_unix() > deadline {
            return Err("Timed out waiting for Microsoft login.".to_string());
        }
        match listener.accept() {
            Ok((mut socket, _)) => {
                match handle_callback(&mut socket) {
                    Callback::Code(code) => break code,
                    Callback::Error(e) => return Err(e),
                    Callback::Ignore => continue,
                    Callback::Bad(e) => {
                        let _ = write_html(&mut socket, 400, "Bad request", &e);
                        continue;
                    }
                }
            }
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                std::thread::sleep(std::time::Duration::from_millis(200));
            }
            Err(e) => return Err(e.to_string()),
        }
    };
    exchange_auth_code(&code, &redirect_uri).map_err(|e| e.to_string())
}

enum Callback {
    Code(String),
    Error(String),
    Ignore,
    Bad(String),
}

fn handle_callback(socket: &mut TcpStream) -> Callback {
    use std::io::{BufRead, BufReader};
    socket
        .set_read_timeout(Some(std::time::Duration::from_secs(5)))
        .ok();
    // Clone for a buffered reader; the original stream stays for the response.
    let read_side = match socket.try_clone() {
        Ok(s) => s,
        Err(_) => return Callback::Bad("socket clone failed".to_string()),
    };
    let mut reader = BufReader::new(read_side);
    let mut request_line = String::new();
    if reader.read_line(&mut request_line).is_err() {
        return Callback::Bad("read failed".to_string());
    }
    // Drain headers.
    let mut line = String::new();
    loop {
        line.clear();
        if reader.read_line(&mut line).is_err() || line.trim().is_empty() {
            break;
        }
    }
    let path = request_line
        .split_whitespace()
        .nth(1)
        .unwrap_or("/")
        .to_string();
    if (path == "/" || path.starts_with("/favicon"))
        && !path.contains("code=")
        && !path.contains("error=")
    {
        let _ = write_html(socket, 204, "No Content", "");
        return Callback::Ignore;
    }
    if let Some(code) = extract_param(&path, "code") {
        let body = include_html_page();
        let _ = write_html(socket, 200, "Login complete", &body);
        return Callback::Code(code);
    }
    if let Some(err) = extract_param(&path, "error") {
        let desc = extract_param(&path, "error_description").unwrap_or_default();
        return Callback::Error(if desc.is_empty() {
            format!("Microsoft login error: {err}")
        } else {
            desc
        });
    }
    Callback::Bad("Not an OAuth callback.".to_string())
}

fn include_html_page() -> String {
    "<html><body style=\"font-family:sans-serif;background:#0c0e16;color:#f5f6fa;\
     display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">\
     <div style=\"text-align:center\"><h2>GNU Client</h2>\
     <p>Login complete. You can close this tab and return to the launcher.</p>\
     </div></body></html>"
        .to_string()
}

fn extract_param(path: &str, key: &str) -> Option<String> {
    let query = path.split('?').nth(1)?;
    for pair in query.split('&') {
        let (k, v) = pair.split_once('=').unwrap_or((pair, ""));
        if k == key {
            return Some(url_decode(v));
        }
    }
    None
}

fn url_decode(s: &str) -> String {
    s.replace('+', " ")
        .split('%')
        .enumerate()
        .map(|(i, part)| {
            if i == 0 {
                part.to_string()
            } else if part.len() >= 2 {
                let hex = &part[..2];
                u8::from_str_radix(hex, 16)
                    .map(|b| format!("{}{}", b as char, &part[2..]))
                    .unwrap_or_else(|_| format!("%{part}"))
            } else {
                format!("%{part}")
            }
        })
        .collect()
}

fn write_html(socket: &mut TcpStream, status: u16, reason: &str, body: &str) -> std::io::Result<()> {
    use std::io::Write;
    let bytes = body.as_bytes();
    let headers = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Type: text/html; charset=utf-8\r\n\
         Content-Length: {}\r\nConnection: close\r\n\r\n",
        bytes.len()
    );
    socket.write_all(headers.as_bytes())?;
    if !bytes.is_empty() {
        socket.write_all(bytes)?;
    }
    socket.flush()
}

/// Exchange an authorization code for an MSA token, then finish the chain.
fn exchange_auth_code(code: &str, redirect_uri: &str) -> Result<MsAuthResult> {
    let body = format!(
        "client_id={}&code={}&grant_type=authorization_code&redirect_uri={}&scope={}",
        urlencoding(AZURE_CLIENT_ID),
        urlencoding(code),
        urlencoding(redirect_uri),
        urlencoding(SCOPE),
    );
    let resp = post_form(TOKEN_URL, &body)?;
    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        bail!("token exchange failed ({status}): {text}");
    }
    let t: TokenResponse = resp.json().context("parsing token response")?;
    complete_auth(&t)
}

fn complete_auth(tok: &TokenResponse) -> Result<MsAuthResult> {
    let msa_refresh_token = tok.refresh_token.clone();

    let xbox_user: XboxUserResponse = post_json(
        XBOX_AUTH_URL,
        "1",
        &serde_json::json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": format!("d={}", tok.access_token),
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }),
    )?;
    let user_token = xbox_user
        .token
        .clone()
        .ok_or_else(|| anyhow!("no xbox user token"))?;
    let uhs = xbox_user
        .uhs()
        .ok_or_else(|| anyhow!("no xbox user hash"))?;

    let xsts: XstsResponse = post_json(
        XSTS_AUTH_URL,
        "1",
        &serde_json::json!({
            "Properties": {
                "SandboxId": "RETAIL",
                "UserTokens": [user_token],
            },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }),
    )?;
    let xsts_token = xsts.token.ok_or_else(|| anyhow!("no XSTS token"))?;

    let mc: McLoginResponse = post_json_plain(
        MC_LOGIN_URL,
        &serde_json::json!({
            "identityToken": format!("XBL3.0 x={uhs};{xsts_token}"),
        }),
    )?;
    let access_token = mc
        .access_token
        .ok_or_else(|| anyhow!("no minecraft access token"))?;

    let profile: ProfileResponse = get_profile(&access_token)?;

    Ok(MsAuthResult {
        msa_refresh_token,
        mc_access_token: access_token,
        mc_uuid: profile.id,
        username: profile.name,
    })
}

/// Refresh an existing MSA account purely from its refresh token.
pub fn refresh_from_refresh_token(refresh_token: &str) -> Result<MsAuthResult> {
    let body = format!(
        "grant_type=refresh_token&client_id={}&scope={}&refresh_token={}",
        MSA_CLIENT_ID,
        urlencoding(SCOPE),
        urlencoding(refresh_token)
    );
    let resp = post_form(TOKEN_URL, &body)?;
    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        bail!("refresh failed ({status}): {text}");
    }
    let t: TokenResponse = resp.json().context("parsing refresh response")?;
    complete_auth(&t)
}

fn post_form(url: &str, body: &str) -> Result<reqwest::blocking::Response> {
    let client = reqwest::blocking::Client::new();
    client
        .post(url)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body(body.to_string())
        .send()
        .map_err(|e| anyhow!("request to {url} failed: {e}"))
}

fn post_json<T: Serialize, R: for<'de> Deserialize<'de>>(
    url: &str,
    contract: &str,
    payload: &T,
) -> Result<R> {
    let client = reqwest::blocking::Client::new();
    let resp = client
        .post(url)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("x-xbl-contract-version", contract)
        .json(payload)
        .send()
        .map_err(|e| anyhow!("request to {url} failed: {e}"))?;
    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        bail!("request to {url} failed ({status}): {text}");
    }
    resp.json().context("parsing JSON response")
}

fn post_json_plain<T: Serialize, R: for<'de> Deserialize<'de>>(
    url: &str,
    payload: &T,
) -> Result<R> {
    let client = reqwest::blocking::Client::new();
    let resp = client
        .post(url)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .json(payload)
        .send()
        .map_err(|e| anyhow!("request to {url} failed: {e}"))?;
    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        bail!("request to {url} failed ({status}): {text}");
    }
    resp.json().context("parsing JSON response")
}

fn get_profile(access_token: &str) -> Result<ProfileResponse> {
    let client = reqwest::blocking::Client::new();
    let resp = client
        .get(MC_PROFILE_URL)
        .header("Authorization", format!("Bearer {access_token}"))
        .send()
        .map_err(|e| anyhow!("profile fetch failed: {e}"))?;
    if !resp.status().is_success() {
        bail!("profile fetch returned {}", resp.status());
    }
    resp.json().context("parsing profile")
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    refresh_token: String,
}

#[derive(Deserialize)]
struct XboxUserResponse {
    #[serde(rename = "Token", default)]
    token: Option<String>,
    #[serde(rename = "DisplayClaims", default)]
    display_claims: Option<DisplayClaims>,
    #[serde(default)]
    uhs: Option<String>,
}

#[derive(Deserialize)]
struct XstsResponse {
    #[serde(rename = "Token", default)]
    token: Option<String>,
}

#[derive(Deserialize)]
struct McLoginResponse {
    #[serde(default)]
    access_token: Option<String>,
}

#[derive(Deserialize)]
struct ProfileResponse {
    id: String,
    name: String,
}

#[derive(Deserialize)]
struct DisplayClaims {
    #[serde(rename = "xui", default)]
    xui: Vec<Xui>,
}

#[derive(Deserialize)]
struct Xui {
    uhs: Option<String>,
}

impl XboxUserResponse {
    fn uhs(&self) -> Option<String> {
        if let Some(u) = &self.uhs {
            return Some(u.clone());
        }
        self.display_claims
            .as_ref()
            .and_then(|d| d.xui.first())
            .and_then(|x| x.uhs.clone())
    }
}

fn urlencoding(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            b' ' => out.push('+'),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

fn now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

#[allow(dead_code)]
fn _unused(g: String) -> String {
    g
}
