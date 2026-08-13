# Grim NoSlow — Coder Spec (Revised, MC 1.8.9)

## 1. Approach

Grim full-speed NoSlow on 1.8.9 requires **Path A only**: on each movement tick where Grim has `isSlowedByUsingItem == true`, the client must send `C09` such that `lastSlotSelected ≠ getSlowedByUsingItemSlot`, then send `C03` (flying). Grim clears slow on the flying packet and sets `didSlotChangeLastTick`; on 1.8 the NoSlow check does not flag on the following prediction even if the client still shows item use.

The mixin redirect (`isUsingItem → false`) gives **client** full speed. Grim prediction only matches when the server slow flag is cleared via Path A. Mixin alone while Grim applies 0.2× will flag NoSlow.

**Why current code fails**

| Failure | Cause |
|---------|--------|
| Grim still applies 0.2× | `updateGrimSlotSpoof()` runs in `onTickStart`, **before** `EntityPlayerSP.onUpdate` → KA `preUpdate` → block/use packets. Use packets set `slowedByUsingItemSlot = lastSlotSelected` after C09, so C03 sees matching slots and Path A never fires. |
| Sword + KA GRIM dead zone | NoSlow skips when `isAutoBlockHandlingBlock()`; KA GRIM skips its own C09 when NoSlow GRIM is on → zero C09 during autoblock. |
| PacketOrderE (1.8) | C09 while `currentFlags != 0` flags immediately; `canSkipTicks` is false on 1.8. Conflicts include attack, release, sprint/sneak C0B. |

**Recommended design**

Introduce **`GrimNoSlowController`** as the **sole owner** of GRIM slot-spoof C09. Send from the **start of `PlayerUpdateHook.beforeWalkingPlayer`**:

```
ClientTick START → GrimNoSlowController.onClientTickStart()   [clear per-tick flags]
onUpdate HEAD    → KeepSprint (may send C0B) → KA preUpdate (attacks, block, use, C07)
onLivingUpdate   → mixin full-speed movement
beforeWalkingPlayer → [Grim C09 here if allowed] → sprint/sneak C0B → C03
```

C09 must land **after** use/block packets that set slow, **before** C03, and **before** sprint/sneak C0B in `onUpdateWalkingPlayer`. KeepSprint may already have sent C0B in `onUpdateHead`; the controller must skip C09 that tick.

**C09 ownership with KillAura**

- NoSlow owns all GRIM C09 when sword/food GRIM is active.
- **Remove** the `!KillAuraModule.isAutoBlockHandlingBlock()` gate.
- KA GRIM case 1 keeps skipping its own `Mc.sendHeldItemChange(...)` when NoSlow GRIM is active.
- KA `onOutboundPacket` for `lastMode == GRIM`: C09 must not clear client block state (unchanged).
- Continue using `sendPacketNoEvent` for Grim C09 so outbound listeners do not tear down client block state.

**Pre-tick KA phase (checker correction)**

`KillAuraAutoBlock.tick()` mutates `grimState` inside its switch. Skip/send decisions must use **phase at entry**, not post-tick state:

- At the **first line** of `tick(Context ctx)`, capture `preGrimPhase = (ctx.mode == GRIM) ? grimState : -1`.
- Cache `preMutationAttackAllowed = ctx.attackEligible` at the same entry point (before switch overrides `attack`).
- Expose both for the current client tick via getters on `KillAuraAutoBlock` / thin forwarders on `KillAuraModule`.
- `willGrimAttackThisTick` = `(preGrimPhase == 0) && preMutationAttackAllowed`. Never read post-tick `grimState`.

**Controller-owned packet observation (checker correction)**

`GrimNoSlowController` registers as a `PacketEvents` listener on NoSlow enable and unregisters on disable. It observes outbound packets **whenever NoSlow GRIM is active** for the current use type — independent of KillAura. Do **not** read `AuraCombatPacketGuard.releaseUseItemThisTick`.

Per-tick flags, owned and cleared by controller `onClientTickStart()`:

- `attackSentThisTick` — C02 ATTACK (via `PacketHelper`)
- `releaseUseItemThisTick` — C07 RELEASE_USE_ITEM
- `entityActionSentThisTick` — any C0B sprint/sneak (START or STOP)

**PacketOrderE skip matrix**

Skip Grim C09 when **any** of:

- `attackSentThisTick`
- `releaseUseItemThisTick`
- `entityActionSentThisTick` (covers KeepSprint C0B in `onUpdateHead`)
- `willGrimAttackThisTick` (pre-tick phase 0 + pre-mutation attack allowed)
- `preGrimPhase == 0` (attack tick — C02 expected; slow cleared by prior RELEASE)
- `preGrimPhase == 2` (release tick — C07 expected; slow cleared by release)
- `preGrimPhase == 3 || preGrimPhase == 4` (wait between block cycles — not slowed)

Send when GRIM active, player using item (or sword block session), and none of the above.

**Consumable vs sword**

Both use the same Path A loop. Food: send on every non-skipped movement tick while eating. Sword manual block: same. Sword + KA GRIM: send on **pre-tick phase 1** (block-start / blocking movement ticks) and other non-skipped phases while `Mc.isUsingItem()`; skip phases 0/2/3/4 per table.

**Not achievable cleanly**

- C09 in `onTickStart` on the same tick use starts — slots match, Path A fails.
- Path B (`canSkipTicks`) on 1.8.
- Partial slow while Grim predicts 0.2×.
- Attack while Grim still has slow on use slot (MultiActionsA); KA GRIM must attack only on pre-tick phase 0 after prior RELEASE.

**Defaults unchanged:** sword VANILLA, food NONE. GRIM selectable per category and must work when selected.

---

## 2. Components / files

| Path | Change |
|------|--------|
| `src/main/java/gnu/client/module/modules/player/GrimNoSlowController.java` | **New** — slot spoof, skip policy, per-tick flags, PacketEvents listener, tick-start clear |
| `src/main/java/gnu/client/module/modules/player/NoSlowModule.java` | Delegate to controller; remove `onTickStart` spoof; add `onGrimPreMovement()`; enable/disable registers listener |
| `src/main/java/gnu/client/runtime/PlayerUpdateHook.java` | Call `NoSlowModule.onGrimPreMovement()` first in `beforeWalkingPlayer` |
| `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java` | Capture `preGrimPhase` and `preMutationAttackAllowed` at `tick()` entry; expose getters |
| `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` | Thin forwarders: `getPreTickGrimPhase()`, `willGrimAttackThisTick()` |
| `src/main/java/gnu/client/mixin/impl/entity/MixinEntityPlayerSPNoSlow.java` | No change expected |
| `src/test/java/gnu/client/module/modules/player/GrimNoSlowControllerTest.java` | **New** — skip policy, slot alternation, pre-tick phase 1 send |
| `src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java` | Keep/extend `nextGrimSlot` tests |

**Do not touch:** `FloatManager`, `canSprint()` / `getMotionMultiplier()` wiring, `AuraCombatPacketGuard`, bow GRIM unless trivial.

---

## 3. Interfaces & contracts

### `GrimNoSlowController`

```
void onEnable() / onDisable()
  // register/unregister PacketEvents listener

void onClientTickStart()
  // clear attackSentThisTick, releaseUseItemThisTick, entityActionSentThisTick

void onOutboundPacket(Object packet)  // via PacketListener.onSend observe-only
  // when NoSlow GRIM active: set flags on C02 attack, C07 release, C0B sprint/sneak

boolean isGrimActive(NoSlowModule ns)
  // enabled && ((sword GRIM && isSwordActive()) || (food GRIM && isEating()))

boolean shouldSendSlotSpoof(SkipContext ctx)

int nextSlot(int currentItem, int swapSlot, boolean toggle, int lastSentSlot)
  // existing nextGrimSlot logic; never return lastSentSlot (BadPacketsA)

void sendSlotSpoof(int target)
  // PacketUtils.sendPacketNoEvent(C09)

void restoreRealSlotIfNeeded()
  // on disable / grim deactivation; send real currentItem if != lastSentSlot
```

### `SkipContext`

| Field | Source |
|-------|--------|
| `usingItem` | `Mc.isUsingItem()` |
| `preGrimPhase` | `KillAuraAutoBlock.getPreTickGrimPhase()` — **entry** phase; `-1` if not KA GRIM |
| `willGrimAttackThisTick` | `KillAuraModule.willGrimAttackThisTick()` |
| `attackSentThisTick` | controller flag |
| `releaseUseItemThisTick` | controller flag (C07, not AuraCombatPacketGuard) |
| `entityActionSentThisTick` | controller flag (C0B sprint/sneak) |

### `KillAuraAutoBlock` additions

At **first line** of `tick(Context ctx)`:

```
preGrimPhase = (ctx.mode == GRIM) ? grimState : -1
preMutationAttackAllowed = ctx.attackEligible
// ... existing switch mutates grimState and attack ...
// store preGrimPhase + preMutationAttackAllowed for current client tick
```

Exposed API:

```
static int getPreTickGrimPhase()
static boolean getPreTickGrimAttackAllowed()
```

### `KillAuraModule` additions

```
static int getPreTickGrimPhase()
  // forward to autoBlockHelper

static boolean willGrimAttackThisTick()
  // autoBlock == GRIM
  // && getPreTickGrimPhase() == 0
  // && getPreTickGrimAttackAllowed()
```

### Invariants

1. **Path A order:** At most one Grim C09 per client tick; if sent, it is the last `HELD_ITEM_CHANGE` before the tick's first `C03`.
2. **BadPacketsA:** Consecutive C09 never share the same slot id.
3. **PacketOrderE (1.8):** No Grim C09 when any skip condition is true (attack, release, entity action, or pre-tick phase 0/2/3/4).
4. **MultiActionsA:** No C02 ATTACK while `isSlowedByUsingItem && lastSlotSelected == slowedByUsingItemSlot`. Pre-tick phase 0 skips C09; attacks only after prior RELEASE (phase 2).
5. **Phase read:** Skip/send logic never reads post-tick `grimState`.
6. **Flag ownership:** Controller clears its three flags on its own `onClientTickStart`; listener registered iff NoSlow enabled.
7. **KA C09:** KA GRIM never double-sends when NoSlow GRIM active.
8. **Visual slot:** Client hotbar stays on real slot; only server `lastSlotSelected` alternates.
9. **Mixin:** GRIM `isAnyActive()` = `isGrimMode() && Mc.isUsingItem()`.

### Swap slot selection

Default: `currentItem == 0 ? 1 : 0` (match KA `grimSwapSlot`). Optional: `KillAuraAutoBlock.findEmptySlot(currentItem)`.

---

## 4. Implementation order

1. Add **`GrimNoSlowController`** with pure `shouldSendSlotSpoof` + `nextSlot` (port existing `nextGrimSlot`).
2. Add **pre-tick capture** in `KillAuraAutoBlock.tick()` at entry; expose getters; add `KillAuraModule` forwarders.
3. Wire **PacketEvents listener** on controller — observe C02/C07/C0B; `onClientTickStart` flag clear; register on NoSlow enable, unregister on disable.
4. **Move send site** — delete spoof from `NoSlowModule.onTickStart`; add `NoSlowModule.onGrimPreMovement()` called first in `PlayerUpdateHook.beforeWalkingPlayer`.
5. **Remove KA gate** — delete `!KillAuraModule.isAutoBlockHandlingBlock()` condition; verify KA GRIM case 1 still skips duplicate C09 when NoSlow GRIM on.
6. **Fix `isGrimMode()`** — gate sword branch through `isSwordActive()` so `killaura-only` applies to GRIM.
7. **Tests** — skip matrix with controller-owned flags; phase-1 send uses **pre-tick** phase 1; pre-tick phase capture test on KA helper.
8. **Manual smoke** — Grim 1.8.9: food GRIM, sword GRIM, KA GRIM + sword GRIM, KeepSprint C0B conflict tick.

---

## 5. Out of scope

- Bow GRIM mode (unless trivial reuse of controller call).
- `FloatManager` / `BlinkModules.NO_SLOW` wiring.
- `canSprint()` / `getMotionMultiplier()` consumers.
- Partial slowdown / alternating 100/20 motion.
- Reading `AuraCombatPacketGuard.releaseUseItemThisTick`.
- Post-tick `grimState` for skip decisions.
- Grim source modifications; Via / post-1.8 Path B.
- VANILLA/NONE NoSlow mode behavior changes.

---

## 6. Verification criteria

### Unit tests (`GrimNoSlowControllerTest`)

| Test | Pass condition |
|------|----------------|
| `nextGrimSlot` alternation | No consecutive duplicate slots |
| Skip when `attackSentThisTick` | no send |
| Skip when `releaseUseItemThisTick` (controller C07 observe) | no send |
| Skip when `entityActionSentThisTick` (C0B already sent) | no send |
| Skip when `preGrimPhase == 0` | no send |
| Skip when `preGrimPhase == 2` | no send |
| Skip when `willGrimAttackThisTick` | no send |
| **Send when `preGrimPhase == 1`** + using + no flags | **send** (pre-tick phase, not post-mutation state) |
| Send food GRIM eating, no flags, `preGrimPhase == -1` | send |
| Flags cleared on `onClientTickStart` | all false after clear |

### Unit tests (`KillAuraAutoBlock` — optional)

| Test | Pass condition |
|------|----------------|
| Pre-tick phase capture | Tick that transitions 0→1 internally: `getPreTickGrimPhase()` == **0** for that tick (not 1) |

### Integration / manual (Grim 1.8.9)

| Scenario | Expected |
|----------|----------|
| `food-mode=GRIM`, hold eat, sprint + strafe | Full speed, no NoSlow / PacketOrderE / BadPacketsA flags |
| `sword-mode=GRIM`, manual block + move | Full speed while blocking, no flags |
| KA `Auto-block=GRIM` + `sword-mode=GRIM`, fight moving target | Full speed on block phases (pre-tick phase 1); attacks on phase 0 without flags |
| KeepSprint emits C0B in `onUpdateHead` | No C09 same tick; full speed resumes next eligible tick |
| Disable NoSlow mid-use | One C09 restores real slot |
| NoSlow GRIM without KA enabled | Listener still tracks C07/C02/C0B independently |

### Packet trace invariant (debug)

On send ticks, outbound order must be:

```
[preUpdate: C08/C07/C02 optional]
[onUpdateHead: C0B optional — if present, no C09 this tick]
C09  (if not skipped)
C0B? (walking sprint/sneak — after C09)
C03
```

Never: C09 before the tick's use-start and expecting Path A same tick; never C09 after C02 ATTACK same tick.

### Build

`./gradlew test --tests gnu.client.module.modules.player.GrimNoSlowControllerTest --tests gnu.client.module.modules.player.NoSlowModeTest` passes; `./gradlew build` clean.

---

**Summary invariant:** Every movement tick where Grim believes the player is item-slowed, NoSlow sends exactly one BadPacketsA-safe C09 after all use/block/attack decisions and before C03 — using **pre-tick** KA GRIM phase, **controller-owned** C02/C07/C0B observation with self-managed tick clearing, and **no** reliance on post-mutation `grimState` or AuraCombatPacketGuard.

[REDACTED]