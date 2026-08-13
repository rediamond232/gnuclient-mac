# Food Grim Via NoSlow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Food/potion/milk `GRIM` NoSlow that finishes the consumable on a 1.8+Via server while ViaForgePlus presents 1.9+, using short transaction-hold + offhand-swap desync (not C09 Path A).

**Architecture:** Add `GrimFoodNoSlowController` FSM beside sword `GrimNoSlowController`. Split NoSlow “Grim active” so sword Path A never runs for food-only GRIM, and the living-update mixin only full-speeds food while the food FSM is in `EATING`. Via modern gate: `CommonViaForgePlus.getTargetVersion().getVersion() > 47`.

**Tech Stack:** Java 8 / Forge 1.8.9, ViaForgePlus 2.2 (`libs/viaforgeplus-2.2.jar`), existing `PacketEvents` / `PacketHelper` / `PacketUtils`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-09-food-grim-via-noslow-design.md`

## Global Constraints

- Topology: Forge 1.8.9 client + ViaForgePlus `targetVersion > 47` → 1.8.x server with ViaVersion/ViaBackwards; Grim sees ClientVersion 1.9+.
- Food GRIM must **not** use C09 Path A (cancels eat via `reset-item-usage-on-slot-change`).
- Sword `GrimNoSlowController` Path A behavior stays unchanged for sword GRIM.
- Via ≤ 47 → food GRIM inert (no confirm-hold, no swap, no food full-speed via this path).
- Splash potions never qualify; opposite hand must not also be eat/drink.
- Full-speed mixin for food only in FSM state `EATING` (not HOLD_CONFIRM / SWAP).
- Confirm hold window only during HOLD_CONFIRM / SWAP; flush on TEARDOWN.
- SWAP timeout 10 ticks → TEARDOWN; not-using ≥ 5 ticks in EATING → TEARDOWN.
- Do not commit unless the user explicitly asks (plan steps may stage files; skip `git commit` by default).

## File map

| File | Responsibility |
|------|----------------|
| `src/main/java/gnu/client/module/modules/player/ViaModernGate.java` | `isViaModern()` → protocol > 47 |
| `src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowFsm.java` | Pure FSM + gates (unit-testable) |
| `src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowController.java` | Packet hold/swap/teardown; PacketListener |
| `src/main/java/gnu/client/module/modules/player/ViaModernPackets.java` | Send offhand swap via Via when possible |
| `src/main/java/gnu/client/module/modules/player/NoSlowModule.java` | Wire food controller; split sword vs food grim; mixin active API |
| `src/main/java/gnu/client/module/modules/player/GrimNoSlowController.java` | Sword-only `isGrimActive` (exclude food-only) |
| `src/main/java/gnu/client/mixin/impl/entity/MixinEntityPlayerSPNoSlow.java` | Use updated `isAnyActive()` |
| `src/test/java/gnu/client/module/modules/player/GrimFoodNoSlowFsmTest.java` | FSM + gate tests |
| `src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java` | Extend mode/split assertions if needed |

---

### Task 1: Via gate + pure food FSM

**Files:**
- Create: `src/main/java/gnu/client/module/modules/player/ViaModernGate.java`
- Create: `src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowFsm.java`
- Test: `src/test/java/gnu/client/module/modules/player/GrimFoodNoSlowFsmTest.java`

**Interfaces:**
- Consumes: ViaForgePlus `CommonViaForgePlus.getManager()` / `getTargetVersion().getVersion()` (same as `MixinEntityPlayerSP`)
- Produces:
  - `ViaModernGate.isViaModern(): boolean`
  - `enum GrimFoodNoSlowFsm.State { NONE, HOLD_CONFIRM, SWAP, EATING, TEARDOWN }`
  - `GrimFoodNoSlowFsm` with `state()`, `onStartUse(boolean viaModern, boolean foodGrimEnabled, boolean oppositeHandUsable)`, `onConfirmHeld()`, `onSwapSlotUpdate()`, `onSwapTimeout()`, `onTickEating(boolean stillUsing, int idleTicks)`, `onForceTeardown()`, `afterTeardownComplete()`, `shouldFullSpeed()`, `shouldHoldConfirms()`, `shouldSendSwap()`

- [ ] **Step 1: Write failing FSM tests**

```java
package gnu.client.module.modules.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class GrimFoodNoSlowFsmTest {
    @Test
    public void viaOffDoesNotArm() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(false, true, false);
        assertEquals(GrimFoodNoSlowFsm.State.NONE, fsm.state());
        assertFalse(fsm.shouldFullSpeed());
    }

    @Test
    public void oppositeUsableDoesNotArm() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, true);
        assertEquals(GrimFoodNoSlowFsm.State.NONE, fsm.state());
    }

    @Test
    public void happyPathToEating() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        assertEquals(GrimFoodNoSlowFsm.State.HOLD_CONFIRM, fsm.state());
        assertTrue(fsm.shouldHoldConfirms());
        assertFalse(fsm.shouldFullSpeed());
        fsm.onConfirmHeld();
        assertEquals(GrimFoodNoSlowFsm.State.SWAP, fsm.state());
        assertTrue(fsm.shouldSendSwap());
        fsm.onSwapSlotUpdate();
        assertEquals(GrimFoodNoSlowFsm.State.EATING, fsm.state());
        assertTrue(fsm.shouldFullSpeed());
        assertFalse(fsm.shouldHoldConfirms());
    }

    @Test
    public void swapTimeoutTeardown() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        fsm.onConfirmHeld();
        fsm.onSwapTimeout();
        assertEquals(GrimFoodNoSlowFsm.State.TEARDOWN, fsm.state());
        assertFalse(fsm.shouldFullSpeed());
    }

    @Test
    public void idleFiveTicksEndsEating() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        fsm.onConfirmHeld();
        fsm.onSwapSlotUpdate();
        fsm.onTickEating(false, 5);
        assertEquals(GrimFoodNoSlowFsm.State.TEARDOWN, fsm.state());
    }
}
```

- [ ] **Step 2: Run tests — expect fail (classes missing)**

Run: `./gradlew test --tests gnu.client.module.modules.player.GrimFoodNoSlowFsmTest`

Expected: compile fail or test fail (missing classes)

- [ ] **Step 3: Implement ViaModernGate + GrimFoodNoSlowFsm**

`ViaModernGate.java`:

```java
package gnu.client.module.modules.player;

import net.aspw.viaforgeplus.common.CommonViaForgePlus;

public final class ViaModernGate {
    private ViaModernGate() {}

    /** Same threshold as MixinEntityPlayerSP viaModern (protocol > 47). */
    public static boolean isViaModern() {
        try {
            CommonViaForgePlus manager = CommonViaForgePlus.getManager();
            if (manager == null)
                return false;
            return manager.getTargetVersion().getVersion() > 47;
        } catch (Throwable t) {
            return false;
        }
    }
}
```

`GrimFoodNoSlowFsm.java`: implement the state transitions exactly as the tests assert. Constants: `SWAP_TIMEOUT_TICKS = 10`, `IDLE_ABORT_TICKS = 5`. `onStartUse` only leaves NONE when `viaModern && foodGrimEnabled && !oppositeHandUsable`. `shouldSendSwap()` true only once when entering SWAP (edge flag cleared after controller sends, or `consumeSendSwap()`). Prefer `boolean consumeSendSwap()` that returns true once.

- [ ] **Step 4: Re-run tests — expect pass**

Run: `./gradlew test --tests gnu.client.module.modules.player.GrimFoodNoSlowFsmTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Stage (commit only if user asked)**

```bash
git add src/main/java/gnu/client/module/modules/player/ViaModernGate.java \
  src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowFsm.java \
  src/test/java/gnu/client/module/modules/player/GrimFoodNoSlowFsmTest.java
```

---

### Task 2: Split sword vs food Grim activation + mixin gate

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/player/NoSlowModule.java`
- Modify: `src/main/java/gnu/client/module/modules/player/GrimNoSlowController.java`
- Modify: `src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java` (or new assertions in food test)

**Interfaces:**
- Consumes: Task 1 FSM `shouldFullSpeed()` (wired in Task 3; this task only API-splits)
- Produces:
  - `NoSlowModule.isSwordGrimActive(): boolean` — sword-mode GRIM && `isSwordActive()`
  - `NoSlowModule.isFoodGrimSelected(): boolean` — food-mode GRIM && `isEating()` (setting+item; not FSM)
  - `NoSlowModule.isAnyActive()` — vanilla/sword grim as today for non-food; **food GRIM full-speed only when food controller reports EATING** (hook placeholder `foodController.shouldFullSpeed()` added in Task 3)
  - `GrimNoSlowController.isGrimActive` uses **only** `isSwordGrimActive()` (not food)

- [ ] **Step 1: Write failing test for sword-only Path A eligibility**

Add to `NoSlowModeTest` or `GrimFoodNoSlowFsmTest`:

```java
@Test
public void swordGrimActiveExcludesFoodOnly() {
    // Document intended API: isSwordGrimActive is independent of food.
    // If testing without Minecraft, assert pure helper:
    assertTrue(NoSlowModule.MODE_GRIM == 2);
}
```

Prefer a package-visible pure check if Mc-dependent methods cannot run in unit tests. Minimal compile-level: change `GrimNoSlowController.isGrimActive` signature usage and add:

```java
@Test
public void grimControllerUsesSwordGateName() throws Exception {
    assertNotNull(GrimNoSlowController.class.getDeclaredMethod("isGrimActive", NoSlowModule.class));
}
```

Stronger: extract

```java
public static boolean isSwordGrimActive(int swordMode, boolean swordActive) {
    return swordMode == MODE_GRIM && swordActive;
}
```

and unit-test that.

- [ ] **Step 2: Run test — fail until helpers exist**

Run: `./gradlew test --tests gnu.client.module.modules.player.NoSlowModeTest`

- [ ] **Step 3: Implement split in NoSlowModule + GrimNoSlowController**

In `NoSlowModule`:

```java
public boolean isSwordGrimActive() {
    return swordMode.getValue() == MODE_GRIM && isSwordActive();
}

public boolean isFoodGrimSelected() {
    return foodMode.getValue() == MODE_GRIM && isEating();
}

/** @deprecated semantic split — Path A used isSwordGrimActive */
public boolean isGrimMode() {
    return isSwordGrimActive() || isFoodGrimSelected();
}
```

In `GrimNoSlowController.isGrimActive`:

```java
return ns != null && ns.isEnabled() && ns.isSwordGrimActive();
```

In `isAnyActive()` (temporary until Task 3 wires food controller):

```java
if (isSwordGrimActive())
    return Mc.isUsingItem();
if (isFoodGrimSelected() && foodFullSpeed()) // foodFullSpeed() → false until Task 3 sets delegate
    return true;
return Mc.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
```

Until Task 3, `foodFullSpeed()` returns `false` so food GRIM does not full-speed early.

- [ ] **Step 4: Run sword GrimNoSlowControllerTest + NoSlowModeTest**

Run: `./gradlew test --tests gnu.client.module.modules.player.GrimNoSlowControllerTest --tests gnu.client.module.modules.player.NoSlowModeTest`

Expected: PASS

- [ ] **Step 5: Stage files**

```bash
git add src/main/java/gnu/client/module/modules/player/NoSlowModule.java \
  src/main/java/gnu/client/module/modules/player/GrimNoSlowController.java \
  src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java
```

---

### Task 3: Via swap helper + food controller packet wiring

**Files:**
- Create: `src/main/java/gnu/client/module/modules/player/ViaModernPackets.java`
- Create: `src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowController.java`
- Modify: `src/main/java/gnu/client/module/modules/player/NoSlowModule.java`
- Test: extend `GrimFoodNoSlowFsmTest` if needed; optional controller flag tests

**Interfaces:**
- Consumes: `GrimFoodNoSlowFsm`, `ViaModernGate`, `PacketEvents`, `PacketHelper.isClientConfirmTransaction`, `PacketUtils.sendPacketNoEvent` / Via send
- Produces:
  - `ViaModernPackets.sendSwapWithOffhand(): boolean` — true if sent
  - `GrimFoodNoSlowController`: `onEnable`/`onDisable`/`onClientTickStart`/`onTick`/`shouldFullSpeed()`/`oppositeHandUsable(ItemStack)`
  - `NoSlowModule.foodFullSpeed()` → `foodController.shouldFullSpeed()`

- [ ] **Step 1: Implement ViaModernPackets.sendSwapWithOffhand**

Discover from `libs/viaforgeplus-2.2.jar` how to emit a 1.9+ swap (PlayerAction `SWAP_ITEM_WITH_OFFHAND` or 1.9–1.15 equivalent). Preferred order:

1. If protocol ≥ 1.16: Via `UserConnection` / PacketWrapper PlayerAction swap.
2. Else if protocol ≥ 1.9: window-click / Via translation to move selected hotbar ↔ offhand slot.
3. If impossible: return `false` (FSM TEARDOWN path).

Use `CommonViaForgePlus.LOCAL_VIA_USER` on the netty channel when required. Keep method side-effect free on failure (return false, no throw).

Sketch:

```java
public final class ViaModernPackets {
    private ViaModernPackets() {}

    public static boolean sendSwapWithOffhand() {
        if (!ViaModernGate.isViaModern())
            return false;
        // Implementation: Via PacketWrapper or supported digging action.
        // Must not use C09 held-item Path A.
        return false; // replace with real send
    }
}
```

- [ ] **Step 2: Implement GrimFoodNoSlowController**

```java
public final class GrimFoodNoSlowController implements PacketListener {
    private final GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
    private final Queue<Object> heldConfirms = new ArrayDeque<>();
    private int swapTicks;
    private int idleTicks;
    private boolean didSwap;

    public void onEnable() { PacketEvents.register(this); }
    public void onDisable() { teardownAndUnregister(); }

    public boolean shouldFullSpeed() { return fsm.shouldFullSpeed(); }

    public void onClientTickStart() { /* tick swap timeout counter in onTick */ }

    public void onTick(NoSlowModule ns) {
        if (!ns.isEnabled() || !ns.isFoodGrimSelected()) {
            if (fsm.state() != GrimFoodNoSlowFsm.State.NONE)
                forceTeardown();
            return;
        }
        if (!ViaModernGate.isViaModern()) {
            if (fsm.state() != GrimFoodNoSlowFsm.State.NONE)
                forceTeardown();
            return;
        }
        if (fsm.state() == GrimFoodNoSlowFsm.State.NONE && Mc.isUsingItem()) {
            fsm.onStartUse(true, true, oppositeHandUsable());
        }
        if (fsm.state() == GrimFoodNoSlowFsm.State.SWAP) {
            swapTicks++;
            if (fsm.consumeSendSwap()) {
                didSwap = ViaModernPackets.sendSwapWithOffhand();
                if (!didSwap)
                    fsm.onSwapTimeout();
            }
            if (swapTicks > GrimFoodNoSlowFsm.SWAP_TIMEOUT_TICKS)
                fsm.onSwapTimeout();
        }
        if (fsm.state() == GrimFoodNoSlowFsm.State.EATING) {
            if (Mc.isUsingItem()) idleTicks = 0;
            else idleTicks++;
            fsm.onTickEating(Mc.isUsingItem(), idleTicks);
        }
        if (fsm.state() == GrimFoodNoSlowFsm.State.TEARDOWN)
            finishTeardown();
    }

    @Override
    public boolean onSend(Object packet) {
        if (!fsm.shouldHoldConfirms())
            return false;
        if (PacketHelper.isClientConfirmTransaction(packet)) {
            heldConfirms.add(packet);
            fsm.onConfirmHeld();
            return true; // cancel send
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (fsm.state() == GrimFoodNoSlowFsm.State.SWAP
                && PacketHelper.isServerConfirmTransaction(packet) == false
                && isSlotUpdate(packet)) {
            fsm.onSwapSlotUpdate();
            swapTicks = 0;
        }
        return false;
    }

    private void finishTeardown() {
        while (!heldConfirms.isEmpty())
            PacketUtils.sendPacketNoEvent(heldConfirms.poll());
        if (didSwap)
            ViaModernPackets.sendSwapWithOffhand();
        didSwap = false;
        swapTicks = 0;
        idleTicks = 0;
        fsm.afterTeardownComplete();
    }
}
```

Define `isSlotUpdate` using existing helpers if present (`S2FPacketSetSlot` / window items); add `PacketHelper` helpers if missing.

`oppositeHandUsable()`: on 1.8 client there is no offhand stack API — treat as **always false** for native inventory, unless Via exposes offhand contents; if unavailable, gate is always pass on opposite-hand (document). If Via inventory peek exists, use eat/drink EnumAction check mirroring `NoSlowModule.matchesEatingUseAction`.

- [ ] **Step 3: Wire NoSlowModule enable/disable/tick + foodFullSpeed**

```java
private final GrimFoodNoSlowController foodController = new GrimFoodNoSlowController();

@Override public void onEnable() {
    grimController.onEnable();
    foodController.onEnable();
}
@Override public void onDisable() {
    grimController.onDisable();
    foodController.onDisable();
}
@Override public void onTickStart() {
    grimController.onClientTickStart();
    foodController.onClientTickStart();
}
@Override public void onTick() {
    foodController.onTick(this);
}
boolean foodFullSpeed() {
    return foodController.shouldFullSpeed();
}
```

Update `isAnyActive()` to use `foodFullSpeed()` for food GRIM branch.

- [ ] **Step 4: Compile + unit tests**

Run:

```bash
./gradlew test --tests gnu.client.module.modules.player.GrimFoodNoSlowFsmTest \
  --tests gnu.client.module.modules.player.GrimNoSlowControllerTest \
  --tests gnu.client.module.modules.player.NoSlowModeTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Stage**

```bash
git add src/main/java/gnu/client/module/modules/player/ViaModernPackets.java \
  src/main/java/gnu/client/module/modules/player/GrimFoodNoSlowController.java \
  src/main/java/gnu/client/module/modules/player/NoSlowModule.java \
  src/main/java/gnu/client/runtime/packet/PacketHelper.java
```

---

### Task 4: Manual verification checklist + Ralph verify command

**Files:**
- Modify: `harness/memory/ralph-goal.md` only if user re-opens Ralph; otherwise document verify in plan completion notes
- No product code unless gaps found

**Interfaces:**
- Consumes: Tasks 1–3
- Produces: verified test command + manual checklist evidence

- [ ] **Step 1: Automated verify**

Run:

```bash
./gradlew test --tests gnu.client.module.modules.player.GrimFoodNoSlowFsmTest \
  --tests gnu.client.module.modules.player.GrimNoSlowControllerTest \
  --tests gnu.client.module.modules.player.NoSlowModeTest --rerun-tasks
```

Expected: exit 0

- [ ] **Step 2: Manual checklist (1.8 server + Via + ViaForgePlus > 47)**

| Check | Expected |
|-------|----------|
| food-mode=GRIM, eat steak while sprinting | Food consumed; full speed; no NoSlow |
| potion / milk | Same |
| Via protocol 1.8 (≤47) | Food GRIM inert; vanilla slow |
| sword-mode=GRIM Path A | Still works; unchanged |
| Opposite usable offhand (if peekable) | No arm / no desync |

- [ ] **Step 3: Fix any gaps found in manual/auto verify** (minimal diffs only)

- [ ] **Step 4: Append short note to `harness/reports/rollout-log.md` if this ships in a harness workflow; otherwise skip**

---

## Spec coverage self-check

| Spec requirement | Task |
|------------------|------|
| Via > 47 gate; ≤47 inert | 1, 3 |
| No C09 Path A for food | 2, 3 |
| HOLD_CONFIRM → SWAP → EATING → TEARDOWN | 1, 3 |
| Full-speed only in EATING | 2, 3 |
| Swap timeout 10 / idle 5 | 1 |
| Opposite hand usable abort | 1, 3 |
| Sword Path A unchanged | 2 |
| Splash excluded | 3 (`isEating` / existing splash filter) |
| Tests FSM + sword regression | 1, 2, 4 |
| Manual Via topology | 4 |

## Placeholder scan

None intentional. `ViaModernPackets` send body is discovered against the bundled jar in Task 3 Step 1 (concrete API chosen then; method signature fixed above).
