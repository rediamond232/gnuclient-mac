use eframe::egui::{self, Layout, Ui};
use egui::TextEdit;

use crate::app::LauncherApp;
use crate::minecraft::instance::GameInstanceConfig;
use crate::modrinth::client::{self, ModrinthProject};
use crate::modrinth::types::ModrinthType;
use crate::state::{InstallOutcome, SearchResult};
use crate::ui::widgets;
use crate::util::theme;

pub fn show(
    app: &mut LauncherApp,
    ui: &mut Ui,
    mtype: ModrinthType,
    inst: Option<&GameInstanceConfig>,
) {
    let pal = theme::palette_for(&app.config.theme);

    ui.add_space(2.0);
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.label(
                egui::RichText::new(mtype.title())
                    .font(theme::display(24.0))
                    .color(pal.text),
            );
            ui.label(
                egui::RichText::new(format!(
                    "Browse & manage {} for your instance.",
                    mtype.title().to_lowercase()
                ))
                .font(theme::body(13.0))
                .color(pal.text_dim),
            );
        });
        ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
            ui.add(
                TextEdit::singleline(&mut app.search_query)
                    .hint_text(format!("Search {}...", mtype.title()))
                    .desired_width(280.0)
                    .font(theme::body(14.0)),
            );
            if widgets::ghost_button(ui, "Search", pal, 96.0) {
                trigger_search(app, mtype);
            }
        });
    });
    ui.add_space(12.0);

    // Auto-search on first paint for this tab.
    let needs_initial = !app
        .state
        .state(mtype)
        .map(|s| s.loaded_once)
        .unwrap_or(false);
    if needs_initial && !app.state.state(mtype).map(|s| s.loading).unwrap_or(false) {
        trigger_search(app, mtype);
    }

    // Installed section.
    widgets::section_title(ui, pal, "Installed");
    let installed = match mtype {
        ModrinthType::Mod => inst.map(|i| i.installed_mods.clone()).unwrap_or_default(),
        ModrinthType::Shader => inst
            .map(|i| i.installed_shaders.clone())
            .unwrap_or_default(),
        ModrinthType::ResourcePack => inst.map(|i| i.installed_packs.clone()).unwrap_or_default(),
    };
    show_installed(app, ui, &installed, pal);
    ui.add_space(16.0);

    // Browse section.
    widgets::section_title(ui, pal, "Browse Modrinth");
    egui::ScrollArea::vertical()
        .auto_shrink([false, false])
        .max_height(ui.available_height().max(200.0))
        .show(ui, |ui| {
            let (loading, error, results) = {
                let state = app.state.state(mtype);
                (
                    state.map(|s| s.loading).unwrap_or(false),
                    state.and_then(|s| s.error.clone()),
                    state.map(|s| s.results.clone()).unwrap_or_default(),
                )
            };
            if loading {
                ui.spinner();
                ui.label(
                    egui::RichText::new("Searching Modrinth...")
                        .font(theme::body(13.0))
                        .color(pal.text_dim),
                );
            } else if let Some(err) = error {
                ui.label(
                    egui::RichText::new(err)
                        .font(theme::body(13.0))
                        .color(pal.danger),
                );
            } else if results.is_empty() {
                ui.label(
                    egui::RichText::new("No projects found. Try a different search.")
                        .font(theme::body(13.0))
                        .color(pal.text_dim),
                );
            } else {
                for proj in &results {
                    project_row(app, ui, proj, mtype, inst, pal);
                }
            }
        });
}

fn trigger_search(app: &mut LauncherApp, mtype: ModrinthType) {
    let mtype_apistr = mtype.api_string();
    let loader = mtype.loader();
    let query = app.search_query.clone();
    let tx = app.state.channels.search_tx.clone();
    let rt = app.runtime.clone();
    {
        let s = app.state.state_mut(mtype);
        s.loading = true;
        s.error = None;
        s.query = query.clone();
    }
    rt.spawn(async move {
        let api = mtype_apistr.to_string();
        let ldr = loader.to_string();
        let result = tokio::task::spawn_blocking(move || {
            client::search(
                &query,
                &api,
                crate::minecraft::instance::MC_VERSION,
                &ldr,
                20,
            )
        })
        .await;
        let payload = match result {
            Ok(Ok(search)) => SearchResult::Ok(
                mtype,
                search.hits.into_iter().map(ModrinthProject::from).collect(),
            ),
            Ok(Err(e)) => SearchResult::Err(mtype, e.to_string()),
            Err(e) => SearchResult::Err(mtype, e.to_string()),
        };
        let _ = tx.send(payload);
    });
}

fn show_installed(
    app: &mut LauncherApp,
    ui: &mut Ui,
    items: &[crate::minecraft::instance::InstalledContent],
    pal: &crate::util::theme::LuxPalette,
) {
    if items.is_empty() {
        ui.label(
            egui::RichText::new("Nothing installed yet.")
                .font(theme::body(13.0))
                .color(pal.text_dim),
        );
        return;
    }
    for item in items {
        if let Some(u) = &item.icon_url {
            app.request_icon(u);
        }
    }
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        let mut to_remove: Option<String> = None;
        for item in items {
            ui.horizontal(|ui| {
                let tex = item.icon_url.as_deref().and_then(|u| app.icon_texture(u));
                widgets::icon_tile(ui, tex, &item.name, pal, 44.0);
                ui.vertical(|ui| {
                    ui.label(
                        egui::RichText::new(&item.name)
                            .font(theme::body_sb(15.0))
                            .color(pal.text),
                    );
                    ui.label(
                        egui::RichText::new(format!(
                            "v{}  ·  {}",
                            item.version, item.file_name
                        ))
                        .font(theme::mono(11.0))
                        .color(pal.text_dim),
                    );
                });
                ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                    if widgets::ghost_button(ui, "Remove", pal, 96.0) {
                        to_remove = Some(item.id.clone());
                    }
                });
            });
            widgets::divider(ui, pal);
        }
        if let Some(id) = to_remove {
            let data = app.config.data_dir.clone();
            if let Some(inst) = app.active_instance_mut() {
                let snapshot = inst.clone();
                let item = snapshot
                    .installed_mods
                    .iter()
                    .chain(snapshot.installed_shaders.iter())
                    .chain(snapshot.installed_packs.iter())
                    .find(|i| i.id == id)
                    .cloned();
                if let Some(item) = item {
                    crate::modrinth::install::remove_content(inst, &item, &data, true);
                }
                app.persist();
            }
        }
    });
    ui.add_space(6.0);
}

fn project_row(
    app: &mut LauncherApp,
    ui: &mut Ui,
    proj: &ModrinthProject,
    mtype: ModrinthType,
    inst: Option<&GameInstanceConfig>,
    pal: &crate::util::theme::LuxPalette,
) {
    if let Some(u) = &proj.icon_url {
        app.request_icon(u);
    }
    widgets::card(ui, pal, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            let tex = proj.icon_url.as_deref().and_then(|u| app.icon_texture(u));
            widgets::icon_tile(ui, tex, &proj.title, pal, 52.0);
            ui.vertical(|ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new(&proj.title)
                            .font(theme::body_sb(16.0))
                            .color(pal.text),
                    );
                    ui.add_space(10.0);
                    ui.label(
                        egui::RichText::new(format!("{} downloads", proj.downloads))
                            .font(theme::mono(11.0))
                            .color(pal.text_dim),
                    );
                });
                ui.label(
                    egui::RichText::new(&proj.description)
                        .font(theme::body(12.0))
                        .color(pal.text_dim),
                );
            });
            ui.with_layout(Layout::right_to_left(egui::Align::Center), |ui| {
                let installed = inst
                    .map(|i| match mtype {
                        ModrinthType::Mod => i.installed_mods.iter(),
                        ModrinthType::Shader => i.installed_shaders.iter(),
                        ModrinthType::ResourcePack => i.installed_packs.iter(),
                    })
                    .map(|mut list| list.any(|m| m.project_id.as_deref() == Some(&proj.id)))
                    .unwrap_or(false);
                let btn_label = if installed { "Installed" } else { "Install" };
                let from = if installed { pal.success } else { pal.accent };
                if widgets::primary_button(
                    ui,
                    btn_label,
                    from,
                    from,
                    egui::vec2(104.0, 36.0),
                ) && !installed
                {
                    install_project(app, proj, mtype);
                }
            });
        });
    });
    ui.add_space(6.0);
}

fn install_project(app: &mut LauncherApp, proj: &ModrinthProject, mtype: ModrinthType) {
    let game_version = crate::minecraft::instance::MC_VERSION.to_string();
    let loader = mtype.loader().to_string();
    let api = mtype.api_string().to_string();
    let tx = app.state.channels.install_tx.clone();
    let proj_clone = proj.clone();

    app.show_notice(
        format!("Resolving {}...", proj.title),
        crate::app::NoticeKind::Info,
    );

    let rt = app.runtime.clone();
    rt.spawn(async move {
        let mtype = mtype;
        let outcome = tokio::task::spawn_blocking(move || {
            match client::best_version(&proj_clone.id, &game_version, &loader, &api) {
                Ok(version) => InstallOutcome::Ready(proj_clone, version, mtype),
                Err(e) => InstallOutcome::Err(e.to_string()),
            }
        })
        .await;
        let _ = tx.send(match outcome {
            Ok(oc) => oc,
            Err(e) => InstallOutcome::Err(e.to_string()),
        });
    });
}