use eframe::egui::{self, Layout, Ui};
use egui::TextEdit;

use crate::app::LauncherApp;
use crate::minecraft::instance::GameInstanceConfig;
use crate::ui::widgets;
use crate::util::theme;

pub fn show(app: &mut LauncherApp, ui: &mut Ui, inst: Option<&GameInstanceConfig>) {
    let pal = theme::palette_for(&app.config.theme);

    ui.add_space(2.0);
    ui.label(
        egui::RichText::new("Settings")
            .font(theme::display(24.0))
            .color(pal.text),
    );
    ui.label(
        egui::RichText::new("Instances, JVM args and launcher preferences.")
            .font(theme::body(13.0))
            .color(pal.text_dim),
    );
    ui.add_space(16.0);

    // Instance selector.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "Instance");
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("Active instance")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
            let current = app.active_instance_id.clone();
            egui::ComboBox::from_id_salt("instance_sel")
                .selected_text(
                    app.config
                        .instance_by_id(&current)
                        .map(|i| i.name.clone())
                        .unwrap_or_default(),
                )
                .width(240.0)
                .show_ui(ui, |ui| {
                    let ids: Vec<String> =
                        app.config.instances.iter().map(|i| i.id.clone()).collect();
                    for id in ids {
                        let name = app
                            .config
                            .instance_by_id(&id)
                            .map(|i| i.name.clone())
                            .unwrap_or_default();
                        if ui
                            .selectable_label(app.active_instance_id == id, &name)
                            .clicked()
                        {
                            app.active_instance_id = id;
                            app.persist();
                        }
                    }
                });
        });
        // Create new instance.
        ui.add_space(6.0);
        let mut new_name = String::new();
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("New")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
            let resp = widgets::field(ui, &mut new_name, "Instance name…", 220.0);
            if resp.lost_focus() && !new_name.trim().is_empty() {
                let name = new_name.trim().to_string();
                let data = app.config.data_dir.clone();
                let new_inst = GameInstanceConfig::new(name, &data);
                app.config.upsert_instance(new_inst);
                app.active_instance_id = app
                    .config
                    .instances
                    .last()
                    .map(|i| i.id.clone())
                    .unwrap_or_default();
                app.persist();
            }
        });
    });
    ui.add_space(14.0);

    // JVM args editor.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "JVM Arguments");
        if let Some(inst) = inst {
            let mut jvm = inst.jvm_args.clone();
            let resp = ui.add(
                TextEdit::multiline(&mut jvm)
                    .desired_width(ui.available_width() - 20.0)
                    .desired_rows(4)
                    .font(egui::TextStyle::Monospace)
                    .hint_text("e.g. -Xmx2G -XX:+UseG1GC"),
            );
            if resp.changed() {
                if let Some(inst_mut) = app.active_instance_mut() {
                    inst_mut.jvm_args = jvm.clone();
                }
                app.persist();
            }
            ui.add_space(6.0);
            let valid = crate::minecraft::launch::validate_jvm_args(&jvm);
            match valid {
                Ok(_) => ui.label(
                    egui::RichText::new("✓ Valid arguments")
                        .font(theme::mono(12.0))
                        .color(pal.success),
                ),
                Err(e) => ui.label(
                    egui::RichText::new(format!("✕ {e}"))
                        .font(theme::mono(12.0))
                        .color(pal.danger),
                ),
            };
        } else {
            ui.label(
                egui::RichText::new("No active instance.")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
        }
    });
    ui.add_space(14.0);

    // GNUClient jar.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "GNUClient");
        if let Some(inst) = inst {
            if inst.has_gnuclient {
                ui.label(
                    egui::RichText::new("✓ GNUClient is installed in this instance.")
                        .font(theme::body(13.0))
                        .color(pal.success),
                );
                if let Some(jar) = &inst.gnuclient_jar {
                    ui.label(
                        egui::RichText::new(format!("Jar: {}", jar.display()))
                            .font(theme::mono(11.0))
                            .color(pal.text_dim),
                    );
                }
            } else {
                ui.label(
                    egui::RichText::new("GNUClient jar not installed. Select it to bundle.")
                        .font(theme::body(13.0))
                        .color(pal.warn),
                );
            }
            ui.add_space(6.0);
            if widgets::ghost_button(ui, "Select GNUClient Jar...", pal, 200.0) {
                select_gnuclient_jar(app);
            }
        }
    });
    ui.add_space(14.0);

    // General settings.
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        widgets::section_title(ui, pal, "General");
        // Theme.
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("Theme")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
            let themes = ["onyx", "aurora", "obsidian"];
            for t in themes {
                let selected = app.config.theme == t;
                if ui.selectable_label(selected, t.to_uppercase()).clicked() {
                    app.config.theme = t.to_string();
                    app.persist();
                }
            }
        });
        ui.add_space(6.0);
        // Concurrent downloads.
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("Max downloads")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
            let mut n = app.config.max_concurrent_downloads as f32;
            if ui
                .add(egui::Slider::new(&mut n, 1.0..=32.0).integer())
                .changed()
            {
                app.config.max_concurrent_downloads = n as usize;
                app.download_manager.set_max_concurrent(n as usize);
                app.persist();
            }
        });
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("Auto-install Java 8")
                    .font(theme::body(13.0))
                    .color(pal.text_dim),
            );
            let mut b = app.config.auto_install_java;
            if ui.checkbox(&mut b, "").changed() {
                app.config.auto_install_java = b;
                app.persist();
            }
        });
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(format!("Data dir: {}", app.config.data_dir.display()))
                    .font(theme::mono(11.0))
                    .color(pal.text_dim),
            );
            ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                if widgets::ghost_button(ui, "Open", pal, 88.0) {
                    open_dir(&app.config.data_dir);
                }
            });
        });
    });
}

fn select_gnuclient_jar(app: &mut LauncherApp) {
    if app.jar_pick_rx.is_some() {
        return;
    }
    let (tx, rx) = std::sync::mpsc::channel();
    app.jar_pick_rx = Some(rx);
    // Run the native file dialog off the UI thread so it can't freeze the launcher.
    // rfd drives its own event loop, so this is safe from a background thread.
    std::thread::spawn(move || {
        let picked = pick_jar_native().map(std::path::PathBuf::from);
        let _ = tx.send(picked);
    });
}

fn pick_jar_native() -> Option<String> {
    rfd::FileDialog::new()
        .set_title("Select GNUClient jar")
        .add_filter("Java Archive", &["jar"])
        .pick_file()
        .map(|p| p.to_string_lossy().into_owned())
}

fn open_dir(path: &std::path::Path) {
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg(path).spawn();
    }
    #[cfg(target_os = "linux")]
    {
        let _ = std::process::Command::new("xdg-open").arg(path).spawn();
    }
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("explorer").arg(path).spawn();
    }
}