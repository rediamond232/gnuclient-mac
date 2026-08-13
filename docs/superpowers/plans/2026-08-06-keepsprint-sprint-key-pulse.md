# KeepSprint Sprint-Key Pulse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace soft KeepSprint with a sprint-key pulse state machine so KillAura attacks happen after walk C03(s) clear Grim `lastSprinting`, skipping client `×0.6` only then — Rise-like speed with brief walk dips.

**Architecture:** Pure `KeepSprintPulseState` owns SPRINT→ARM→WALK→HIT→RECOVER. `KeepSprintModule` wires settings, sprint-key suppress, STOP via walking (fallback explicit STOP if slot free), C03 counting after walking, and KA defer/before/after hooks. No same-tick STOP-before-C02 hard keep while packet sprint is still “last”.

**Tech Stack:** Java 8, Forge 1.8.9, JUnit 4, existing `Mc`, `AuraCombatPacketGuard`, `SprintModule`/`WTapModule` suppress hooks, KA `tryPerformAttack` hooks.

**Spec:** `docs/superpowers/specs/2026-08-06-keepsprint-sprint-key-pulse-design.md`

**Commits:** Only if the user asked for commits; otherwise skip commit steps.

## Global Constraints

- KA-only KeepSprint; yield when `WTapModule.shouldSuppressSprintKey()` is true
- Attack with skipped `×0.6` only in HIT (walk-cleared); otherwise defer KA
- Sprint control is key-first; explicit `STOP` only if packet sprint still true after key release and `AuraCombatPacketGuard.isSprintSlotFree()`
- Jump suppress while enabled and not client-sprinting
- Do not immediate `setSprinting(true)` / START in `onAfter` after an owned hit — RECOVER holds key only
- No Rise source scanning

---

## File map

| Path | Responsibility |
|------|----------------|
| Create `src/main/java/gnu/client/module/modules/combat/KeepSprintPulseState.java` | Pure phase machine + C03/gap counters |
| Create `src/test/java/gnu/client/module/modules/combat/KeepSprintPulseStateTest.java` | Phase / defer / gap tests |
| Rewrite `src/main/java/gnu/client/module/modules/combat/KeepSprintLogic.java` | Small pure helpers (jump, yield, own-hit) |
| Rewrite `src/test/java/gnu/client/module/modules/combat/KeepSprintLogicTest.java` | Match new helpers |
| Rewrite `src/main/java/gnu/client/module/modules/combat/KeepSprintModule.java` | Settings + Mc/KA/Sprint wiring |
| Modify `src/test/java/gnu/client/module/modules/combat/KeepSprintModuleTest.java` | WalkC03s / SprintGap / Debug defaults |
| Touch (no API change expected) | `KillAuraModule.tryPerformAttack`, `SprintModule`, `SpeedModule`, `PlayerUpdateHook`, `MovementInputHook` — already call KeepSprint statics |

---

### Task 1: KeepSprintPulseState (TDD)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/combat/KeepSprintPulseState.java`
- Test: `src/test/java/gnu/client/module/modules/combat/KeepSprintPulseStateTest.java`

**Interfaces:**
- Produces:
  - `enum Phase { SPRINT, ARM, WALK, HIT, RECOVER }`
  - `Phase getPhase()`
  - `void reset()`
  - `void setWalkC03s(int n)` / `void setSprintGap(int n)` — clamp `WalkC03s` to ≥1, `SprintGap` to ≥0
  - `boolean shouldSuppressSprintKey()` — true for ARM, WALK, HIT
  - `boolean shouldDeferAttack()` — true when module pulse active and phase is ARM or WALK (not yet HIT); also true in SPRINT when `armRequested` and gap forces arm before hit (see `onImminentAttack`)
  - `boolean canOwnAttack()` — true only in HIT
  - `void onImminentAttack()` — from SPRINT: if sprintGapRemaining > 0, stay SPRINT and do not arm yet (KA may vanilla-hit); if gap elapsed, enter ARM. From HIT: no-op. From ARM/WALK: no-op (defer handles).
  - `void onClientTick()` — in SPRINT decrement sprintGapRemaining; in RECOVER after key restore tick → SPRINT and set sprintGapRemaining = SprintGap
  - `void onPacketSprintCleared()` — ARM → WALK, reset walkC03Count to 0
  - `void onWalkC03()` — if WALK, increment count; when ≥ WalkC03s → HIT
  - `void onOwnedHitFinished()` — HIT → RECOVER
  - `void onRecovered()` — RECOVER → SPRINT, set gap
  - `void onWtapOrDisable()` — reset to SPRINT, clear flags

- [x] **Step 1: Write the failing test**

```java
package gnu.client.module.modules.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepSprintPulseStateTest {

    @Test
    public void startsInSprint() {
        KeepSprintPulseState s = new KeepSprintPulseState();
        assertEquals(KeepSprintPulseState.Phase.SPRINT, s.getPhase());
        assertFalse(s.shouldSuppressSprintKey());
        assertFalse(s.shouldDeferAttack());
        assertFalse(s.canOwnAttack());
    }

    @Test
    public void imminentWithGapElapsedArmsAndDefers() {
        KeepSprintPulseState s = new KeepSprintPulseState();
        s.setSprintGap(0);
        s.onImminentAttack();
        assertEquals(KeepSprintPulseState.Phase.ARM, s.getPhase());
        assertTrue(s.shouldSuppressSprintKey());
        assertTrue(s.shouldDeferAttack());
        assertFalse(s.canOwnAttack());
    }

    @Test
    public void walkC03sReachHit() {
        KeepSprintPulseState s = new KeepSprintPulseState();
        s.setWalkC03s(1);
        s.setSprintGap(0);
        s.onImminentAttack();
        s.onPacketSprintCleared();
        assertEquals(KeepSprintPulseState.Phase.WALK, s.getPhase());
        s.onWalkC03();
        assertEquals(KeepSprintPulseState.Phase.HIT, s.getPhase());
        assertTrue(s.canOwnAttack());
        assertFalse(s.shouldDeferAttack());
    }

    @Test
    public void ownedHitThenRecoverThenSprintWithGap() {
        KeepSprintPulseState s = new KeepSprintPulseState();
        s.setWalkC03s(1);
        s.setSprintGap(0);
        s.onImminentAttack();
        s.onPacketSprintCleared();
        s.onWalkC03();
        s.onOwnedHitFinished();
        assertEquals(KeepSprintPulseState.Phase.RECOVER, s.getPhase());
        s.setSprintGap(2);
        s.onRecovered();
        assertEquals(KeepSprintPulseState.Phase.SPRINT, s.getPhase());
        s.onImminentAttack(); // gap remaining — stay SPRINT
        assertEquals(KeepSprintPulseState.Phase.SPRINT, s.getPhase());
        s.onClientTick();
        s.onClientTick();
        s.onImminentAttack();
        assertEquals(KeepSprintPulseState.Phase.ARM, s.getPhase());
    }

    @Test
    public void wtapResets() {
        KeepSprintPulseState s = new KeepSprintPulseState();
        s.setSprintGap(0);
        s.onImminentAttack();
        s.onWtapOrDisable();
        assertEquals(KeepSprintPulseState.Phase.SPRINT, s.getPhase());
        assertFalse(s.shouldSuppressSprintKey());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintPulseStateTest -q`

Expected: FAIL (class not found)

- [ ] **Step 3: Write minimal implementation**

```java
package gnu.client.module.modules.combat;

public final class KeepSprintPulseState {

    public enum Phase { SPRINT, ARM, WALK, HIT, RECOVER }

    private Phase phase = Phase.SPRINT;
    private int walkC03s = 1;
    private int sprintGap = 3;
    private int walkC03Count;
    private int sprintGapRemaining;

    public Phase getPhase() { return phase; }

    public void reset() {
        phase = Phase.SPRINT;
        walkC03Count = 0;
        sprintGapRemaining = 0;
    }

    public void setWalkC03s(int n) {
        walkC03s = Math.max(1, n);
    }

    public void setSprintGap(int n) {
        sprintGap = Math.max(0, n);
    }

    public boolean shouldSuppressSprintKey() {
        return phase == Phase.ARM || phase == Phase.WALK || phase == Phase.HIT;
    }

    public boolean shouldDeferAttack() {
        return phase == Phase.ARM || phase == Phase.WALK;
    }

    public boolean canOwnAttack() {
        return phase == Phase.HIT;
    }

    public void onImminentAttack() {
        if (phase != Phase.SPRINT)
            return;
        if (sprintGapRemaining > 0)
            return;
        phase = Phase.ARM;
        walkC03Count = 0;
    }

    public void onClientTick() {
        if (phase == Phase.SPRINT && sprintGapRemaining > 0)
            sprintGapRemaining--;
    }

    public void onPacketSprintCleared() {
        if (phase == Phase.ARM) {
            phase = Phase.WALK;
            walkC03Count = 0;
        }
    }

    public void onWalkC03() {
        if (phase != Phase.WALK)
            return;
        walkC03Count++;
        if (walkC03Count >= walkC03s)
            phase = Phase.HIT;
    }

    public void onOwnedHitFinished() {
        if (phase == Phase.HIT)
            phase = Phase.RECOVER;
    }

    public void onRecovered() {
        if (phase != Phase.RECOVER)
            return;
        phase = Phase.SPRINT;
        sprintGapRemaining = sprintGap;
        walkC03Count = 0;
    }

    public void onWtapOrDisable() {
        reset();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintPulseStateTest -q`

Expected: BUILD SUCCESSFUL

---

### Task 2: KeepSprintLogic helpers (TDD)

**Files:**
- Rewrite: `src/main/java/gnu/client/module/modules/combat/KeepSprintLogic.java`
- Rewrite: `src/test/java/gnu/client/module/modules/combat/KeepSprintLogicTest.java`

**Interfaces:**
- Produces:
  - `boolean shouldSuppressJump(boolean moduleEnabled, boolean clientSprinting)`
  - `boolean shouldYieldToWtap(boolean moduleEnabled, boolean wtapSuppress)` — true when module on and wtap suppress
  - `boolean shouldOwnHit(boolean moduleEnabled, boolean wtapSuppress, boolean canOwnAttack)`

- [x] **Step 1: Write the failing test**

```java
package gnu.client.module.modules.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepSprintLogicTest {

    @Test
    public void jumpOnlyWhileSprinting() {
        assertTrue(KeepSprintLogic.shouldSuppressJump(true, false));
        assertFalse(KeepSprintLogic.shouldSuppressJump(true, true));
        assertFalse(KeepSprintLogic.shouldSuppressJump(false, false));
    }

    @Test
    public void yieldToWtap() {
        assertTrue(KeepSprintLogic.shouldYieldToWtap(true, true));
        assertFalse(KeepSprintLogic.shouldYieldToWtap(true, false));
        assertFalse(KeepSprintLogic.shouldYieldToWtap(false, true));
    }

    @Test
    public void ownHitOnlyWhenArmed() {
        assertTrue(KeepSprintLogic.shouldOwnHit(true, false, true));
        assertFalse(KeepSprintLogic.shouldOwnHit(true, false, false));
        assertFalse(KeepSprintLogic.shouldOwnHit(true, true, true));
        assertFalse(KeepSprintLogic.shouldOwnHit(false, false, true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest -q`

Expected: FAIL on missing `shouldYieldToWtap` / `shouldOwnHit` (or soft helpers still present)

- [ ] **Step 3: Write minimal implementation**

```java
package gnu.client.module.modules.combat;

public final class KeepSprintLogic {

    private KeepSprintLogic() {}

    public static boolean shouldSuppressJump(boolean moduleEnabled, boolean clientSprinting) {
        return moduleEnabled && !clientSprinting;
    }

    public static boolean shouldYieldToWtap(boolean moduleEnabled, boolean wtapSuppress) {
        return moduleEnabled && wtapSuppress;
    }

    public static boolean shouldOwnHit(boolean moduleEnabled, boolean wtapSuppress, boolean canOwnAttack) {
        return moduleEnabled && !wtapSuppress && canOwnAttack;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest -q`

Expected: BUILD SUCCESSFUL

---

### Task 3: KeepSprintModule pulse wiring + settings

**Files:**
- Rewrite: `src/main/java/gnu/client/module/modules/combat/KeepSprintModule.java`
- Modify: `src/test/java/gnu/client/module/modules/combat/KeepSprintModuleTest.java`

**Interfaces:**
- Consumes: `KeepSprintPulseState`, `KeepSprintLogic`, `Mc`, `AuraCombatPacketGuard`, `WTapModule.shouldSuppressSprintKey()`
- Produces (same static surface KA/Sprint already call):
  - `shouldSuppressSprintKey()`, `shouldDeferKillAuraAttack()`, `tryBeginFightForImminentAttack(player)`, `onBeforeKillAuraAttack`, `onAfterKillAuraAttack`, `maintainWalkState`, `onAfterWalking`, `onClientTickStart`, `onKillAuraTargetLost`, `patchMovementInput`

**Behavior to implement:**

1. Settings: `SliderSetting("WalkC03s", 1f, 1f, 3f, 1f)`, `SliderSetting("SprintGap", 3f, 0f, 10f, 1f)`, `BoolSetting("Debug", false)`. Description: `"Sprint-key pulse KeepSprint (Grim)"`.

2. One `KeepSprintPulseState` instance field on the module; `instance()` returns module; static methods delegate.

3. `onEnable`/`onDisable`/`onKillAuraTargetLost`/`onWtap`: `state.onWtapOrDisable()`; on disable release suppress by resetting state.

4. Each tick (`onClientTickStart`): if yield → reset state and return. Else `state.setWalkC03s((int) walkC03s.getValue())`, `state.setSprintGap((int) sprintGap.getValue())`, `state.onClientTick()`.

5. `maintainWalkState` (preUpdate / beforeWalking): if yield return. If phase ARM: `Mc.setSprintKeyState(false)`, `Mc.setClientSprinting(player, false)`, `Mc.clearSprintToggleTimer(player)`. If still `Mc.getServerSprintState(player)` and `AuraCombatPacketGuard.isSprintSlotFree()`, `Mc.sendSprintActionPacket(player, false)`. If `!Mc.getServerSprintState(player)`, `state.onPacketSprintCleared()`.

6. `onAfterWalking`: if phase WALK (or just cleared into WALK this tick), `state.onWalkC03()`. If phase RECOVER and (`Mc.isClientSprinting(player)` or sprint key will be held), call `state.onRecovered()` once sprint key is held via `Mc.setSprintKeyState(true)` at start of RECOVER handling.

7. `tryBeginFightForImminentAttack`: if yield false; else `state.onImminentAttack()`; return `false` always (do not cancel tick — defer handles). Spec: imminent arms when gap elapsed.

8. `shouldDeferKillAuraAttack`: yield → false; else `state.shouldDeferAttack()`.

9. `shouldSuppressSprintKey`: yield → false; else `state.shouldSuppressSprintKey()`.

10. `onBeforeKillAuraAttack`: if `KeepSprintLogic.shouldOwnHit(...)`: `Mc.setSprintKeyState(false)`; `Mc.setClientSprinting(player, false)`; return `true`. Else return `false` (vanilla `×0.6`).

11. `onAfterKillAuraAttack`: if `owned`: `state.onOwnedHitFinished()`; `Mc.setSprintKeyState(true)` (RECOVER key only — no START/`setClientSprinting`); debug phase. Else no-op for pulse.

12. `patchMovementInput`: unchanged jump suppress via `KeepSprintLogic.shouldSuppressJump`.

13. Debug: optional chat on phase change (track `lastDebugPhase`).

- [ ] **Step 1: Update module test for new settings**

```java
@Test
public void defaultSettings() {
    KeepSprintModule m = new KeepSprintModule();
    assertEquals("KeepSprint", m.getName());
    SliderSetting walk = null;
    SliderSetting gap = null;
    BoolSetting debug = null;
    for (Setting<?> s : m.getSettings()) {
        if ("WalkC03s".equals(s.getName())) walk = (SliderSetting) s;
        if ("SprintGap".equals(s.getName())) gap = (SliderSetting) s;
        if ("Debug".equals(s.getName())) debug = (BoolSetting) s;
    }
    assertNotNull(walk);
    assertEquals(1f, walk.getValue(), 0.01f);
    assertNotNull(gap);
    assertEquals(3f, gap.getValue(), 0.01f);
    assertNotNull(debug);
    assertFalse(debug.getValue());
}
```

- [ ] **Step 2: Run test — expect fail / wrong settings**

Run: `./gradlew test --tests gnu.client.module.modules.combat.KeepSprintModuleTest -q`

- [ ] **Step 3: Implement `KeepSprintModule` as specified above**

Keep all existing public static method names so `KillAuraModule` / `SprintModule` / `PlayerUpdateHook` need no signature changes. `tryBeginFightForImminentAttack` must call `onImminentAttack` and return `false`.

RECOVER completion: in `onAfterWalking`, if phase is RECOVER: `Mc.setSprintKeyState(true)`; then `state.onRecovered()`.

- [ ] **Step 4: Run all KeepSprint tests + build**

Run:

```bash
./gradlew test --tests 'gnu.client.module.modules.combat.KeepSprint*' -q
./gradlew build -q
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Manual in-game checklist (no code)

- [ ] **Step 1: Install jar** into Prism `1.8.9` mods (replace `gnuclient-*.jar`)

- [ ] **Step 2: Config** — Enable KeepSprint + Sprint + KillAura. Disable WTap, Blink, Speed. Debug on. WalkC03s=1, SprintGap=3.

- [ ] **Step 3: Fight on Grim** — Expect Debug ARM→WALK→HIT→RECOVER; speed closer to Rise (brief dips); no `.076866` AttackSlow Simulation chains.

- [ ] **Step 4: WTap on** — KeepSprint should yield (no suppress/defer from pulse).

- [ ] **Step 5: If still AttackSlow flags** — try WalkC03s=2; if still flags while Debug shows HIT with packet sprint still true, fix ARM STOP sync before changing state machine again.

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| SPRINT→ARM→WALK→HIT→RECOVER | Task 1–3 |
| WalkC03s / SprintGap / Debug | Task 3 |
| Sprint key + SprintModule suppress | Task 3 (`shouldSuppressSprintKey`) |
| STOP via walking / fallback slot-free STOP | Task 3 `maintainWalkState` |
| Defer until HIT | Task 1 + 3 |
| Own hit skip `×0.6` only in HIT | Task 2–3 |
| No immediate START after owned hit | Task 3 `onAfter` |
| Jump suppress | Task 2–3 |
| WTap yield | Task 2–3 |
| KA hooks unchanged names | Task 3 |
| Manual Grim verify | Task 4 |

## Placeholder scan

None intentional. Commit steps omitted per repo preference unless user requests commits.
