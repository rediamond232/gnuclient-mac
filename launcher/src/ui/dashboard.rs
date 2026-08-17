use eframe::egui::{self, Align, Layout, RichText};
use egui::Ui;

use crate::app::{GameStatus, LauncherApp};
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;

pub fn show(app: &mut LauncherApp, ui: &mut Ui, inst: Option<&GameInstanceConfig>) {
    let pal = crate::util::theme::palette_for(&app.config.theme);

    ui.add_space(12.0);
    ui.horizontal(|ui| {
        ui.label(RichText::new("Home").size(28.0).strong().color(pal.text));
    });
    ui.add_space(8.0);

    let Some(inst) = inst else {
        ui.label(RichText::new("No instance selected.").color(pal.text_dim));
        return;
    };

    // Hero panel.
    widgets::gradient_card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                RichText::new("◆ GNUClient")
                    .size(22.0)
                    .strong()
                    .color(pal.text),
            );
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                let status_label = match &app.game_status {
                    GameStatus::Running => RichText::new("● Running").color(pal.success),
                    GameStatus::Launching => RichText::new("◌ Launching").color(pal.warn),
                    GameStatus::Exited(code) => {
                        RichText::new(format!("✕ Exited ({code})")).color(pal.danger)
                    }
                    GameStatus::Failed(e) => RichText::new(format!("✕ {e}")).color(pal.danger),
                    GameStatus::Idle => RichText::new("● Ready").color(pal.text_dim),
                };
                ui.label(status_label.size(14.0));
            });
        });
        ui.add_space(14.0);

        ui.horizontal_wrapped(|ui| {
            widgets::badge(ui, "MC", inst.mc_version.as_str(), pal.accent, pal);
            widgets::badge(ui, "Forge", inst.forge_version.as_str(), pal.accent, pal);
            widgets::badge(ui, "Mods", &inst.installed_mods.len().to_string(), pal.accent, pal);
            widgets::badge(
                ui,
                "Shaders",
                &inst.installed_shaders.len().to_string(),
                pal.accent,
                pal,
            );
            widgets::badge(
                ui,
                "Packs",
                &inst.installed_packs.len().to_string(),
                pal.accent,
                pal,
            );
            if inst.has_gnuclient {
                widgets::badge(ui, "GNUClient", "installed", pal.success, pal);
            } else {
                widgets::badge(ui, "GNUClient", "missing", pal.warn, pal);
            }
        });
        ui.add_space(16.0);

        // Quick actions.
        ui.horizontal(|ui| {
            ui.add_space(4.0);
            if widgets::primary_button(
                ui,
                "Play",
                pal.accent,
                pal.accent2,
                egui::vec2(132.0, 44.0),
            ) {
                if app.selected_account().is_none() {
                    app.show_notice(
                        "Add a Microsoft account to launch",
                        crate::app::NoticeKind::Info,
                    );
                    app.screen = crate::ui::Screen::Accounts;
                } else if !app.launch_busy {
                    crate::minecraft::launch::begin_launch(app);
                }
            }
            ui.add_space(10.0);
            if widgets::ghost_button(ui, "Manage Mods", pal, 150.0) {
                app.screen = crate::ui::Screen::Mods;
            }
            ui.add_space(10.0);
            if widgets::ghost_button(ui, "Settings", pal, 120.0) {
                app.screen = crate::ui::Screen::Settings;
            }
        });
    });

    ui.add_space(16.0);

    // Account status card.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Account");
        if let Some(acc) = app.selected_account() {
            ui.horizontal(|ui| {
                let dot = if acc.is_online() {
                    pal.success
                } else {
                    pal.warn
                };
                ui.label(RichText::new("●").color(dot).size(18.0));
                ui.add_space(6.0);
                ui.label(RichText::new(&acc.username).strong());
                ui.add_space(8.0);
                ui.label(
                    RichText::new(if acc.is_online() { "Online" } else { "Offline" })
                        .color(pal.text_dim),
                );
            });
        } else {
            ui.label(RichText::new("No account added yet.").color(pal.text_dim));
            ui.add_space(6.0);
            if widgets::ghost_button(ui, "Add Account", pal, 140.0) {
                app.screen = crate::ui::Screen::Accounts;
            }
        }
    });

    ui.add_space(16.0);

    // Console / game log.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Console");
        egui::ScrollArea::vertical()
            .max_height(220.0)
            .auto_shrink([false, false])
            .show(ui, |ui| {
                let log = app.game_log.lock().unwrap().clone();
                if log.is_empty() {
                    ui.label(RichText::new("Game output will appear here.").color(pal.text_dim));
                } else {
                    for line in log.iter().rev().take(200) {
                        ui.label(
                            RichText::new(line)
                                .size(12.0)
                                .monospace()
                                .color(pal.text_dim),
                        );
                    }
                }
            });
    });
}
