package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.module.modules.combat.AntiBotModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.runtime.mc.Mc;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Custom player nametags via 2D screen-space projection (themed UiFont overlay).
 */
public final class NameTagsModule extends Module {

    private static final float TAG_SIZE = 8.5f;
    private static final float HP_SIZE = 7.5f;
    private static final float PAD_X = 4f;
    private static final float PAD_Y = 2.5f;
    private static final float RADIUS = 4f;

    static final class EntityData {
        double lastX;
        double lastY;
        double lastZ;
        double posX;
        double posY;
        double posZ;
        boolean sneaking;
        String name;
        float health;
        float maxHealth;
    }

    private final SliderSetting scale = addSetting(new SliderSetting("Scale", 1.0f, 0.5f, 3.0f));
    private final BoolSetting autoScale = addSetting(new BoolSetting("Auto Scale", true));
    private final BoolSetting showHealth = addSetting(new BoolSetting("Health", true));
    private final BoolSetting showSelf = addSetting(new BoolSetting("Show Self", false));
    private final BoolSetting background = addSetting(new BoolSetting("Background", true));

    private final List<EntityData> cache = new ArrayList<>();
    private final List<Entity> scratch = new ArrayList<>();

    private double vpX, vpY, vpZ;
    private double lastVpX, lastVpY, lastVpZ;
    private int mcDisplayWidth = 1;
    private int mcDisplayHeight = 1;
    private float lastPartialTicks;

    private final FloatBuffer savedModelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer savedProjection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
    private boolean glStateCaptured;

    public NameTagsModule() {
        super("NameTags", "Custom player nametags", Category.VISUALS);
        scale.visibleWhen(() -> !autoScale.getValue());
    }

    @Override
    public void onEnable() {
        cache.clear();
        glStateCaptured = false;
    }

    @Override
    public void onDisable() {
        cache.clear();
        glStateCaptured = false;
    }

    /** Called at end of world render while GL matrices match the 3D scene. */
    public void captureGlState(float partialTicks) {
        lastPartialTicks = partialTicks;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, savedModelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, savedProjection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport);
        glStateCaptured = true;
    }

    @Override
    public void onTick() {
        cache.clear();

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return;

        mcDisplayWidth = Mc.mc().displayWidth;
        mcDisplayHeight = Mc.mc().displayHeight;
        if (mcDisplayWidth < 1)
            mcDisplayWidth = 1;
        if (mcDisplayHeight < 1)
            mcDisplayHeight = 1;

        for (Entity entity : Mc.getWorldEntitiesFilteredInto(world, scratch)) {
            if (!showSelf.getValue() && entity == player)
                continue;

            if (AntiBotModule.isActive() && RavenAntiBot.isBot(entity))
                continue;

            if (!(entity instanceof EntityPlayer))
                continue;

            EntityPlayer ep = (EntityPlayer) entity;
            String name = plainName(ep);
            if (name == null || name.isEmpty())
                continue;

            EntityData data = obtain(cache, cache.size());
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = entity.posX;
            data.posY = entity.posY;
            data.posZ = entity.posZ;
            data.sneaking = entity.isSneaking();
            data.name = name;
            data.health = Mc.getHealth(ep);
            data.maxHealth = Math.max(1f, ep.getMaxHealth());
        }

        lastVpX = vpX;
        lastVpY = vpY;
        lastVpZ = vpZ;
        double[] vp = Mc.getViewerPos(1.0f);
        vpX = vp[0];
        vpY = vp[1];
        vpZ = vp[2];
    }

    @Override
    public void onOverlay(Object sr) {
        if (cache.isEmpty() || !glStateCaptured)
            return;

        if (!(sr instanceof ScaledResolution))
            return;
        ScaledResolution scaled = (ScaledResolution) sr;
        int sw = scaled.getScaledWidth();
        int sh = scaled.getScaledHeight();
        if (sw < 1 || sh < 1)
            return;

        double rvpX = lastVpX + (vpX - lastVpX) * lastPartialTicks;
        double rvpY = lastVpY + (vpY - lastVpY) * lastPartialTicks;
        double rvpZ = lastVpZ + (vpZ - lastVpZ) * lastPartialTicks;

        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
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

        for (EntityData data : cache) {
            double wx = Mc.lerp(data.lastX, data.posX, lastPartialTicks);
            double wy = Mc.lerp(data.lastY, data.posY, lastPartialTicks)
                    + (data.sneaking ? 1.5 : 1.8) + 0.3;
            double wz = Mc.lerp(data.lastZ, data.posZ, lastPartialTicks);

            double[] screen = projectToScreen(wx, wy, wz, rvpX, rvpY, rvpZ);
            if (screen == null)
                continue;
            if (screen[2] < 0.0 || screen[2] > 1.0)
                continue;

            float sx = (float) (screen[0] / mcDisplayWidth * sw);
            float sy = (float) ((1.0 - screen[1] / mcDisplayHeight) * sh);

            double dist = Math.sqrt(
                    (wx - rvpX) * (wx - rvpX)
                            + (wy - rvpY) * (wy - rvpY)
                            + (wz - rvpZ) * (wz - rvpZ));
            float scaleFactor = autoScale.getValue()
                    ? Math.max(0.5f, Math.min(1.5f, (float) dist * 0.05f + 0.5f))
                    : scale.getValue();

            String name = data.name;
            float nameW = UiFont.width(name, TAG_SIZE);
            String hpText = null;
            float hpW = 0f;
            int hpColor = UiKit.TEXT;
            if (showHealth.getValue()) {
                float pct = UiKit.clamp01(data.health / data.maxHealth);
                // Full HP → theme accent; low HP → danger red
                hpColor = ClientTheme.lerpArgb(UiKit.DANGER, ClientTheme.getFadeColor(0), pct);
                hpText = String.format("%.0f", data.health);
                hpW = UiFont.width(hpText, HP_SIZE) + 3f;
            }
            float contentW = nameW + hpW;
            float contentH = Math.max(UiFont.height(TAG_SIZE), UiFont.height(HP_SIZE));
            if (contentW <= 0f)
                continue;

            GL11.glPushMatrix();
            GL11.glTranslatef(sx, sy, 0.0f);
            GL11.glScalef(scaleFactor, scaleFactor, 1.0f);

            float boxW = contentW + PAD_X * 2f;
            float boxH = contentH + PAD_Y * 2f;
            float boxX = -boxW * 0.5f;
            float boxY = -PAD_Y;

            if (background.getValue()) {
                UiKit.drawRoundedPanel(boxX, boxY, boxW, boxH, RADIUS,
                        UiKit.withAlpha(UiKit.SURFACE, 0.92f));
            }

            float textY = boxY + (boxH - contentH) * 0.5f;
            float textX = boxX + PAD_X;
            UiFont.draw(name, textX, textY, TAG_SIZE, UiKit.TEXT);
            if (hpText != null) {
                UiFont.draw(hpText, textX + nameW + 3f, textY + (contentH - UiFont.height(HP_SIZE)) * 0.5f,
                        HP_SIZE, hpColor);
            }

            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        if (depthWas)
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        else
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (blendWas)
            GL11.glEnable(GL11.GL_BLEND);
        else
            GL11.glDisable(GL11.GL_BLEND);
        if (texWas)
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        else
            GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private double[] projectToScreen(double wx, double wy, double wz,
            double camX, double camY, double camZ) {
        float x = (float) (wx - camX);
        float y = (float) (wy - camY);
        float z = (float) (wz - camZ);

        float[] eye = transformPoint(savedModelview, x, y, z, 1.0f);
        float[] clip = transformPoint(savedProjection, eye[0], eye[1], eye[2], eye[3]);
        if (clip[3] == 0.0f)
            return null;

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];
        float ndcZ = clip[2] / clip[3];

        int vx = savedViewport.get(0);
        int vy = savedViewport.get(1);
        int vw = savedViewport.get(2);
        int vh = savedViewport.get(3);

        return new double[] {
                vx + (ndcX + 1.0f) * 0.5f * vw,
                vy + (ndcY + 1.0f) * 0.5f * vh,
                (ndcZ + 1.0f) * 0.5f
        };
    }

    private static float[] transformPoint(FloatBuffer matrix, float x, float y, float z, float w) {
        float m0 = matrix.get(0);
        float m1 = matrix.get(1);
        float m2 = matrix.get(2);
        float m3 = matrix.get(3);
        float m4 = matrix.get(4);
        float m5 = matrix.get(5);
        float m6 = matrix.get(6);
        float m7 = matrix.get(7);
        float m8 = matrix.get(8);
        float m9 = matrix.get(9);
        float m10 = matrix.get(10);
        float m11 = matrix.get(11);
        float m12 = matrix.get(12);
        float m13 = matrix.get(13);
        float m14 = matrix.get(14);
        float m15 = matrix.get(15);
        return new float[] {
                m0 * x + m4 * y + m8 * z + m12 * w,
                m1 * x + m5 * y + m9 * z + m13 * w,
                m2 * x + m6 * y + m10 * z + m14 * w,
                m3 * x + m7 * y + m11 * z + m15 * w
        };
    }

    private static String plainName(EntityPlayer entity) {
        if (entity.getDisplayName() != null) {
            String formatted = entity.getDisplayName().getUnformattedText();
            if (formatted != null && !formatted.isEmpty()) {
                return stripCodes(formatted);
            }
        }
        String name = entity.getName();
        return name == null ? null : stripCodes(name);
    }

    private static String stripCodes(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static EntityData obtain(List<EntityData> list, int index) {
        if (index < list.size())
            return list.get(index);
        EntityData data = new EntityData();
        list.add(data);
        return data;
    }
}
