# Watermark FPS Pipe Divider Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Separate HUD watermark brand and FPS with a piped gap: `GNU  |  120 FPS`.

**Architecture:** Three text draws in `HudRenderer.drawWatermark` when FPS is on; brand-only when off.

**Tech Stack:** Java, existing `UiKit.drawGlowText` / `UiFont.width` / `ClientTheme`.

---

### Task 1: Pipe divider in drawWatermark

**Files:**
- Modify: `src/main/java/gnu/client/ui/hud/HudRenderer.java`
- Spec: `docs/superpowers/specs/2026-08-08-watermark-fps-divider-design.md`

**Step 1:** Add `WATERMARK_DIVIDER_GAP` (~4f) next to existing watermark constants.

**Step 2:** In `drawWatermark`, when `showFps`:
1. Draw brand as today.
2. Advance `x + brandWidth + WATERMARK_DIVIDER_GAP`, draw `|` at quieter alpha (~0.55).
3. Advance by divider width + gap, draw `N FPS` (no leading space) at existing FPS alpha.

**Step 3:** When `!showFps`, leave brand-only (no pipe).

**Step 4:** Compile / run existing HUD tests if convenient (`HudArrayListRailTest`).

**Done when:** In-game watermark reads `GNU  |  <fps> FPS` with clear gaps; FPS toggle off shows only `GNU`.
