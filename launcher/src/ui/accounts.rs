use eframe::egui::{self, Layout, Ui};

use crate::app::{LauncherApp, NoticeKind};
use crate::minecraft::auth;
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;
use crate::util::theme;

pub fn show(app: &mut LauncherApp, ui: &mut Ui, _inst: Option<&GameInstanceConfig>) {
    let pal = theme::palette_for(&app.config.theme);

    ui.add_space(2.0);
    ui.label(
        egui::RichText::new("Accounts")
            .font(theme::display(24.0))
            .color(pal.text),
    );
    ui.label(
        egui::RichText::new("Sign in with Microsoft to play online.")
            .font(theme::body(13.0))
            .color(pal.text_dim),
    );
    ui.add_space(16.0);

    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Microsoft Account");
        ui.add_space(6.0);

        if app.accounts.is_empty() {
            ui.label(
                egui::RichText::new(
                    "Sign in with your Microsoft account to play. We use the \
                     official device-code flow — no password is stored here.",
                )
                .font(theme::body(13.0))
                .color(pal.text_dim),
            );
            ui.add_space(10.0);
        }

        if app.device_login.is_some() {
            // Active login in progress.
            let url = {
                let s = app.device_login.as_ref().unwrap();
                s.authorize_url.clone()
            };
            ui.label(
                egui::RichText::new(
                    "Sign in with Microsoft, then allow Xbox access. The \
                     redirect lands back in the launcher automatically.",
                )
                .font(theme::body(13.0))
                .color(pal.text_dim),
            );
            ui.add_space(4.0);
            ui.label(
                egui::RichText::new(&url)
                    .font(theme::mono(12.0))
                    .color(pal.accent2)
                    .underline(),
            );
            ui.add_space(8.0);
            let mut cancelled = false;
            ui.horizontal(|ui| {
                if widgets::primary_button(
                    ui,
                    "Open Browser",
                    pal.accent2,
                    pal.accent2,
                    egui::vec2(150.0, 40.0),
                ) {
                    open_url(&url);
                }
                ui.add_space(10.0);
                if widgets::ghost_button(ui, "Cancel", pal, 110.0) {
                    cancelled = true;
                }
            });
            if cancelled {
                app.device_login = None;
            }
            ui.add_space(4.0);
            ui.label(
                egui::RichText::new("Waiting for you to complete sign-in in your browser…")
                    .font(theme::body(12.0))
                    .color(pal.warn),
            );
        } else {
            if widgets::primary_button(
                ui,
                "Sign in with Microsoft",
                pal.accent,
                pal.accent_soft,
                egui::vec2(240.0, 44.0),
            ) {
                match auth::start_localhost_login() {
                    Ok(session) => {
                        let url = session.authorize_url.clone();
                        open_url(&url);
                        app.device_login = Some(session);
                        app.show_notice(
                            "Opened Microsoft sign-in in your browser",
                            NoticeKind::Success,
                        );
                    }
                    Err(e) => {
                        app.show_notice(format!("Login init failed: {e}"), NoticeKind::Error);
                    }
                }
            }
        }

        if app.device_login.is_none() && app.accounts.is_empty() {
            ui.add_space(8.0);
            ui.label(
                egui::RichText::new(
                    "The launcher will open a Microsoft sign-in link. Complete it \
                     in your browser and return here.",
                )
                .font(theme::body(12.0))
                .color(pal.text_dim),
            );
        }
    });

    ui.add_space(14.0);

    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Saved Accounts");
        if app.accounts.is_empty() {
            ui.label(
                egui::RichText::new("No saved accounts yet.")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
        } else {
            let mut to_remove: Option<usize> = None;
            for (i, acc) in app.accounts.iter().enumerate() {
                ui.horizontal(|ui| {
                    let dot = if acc.is_online() {
                        pal.success
                    } else {
                        pal.warn
                    };
                    ui.painter().circle_filled(
                        ui.cursor().min + egui::vec2(5.0, 12.0),
                        4.0,
                        dot,
                    );
                    ui.add_space(16.0);
                    ui.vertical(|ui| {
                        ui.label(
                            egui::RichText::new(&acc.username)
                                .font(theme::body_sb(15.0))
                                .color(pal.text),
                        );
                        ui.label(
                            egui::RichText::new(format!(
                                "{}  ·  UUID {}",
                                if acc.is_online() { "Online" } else { "Offline" },
                                &acc.mc_uuid[..acc.mc_uuid.len().min(8)]
                            ))
                            .font(theme::mono(11.0))
                            .color(pal.text_dim),
                        );
                    });
                    ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                        if widgets::ghost_button(ui, "Remove", pal, 96.0) {
                            to_remove = Some(i);
                        }
                    });
                });
                ui.add_space(6.0);
            }
            if let Some(idx) = to_remove {
                app.accounts.remove(idx);
                let _ = crate::config::accounts::save_accounts(&app.accounts);
            }
        }
    });
}

fn open_url(url: &str) {
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg(url).spawn();
    }
    #[cfg(target_os = "linux")]
    {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("cmd")
            .args(["/c", "start", "", url])
            .spawn();
    }
}