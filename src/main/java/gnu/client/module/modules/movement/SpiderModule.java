package gnu.client.module.modules.movement;

import gnu.client.event.PreMotionEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Spider — climb walls by keeping the server's copy of the player stuck inside
 * the wall block, then climbing with ordinary vanilla jumps.
 *
 * <p>Grim (and NCP-family) exempt vertical movement while the bounding box
 * intersects a solid block, because piston/trap pushes are legitimately
 * capable of that. While climbing we shift the <i>sent</i> position a few
 * centimetres into the wall block (packet-level only — local physics and
 * render position stay at the wall face), so the server computes the bbox as
 * overlapping the wall. Vanilla collision resolution imposes no vertical
 * constraint on an entity that already overlaps a block: a regular jump rises
 * unconstrained, and the wall block's top face catches the player on the way
 * down. Every vanilla jump therefore climbs exactly one block, with no motion
 * or on-ground manipulation of any kind.
 */
public final class SpiderModule extends Module {

    private final SliderSetting wallOffset = addSetting(new SliderSetting("Wall offset", 0.08f, 0.0f, 0.25f, 0.01f));
    private final BoolSetting requireJump = addSetting(new BoolSetting("Require jump", true));

    public SpiderModule() {
        super("Spider", "Climb walls by faking a stuck-in-block state (Grim)",
                Category.PLAYER);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @Override
    public String[] getSuffix() {
        float v = wallOffset.getValue();
        String s = v == Math.rint(v) ? Integer.toString((int) v) : String.format("%.2f", v);
        return new String[] { s };
    }

    /**
     * Packet shaping: clip the sent position into the wall so the server sees
     * the bbox overlapping a solid block. Nothing else is touched — motionY
     * and onGround stay vanilla, so each ordinary jump climbs one block.
     */
    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (!Mc.isInGame())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null || !isClimbing(player))
            return;

        float offset = wallOffset.getValue();
        if (offset <= 0.0f)
            return;
        double[] dir = pushDirection(
                player.rotationYaw,
                player.movementInput.moveForward,
                player.movementInput.moveStrafe);
        if (dir == null)
            return;
        event.setPosX(event.getPosX() + dir[0] * offset);
        event.setPosZ(event.getPosZ() + dir[1] * offset);
    }

    private boolean isClimbing(EntityPlayerSP player) {
        if (player.isInWater() || player.isInLava() || player.isSneaking() || player.isRiding())
            return false;
        if (player.capabilities.isFlying)
            return false;
        if (!player.isCollidedHorizontally)
            return false;
        if (requireJump.getValue() && !Mc.isJumpKeyHeld())
            return false;
        return player.movementInput.moveForward != 0.0f || player.movementInput.moveStrafe != 0.0f;
    }

    /**
     * Normalized horizontal push direction in world space from the player's
     * yaw-relative input, or {@code null} when there is no movement input.
     */
    static double[] pushDirection(float yaw, float moveForward, float moveStrafe) {
        if (moveForward == 0.0f && moveStrafe == 0.0f)
            return null;
        float yawRad = yaw * 0.017453292f;
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);
        double x = -sin * moveForward + cos * moveStrafe;
        double z = cos * moveForward + sin * moveStrafe;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-6)
            return null;
        return new double[] { x / len, z / len };
    }
}
