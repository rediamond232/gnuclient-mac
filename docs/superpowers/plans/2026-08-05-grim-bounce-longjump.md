# Grim Bounce Longjump Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Longjump module that pulses Timer while falling, captures a slime/bed bounce (and optional setback `S12`), multiplies velocity once, then restores Timer and rides vanilla momentum.

**Architecture:** Pure state machine (`LongjumpState`) owns Idle/Arming/Release/Cooldown and one-shot capture. `LongjumpModule` wires settings, tick detection (bounce blocks / motionY flip), `S08`/`S12` listening, Timer pulse via `Mc.setTimerSpeed` / `Mc.resetTimer`, and one-shot `Mc.setMotion` multiply. No sustained fly.

**Tech Stack:** Java 8, Forge 1.8.9, JUnit 4, existing `PacketEvents` / `PacketListener` / `Mc` timer helpers.

**Spec:** `docs/superpowers/specs/2026-08-05-grim-bounce-longjump-design.md`

**Commits:** Only if the user asked for commits; otherwise skip commit steps.

## Global Constraints

- Category: `Category.PLAYER` (same as Speed/Spider/Timer)
- Defaults: TimerSpeed `1.8`, TimerTicks `5`, VelocityMultiply `2.0`, RequireBounce `true`, Cooldown `30`
- Multiply velocity **once** per arming window; never leave Timer elevated after Release/disable
- Do not cancel `S08`/`S12`; do not spoof `onGround`
- Leave existing Fly module alone (out of scope)

---

## File map

| Path | Responsibility |
|------|----------------|
| Create `src/main/java/gnu/client/module/modules/movement/LongjumpState.java` | Idle/Arming/Release/Cooldown state machine + one-shot capture flag |
| Create `src/main/java/gnu/client/module/modules/movement/LongjumpModule.java` | Settings, tick/bounce/`S12` wiring, Timer pulse, motion multiply |
| Create `src/test/java/gnu/client/module/modules/movement/LongjumpStateTest.java` | State machine + capture-once tests |
| Create `src/test/java/gnu/client/module/modules/movement/LongjumpModuleTest.java` | Default settings / suffix |
| Modify `src/main/java/gnu/client/GnuClientMod.java` | Register `LongjumpModule` |

---

### Task 1: LongjumpState state machine (TDD)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/movement/LongjumpState.java`
- Test: `src/test/java/gnu/client/module/modules/movement/LongjumpStateTest.java`

**Interfaces:**
- Produces:
  - `enum Phase { IDLE, ARMING, COOLDOWN }`
  - `void reset()`
  - `Phase getPhase()`
  - `boolean tryStartArming(int timerTicks)` — IDLE only; starts ARMING with remaining timer ticks; returns false if not IDLE
  - `void onClientTick()` — decrements arming timer or cooldown; ARMING expiry with no capture → COOLDOWN; COOLDOWN expiry → IDLE
  - `boolean tryCapture()` — ARMING only, once; returns true on first success, false if already captured or not ARMING
  - `boolean hasCaptured()`
  - `boolean isTimerPulseActive()` — true while ARMING and remaining timer ticks > 0 and not yet past pulse (same as ARMING before release)
  - `void beginCooldown(int cooldownTicks)` — enter COOLDOWN (from capture release or failed arming)
  - `boolean canArm()` — phase == IDLE

- [ ] **Step 1: Write the failing test**

Create `src/test/java/gnu/client/module/modules/movement/LongjumpStateTest.java`:

```java
package gnu.client.module.modules.movement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LongjumpStateTest {

    @Test
    public void startsIdle() {
        LongjumpState s = new LongjumpState();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertTrue(s.canArm());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void armingStartsTimerPulse() {
        LongjumpState s = new LongjumpState();
        assertTrue(s.tryStartArming(3));
        assertEquals(LongjumpState.Phase.ARMING, s.getPhase());
        assertTrue(s.isTimerPulseActive());
        assertFalse(s.canArm());
    }

    @Test
    public void captureOnceOnly() {
        LongjumpState s = new LongjumpState();
        s.tryStartArming(5);
        assertTrue(s.tryCapture());
        assertTrue(s.hasCaptured());
        assertFalse(s.tryCapture());
    }

    @Test
    public void captureRejectedWhenIdle() {
        LongjumpState s = new LongjumpState();
        assertFalse(s.tryCapture());
    }

    @Test
    public void armingExpiresToCooldownWithoutCapture() {
        LongjumpState s = new LongjumpState();
        s.tryStartArming(2);
        s.onClientTick(); // remaining 1
        assertEquals(LongjumpState.Phase.ARMING, s.getPhase());
        s.onClientTick(); // remaining 0 → cooldown with default handoff: caller uses beginCooldown
        // State machine: when arming ticks hit 0 without capture, auto-enter cooldown needing beginCooldown from module
        // Prefer: onClientTick when armingRemaining hits 0 calls internal goCooldown(0) then module sets ticks —
        // Spec: failed arming → Release → Cooldown. Implement as: arming expiry sets phase COOLDOWN with 0 ticks
        // until beginCooldown is called OR onClientTick accepts cooldown length at arm start.
    }
}
```

Replace the incomplete last test with this complete version (do not leave the stub):

```java
    @Test
    public void armingExpiresToCooldownWithoutCapture() {
        LongjumpState s = new LongjumpState();
        s.tryStartArming(2);
        s.setCooldownLength(4); // used when arming expires or after capture release
        s.onClientTick(); // armingRemaining: 2 → 1
        assertEquals(LongjumpState.Phase.ARMING, s.getPhase());
        s.onClientTick(); // 1 → 0, no capture → COOLDOWN with 4
        assertEquals(LongjumpState.Phase.COOLDOWN, s.getPhase());
        assertFalse(s.isTimerPulseActive());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void captureThenReleaseEntersCooldown() {
        LongjumpState s = new LongjumpState();
        s.setCooldownLength(3);
        s.tryStartArming(5);
        assertTrue(s.tryCapture());
        s.beginCooldownAfterCapture();
        assertEquals(LongjumpState.Phase.COOLDOWN, s.getPhase());
        assertFalse(s.isTimerPulseActive());
        s.onClientTick();
        s.onClientTick();
        s.onClientTick();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertTrue(s.canArm());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void resetClearsAll() {
        LongjumpState s = new LongjumpState();
        s.setCooldownLength(10);
        s.tryStartArming(5);
        s.tryCapture();
        s.beginCooldownAfterCapture();
        s.reset();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertFalse(s.hasCaptured());
        assertTrue(s.canArm());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests gnu.client.module.modules.movement.LongjumpStateTest`

Expected: FAIL — `cannot find symbol: class LongjumpState`

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/gnu/client/module/modules/movement/LongjumpState.java`:

```java
package gnu.client.module.modules.movement;

public final class LongjumpState {

    public enum Phase { IDLE, ARMING, COOLDOWN }

    private Phase phase = Phase.IDLE;
    private int armingRemaining;
    private int cooldownRemaining;
    private int cooldownLength = 30;
    private boolean captured;

    public Phase getPhase() { return phase; }
    public boolean canArm() { return phase == Phase.IDLE; }
    public boolean hasCaptured() { return captured; }

    public boolean isTimerPulseActive() {
        return phase == Phase.ARMING && armingRemaining > 0 && !captured;
    }

    public void setCooldownLength(int ticks) {
        cooldownLength = Math.max(0, ticks);
    }

    public void reset() {
        phase = Phase.IDLE;
        armingRemaining = 0;
        cooldownRemaining = 0;
        captured = false;
    }

    public boolean tryStartArming(int timerTicks) {
        if (phase != Phase.IDLE)
            return false;
        phase = Phase.ARMING;
        armingRemaining = Math.max(1, timerTicks);
        captured = false;
        return true;
    }

    public boolean tryCapture() {
        if (phase != Phase.ARMING || captured)
            return false;
        captured = true;
        armingRemaining = 0;
        return true;
    }

    /** After a successful capture multiply — enter cooldown. */
    public void beginCooldownAfterCapture() {
        phase = Phase.COOLDOWN;
        cooldownRemaining = cooldownLength;
        armingRemaining = 0;
    }

    public void onClientTick() {
        if (phase == Phase.ARMING) {
            if (captured)
                return;
            if (armingRemaining > 0)
                armingRemaining--;
            if (armingRemaining <= 0 && !captured) {
                phase = Phase.COOLDOWN;
                cooldownRemaining = cooldownLength;
            }
            return;
        }
        if (phase == Phase.COOLDOWN) {
            if (cooldownRemaining > 0)
                cooldownRemaining--;
            if (cooldownRemaining <= 0) {
                phase = Phase.IDLE;
                captured = false;
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests gnu.client.module.modules.movement.LongjumpStateTest`

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit** (skip unless user requested commits)

```bash
git add src/main/java/gnu/client/module/modules/movement/LongjumpState.java \
        src/test/java/gnu/client/module/modules/movement/LongjumpStateTest.java
git commit -m "Add LongjumpState machine for bounce longjump."
```

---

### Task 2: Bounce detection helpers (TDD)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/movement/LongjumpBounce.java`
- Test: `src/test/java/gnu/client/module/modules/movement/LongjumpBounceTest.java`

**Interfaces:**
- Produces:
  - `static boolean isBounceBlock(Block block)` — slime or bed
  - `static boolean isBounceMotionFlip(double prevMotionY, double motionY)` — prev < -0.08 and motionY > 0.2 (slime-style reverse)

- [ ] **Step 1: Write the failing test**

```java
package gnu.client.module.modules.movement;

import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockSlime;
import net.minecraft.init.Blocks;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LongjumpBounceTest {

    @Test
    public void slimeAndBedAreBounceBlocks() {
        assertTrue(LongjumpBounce.isBounceBlock(Blocks.slime_block));
        assertTrue(LongjumpBounce.isBounceBlock(new BlockBed()));
        assertFalse(LongjumpBounce.isBounceBlock(Blocks.stone));
        assertFalse(LongjumpBounce.isBounceBlock(null));
    }

    @Test
    public void motionFlipDetectsBounce() {
        assertTrue(LongjumpBounce.isBounceMotionFlip(-0.5, 0.8));
        assertFalse(LongjumpBounce.isBounceMotionFlip(-0.5, -0.1));
        assertFalse(LongjumpBounce.isBounceMotionFlip(0.1, 0.8));
    }
}
```

Note: If `Blocks.slime_block` / `new BlockBed()` cannot be constructed in unit tests without Minecraft bootstrap, keep helpers pure and test only `isBounceMotionFlip` plus a package-visible `isBounceBlockByClassName(String)` **or** test `isBounceBlock` with null/stone via mocking. Prefer:

```java
public static boolean isBounceBlock(Block block) {
    if (block == null) return false;
    return block instanceof BlockSlime || block instanceof BlockBed;
}
```

and in the test, **only** assert `isBounceMotionFlip` if block instances fail under JUnit. If `instanceof` tests need live blocks, skip block instance tests and add:

```java
@Test
public void nullBlockIsNotBounce() {
    assertFalse(LongjumpBounce.isBounceBlock(null));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests gnu.client.module.modules.movement.LongjumpBounceTest`

Expected: FAIL — missing `LongjumpBounce`

- [ ] **Step 3: Write minimal implementation**

```java
package gnu.client.module.modules.movement;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockSlime;

public final class LongjumpBounce {
    private LongjumpBounce() {}

    public static boolean isBounceBlock(Block block) {
        if (block == null)
            return false;
        return block instanceof BlockSlime || block instanceof BlockBed;
    }

    public static boolean isBounceMotionFlip(double prevMotionY, double motionY) {
        return prevMotionY < -0.08 && motionY > 0.2;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests gnu.client.module.modules.movement.LongjumpBounceTest`

Expected: PASS (if block bootstrap fails, drop slime/bed instance asserts; keep null + motionFlip)

- [ ] **Step 5: Commit** (skip unless requested)

---

### Task 3: LongjumpModule + registration

**Files:**
- Create: `src/main/java/gnu/client/module/modules/movement/LongjumpModule.java`
- Modify: `src/main/java/gnu/client/GnuClientMod.java` (import + `safeRegister(new LongjumpModule())` near Timer/Speed)
- Test: `src/test/java/gnu/client/module/modules/movement/LongjumpModuleTest.java`

**Interfaces:**
- Consumes: `LongjumpState`, `LongjumpBounce`, `Mc.setTimerSpeed` / `Mc.resetTimer` / `Mc.setMotion` / `Mc.getMotion*`, `PacketEvents`, `S08PacketPlayerPosLook`, `S12PacketEntityVelocity`
- Produces: enabled module named `"Longjump"`

- [ ] **Step 1: Write failing module settings test**

```java
package gnu.client.module.modules.movement;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LongjumpModuleTest {

    @Test
    public void defaultSettings() {
        LongjumpModule m = new LongjumpModule();
        assertEquals("Longjump", m.getName());
        assertEquals(gnu.client.module.Category.PLAYER, m.getCategory());

        SliderSetting timerSpeed = null, timerTicks = null, multiply = null, cooldown = null;
        BoolSetting requireBounce = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "TimerSpeed": timerSpeed = (SliderSetting) s; break;
                case "TimerTicks": timerTicks = (SliderSetting) s; break;
                case "VelocityMultiply": multiply = (SliderSetting) s; break;
                case "RequireBounce": requireBounce = (BoolSetting) s; break;
                case "Cooldown": cooldown = (SliderSetting) s; break;
                default: break;
            }
        }
        assertNotNull(timerSpeed);
        assertNotNull(timerTicks);
        assertNotNull(multiply);
        assertNotNull(requireBounce);
        assertNotNull(cooldown);
        assertEquals(1.8f, timerSpeed.getValue(), 0.001f);
        assertEquals(5.0f, timerTicks.getValue(), 0.001f);
        assertEquals(2.0f, multiply.getValue(), 0.001f);
        assertTrue(requireBounce.getValue());
        assertEquals(30.0f, cooldown.getValue(), 0.001f);
    }
}
```

- [ ] **Step 2: Run test — expect missing LongjumpModule**

Run: `./gradlew test --tests gnu.client.module.modules.movement.LongjumpModuleTest`

- [ ] **Step 3: Implement LongjumpModule**

```java
package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * Longjump — Timer pulse into slime/bed bounce, one-shot velocity multiply, ride.
 * Spec: docs/superpowers/specs/2026-08-05-grim-bounce-longjump-design.md
 */
public final class LongjumpModule extends Module implements PacketListener {

    private final SliderSetting timerSpeed =
            addSetting(new SliderSetting("TimerSpeed", 1.8f, 1.0f, 3.0f, 0.05f));
    private final SliderSetting timerTicks =
            addSetting(new SliderSetting("TimerTicks", 5.0f, 1.0f, 20.0f, 1.0f));
    private final SliderSetting velocityMultiply =
            addSetting(new SliderSetting("VelocityMultiply", 2.0f, 1.0f, 5.0f, 0.1f));
    private final BoolSetting requireBounce =
            addSetting(new BoolSetting("RequireBounce", true));
    private final SliderSetting cooldown =
            addSetting(new SliderSetting("Cooldown", 30.0f, 0.0f, 100.0f, 1.0f));

    private final LongjumpState state = new LongjumpState();
    private double prevMotionY;
    private boolean bounceSeenThisArm;
    private boolean pendingMultiply; // apply multiply next tick after S12 so vanilla applied first

    public LongjumpModule() {
        super("Longjump", "Timer+bounce one-shot velocity longjump (Grim)", Category.PLAYER);
    }

    LongjumpState state() { return state; }

    @Override
    public void onEnable() {
        state.reset();
        bounceSeenThisArm = false;
        pendingMultiply = false;
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        restoreTimer();
        state.reset();
        pendingMultiply = false;
    }

    @Override
    public void onTickStart() {
        if (!isEnabled() || !Mc.isInGame())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        state.setCooldownLength(Math.round(cooldown.getValue()));

        if (pendingMultiply) {
            applyMultiplyOnce(player);
            pendingMultiply = false;
            state.beginCooldownAfterCapture();
            restoreTimer();
        }

        state.onClientTick();
        syncTimerPulse();

        if (state.getPhase() == LongjumpState.Phase.IDLE
                && player.motionY < 0.0
                && !player.onGround
                && !player.capabilities.isFlying) {
            if (state.tryStartArming(Math.round(timerTicks.getValue()))) {
                bounceSeenThisArm = false;
                syncTimerPulse();
            }
        }

        if (state.getPhase() == LongjumpState.Phase.ARMING && !state.hasCaptured()) {
            if (detectBounce(player)) {
                bounceSeenThisArm = true;
                if (requireBounce.getValue())
                    tryCaptureFromBounce(player);
            }
        }

        prevMotionY = player.motionY;
    }

    private boolean detectBounce(EntityPlayerSP player) {
        if (LongjumpBounce.isBounceMotionFlip(prevMotionY, player.motionY))
            return true;
        Block under = blockUnder(player);
        return LongjumpBounce.isBounceBlock(under) && player.motionY > 0.2;
    }

    private Block blockUnder(EntityPlayerSP player) {
        if (Mc.world() == null)
            return null;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY - 0.2);
        int z = MathHelper.floor_double(player.posZ);
        return Mc.world().getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    private void tryCaptureFromBounce(EntityPlayerSP player) {
        if (!state.tryCapture())
            return;
        applyMultiplyOnce(player);
        state.beginCooldownAfterCapture();
        restoreTimer();
    }

    private void applyMultiplyOnce(EntityPlayerSP player) {
        double m = velocityMultiply.getValue();
        Mc.setMotion(player.motionX * m, player.motionY * m, player.motionZ * m);
    }

    private void syncTimerPulse() {
        if (state.isTimerPulseActive())
            Mc.setTimerSpeed(timerSpeed.getValue());
        else if (state.getPhase() != LongjumpState.Phase.ARMING)
            restoreTimer();
    }

    private void restoreTimer() {
        Module timer = ModuleManager.INSTANCE.getModule("Timer");
        if (timer instanceof TimerModule && timer.isEnabled()) {
            // TimerModule reapplies its speed on its own ticks; reset then let it win next tick
            Mc.resetTimer();
        } else {
            Mc.resetTimer();
        }
    }

    @Override
    public boolean onSend(Object packet) { return false; }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled())
            return false;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (packet instanceof S08PacketPlayerPosLook) {
            // Setback look — do not capture alone when RequireBounce; just note window
            return false;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() != player.getEntityId())
                return false;
            if (state.getPhase() != LongjumpState.Phase.ARMING || state.hasCaptured())
                return false;
            if (requireBounce.getValue() && !bounceSeenThisArm)
                return false;
            if (!state.tryCapture())
                return false;
            // Let vanilla apply S12 this tick; multiply on next onTickStart
            pendingMultiply = true;
            return false;
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { String.format("%.1fx", velocityMultiply.getValue()) };
    }
}
```

Register in `GnuClientMod.java`:

```java
import gnu.client.module.modules.movement.LongjumpModule;
// ...
safeRegister(new LongjumpModule());
```

Place near `TimerModule` / `SpeedModule` registration.

- [ ] **Step 4: Run unit tests**

Run:

```bash
./gradlew test --tests gnu.client.module.modules.movement.LongjumpStateTest \
  --tests gnu.client.module.modules.movement.LongjumpBounceTest \
  --tests gnu.client.module.modules.movement.LongjumpModuleTest
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Full compile**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit** (skip unless requested)

---

### Task 4: Manual verification checklist (no code)

- [ ] **Step 1:** Launch client, enable Longjump, disable Fly
- [ ] **Step 2:** On a Grim test world with slime: fall onto slime while Longjump on
- [ ] **Step 3:** Confirm Timer briefly speeds up, bounce launches farther than vanilla, Timer returns to 1.0
- [ ] **Step 4:** Confirm a second attempt respects Cooldown
- [ ] **Step 5:** Disable Longjump mid-arm — Timer must be 1.0 afterward

---

## Spec coverage check

| Spec requirement | Task |
|------------------|------|
| Timer pulse while arming | Task 3 `syncTimerPulse` |
| Bounce detect slime/bed + motion flip | Task 2 + Task 3 |
| One-shot velocity multiply | Task 1 capture + Task 3 apply |
| RequireBounce gate for S12 | Task 3 `onReceive` |
| Restore Timer on release/disable | Task 3 `restoreTimer` |
| Cooldown | Task 1 + Task 3 |
| Defaults 1.8 / 5 / 2.0 / true / 30 | Task 3 settings test |
| No cancel S08/S12, no onGround spoof | Task 3 |
| Leave Fly alone | Explicit non-touch |
| Unit tests state/capture | Task 1–3 |

## Placeholder scan

None intentionally left. If Minecraft block bootstrap breaks `LongjumpBounceTest` slime/bed asserts, keep `isBounceBlock(null)` + `isBounceMotionFlip` only (documented in Task 2).
