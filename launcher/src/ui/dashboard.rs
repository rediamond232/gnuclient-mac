use eframe::egui::{self, Align2, Color32, Layout, Rect, Sense, Stroke, Ui, Vec2};

use crate::app::{GameStatus, LauncherApp};
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;
use crate::util::theme;

pub fn show(app: &mut LauncherApp, ui: &mut Ui, inst: Option<&GameInstanceConfig>) {
    let pal = theme::palette_for(&app.config.theme);

    ui.add_space(2.0);
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.label(
                egui::RichText::new("Dashboard")
                    .font(theme::display(24.0))
                    .color(pal.text),
            );
            ui.label(
                egui::RichText::new("Instance status & quick actions")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
        });
    });
    ui.add_space(18.0);

    let Some(inst) = inst else {
        ui.label(
            egui::RichText::new("No instance selected — add one in Settings.")
                .color(pal.text_dim),
        );
        return;
    };

    status_deck(app, ui, inst, pal);
    ui.add_space(16.0);

    // Account panel.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Account");
        ui.add_space(2.0);
        if let Some(acc) = app.selected_account() {
            let username = acc.username.clone();
            let online = acc.is_online();
            ui.horizontal(|ui| {
                let dot = if online { pal.success } else { pal.warn };
                ui.painter().circle_filled(
                    ui.cursor().min + Vec2::new(5.0, 10.0),
                    4.0,
                    dot,
                );
                ui.add_space(16.0);
                ui.vertical(|ui| {
                    ui.label(
                        egui::RichText::new(&username)
                            .font(theme::body_sb(15.0))
                            .color(pal.text),
                    );
                    ui.label(
                        egui::RichText::new(if online {
                            "ONLINE · Microsoft"
                        } else {
                            "OFFLINE"
                        })
                        .font(theme::mono(11.0))
                        .color(if online { pal.success } else { pal.text_dim }),
                    );
                });
                ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                    if widgets::ghost_button(ui, "Manage", pal, 100.0) {
                        app.screen = crate::ui::Screen::Accounts;
                    }
                });
            });
        } else {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("No account linked yet — sign in to play online.")
                        .color(pal.text_dim),
                );
                ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                    if widgets::primary_button(
                        ui,
                        "Add Account",
                        pal.accent,
                        pal.accent,
                        Vec2::new(150.0, 42.0),
                    ) {
                        app.screen = crate::ui::Screen::Accounts;
                    }
                });
            });
        }
    });

    ui.add_space(16.0);

    // Console / game log.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            widgets::section_title(ui, pal, "Console");
            ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                ui.label(
                    egui::RichText::new("GNUCLIENT // stdout")
                        .font(theme::mono(10.0))
                        .color(pal.text_dim),
                );
            });
        });
        egui::ScrollArea::vertical()
            .max_height(210.0)
            .auto_shrink([false, false])
            .show(ui, |ui| {
                let log = app.game_log.lock().unwrap().clone();
                if log.is_empty() {
                    ui.label(
                        egui::RichText::new("$ game output will appear here")
                            .font(theme::mono(12.0))
                            .color(pal.text_dim),
                    );
                } else {
                    for line in log.iter().rev().take(200) {
                        ui.label(
                            egui::RichText::new(line)
                                .font(theme::mono(12.0))
                                .color(pal.text_dim),
                        );
                    }
                }
            });
    });
}

fn status_pill(app: &LauncherApp, ui: &mut Ui, pal: &theme::LuxPalette) {
    let (text, color) = match &app.game_status {
        GameStatus::Running => ("RUNNING".to_string(), pal.success),
        GameStatus::Launching => ("LAUNCHING".to_string(), theme::pulse(ui.ctx(), pal)),
        GameStatus::Exited(code) => (format!("EXITED {code}"), pal.danger),
        GameStatus::Failed(e) => (format!("FAILED {e}"), pal.danger),
        GameStatus::Idle => ("READY".to_string(), pal.accent),
    };
    let width = text.len() as f32 * 8.0 + 36.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, 28.0), Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        painter.rect_filled(rect, 14.0, Color32::from_rgba_unmultiplied(
            color.r(), color.g(), color.b(), 22,
        ));
        painter.rect_stroke(
            rect,
            14.0,
            Stroke::new(1.0_f32, color.gamma_multiply(0.6)),
            egui::StrokeKind::Inside,
        );
        painter.circle_filled(
            egui::pos2(rect.left() + 13.0, rect.center().y),
            3.5,
            color,
        );
        painter.text(
            egui::pos2(rect.left() + 24.0, rect.center().y),
            Align2::LEFT_CENTER,
            text,
            theme::mono_sb(11.0),
            color,
        );
    }
    ui.add_space(6.0);
}

fn status_deck(
    app: &mut LauncherApp,
    ui: &mut Ui,
    inst: &GameInstanceConfig,
    pal: &theme::LuxPalette,
) {
    let width = ui.available_width();
    let height = 234.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, height), Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        painter.rect_filled(rect, 14.0, pal.card);
        painter.rect_stroke(rect, 14.0, Stroke::new(1.0_f32, pal.edge), egui::StrokeKind::Inside);
        // Corner washes — one ember, one ice. Subtle.
        theme::radial_fill(
            painter,
            Rect::from_center_size(
                rect.left_top() + Vec2::new(90.0, 70.0),
                Vec2::new(360.0, 360.0),
            ),
            pal.accent,
            0.06,
        );
        theme::radial_fill(
            painter,
            Rect::from_center_size(
                rect.right_bottom() - Vec2::new(90.0, 80.0),
                Vec2::new(320.0, 320.0),
            ),
            pal.accent2,
            0.05,
        );
    }

    let inner = Rect::from_min_size(
        rect.min + Vec2::new(20.0, 16.0),
        Vec2::new(rect.width() - 40.0, rect.height() - 32.0),
    );
    ui.allocate_new_ui(egui::UiBuilder::new().max_rect(inner), |ui| {
        ui.horizontal(|ui| {
            ui.vertical(|ui| {
                ui.label(
                    egui::RichText::new(&inst.name)
                        .font(theme::display(22.0))
                        .color(pal.text),
                );
                ui.label(
                    egui::RichText::new(format!(
                        "FORGE {}/{}  ·  {} MC",
                        inst.forge_version,
                        inst.mc_version,
                        inst.mc_version
                    ))
                    .font(theme::mono(11.0))
                    .color(pal.text_dim),
                );
            });
            ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                status_pill(app, ui, pal);
            });
        });
        ui.add_space(14.0);
        ui.horizontal_wrapped(|ui| {
            widgets::stat_tile(ui, pal, "MC", &inst.mc_version);
            widgets::stat_tile(ui, pal, "FORGE", &inst.forge_version);
            widgets::stat_tile(ui, pal, "MODS", &inst.installed_mods.len().to_string());
            widgets::stat_tile(ui, pal, "SHADERS", &inst.installed_shaders.len().to_string());
            widgets::stat_tile(ui, pal, "PACKS", &inst.installed_packs.len().to_string());
            widgets::stat_tile(
                ui,
                pal,
                "GNUCLIENT",
                if inst.has_gnuclient { "OK" } else { "MISSING" },
            );
        });
        ui.add_space(14.0);
        let running = matches!(app.game_status, GameStatus::Running);
        ui.horizontal(|ui| {
            ui.add_space(4.0);
            if widgets::primary_button(
                ui,
                if running { "Stop" } else { "Play" },
                if running { pal.danger } else { pal.accent },
                if running { pal.danger } else { pal.accent },
                Vec2::new(132.0, 42.0),
            ) {
                if running {
                    app.stop_game();
                } else if app.selected_account().is_none() {
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
}