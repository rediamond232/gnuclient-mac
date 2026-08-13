# KillAura FAKE Visual Block Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make KillAura Auto-block FAKE show a continuous first-person sword block pose (classic 1.7 dig-while-blocking), routed through the same `ItemRenderer` BLOCK branch so Animations modes apply.

**Architecture:** Keep existing `fakeBlockState` / `KillAuraAutoBlock.isFakeBlocking()`. Expose `KillAuraModule.isFakeBlocking()` for render. In `ItemRenderer.renderItemInFirstPerson` only, redirect `EntityPlayer.getItemInUseCount()` so when fake-blocking and the player is not already using an item, the count is treated as `> 0`. Held sword’s `getItemUseAction()` is already `BLOCK`, so vanilla enters the block branch; existing `MixinItemRendererAnimations` injects run unchanged. No packet / no entity use-item mutation.

**Tech Stack:** Java 8, Forge 1.8.9, Sponge Mixin, existing KillAura / Animations modules, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-08-killaura-fakeblock-visual-design.md`

## Global Constraints

- Render-scoped only — never spoof `EntityPlayerSP.isUsingItem()` globally.
- No use/block packets from FAKE.
- Swings must still play (`getSwingProgress` unchanged).
- Animations module GL path must keep working without edits when BLOCK branch is entered.
- Follow existing static KA helper patterns (`shouldAutoBlock()`, `isAutoBlockHandlingBlock()`).

## File map

| Path | Responsibility |
|------|----------------|
| `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` | Static `isFakeBlocking()` bridge |
| `src/main/java/gnu/client/mixin/impl/render/MixinItemRendererFakeBlock.java` | FP-only `getItemInUseCount` redirect |
| `src/main/resources/mixins.gnuclient.json` | Register new mixin |
| `src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java` | Document FAKE index + fake-block purity notes if useful |
| `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java` | No logic change (already sets `fakeBlockState` in FAKE) |

---

### Task 1: `KillAuraModule.isFakeBlocking()` API

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` (near `isAutoBlockHandlingBlock` ~L239)
- Modify: `src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java`

**Interfaces:**
- Consumes: `KillAuraAutoBlock.isFakeBlocking()` → `boolean` (`fakeBlockState && Mc.isHoldingSword()`)
- Produces: `public static boolean KillAuraModule.isFakeBlocking()`

- [ ] **Step 1: Extend the unit test for FAKE mode constant + document expected gate**

Add to `KillAuraAutoBlockTest.java`:

```java
@Test
public void fakeModeIndexIsEight() {
    assertEquals(8, KillAuraAutoBlock.FAKE);
}

@Test
public void fakeIsNotAShouldAutoBlockMode() {
    // Visual-only: must not drive NoSlow / real block session helpers
    assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.FAKE));
}
```

(`fakeIsNotAShouldAutoBlockMode` may already be covered by `shouldAutoBlockModesMatchReference` — if so, only add `fakeModeIndexIsEight`.)

- [ ] **Step 2: Run tests**

```bash
./gradlew test --tests gnu.client.module.modules.combat.killaura.KillAuraAutoBlockTest
```

Expected: PASS (or FAIL only on missing `fakeModeIndexIsEight` until added — then PASS).

- [ ] **Step 3: Add static helper on `KillAuraModule`**

Place immediately after `isAutoBlockHandlingBlock()`:

```java
/**
 * Client-side visual sword block (Auto-block FAKE and other modes that set
 * {@code fakeBlockState}). Render mixins only — not a real use-item session.
 */
public static boolean isFakeBlocking() {
    Module module = ModuleManager.instance().getModule("KillAura");
    if (!(module instanceof KillAuraModule) || !module.isEnabled())
        return false;
    return ((KillAuraModule) module).autoBlockHelper.isFakeBlocking();
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava -q
```

Expected: SUCCESS.

- [ ] **Step 5: Commit** (skip if user did not ask to commit)

```bash
git add src/main/java/gnu/client/module/modules/combat/KillAuraModule.java \
  src/test/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlockTest.java
git commit -m "$(cat <<'EOF'
Expose KillAura isFakeBlocking for first-person render.

EOF
)"
```

---

### Task 2: ItemRenderer fake-block mixin

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/render/MixinItemRendererFakeBlock.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

**Interfaces:**
- Consumes: `KillAuraModule.isFakeBlocking()`
- Produces: FP render enters `EnumAction.BLOCK` branch when fake-blocking and `getItemInUseCount()` would otherwise be `0`

**Vanilla gate (1.8.9 MCP):** `ItemRenderer.renderItemInFirstPerson` roughly:

```text
if (player.getItemInUseCount() > 0) {
    switch (stack.getItemUseAction()) {
        case BLOCK:
            transformFirstPersonItem(...);  // Animations redirects ordinal 2
            doBlockTransformations();       // Animations injects here
            ...
    }
} else {
    // normal swing / hold
}
```

Sword `ItemStack.getItemUseAction()` is already `EnumAction.BLOCK`. Forcing use-count `> 0` for the render method is enough.

- [ ] **Step 1: Create mixin**

Create `src/main/java/gnu/client/mixin/impl/render/MixinItemRendererFakeBlock.java`:

```java
package gnu.client.mixin.impl.render;

import gnu.client.module.modules.combat.KillAuraModule;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererFakeBlock {

    /**
     * When KillAura reports fake-blocking, treat use-count as active so the
     * sword BLOCK first-person branch runs. Scoped to this method only.
     */
    @Redirect(
            method = "renderItemInFirstPerson",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;getItemInUseCount()I"))
    private int gnu$fakeBlockItemInUseCount(EntityPlayer player) {
        int real = player.getItemInUseCount();
        if (real > 0) {
            return real;
        }
        if (KillAuraModule.isFakeBlocking()) {
            return 1;
        }
        return 0;
    }
}
```

**If the invoke owner in bytecode is `AbstractClientPlayer` / `EntityPlayerSP` instead of `EntityPlayer`:** change the `target` to match the failing apply error from Mixin (keep method name `getItemInUseCount()I`). Prefer the most specific owner that appears in the method.

**If Mixin reports 0 injection points:** decompile / check `minecraft-client` mapping and adjust owner; do **not** fall back to redirecting `isUsingItem()` on the player entity outside this method.

- [ ] **Step 2: Register mixin**

In `src/main/resources/mixins.gnuclient.json`, add next to the other ItemRenderer entry:

```json
"render.MixinItemRendererFakeBlock",
```

Keep JSON valid (comma after previous `"render.MixinItemRendererAnimations"` entry).

- [ ] **Step 3: Compile / mixin apply**

```bash
./gradlew compileJava -q
```

Expected: SUCCESS, no mixin apply errors for `gnu$fakeBlockItemInUseCount`.

If apply fails with “could not locate” / “0 targets”, fix the `@At` target owner as in Step 1 note and recompile.

- [ ] **Step 4: Commit** (skip if user did not ask to commit)

```bash
git add src/main/java/gnu/client/mixin/impl/render/MixinItemRendererFakeBlock.java \
  src/main/resources/mixins.gnuclient.json
git commit -m "$(cat <<'EOF'
Force first-person block pose when KillAura fake-blocks.

EOF
)"
```

---

### Task 3: Manual verification checklist

**Files:** none (in-game)

- [ ] **Step 1: FAKE visual**

In-game: enable KillAura, Auto-block **FAKE**, hold sword, approach a living target in AutoBlockRange.

Expected: first-person sword stays in block pose; swings still animate over it while attacking.

- [ ] **Step 2: Animations parity**

With Animations enabled, cycle modes (Vanilla / Exhibition / Sigma / etc.) while FAKE is active.

Expected: pose matches the same mode’s look as when really RMB-blocking with a sword.

- [ ] **Step 3: Off paths**

- No target / leave range → pose returns to normal hold.
- Auto-block NONE or module off → no forced block pose.
- Not holding sword → no forced block pose.
- Real RMB block still works.
- Walking speed with FAKE alone (no real use) should **not** get vanilla use-item slowdown.

- [ ] **Step 4: Mark spec status**

Update `docs/superpowers/specs/2026-08-08-killaura-fakeblock-visual-design.md` status line from `approved (pending user review of this file)` to `implemented` after manual checks pass.

- [ ] **Step 5: Commit docs** (skip if user did not ask to commit)

```bash
git add docs/superpowers/specs/2026-08-08-killaura-fakeblock-visual-design.md \
  docs/superpowers/plans/2026-08-08-killaura-fakeblock-visual.md
git commit -m "$(cat <<'EOF'
Document KillAura FAKE visual block plan and mark implemented.

EOF
)"
```

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| Continuous FP block pose when FAKE + target + sword | Task 2 + FAKE already sets `fakeBlockState` |
| Classic 1.7 swings on top | Task 2 (no swing suppression) |
| Match Animations block path | Task 2 forces BLOCK branch; Animations mixin untouched |
| No packets / no global `isUsingItem` spoof | Task 2 redirect only in `renderItemInFirstPerson` |
| `KillAuraModule.isFakeBlocking()` bridge | Task 1 |
| Manual verification | Task 3 |

**Placeholder scan:** none.  
**Type consistency:** `isFakeBlocking()` → `boolean` on both helper and module static.

---

**Task 3 (static):** Spec status set to `code complete — awaiting in-game verify`. In-game steps remain in Task 3 above and in the spec’s “In-game verification checklist” section — run those in client before claiming full implementation.
