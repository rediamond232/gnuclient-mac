# KeepSprint (Sprint-Key Pulse) — Design

**Date:** 2026-08-06  
**Status:** Approved for planning  
**Goal:** Rise-like KeepSprint for KillAura: keep post-hit speed most of the time via short walk windows so Grim does not expect AttackSlow, without same-tick packet desync and without soft always-`×0.6`.

## Background

Vanilla 1.8: attacking while sprinting applies `motion *= 0.6` and `setSprinting(false)`.

Grim (`PacketPlayerAttack`) uses **`lastSprinting`** from the **previous** movement prediction. On 1.8, if that is true, AttackSlow is mandatory (`minAttackSlow = maxAttackSlow = 1`). Skipping client `×0.6` while Grim still expects it produces Simulation offsets like `.076866 → .041969`.

Same-tick `STOP` before C02 does **not** clear `lastSprinting`. Soft KeepSprint (always sprint, always take `×0.6`) is Simulation-safer but always slower than Rise.

User observation of Rise (no Rise source): sometimes a mild slowdown, less than full AttackSlow — presumed short **walk** intervals because it does not always sprint. Prefer **sprint-key toggle** over blasting STOP/START around every hit; STOP should come from normal walking sync when the key is released.

## Requirements

| Requirement | Decision |
|-------------|----------|
| Scope | KillAura attacks only |
| Feel | Rise-like pulse: mostly keep speed; brief walk dips |
| Sprint control | Sprint **key** + suppress `SprintModule`; STOP via walking sync when possible |
| Grim | Attack only after ≥`WalkC03s` move packets with packet sprint cleared |
| WTap | Yield when `WTapModule.shouldSuppressSprintKey()` |
| Jump | Suppress jump while not client-sprinting |
| Flagless | No hard keep while `lastSprinting` would still be true |

## Non-goals

- Soft always-`×0.6` as the primary path
- Hard keep while still packet-sprinting
- KeepSprint on vanilla / AutoClicker clicks (v1)
- Overriding WTap
- Scanning Rise source

## State machine

```
SPRINT
  sprint key held (SprintModule allowed)
  after SprintGap ticks since last recover (or first imminent KA hit) → ARM

ARM
  suppress SprintModule / release sprint key
  client sprinting false
  wait until packet sprint false (walking STOP, or one Mc.sendSprintActionPacket(false)
    only if slot free and walking has not synced yet)
  → WALK

WALK
  count player-move C03s after STOP
  when count >= WalkC03s → HIT (armed)

HIT
  KA may attack; onBefore returns owned=true path: ensure not client-sprinting
    so vanilla does not apply ×0.6; do not restore sprint mid-attack
  after hit → RECOVER (or stay HIT for same window if still walk-cleared)

RECOVER
  hold sprint key; allow SprintModule
  living/walking sends START when ready
  → SPRINT; reset SprintGap timer

WTap suppress or module off → idle SPRINT-equivalent (no suppress, no defer)
```

If ARM cannot clear packet sprint before KA wants to hit → **defer** the KA attack (`shouldDeferKillAuraAttack`) rather than attacking while Grim would AttackSlow and client skips `×0.6`.

```
WTap? ──yes──► no KeepSprint ownership / no defer from KeepSprint
   │ no
SPRINT ──gap/imminent──► ARM ──STOP synced──► WALK ──WalkC03s──► HIT
                                                      │
                                              KA attack (no ×0.6)
                                                      │
                                                   RECOVER ──► SPRINT
```

## Integration

| Hook | Behavior |
|------|----------|
| `KillAuraModule.tryPerformAttack` | `tryBeginFight` / `shouldDefer` / `onBefore` / `onAfter` as today |
| `SprintModule` / `SpeedModule` | Honor `KeepSprintModule.shouldSuppressSprintKey()` during ARM/WALK/HIT |
| `MovementInputHook` | Jump suppress when enabled and not client-sprinting |
| `PlayerUpdateHook` / after walking | Advance WALK C03 count; drive ARM→WALK→HIT transitions |
| `AuraCombatPacketGuard` | One sprint C0B per move; if STOP must be sent explicitly, only when slot free |

`onBeforeKillAuraAttack`: when in HIT (armed walk-cleared), force client not sprinting and return `owned=true` so KA does not rely on vanilla sprint slow. When not armed, return false (vanilla slow) — prefer defer over this when pulse is active and gap says we should be walking.

`onAfterKillAuraAttack`: if owned, do **not** immediate START/`setSprinting(true)`; enter RECOVER (key on only) after the hit so START goes through walking.

## Settings

| Setting | Default | Notes |
|---------|---------|-------|
| WalkC03s | 1 | Move packets after STOP before HIT |
| SprintGap | 3 | Client ticks of SPRINT between RECOVER and next ARM |
| Debug | false | Phase / defer chat |

## Testing

- Unit: phase transitions; defer when not walk-cleared; yield to WTap; jump suppress; WalkC03s / SprintGap bounds
- Manual: KA + Sprint + KeepSprint on Grim — feel closer to Rise (brief dips, not permanent walk); Debug shows ARM→WALK→HIT→RECOVER; no AttackSlow Simulation chains; WTap still owns when enabled

## Out of scope for v1

- Soft/Hard mode enum (pulse replaces soft as the module)
- Per-AC profiles
- Vanilla click KeepSprint
