package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.ui.ClientTheme;
import gnu.client.util.EspOutline;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Player ESP.
 *
 * <ul>
 *   <li><b>Model</b> — spectral-arrow style silhouette highlight (thin rim, theme/custom RGB).</li>
 *   <li><b>Box</b> — single 3D AABB outline.</li>
 * </ul>
 */
public final class EspModule extends Module {

    private static EspModule INSTANCE;

    private static boolean outlining;

    private static final class EntityData {
        double lastX, lastY, lastZ, posX, posY, posZ;
        boolean sneaking;
    }

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Model", "Model", "Box"));
    private final BoolSetting useTheme = addSetting(new BoolSetting("Use Theme Colors", true));
    private final SliderSetting r = addSetting(new SliderSetting("Red", 255.0f, 0.0f, 255.0f));
    private final SliderSetting g = addSetting(new SliderSetting("Green", 255.0f, 0.0f, 255.0f));
    private final SliderSetting b = addSetting(new SliderSetting("Blue", 255.0f, 0.0f, 255.0f));
    private final BoolSetting showSelf = addSetting(new BoolSetting("Show Self", false));
    private final BoolSetting playersOnly = addSetting(new BoolSetting("Players Only", true));

    private final List<EntityData> cache = new ArrayList<EntityData>();
    private final List<Entity> scratch = new ArrayList<Entity>();
    private boolean forgeRegistered;

    public EspModule() {
        super("ESP", "Player model or box outline", Category.VISUALS);
        INSTANCE = this;
        r.visibleWhen(() -> !useTheme.getValue());
        g.visibleWhen(() -> !useTheme.getValue());
        b.visibleWhen(() -> !useTheme.getValue());
    }

    public static EspModule instance() {
        return INSTANCE;
    }

    public static boolean wantsVanillaOutline() {
        return false;
    }

    public static boolean shouldOutlineEntity(Entity entity) {
        return false;
    }

    public static float[] outlineRgbOrNull() {
        return null;
    }

    private boolean isModelMode() {
        return "Model".equalsIgnoreCase(mode.getCurrentMode());
    }

    @Override
    public void onEnable() {
        cache.clear();
        if (isModelMode() && !forgeRegistered) {
            MinecraftForge.EVENT_BUS.register(this);
            forgeRegistered = true;
        }
    }

    @Override
    public void onDisable() {
        cache.clear();
        EspOutline.clearQueue();
        if (forgeRegistered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            forgeRegistered = false;
        }
        outlining = false;
    }

    @Override
    public void onTick() {
        // Keep Forge listener in sync when mode flips while enabled.
        if (isEnabled() && isModelMode() && !forgeRegistered) {
            MinecraftForge.EVENT_BUS.register(this);
            forgeRegistered = true;
        } else if (isEnabled() && !isModelMode() && forgeRegistered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            forgeRegistered = false;
            outlining = false;
        }

        cache.clear();
        if (!isEnabled() || isModelMode()) {
            return;
        }
        if (Mc.player() == null || Mc.world() == null) {
            return;
        }
        Entity self = Mc.player();
        for (Entity entity : Mc.getWorldEntitiesFilteredInto(Mc.world(), scratch)) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            if (!showSelf.getValue() && entity == self) {
                continue;
            }
            EntityData data = obtain(cache, cache.size());
            data.lastX = entity.lastTickPosX;
            data.lastY = entity.lastTickPosY;
            data.lastZ = entity.lastTickPosZ;
            data.posX = entity.posX;
            data.posY = entity.posY;
            data.posZ = entity.posZ;
            data.sneaking = entity.isSneaking();
        }
    }

    @Override
    public void onRender(float partialTicks) {
        if (!isEnabled() || !Mc.isInGame()) {
            return;
        }
        // Model mode: one batched thin rim pass for everyone queued this frame.
        if (isModelMode()) {
            EspOutline.flush();
            return;
        }
        if (cache.isEmpty()) {
            return;
        }
        double[] vp = Mc.getViewerPos(partialTicks);
        float[] rgb = resolveColor();
        RenderHelper.begin();
        for (int i = 0; i < cache.size(); i++) {
            EntityData data = cache.get(i);
            double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
            double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
            double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);
            double rx = ix - vp[0];
            double ry = iy - vp[1];
            double rz = iz - vp[2];
            double height = data.sneaking ? 1.5 : 1.8;
            RenderHelper.drawBoundingBox(
                    rx - 0.3, ry, rz - 0.3,
                    rx + 0.3, ry + height, rz + 0.3,
                    rgb[0], rgb[1], rgb[2], 0.95f, 1.8f);
        }
        RenderHelper.end();
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (!isModelMode() || outlining || EspOutline.isBusy() || !isEnabled()
                || event.entity == null || event.renderer == null) {
            return;
        }
        if (!matchesFilter(event.entity)) {
            return;
        }
        float partialTicks = Mc.getPartialTicks();
        float[] rgb = resolveColor();
        float yaw = event.entity.prevRotationYaw
                + (event.entity.rotationYaw - event.entity.prevRotationYaw) * partialTicks;
        EspOutline.renderModelOutline(
                event.renderer,
                event.entity,
                event.x, event.y, event.z,
                yaw, partialTicks,
                rgb[0], rgb[1], rgb[2]);
    }

    private boolean matchesFilter(EntityLivingBase entity) {
        Entity self = Mc.player();
        if (!showSelf.getValue() && entity == self) {
            return false;
        }
        if (playersOnly.getValue()) {
            return entity instanceof EntityPlayer;
        }
        return true;
    }

    private float[] resolveColor() {
        if (useTheme.getValue()) {
            return ClientTheme.rgbFloats(ClientTheme.getFadeColor(0));
        }
        return new float[] {
                r.getValue() / 255.0f,
                g.getValue() / 255.0f,
                b.getValue() / 255.0f
        };
    }

    private static EntityData obtain(List<EntityData> list, int index) {
        if (index < list.size()) {
            return list.get(index);
        }
        EntityData data = new EntityData();
        list.add(data);
        return data;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
