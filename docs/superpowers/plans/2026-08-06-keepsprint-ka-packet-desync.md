# KeepSprint KA Packet Desync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a KillAura-only KeepSprint module that sends STOP_SPRINTING before the attack C02, skips vanilla `0.6` attack slow, and yields to WTap — flagless on Grim-family sprint AttackSlow.

**Architecture:** Pure gate helper (`KeepSprintLogic`) decides whether to own a hit. `KeepSprintModule` exposes `onBeforeKillAuraAttack` / `onAfterKillAuraAttack`. `KillAuraModule.tryPerformAttack` calls those hooks around the existing attack path. STOP uses `Mc.sendSprintActionPacket`; abort if `AuraCombatPacketGuard.shouldCancelEntityAction` would block the STOP.

**Tech Stack:** Java 8, Forge 1.8.9, JUnit 4, existing `Mc` / `WTapModule` / `AuraCombatPacketGuard`.

**Spec:** `docs/superpowers/specs/2026-08-06-keepsprint-ka-packet-desync-design.md`

**Commits:** Only if the user asked for commits; otherwise skip commit steps.

## Global Constraints

- KillAura attacks only (not vanilla clicks)
- Yield when `WTapModule.shouldSuppressSprintKey()` is true
- If STOP cannot be sent this move window, abort KeepSprint for that hit (vanilla slow)
- No per-AC mode list in v1
- Default: module off; `OnlyWhenMoving` default true

---

## File map

| Path | Responsibility |
|------|----------------|
| Create `src/main/java/gnu/client/module/modules/combat/KeepSprintLogic.java` | Pure gate: shouldOwnHit(enabled, sprinting, moving, wtapSuppress, stopSlotFree) |
| Create `src/main/java/gnu/client/module/modules/combat/KeepSprintModule.java` | Settings + before/after KA attack hooks |
| Modify `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` | Call KeepSprint hooks in `tryPerformAttack` |
| Modify `src/main/java/gnu/client/GnuClientMod.java` | Register KeepSprintModule |
| Create `src/test/java/gnu/client/module/modules/combat/KeepSprintLogicTest.java` | Gate unit tests |
| Create `src/test/java/gnu/client/module/modules/combat/KeepSprintModuleTest.java` | Default settings |

---

### Task 1: KeepSprintLogic gate (TDD)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/combat/KeepSprintLogic.java`
- Test: `src/test/java/gnu/client/module/modules/combat/KeepSprintLogicTest.java`

**Interfaces:**
- Produces: `static boolean shouldOwnHit(boolean moduleEnabled, boolean clientSprinting, boolean moving, boolean onlyWhenMoving, boolean wtapSuppress, boolean stopSlotFree)`

- [ ] **Step 1: Write the failing test**

```java
package gnu.client.module.modules.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepSprintLogicTest {

    @Test
    public void ownsHitWhenAllGatesPass() {
        assertTrue(KeepSprintLogic.shouldOwnHit(true, true, true, true, false, true));
    }

    @Test
    public void yieldsWhenDisabled() {
        assertFalse(KeepSprintLogic.shouldOwnHit(false, true, true, true, false, true));
    }

    @Test
    public void yieldsWhenNotSprinting() {
        assertFalse(KeepSprintLogic.shouldOwnHit(true, false, true, true, false, true));
    }

    @Test
    public void yieldsToWtapSuppress() {
        assertFalse(KeepSprintLogic.shouldOwnHit(true, true, true, true, true, true));
    }

    @Test
    public void abortsWhenStopSlotBusy() {
        assertFalse(KeepSprintLogic.shouldOwnHit(true, true, true, true, false, false));
    }

    @Test
    public void onlyWhenMovingRequiresMovement() {
        assertFalse(KeepSprintLogic.shouldOwnHit(true, true, false, true, false, true));
        assertTrue(KeepSprintLogic.shouldOwnHit(true, true, false, false, false, true));
    }
}
```

- [ ] **Step 2: Run test — expect missing class**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest`

Expected: FAIL compile — `KeepSprintLogic` not found

- [ ] **Step 3: Minimal implementation**

```java
package gnu.client.module.modules.combat;

public final class KeepSprintLogic {
    private KeepSprintLogic() {}

    public static boolean shouldOwnHit(
            boolean moduleEnabled,
            boolean clientSprinting,
            boolean moving,
            boolean onlyWhenMoving,
            boolean wtapSuppress,
            boolean stopSlotFree) {
        if (!moduleEnabled || !clientSprinting || wtapSuppress || !stopSlotFree)
            return false;
        if (onlyWhenMoving && !moving)
            return false;
        return true;
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest`

- [ ] **Step 5: Commit** (skip unless requested)

---

### Task 2: KeepSprintModule + registration

**Files:**
- Create: `src/main/java/gnu/client/module/modules/combat/KeepSprintModule.java`
- Modify: `src/main/java/gnu/client/GnuClientMod.java`
- Test: `src/test/java/gnu/client/module/modules/combat/KeepSprintModuleTest.java`

**Interfaces:**
- Consumes: `KeepSprintLogic`, `WTapModule.shouldSuppressSprintKey()`, `Mc.isClientSprinting`, `Mc.setClientSprinting`, `Mc.sendSprintActionPacket`, `Mc.setSprintKeyState`, `AuraCombatPacketGuard.shouldCancelEntityAction`
- Produces:
  - `static boolean onBeforeKillAuraAttack(EntityPlayerSP player)` — returns true if this hit is owned (STOP sent, client sprint cleared for vanilla skip)
  - `static void onAfterKillAuraAttack(EntityPlayerSP player, boolean owned)` — restore client sprint/key if owned

- [ ] **Step 1: Write settings test**

```java
package gnu.client.module.modules.combat;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KeepSprintModuleTest {
    @Test
    public void defaultSettings() {
        KeepSprintModule m = new KeepSprintModule();
        assertEquals("KeepSprint", m.getName());
        assertEquals(gnu.client.module.Category.COMBAT, m.getCategory());
        BoolSetting onlyMoving = null;
        for (Setting<?> s : m.getSettings()) {
            if ("OnlyWhenMoving".equals(s.getName()))
                onlyMoving = (BoolSetting) s;
        }
        assertNotNull(onlyMoving);
        assertTrue(onlyMoving.getValue());
    }
}
```

- [ ] **Step 2: Run — expect missing module**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintModuleTest`

- [ ] **Step 3: Implement KeepSprintModule**

```java
package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.runtime.AuraCombatPacketGuard;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * Flagless KeepSprint for KillAura — STOP before C02 so AC does not expect AttackSlow.
 * Spec: docs/superpowers/specs/2026-08-06-keepsprint-ka-packet-desync-design.md
 */
public final class KeepSprintModule extends Module {

    private final BoolSetting onlyWhenMoving =
            addSetting(new BoolSetting("OnlyWhenMoving", true));

    public KeepSprintModule() {
        super("KeepSprint", "KA packet-desync keep sprint (flagless)", Category.COMBAT);
    }

    private static KeepSprintModule instance() {
        Module m = ModuleManager.INSTANCE.getModule("KeepSprint");
        return m instanceof KeepSprintModule ? (KeepSprintModule) m : null;
    }

    /**
     * @return true if KeepSprint owns this hit (STOP sent, client sprint false for vanilla skip)
     */
    public static boolean onBeforeKillAuraAttack(EntityPlayerSP player) {
        KeepSprintModule mod = instance();
        if (mod == null || player == null)
            return false;

        boolean moving = player.movementInput != null
                && (player.movementInput.moveForward != 0.0f
                    || player.movementInput.moveStrafe != 0.0f);

        // Peek whether STOP would be cancelled (BadPacketsX / guard) without sending.
        C0BPacketEntityAction probe = new C0BPacketEntityAction(
                player, C0BPacketEntityAction.Action.STOP_SPRINTING);
        boolean stopSlotFree = !AuraCombatPacketGuard.shouldCancelEntityAction(probe);

        if (!KeepSprintLogic.shouldOwnHit(
                mod.isEnabled(),
                Mc.isClientSprinting(player),
                moving,
                mod.onlyWhenMoving.getValue(),
                WTapModule.shouldSuppressSprintKey(),
                stopSlotFree))
            return false;

        Mc.setSprintKeyState(false);
        Mc.setClientSprinting(player, false);
        Mc.sendSprintActionPacket(player, false);
        return true;
    }

    public static void onAfterKillAuraAttack(EntityPlayerSP player, boolean owned) {
        if (!owned || player == null)
            return;
        // Restore sprint feel; START C0B left to next walking-player sync / SprintModule.
        Mc.setClientSprinting(player, true);
        Mc.setSprintKeyState(true);
    }
}
```

**Important:** `sendSprintActionPacket` must run **after** client sprint is set false so the packet reflects STOP. Order in `onBefore`: key false → client false → send STOP.

Register in `GnuClientMod.registerModules`:

```java
import gnu.client.module.modules.combat.KeepSprintModule;
// near WTap / Sprint:
safeRegister(new KeepSprintModule());
```

- [ ] **Step 4: Run KeepSprint tests**

```bash
./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest \
  --tests gnu.client.module.modules.combat.KeepSprintModuleTest
```

Expected: PASS

- [ ] **Step 5: Commit** (skip unless requested)

---

### Task 3: Wire KillAura.tryPerformAttack

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` (`tryPerformAttack`)

**Interfaces:**
- Consumes: `KeepSprintModule.onBeforeKillAuraAttack` / `onAfterKillAuraAttack`

- [ ] **Step 1: Patch tryPerformAttack**

Inside `tryPerformAttack`, after `notifyPreAttackHooks` and silent-aim save, **before** the GRIM / `Mc.attackEntity` block:

```java
        boolean keepSprintOwned = KeepSprintModule.onBeforeKillAuraAttack(player);
        try {
            boolean attacked;
            if (autoBlock.getValue() == KillAuraAutoBlock.GRIM) {
                // ... existing C02 INTERACT + ATTACK + attackTargetEntityWithCurrentItem ...
            } else {
                attacked = Mc.attackEntity(attackTarget, false);
            }
            // ... existing silent-aim restore ...
            if (!attacked) {
                KeepSprintModule.onAfterKillAuraAttack(player, keepSprintOwned);
                return false;
            }
            KeepSprintModule.onAfterKillAuraAttack(player, keepSprintOwned);
            // ... existing hitRegistered / lastAttackMs ...
            return true;
        } catch (Throwable t) {
            KeepSprintModule.onAfterKillAuraAttack(player, keepSprintOwned);
            throw t;
        }
```

Prefer a cleaner structure without broad try/catch if the method has no throws — just call `onAfter` on both success and failure paths:

```java
        boolean keepSprintOwned = KeepSprintModule.onBeforeKillAuraAttack(player);

        boolean attacked;
        if (autoBlock.getValue() == KillAuraAutoBlock.GRIM) {
            Mc.addToSendQueue(new C02PacketUseEntity(attackTarget, C02PacketUseEntity.Action.INTERACT));
            Mc.addToSendQueue(new C02PacketUseEntity(attackTarget, C02PacketUseEntity.Action.ATTACK));
            player.attackTargetEntityWithCurrentItem(attackTarget);
            attacked = true;
        } else {
            attacked = Mc.attackEntity(attackTarget, false);
        }

        if (useSilentAim) {
            player.rotationYaw = savedYaw;
            player.rotationPitch = savedPitch;
        }

        KeepSprintModule.onAfterKillAuraAttack(player, keepSprintOwned);

        if (!attacked)
            return false;
        // ... rest unchanged ...
```

Add import: `gnu.client.module.modules.combat.KeepSprintModule` (same package — no import needed if same package; KeepSprint is same package `combat` — **no import required**).

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`

Expected: SUCCESS

- [ ] **Step 3: Full related tests + build**

```bash
./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest \
  --tests gnu.client.module.modules.combat.KeepSprintModuleTest build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit** (skip unless requested)

---

### Task 4: Manual checklist

- [ ] KA + Sprint + KeepSprint on Grim: speed held through hits; no AttackSlow Simulation spam
- [ ] Enable WTap SprintTap: KeepSprint does not own those hits (vanilla slow / WTap behavior)
- [ ] KeepSprint off: vanilla KA attack slow unchanged

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| KA-only hook | Task 3 |
| WTap yield | Task 1 + 2 |
| STOP before C02 | Task 2 before, Task 3 order |
| Skip vanilla 0.6 via client sprint false | Task 2 |
| Abort if STOP slot busy | Task 1 + 2 probe |
| OnlyWhenMoving | Task 1 + 2 |
| Restore after attack | Task 2 `onAfter` |

## Placeholder scan

None. Probe packet construction for `shouldCancelEntityAction` must use real `C0BPacketEntityAction` STOP — if guard is inactive when KA off, `shouldCancelEntityAction` returns false (slot free); KeepSprint only runs from KA attack path while KA is on, so guard is active when needed.
