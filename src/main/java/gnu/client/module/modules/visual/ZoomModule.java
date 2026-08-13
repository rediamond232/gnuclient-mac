package gnu.client.module.modules.visual;

import gnu.client.mixin.impl.accessors.IAccessorGameSettings;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import org.lwjgl.input.Keyboard;

/**
 * Zoom — OptiFine-style FOV zoom on a held key.
 *
 * <p>Implemented purely by overriding {@code GameSettings.fovSetting} while the key is held and
 * restoring the player's original value on release, so it needs no render-path mixin and stays
 * compatible with OptiFine's own zoom (they simply both write the same setting).
 *
 * <p>Default key = C (LWJGL 46), matching OptiFine.
 */
public final class ZoomModule extends Module {

    /** LWJGL key code for 'C'. */
    private static final int DEFAULT_KEY = 46;

    private final BoolSetting smooth = addSetting(new BoolSetting("Smooth", true));
    private final SliderSetting zoomFov = addSetting(new SliderSetting("Zoom FOV", 25f, 5f, 70f, 1f));
    private final SliderSetting speed = addSetting(new SliderSetting("Speed", 0.35f, 0.05f, 1.0f, 0.05f));

    /** FOV to restore when zoom ends. Only valid while {@link #zooming}. */
    private float savedFov;

    private boolean zooming;

    /** Current interpolated FOV, so Smooth ramps instead of snapping. */
    private float currentFov;

    public ZoomModule() {
        super("Zoom", "Hold a key to zoom in (OptiFine-style FOV zoom)", Category.VISUALS);
        setKeyCode(DEFAULT_KEY);
        speed.visibleWhen(() -> smooth.getValue());
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        stopZoom();
    }

    @Override
    public void onTick() {
        // Mc.isInGame() is false whenever a GUI is open, so opening chat/inventory while zoomed
        // restores FOV through this branch.
        if (!Mc.isInGame()) {
            stopZoom();
            setEnabled(false);
            return;
        }

        boolean wantZoom = isZoomKeyHeld();

        if (wantZoom && !zooming) {
            savedFov = getFov();
            currentFov = savedFov;
            zooming = true;
        }

        if (!zooming) {
            // Key was tapped and released inside a single tick, so zoom never started.
            setEnabled(false);
            return;
        }

        if (wantZoom) {
            float target = zoomFov.getValue();
            if (smooth.getValue()) {
                currentFov += (target - currentFov) * speed.getValue();
                if (Math.abs(target - currentFov) < 0.1f)
                    currentFov = target;
            } else {
                currentFov = target;
            }
            setFov(currentFov);
            return;
        }

        // Key released: ease back out, then hand the setting back to the player.
        if (smooth.getValue()) {
            currentFov += (savedFov - currentFov) * speed.getValue();
            if (Math.abs(savedFov - currentFov) >= 0.1f) {
                setFov(currentFov);
                return;
            }
        }
        stopZoom();
        // The keybind toggles enabled state on each key-down edge, so releasing has to switch
        // the module back off or the next press would toggle it off instead of zooming.
        setEnabled(false);
    }

    private void stopZoom() {
        if (!zooming)
            return;
        zooming = false;
        setFov(savedFov);
    }

    private boolean isZoomKeyHeld() {
        int code = getKeyCode();
        if (code < 0)
            return false;
        try {
            return Keyboard.isKeyDown(code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float getFov() {
        return ((IAccessorGameSettings) Mc.settings()).getFovSetting();
    }

    private void setFov(float value) {
        ((IAccessorGameSettings) Mc.settings()).setFovSetting(value);
    }
}
