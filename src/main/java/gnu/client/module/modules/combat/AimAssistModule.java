package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.util.RenderHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Timewarp Aim Assist — faithful port from ctw.dll.
 *
 * Tick-based rotation update (smoothRotationHv + fixRotation).
 * Separate Horizontal speed / Vertical speed.
 * AimMode = Static (center) / Multipoint (offset slider).
 * TargetingMode = Closest / Lowest HP / Single / Switch.
 *
 * Architecture: onTick does everything (conditions, target selection,
 * smoothing, rotation write). onRender / onOverlay handle visuals only
 * (target line, FOV circle).
 */
public final class AimAssistModule extends Module {

    private static final List<String> AIM_MODES = Arrays.asList("Static", "Multipoint");
    private static final List<String> TARGETING_MODES = Arrays.asList(
            "Closest", "Lowest HP", "Single", "Switch");

    private static final float CHECK_PITCH_LIMIT = 80.0f;
    private static final float VERTICAL_CHECK_LIMIT = 45.0f;


    /** Shared timestamp updated by AimAssist, AutoClicker, and KillAura on each click/attack. */
    public static volatile long lastClickMs = 0L;

    // ==================== Settings (Timewarp-matching) ====================

    private final ModeSetting aimMode = addSetting(new ModeSetting("Mode", 0, AIM_MODES));
    private final ModeSetting targetingMode = addSetting(new ModeSetting("Targeting", 0, TARGETING_MODES));
    private final SliderSetting horizontalSpeed = addSetting(new SliderSetting("Horizontal speed", 10.0f, 1.0f, 100.0f));
    private final SliderSetting verticalSpeed = addSetting(new SliderSetting("Vertical speed", 10.0f, 0.0f, 100.0f));
    private final SliderSetting maximumFov = addSetting(new SliderSetting("MaximumFov", 90.0f, 15.0f, 360.0f));
    private final SliderSetting maximumRange = addSetting(new SliderSetting("MaximumRange", 4.5f, 0.0f, 6.0f));
    private final SliderSetting minimumRange = addSetting(new SliderSetting("MinimumRange", 0.0f, 0.0f, 6.0f));
    private final SliderSetting targetSwitchDelay = addSetting(new SliderSetting("TargetSwitchDelay", 0.0f, 0.0f, 1000.0f));
    private final SliderSetting multipointOffset = addSetting(new SliderSetting("Multipoint offset", 0.0f, 0.0f, 100.0f));
    private final SliderSetting randomization = addSetting(new SliderSetting("Randomization", 50.0f, 0.0f, 100.0f));

    private final BoolSetting visualizeFov = addSetting(new BoolSetting("Visualize FOV", false));
    private final SliderSetting fovColorR = addSetting(new SliderSetting("FovColorR", 255.0f, 0.0f, 255.0f));
    private final SliderSetting fovColorG = addSetting(new SliderSetting("FovColorG", 255.0f, 0.0f, 255.0f));
    private final SliderSetting fovColorB = addSetting(new SliderSetting("FovColorB", 255.0f, 0.0f, 255.0f));

    private final BoolSetting targetLine = addSetting(new BoolSetting("Target line", false));
    private final SliderSetting targetLineColorR = addSetting(new SliderSetting("TargetLineColorR", 255.0f, 0.0f, 255.0f));
    private final SliderSetting targetLineColorG = addSetting(new SliderSetting("TargetLineColorG", 0.0f, 0.0f, 255.0f));
    private final SliderSetting targetLineColorB = addSetting(new SliderSetting("TargetLineColorB", 0.0f, 0.0f, 255.0f));
    private final SliderSetting targetLineThickness = addSetting(new SliderSetting("Line thickness", 1.5f, 1.0f, 4.0f));

    private final BoolSetting aimAtInvisible = addSetting(new BoolSetting("Aim at invisible", false));
    private final BoolSetting ignoreFriends = addSetting(new BoolSetting("Ignore friends", true));
    private final BoolSetting sprintingOnly = addSetting(new BoolSetting("Sprinting only", false));
    private final BoolSetting hurtTimeFilter = addSetting(new BoolSetting("Hurt time filter", false));
    private final SliderSetting hurtTime = addSetting(new SliderSetting("Hurt time", 1.0f, 0.0f, 10.0f));
    private final BoolSetting holdingWeapon = addSetting(new BoolSetting("Holding weapon", false));
    private final BoolSetting requireEntityHit = addSetting(new BoolSetting("RequireEntityHit", false));
    private final BoolSetting requireLmb = addSetting(new BoolSetting("Require LMB", true));
    private final BoolSetting requireRmb = addSetting(new BoolSetting("Require RMB", false));
    private final BoolSetting checkPitch = addSetting(new BoolSetting("Check pitch", false));
    private final BoolSetting verticalCheck = addSetting(new BoolSetting("Vertical check", false));
    private final BoolSetting disableInWater = addSetting(new BoolSetting("Disable in water", false));
    private final BoolSetting insideEntityHitbox = addSetting(new BoolSetting("Inside entity hitbox", false));
    private final BoolSetting throughWalls = addSetting(new BoolSetting("Through walls", false));

    // ==================== State ====================

    /** Current locked target (persists across ticks for Single mode / TargetSwitchDelay). */
    private Entity lockedTarget;
    /** Wall-clock ms of the last target switch, for TargetSwitchDelay. */
    private long lastTargetSwitchMs;
    /** The entity we are currently aiming at (may be null). */
    private Entity currentTarget;
    /** Whether we have an active aim target this tick. */
    private boolean hasTarget;

    public AimAssistModule() {
        super("Aim Assist", "Aims at players for you", Category.COMBAT);
        multipointOffset.visibleWhen(() -> aimMode.getValue() == 1);
        targetSwitchDelay.visibleWhen(() -> targetingMode.getValue() != 2);
        fovColorR.visibleWhen(() -> visualizeFov.getValue());
        fovColorG.visibleWhen(() -> visualizeFov.getValue());
        fovColorB.visibleWhen(() -> visualizeFov.getValue());
        targetLineColorR.visibleWhen(() -> targetLine.getValue());
        targetLineColorG.visibleWhen(() -> targetLine.getValue());
        targetLineColorB.visibleWhen(() -> targetLine.getValue());
        targetLineThickness.visibleWhen(() -> targetLine.getValue());
        hurtTime.visibleWhen(() -> hurtTimeFilter.getValue());
    }

    @Override
    public void onEnable() {
        lockedTarget = null;
        currentTarget = null;
        lastTargetSwitchMs = 0L;
        lastClickMs = System.currentTimeMillis();
        hasTarget = false;
    }

    @Override
    public void onDisable() {
        lockedTarget = null;
        currentTarget = null;
        hasTarget = false;
    }

    // ==================== Tick-based aim (Timewarp faithful) ====================
    //
    // Timewarp's AimAssist runs in the client tick (not per-frame).
    // Each tick:
    //   1. Check conditions (GUI, in-game focus, weapon, LMB, sprint, water, pitch)
    //   2. Select target via TargetHandler (FOV, range, filters, LOS check)
    //   3. If no target or filters fail → stop
    //   4. Compute raw target rotation toward aim point
    //   5. smoothRotationHv: base + (target - base) * (speed / 100)
    //   6. fixRotation: GCD snap so per-tick delta is a multiple of mouse sensitivity
    //   7. Write rotationYaw / rotationPitch to EntityPlayer
    //
    // NOTE: base rotation is read from actual player state every tick (not from
    // internal smoothYaw/smoothPitch). This ensures manual mouse input feeds
    // directly into the smoothing — speed acts as a % pull towards target per
    // tick from wherever the user is actually aiming, not from where the
    // aimassist left off. Fixes "sticky" resistance at low speed settings.

    @Override
    public void onTick() {
        if (!isEnabled() || !conditionsMet()) {
            hasTarget = false;
            return;
        }

        if (KillAuraModule.shouldYieldAimAssist()) {
            hasTarget = false;
            return;
        }

        // Hover delay: if LMB is not held, stop rotating
        if (!ClientBootstrap.isLeftMouseDown()) {
            hasTarget = false;
            return;
        }

        EntityPlayerSP player = Mc.player();
        if (player == null) {
            hasTarget = false;
            return;
        }
        RavenRotationUtils.beginTick(player);

        Entity candidate = selectTarget();
        if (candidate == null) {
            currentTarget = null;
            hasTarget = false;
            if (targetingMode.getValue() == 2) // Single mode: unlock
                lockedTarget = null;
            return;
        }

        long now = System.currentTimeMillis();

        // Apply target switching logic
        int mode = targetingMode.getValue();
        if (mode == 2) {
            // Single: lock to first target, never switch
            if (lockedTarget != null && isValidCandidate(lockedTarget))
                candidate = lockedTarget;
            else
                lockedTarget = candidate;
        } else {
            // TargetSwitchDelay: delay before switching to a different target
            long switchDelay = targetSwitchDelay.getValue().longValue();
            if (switchDelay > 0 && lockedTarget != null && candidate != lockedTarget
                    && now - lastTargetSwitchMs < switchDelay)
                candidate = lockedTarget;

            if (candidate != lockedTarget) {
                lastTargetSwitchMs = now;
                lockedTarget = candidate;
            }
        }

        currentTarget = candidate;

        // Extra per-target filters (hurt time, vertical, inside hitbox, crosshair)
        if (!passesTargetFilters(candidate)) {
            hasTarget = false;
            return;
        }

        hasTarget = true;

        // Read base rotation from actual player state every tick,
        // so manual mouse input feeds into aim smoothing directly.
        // Without this, internal smoothYaw/smoothPitch overwrite user input,
        // creating "sticky" resistance even at low speed settings.
        float baseYaw = player.rotationYaw;
        float basePitch = player.rotationPitch;
        double mp = aimMode.getValue() == 1 ? multipointOffset.getValue() : 0.0;
        boolean blockWalls = !throughWalls.getValue();
        float[] rawTarget = RavenRotationUtils.getRawRotationsToTarget(
                candidate, mp, mp,
                blockWalls, maximumRange.getValue(),
                blockWalls, false);
        if (rawTarget == null)
            return;

        // Timewarp smoothRotationHv: base + (target - base) * (speed / 100)
        // with randomization jitter, then GCD fixRotation
        int hSpeed = Math.round(horizontalSpeed.getValue());
        int vSpeed = Math.round(verticalSpeed.getValue());
        float[] result = RavenRotationUtils.smoothRotationHv(
                baseYaw, basePitch,
                rawTarget[0], rawTarget[1],
                hSpeed, vSpeed, randomization.getValue());

        float newYaw = result[0];
        float newPitch = result[1];

        // Write rotation to player entity
        player.rotationYaw = newYaw;
        player.rotationPitch = newPitch;

    }

    // ==================== Visuals (onRender / onOverlay) ====================

    @Override
    public void onRender(float partialTicks) {
        // Target line rendering
        if (!isEnabled() || currentTarget == null || !targetLine.getValue() || !Mc.isInGame())
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        double mp = aimMode.getValue() == 1 ? multipointOffset.getValue() : 0.0;
        double[] eye = eyePos(player, partialTicks);
        double[] aim = RavenRotationUtils.getAimPoint(currentTarget, mp, mp);
        if (eye == null || aim == null)
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        float r = targetLineColorR.getValue() / 255.0f;
        float g = targetLineColorG.getValue() / 255.0f;
        float b = targetLineColorB.getValue() / 255.0f;

        RenderHelper.begin();
        RenderHelper.drawLine3D(
                eye[0] - vp[0], eye[1] - vp[1], eye[2] - vp[2],
                aim[0] - vp[0], aim[1] - vp[1], aim[2] - vp[2],
                r, g, b, 0.85f, targetLineThickness.getValue());
        RenderHelper.end();
    }

    @Override
    public void onOverlay(Object sr) {
        if (!isEnabled() || !visualizeFov.getValue())
            return;
        if (!(sr instanceof ScaledResolution))
            return;
        ScaledResolution scaled = (ScaledResolution) sr;

        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw < 1 || sh < 1)
            return;

        float fovVal = maximumFov.getValue();
        if (fovVal >= 360.0f)
            return;

        float cx = sw * 0.5f;
        float cy = sh * 0.5f;
        float radius = (float) (Math.tan(Math.toRadians(fovVal * 0.5)) * (sh * 0.45));
        radius = Math.max(20.0f, Math.min(radius, Math.min(sw, sh) * 0.48f));

        float r = fovColorR.getValue() / 255.0f;
        float g = fovColorG.getValue() / 255.0f;
        float b = fovColorB.getValue() / 255.0f;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, sw, sh, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(r, g, b, 0.75f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            GL11.glVertex2d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
        }
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    // ==================== Condition helpers (Timewarp TargetHandler) ====================

    private boolean conditionsMet() {
        if (!Mc.isResolved())
            return false;
        if (Mc.currentScreen() != null)
            return false;
        if (!Mc.mc().inGameHasFocus)
            return false;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (holdingWeapon.getValue() && !holdingWeapon(player))
            return false;
        if (requireLmb.getValue() && !isLeftMouseDown())
            return false;
        if (requireRmb.getValue() && !isRightMouseDown())
            return false;
        if (sprintingOnly.getValue() && !Mc.isClientSprinting(player))
            return false;
        if (disableInWater.getValue() && player.isInWater())
            return false;
        if (checkPitch.getValue() && Math.abs(player.rotationPitch) > CHECK_PITCH_LIMIT)
            return false;

        return true;
    }

    private boolean passesTargetFilters(Entity target) {
        EntityPlayerSP player = Mc.player();
        if (player == null || target == null)
            return false;

        if (hurtTimeFilter.getValue()) {
            if (!(target instanceof EntityLivingBase))
                return false;
            int hurt = ((EntityLivingBase) target).hurtTime;
            if (hurt < hurtTime.getValue().intValue())
                return false;
        }

        if (verticalCheck.getValue()) {
            float pitchDelta = (float) Math.abs(RavenRotationUtils.pitchDifference(target,
                    player.rotationPitch));
            if (pitchDelta > VERTICAL_CHECK_LIMIT)
                return false;
        }

        if (insideEntityHitbox.getValue() && !isInsideTargetHitbox(player, target))
            return false;

        if (requireEntityHit.getValue() && !isCrosshairOnEntity(target))
            return false;

        return true;
    }

    /**
     * Timewarp TargetHandler.selectTarget():
     * - Iterates world loaded entities (EntityPlayer only)
     * - Filters by alive, friends, invisible, range, FOV
     * - Sorts by targeting mode (closest, lowest HP, angle, hurt time)
     * - Validates aim point (LOS / multipoint)
     * - Returns first valid candidate
     */
    private Entity selectTarget() {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return null;

        if (lockedTarget != null && isValidCandidate(lockedTarget)) {
            int mode = targetingMode.getValue();
            if (mode == 2)
                return lockedTarget;
            long switchDelay = targetSwitchDelay.getValue().longValue();
            if (switchDelay > 0 && System.currentTimeMillis() - lastTargetSwitchMs < switchDelay)
                return lockedTarget;
        }

        float viewYaw = player.rotationYaw;
        int fovVal = Math.round(maximumFov.getValue());
        double rangeMaxVal = maximumRange.getValue();
        double rangeMinVal = minimumRange.getValue();
        double rangeMaxSq = (rangeMaxVal + 1.0) * (rangeMaxVal + 1.0);
        double rangeMinSq = Math.max(0.0, rangeMinVal - 0.5);
        rangeMinSq *= rangeMinSq;

        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : Mc.getWorldEntitiesFiltered(world)) {
            if (entity == null || entity == player || !Mc.isEntityPlayer(entity))
                continue;
            if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).deathTime > 0)
                continue;
            if (ignoreFriends.getValue() && shouldFilterFriend(entity))
                continue;
            if (!aimAtInvisible.getValue() && isInvisible(entity))
                continue;

            double centerSq = RavenRotationUtils.distanceSqCenters(entity);
            if (centerSq > rangeMaxSq || centerSq < rangeMinSq)
                continue;
            if (fovVal != 360 && !RavenRotationUtils.inFov(viewYaw, fovVal, RavenRotationUtils.angleToEntity(entity)))
                continue;
            candidates.add(entity);
        }

        if (candidates.isEmpty())
            return null;

        Comparator<Entity> primary = buildTargetingComparator(player, viewYaw);
        candidates.sort(primary);

        double mp = aimMode.getValue() == 1 ? multipointOffset.getValue() : 0.0;
        boolean blockWalls = !throughWalls.getValue();
        int limit = Math.min(candidates.size(), 3);
        for (int i = 0; i < limit; i++) {
            Entity candidate = candidates.get(i);
            if (!blockWalls && aimMode.getValue() == 0)
                return candidate;
            if (RavenRotationUtils.hasValidAimPoint(candidate, mp, mp, rangeMaxVal, blockWalls, false))
                return candidate;
        }
        return null;
    }

    private boolean isValidCandidate(Entity entity) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null || entity == player)
            return false;
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).deathTime > 0)
            return false;

        float viewYaw = player.rotationYaw;
        int fovVal = Math.round(maximumFov.getValue());
        double rangeMaxVal = maximumRange.getValue();
        double rangeMinVal = minimumRange.getValue();
        double distSq = RavenRotationUtils.distanceSqFromEyeToClosestOnAabb(entity);
        if (distSq > rangeMaxVal * rangeMaxVal || distSq < rangeMinVal * rangeMinVal)
            return false;
        if (fovVal != 360 && !RavenRotationUtils.inFov(viewYaw, fovVal, RavenRotationUtils.angleToEntity(entity)))
            return false;

        double mp = aimMode.getValue() == 1 ? multipointOffset.getValue() : 0.0;
        boolean blockWalls = !throughWalls.getValue();
        return RavenRotationUtils.hasValidAimPoint(entity, mp, mp, rangeMaxVal, blockWalls, false);
    }

    private boolean shouldFilterFriend(Entity entity) {
        return false;
    }

    private Comparator<Entity> buildTargetingComparator(EntityPlayerSP player, float viewYaw) {
        switch (targetingMode.getValue()) {
            case 1:
                return Comparator.comparingDouble(this::entityHealth);
            case 2:
            case 3:
            case 0:
            default:
                return Comparator.comparingDouble(RavenRotationUtils::distanceSqCenters);
        }
    }

    private double entityHealth(Entity entity) {
        if (entity instanceof EntityLivingBase)
            return ((EntityLivingBase) entity).getHealth();
        return 0.0;
    }

    private static boolean holdingWeapon(EntityPlayer player) {
        ItemStack stack = player.getHeldItem();
        if (stack == null)
            return false;
        return stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemAxe;
    }

    private static boolean isLeftMouseDown() {
        return ClientBootstrap.isLeftMouseDown();
    }

    private static boolean isRightMouseDown() {
        return Mc.isPhysicalRmbDown();
    }

    private static boolean isInvisible(Entity entity) {
        return entity != null && entity.isInvisible();
    }

    private static boolean isInsideTargetHitbox(Entity player, Entity target) {
        double[] eye = eyePos(player, 1.0f);
        AxisAlignedBB bb = target.getEntityBoundingBox();
        if (eye == null || bb == null)
            return false;
        return bb.isVecInside(new Vec3(eye[0], eye[1], eye[2]));
    }

    private static boolean isCrosshairOnEntity(Entity target) {
        MovingObjectPosition mop = Mc.objectMouseOver();
        return mop != null && mop.entityHit == target;
    }

    private static double[] eyePos(Entity player, float partialTicks) {
        Vec3 vec = player.getPositionEyes(partialTicks);
        if (vec == null)
            return null;
        return new double[] { vec.xCoord, vec.yCoord, vec.zCoord };
    }

    @Override
    public String[] getSuffix() {
        return new String[] { aimMode.getCurrentMode() };
    }
}
