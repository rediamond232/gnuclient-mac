# KeepSprint (KA Packet Desync) — Design

**Date:** 2026-08-06  
**Status:** Approved for planning  
**Goal:** Flagless KeepSprint for KillAura only: keep post-hit sprint speed without Grim (and similar) AttackSlow / Simulation flags, yielding to WTap when it owns the hit.

## Background

Vanilla 1.8: attacking while sprinting applies `motion *= 0.6` and `setSprinting(false)`.

Grim (`PacketPlayerAttack`): if `lastSprinting` is true at the attack, it adds AttackSlow possibilities (`×0.6`). Skipping the client slow while Grim still thinks you were sprinting flags Simulation.

Naive “never apply 0.6” is therefore **not** flagless. Rise-style public approach (no Rise source used): desync sprint so the AC does **not** expect AttackSlow, while the client never applies the slow.

## Requirements

| Requirement | Decision |
|-------------|----------|
| Scope | KillAura attacks only (not vanilla clicks) |
| WTap | KeepSprint **yields** when `WTapModule.shouldSuppressSprintKey()` is true |
| Flagless | Packet STOP before C02 so AC `lastSprinting` is false; skip client `0.6` |
| Universal | Same packet-desync pattern works on Grim-family and most sprint-state ACs; not a per-AC mode list |

## Non-goals

- KeepSprint on manual / AutoClicker clicks (v1)
- Overriding WTap / SprintTap
- Client-only cancel of `0.6` while still packet-sprinting

## Mechanism

On each KillAura hit that KeepSprint should own:

1. **Gate:** KeepSprint enabled, KA about to attack, player client-sprinting (or server sprint true), and **not** `WTapModule.shouldSuppressSprintKey()`.
2. **Pre-attack STOP:** Send one `C0B STOP_SPRINTING` (via existing `Mc.sendSprintActionPacket`) **before** the attack `C02`, so Grim’s sprint state clears before AttackSlow is considered.
3. **Skip vanilla slow:** Set client sprinting false **before** `attackTargetEntityWithCurrentItem` / `Mc.attackEntity` so vanilla’s sprint check does not apply `0.6`. Motion stays at full sprint speed.
4. **Restore feel:** After the attack call, restore client sprinting / sprint key so `SprintModule` can continue; allow the normal next-move `START_SPRINTING` from walking-player sync (do not spam START in the same BadPacketsX window if a sprint C0B was just sent).
5. **If STOP cannot be sent** (sprint C0B slot already used this move — `AuraCombatPacketGuard`): **abort KeepSprint for that hit** and leave vanilla slow (safer than flagging).

```
WTap suppress? ──yes──► vanilla KA attack (no KeepSprint)
       │ no
KeepSprint on + sprinting?
       │ yes
STOP C0B ok this move?
       │ no ──► vanilla slow
       │ yes
STOP → client sprint false → C02/attack (no 0.6) → restore client sprint/key
```

## Integration points

- **Hook:** `KillAuraModule.tryPerformAttack` immediately before the GRIM / `Mc.attackEntity` attack path (after ray/swing as today). Prefer a small `KeepSprintModule.onBeforeKillAuraAttack(player)` / `onAfterKillAuraAttack(player)` API so KA stays thin.
- **WTap:** Existing `WTapModule.shouldSuppressSprintKey()` — no new WTap API.
- **Sprint:** Do not fight `SprintModule` key holding after the hit; KeepSprint only shapes the attack tick.
- **Packet guard:** Honor one sprint action per C03; if STOP would be cancelled, skip KeepSprint that hit.

## Settings

| Setting | Default | Notes |
|---------|---------|-------|
| (module enable) | off | Primary toggle |
| OnlyWhenMoving | true | Optional: require forward/strafe input |

No per-AC mode list in v1.

## Testing

- Unit: gate logic — yields when WTap suppress; skips when not sprinting; records “needs stop” vs abort when slot busy (pure helper).
- Manual: KA + Sprint + KeepSprint on Grim test — speed held through hits, no Simulation AttackSlow flags; enable WTap SprintTap — KeepSprint does not fire on those hits.

## Out of scope for v1

- Vanilla click KeepSprint  
- Multiplying motion after the fact as a substitute for STOP  
- Changing MoreKB / Lagrange attack hooks beyond the KA pre/post hooks
