package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.velocity.AACVelocity;
import gnu.client.module.modules.combat.velocity.BounceVelocity;
import gnu.client.module.modules.combat.velocity.BufferAbuseVelocity;
import gnu.client.module.modules.combat.velocity.DelayVelocity;
import gnu.client.module.modules.combat.velocity.GrimReduceVelocity;
import gnu.client.module.modules.combat.velocity.GrimTestVelocity;
import gnu.client.module.modules.combat.velocity.GrimVelocity;
import gnu.client.module.modules.combat.velocity.GroundVelocity;
import gnu.client.module.modules.combat.velocity.IntaveReduceVelocity;
import gnu.client.module.modules.combat.velocity.IntaveVelocity;
import gnu.client.module.modules.combat.velocity.JumpResetVelocity;
import gnu.client.module.modules.combat.velocity.KarhuVelocity;
import gnu.client.module.modules.combat.velocity.LegitSmartVelocity;
import gnu.client.module.modules.combat.velocity.LegitTestVelocity;
import gnu.client.module.modules.combat.velocity.LegitVelocity;
import gnu.client.module.modules.combat.velocity.MMCVelocity;
import gnu.client.module.modules.combat.velocity.MatrixVelocity;
import gnu.client.module.modules.combat.velocity.OMDelayVelocity;
import gnu.client.module.modules.combat.velocity.HypixelVelocity;
import gnu.client.module.modules.combat.velocity.RedeskyVelocity;
import gnu.client.module.modules.combat.velocity.ReverseVelocity;
import gnu.client.module.modules.combat.velocity.StandardVelocity;
import gnu.client.module.modules.combat.velocity.TickVelocity;
import gnu.client.module.modules.combat.velocity.UniversoCraftVelocity;
import gnu.client.module.modules.combat.velocity.VelocityDelayQueue;
import gnu.client.module.modules.combat.velocity.VelocityMode;
import gnu.client.module.modules.combat.velocity.VulcanVelocity;
import gnu.client.module.modules.combat.velocity.WatchdogPredictionVelocity;
import gnu.client.module.modules.combat.velocity.WatchdogVelocity;
import gnu.client.event.JumpEvent;
import gnu.client.event.StrafeEvent;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.network.KnockbackDelayModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * OpenMiau-style Velocity — 26 modes via {@link VelocityMode} strategies.
 *
 * <p>Tick dispatch: {@link #onTickStart()} → {@code onUpdate(true)} (PRE / early tick);
 * {@link #onTick()} → {@code onUpdate(false)} (POST / late tick).</p>
 */
public final class VelocityModule extends Module implements PacketListener {

    private static final int STANDARD_MODE_INDEX = 7;

    private static final List<String> MODE_NAMES = Collections.unmodifiableList(Arrays.asList(
            "OMDelay", "Reverse", "LegitTest", "LegitSmart", "IntaveReduce", "Grimtest", "JumpReset",
            "Standard", "AAC", "Bounce", "BufferAbuse", "Delay", "Grim", "GrimReduce", "Ground",
            "Hypixel", "Intave",
            "Karhu", "Legit", "MMC", "Matrix", "Redesky", "Tick", "UniversoCraft", "Vulcan",
            "WatchdogPrediction", "Watchdog"));

    // ── Settings (public final — modes read directly) ─────────────────────

    public final ModeSetting mode =
            addSetting(new ModeSetting("Mode", STANDARD_MODE_INDEX, MODE_NAMES));

    public final SliderSetting delayTicks =
            addSetting(new SliderSetting("Delay Ticks", 3.0f, 1.0f, 20.0f, 1.0f));
    public final SliderSetting delayChance =
            addSetting(new SliderSetting("Delay Chance", 100.0f, 0.0f, 100.0f));

    public final SliderSetting legitSmartJumpLimit =
            addSetting(new SliderSetting("Legit Smart Jump Limit", 2.0f, 1.0f, 5.0f, 1.0f));

    public final SliderSetting intaveReduceFactor =
            addSetting(new SliderSetting("Intave Reduce Factor", 0.6f, 0.6f, 1.0f));
    public final SliderSetting intaveReduceHurtTime =
            addSetting(new SliderSetting("Intave Reduce Hurt Time", 9.0f, 1.0f, 10.0f, 1.0f));

    public final SliderSetting chance =
            addSetting(new SliderSetting("Chance", 100.0f, 0.0f, 100.0f));

    public final SliderSetting horizontal =
            addSetting(new SliderSetting("Horizontal", 0.0f, 0.0f, 100.0f));
    public final SliderSetting vertical =
            addSetting(new SliderSetting("Vertical", 100.0f, 0.0f, 100.0f));

    public final SliderSetting explosionHorizontal =
            addSetting(new SliderSetting("Explosions Horizontal", 100.0f, 0.0f, 100.0f));
    public final SliderSetting explosionVertical =
            addSetting(new SliderSetting("Explosions Vertical", 100.0f, 0.0f, 100.0f));

    public final SliderSetting grimReduceJumpLimit =
            addSetting(new SliderSetting("Grim Reduce Jump Limit", 2.0f, 1.0f, 5.0f, 1.0f));

    public final BoolSetting fakeCheck =
            addSetting(new BoolSetting("Fake Check", true));
    public final BoolSetting debugLog =
            addSetting(new BoolSetting("Debug Log", false));
    public final BoolSetting onSwing =
            addSetting(new BoolSetting("On Swing", false));

    // ── Shared state (OpenMiau Velocity.java parity) ──────────────────────

    public int chanceCounter;
    public int delayChanceCounter;
    public boolean pendingExplosion;
    public boolean allowNext = true;
    public boolean jumpFlag;
    public boolean reverseFlag;
    public boolean delayActive;
    public boolean shouldJump;
    public int jumpCooldown;
    public boolean hasReceivedVelocity;
    public int legitSmartJumpCount;
    public int intaveTick;
    public int intaveDamageTick;

    public final VelocityDelayQueue delayQueue = new VelocityDelayQueue();
    public final Random random = new Random();

    public final List<VelocityMode> modes = new ArrayList<>();

    public VelocityModule() {
        super("Velocity", "Reduces knockback from hits", Category.COMBAT);

        delayTicks.visibleWhen(() -> modeIs("OMDelay"));
        delayChance.visibleWhen(() -> modeIs("OMDelay"));
        legitSmartJumpLimit.visibleWhen(() -> modeIs("LegitSmart"));
        intaveReduceFactor.visibleWhen(() -> modeIs("IntaveReduce"));
        intaveReduceHurtTime.visibleWhen(() -> modeIs("IntaveReduce"));
        chance.visibleWhen(() -> modeIsAny("Legit", "LegitTest", "LegitSmart", "JumpReset"));
        horizontal.visibleWhen(() -> modeIsAny("Standard", "BufferAbuse", "Redesky", "Vulcan"));
        vertical.visibleWhen(() -> modeIsAny("Standard", "BufferAbuse", "Redesky", "Vulcan"));
        explosionHorizontal.visibleWhen(() -> modeIs("Standard"));
        explosionVertical.visibleWhen(() -> modeIs("Standard"));
        grimReduceJumpLimit.visibleWhen(() -> modeIs("Grimtest"));

        modes.add(new OMDelayVelocity(this));
        modes.add(new ReverseVelocity(this));
        modes.add(new LegitTestVelocity(this));
        modes.add(new LegitSmartVelocity(this));
        modes.add(new IntaveReduceVelocity(this));
        modes.add(new GrimTestVelocity(this));
        modes.add(new JumpResetVelocity(this));
        modes.add(new StandardVelocity(this));
        modes.add(new AACVelocity(this));
        modes.add(new BounceVelocity(this));
        modes.add(new BufferAbuseVelocity(this));
        modes.add(new DelayVelocity(this));
        modes.add(new GrimVelocity(this));
        modes.add(new GrimReduceVelocity(this));
        modes.add(new GroundVelocity(this));
        modes.add(new HypixelVelocity(this));
        modes.add(new IntaveVelocity(this));
        modes.add(new KarhuVelocity(this));
        modes.add(new LegitVelocity(this));
        modes.add(new MMCVelocity(this));
        modes.add(new MatrixVelocity(this));
        modes.add(new RedeskyVelocity(this));
        modes.add(new TickVelocity(this));
        modes.add(new UniversoCraftVelocity(this));
        modes.add(new VulcanVelocity(this));
        modes.add(new WatchdogPredictionVelocity(this));
        modes.add(new WatchdogVelocity(this));
    }

    public VelocityMode getActiveMode() {
        String current = mode.getCurrentMode();
        for (VelocityMode m : modes) {
            if (m.getName().equals(current))
                return m;
        }
        return modes.isEmpty() ? null : modes.get(0);
    }

    /** True when enabled — OpenMiau treats Velocity as always reducing when on. */
    public boolean reducesKnockback() {
        return isEnabled();
    }

    public boolean isInLiquidOrWeb() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return player.isInWater() || player.isInLava()
                || player.isInsideOfMaterial(Material.web);
    }

    /** On ground; KillAura autoblock gate deferred until KillAuraModule exposes shouldAutoBlock. */
    public boolean canDelay() {
        EntityPlayerSP player = Mc.player();
        return player != null && player.onGround;
    }

    public void noteAttack(Object target) {
        if (!isEnabled())
            return;
        getActiveMode().onAttack(target);
    }

    public static void patchMovementInput(Object movInput) {
        Module mod = ModuleManager.INSTANCE.getModule("Velocity");
        if (!(mod instanceof VelocityModule))
            return;
        VelocityModule vm = (VelocityModule) mod;
        if (!vm.isEnabled() || movInput == null)
            return;
        vm.getActiveMode().onMoveInput((MovementInput) movInput);
    }

    public static void patchStrafe(StrafeEvent event) {
        Module mod = ModuleManager.INSTANCE.getModule("Velocity");
        if (!(mod instanceof VelocityModule))
            return;
        VelocityModule vm = (VelocityModule) mod;
        if (!vm.isEnabled() || event == null)
            return;
        vm.getActiveMode().onStrafe(event);
    }

    public static void patchJump(JumpEvent event) {
        Module mod = ModuleManager.INSTANCE.getModule("Velocity");
        if (!(mod instanceof VelocityModule))
            return;
        VelocityModule vm = (VelocityModule) mod;
        if (!vm.isEnabled() || event == null)
            return;
        vm.getActiveMode().onJump(event);
    }

    @Override
    public void onEnable() {
        PacketEvents.register(this);
        getActiveMode().onEnable();
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        getActiveMode().onDisable();
        resetSharedState();
        delayQueue.clear();
        Mc.setTimerSpeed(1.0f);
    }

    /** PRE — early client tick ({@code ClientTickEvent} START). */
    @Override
    public void onTickStart() {
        if (!isEnabled())
            return;
        getActiveMode().onUpdate(true);
    }

    /** POST — late client tick ({@code ClientTickEvent} END). */
    @Override
    public void onTick() {
        if (!isEnabled())
            return;
        getActiveMode().onUpdate(false);
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled())
            return false;
        // KnockbackDelay owns inbound — do not cancel/modify self S12 out from under it.
        if (KnockbackDelayModule.isOwningInboundQueue())
            return false;
        return getActiveMode().onReceive(packet);
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isEnabled())
            return false;
        return getActiveMode().onSend(packet);
    }

    private void resetSharedState() {
        chanceCounter = 0;
        delayChanceCounter = 0;
        pendingExplosion = false;
        allowNext = true;
        jumpFlag = false;
        reverseFlag = false;
        delayActive = false;
        shouldJump = false;
        jumpCooldown = 0;
        hasReceivedVelocity = false;
        legitSmartJumpCount = 0;
        intaveTick = 0;
        intaveDamageTick = 0;
    }

    private boolean modeIs(String name) {
        return name.equals(mode.getCurrentMode());
    }

    private boolean modeIsAny(String... names) {
        String current = mode.getCurrentMode();
        for (String n : names) {
            if (n.equals(current))
                return true;
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
