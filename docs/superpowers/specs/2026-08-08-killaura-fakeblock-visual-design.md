# KillAura FAKE visual block (1.7 + Animations)

**Date:** 2026-08-08  
**Status:** code complete — awaiting in-game verify  
**Ship path:** `KillAuraAutoBlock` + `ItemRenderer` render mixin

## Problem

KillAura Auto-block **FAKE** only sets an internal `fakeBlockState` flag. Nothing consumes `isFakeBlocking()`, so there is no visual block pose and no packets. Users expect the classic 1.7 look: sword held in the block pose while swings still play (as when blocking and digging in 1.7).

## Goals

- While FAKE is active, KA is enabled, sword is held, and a valid AutoBlockRange target exists: first-person sword stays in the **block pose**.
- Swings continue on top of that pose (classic 1.7 dig-while-blocking).
- Visual block goes through the same first-person path as a real sword block so **Animations** modes and scale apply when Animations is enabled; vanilla `doBlockTransformations` when disabled.
- Client-side render only: no use/block packets, no global `isUsingItem` spoof (no extra noslow / digging side effects).

## Non-goals

- Third-person arm pose or what other players see.
- Changing behavior of other AutoBlock modes (VANILLA, SPOOF, GRIM, etc.).
- New HUD/KA settings for FakeBlock.
- Replacing or rewriting Animations GL math.

## Decision

**Approach 1 (approved):** Force `ItemRenderer.renderItemInFirstPerson` into the sword `EnumAction.BLOCK` branch when `KillAuraModule.isFakeBlocking()` is true. Existing Animations mixin injects keep working unchanged.

## Architecture

### State (already present)

- `KillAuraAutoBlock` FAKE case: `isBlocking = false`, `fakeBlockState = ctx.hasValidTarget`.
- `KillAuraAutoBlock.isFakeBlocking()` → `fakeBlockState && Mc.isHoldingSword()`.

### API surface

- Add `KillAuraModule.isFakeBlocking()` static helper (same pattern as `shouldAutoBlock()` / `isAutoBlockHandlingBlock()`), delegating to the module’s `autoBlockHelper` when KA is enabled.

### Render hook

- Extend or add a small mixin on `ItemRenderer.renderItemInFirstPerson` (prefer next to existing `MixinItemRendererAnimations`, or a dedicated tiny mixin if cleaner).
- When `KillAuraModule.isFakeBlocking()` and held item is a sword: make the use-action / use-count check that selects the BLOCK branch return the blocking path **for this render only**.
- Do **not** mutate `EntityPlayerSP` use-item fields.
- Real blocking (`isUsingItem` + sword) continues to work as today; fake only fills the gap when not actually using.

### Animations interaction

- No changes required inside `AnimationsModule` if the vanilla BLOCK branch is entered: current `@Inject` before `doBlockTransformations()` and scale inject already run.
- If Animations is off, vanilla block transform still applies via the forced branch.

### Data flow

```
KA tick (FAKE) → fakeBlockState = hasValidTarget
ItemRenderer FP render → isFakeBlocking()? → force BLOCK branch
  → Animations inject (if on) / vanilla doBlockTransformations
  → swing progress still from player.getSwingProgress (1.7-style)
```

## Testing

- Manual: KA on, Auto-block FAKE, sword, target in range → FP sword in block pose; attacks still swing over it; Animations mode changes should match real block look.
- Manual: no target / FAKE off / not holding sword → normal FP hold.
- Manual: real RMB block still works; no unexpected slowdown from FAKE alone.
- Unit: optional thin test that FAKE mode still sets `isFakeBlocking` semantics via helper purity if already covered; no GL unit test required.

## Risks

- Wrong redirect target (spoofing `isUsingItem` globally) would break movement — must stay render-scoped.
- Mixin ordinal fragility on `renderItemInFirstPerson` — pin to the EnumAction.BLOCK / item-use check used for swords; verify against 1.8.9 MCP mappings used by the project.

## In-game verification checklist (human)

Static wiring and unit tests passed; confirm in client before marking fully implemented.

- [ ] **FAKE visual:** KillAura on, Auto-block **FAKE**, sword, target in AutoBlockRange → FP block pose; swings still animate while attacking.
- [ ] **Animations parity:** With Animations on, cycle modes while FAKE active → pose matches real RMB block for each mode.
- [ ] **Off paths:** No target / out of range → normal hold; Auto-block NONE or KA off → no forced pose; not holding sword → no forced pose; real RMB block still works; FAKE alone does not apply vanilla use-item slowdown.
