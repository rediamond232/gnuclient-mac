use eframe::egui::{self, Color32, RichText, Stroke, Ui};
use egui::{Frame, Pos2, Rect, TextureHandle, Vec2};

use crate::util::theme::{self, LuxPalette};

/// A styled card container with a subtle border.
pub fn card(ui: &mut Ui, palette: &LuxPalette, add_contents: impl FnOnce(&mut Ui)) {
    Frame::new()
        .fill(palette.card)
        .corner_radius(14.0)
        .stroke(Stroke::new(1.0_f32, palette.card_hover))
        .inner_margin(egui::Margin::symmetric(18, 16))
        .show(ui, add_contents);
}

/// A hero card with an accent gradient fill and soft glow.
pub fn gradient_card(
    ui: &mut Ui,
    palette: &LuxPalette,
    add_contents: impl FnOnce(&mut Ui),
) {
    let height = 220.0;
    let width = ui.available_width();
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, height), egui::Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        theme::glow(painter, rect, palette.accent, 26.0);
        theme::gradient_rect(painter, rect, 16.0, palette.card, palette.accent_soft, true);
        painter.rect_stroke(
            rect,
            16.0,
            Stroke::new(1.0_f32, palette.card_hover),
            egui::StrokeKind::Inside,
        );
    }
    // Contents laid out inside the hero.
    let inner = Rect::from_min_size(
        rect.min + Vec2::new(18.0, 14.0),
        Vec2::new(rect.width() - 36.0, rect.height() - 28.0),
    );
    ui.allocate_new_ui(egui::UiBuilder::new().max_rect(inner), add_contents);
}

/// A gradient-filled primary button with a hover glow.
pub fn primary_button(
    ui: &mut Ui,
    label: &str,
    from: Color32,
    to: Color32,
    size: Vec2,
) -> bool {
    let (rect, response) = ui.allocate_exact_size(size, egui::Sense::click());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        let (a, b) = if response.hovered() {
            (from, to)
        } else {
            (from.gamma_multiply(0.9), to.gamma_multiply(0.9))
        };
        if response.hovered() {
            theme::glow(painter, rect, a, 14.0);
        }
        theme::gradient_rect(painter, rect, 12.0, a, b, true);
        painter.rect_filled(
            Rect::from_min_max(rect.min, Pos2::new(rect.max.x, rect.min.y + 1.5)),
            12.0,
            Color32::from_white_alpha(40),
        );
        painter.text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            label,
            egui::FontId::proportional(15.0),
            Color32::WHITE,
        );
    }
    response
        .on_hover_cursor(egui::CursorIcon::PointingHand)
        .clicked()
}

/// A row of nav/tab chips.
pub fn tab_chip(ui: &mut Ui, label: &str, selected: bool, palette: &LuxPalette) -> bool {
    let fg = if selected {
        Color32::WHITE
    } else {
        palette.text_dim
    };
    let (rect, response) = ui.allocate_exact_size(Vec2::new(110.0, 34.0), egui::Sense::click());
    if ui.is_rect_visible(rect) {
        if selected {
            theme::gradient_rect(ui.painter(), rect, 9.0, palette.accent, palette.accent2, true);
        } else {
            ui.painter().rect_filled(rect, 9.0, palette.card);
        }
        ui.painter().text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            label,
            egui::FontId::proportional(14.0),
            fg,
        );
    }
    response
        .on_hover_cursor(egui::CursorIcon::PointingHand)
        .clicked()
}

/// A subtle "ghost" button.
pub fn ghost_button(ui: &mut Ui, label: &str, palette: &LuxPalette, width: f32) -> bool {
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, 32.0), egui::Sense::click());
    if ui.is_rect_visible(rect) {
        let fill = if response.hovered() {
            palette.card_hover
        } else {
            Color32::TRANSPARENT
        };
        ui.painter().rect_filled(rect, 9.0, fill);
        ui.painter().rect_stroke(
            rect,
            9.0,
            Stroke::new(1.0_f32, palette.card_hover),
            egui::StrokeKind::Inside,
        );
        ui.painter().text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            label,
            egui::FontId::proportional(13.0),
            if response.hovered() {
                palette.text
            } else {
                palette.accent
            },
        );
    }
    response
        .on_hover_cursor(egui::CursorIcon::PointingHand)
        .clicked()
}

/// A rounded mod icon tile: shows the image when available, otherwise a
/// gradient monogram tile.
pub fn icon_tile(
    ui: &mut Ui,
    tex: Option<&TextureHandle>,
    fallback: &str,
    palette: &LuxPalette,
    size: f32,
) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(size, size), egui::Sense::hover());
    if !ui.is_rect_visible(rect) {
        return;
    }
    let radius: f32 = size * 0.24;
    if let Some(t) = tex {
        // Image with truly rounded corners, framed by a soft accent ring.
        let inner = rect.shrink(2.0);
        ui.put(
            inner,
            egui::Image::new(t)
                .fit_to_exact_size(inner.size())
                .rounding(radius),
        );
        ui.painter().rect_stroke(
            rect,
            radius,
            Stroke::new(1.5_f32, palette.accent_soft),
            egui::StrokeKind::Inside,
        );
    } else {
        let painter = ui.painter();
        theme::gradient_rect(painter, rect, radius, palette.accent, palette.accent2, true);
        let ch = fallback
            .chars()
            .next()
            .unwrap_or('?')
            .to_uppercase()
            .to_string();
        painter.text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            ch,
            egui::FontId::proportional(size * 0.42),
            Color32::WHITE,
        );
    }
    ui.add_space(12.0);
}

/// A small status pill (label + value).
pub fn badge(
    ui: &mut Ui,
    label: &str,
    value: &str,
    color: Color32,
    palette: &LuxPalette,
) {
    let text = format!("{label} {value}");
    let width = text.len() as f32 * 6.4 + 26.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, 26.0), egui::Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        painter.rect_filled(rect, 13.0, palette.card_hover);
        painter.rect_stroke(
            rect,
            13.0,
            Stroke::new(1.0_f32, color.gamma_multiply(0.5)),
            egui::StrokeKind::Inside,
        );
        painter.text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            text,
            egui::FontId::proportional(11.0),
            palette.text,
        );
    }
    ui.add_space(6.0);
}

/// Section heading with an accent tick.
pub fn section_title(ui: &mut Ui, palette: &LuxPalette, title: &str) {
    ui.add_space(6.0);
    ui.horizontal(|ui| {
        let tick = ui
            .allocate_exact_size(Vec2::new(4.0, 18.0), egui::Sense::hover())
            .0;
        theme::gradient_rect(ui.painter(), tick, 2.0, palette.accent, palette.accent2, true);
        ui.add_space(8.0);
        ui.label(RichText::new(title).size(18.0).strong().color(palette.text));
    });
    ui.add_space(8.0);
}

/// A subtle divider line.
pub fn divider(ui: &mut Ui, palette: &LuxPalette) {
    let w = ui.available_width();
    let (rect, _) = ui.allocate_exact_size(Vec2::new(w, 1.0), egui::Sense::hover());
    ui.painter().rect_filled(rect, 0.0, palette.card_hover);
    ui.add_space(6.0);
}
