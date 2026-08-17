use eframe::egui::{self, Align2, Color32, Rect, Sense, Stroke};
use egui::Context;

use crate::app::{LauncherApp, NoticeKind};
use crate::util::theme;

pub fn show(app: &mut LauncherApp, ctx: &Context) {
    let Some(notice) = app.notice.clone() else {
        return;
    };
    let pal = theme::palette_for(&app.config.theme);
    let color = match notice.kind {
        NoticeKind::Info => pal.accent,
        NoticeKind::Success => pal.success,
        NoticeKind::Error => pal.danger,
    };

    egui::Area::new("toast".into())
        .anchor(Align2::RIGHT_TOP, egui::vec2(-16.0, 16.0))
        .order(egui::Order::Foreground)
        .show(ctx, |ui| {
            let width = 340.0;
            let rect = ui
                .allocate_exact_size(egui::vec2(width, 56.0), Sense::hover())
                .0;
            let painter = ui.painter();
            painter.rect_filled(rect, 11.0, pal.bg_deep);
            painter.rect_stroke(
                rect,
                11.0,
                Stroke::new(1.0_f32, pal.edge),
                egui::StrokeKind::Inside,
            );
            // Accent bar on the leading edge.
            painter.rect_filled(
                Rect::from_min_max(
                    egui::pos2(rect.left() + 10.0, rect.top() + 12.0),
                    egui::pos2(rect.left() + 13.0, rect.bottom() - 12.0),
                ),
                1.5,
                color,
            );
            painter.text(
                egui::pos2(rect.left() + 26.0, rect.center().y),
                Align2::LEFT_CENTER,
                &notice.text,
                theme::body_sb(14.0),
                pal.text,
            );

            // Auto-dismiss after a few seconds.
            let now = ui.input(|i| i.time as f32);
            if app.notice_show_time.map(|t| now - t > 4.0).unwrap_or(false) {
                app.clear_notice();
            }
            let _ = Color32::TRANSPARENT;
        });
}