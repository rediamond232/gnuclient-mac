# Grim Bounce Longjump — Design

**Date:** 2026-08-05  
**Status:** Approved for planning  
**Goal:** Burst longjump with the same *shape* as the fireball+setback+one-tick velocity multiply find, but using slime/bed bounce + Timer instead of custom fireball physics so it works on normal Grim servers.

## Background

A strong longjump was observed on an AC test server:

1. Trigger a Grim setback  
2. Fireball impulse (explosion + setback `S12`)  
3. Multiply velocity **once** at the start of that impulse  
4. Disable multiply and ride vanilla momentum (can launch very far vertically)

That peak strength depended on that server’s non-standard fireball physics. On stock Grim, explosions are tracked by `ExplosionHandler` and the same fireball chain does not transfer.

What *does* transfer:

- Grim setbacks send `S08` and often `S12` with simulated velocity (`SetbackTeleportUtil`)  
- Slime/bed bounces are real vanilla impulses Grim already models (with bouncy-block uncertainty)  
- Client Timer can accelerate packets and help force/shape a setback window into the bounce  
- One-tick amplify-then-release matches the “weird momentum” carry

## Non-goals

- Sustained open-air hover fly (previous setback-budget Fly)  
- Fireball / explosion dependency  
- Zero Simulation/Timer flags (burst flags and light rubberbands are expected)

## User-facing behavior

Module name: **Longjump** (Movement / Player category, consistent with Speed/Spider/Timer).

While enabled and armed:

1. Player falls/jumps onto slime (or bed — “bounce” block).  
2. Module pulses Timer high for a short window before/during the bounce.  
3. On bounce impulse and/or setback `S12`, apply a **single** velocity multiply (XZ + Y).  
4. Immediately restore Timer to `1.0` and stop multiplying.  
5. Vanilla friction carries the boosted vector.  
6. Cooldown prevents spam setbacks.

## Settings

| Setting | Default | Purpose |
|--------|---------|---------|
| TimerSpeed | 1.8 | Pulse speed while arming into bounce/setback |
| TimerTicks | 5 | How long Timer stays elevated |
| VelocityMultiply | 2.0 | One-shot multiply factor on capture |
| RequireBounce | true | Require bounce block / bounce `motionY` before capture |
| Cooldown | 30 | Lockout ticks after a successful or abandoned attempt |

## State machine

```
Idle
  → (enabled, not cooling down, falling motionY < 0) → Arming
Arming
  → start Timer pulse (TimerTicks)
  → wait for capture condition in window
  → on capture: Multiply once → Release
  → on TimerTicks expiry without capture → Release (no multiply) → Cooldown
Release
  → Timer restored, clear multiply flag
  → Cooldown → Idle
```

### Capture rules

Capture fires **once** per arming window.

When `RequireBounce` is true (default):

- Bounce must be detected first (feet on slime/bed, or `motionY` flip from negative to strong positive consistent with bounce), **and**
- Optionally also amplify if `S12` for the local player arrives in the same window (setback/bounce velocity packet)

When `RequireBounce` is false:

- First local `S08`/`S12` during arming is enough to capture

On capture:

- Multiply current client motion by `VelocityMultiply` once (after vanilla has applied any inbound `S12`)  
- Do **not** keep multiplying on later ticks  
- End Timer pulse the same tick

### Interaction with existing Timer module

- Prefer controlling timer via the same `Mc` / `IAccessorTimer` path `TimerModule` uses.  
- While Longjump owns a pulse, it must restore `1.0` on release/disable even if the standalone Timer module is off.  
- If standalone Timer is also enabled, Longjump pulse overrides for the pulse duration, then restores to Timer’s speed if Timer is still on, else `1.0`.

## Packet handling

- Listen for `S08PacketPlayerPosLook` and `S12PacketEntityVelocity` (local entity only).  
- Do not cancel these packets — vanilla must apply setback/velocity; we only amplify client motion once after/at apply.  
- Do not spoof `onGround` during the boost (avoids Grim setback-ground jump abuse resync).

## Grim expectations (honest)

- Timer check may flag during the pulse (VL/setback depending on config).  
- Simulation may flag if multiply exceeds prediction; the point is one burst then vanilla carry, not sustained illegal motion.  
- Strength will be lower than the custom-fireball test server; success = repeatable bounce longjump on stock Grim with slime/beds.

## Testing

- Unit tests: state machine (Idle → Arming → Capture once → Release → Cooldown); timer pulse tick counting; multiply applied once only.  
- Manual: slime pit on a Grim test world — arm, bounce, confirm single boost and timer restore; confirm disable cleans up timer.

## Out of scope for v1

- Boat envelope mode  
- Fireball mode  
- Replacing/removing the existing soft Fly module (can leave disabled or remove in a follow-up)
