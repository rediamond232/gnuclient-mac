use eframe::egui::{self, Align2, Color32, Pos2, Rect, Response, RichText, Sense, Stroke, Ui, Vec2};

use crate::util::theme::{self, LuxPalette};

/// A liquid-glass panel: translucent fill, bright rim.
pub fn card(ui: &mut Ui, palette: &LuxPalette, add_contents: impl FnOnce(&mut Ui)) {
    let frame = egui::Frame::new()
        // Translucent dark fill so the colorful backdrop reads through.
        .fill(egui::Color32::from_rgba_unmultiplied(22, 26, 38, 90))
        .corner_radius(16.0)
        // Bright glass rim.
        .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 38)))
        .inner_margin(egui::Margin::symmetric(18, 16));
    let resp = frame.show(ui, add_contents);
    let rect = resp.response.rect;
    let painter = ui.painter();
    // Subtle bottom shading for depth.
    painter.rect_filled(
        Rect::from_min_max(
            rect.min + Vec2::new(2.0, rect.height() - 2.0),
            rect.min + Vec2::new(rect.width() - 2.0, rect.height()),
        ),
        1.0,
        Color32::from_black_alpha(22),
    );
}

/// The signature action button. Derives a value gradient (lighter top,
/// darker bottom) from the single accent `from`. Text is dark for contrast.
pub fn primary_button(
    ui: &mut Ui,
    label: &str,
    from: Color32,
    _to: Color32,
    size: Vec2,
) -> bool {
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        let t = theme::anim(ui.ctx(), response.id, response.hovered(), 0.10);
        if t > 0.0 {
            theme::glow(painter, rect, from, 2.5 + 2.5 * t);
        }
        let (top, bottom) = if response.is_pointer_button_down_on() {
            (
                theme::mix(from, Color32::BLACK, 0.20),
                theme::mix(from, Color32::BLACK, 0.34),
            )
        } else {
            (
                theme::mix(from, Color32::WHITE, 0.12),
                theme::mix(from, Color32::BLACK, 0.16),
            )
        };
        theme::gradient_rect(painter, rect, 10.0, top, bottom, true);
        painter.text(
            rect.center(),
            Align2::CENTER_CENTER,
            label,
            theme::body_sb(14.0),
            Color32::from_rgb(16, 12, 8),
        );
    }
    response.on_hover_cursor(egui::CursorIcon::PointingHand).clicked()
}

/// A quiet secondary button: hairline border, fill fades in on hover.
pub fn ghost_button(ui: &mut Ui, label: &str, palette: &LuxPalette, width: f32) -> bool {
    let (rect, response) =
        ui.allocate_exact_size(Vec2::new(width, 42.0), Sense::click());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        let t = theme::anim(ui.ctx(), response.id, response.hovered(), 0.10);
        let fill = theme::lerp(palette.card, palette.card_hover, t);
        painter.rect_filled(rect, 9.0, fill);
        painter.rect_stroke(
            rect,
            9.0,
            Stroke::new(1.0_f32, palette.edge),
            egui::StrokeKind::Inside,
        );
        painter.text(
            rect.center(),
            Align2::CENTER_CENTER,
            label,
            theme::body(14.0),
            theme::lerp(palette.text_dim, palette.text, t),
        );
    }
    response.on_hover_cursor(egui::CursorIcon::PointingHand).clicked()
}

/// A slim text pill with a status dot — used for inline readouts.
pub fn badge(ui: &mut Ui, label: &str, value: &str, color: Color32, palette: &LuxPalette) {
    let text = format!("{label} {value}");
    let width = text.len() as f32 * 7.2 + 30.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, 26.0), Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        painter.rect_filled(rect, 13.0, palette.card_hover);
        painter.rect_stroke(
            rect,
            13.0,
            Stroke::new(1.0_f32, color.gamma_multiply(0.45)),
            egui::StrokeKind::Inside,
        );
        painter.circle_filled(
            egui::pos2(rect.left() + 12.0, rect.center().y),
            2.5,
            color,
        );
        painter.text(
            egui::pos2(rect.left() + 22.0, rect.center().y),
            Align2::LEFT_CENTER,
            label,
            theme::mono(10.0),
            palette.text_dim,
        );
        painter.text(
            egui::pos2(rect.right() - 12.0, rect.center().y),
            Align2::RIGHT_CENTER,
            value,
            theme::mono(11.0),
            palette.text,
        );
    }
    ui.add_space(6.0);
}

/// Draw a crisp vector glyph for a nav item. Keys: "home", "mods", "shaders",
/// "packs", "accounts", "settings". Drawn with the painter so it never depends
/// on a font having the right codepoint (which caused missing/tofu icons).
pub fn draw_nav_icon(painter: &egui::Painter, key: &str, c: egui::Pos2, color: Color32) {
    let none = egui::Stroke::NONE;
    match key {
        "home" => {
            painter.add(egui::Shape::convex_polygon(
                vec![
                    egui::pos2(c.x - 9.0, c.y - 1.0),
                    egui::pos2(c.x, c.y - 9.0),
                    egui::pos2(c.x + 9.0, c.y - 1.0),
                ],
                color,
                none,
            ));
            painter.rect_filled(
                egui::Rect::from_min_max(
                    egui::pos2(c.x - 6.0, c.y - 1.0),
                    egui::pos2(c.x + 6.0, c.y + 9.0),
                ),
                1.0,
                color,
            );
        }
        "mods" => {
            for (dx, dy) in [(-5.5, -5.5), (1.0, -5.5), (-5.5, 1.0), (1.0, 1.0)] {
                painter.rect_filled(
                    egui::Rect::from_min_size(
                        egui::pos2(c.x + dx, c.y + dy),
                        egui::vec2(5.0, 5.0),
                    ),
                    1.0,
                    color,
                );
            }
        }
        "shaders" => {
            let pts = vec![
                egui::pos2(c.x, c.y - 10.0),
                egui::pos2(c.x + 3.0, c.y - 3.0),
                egui::pos2(c.x + 10.0, c.y),
                egui::pos2(c.x + 3.0, c.y + 3.0),
                egui::pos2(c.x, c.y + 10.0),
                egui::pos2(c.x - 3.0, c.y + 3.0),
                egui::pos2(c.x - 10.0, c.y),
                egui::pos2(c.x - 3.0, c.y - 3.0),
            ];
            painter.add(egui::Shape::convex_polygon(pts, color, none));
        }
        "packs" => {
            painter.rect_filled(
                egui::Rect::from_min_max(
                    egui::pos2(c.x - 5.0, c.y - 2.0),
                    egui::pos2(c.x + 5.0, c.y + 8.0),
                ),
                1.0,
                color,
            );
            painter.add(egui::Shape::convex_polygon(
                vec![
                    egui::pos2(c.x - 5.0, c.y - 2.0),
                    egui::pos2(c.x + 5.0, c.y - 2.0),
                    egui::pos2(c.x + 2.0, c.y - 7.0),
                    egui::pos2(c.x - 8.0, c.y - 7.0),
                ],
                color,
                none,
            ));
        }
        "accounts" => {
            painter.circle_filled(egui::pos2(c.x, c.y - 5.0), 4.0, color);
            painter.add(egui::Shape::convex_polygon(
                vec![
                    egui::pos2(c.x - 8.0, c.y + 10.0),
                    egui::pos2(c.x - 4.0, c.y + 2.0),
                    egui::pos2(c.x + 4.0, c.y + 2.0),
                    egui::pos2(c.x + 8.0, c.y + 10.0),
                ],
                color,
                none,
            ));
        }
        "settings" => {
            for (i, y) in [c.y - 5.0, c.y, c.y + 5.0].iter().enumerate() {
                painter.line_segment(
                    [egui::pos2(c.x - 9.0, *y), egui::pos2(c.x + 9.0, *y)],
                    egui::Stroke::new(1.5, color),
                );
                let kx = if i == 0 { c.x - 3.0 } else if i == 1 { c.x + 3.0 } else { c.x - 1.0 };
                painter.circle_filled(egui::pos2(kx, *y), 3.0, color);
            }
        }
        "dev" => {
            let s = egui::Stroke::new(2.5, color);
            // A "code" glyph: </> — left chevron, slash, right chevron.
            painter.line_segment([egui::pos2(c.x - 8.0, c.y - 5.0), egui::pos2(c.x - 3.0, c.y)], s);
            painter.line_segment([egui::pos2(c.x - 3.0, c.y), egui::pos2(c.x - 8.0, c.y + 5.0)], s);
            painter.line_segment([egui::pos2(c.x - 1.0, c.y + 6.0), egui::pos2(c.x + 1.0, c.y - 6.0)], s);
            painter.line_segment([egui::pos2(c.x + 8.0, c.y - 5.0), egui::pos2(c.x + 3.0, c.y)], s);
            painter.line_segment([egui::pos2(c.x + 3.0, c.y), egui::pos2(c.x + 8.0, c.y + 5.0)], s);
        }
        _ => {}
    }
}

/// A sidebar navigation item. Ember left notch + tinted fill when active,
/// quiet hairline hover otherwise. Returns true when clicked.
pub fn nav_item(ui: &mut Ui, icon: &str, label: &str, active: bool, palette: &LuxPalette) -> bool {
    let (rect, response) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 42.0), Sense::click());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        let hov = theme::anim(ui.ctx(), response.id, response.hovered() && !active, 0.12);
        if active {
            painter.rect_filled(
                rect,
                9.0,
                Color32::from_rgba_unmultiplied(
                    palette.accent.r(),
                    palette.accent.g(),
                    palette.accent.b(),
                    24,
                ),
            );
        } else if hov > 0.0 {
            painter.rect_filled(
                rect,
                9.0,
                theme::lerp(Color32::TRANSPARENT, palette.card_hover, hov),
            );
        }
        // Ember left notch.
        if active {
            painter.rect_filled(
                Rect::from_min_size(rect.min, Vec2::new(3.0, rect.height())),
                1.5,
                palette.accent,
            );
        }
        // Icon seat.
        let icon_r = Rect::from_center_size(
            egui::pos2(rect.min.x + 24.0, rect.center().y),
            Vec2::new(30.0, 30.0),
        );
        if active {
            painter.rect_filled(
                icon_r,
                8.0,
                Color32::from_rgba_unmultiplied(
                    palette.accent.r(),
                    palette.accent.g(),
                    palette.accent.b(),
                    30,
                ),
            );
        }
        let icon_color = if active { palette.accent } else { palette.text_dim };
        draw_nav_icon(painter, icon, icon_r.center(), icon_color);
        painter.text(
            egui::pos2(rect.min.x + 46.0, rect.center().y),
            Align2::LEFT_CENTER,
            label,
            theme::body(14.0),
            if active { palette.text } else { palette.text_dim },
        );
    }
    response.on_hover_cursor(egui::CursorIcon::PointingHand).clicked()
}

/// A rounded mod icon tile: shows the image when available, otherwise a
/// mono monogram on a hairline panel.
pub fn icon_tile(
    ui: &mut Ui,
    tex: Option<&egui::TextureHandle>,
    fallback: &str,
    palette: &LuxPalette,
    size: f32,
) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(size, size), Sense::hover());
    if !ui.is_rect_visible(rect) {
        return;
    }
    let radius: f32 = size * 0.22;
    if let Some(t) = tex {
        let inner = rect.shrink(1.5);
        ui.put(
            inner,
            egui::Image::new(t)
                .fit_to_exact_size(inner.size())
                .corner_radius(radius),
        );
        ui.painter().rect_stroke(
            rect,
            radius,
            Stroke::new(1.0_f32, palette.edge),
            egui::StrokeKind::Inside,
        );
    } else {
        let painter = ui.painter();
        painter.rect_filled(rect, radius, palette.card_hover);
        painter.rect_stroke(
            rect,
            radius,
            Stroke::new(1.0_f32, palette.edge),
            egui::StrokeKind::Inside,
        );
        let ch = fallback
            .chars()
            .next()
            .unwrap_or('?')
            .to_uppercase()
            .to_string();
        painter.text(
            rect.center(),
            Align2::CENTER_CENTER,
            ch,
            theme::display(size * 0.34),
            palette.accent,
        );
    }
    ui.add_space(12.0);
}

/// A compact stat card for the dashboard (mono label + value).
pub fn stat_tile(ui: &mut Ui, palette: &LuxPalette, label: &str, value: &str) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(132.0, 62.0), Sense::hover());
    if ui.is_rect_visible(rect) {
        let painter = ui.painter();
        painter.rect_filled(rect, 10.0, palette.card);
        painter.rect_stroke(
            rect,
            10.0,
            Stroke::new(1.0_f32, palette.edge),
            egui::StrokeKind::Inside,
        );
        painter.text(
            egui::pos2(rect.left() + 14.0, rect.top() + 12.0),
            Align2::LEFT_TOP,
            label.to_uppercase(),
            theme::mono(10.0),
            palette.text_dim,
        );
        painter.text(
            egui::pos2(rect.left() + 14.0, rect.bottom() - 13.0),
            Align2::LEFT_BOTTOM,
            value,
            theme::display(18.0),
            palette.text,
        );
    }
    ui.add_space(10.0);
}

/// Section heading with a short ember tick.
pub fn section_title(ui: &mut Ui, palette: &LuxPalette, title: &str) {
    ui.add_space(2.0);
    ui.horizontal(|ui| {
        let (tick, _) = ui.allocate_exact_size(Vec2::new(3.0, 15.0), Sense::hover());
        ui.painter().rect_filled(tick, 1.5, palette.accent);
        ui.add_space(9.0);
        ui.label(
            RichText::new(title)
                .font(theme::display(15.0))
                .color(palette.text),
        );
    });
    ui.add_space(4.0);
}

/// A subtle hairline divider.
pub fn divider(ui: &mut Ui, palette: &LuxPalette) {
    let w = ui.available_width();
    let (rect, _) = ui.allocate_exact_size(Vec2::new(w, 1.0), Sense::hover());
    ui.painter().rect_filled(rect, 0.0, palette.edge);
    ui.add_space(6.0);
}

/// A clickable text input with the launcher's field styling.
pub fn field<'a>(ui: &mut Ui, text: &'a mut String, hint: &str, width: f32) -> Response {
    ui.add(
        egui::TextEdit::singleline(text)
            .hint_text(hint)
            .desired_width(width)
            .font(theme::body(14.0)),
    )
}