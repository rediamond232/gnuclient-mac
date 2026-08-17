pub mod accounts;
pub mod content;
pub mod dashboard;
pub mod settings;
pub mod toast;
pub mod widgets;

use eframe::egui;
use egui::Context;

use crate::app::LauncherApp;
use crate::minecraft::instance::GameInstanceConfig;
use crate::util::theme;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Screen {
    Dashboard,
    Mods,
    Shaders,
    Packs,
    Settings,
    Accounts,
}

impl Screen {
    pub fn title(self) -> &'static str {
        match self {
            Screen::Dashboard => "Home",
            Screen::Mods => "Mods",
            Screen::Shaders => "Shaders",
            Screen::Packs => "Resource Packs",
            Screen::Settings => "Settings",
            Screen::Accounts => "Accounts",
        }
    }

    fn icon(self) -> &'static str {
        match self {
            Screen::Dashboard => "⌂",
            Screen::Mods => "▤",
            Screen::Shaders => "✦",
            Screen::Packs => "▦",
            Screen::Settings => "⚙",
            Screen::Accounts => "◎",
        }
    }
}

/// The root layout: a luxurious left sidebar + central content area.
pub fn render(app: &mut LauncherApp, ctx: &Context) {
    let active: Option<GameInstanceConfig> = app.active_instance().cloned();
    let selected_id = app.active_instance_id.clone();

    egui::SidePanel::left("nav")
        .resizable(false)
        .default_width(220.0)
        .show(ctx, |ui| {
            sidebar(app, ui, &selected_id);
        });

    egui::CentralPanel::default().show(ctx, |ui| match app.screen {
        Screen::Dashboard => dashboard::show(app, ui, active.as_ref()),
        Screen::Mods => content::show(
            app,
            ui,
            crate::modrinth::types::ModrinthType::Mod,
            active.as_ref(),
        ),
        Screen::Shaders => content::show(
            app,
            ui,
            crate::modrinth::types::ModrinthType::Shader,
            active.as_ref(),
        ),
        Screen::Packs => content::show(
            app,
            ui,
            crate::modrinth::types::ModrinthType::ResourcePack,
            active.as_ref(),
        ),
        Screen::Settings => settings::show(app, ui, active.as_ref()),
        Screen::Accounts => accounts::show(app, ui, active.as_ref()),
    });

    toast::show(app, ctx);
}

fn sidebar(app: &mut LauncherApp, ui: &mut egui::Ui, selected_id: &str) {
    let pal = crate::util::theme::palette_for(&app.config.theme);

    // Brand header with a gradient mark.
    ui.add_space(18.0);
    ui.horizontal(|ui| {
        ui.add_space(16.0);
        let (rect, _) = ui.allocate_exact_size(egui::vec2(34.0, 34.0), egui::Sense::hover());
        if ui.is_rect_visible(rect) {
            theme::glow(ui.painter(), rect, pal.accent, 16.0);
            theme::gradient_rect(ui.painter(), rect, 9.0, pal.accent, pal.accent2, true);
            ui.painter().text(
                rect.center(),
                egui::Align2::CENTER_CENTER,
                "◆",
                egui::FontId::proportional(20.0),
                egui::Color32::WHITE,
            );
        }
        ui.add_space(10.0);
        ui.vertical(|ui| {
            ui.label(egui::RichText::new("GNUClient").size(21.0).strong().color(pal.text));
            ui.label(
                egui::RichText::new("Launcher")
                    .size(12.0)
                    .color(pal.text_dim),
            );
        });
    });
    ui.add_space(22.0);

    // Instance selector.
    let name = app
        .config
        .instance_by_id(selected_id)
        .map(|i| i.name.clone())
        .unwrap_or_default();
    ui.horizontal(|ui| {
        ui.add_space(16.0);
        let (rect, _) = ui.allocate_exact_size(egui::vec2(12.0, 12.0), egui::Sense::hover());
        if ui.is_rect_visible(rect) {
            ui.painter()
                .circle_filled(rect.center(), 5.0, theme::pulse(ui.ctx(), pal));
        }
        ui.add_space(8.0);
        ui.label(
            egui::RichText::new(name)
                .size(13.0)
                .strong()
                .color(pal.text_dim),
        );
    });
    ui.add_space(14.0);

    // Nav buttons.
    for screen in [
        Screen::Dashboard,
        Screen::Mods,
        Screen::Shaders,
        Screen::Packs,
        Screen::Accounts,
        Screen::Settings,
    ] {
        let is_active = app.screen == screen;
        let (rect, response) = ui.allocate_exact_size(
            egui::vec2(ui.available_width() - 20.0, 40.0),
            egui::Sense::click(),
        );
        if ui.is_rect_visible(rect) {
            let painter = ui.painter();
            if is_active {
                theme::gradient_rect(
                    painter,
                    rect,
                    10.0,
                    pal.accent_soft.gamma_multiply(0.55),
                    pal.accent_soft.gamma_multiply(0.25),
                    true,
                );
                // Accent edge on the left.
                let edge = egui::Rect::from_min_size(
                    rect.min,
                    egui::vec2(3.5, rect.height()),
                );
                theme::gradient_rect(painter, edge, 2.0, pal.accent, pal.accent2, true);
            } else if response.hovered() {
                painter.rect_filled(rect, 10.0, pal.card_hover);
            }
            // Icon tile.
            let icon_r = egui::Rect::from_center_size(
                egui::pos2(rect.min.x + 24.0, rect.center().y),
                egui::vec2(26.0, 26.0),
            );
            if is_active {
                painter.rect_filled(icon_r, 7.0, pal.accent_soft);
            }
            painter.text(
                icon_r.center(),
                egui::Align2::CENTER_CENTER,
                screen.icon(),
                egui::FontId::proportional(14.0),
                if is_active { pal.accent } else { pal.text_dim },
            );
            painter.text(
                egui::pos2(rect.min.x + 46.0, rect.center().y),
                egui::Align2::LEFT_CENTER,
                screen.title(),
                egui::FontId::proportional(14.0),
                if is_active { pal.text } else { pal.text_dim },
            );
        }
        if response.on_hover_cursor(egui::CursorIcon::PointingHand).clicked() {
            app.screen = screen;
        }
        ui.add_space(4.0);
    }

    // Launch button pinned at bottom.
    ui.with_layout(egui::Layout::bottom_up(egui::Align::Center), |ui| {
        ui.add_space(20.0);
        let can_launch = app.active_instance().is_some() && app.selected_account().is_some();
        let text = if !can_launch {
            "Add Account to Launch"
        } else {
            match &app.game_status {
                crate::app::GameStatus::Running => "Game Running",
                crate::app::GameStatus::Launching => "Launching...",
                _ => "Play",
            }
        };
        let accent = crate::util::theme::pulse(ui.ctx(), pal);
        let (rect, response) =
            ui.allocate_exact_size(egui::vec2(180.0, 46.0), egui::Sense::click());
        if ui.is_rect_visible(rect) {
            let painter = ui.painter();
            theme::glow(painter, rect, accent, 20.0);
            theme::gradient_rect(
                painter,
                rect,
                12.0,
                accent,
                pal.accent2.gamma_multiply(0.85),
                true,
            );
            painter.text(
                rect.center(),
                egui::Align2::CENTER_CENTER,
                text,
                egui::FontId::proportional(16.0),
                egui::Color32::WHITE,
            );
        }
        if response
            .on_hover_cursor(egui::CursorIcon::PointingHand)
            .clicked()
            && can_launch
            && !app.launch_busy
        {
            crate::minecraft::launch::begin_launch(app);
        }
        ui.add_space(24.0);
    });
}
