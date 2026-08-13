package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.SliderSetting;
import gnu.client.render.MotionBlur;

/**
 * High-frame-rate frame blending. Every frame is composited into a persistent
 * accumulation buffer, the way a 240 FPS capture looks when downsampled to
 * display rate: anything that moves on screen trails, not just camera rotation.
 * Sharp when nothing moves. Shutter time is wall-clock so the trail has the
 * same real length at any FPS. HUD is not blurred.
 */
public final class MotionBlurModule extends Module {

    private static MotionBlurModule INSTANCE;

    private final SliderSetting amount = addSetting(new SliderSetting("Amount", 4f, 0f, 10f, 0.5f));

    public MotionBlurModule() {
        super("MotionBlur", "Frame-blended motion blur", Category.VISUALS);
        INSTANCE = this;
    }

    public static MotionBlurModule instance() {
        return INSTANCE;
    }

    public float getAmount() {
        return amount.getValue();
    }

    @Override
    public void onEnable() {
        MotionBlur.reset();
    }

    @Override
    public void onDisable() {
        MotionBlur.dispose();
    }

    @Override
    public String[] getSuffix() {
        float v = amount.getValue();
        if (v == (int) v) {
            return new String[] { Integer.toString((int) v) };
        }
        return new String[] { String.format("%.1f", v) };
    }
}
