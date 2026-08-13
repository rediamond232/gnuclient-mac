# KillAura autoblock — wsamiaw full mode parity

**Status:** approved design (approach 1 — extend `KillAuraAutoBlock`)  
**Date:** 2026-08-03  
**Ship path:** `gnuclient-recode/`  
**References:**
- User-supplied `wsamiaw.module.modules.KillAura` (auto-block modes 0–11)
- Existing: `KillAuraModule`, `KillAuraAutoBlock`, `BlinkManager` / `BlinkModules.AUTO_BLOCK`

---

## Problem

GNUClient KillAura autoblock stops at GRIM (mode 9). The wsamiaw reference adds **WATCHDOG2** and **HYPIXEL3**, different attack-while-blocking rules for GRIM/WATCHDOG2/HYPIXEL3, jump suppress via `shouldAutoBlock()`, and optional keep-sprint cancel on KB for those two modes. GNUClient also has a manual-RMB “keep blocking” workaround that diverges from the reference.

## Goals

- Exact mode list: `NONE, VANILLA, SPOOF, HYPIXEL, BLINK, INTERACT, SWAP, LEGIT, FAKE, GRIM, WATCHDOG2, HYPIXEL3`
- Port WATCHDOG2 and HYPIXEL3 tick cycles to match the paste
- Align `shouldDeferAttack` with reference exclusions: allow attack-while-blocking for **VANILLA, GRIM, WATCHDOG2, HYPIXEL3** only
- Add `shouldAutoBlock()` and suppress jump when true
- Add `DisableKeepSprintOnKB` (default true, visible for WATCHDOG2/HYPIXEL3); cancel sprint on self S12 / nonzero S27 without dropping the packet
- Keep single **AutoBlockCPS** for blocking APS and WATCHDOG2 hold delay (user choice B)
- Remove `shouldKeepBlockingForManualUse` and related tests (exact reference teardown)

## Non-goals

- Splitting modes into separate classes
- Replacing AutoBlockCPS with min/max APS pair
- Porting unrelated KillAura features from the paste (target filters, rotations POLAR, debug health log, etc.)
- Changing VelocityModule beyond what `shouldAutoBlock` enables later if desired

## Design

### 1. Settings (`KillAuraModule`)

| Setting | Change |
|---------|--------|
| Auto-block | Append `WATCHDOG2`, `HYPIXEL3` to mode list (indices 10, 11) |
| AutoBlockCPS | Unchanged single slider; feeds blocking delay + WATCHDOG2 hold |
| AutoBlockRange / AutoBlockRequirePress / GrimReleaseDelay | Unchanged visibility rules; GrimReleaseDelay stays GRIM-only |
| DisableKeepSprintOnKB | **New** bool, default `true`, visible when mode is WATCHDOG2 or HYPIXEL3 |

Default Auto-block remains **NONE** (existing GNUClient default; not SPOOF).

### 2. `KillAuraAutoBlock` extensions

Constants: `WATCHDOG2 = 10`, `HYPIXEL3 = 11`.

New state:
- `hypixel3Asw` (0–2 cycle)
- `watchdog2BlockDelayMs` (default 166)
- `watchdog2BlockStartMs` (or equivalent timer) for hold elapsed checks

**WATCHDOG2 cycle** (when `hasValidTarget` and not digging/placing):
0. `attack=false`; start block if needed (`swap`); set hold delay from AutoBlockCPS; `blockTick=1`
1. `attack=false`; when player-blocking and hold elapsed → `stopBlock()`; `blockTick=2`
2. `attack=false`; when `attackDelayMs <= 0` → `blockTick=3`
3. `attack=true`; clear blocking session flags for this tick; after a successful attack from KA, reset `blockTick=0`

**HYPIXEL3 cycle**:
- While valid target: `setBlinkState(true, AUTO_BLOCK)`
- 0/1: stop block if blocking; `attack=false`; advance
- 2: start block if needed (`swap`); `blocked=true` (blink pulse after attack path); `hypixel3Asw=0`
- `isBlocking=true`, `fakeBlockState=true`
- No valid target: blink off, clear flags, `hypixel3Asw=0`

**API:**
- `shouldAutoBlock()` — player-blocking + session + mode ∈ {3,4,5,6,7,9,10,11} + not water/lava
- `shouldDeferAttack()` — defer when player-blocking unless mode ∈ {VANILLA, GRIM, WATCHDOG2, HYPIXEL3}
- `onWatchdog2Attacked()` / applyAfterAttack path — reset `blockTick` when mode is WATCHDOG2 and attack succeeded
- `reset()` / disable — clear new state; stop blink AUTO_BLOCK

Remove `shouldKeepBlockingForManualUse` and the `!block` branch that preserved manual RMB block.

### 3. KillAuraModule wiring

- Build Context as today; pass AutoBlockCPS-derived delay into WATCHDOG2 via Context or helper method
- After `tryPerformAttack`, if WATCHDOG2 and attacked → reset blockTick
- `patchMovementInput`: if `shouldAutoBlock()` → `input.jump = false` (in addition to existing Silent movefix)
- Implement `PacketListener` on KA (register enable / unregister disable): on receive S12 (entityId == self) or S27 (any motion component nonzero), if mode WATCHDOG2/HYPIXEL3 and `DisableKeepSprintOnKB` → `player.setSprinting(false)`; never cancel packet
- Static `shouldAutoBlock()` for other modules if useful (Velocity comment)

### 4. Tests

- Delete tests for `shouldKeepBlockingForManualUse`
- Add tests for mode constants 10/11, `shouldDeferAttack` mode set, and `shouldAutoBlock` mode membership (pure logic / package-visible helpers where MC is not required)

## Error handling / edge cases

- Digging/placing: WATCHDOG2/HYPIXEL3 skip cycle advances (match paste); HYPIXEL3 sets `attack=false` when digging/placing
- Missing sword / `canAutoBlock` false: existing `!block` teardown (blink off, clear session) — no manual keep
- Mode switch mid-fight: existing `lastMode` + reset on disable; no extra migration required

## Success criteria

- Mode list and indices match the paste
- WATCHDOG2 / HYPIXEL3 packet/tick behavior matches the paste given AutoBlockCPS instead of min/max APS
- Jump suppressed when `shouldAutoBlock()` is true
- KB sprint cancel works when setting enabled for modes 10/11
- Manual RMB keep-block workaround gone; related tests removed/replaced
