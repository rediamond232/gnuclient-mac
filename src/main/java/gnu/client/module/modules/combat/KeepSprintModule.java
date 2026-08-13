package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.AuraCombatPacketGuard;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * KillAura sprint: Prediction clears Grim {@code lastSprinting} then skips {@code ×0.6};
 * Grim keeps packet sprint and matches AttackSlow ({@code ×0.6}) for clean Simulation.
 */
public final class KeepSprintModule extends Module {

    private static final List<String> MODE_NAMES = unmodifiableList(asList("Prediction", "Grim"));
    private static final int DEFAULT_MODE_INDEX = 0;
    /** Fixed walk C03s before owning a hit — not user-tunable. */
    private static final int FIXED_WALK_C03S = 2;
    private static final String MODULE_DESCRIPTION =
            "KA sprint — Prediction: stop before hit, skip ×0.6 after 2 walk C03s; "
                    + "Grim: never stop sprint, match AttackSlow (×0.6), bypasses Simulation not the slow";

    public final ModeSetting mode =
            addSetting(new ModeSetting("Mode", DEFAULT_MODE_INDEX, MODE_NAMES));

    /** Kept for config compat; always hidden and forced to {@link #FIXED_WALK_C03S}. */
    private final SliderSetting walkC03s =
            addSetting(new SliderSetting("WalkC03s", 2f, 1f, 3f, 1f));
    private final BoolSetting debug =
            addSetting(new BoolSetting("Debug", false));

    private final KeepSprintStopState state = new KeepSprintStopState();
    private KeepSprintStopState.Phase lastDebugPhase = KeepSprintStopState.Phase.IDLE;
    private boolean stopSent;
    /** Owned Prediction hit: keep walk until afterWalking, then soft key restore. */
    private boolean pendingKeyRestore;

    public KeepSprintModule() {
        super("KeepSprint", MODULE_DESCRIPTION, Category.COMBAT);
        walkC03s.visibleWhen(() -> false);
    }

    @Override
    public void onEnable() {
        state.reset();
        stopSent = false;
        pendingKeyRestore = false;
        lastDebugPhase = KeepSprintStopState.Phase.IDLE;
    }

    @Override
    public void onDisable() {
        state.onWtapOrDisable();
        stopSent = false;
        pendingKeyRestore = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }

    private static KeepSprintModule instance() {
        Module m = ModuleManager.INSTANCE.getModule("KeepSprint");
        return m instanceof KeepSprintModule ? (KeepSprintModule) m : null;
    }

    private void clearStopWalkState() {
        state.reset();
        stopSent = false;
        pendingKeyRestore = false;
    }

    private boolean isStopWalk() {
        return KeepSprintLogic.isStopWalkMode(mode.getCurrentMode());
    }

    private boolean isGrim() {
        return KeepSprintLogic.isGrimMode(mode.getCurrentMode());
    }

    /** Prediction mode needs a live KA target; otherwise drop any armed walk window. */
    private boolean requireKillAuraTarget() {
        if (KeepSprintLogic.shouldArmForKillAuraTarget(KillAuraModule.getCurrentTarget() != null))
            return true;
        clearStopWalkState();
        return false;
    }

    private boolean yielding(EntityPlayerSP player) {
        if (KeepSprintLogic.shouldYieldToWtap(true, WTapModule.shouldSuppressSprintKey()))
            return true;
        int hurt = player != null ? player.hurtTime : (Mc.player() != null ? Mc.player().hurtTime : 0);
        return KeepSprintLogic.shouldSkipForHurt(true, hurt);
    }

    private boolean yielding() {
        return yielding(Mc.player());
    }

    private void syncSettings() {
        walkC03s.setValue((float) FIXED_WALK_C03S);
        state.setWalkC03s(FIXED_WALK_C03S);
    }

    private void debugPhase() {
        if (!debug.getValue())
            return;
        KeepSprintStopState.Phase p = state.getPhase();
        if (p == lastDebugPhase)
            return;
        lastDebugPhase = p;
        Mc.addChatMessage("§7[KeepSprint] §f" + p.name().toLowerCase());
    }

    private void applyStop(EntityPlayerSP player) {
        Mc.setSprintKeyState(false);
        if (Mc.isClientSprinting(player)) {
            Mc.setClientSprinting(player, false);
            Mc.clearSprintToggleTimer(player);
        }
        if (!stopSent && Mc.getServerSprintState(player) && AuraCombatPacketGuard.isSprintSlotFree()) {
            Mc.sendSprintActionPacket(player, false);
            stopSent = true;
        }
    }

    /** Sprint key only — START via next living/walking (Prediction after attack C03). */
    private void restoreSprintKeyOnly(EntityPlayerSP player) {
        Mc.setSprintKeyState(true);
    }

    /** Key + client sprint (Grim after AttackSlow). */
    private void restoreSprintHard(EntityPlayerSP player) {
        Mc.setSprintKeyState(true);
        Mc.setClientSprinting(player, true);
    }

    public static void maintainWalkState() {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled())
            return;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return;
        }
        if (mod.yielding()) {
            mod.clearStopWalkState();
            return;
        }
        if (!mod.requireKillAuraTarget())
            return;
        mod.syncSettings();
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (mod.pendingKeyRestore
                || mod.state.getPhase() == KeepSprintStopState.Phase.STOPPING
                || mod.state.getPhase() == KeepSprintStopState.Phase.READY)
            mod.applyStop(player);

        mod.debugPhase();
    }

    public static void onKillAuraTargetReady(EntityPlayerSP player) {}

    public static void onKillAuraTargetLost(EntityPlayerSP player) {
        KeepSprintModule mod = instance();
        if (mod == null)
            return;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return;
        }
        mod.clearStopWalkState();
        mod.debugPhase();
    }

    public static void onAfterWalking() {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled())
            return;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return;
        }
        if (mod.yielding()) {
            mod.clearStopWalkState();
            return;
        }
        if (!mod.requireKillAuraTarget())
            return;
        mod.syncSettings();
        EntityPlayerSP player = Mc.player();
        boolean cleared = player != null && !Mc.getServerSprintState(player);
        if (mod.state.getPhase() == KeepSprintStopState.Phase.STOPPING)
            mod.state.onWalkC03(cleared);

        // Soft restore after the attack tick's walk C03 — not at onUpdate HEAD.
        if (mod.pendingKeyRestore && KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(true)) {
            mod.pendingKeyRestore = false;
            if (player != null)
                mod.restoreSprintKeyOnly(player);
            if (mod.debug.getValue())
                Mc.addChatMessage("§7[KeepSprint] §frecover");
        }
        mod.debugPhase();
    }

    public static void onClientTickStart() {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled())
            return;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return;
        }
        if (mod.yielding() || !mod.requireKillAuraTarget())
            mod.clearStopWalkState();
    }

    public static boolean tryBeginFightForImminentAttack(EntityPlayerSP player) {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled() || player == null)
            return false;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return false;
        }
        if (mod.yielding(player)) {
            mod.clearStopWalkState();
            return false;
        }
        if (!mod.requireKillAuraTarget())
            return false;
        mod.syncSettings();

        boolean needsStop = Mc.isClientSprinting(player) || Mc.getServerSprintState(player);
        if (KeepSprintLogic.shouldBeginStop(
                true, false, mod.state.canOwnAttack(), needsStop)) {
            mod.state.beginStop();
            mod.stopSent = false;
            mod.pendingKeyRestore = false;
            mod.applyStop(player);
            mod.debugPhase();
        }
        return false;
    }

    public static boolean shouldDeferKillAuraAttack() {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled())
            return false;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return false;
        }
        if (mod.yielding() || !mod.requireKillAuraTarget())
            return false;
        if (mod.state.shouldDeferAttack())
            return true;

        // READY but sprint re-armed — never skip ×0.6 while Grim may still AttackSlow.
        if (mod.state.canOwnAttack()) {
            EntityPlayerSP player = Mc.player();
            if (player != null) {
                mod.applyStop(player);
                if (!KeepSprintLogic.canSkipAttackSlow(
                        true, Mc.isClientSprinting(player), Mc.getServerSprintState(player))) {
                    mod.state.invalidateReady();
                    mod.stopSent = false;
                    mod.applyStop(player);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean shouldSuppressSprintKey() {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled())
            return false;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return false;
        }
        if (mod.yielding() || !mod.requireKillAuraTarget())
            return false;
        return mod.pendingKeyRestore || mod.state.shouldSuppressSprintKey();
    }

    public static void patchMovementInput(Object movementInput) {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled() || !(movementInput instanceof MovementInput))
            return;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return;
        }
        if (mod.yielding() || !mod.requireKillAuraTarget())
            return;
        // Sprint-only jump feel: suppress jump only while Prediction owns the walk window.
        boolean stopWindow = mod.pendingKeyRestore || mod.state.shouldSuppressSprintKey();
        EntityPlayerSP player = Mc.player();
        boolean sprinting = player != null && Mc.isClientSprinting(player);
        if (KeepSprintLogic.shouldSuppressJump(true, stopWindow, sprinting))
            ((MovementInput) movementInput).jump = false;
    }

    public static boolean onBeforeKillAuraAttack(EntityPlayerSP player) {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled() || player == null)
            return false;
        if (!mod.isStopWalk()) {
            mod.clearStopWalkState();
            return false;
        }
        if (mod.yielding(player)) {
            mod.clearStopWalkState();
            return false;
        }
        if (!mod.requireKillAuraTarget())
            return false;
        if (!KeepSprintLogic.shouldOwnHit(true, WTapModule.shouldSuppressSprintKey(), mod.state.canOwnAttack()))
            return false;

        mod.applyStop(player);
        if (!KeepSprintLogic.canSkipAttackSlow(
                true, Mc.isClientSprinting(player), Mc.getServerSprintState(player))) {
            mod.state.invalidateReady();
            mod.stopSent = false;
            mod.applyStop(player);
            return false;
        }
        if (mod.debug.getValue())
            Mc.addChatMessage("§7[KeepSprint] §fhit (no slow)");
        return true;
    }

    public static void onAfterKillAuraAttack(EntityPlayerSP player, boolean owned) {
        KeepSprintModule mod = instance();
        if (mod == null || !mod.isEnabled() || player == null)
            return;
        if (KeepSprintLogic.shouldHardRestoreSprint(mod.isGrim())) {
            mod.clearStopWalkState();
            if (mod.yielding(player))
                return;
            mod.restoreSprintHard(player);
            return;
        }
        if (!owned)
            return;
        if (mod.yielding(player)) {
            mod.clearStopWalkState();
            return;
        }

        mod.state.onOwnedHitFinished();
        mod.stopSent = false;
        // Keep walking through this tick's living + C03; soft key restore in onAfterWalking.
        if (KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(owned)) {
            mod.pendingKeyRestore = true;
            mod.applyStop(player);
        }
        mod.debugPhase();
    }
}
