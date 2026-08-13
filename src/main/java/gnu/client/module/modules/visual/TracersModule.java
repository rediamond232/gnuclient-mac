package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.ui.ClientTheme;
import gnu.client.util.RenderHelper;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines from the camera to player torsos (raven Tracers, simplified).
 */
public final class TracersModule extends Module {

    static final class EntityData {
        double lastX;
        double lastY;
        double lastZ;
        double posX;
        double posY;
        double posZ;
        boolean sneaking;
    }

    private final BoolSetting useTheme = addSetting(new BoolSetting("Use Theme Colors", true));
    private final SliderSetting r = addSetting(new SliderSetting("Red", 0.0f, 0.0f, 255.0f));
    private final SliderSetting g = addSetting(new SliderSetting("Green", 255.0f, 0.0f, 255.0f));
    private final SliderSetting b = addSetting(new SliderSetting("Blue", 0.0f, 0.0f, 255.0f));
    private final SliderSetting lineWidth = addSetting(new SliderSetting("Width", 1.0f, 1.0f, 3.0f));
    private final BoolSetting showSelf = addSetting(new BoolSetting("Show Self", false));

    private static final double EYE_Y = 1.62;

    private final List<EntityData> cache = new ArrayList<>();
    private final List<Entity> scratch = new ArrayList<>();

    public TracersModule() {
        super("Tracers", "Lines from camera to players", Category.VISUALS);
        r.visibleWhen(() -> !useTheme.getValue());
        g.visibleWhen(() -> !useTheme.getValue());
        b.visibleWhen(() -> !useTheme.getValue());
    }

    @Override
    public void onEnable() {
        cache.clear();
    }

    @Override
    public void onDisable() {
        cache.clear();
    }

    @Override
    public void onTick() {
        cache.clear();

        if (Mc.player() == null || Mc.world() == null)
            return;

        Entity self = Mc.player();
        for (Entity entity : Mc.getWorldEntitiesFilteredInto(Mc.world(), scratch)) {
            if (!showSelf.getValue() && entity == self)
                continue;

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
        if (cache.isEmpty())
            return;

        if (!Mc.isInGame())
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        double rvpX = vp[0];
        double rvpY = vp[1];
        double rvpZ = vp[2];

        float[] rgb = resolveColor();
        float fr = rgb[0];
        float fg = rgb[1];
        float fb = rgb[2];
        float lw = lineWidth.getValue();

        RenderHelper.begin();

        for (EntityData data : cache) {
            double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
            double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
            double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);

            double rx = ix - rvpX;
            double ry = iy - rvpY;
            double rz = iz - rvpZ;

            double height = data.sneaking ? 1.5 : 1.8;
            double ty = ry + height * 0.5;

            RenderHelper.drawLine3D(
                    0.0, EYE_Y, 0.0,
                    rx, ty, rz,
                    fr, fg, fb, 1.0f, lw);
        }

        RenderHelper.end();
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
        if (index < list.size())
            return list.get(index);
        EntityData data = new EntityData();
        list.add(data);
        return data;
    }
}
