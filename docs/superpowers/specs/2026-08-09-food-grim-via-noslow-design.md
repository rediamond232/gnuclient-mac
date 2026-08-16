# Adaptive Food Grim NoSlow (Via ≥1.16 client → any Grim) — Design

**Date:** 2026-08-09  
**Updated:** 2026-08-16  
**Status:** Implemented (double-C09, config-independent)  
**Repo:** gnuclient-recode  
**Grim reference:** `~/Grim` (local checkout)

## Problem

Sword `GRIM` NoSlow uses Path A C09 slot-spoof. That clears Grim's `isSlowedByUsingItem`
but also triggers `reset-item-usage-on-slot-change` (default on) and cancels server item use —
fine for visual sword block, **fatal for finishing food/potion/milk**.

Requirement for food: **consumable finishes on the server**, **full move speed**, **no Grim
NoSlow flags — regardless of server config.**

## Topology

| Layer         | Version                          |
|---------------|----------------------------------|
| Native client | Forge 1.8.9 (gnuclient)          |
| ViaForgePlus  | target ≥ **1.16**               |
| Server        | Any Grim backend (Via → 1.8, or modern) |
| Grim `ClientVersion` | ≥1.16 → `canSkipTicks()` is `true` |

## Approach — double-C09 (shipped technique)

Matches the widely-shipped client `GrimNoslow` (e.g. Rise): every tick while eating, send
`C09(adjacent)` then `C09(real)`.

With ViaForge ≥1.16, Grim's `canSkipTicks()` is `true`, so the `HELD_ITEM_CHANGE` path in
`PacketPlayerDigging`:

```java
boolean usingInMainHand = isSlowedByUsingItem && itemInUseHand == MAIN_HAND;
if (usingInMainHand && canSkipTicks() && !isTickingReliablyFor(3)) {
    setSlowedByUsingItem(false);
    checkManager.getNoSlow().didSlotChangeLastTick = true; // one-tick NoSlow grace
}
```

clears `isSlowedByUsingItem` and grants the one-tick grace → **full client speed, no NoSlow
flag**. The back-to-real C09 is what the clear path sees.

This is deliberately the **same wire behavior as the shipped clients**, implemented so it can be
validated against a real server rather than reasoned about from one Grim build. My prior
"impossible on default config" conclusion was against the specific `~/Grim` dev checkout and
conflicts with shipped behavior — this version is the concrete, testable implementation.

## Client-side movement slow

`MixinEntityPlayerSPNoSlow` redirects `isUsingItem()` to `false` when `NoSlowModule.isAnyActive()`.
`isAnyActive()` returns true when food-GRIM is eating and `foodFullSpeed()` (`shouldFullSpeed()`
→ armed), so the client's own `0.2×` movement slow is also cancelled — the player moves at full
speed visually and in physics.

## Components

| Piece | Role |
|-------|------|
| `NoSlowModule` food-mode `GRIM` | Enable food path |
| `GrimFoodNoSlowController` | Double-C09 every tick while eating; `shouldFullSpeed()` feeds mixin |
| `GrimNoSlowController` | Sword Path A only (unchanged) |
| `MixinEntityPlayerSPNoSlow` | Cancel client movement slow when food full-speed armed |

## State machine

```
IDLE
  └─(food GRIM + eating) → ARM
ARM            // each tick: C09(adjacent), C09(real); shouldFullSpeed()=true
  └─(not eating / deselect / disable) → IDLE
```

## Error handling

| Condition | Action |
|-----------|--------|
| Not food-GRIM / not eating | Do not arm; `shouldFullSpeed()` false |
| Disable / gate lost | Unarm |
| Via <1.16 | `canSkipTicks()` false → technique relies on real server; still sends (harmless) |

## Testing

- Manual on the real server (all configs): eat gapple/milk/potion → finishes, effects apply,
  full move speed, no NoSlow flag, no setback.
- `GrimNoSlowControllerTest` (sword Path A) unchanged and passing.
- `BlinkManager`/`NoSlowMode` tests unaffected.

## Acceptance criteria

1. Consumable finishes server-side (stack decrements, effects apply).
2. Full move speed while eating.
3. No Grim NoSlow flag / setback.
4. Works regardless of `reset-item-usage-on-slot-change` / `force-slow-movement`.
5. Sword `GRIM` Path A unchanged.