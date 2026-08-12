package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.ReachModule;
import gnu.client.module.modules.combat.MoreKBModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;

/**
 * Forge attack notification helper — dispatches attack/swing events to combat modules.
 *
 * <p>KillAura calls {@link #noteAttack} before {@code Mc.attackEntity}, which also fires
 * Forge {@code AttackEntityEvent}. {@link #onForgeAttack} skips a duplicate notify for
 * that same attack so WTap/MoreKB do not inject two sprint C0B bursts (BadPacketsX).
 */
public final class CombatAttackNotify {

    private static boolean prevPhysicalLmb;
    /** Set by {@link #noteAttack}; consumed by {@link #onForgeAttack}. */
    private static boolean skipNextForgeDuplicate;
    /** Timestamp of the most recent attack (set in {@link #noteAttack}). */
    private static long lastAttackMs = 0L;

    private CombatAttackNotify() {}

    public static void noteAttack(Object target) {
        if (!Mc.isInGame() || target == null)
            return;
        lastAttackMs = System.currentTimeMillis();
        dispatch(target);
        skipNextForgeDuplicate = true;
    }

    /** Time of the most recent KillAura/physical attack, or 0 if none. */
    public static long getLastAttackMs() {
        return lastAttackMs;
    }

    /** From Forge {@code AttackEntityEvent} — skips if KillAura already notified. */
    public static void onForgeAttack(Object target) {
        if (skipNextForgeDuplicate) {
            skipNextForgeDuplicate = false;
            return;
        }
        if (!Mc.isInGame() || target == null)
            return;
        dispatch(target);
    }

    private static void dispatch(Object target) {
        Module wTap = ModuleManager.INSTANCE.getModule("W Tap");
        if (wTap instanceof WTapModule && wTap.isEnabled() && target instanceof Entity)
            ((WTapModule) wTap).noteForgeAttack((Entity) target);

        Module moreKb = ModuleManager.INSTANCE.getModule("MoreKB");
        if (moreKb instanceof MoreKBModule && moreKb.isEnabled() && target instanceof Entity)
            ((MoreKBModule) moreKb).noteForgeAttack((Entity) target);

        Module velocity = ModuleManager.INSTANCE.getModule("Velocity");
        if (velocity instanceof VelocityModule && velocity.isEnabled())
            ((VelocityModule) velocity).noteAttack(target);
    }

    public static void tickReachOnLmbEdge() {
        if (!Mc.isResolved() || !Mc.isInGame())
            return;
        boolean lmb = Mc.isPhysicalLmbDown();
        if (lmb && !prevPhysicalLmb)
            ReachModule.applyIfEnabled();
        prevPhysicalLmb = lmb;
    }
}
