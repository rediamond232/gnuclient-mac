use eframe::egui::{self, Align2, Color32, RichText, Stroke};
use egui::Context;

use crate::app::{LauncherApp, NoticeKind};

pub fn show(app: &mut LauncherApp, ctx: &Context) {
    let Some(notice) = app.notice.clone() else {
        return;
    };
    let pal = crate::util::theme::palette_for(&app.config.theme);
    let color = match notice.kind {
        NoticeKind::Info => pal.accent,
        NoticeKind::Success => pal.success,
        NoticeKind::Error => pal.danger,
    };

    egui::Area::new("toast".into())
        .anchor(Align2::RIGHT_TOP, egui::vec2(-16.0, 16.0))
        .show(ctx, |ui| {
            let width = 320.0;
            let rect = ui
                .allocate_exact_size(egui::vec2(width, 52.0), egui::Sense::hover())
                .0;
            ui.painter()
                .rect_filled(rect, 10.0, Color32::from_rgba_unmultiplied(25, 25, 40, 235));
            ui.painter().rect_stroke(
                rect,
                10.0,
                Stroke::new(2.0_f32, color),
                egui::StrokeKind::Inside,
            );
            ui.painter().text(
                egui::pos2(rect.left() + 16.0, rect.center().y),
                Align2::LEFT_CENTER,
                &notice.text,
                egui::FontId::proportional(14.0),
                Color32::WHITE,
            );

            // Auto-dismiss after a few seconds.
            let now = ui.input(|i| i.time as f32);
            if app.notice_show_time.map(|t| now - t > 4.0).unwrap_or(false) {
                app.clear_notice();
            }
            let _ = RichText::default();
        });
}
