# KeepSprint Dual-Mode — Revised Coder Spec

**Suggested design doc path (coder may create):** `docs/superpowers/specs/2026-08-09-keepsprint-grim-continuous-design.md`

---

## 1. Approach

Add a **ModeSetting** to KeepSprint with two modes:

- **StopWalk** (default): Preserve existing behavior. Stop sprint before each KillAura hit, wait for walk C03(s) so Grim `lastSprinting` clears, skip client `×0.6` on owned hits, restore sprint after.
- **Grim** (new): Never stop sprint — no STOP C0B, no walk-C03 stop pulse, no defer, no sprint-key suppress. Accept vanilla `×0.6` on every hit while packet-sprinting so client motion matches Grim's mandatory AttackSlow. Restore client sprint **same tick, before `PreMotionEvent`**, so walking sync does not emit STOP C0B (BadPacketsF/X).

### Why two modes exist (Grim constraint)

On 1.8 standard living hits, if `GrimPlayer.lastSprinting == true` at attack time:

| Grim component | Behavior |
|----------------|----------|
| `PacketPlayerAttack` | Sets `minAttackSlow = maxAttackSlow = 1` |
| `PredictionEngine.addAttackSlowToPossibilities` | Applies `motionX/Z × 0.6` |
| `OffsetHandler` (Simulation) | Flags if client **skips** `×0.6` while Grim expects AttackSlow |

**There is no flagless path that both skips `×0.6` and keeps `lastSprinting == true`.**

- **StopWalk** evades AttackSlow by clearing `lastSprinting` (walk + STOP), then skipping slow.
- **Grim** bypasses Simulation by **matching** AttackSlow (accept `×0.6`), not by skipping it.

"Fully bypasses Grim" in Grim mode means **no Simulation flags**, not "no slowdown."

### Grim mode tick order (timing invariant)

Attack runs inside `onBeforeWalkingAttack` → `tryPerformAttack`, **before** `PreMotionEvent` is constructed:

```
onBeforeWalkingAttack → tryPerformAttack
  → onBeforeKillAuraAttack   (Grim: return false, no STOP)
  → vanilla attack           (×0.6 + setSprinting(false))
  → onAfterKillAuraAttack    (Grim: restore client sprint + sprint key)
→ PreMotionEvent built with isSprinting() == true
→ serverSprintState still true → no STOP C0B emitted
```

No new hook is needed; restore must complete inside `tryPerformAttack`.

### StopWalk state hygiene (Grim / mode switch)

Whenever the module is **not** in StopWalk mode (`!isStopWalk()`), **`KeepSprintStopState` must be eagerly reset to IDLE** and **`stopSent` must be cleared**. This prevents stale STOPPING/READY phase, defer, or suppress from leaking after a mid-fight mode switch (e.g. StopWalk → Grim while STOPPING).

**Recommended implementation:** private helper `clearStopWalkState()` (`state.reset(); stopSent = false;`) invoked:
- **Every Grim `onClientTickStart` tick** (first action, regardless of yielding), and
- At the top of any hook path that runs while `!isStopWalk()` (defense in depth).

### Shared yield rules (both modes)

- Yield when `WTapModule.shouldSuppressSprintKey()` is true.
- Yield when `hurtTime > 0` (vanilla KB / AttackSlow interaction).
- Scope: KillAura attacks only (not vanilla / AutoClicker clicks).

---

## 2. Components / Files

| File | Action |
|------|--------|
| `src/main/java/gnu/client/module/modules/combat/KeepSprintModule.java` | Add `ModeSetting`; mode dispatch in all static hooks; `clearStopWalkState()` helper; constructor `visibleWhen`; updated module description |
| `src/main/java/gnu/client/module/modules/combat/KeepSprintLogic.java` | Add mode helpers; gate existing StopWalk-only logic |
| `src/main/java/gnu/client/module/modules/combat/KeepSprintStopState.java` | **No logic changes** (StopWalk-only state machine) |
| `src/test/java/gnu/client/module/modules/combat/KeepSprintLogicTest.java` | Add mode gating + Grim restore predicate tests |
| `src/test/java/gnu/client/module/modules/combat/KeepSprintModuleTest.java` | Assert Mode setting, default StopWalk, WalkC03s visibility, module description text |
| `src/test/java/gnu/client/module/modules/combat/KeepSprintStopStateTest.java` | **Unchanged** |
| `docs/superpowers/specs/2026-08-09-keepsprint-grim-continuous-design.md` | Optional: copy this spec (project pattern) |

**No changes expected:**

- `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` (hooks already wired)
- `src/main/java/gnu/client/runtime/PlayerUpdateHook.java`
- `src/main/java/gnu/client/module/modules/movement/SprintModule.java`
- `src/main/java/gnu/client/module/modules/movement/SpeedModule.java`
- `src/main/java/gnu/client/runtime/mc/Mc.java`
- `src/main/java/gnu/client/runtime/MovementInputHook.java`

---

## 3. Interfaces & Contracts

### 3.1 ModeSetting and constructor wiring (VelocityModule pattern)

Declare settings as fields (including `mode`), then register visibility in the **constructor body after `super(...)`**, once `mode` is initialized:

```java
private static final List<String> MODE_NAMES = unmodifiableList(asList("StopWalk", "Grim"));
private static final int DEFAULT_MODE_INDEX = 0;

public final ModeSetting mode =
    addSetting(new ModeSetting("Mode", DEFAULT_MODE_INDEX, MODE_NAMES));
private final SliderSetting walkC03s = ...;
private final BoolSetting debug = ...;

public KeepSprintModule() {
    super("KeepSprint", MODULE_DESCRIPTION, Category.COMBAT);
    walkC03s.visibleWhen(() -> isStopWalk());
}
```

- Default mode: **StopWalk** (index 0).
- **Do not** add per-mode tooltips — not a UI deliverable for this task.

### 3.2 Module description (single string, both modes)

Replace the current one-line description with a string that documents **both** modes, e.g.:

```java
private static final String MODULE_DESCRIPTION =
    "KA sprint — StopWalk: stop before hit, skip ×0.6 after walk C03(s); "
    + "Grim: never stop sprint, match AttackSlow (×0.6), bypasses Simulation not the slow";
```

Coder may tighten wording; both mode names and both semantics must appear in the module `super(...)` description argument.

### 3.3 KeepSprintLogic additions

```java
static boolean isStopWalkMode(String modeName);
static boolean isGrimMode(String modeName);
static boolean usesStopWalkStateMachine(boolean enabled, String modeName);
static boolean shouldRestoreSprintAfterGrimHit(boolean enabled, String modeName, boolean yielding);
```

Existing helpers (`shouldBeginStop`, `shouldOwnHit`, `shouldSuppressJump`, `shouldYieldToWtap`, `shouldSkipForHurt`) remain unchanged. Callers gate StopWalk-only paths with `usesStopWalkStateMachine` or `isStopWalkMode`.

### 3.4 KeepSprintModule private helpers

```java
private void clearStopWalkState();   // state.reset(); stopSent = false;
private boolean isStopWalk();        // isStopWalkMode(mode.getCurrentMode())
private boolean isGrim();
```

When `!isStopWalk()`, call `clearStopWalkState()` before any Grim hook logic (mandatory every Grim `onClientTickStart` tick).

### 3.5 Mode semantics

| Mode | Index | Speed feel | Grim interaction |
|------|-------|------------|------------------|
| **StopWalk** | 0 | Rise-like: no `×0.6` when armed | Clears `lastSprinting`; Grim does **not** schedule AttackSlow |
| **Grim** | 1 | Vanilla attack slow each hit (`×0.6`) | Grim **does** schedule AttackSlow; client matches → Simulation clean |

### 3.6 Exact hook behavior matrix: StopWalk vs Grim

**Global precondition for every hook:** if module disabled → early return (existing).

**When `!isStopWalk()` (Grim):** call `clearStopWalkState()` at hook entry (defense in depth). Grim-specific rows below assume state is already cleared.

**When `yielding()`** (`WTapModule.shouldSuppressSprintKey()` OR `hurtTime > 0`):
- **StopWalk:** reset state, clear `stopSent`, return.
- **Grim:** no yield-specific state work beyond the mandatory `clearStopWalkState()` already done at tick start / hook entry; skip Grim sprint restore in `onAfterKillAuraAttack`.

| Hook | StopWalk | Grim |
|------|----------|------|
| **`tryBeginFightForImminentAttack(player)`** | When imminent attack and needs stop and not READY: `state.beginStop()`, `stopSent = false`, `applyStop(player)`. Always returns `false`. | **`clearStopWalkState()`** then no-op. Never `beginStop`, never `applyStop`. Always returns `false`. |
| **`shouldDeferKillAuraAttack()`** | Returns `true` when `state.getPhase() == STOPPING`. | **`clearStopWalkState()`** then always **`false`**. |
| **`shouldSuppressSprintKey()`** | Returns `true` when phase is `STOPPING` or `READY`. | **`clearStopWalkState()`** then always **`false`**. |
| **`maintainWalkState()`** | During `STOPPING` or `READY`: call `applyStop(player)`. | **`clearStopWalkState()`** then no-op. Never `applyStop`. |
| **`onAfterWalking()`** | When phase is `STOPPING`: `state.onWalkC03(cleared)` where `cleared = !Mc.getServerSprintState(player)`. | **`clearStopWalkState()`** then no-op. |
| **`onBeforeKillAuraAttack(player)`** | If `shouldOwnHit(...)`: `applyStop(player)`, return `true`. Else return `false`. | **`clearStopWalkState()`** then always return **`false`**. Never own hit; vanilla applies `×0.6`. |
| **`onAfterKillAuraAttack(player, owned)`** | If `owned == false`: return. If yielding: reset state. Else: `state.onOwnedHitFinished()`, `stopSent = false`, `Mc.setSprintKeyState(true)`, `Mc.setClientSprinting(player, true)`. | If yielding: return (no restore). If not yielding: `Mc.setSprintKeyState(true)`, `Mc.setClientSprinting(player, true)`. Do **not** send START C0B if `Mc.getServerSprintState(player)` already true. Ignore `owned`. |
| **`patchMovementInput(movementInput)`** | If not yielding and not client-sprinting: set `jump = false`. | No-op. Never suppress jump. |
| **`onClientTickStart()`** | If yielding: reset state, clear `stopSent`. | **`clearStopWalkState()` every tick** (first action, even when not yielding). If yielding: return after clear. |
| **`onKillAuraTargetLost(player)`** | Reset state, clear `stopSent`. | **`clearStopWalkState()`**. |
| **`onKillAuraTargetReady(player)`** | No-op. | No-op. |
| **`onEnable()`** | Reset state, clear `stopSent`. | Same. |
| **`onDisable()`** | `state.onWtapOrDisable()`, clear `stopSent`. | Same. |

### 3.7 Grim `onAfterKillAuraAttack` restore contract

When Grim mode and not yielding, after vanilla attack:

1. `Mc.setSprintKeyState(true)`
2. `Mc.setClientSprinting(player, true)`
3. **Do not** call `Mc.sendSprintActionPacket(player, true)` if `Mc.getServerSprintState(player)` is already `true`
4. **Never** call `applyStop()` or set `stopSent = true`
5. **Never** send STOP C0B at any point in Grim mode

### 3.8 StopWalk invariants (unchanged)

- Never own hit unless `phase == READY`.
- Never skip `×0.6` unless `onBeforeKillAuraAttack` returns `true`.
- STOP via walking sync preferred; explicit STOP only if `AuraCombatPacketGuard.isSprintSlotFree()`.
- `applyStop`: release sprint key, clear client sprint, send STOP C0B once per stop cycle if slot free.

### 3.9 Grim invariants

- **`onBeforeKillAuraAttack` never returns `true`.**
- **Never** send STOP C0B, defer attack, or suppress sprint key.
- **Always** restore client sprint same tick after attack when not yielding.
- Accept vanilla `×0.6` every hit while packet-sprinting.
- Bypass Simulation by **matching** AttackSlow, not skipping it.
- **Whenever `!isStopWalk()`:** `KeepSprintStopState` must be IDLE and `stopSent == false`. Enforced by `clearStopWalkState()` on every Grim `onClientTickStart` tick and at Grim hook entry.

### 3.10 Edge cases

| Case | StopWalk | Grim |
|------|----------|------|
| WTap suppress | Yield; no ownership | Yield; no restore; state cleared each tick |
| `hurtTime > 0` | Yield | Yield; no restore; state cleared each tick |
| Mode switch StopWalk → Grim mid-STOPPING | N/A | Next Grim `onClientTickStart`: state IDLE; no defer/suppress |
| Mode switch Grim → StopWalk mid-fight | Fresh IDLE; new stop cycle on next imminent hit | N/A |
| Blink active | `sendSprintActionPacket` no-op (existing) | Never sends C0B |
| Not sprinting at hit | Normal StopWalk flow | Vanilla no-slow; restore still safe if SprintModule expects key held |
| KA GRIM autoblock (double C02) | Unchanged | Restore after attack call; timing unchanged |
| Module disable / target lost | Reset state | `clearStopWalkState()` |

---

## 4. Implementation Order

1. **KeepSprintLogic — mode helpers (TDD first)**
   - Add `isStopWalkMode`, `isGrimMode`, `usesStopWalkStateMachine`, `shouldRestoreSprintAfterGrimHit`.
   - Write unit tests in `KeepSprintLogicTest` before implementation.

2. **KeepSprintModule — ModeSetting + constructor**
   - Add `ModeSetting` with `"StopWalk"`, `"Grim"`, default index 0.
   - Update `super(...)` with combined `MODULE_DESCRIPTION` (both modes).
   - In constructor **after** `super(...)`, register `walkC03s.visibleWhen(() -> isStopWalk())`.
   - Add `clearStopWalkState()`, `isStopWalk()`, `isGrim()`.

3. **Gate all StopWalk machinery**
   - Wrap stop/defer/suppress/walk-count/ownership with `isStopWalk()` checks.
   - At Grim hook entry: call `clearStopWalkState()`.

4. **Implement Grim `onClientTickStart`**
   - **Every tick** when Grim: `clearStopWalkState()` first (not only when yielding).

5. **Implement Grim branch in `onAfterKillAuraAttack`**
   - When `isGrim() && !yielding`: restore sprint key + `Mc.setClientSprinting(player, true)` regardless of `owned`.
   - Do not send START C0B if server sprint already true.

6. **Implement remaining Grim no-ops**
   - Per matrix §3.6.

7. **KeepSprintModuleTest**
   - Mode default StopWalk; WalkC03s hidden in Grim; module description mentions both modes.

8. **Optional design doc** at path above.

9. **Run verification** (Section 6).

---

## 5. Out of Scope

- Changing Grim anticheat itself.
- Rise source analysis or parity.
- Soft always-`×0.6` as default or replacement for StopWalk.
- KeepSprint on vanilla / AutoClicker clicks.
- Overriding WTap / SprintTap ownership.
- Per-AC mode lists beyond StopWalk / Grim.
- Per-mode setting tooltips (semantics live in module description only).
- New hooks in KillAura or PlayerUpdateHook.
- Implementing code (coder's job).

---

## 6. Verification Criteria / Acceptance / Test Plan

### 6.1 Acceptance criteria

1. **ModeSetting** with `"StopWalk"` and `"Grim"`; default **StopWalk**.
2. **Module description** documents both modes in one string passed to `super(...)`.
3. **`walkC03s.visibleWhen`** registered in constructor after `mode` is initialized.
4. **StopWalk regression:** Identical to current — stop pulse, defer during STOPPING, own hit only when READY, walk C03 count, jump suppress when not sprinting.
5. **Grim mode:**
   - No STOP C0B on KA hits.
   - No defer or sprint-key suppress from KeepSprint.
   - Vanilla `×0.6` while packet-sprinting.
   - Client sprint restored same tick before walking sync.
   - **`clearStopWalkState()` every Grim tick** in `onClientTickStart`.
6. **Mode-switch hygiene:** StopWalk → Grim while STOPPING clears state immediately; Grim → StopWalk starts fresh IDLE cycle.
7. Config persists selected mode.

### 6.2 Unit test plan

**KeepSprintLogicTest — new cases:**

| Test | Assert |
|------|--------|
| `isStopWalkModeRecognized` | `"StopWalk"` → true; `"Grim"` → false |
| `isGrimModeRecognized` | `"Grim"` → true; `"StopWalk"` → false |
| `usesStopWalkStateMachine` | true only when enabled + StopWalk |
| `shouldRestoreSprintAfterGrimHit` | true when enabled + Grim + not yielding; false otherwise |
| Existing tests | All pass unchanged |

**KeepSprintModuleTest — extend:**

| Test | Assert |
|------|--------|
| `defaultModeIsStopWalk` | Mode exists; default `"StopWalk"` |
| `walkC03sOnlyVisibleInStopWalk` | Hidden when mode is Grim |
| `moduleDescriptionDocumentsBothModes` | Description contains `"StopWalk"` and `"Grim"` and both semantics (skip ×0.6 / match AttackSlow or equivalent) |

**KeepSprintStopStateTest:** unchanged; all pass.

### 6.3 Verification command

```bash
./gradlew test --tests gnu.client.module.modules.combat.KeepSprintLogicTest \
  --tests gnu.client.module.modules.combat.KeepSprintModuleTest \
  --tests gnu.client.module.modules.combat.KeepSprintStopStateTest
```

### 6.4 Manual test plan

| Scenario | Expected |
|----------|----------|
| KA + Sprint + KeepSprint **Grim** on Grim server | No Simulation AttackSlow chains; sprint never stops; `×0.6` per hit |
| KA + Sprint + KeepSprint **StopWalk** on Grim server | Brief walk dips; no `×0.6` when armed; matches pre-change behavior |
| WTap enabled | Both modes yield; WTap owns sprint |
| Debug on + StopWalk | Phase transitions log (STOPPING → READY → IDLE) |
| **StopWalk → Grim while STOPPING** | Within one tick: no defer, no sprint-key suppress, no `applyStop`; KA attacks normally in Grim |
| **Grim → StopWalk mid-fight** | State IDLE; next hit begins fresh stop cycle (not stuck READY/STOPPING) |
| Mode switch StopWalk ↔ Grim (general) | Hook behavior matches matrix; no stale StopWalk state in Grim |

---

This spec is complete and sufficient for implementation without further design questions.

[REDACTED]