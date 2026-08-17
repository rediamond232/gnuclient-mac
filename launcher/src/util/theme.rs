use egui::{Color32, Context, Rgba, Stroke, TextStyle, Visuals};

/// Luxurious palette tuned for a dark, premium launcher feel.
pub struct LuxPalette {
    pub bg: Color32,
    pub bg_deep: Color32,
    pub panel: Color32,
    pub card: Color32,
    pub card_hover: Color32,
    pub accent: Color32,
    pub accent_soft: Color32,
    pub accent2: Color32,
    pub text: Color32,
    pub text_dim: Color32,
    pub danger: Color32,
    pub success: Color32,
    pub warn: Color32,
}

pub const ONYX: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(12, 12, 18),
    bg_deep: Color32::from_rgb(6, 6, 10),
    panel: Color32::from_rgb(18, 18, 28),
    card: Color32::from_rgb(24, 24, 36),
    card_hover: Color32::from_rgb(30, 30, 46),
    accent: Color32::from_rgb(120, 180, 255),
    accent_soft: Color32::from_rgb(60, 90, 160),
    accent2: Color32::from_rgb(180, 120, 255),
    text: Color32::from_rgb(235, 238, 245),
    text_dim: Color32::from_rgb(150, 155, 170),
    danger: Color32::from_rgb(235, 90, 90),
    success: Color32::from_rgb(90, 210, 130),
    warn: Color32::from_rgb(235, 190, 90),
};

pub const AURORA: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(10, 16, 24),
    bg_deep: Color32::from_rgb(5, 9, 14),
    panel: Color32::from_rgb(16, 24, 34),
    card: Color32::from_rgb(21, 30, 42),
    card_hover: Color32::from_rgb(26, 37, 51),
    accent: Color32::from_rgb(70, 220, 200),
    accent_soft: Color32::from_rgb(30, 110, 120),
    accent2: Color32::from_rgb(120, 160, 255),
    text: Color32::from_rgb(238, 242, 245),
    text_dim: Color32::from_rgb(150, 165, 175),
    danger: Color32::from_rgb(235, 90, 90),
    success: Color32::from_rgb(90, 210, 130),
    warn: Color32::from_rgb(235, 190, 90),
};

pub const OBSIDIAN: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(16, 12, 20),
    bg_deep: Color32::from_rgb(9, 6, 12),
    panel: Color32::from_rgb(24, 18, 30),
    card: Color32::from_rgb(30, 23, 38),
    card_hover: Color32::from_rgb(37, 29, 46),
    accent: Color32::from_rgb(230, 120, 220),
    accent_soft: Color32::from_rgb(120, 50, 120),
    accent2: Color32::from_rgb(120, 180, 255),
    text: Color32::from_rgb(240, 236, 242),
    text_dim: Color32::from_rgb(165, 155, 172),
    danger: Color32::from_rgb(235, 90, 90),
    success: Color32::from_rgb(90, 210, 130),
    warn: Color32::from_rgb(235, 190, 90),
};

pub fn palette_for(name: &str) -> &'static LuxPalette {
    match name {
        "aurora" => &AURORA,
        "obsidian" => &OBSIDIAN,
        _ => &ONYX,
    }
}

/// Install the application visuals for a given palette.
pub fn install_visuals(ctx: &Context, p: &LuxPalette, scale: f32) {
    let mut visuals = Visuals::dark();
    visuals.panel_fill = p.panel;
    visuals.window_fill = p.panel;
    visuals.extreme_bg_color = p.bg_deep;
    visuals.faint_bg_color = p.bg;

    visuals.widgets.inactive.bg_fill = p.card;
    visuals.widgets.inactive.fg_stroke = Stroke::new(1.0_f32, p.text);
    visuals.widgets.inactive.weak_bg_fill = p.card;

    visuals.widgets.hovered.bg_fill = p.card_hover;
    visuals.widgets.hovered.fg_stroke = Stroke::new(1.2_f32, p.text);

    visuals.widgets.active.bg_fill = p.accent_soft;
    visuals.widgets.active.fg_stroke = Stroke::new(1.2_f32, Color32::WHITE);

    visuals.widgets.open.bg_fill = p.accent_soft;
    visuals.widgets.open.fg_stroke = Stroke::new(1.2_f32, Color32::WHITE);

    visuals.selection.bg_fill = p.accent_soft;
    visuals.selection.stroke = Stroke::new(1.0_f32, p.accent);
    visuals.hyperlink_color = p.accent;
    visuals.text_cursor.stroke = Stroke::new(1.5_f32, p.accent);

    visuals.window_stroke = Stroke::new(1.0_f32, p.card_hover);
    visuals.window_corner_radius = 12.0.into();
    visuals.popup_shadow = egui::epaint::Shadow {
        offset: [0, 8],
        blur: 24,
        spread: 0,
        color: Color32::from_black_alpha(140),
    };
    visuals.override_text_color = Some(p.text);

    ctx.set_visuals(visuals);

    // Typography.
    let mut style = (*ctx.style()).clone();
    style.spacing.item_spacing = egui::vec2(10.0, 10.0);
    style.spacing.button_padding = egui::vec2(14.0, 8.0);
    style.spacing.interact_size.y = 34.0;
    style.text_styles.insert(
        TextStyle::Heading,
        egui::FontId::new(26.0 * scale, egui::FontFamily::Proportional),
    );
    style.text_styles.insert(
        TextStyle::Body,
        egui::FontId::new(15.0 * scale, egui::FontFamily::Proportional),
    );
    style.text_styles.insert(
        TextStyle::Button,
        egui::FontId::new(15.0 * scale, egui::FontFamily::Proportional),
    );
    ctx.set_style(style);

    // Rounded corners for widgets.
    ctx.style_mut(|s| {
        for w in [
            &mut s.visuals.widgets.noninteractive,
            &mut s.visuals.widgets.inactive,
            &mut s.visuals.widgets.hovered,
            &mut s.visuals.widgets.active,
            &mut s.visuals.widgets.open,
        ] {
            w.corner_radius = 8.0.into();
        }
        s.visuals.slider_trailing_fill = true;
    });
}

pub fn lerp(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    Color32::from_rgba_unmultiplied(
        (a.r() as f32 + (b.r() as f32 - a.r() as f32) * t) as u8,
        (a.g() as f32 + (b.g() as f32 - a.g() as f32) * t) as u8,
        (a.b() as f32 + (b.b() as f32 - a.b() as f32) * t) as u8,
        (a.a() as f32 + (b.a() as f32 - a.a() as f32) * t) as u8,
    )
}

/// Smoothly pulse between accent colors for "live" elements.
pub fn pulse(ctx: &Context, p: &LuxPalette) -> Color32 {
    let t = ctx.input(|i| i.time as f32 * 0.5);
    let s = (t.sin() * 0.5 + 0.5) as f32;
    lerp(p.accent, p.accent2, s)
}

pub fn rgba(color: Color32, alpha: f32) -> Rgba {
    let c = color.to_array();
    Rgba::from_rgba_unmultiplied(
        c[0] as f32 / 255.0,
        c[1] as f32 / 255.0,
        c[2] as f32 / 255.0,
        alpha,
    )
}

/// Draw a rounded rectangle filled with a linear gradient between two colors.
/// `vertical` picks a top->bottom vs left->right gradient.
pub fn gradient_rect(
    painter: &egui::Painter,
    rect: egui::Rect,
    rounding: f32,
    from: Color32,
    to: Color32,
    vertical: bool,
) {
    let rounding = rounding.clamp(0.0, rect.width().min(rect.height()) * 0.5);
    let (min, max) = (rect.min, rect.max);
    let cx0 = min.x + rounding;
    let cy0 = min.y + rounding;
    let cx1 = max.x - rounding;
    let cy1 = max.y - rounding;

    // Sample the rounded-corner outline clockwise (4 arcs, 5 segments each).
    let seg = 5;
    let mut pts: Vec<egui::Pos2> = Vec::new();
    let corners = [
        (egui::pos2(cx0, cy0), std::f32::consts::PI, std::f32::consts::PI * 0.5), // top-left: left -> top
        (egui::pos2(cx1, cy0), std::f32::consts::TAU * 0.75, std::f32::consts::PI * 0.5), // top-right: top -> right
        (egui::pos2(cx1, cy1), 0.0, std::f32::consts::PI * 0.5), // bottom-right: right -> bottom
        (egui::pos2(cx0, cy1), std::f32::consts::PI * 0.5, std::f32::consts::PI * 0.5), // bottom-left: bottom -> left
    ];
    for (c, a0, da) in corners {
        for i in 0..seg {
            let a = a0 + da * (i as f32 / seg as f32);
            pts.push(egui::pos2(c.x + a.cos() * rounding, c.y + a.sin() * rounding));
        }
    }

    let color = |p: egui::Pos2| -> Color32 {
        let t = if vertical {
            ((p.y - min.y) / (max.y - min.y).max(1.0)).clamp(0.0, 1.0)
        } else {
            ((p.x - min.x) / (max.x - min.x).max(1.0)).clamp(0.0, 1.0)
        };
        lerp(from, to, t)
    };

    let mut mesh = egui::Mesh::default();
    let center = rect.center();
    mesh.colored_vertex(center, color(center));
    let center_idx = mesh.vertices.len() - 1;
    let outline: Vec<usize> = pts
        .iter()
        .map(|p| {
            mesh.colored_vertex(*p, color(*p));
            mesh.vertices.len() - 1
        })
        .collect();
    for i in 0..outline.len() {
        let j = (i + 1) % outline.len();
        mesh.add_triangle(center_idx as u32, outline[i] as u32, outline[j] as u32);
    }
    painter.add(egui::Shape::mesh(mesh));
}

/// Soft ambient glow around a rounded rect, drawn beneath content.
pub fn glow(painter: &egui::Painter, rect: egui::Rect, color: Color32, radius: f32) {
    const LAYERS: usize = 5;
    for i in 0..LAYERS {
        let t = i as f32 / (LAYERS as f32 - 1.0); // 0 outermost
        let grow = radius * (1.0 - t);
        let alpha = (26.0 * (1.0 - t)) as u8;
        let r = rect.expand(grow);
        painter.rect_filled(
            r,
            (8.0 + grow).max(0.0),
            Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), alpha),
        );
    }
}
