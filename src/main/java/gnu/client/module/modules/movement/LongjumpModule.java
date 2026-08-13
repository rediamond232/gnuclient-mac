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
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

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
    private boolean pendingMultiply;

    public LongjumpModule() {
        super("Longjump", "Timer+bounce one-shot velocity longjump (Grim)", Category.PLAYER);
    }

    LongjumpState state() {
        return state;
    }

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
        World world = Mc.world();
        if (world == null)
            return null;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY - 0.2);
        int z = MathHelper.floor_double(player.posZ);
        IBlockState state = world.getBlockState(new BlockPos(x, y, z));
        return state == null ? null : state.getBlock();
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
            Mc.resetTimer();
        } else {
            Mc.resetTimer();
        }
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled())
            return false;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (packet instanceof S08PacketPlayerPosLook)
            return false;

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
