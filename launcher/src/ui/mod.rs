pub mod accounts;
pub mod content;
pub mod dashboard;
pub mod settings;
pub mod toast;
pub mod widgets;

use eframe::egui;
use egui::{Align, Align2, Color32, Context, Layout, Pos2, Rect, Sense, Stroke, Vec2};

use crate::app::{GameStatus, LauncherApp};
use crate::minecraft::instance::GameInstanceConfig;
use crate::util::theme;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Screen {
    Dashboard,
    Mods,
    Shaders,
    Packs,
    Accounts,
    Settings,
    Dev,
}

impl Screen {
    pub fn title(self) -> &'static str {
        match self {
            Screen::Dashboard => "Home",
            Screen::Mods => "Mods",
            Screen::Shaders => "Shaders",
            Screen::Packs => "Resource Packs",
            Screen::Accounts => "Accounts",
            Screen::Settings => "Settings",
            Screen::Dev => "Dev",
        }
    }

    fn icon(self) -> &'static str {
        // Keys consumed by `widgets::draw_nav_icon` (drawn as vector glyphs,
        // so they never depend on a font having the right codepoint).
        match self {
            Screen::Dashboard => "home",
            Screen::Mods => "mods",
            Screen::Shaders => "shaders",
            Screen::Packs => "packs",
            Screen::Accounts => "accounts",
            Screen::Settings => "settings",
            Screen::Dev => "dev",
        }
    }
}

/// The root layout: a deep sidebar + an atmosphere-washed central pane.
pub fn render(app: &mut LauncherApp, ctx: &Context) {
    let pal = theme::palette_for(&app.config.theme);
    let active: Option<GameInstanceConfig> = app.active_instance().cloned();
    let selected_id = app.active_instance_id.clone();

    egui::SidePanel::left("nav")
        .resizable(false)
        .default_width(216.0)
        .frame(
            egui::Frame::new()
                .fill(pal.bg_deep)
                .inner_margin(egui::Margin::symmetric(14, 12)),
        )
        .show(ctx, |ui| {
            sidebar(app, ui, &selected_id, pal);
        });

    egui::CentralPanel::default()
        .frame(
            egui::Frame::new()
                .fill(pal.bg)
                .inner_margin(egui::Margin::same(24)),
        )
        .show(ctx, |ui| {
            theme::atmosphere(ui.painter(), ui.max_rect(), pal);
            match app.screen {
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
                Screen::Accounts => accounts::show(app, ui, active.as_ref()),
                Screen::Settings => settings::show(app, ui, active.as_ref()),
                Screen::Dev => crate::dev::show(app, ui, active.as_ref()),
            }
        });

    toast::show(app, ctx);
}

fn sidebar(app: &mut LauncherApp, ui: &mut egui::Ui, _selected_id: &str, pal: &theme::LuxPalette) {
    brand(ui, pal);
    ui.add_space(22.0);

    let nav = [
        Screen::Dashboard,
        Screen::Mods,
        Screen::Shaders,
        Screen::Packs,
        Screen::Accounts,
        Screen::Settings,
        Screen::Dev,
    ];
    for s in nav {
        let active = app.screen == s;
        if widgets::nav_item(ui, s.icon(), s.title(), active, pal) {
            app.screen = s;
        }
        ui.add_space(4.0);
    }

    ui.with_layout(Layout::bottom_up(Align::LEFT), |ui| {
        ui.add_space(16.0);
        play_button(app, ui, pal);
        ui.add_space(14.0);
        instance_chip(app, ui, pal);
    });
}

fn brand(ui: &mut egui::Ui, pal: &theme::LuxPalette) {
    ui.horizontal(|ui| {
        let (rect, _) = ui.allocate_exact_size(Vec2::new(38.0, 38.0), Sense::hover());
        if ui.is_rect_visible(rect) {
            let painter = ui.painter();
            // Glass tile with a deep two-tone gradient.
            theme::gradient_rect(
                painter,
                rect,
                11.0,
                pal.accent,
                theme::mix(pal.accent, pal.accent2, 0.45),
                true,
            );
            // Bright glass rim to match the cards.
            painter.rect_stroke(
                rect,
                11.0,
                Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 40)),
                egui::StrokeKind::Inside,
            );
            // Vector "G" monogram — crisp at any size, unlike a font glyph.
            let c = rect.center();
            let r = rect.width() * 0.285;
            let ink = Color32::from_rgb(20, 14, 8);
            let thick = rect.width() * 0.13;
            let gap = 38_f32.to_radians();
            let steps = 44;
            let mut pts: Vec<Pos2> = Vec::with_capacity(steps + 1);
            for i in 0..=steps {
                let a = gap + (std::f32::consts::TAU - 2.0 * gap) * (i as f32 / steps as f32);
                pts.push(c + Vec2::new(a.cos(), a.sin()) * r);
            }
            let stroke = Stroke::new(thick, ink);
            for i in 0..steps {
                painter.line_segment([pts[i], pts[i + 1]], stroke);
            }
            // Crossbar of the G.
            painter.line_segment(
                [Pos2::new(c.x - r * 0.35, c.y), Pos2::new(c.x + r * 0.92, c.y)],
                Stroke::new(thick, ink),
            );
            // A small ember spark at the opening — a nod to the terminal cursor.
            painter.circle_filled(
                Pos2::new(c.x + r, c.y),
                rect.width() * 0.055,
                pal.accent2,
            );
        }
        ui.add_space(10.0);
        ui.vertical(|ui| {
            ui.label(
                egui::RichText::new("GNUCLIENT")
                    .font(theme::display(18.0))
                    .color(pal.text)
                    .extra_letter_spacing(0.6),
            );
            ui.label(
                egui::RichText::new("LAUNCHER  //  v0.1")
                    .font(theme::mono(9.0))
                    .color(pal.text_dim),
            );
        });
    });
}

fn instance_chip(app: &mut LauncherApp, ui: &mut egui::Ui, pal: &theme::LuxPalette) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(188.0, 40.0), Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        // Glass chip — translucent fill + bright rim, matching the cards.
        painter.rect_filled(rect, 10.0, Color32::from_rgba_unmultiplied(22, 26, 38, 90));
        painter.rect_stroke(
            rect,
            10.0,
            Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 38)),
            egui::StrokeKind::Inside,
        );
        let dot = match &app.game_status {
            GameStatus::Running => pal.success,
            GameStatus::Launching => theme::pulse(ui.ctx(), pal),
            GameStatus::Exited(_) | GameStatus::Failed(_) => pal.danger,
            GameStatus::Idle => pal.text_dim,
        };
        painter.circle_filled(
            egui::pos2(rect.left() + 24.0, rect.center().y),
            3.0,
            dot,
        );
        let name = app
            .active_instance()
            .map(|i| i.name.clone())
            .unwrap_or_else(|| "No instance".to_string());
        painter.text(
            egui::pos2(rect.left() + 46.0, rect.center().y),
            Align2::LEFT_CENTER,
            name,
            theme::body_sb(13.0),
            pal.text,
        );
        let ver = app
            .active_instance()
            .map(|i| i.mc_version.clone())
            .unwrap_or_default();
        painter.text(
            egui::pos2(rect.right() - 10.0, rect.center().y),
            Align2::RIGHT_CENTER,
            ver,
            theme::mono(10.0),
            pal.text_dim,
        );
    }
}

fn play_button(app: &mut LauncherApp, ui: &mut egui::Ui, pal: &theme::LuxPalette) {
    let can_launch = app.active_instance().is_some() && app.selected_account().is_some();
    let running = matches!(app.game_status, GameStatus::Running);
    let text = if !can_launch {
        "ADD ACCOUNT"
    } else if running {
        "STOP"
    } else {
        match &app.game_status {
            GameStatus::Launching => "LAUNCHING...",
            _ => "PLAY",
        }
    };
    let (rect, response) = ui.allocate_exact_size(Vec2::new(188.0, 48.0), Sense::click());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        let t = theme::anim(ui.ctx(), response.id, response.hovered(), 0.10);
        let base = if !can_launch {
            pal.text_dim
        } else if running {
            pal.danger
        } else {
            pal.accent
        };
        if t > 0.0 && can_launch {
            theme::glow(painter, rect, base, 2.5 + 2.5 * t);
        }
        let (top, bot) = if response.is_pointer_button_down_on() {
            (
                theme::mix(base, Color32::BLACK, 0.20),
                theme::mix(base, Color32::BLACK, 0.34),
            )
        } else {
            (
                theme::mix(base, Color32::WHITE, 0.10),
                theme::mix(base, Color32::BLACK, 0.16),
            )
        };
        theme::gradient_rect(painter, rect, 11.0, top, bot, true);
        // Drawn play triangle (no font dependency) + left-aligned label.
        let cy = rect.center().y;
        if running {
            // Stop: a filled square instead of the play triangle.
            let sq = Rect::from_center_size(
                egui::pos2(rect.left() + 24.0, cy),
                Vec2::new(11.0, 11.0),
            );
            painter.rect_filled(sq, 2.0, Color32::from_rgb(16, 12, 8));
        } else {
            let tri = [
                egui::pos2(rect.left() + 18.0, cy - 8.0),
                egui::pos2(rect.left() + 18.0, cy + 8.0),
                egui::pos2(rect.left() + 30.0, cy),
            ];
            painter.add(egui::Shape::convex_polygon(
                tri.to_vec(),
                Color32::from_rgb(16, 12, 8),
                egui::Stroke::NONE,
            ));
        }
        painter.text(
            egui::pos2(rect.left() + 46.0, cy),
            Align2::LEFT_CENTER,
            text,
            theme::display_sb(15.0),
            Color32::from_rgb(16, 12, 8),
        );
    }
    if response
        .on_hover_cursor(egui::CursorIcon::PointingHand)
        .clicked()
    {
        if running {
            app.stop_game();
        } else if can_launch && !app.launch_busy {
            crate::minecraft::launch::begin_launch(app);
        }
    }
}