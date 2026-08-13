package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.Arrays;

/**
 * Fly — soft Grim setback-budget glide/hover.
 *
 * <p>Keeps per-tick Simulation offset under the immediate-setback threshold,
 * pulses illegal ticks with vanilla rest (advantage decay), and locks longer
 * after each S08 so setbacks stop slamming every tick.</p>
 */
public final class FlyModule extends Module implements PacketListener {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", FlySetbackState.MODE_GLIDE,
            Arrays.asList("Hover", "Glide")));
    private final SliderSetting speed = addSetting(new SliderSetting("Speed", 0.05f, 0.01f, 0.2f, 0.01f));
    private final SliderSetting setbackCooldown = addSetting(
            new SliderSetting("SetbackCooldown", 10.0f, 2.0f, 40.0f, 1.0f));
    private final SliderSetting activeTicks = addSetting(
            new SliderSetting("ActiveTicks", 2.0f, 1.0f, 10.0f, 1.0f));
    private final SliderSetting restTicks = addSetting(
            new SliderSetting("RestTicks", 3.0f, 1.0f, 20.0f, 1.0f));

    private final FlySetbackState setbackState = new FlySetbackState();

    public FlyModule() {
        super("Fly", "Soft Grim glide/hover (budgeted Simulation offset)",
                Category.PLAYER);
    }

    FlySetbackState setbackState() {
        return setbackState;
    }

    @Override
    public void onEnable() {
        setbackState.reset();
        setbackState.setPulse(Math.round(activeTicks.getValue()), Math.round(restTicks.getValue()));
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        setbackState.reset();
    }

    @Override
    public void onTickStart() {
        if (!isEnabled() || !Mc.isInGame())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        setbackState.setPulse(Math.round(activeTicks.getValue()), Math.round(restTicks.getValue()));
        setbackState.onClientTick();

        if (!setbackState.canApplyAirControl())
            return;
        if (player.capabilities.isFlying || player.isRiding())
            return;
        if (player.onGround)
            return;

        applyAirControl(player);
    }

    private void applyAirControl(EntityPlayerSP player) {
        float moveForward = 0.0f;
        float moveStrafe = 0.0f;
        if (player.movementInput != null) {
            moveForward = player.movementInput.moveForward;
            moveStrafe = player.movementInput.moveStrafe;
        }

        double motionY = FlySetbackState.verticalMotion(mode.getIndex(), player.motionY);
        double mx = 0.0;
        double mz = 0.0;
        double[] horiz = FlySetbackState.horizontalMotion(
                player.rotationYaw, moveForward, moveStrafe, speed.getValue());
        if (horiz != null) {
            mx = horiz[0];
            mz = horiz[1];
        }

        // Baseline ≈ current fall vector — Grim predicts continuing that motion.
        double baselineY = player.motionY;
        if (baselineY > -0.01)
            baselineY = FlySetbackState.TYPICAL_FALL_MOTION_Y;

        double[] clamped = FlySetbackState.clampToBudget(
                mx, motionY, mz, baselineY, FlySetbackState.MAX_SAFE_OFFSET);
        Mc.setMotion(clamped[0], clamped[1], clamped[2]);
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return false;
        if (!isEnabled())
            return false;
        setbackState.onSetbackReceived(Math.round(setbackCooldown.getValue()));
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
