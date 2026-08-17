use std::sync::Arc;

use egui::{
    Color32, Context, FontData, FontDefinitions, FontFamily, FontId, Painter, Pos2, Rect, Rgba,
    Shape, Stroke, TextStyle, Vec2, Visuals,
};

// --------------------------------------------------------------------------
// Palette
// --------------------------------------------------------------------------

/// "OBSIDIAN TERMINAL" palette: a cold-war aerospace console.
///
/// Deep obsidian blue-black canvas. One "heat" accent (ember) used for brand,
/// active nav and the primary action, and one "system" accent (ice cyan) for
/// links, loading and selection. Everything else stays quiet so the accent
/// moments land.
#[derive(Clone, Copy)]
pub struct LuxPalette {
    pub bg: Color32,
    pub bg_deep: Color32,
    pub panel: Color32,
    pub card: Color32,
    pub card_hover: Color32,
    /// Crisp 1px hairline used on all surfaces.
    pub edge: Color32,
    /// Primary "heat" accent (ember).
    pub accent: Color32,
    /// Muted ember for fills / borders (low-contrast accent).
    pub accent_soft: Color32,
    /// Secondary "system" accent (ice cyan).
    pub accent2: Color32,
    pub text: Color32,
    pub text_dim: Color32,
    pub danger: Color32,
    pub success: Color32,
    pub warn: Color32,
}

pub const ONYX: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(10, 13, 19),
    bg_deep: Color32::from_rgb(7, 9, 14),
    panel: Color32::from_rgb(16, 20, 29),
    card: Color32::from_rgb(20, 26, 38),
    card_hover: Color32::from_rgb(27, 34, 49),
    edge: Color32::from_rgb(31, 40, 58),
    accent: Color32::from_rgb(255, 180, 84), // ember
    accent_soft: Color32::from_rgb(112, 84, 50), // muted ember
    accent2: Color32::from_rgb(79, 208, 255), // ice cyan
    text: Color32::from_rgb(233, 238, 247),
    text_dim: Color32::from_rgb(122, 133, 152),
    danger: Color32::from_rgb(255, 92, 92),
    success: Color32::from_rgb(61, 220, 132),
    warn: Color32::from_rgb(255, 202, 102),
};

/// Cool-shifted variant: ice cyan becomes the primary accent.
pub const AURORA: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(8, 12, 18),
    bg_deep: Color32::from_rgb(5, 8, 12),
    panel: Color32::from_rgb(14, 20, 30),
    card: Color32::from_rgb(18, 26, 38),
    card_hover: Color32::from_rgb(24, 34, 49),
    edge: Color32::from_rgb(28, 40, 58),
    accent: Color32::from_rgb(79, 208, 255), // ice cyan
    accent_soft: Color32::from_rgb(42, 104, 138), // muted cyan
    accent2: Color32::from_rgb(255, 180, 84), // ember
    text: Color32::from_rgb(233, 239, 247),
    text_dim: Color32::from_rgb(118, 133, 152),
    danger: Color32::from_rgb(255, 92, 92),
    success: Color32::from_rgb(61, 220, 132),
    warn: Color32::from_rgb(255, 202, 102),
};

/// Warm-shifted variant: ember stays primary, cyan is a cooler secondary.
pub const OBSIDIAN: LuxPalette = LuxPalette {
    bg: Color32::from_rgb(12, 11, 16),
    bg_deep: Color32::from_rgb(8, 7, 11),
    panel: Color32::from_rgb(19, 18, 26),
    card: Color32::from_rgb(24, 22, 33),
    card_hover: Color32::from_rgb(31, 28, 43),
    edge: Color32::from_rgb(40, 36, 56),
    accent: Color32::from_rgb(255, 176, 82), // ember
    accent_soft: Color32::from_rgb(116, 84, 50),
    accent2: Color32::from_rgb(79, 208, 255), // ice cyan
    text: Color32::from_rgb(242, 237, 245),
    text_dim: Color32::from_rgb(132, 125, 144),
    danger: Color32::from_rgb(255, 92, 92),
    success: Color32::from_rgb(61, 220, 132),
    warn: Color32::from_rgb(255, 202, 102),
};

pub fn palette_for(name: &str) -> &'static LuxPalette {
    match name {
        "aurora" => &AURORA,
        "obsidian" => &OBSIDIAN,
        _ => &ONYX,
    }
}

// --------------------------------------------------------------------------
// Fonts
// --------------------------------------------------------------------------

const CHAKRA_BOLD: &[u8] = include_bytes!("../../assets/fonts/ChakraPetch-Bold.ttf");
const CHAKRA_SEMIBOLD: &[u8] = include_bytes!("../../assets/fonts/ChakraPetch-SemiBold.ttf");
const BARLOW_REGULAR: &[u8] = include_bytes!("../../assets/fonts/Barlow-Regular.ttf");
const BARLOW_MEDIUM: &[u8] = include_bytes!("../../assets/fonts/Barlow-Medium.ttf");
const BARLOW_SEMIBOLD: &[u8] = include_bytes!("../../assets/fonts/Barlow-SemiBold.ttf");
const PLEXMONO_REGULAR: &[u8] = include_bytes!("../../assets/fonts/IBMPlexMono-Regular.ttf");
const PLEXMONO_MEDIUM: &[u8] = include_bytes!("../../assets/fonts/IBMPlexMono-Medium.ttf");
const PLEXMONO_SEMIBOLD: &[u8] = include_bytes!("../../assets/fonts/IBMPlexMono-SemiBold.ttf");

/// Install the bundled typefaces. Call once at startup.
pub fn install_fonts(ctx: &Context) {
    let mut fonts = FontDefinitions::default();
    fonts.font_data.insert(
        "chakra_bold".into(),
        Arc::new(FontData::from_static(CHAKRA_BOLD)),
    );
    fonts.font_data.insert(
        "chakra_semibold".into(),
        Arc::new(FontData::from_static(CHAKRA_SEMIBOLD)),
    );
    fonts.font_data.insert(
        "barlow_regular".into(),
        Arc::new(FontData::from_static(BARLOW_REGULAR)),
    );
    fonts.font_data.insert(
        "barlow_medium".into(),
        Arc::new(FontData::from_static(BARLOW_MEDIUM)),
    );
    fonts.font_data.insert(
        "barlow_semibold".into(),
        Arc::new(FontData::from_static(BARLOW_SEMIBOLD)),
    );
    fonts.font_data.insert(
        "plexmono_regular".into(),
        Arc::new(FontData::from_static(PLEXMONO_REGULAR)),
    );
    fonts.font_data.insert(
        "plexmono_medium".into(),
        Arc::new(FontData::from_static(PLEXMONO_MEDIUM)),
    );
    fonts.font_data.insert(
        "plexmono_semibold".into(),
        Arc::new(FontData::from_static(PLEXMONO_SEMIBOLD)),
    );

    let latin_fb: Vec<String> =
        ["Ubuntu-Light", "NotoEmoji-Regular", "emoji-icon-font"].map(String::from).to_vec();
    let mono_fb: Vec<String> =
        ["Hack", "NotoEmoji-Regular", "emoji-icon-font"].map(String::from).to_vec();

    let mut display: Vec<String> = vec!["chakra_bold".into(), "chakra_semibold".into()];
    display.extend(latin_fb.iter().cloned());
    let mut display_semi: Vec<String> = vec!["chakra_semibold".into(), "chakra_bold".into()];
    display_semi.extend(latin_fb.iter().cloned());

    let mut body: Vec<String> = vec![
        "barlow_regular".into(),
        "barlow_medium".into(),
        "barlow_semibold".into(),
    ];
    body.extend(latin_fb.iter().cloned());
    let mut body_medium: Vec<String> = vec!["barlow_medium".into(), "barlow_semibold".into()];
    body_medium.extend(latin_fb.iter().cloned());
    let mut body_semi: Vec<String> = vec!["barlow_semibold".into(), "barlow_medium".into()];
    body_semi.extend(latin_fb.iter().cloned());

    let mut mono: Vec<String> = vec![
        "plexmono_regular".into(),
        "plexmono_medium".into(),
        "plexmono_semibold".into(),
    ];
    mono.extend(mono_fb.iter().cloned());
    let mut mono_medium: Vec<String> = vec!["plexmono_medium".into(), "plexmono_semibold".into()];
    mono_medium.extend(mono_fb.iter().cloned());
    let mut mono_semi: Vec<String> = vec!["plexmono_semibold".into(), "plexmono_medium".into()];
    mono_semi.extend(mono_fb.iter().cloned());

    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Display")), display);
    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Display-Semi")), display_semi);
    fonts.families.insert(FontFamily::Proportional, body);
    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Body-Medium")), body_medium);
    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Body-Semi")), body_semi);
    fonts.families.insert(FontFamily::Monospace, mono);
    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Mono-Medium")), mono_medium);
    fonts
        .families
        .insert(FontFamily::Name(Arc::from("Mono-Semi")), mono_semi);

    ctx.set_fonts(fonts);
}

// Convenience font helpers (size, family).
pub fn display(px: f32) -> FontId {
    FontId::new(px, FontFamily::Name(Arc::from("Display")))
}
pub fn display_sb(px: f32) -> FontId {
    FontId::new(px, FontFamily::Name(Arc::from("Display-Semi")))
}
pub fn body(px: f32) -> FontId {
    FontId::new(px, FontFamily::Proportional)
}
pub fn body_med(px: f32) -> FontId {
    FontId::new(px, FontFamily::Name(Arc::from("Body-Medium")))
}
pub fn body_sb(px: f32) -> FontId {
    FontId::new(px, FontFamily::Name(Arc::from("Body-Semi")))
}
pub fn mono(px: f32) -> FontId {
    FontId::new(px, FontFamily::Monospace)
}
pub fn mono_sb(px: f32) -> FontId {
    FontId::new(px, FontFamily::Name(Arc::from("Mono-Semi")))
}

// --------------------------------------------------------------------------
// Visuals
// --------------------------------------------------------------------------

pub fn install_visuals(ctx: &Context, p: &LuxPalette, scale: f32) {
    let mut v = Visuals::dark();
    v.panel_fill = p.panel;
    v.window_fill = p.panel;
    v.extreme_bg_color = p.bg_deep;
    v.faint_bg_color = p.bg;

    v.widgets.inactive.bg_fill = p.card;
    v.widgets.inactive.bg_stroke = Stroke::new(1.0_f32, p.edge);
    v.widgets.inactive.fg_stroke = Stroke::new(1.0_f32, p.text);
    v.widgets.inactive.weak_bg_fill = Color32::TRANSPARENT;

    v.widgets.hovered.bg_fill = p.card_hover;
    v.widgets.hovered.bg_stroke = Stroke::new(1.0_f32, p.accent_soft);
    v.widgets.hovered.fg_stroke = Stroke::new(1.0_f32, p.text);

    v.widgets.active.bg_fill = p.accent_soft;
    v.widgets.active.bg_stroke = Stroke::new(1.5_f32, p.accent);
    v.widgets.active.fg_stroke = Stroke::new(1.0_f32, Color32::WHITE);

    v.widgets.open.bg_fill = p.card_hover;
    v.widgets.open.bg_stroke = Stroke::new(1.0_f32, p.edge);
    v.widgets.open.fg_stroke = Stroke::new(1.0_f32, p.text);

    v.selection.bg_fill = p.accent_soft;
    v.selection.stroke = Stroke::new(1.0_f32, p.accent);
    v.hyperlink_color = p.accent2;
    v.text_cursor.stroke = Stroke::new(1.5_f32, p.accent);

    v.window_stroke = Stroke::new(1.0_f32, p.edge);
    v.window_corner_radius = 10.0.into();
    v.popup_shadow = egui::epaint::Shadow {
        offset: [0, 10],
        blur: 30,
        spread: 0,
        color: Color32::from_black_alpha(180),
    };
    v.override_text_color = Some(p.text);
    ctx.set_visuals(v);

    let mut s = (*ctx.style()).clone();
    s.spacing.item_spacing = Vec2::new(10.0, 8.0);
    s.spacing.button_padding = Vec2::new(14.0, 8.0);
    s.spacing.interact_size.y = 34.0;
    s.text_styles.insert(TextStyle::Heading, display(24.0 * scale));
    s.text_styles.insert(TextStyle::Body, body(15.0 * scale));
    s.text_styles.insert(TextStyle::Button, body(14.0 * scale));
    s.text_styles.insert(TextStyle::Small, body(12.0 * scale));
    s.text_styles.insert(TextStyle::Monospace, mono(13.0 * scale));
    ctx.set_style(s);

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

// --------------------------------------------------------------------------
// Color / animation helpers
// --------------------------------------------------------------------------

pub fn lerp(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    Color32::from_rgba_unmultiplied(
        (a.r() as f32 + (b.r() as f32 - a.r() as f32) * t) as u8,
        (a.g() as f32 + (b.g() as f32 - a.g() as f32) * t) as u8,
        (a.b() as f32 + (b.b() as f32 - a.b() as f32) * t) as u8,
        (a.a() as f32 + (b.a() as f32 - a.a() as f32) * t) as u8,
    )
}

pub fn mix(a: Color32, b: Color32, t: f32) -> Color32 {
    lerp(a, b, t)
}

/// Animate a boolean toward 0..1 for smooth hover/state transitions.
pub fn anim(ctx: &Context, id: egui::Id, target: bool, speed: f32) -> f32 {
    ctx.animate_value_with_time(id.with("anim"), if target { 1.0 } else { 0.0 }, speed)
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

/// Smoothly pulse between accent and accent2 (used for "live" dots).
pub fn pulse(ctx: &Context, p: &LuxPalette) -> Color32 {
    let t = ctx.input(|i| i.time as f32 * 0.5);
    let s = (t.sin() * 0.5 + 0.5) as f32;
    lerp(p.accent, p.accent2, s)
}

// --------------------------------------------------------------------------
// Custom drawing
// --------------------------------------------------------------------------

/// Draw a rounded rectangle with a top->bottom linear gradient.
pub fn gradient_rect(
    painter: &Painter,
    rect: Rect,
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

    let seg = 6;
    let mut pts: Vec<Pos2> = Vec::new();
    let corners = [
        (Pos2::new(cx0, cy0), std::f32::consts::PI, std::f32::consts::PI * 0.5),
        (
            Pos2::new(cx1, cy0),
            std::f32::consts::TAU * 0.75,
            std::f32::consts::PI * 0.5,
        ),
        (Pos2::new(cx1, cy1), 0.0, std::f32::consts::PI * 0.5),
        (
            Pos2::new(cx0, cy1),
            std::f32::consts::PI * 0.5,
            std::f32::consts::PI * 0.5,
        ),
    ];
    for (c, a0, da) in corners {
        for i in 0..seg {
            let a = a0 + da * (i as f32 / seg as f32);
            pts.push(Pos2::new(c.x + a.cos() * rounding, c.y + a.sin() * rounding));
        }
    }

    let color = |p: Pos2| -> Color32 {
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
    painter.add(Shape::mesh(mesh));
}

/// A soft radial color wash (center opaque -> edges transparent).
pub fn radial_fill(painter: &Painter, rect: Rect, color: Color32, max_alpha: f32) {
    let c = rect.center();
    let r = rect.width().min(rect.height()) * 0.5;
    let alpha = (max_alpha.clamp(0.0, 1.0) * 255.0) as u8;
    let n = 48;
    let mut mesh = egui::Mesh::default();
    mesh.colored_vertex(
        c,
        Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), alpha),
    );
    for i in 0..n {
        let a = std::f32::consts::TAU * i as f32 / n as f32;
        let p = c + Vec2::new(a.cos(), a.sin()) * r;
        mesh.colored_vertex(p, Color32::TRANSPARENT);
    }
    for i in 0..n {
        mesh.add_triangle(0, 1 + i, 1 + ((i + 1) % n));
    }
    painter.add(Shape::mesh(mesh));
}

/// Layered ambient glow around a rounded rect (drawn beneath content).
pub fn glow(painter: &Painter, rect: Rect, color: Color32, radius: f32) {
    const LAYERS: usize = 5;
    for i in 0..LAYERS {
        let t = i as f32 / (LAYERS as f32 - 1.0); // 0 outermost
        let grow = radius * (1.0 - t);
        // Quadratic falloff keeps the glow hugging the edge instead of
        // blooming far out when the color is bright.
        let alpha = (12.0 * (1.0 - t) * (1.0 - t)) as u8;
        let r = rect.expand(grow);
        painter.rect_filled(
            r,
            (4.0 + grow).max(0.0),
            Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), alpha),
        );
    }
}

/// Fill the background and layer the corner washes + a soft central bloom.
pub fn atmosphere(painter: &Painter, rect: Rect, p: &LuxPalette) {
    painter.rect_filled(rect, 0.0, p.bg);
    radial_fill(
        painter,
        Rect::from_center_size(
            Pos2::new(rect.left() + 140.0, rect.top() + 90.0),
            Vec2::new(rect.width() * 0.7, rect.width() * 0.7),
        ),
        p.accent,
        0.20,
    );
    radial_fill(
        painter,
        Rect::from_center_size(
            Pos2::new(rect.right() - 160.0, rect.bottom() - 110.0),
            Vec2::new(rect.width() * 0.7, rect.width() * 0.7),
        ),
        p.accent2,
        0.18,
    );
    // A soft central bloom to give the glass something to pick up.
    radial_fill(
        painter,
        Rect::from_center_size(
            Pos2::new(rect.center().x, rect.center().y),
            Vec2::new(rect.width() * 0.4, rect.width() * 0.4),
        ),
        p.accent2,
        0.10,
    );
}