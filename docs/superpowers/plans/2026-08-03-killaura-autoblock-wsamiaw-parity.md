# KillAura Autoblock Wsamiaw Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make KillAura autoblock modes match wsamiaw KillAura modes 0–11 (including WATCHDOG2 and HYPIXEL3), with jump suppress, KB sprint cancel, and exact teardown.

**Architecture:** Extend existing `KillAuraAutoBlock` helper with new modes/state/API; wire settings, packet listener, and movement jump suppress in `KillAuraModule`. Keep a single AutoBlockCPS slider for blocking APS and WATCHDOG2 hold delay.

**Tech Stack:** Java 8, Forge 1.8.9, JUnit 4, existing `BlinkManager` / `PacketEvents` / `PacketHelper`.

**Spec:** `docs/superpowers/specs/2026-08-03-killaura-autoblock-wsamiaw-parity-design.md`

**Commits:** Only if the user asked for commits; otherwise skip commit steps.

## Global Constraints

- Mode list/order must be exact: NONE, VANILLA, SPOOF, HYPIXEL, BLINK, INTERACT, SWAP, LEGIT, FAKE, GRIM, WATCHDOG2, HYPIXEL3
- Keep single AutoBlockCPS (no min/max APS pair)
- Remove `shouldKeepBlockingForManualUse` entirely
- `shouldDeferAttack` allows attack-while-blocking only for VANILLA, GRIM, WATCHDOG2, HYPIXEL3
- Default Auto-block remains NONE

---

## File map

| Path | Responsibility |
|------|----------------|
| Modify `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java` | Mode constants 10/11, WATCHDOG2/HYPIXEL3 cycles, shouldAutoBlock, shouldDeferAttack fix, reset/teardown, remove manual-keep |
| Modify `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` | Mode list, DisableKeepSprintOnKB, PacketListener KB cancel, jump suppress, WATCHDOG2 post-attack reset, Context fields |
| Rewrite `src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java` | Drop manual-keep tests; add mode/defer/shouldAutoBlock membership tests |

---

### Task 1: Mode constants + defer/shouldAutoBlock pure logic (TDD)

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java`
- Test: `src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java`

**Interfaces:**
- Produces: `WATCHDOG2 = 10`, `HYPIXEL3 = 11`
- Produces: `static boolean isAttackAllowedWhileBlocking(int mode)`
- Produces: `static boolean isShouldAutoBlockMode(int mode)`
- Produces: updated `shouldDeferAttack()` using `isAttackAllowedWhileBlocking(lastMode)`

- [ ] **Step 1: Replace failing/outdated tests**

Rewrite `KillAuraAutoBlockTest.java` to:

```java
package gnu.client.module.modules.combat.killaura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraAutoBlockTest {

    @Test
    public void watchdog2AndHypixel3ModeIndicesMatchReference() {
        assertEquals(10, KillAuraAutoBlock.WATCHDOG2);
        assertEquals(11, KillAuraAutoBlock.HYPIXEL3);
    }

    @Test
    public void attackWhileBlockingAllowedOnlyForVanillaGrimWatchdog2Hypixel3() {
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.VANILLA));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.GRIM));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.WATCHDOG2));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.HYPIXEL3));

        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.NONE));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.SPOOF));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.HYPIXEL));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.BLINK));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.INTERACT));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.SWAP));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.LEGIT));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.FAKE));
    }

    @Test
    public void shouldAutoBlockModesMatchReference() {
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.NONE));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.VANILLA));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.SPOOF));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.FAKE));

        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.HYPIXEL));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.BLINK));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.INTERACT));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.SWAP));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.LEGIT));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.GRIM));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.WATCHDOG2));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.HYPIXEL3));
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (missing constants / methods)**

Run: `./gradlew test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest`

Expected: compile or assertion failure for missing `WATCHDOG2` / helpers.

- [ ] **Step 3: Minimal implementation in `KillAuraAutoBlock`**

Add:

```java
public static final int WATCHDOG2 = 10;
public static final int HYPIXEL3 = 11;

/** Reference performAttack: modes that may attack while sword-blocking. */
public static boolean isAttackAllowedWhileBlocking(int mode) {
    return mode == VANILLA || mode == GRIM || mode == WATCHDOG2 || mode == HYPIXEL3;
}

/** Reference shouldAutoBlock mode membership (water/lava checked at call site). */
public static boolean isShouldAutoBlockMode(int mode) {
    return mode == HYPIXEL
        || mode == BLINK
        || mode == INTERACT
        || mode == SWAP
        || mode == LEGIT
        || mode == GRIM
        || mode == WATCHDOG2
        || mode == HYPIXEL3;
}
```

Update:

```java
public boolean shouldDeferAttack() {
    return isPlayerBlocking() && !isAttackAllowedWhileBlocking(lastMode);
}
```

Delete `shouldKeepBlockingForManualUse` method entirely.

In `tick()`, replace the `!block` branch with exact reference teardown (no stopBlock, no manual keep):

```java
if (!block) {
    setAutoBlockBlink(false);
    isBlocking = false;
    fakeBlockState = false;
    blockTick = 0;
}
```

Also delete unused Context fields if only used by manual-keep: `manualUseKeyDown`, `requirePress` — only if nothing else needs them after this change. If KA still sets them, leave fields but stop reading them in `tick`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest`

Expected: BUILD SUCCESSFUL, 3 tests pass.

- [ ] **Step 5: Commit (only if user requested commits)**

```bash
git add src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java \
  src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java
git commit -m "$(cat <<'EOF'
Add WATCHDOG2/HYPIXEL3 mode indices and attack-gate helpers.

EOF
)"
```

---

### Task 2: WATCHDOG2 + HYPIXEL3 tick cycles in helper

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java`

**Interfaces:**
- Consumes: `Context.autoBlockCps` (float) for WATCHDOG2 hold delay
- Consumes: `Context.attackDelayMs`
- Produces: `void notifyAttackSucceeded()` — if `lastMode == WATCHDOG2`, set `blockTick = 0`
- Produces: `boolean shouldAutoBlock()` — instance method using player-blocking + `isBlocking` + mode + not water/lava
- Extends `reset()` to clear `hypixel3Asw`, watchdog timer fields

- [ ] **Step 1: Extend Context + state fields**

In `Context`:

```java
/** Single AutoBlockCPS from KillAura settings. */
public float autoBlockCps;
```

In helper class fields:

```java
private int hypixel3Asw;
private long watchdog2BlockDelayMs = 166L;
private long watchdog2BlockStartMs;
```

- [ ] **Step 2: Implement WATCHDOG2 case (mode 10)**

Inside `tick()` switch, after GRIM, before `default`:

```java
case WATCHDOG2:
    if (ctx.hasValidTarget) {
        if (!digging && !placing) {
            switch (blockTick) {
                case 0:
                    attack = false;
                    if (!isPlayerBlocking())
                        swap = true;
                    watchdog2BlockDelayMs = watchdog2HoldDelayMs(ctx.autoBlockCps);
                    watchdog2BlockStartMs = System.currentTimeMillis();
                    blockTick = 1;
                    break;
                case 1:
                    attack = false;
                    if (isPlayerBlocking()
                            && System.currentTimeMillis() - watchdog2BlockStartMs >= watchdog2BlockDelayMs) {
                        stopBlock();
                        blockTick = 2;
                    }
                    break;
                case 2:
                    attack = false;
                    if (ctx.attackDelayMs <= 0L)
                        blockTick = 3;
                    break;
                case 3:
                    attack = true;
                    isBlocking = false;
                    fakeBlockState = false;
                    break;
                default:
                    blockTick = 0;
            }
        }
        setAutoBlockBlink(false);
        if (blockTick != 3) {
            isBlocking = true;
            fakeBlockState = false;
        }
    } else {
        if (isPlayerBlocking())
            stopBlock();
        setAutoBlockBlink(false);
        isBlocking = false;
        fakeBlockState = false;
        blockTick = 0;
    }
    break;
```

Helper for delay (single CPS — user choice B):

```java
static long watchdog2HoldDelayMs(float autoBlockCps) {
    if (autoBlockCps <= 0.0f)
        return 166L;
    return (long) (1000.0 / autoBlockCps);
}
```

- [ ] **Step 3: Implement HYPIXEL3 case (mode 11)**

```java
case HYPIXEL3:
    if (ctx.hasValidTarget) {
        setAutoBlockBlink(true);
        if (!digging && !placing) {
            switch (hypixel3Asw) {
                case 0:
                    if (isPlayerBlocking())
                        stopBlock();
                    attack = false;
                    hypixel3Asw = 1;
                    break;
                case 1:
                    if (isPlayerBlocking())
                        stopBlock();
                    attack = false;
                    hypixel3Asw = 2;
                    break;
                case 2:
                    if (!isPlayerBlocking())
                        swap = true;
                    blocked = true;
                    hypixel3Asw = 0;
                    break;
                default:
                    hypixel3Asw = 0;
            }
        } else {
            attack = false;
        }
        isBlocking = true;
        fakeBlockState = true;
    } else {
        setAutoBlockBlink(false);
        isBlocking = false;
        fakeBlockState = false;
        hypixel3Asw = 0;
    }
    break;
```

- [ ] **Step 4: Add `notifyAttackSucceeded` + `shouldAutoBlock` + reset**

```java
public void notifyAttackSucceeded() {
    if (lastMode == WATCHDOG2)
        blockTick = 0;
}

public boolean shouldAutoBlock() {
    EntityPlayerSP player = Mc.player();
    if (player == null)
        return false;
    if (player.isInWater() || player.isInLava())
        return false;
    return isPlayerBlocking() && isBlocking && isShouldAutoBlockMode(lastMode);
}

public void reset() {
    setAutoBlockBlink(false);
    blinkReset = false;
    blockTick = 0;
    hypixel3Asw = 0;
    grimState = 0;
    grimReleaseTick = 0;
    watchdog2BlockDelayMs = 166L;
    watchdog2BlockStartMs = 0L;
    isBlocking = false;
    fakeBlockState = false;
    if (blockingState || isPlayerBlocking())
        stopBlock();
    blockingState = false;
    lastMode = NONE;
}
```

- [ ] **Step 5: Compile check**

Run: `./gradlew compileJava compileTestJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit (only if user requested commits)**

```bash
git add src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java
git commit -m "$(cat <<'EOF'
Port WATCHDOG2 and HYPIXEL3 autoblock tick cycles.

EOF
)"
```

---

### Task 3: KillAuraModule settings + Context wiring + post-attack reset

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java`

**Interfaces:**
- Consumes: `KillAuraAutoBlock.WATCHDOG2`, `HYPIXEL3`, `notifyAttackSucceeded()`, `Context.autoBlockCps`
- Produces: mode list with 12 entries; `DisableKeepSprintOnKB` BoolSetting

- [ ] **Step 1: Extend mode list + setting**

Replace AUTO_BLOCK_MODES:

```java
private static final List<String> AUTO_BLOCK_MODES = Arrays.asList(
    "NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK", "INTERACT", "SWAP", "LEGIT", "FAKE",
    "GRIM", "WATCHDOG2", "HYPIXEL3");
```

Add after `grimReleaseDelay`:

```java
private final BoolSetting disableKeepSprintOnKb = addSetting(
    new BoolSetting("DisableKeepSprintOnKB", true));
```

In constructor visibility:

```java
disableKeepSprintOnKb.visibleWhen(() -> {
    int m = autoBlock.getValue();
    return m == KillAuraAutoBlock.WATCHDOG2 || m == KillAuraAutoBlock.HYPIXEL3;
});
```

- [ ] **Step 2: Wire Context.autoBlockCps and notifyAttackSucceeded**

In `preUpdate`, when building `ctx`:

```java
ctx.autoBlockCps = autoBlockCps.getValue();
```

Remove assignments to `ctx.manualUseKeyDown` / `ctx.requirePress` if those Context fields were deleted; otherwise leave or clear.

After attack:

```java
boolean attacked = false;
if (tickResult.attackAllowed && !autoBlockHelper.shouldDeferAttack())
    attacked = tryPerformAttack(sp);

autoBlockHelper.applyAfterAttack(tickResult, attacked, aimYaw, aimPitch, livingTarget);
if (attacked)
    autoBlockHelper.notifyAttackSucceeded();
```

Also update `shouldSkipAttackForItemUse` so VANILLA is not the only exception — rely on helper defer instead, or allow the same mode set:

```java
if (KillAuraAutoBlock.isAttackAllowedWhileBlocking(autoBlock.getValue()))
    return false;
```

(replacing the VANILLA-only early return)

- [ ] **Step 3: Compile + existing unit tests**

Run: `./gradlew test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest`

Expected: PASS.

- [ ] **Step 4: Commit (only if user requested commits)**

```bash
git add src/main/java/gnu/client/module/modules/combat/KillAuraModule.java
git commit -m "$(cat <<'EOF'
Wire WATCHDOG2/HYPIXEL3 settings and post-attack reset in KillAura.

EOF
)"
```

---

### Task 4: Jump suppress + KB sprint PacketListener

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java`

**Interfaces:**
- Consumes: `autoBlockHelper.shouldAutoBlock()`, `disableKeepSprintOnKb`
- Produces: `static boolean shouldAutoBlock()` for other modules
- Implements: `PacketListener` with register/unregister on enable/disable

- [ ] **Step 1: Jump suppress in `patchMovementInput`**

At the start of `patchMovementInput` (after null checks / after resolving `killAura`), before returning early for movefix:

```java
if (killAura.autoBlockHelper.shouldAutoBlock()) {
    MovementInput input = (MovementInput) movInput;
    input.jump = false;
}
```

Keep existing Silent movefix logic after that (do not return early just because jump was cleared).

Add:

```java
public static boolean shouldAutoBlock() {
    Module module = ModuleManager.instance().getModule("KillAura");
    if (!(module instanceof KillAuraModule) || !module.isEnabled())
        return false;
    return ((KillAuraModule) module).autoBlockHelper.shouldAutoBlock();
}
```

- [ ] **Step 2: Implement PacketListener on KillAuraModule**

Change class declaration:

```java
public final class KillAuraModule extends Module implements PacketListener {
```

Imports:

```java
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
```

In `onEnable`:

```java
PacketEvents.register(this);
```

In `onDisable` (before or after existing cleanup):

```java
PacketEvents.unregister(this);
```

Implement:

```java
@Override
public boolean onSend(Object packet) {
    return false;
}

@Override
public boolean onReceive(Object packet) {
    if (!isEnabled() || Mc.player() == null || Mc.world() == null)
        return false;
    int mode = autoBlock.getValue();
    if ((mode != KillAuraAutoBlock.WATCHDOG2 && mode != KillAuraAutoBlock.HYPIXEL3)
            || !disableKeepSprintOnKb.getValue())
        return false;

    boolean kb = false;
    if (packet instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() == Mc.player().getEntityId())
            kb = true;
    } else if (PacketHelper.isExplosion(packet)) {
        if (PacketHelper.getExplosionMotionX(packet) != 0.0F
                || PacketHelper.getExplosionMotionY(packet) != 0.0F
                || PacketHelper.getExplosionMotionZ(packet) != 0.0F)
            kb = true;
    }
    if (kb)
        Mc.player().setSprinting(false);
    return false; // never cancel
}
```

Use the exact `PacketHelper` method names that exist in this repo (`getExplosionMotionX` / `Y` / `Z` — already present). If naming differs slightly, call the existing helpers (do not invent new ones).

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest`

Expected: BUILD SUCCESSFUL; tests PASS.

- [ ] **Step 4: Commit (only if user requested commits)**

```bash
git add src/main/java/gnu/client/module/modules/combat/KillAuraModule.java
git commit -m "$(cat <<'EOF'
Suppress jump during shouldAutoBlock and cancel sprint on KB for WATCHDOG2/HYPIXEL3.

EOF
)"
```

---

### Task 5: Spec coverage smoke + cleanup

**Files:**
- Modify (if leftovers): `KillAuraAutoBlock.java`, `KillAuraModule.java`, tests

- [ ] **Step 1: Grep for removed API**

Run:

```bash
rg "shouldKeepBlockingForManualUse|manualUseKeyDown" src/
```

Expected: no matches (or only comments you then delete).

- [ ] **Step 2: Confirm mode list length in a quick test (optional add to KillAuraAutoBlockTest)**

If useful, add:

```java
@Test
public void killAuraAutoBlockModeListHasTwelveEntries() {
    KillAuraModule ka = new KillAuraModule();
    // find ModeSetting "Auto-block" and assert size 12 and last mode HYPIXEL3
}
```

Only if constructing `KillAuraModule` in unit tests does not pull Minecraft — if it does, skip this and rely on the string list constant / manual compile.

- [ ] **Step 3: Full targeted test run**

Run: `./gradlew test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest`

Expected: PASS.

- [ ] **Step 4: Commit (only if user requested commits)**

```bash
git add -u src/main/java/gnu/client/module/modules/combat/ \
  src/test/java/gnu/client/module/modules/combat/killaura/
git commit -m "$(cat <<'EOF'
Finish wsamiaw KillAura autoblock parity cleanup.

EOF
)"
```

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| Modes 0–11 including WATCHDOG2/HYPIXEL3 | 1, 3 |
| WATCHDOG2 tick cycle + AutoBlockCPS hold | 2 |
| HYPIXEL3 blink + 3-tick cycle | 2 |
| shouldDeferAttack for 1/9/10/11 | 1, 3 |
| shouldAutoBlock + jump suppress | 2, 4 |
| DisableKeepSprintOnKB + S12/S27 | 3, 4 |
| Remove manual RMB keep-block | 1 |
| Single AutoBlockCPS retained | 2, 3 |
| reset/disable clears new state | 2 |
| Tests updated | 1, 5 |
