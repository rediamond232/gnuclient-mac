# Food Grim NoSlow (ViaForge ≥1.16 → modern Grim) — Design

**Date:** 2026-08-09  
**Updated:** 2026-08-11  
**Status:** Implemented (NoC0F for modern Grim only)  
**Repo:** gnuclient-recode  
**Grim reference:** `~/Grim` (local checkout)

## Problem

Sword `GRIM` NoSlow uses Path A C09 slot spoof. That clears Grim’s `isSlowedByUsingItem` but also triggers `reset-item-usage-on-slot-change` (default true) and cancels server item use — fine for visual sword block, **fatal for finishing food/potion/milk**.

Requirement for food: **consumable finishes on the server**, **full move speed**, **no Grim NoSlow flags**.

## Topology (required)

| Layer | Version |
|-------|---------|
| Native client | Forge 1.8.9 (gnuclient) |
| ViaForgePlus | target ≥ **1.16** (`ViaModernGate.supportsOffhandSwap()`) |
| Server | **Modern** Grim host with real offhand NMS (1.16+) |
| Grim `ClientVersion` | ≥1.16 (`canSkipTicks`, inventory paths, swap protocol) |

| Non-working topology | Why |
|----------------------|-----|
| Pure 1.8 / Via ≤47 | No offhand swap emit; Path A cancels eat |
| ViaForge 1.9–1.15 | Cannot emit `SWAP_ITEM_WITH_OFFHAND` |
| ViaForge ≥1.16 → **1.8** NMS (+Via) | Via cancels status 6; no slot update → SWAP timeout → no full-speed |

On 1.8 backends the controller may briefly HOLD/SWAP then **timeout** without claiming full speed (probe).

## Non-goals

- C09 Path A for food
- Changing sword `GrimNoSlowController` Path A behavior
- Pure 1.8-protocol food full-speed Grim bypass
- Holding transactions longer than setup (avoid BadPacketsN)
- Bow GRIM

## Approach

Separate **food GRIM** from sword Path A. LiquidBounce-style NoC0F: short confirm-hold + real `SWAP_ITEM_WITH_OFFHAND` so Grim’s inventory lags while the consumable finishes on Spigot.

**1.8 Via cancel:** ViaVersion `Protocol1_8To1_9` cancels PLAYER_ACTION status 6 on 1.8 NMS — no inventory S2C. Claiming full speed then → NoSlow setbacks; holding C0F through that → BadPacketsN. Mitigation: enter `EATING` / full-speed **only** after `isInventorySlotUpdate`; else 10-tick SWAP timeout → TEARDOWN.

Opposite hand must **not** also be eat/drink (abort setup if it is). On 1.8 client without Via offhand peek, opposite-hand gate is always pass (`false`).

## Components

| Piece | Role |
|-------|------|
| `NoSlowModule` food-mode `GRIM` | Enable food path |
| `GrimFoodNoSlowController` | NoC0F: hold C0F/pong → swap → EATING on slot update |
| `GrimFoodNoSlowFsm` | NONE → HOLD_CONFIRM → SWAP → EATING → TEARDOWN |
| `ViaModernGate.supportsOffhandSwap()` | Arm gate (target ≥ 1.16) |
| `ViaModernPackets.sendSwapWithOffhand()` | Emit swap |
| `MixinEntityPlayerSPNoSlow` | Full-speed only when `foodFullSpeed()` (EATING) |
| `GrimNoSlowController` | Sword Path A only |

## State machine

```
NONE
  └─(using + food GRIM + supportsOffhandSwap + opposite not usable)
HOLD_CONFIRM   // cancel/queue outbound C0F / ping-pong
  └─(first confirm held)
SWAP           // send SWAP_ITEM_WITH_OFFHAND
  └─(inventory slot update → EATING; send fail / 10-tick timeout → TEARDOWN)
EATING         // full-speed mixin; confirms NOT held; no C09
  └─(release / not using ≥ 5 ticks / disable / gate lost)
TEARDOWN       // flush confirms, swap back if swapped, → NONE
```

## Error handling

| Condition | Action |
|-----------|--------|
| Via target &lt; 1.16 | Food GRIM inert (no arm) |
| 1.8 NMS backend (swap cancelled) | SWAP timeout → TEARDOWN; vanilla slow |
| Splash potion | Never food GRIM |
| Opposite hand usable eat/drink | Skip setup |
| Mid-eat gate lost / disable | TEARDOWN |
| Swap send fails | TEARDOWN |

## Testing

- Unit: FSM — offhand-swap false → no arm; happy path; swap timeout without full-speed; hold only in setup.
- Unit: sword Path A tests unchanged.
- Manual (modern Grim + ViaForge ≥1.16):
  - food-mode=GRIM, eat while sprinting → consumed, effects (gapple regen), full speed, no NoSlow
  - potion / milk same
  - Via off or &lt;1.16 → no full-speed via this path
  - 1.8+Via backend → timeout, vanilla slow, no false full-speed flag spam
  - Sword GRIM Path A unchanged

## Acceptance criteria

1. ViaForge ≥1.16 on **modern** Grim: food/potion/milk completes with effects, full client speed, no Grim NoSlow in normal play.
2. Via &lt;1.16 or no offhand-swap support: no arm / no food full-speed via this path.
3. 1.8 NMS backend: no sustained full-speed claim (timeout probe).
4. Sword `GRIM` Path A unchanged; existing NoSlow unit tests pass.
