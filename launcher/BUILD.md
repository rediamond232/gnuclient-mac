# Building the GNUClient launcher

The launcher is a Rust/egui desktop app that runs on macOS (Intel + Apple
Silicon), Linux, and Windows. Each OS builds its **own native binary**; there is
no cross-compilation workflow. This document covers the native build steps and
the system packages each platform needs.

All commands assume `cargo`/`rustup` are installed and your shell is in this
`launcher/` directory unless noted.

## General

- Java 8 is **not bundled** into the binary. On first launch the launcher
  downloads a Temurin 8 JRE from Adoptium into the launcher data dir's
  `runtime/` folder, so no separate JRE install is required on any platform.
- Forge 1.8.9 ships x86_64-only LWJGL natives, so the launcher provisions an
  **x86_64** JRE on macOS (under Rosetta on Apple Silicon) and on Windows.
  Linux uses the host architecture.
- The data dir comes from `dirs::data_dir()/gnuclient-launcher`:
  - macOS: `~/Library/Application Support/gnuclient-launcher`
  - Linux: `~/.local/share/gnuclient-launcher`
  - Windows: `%APPDATA%\gnuclient-launcher`

## macOS

This is the primary development target and the only platform built on this
repo's current dev machine.

```sh
cargo build --release
```

The binary is `target/release/gnuclient-launcher`. To run without the Finder
dock icon (headless development) the release build already suppresses the
terminal window on Windows only; on macOS just run the binary.

### Apple Silicon note

The binary is built as `aarch64-apple-darwin`. Forge 1.8.9 needs an x86_64 JRE,
so on first launch the launcher provisions an x86_64 Temurin 8 JRE and runs it
under Rosetta. Two macOS-specific flags in the launcher already handle this:

- `-XstartOnFirstThread` is added **only** on Intel macOS; it is omitted on
  Apple Silicon (it makes `glCheckFramebufferStatus` return 0 under Rosetta and
  crash at launch).
- Zulu JREs are avoided on macOS (their "caulk" allocator SIGILL-crashes under
  Rosetta); the launcher fetches Temurin (Adoptium) instead.

## Linux

Install the system build dependencies first. For Debian/Ubuntu:

```sh
sudo apt update
sudo apt install -y \
  build-essential pkg-config cmake \
  libssl-dev \
  libgtk-3-dev \
  libgl1-mesa-dev \
  libfontconfig1-dev \
  libxcb-render0-dev libxcb-shape0-dev libxcb-xfixes0-dev \
  libxkbcommon-dev \
  libwayland-dev
```

- `libgtk-3-dev` is required by `rfd` (native file dialogs use the GTK3 backend
  on Linux).
- The `libxcb-*`, `libxkbcommon`, and `libwayland` packages are required by
  `eframe`/`egui` (`x11` and `wayland` features are enabled in `Cargo.toml`).
- `libgl1-mesa-dev` provides the OpenGL loader used by the `glow` renderer.
- `libssl-dev` is required by `reqwest`/`rustls` for HTTPS downloads (modrinth,
  Mojang, Adoptium).

Then build:

```sh
cargo build --release
```

The binary is `target/release/gnuclient-launcher`. Run it with:

```sh
./target/release/gnuclient-launcher
```

If you only need an X11/Wayland subset you can drop the unused feature in
`Cargo.toml` (`egui`/`eframe` `x11` or `wayland`) to reduce system deps, but
both are enabled by default for the widest compatibility.

## Windows

Build with the MSVC toolchain (the default `x86_64-pc-windows-msvc` host) and
the Visual Studio Build Tools / Windows SDK installed.

```sh
cargo build --release
```

The binary is `target/release/gnuclient-launcher.exe`.

Notes:
- `main.rs` includes `#![cfg_attr(not(debug_assertions), windows_subsystem =
  "windows")]`, so the release build runs as a GUI app with no console window.
- `rfd` uses the native Windows file dialog.
- On Windows the launcher provisions an **x86_64** Temurin 8 JRE and uses `;`
  as the classpath separator (handled automatically).

## Deploy / distribute

Copy the built binary per OS. There is no bundler; the launcher downloads its
own JRE at first run, so a single executable is enough:

| Platform | Binary | Typical location |
|----------|--------|------------------|
| macOS    | `gnuclient-launcher` | `dist/gnuclient-launcher` |
| Linux    | `gnuclient-launcher` | e.g. `dist/gnuclient-launcher-linux` |
| Windows  | `gnuclient-launcher.exe` | e.g. `dist/gnuclient-launcher.exe` |

## Smoke test after building

Launch the app, add an account, create/select the instance, and hit Play.
Verify:

1. A Temurin 8 JRE appears in `runtime/` in the data dir (first launch only).
2. The game reaches the Forge main menu (not a crash/backtrace).
3. GNUClient shows up in the Forge mod list (its `mcmod.info` is auto-discovered
   from the instance's `minecraft/mods/` folder).

If the game crashes, check the instance's `minecraft/logs/latest.log` and
`minecraft/crash-reports/`.