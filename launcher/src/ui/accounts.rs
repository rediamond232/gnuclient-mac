use eframe::egui::{self, Align, Layout, RichText, Ui};

use crate::app::{LauncherApp, NoticeKind};
use crate::minecraft::auth;
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;

pub fn show(app: &mut LauncherApp, ui: &mut Ui, _inst: Option<&GameInstanceConfig>) {
    let pal = crate::util::theme::palette_for(&app.config.theme);

    ui.add_space(12.0);
    ui.label(
        RichText::new("Accounts")
            .size(26.0)
            .strong()
            .color(pal.text),
    );
    ui.add_space(12.0);

    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Microsoft Account");
        ui.add_space(6.0);

        if app.accounts.is_empty() {
            ui.label(
                RichText::new(
                    "Sign in with your Microsoft account to play. We use the \
                     official device-code flow — no password is stored here.",
                )
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
                RichText::new(
                    "Sign in with Microsoft, then allow Xbox access. The \
                     redirect lands back in the launcher automatically.",
                )
                .color(pal.text_dim),
            );
            ui.add_space(4.0);
            ui.label(RichText::new(&url).color(pal.accent).underline());
            ui.add_space(8.0);
            let mut cancelled = false;
            ui.horizontal(|ui| {
                if ui.button("Open Browser").clicked() {
                    open_url(&url);
                }
                if ui.button("Cancel").clicked() {
                    cancelled = true;
                }
            });
            if cancelled {
                app.device_login = None;
            }
            ui.add_space(4.0);
            ui.label(
                RichText::new("Waiting for you to complete sign-in in your browser…")
                    .size(12.0)
                    .color(pal.warn),
            );
        } else {
            if widgets::primary_button(
                ui,
                "Sign in with Microsoft",
                pal.accent,
                pal.accent_soft,
                egui::vec2(220.0, 42.0),
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
                RichText::new(
                    "The launcher will open a Microsoft sign-in link. Complete it \
                     in your browser and return here.",
                )
                .size(12.0)
                .color(pal.text_dim),
            );
        }
    });

    ui.add_space(14.0);

    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Saved Accounts");
        if app.accounts.is_empty() {
            ui.label(RichText::new("No saved accounts yet.").color(pal.text_dim));
        } else {
            let mut to_remove: Option<usize> = None;
            for (i, acc) in app.accounts.iter().enumerate() {
                ui.horizontal(|ui| {
                    let dot = if acc.is_online() {
                        pal.success
                    } else {
                        pal.warn
                    };
                    ui.label(RichText::new("●").color(dot));
                    ui.vertical(|ui| {
                        ui.label(RichText::new(&acc.username).strong());
                        ui.label(
                            RichText::new(format!(
                                "{} · UUID {}",
                                if acc.is_online() { "Online" } else { "Offline" },
                                &acc.mc_uuid[..acc.mc_uuid.len().min(8)]
                            ))
                            .size(12.0)
                            .color(pal.text_dim),
                        );
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui.small_button("Remove").clicked() {
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
    let _ = Align::default();
}
