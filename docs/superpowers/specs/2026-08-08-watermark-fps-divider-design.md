# Watermark FPS pipe divider

**Date:** 2026-08-08  
**Status:** approved  
**Ship path:** `HudRenderer.drawWatermark`

## Problem

HUD watermark draws client brand (`GNU`) and FPS with only a single space between them, so the two labels read as one cramped string.

## Goals

- Clear visual separation between brand and FPS when FPS is enabled.
- Pipe divider: `GNU  |  120 FPS` with pixel gaps on both sides of `|`.
- Keep existing brand / FPS theming; pipe slightly quieter than FPS.
- When FPS is off, watermark remains brand-only (no pipe).

## Non-goals

- New HUD settings or config keys.
- Changing watermark font size, margin, or position.
- ArrayList / TargetHUD / toast layout changes.

## Decision

Draw three segments when FPS is on: brand → gap → `|` → gap → `N FPS`. Use a small `WATERMARK_DIVIDER_GAP` constant (≈4px) instead of relying on a leading space in the FPS string.

## Implementation notes

- File: `src/main/java/gnu/client/ui/hud/HudRenderer.java` (`drawWatermark` only).
- Pipe color: themed wave color at lower alpha than FPS (~0.55 vs ~0.85).
- No automated visual test required; layout is draw-only.
