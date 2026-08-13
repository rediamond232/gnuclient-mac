package gnu.client.module.modules.visual;

import gnu.client.mixin.impl.accessors.IAccessorC03PacketPlayer;
import gnu.client.mixin.impl.accessors.IAccessorGameSettings;
import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorRenderManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Keyboard;

/**
 * Free Look — Path B: no rotation writes, Mixin redirects orientCamera reads.
 *
 * <p>Unlike the CameraHook approach (which wrote camera angles into player rotation
 * before orientCamera and restored after), Path B uses
 * {@link gnu.client.mixin.impl.render.MixinEntityRenderer} {@code @Redirect}
 * hooks to replace every read of {@code Entity.rotationYaw/rotationPitch} inside
 * {@code EntityRenderer.orientCamera} with a call to
 * {@link gnu.client.runtime.FreeLookHook#redirectYaw(Object)} and
 * {@link gnu.client.runtime.FreeLookHook#redirectPitch(Object)}.
 *
 * <p>The player's real {@code rotationYaw/rotationPitch} is NEVER modified.
 * Freelook camera angles ({@link #cameraYaw}/{@link #cameraPitch}) are seeded
 * from the player's facing direction on enable/enter and updated via
 * {@link #applyCameraDelta(float, float)}, which replicates vanilla's internal:
 * cameraYaw += dYaw * 0.15, cameraPitch -= dPitch * 0.15. Mouse deltas are applied
 * from {@link gnu.client.runtime.FreeLookHook#overrideMouse} during
 * {@code EntityRenderer.updateCameraAndRender}.
 *
 * <p>Packet interceptor (priority 200) overwrites outbound C03/C05/C06 yaw/pitch with the
 * player's real body rotation so freelook camera movement is silent to the server.
 * The handler never cancels or queues packets — lag modules are unaffected.
 *
 * <p>Settings mirror Raven's Freelook: Hold, Invert pitch, Lock pitch,
 * Custom FOV. Default key = L (LWJGL 56).
 *
 * <p>NOTE: The {@code invertPitch} setting is retained as a field but NOT
 * applied in {@link #applyCameraDelta} — vanilla's own invertMouse is already
 * baked into the pitch argument reaching this method, so dPitch already
 * has the correct sign for the vanilla setting. The module invert is a
 * separate user preference that may be applied elsewhere if needed.
 */
public final class FreeLookModule extends Module implements PacketListener {

    // Default LWJGL key code for 'L' = 56 (same as Raven)
    private static final int DEFAULT_KEY = 56;

    // ── Settings (mirrors Raven Freelook.java) ────────────────────────────

    private final BoolSetting hold = addSetting(new BoolSetting("Hold", true));
    private final BoolSetting invertPitch = addSetting(new BoolSetting("Invert pitch", false));
    private final BoolSetting lockPitch = addSetting(new BoolSetting("Lock pitch", true));
    private final BoolSetting customFov = addSetting(new BoolSetting("Custom FOV", false));
    private final SliderSetting fov = addSetting(new SliderSetting("FOV", 90.0f, 10.0f, 150.0f));

    // ── State ─────────────────────────────────────────────────────────────

    /** Whether the freelook perspective is currently active. */
    private boolean perspectiveToggled;

    /**
     * Independent camera angles (accumulated from mouse deltas via
     * {@link #applyCameraDelta} on the render thread).
     * The player's real rotationYaw/rotationPitch is NEVER modified — these
     * angles are returned by FreeLookHook.redirectYaw/redirectPitch when
     * freelook is active.
     */
    private float cameraYaw;
    private float cameraPitch;

    /**
     * The original player facing direction — captured on enable and used only
     * as the seed for camera angles. Player rotation is never written by this
     * module, so no "original" restore is needed.
     */
    private float originalYaw;
    private float originalPitch;

    /** The third-person view mode we saved (0=first, 1=back, 2=front). */
    private int previousPerspective;

    /** Original FOV value to restore on disable. */
    private float lastFov;

    /** Edge-trigger tracking for key press (Raven pattern: prevKeyState). */
    private boolean prevKeyDown;

    // ── Construction ──────────────────────────────────────────────────────

    public FreeLookModule() {
        super("FreeLook", "Look around in third-person without changing player rotation",
                Category.VISUALS);
        setKeyCode(DEFAULT_KEY);
        fov.visibleWhen(() -> customFov.getValue());
    }

    // ── Module lifecycle ─────────────────────────────────────────────────

    @Override
    public void onEnable() {
        if (!canFreelook()) {
            setEnabled(false);
            return;
        }

        // Save current rotation as both original facing and initial camera position
        // Player rotation is never written — this is just the seed for camera angles.
        originalYaw = getPlayerYaw();
        originalPitch = getPlayerPitch();
        cameraYaw = originalYaw;
        cameraPitch = originalPitch;

        // Save current perspective and FOV
        previousPerspective = getThirdPersonView();
        lastFov = getFovSetting();

        // Switch to third-person back view (Raven: enterPerspective → applyThirdPersonView(1))
        applyThirdPersonView(1);

        // Register packet interceptor — overwrite C03/C05/C06 yaw/pitch with real rotation
        PacketEvents.register(this);

        perspectiveToggled = true;
        prevKeyDown = true;
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        if (perspectiveToggled) {
            perspectiveToggled = false;
            resetPerspective();
        }
        prevKeyDown = false;
    }

    // ── Tick handlers (Path B: no rotation writes) ───────────────────────
    //
    // The player's real rotationYaw/rotationPitch is NEVER written by this
    // module. Mouse delta accumulation (applyCameraDelta) runs on the
    // render thread — onTick only manages non-rotation upkeep.

    @Override
    public void onTickStart() {
        // No rotation restore needed — Path B never writes player rotation.
    }

    @Override
    public void onTick() {
        if (!canFreelook()) {
            if (perspectiveToggled) {
                setEnabled(false);
            }
            return;
        }

        // ── Hold mode: edge-triggered key check (Raven pattern: onPressed) ──
        boolean keyDown = isFreelookKeyHeld();
        if (keyDown != prevKeyDown) {
            if (!isEnabled()) {
                if (perspectiveToggled) {
                    resetPerspective();
                }
                prevKeyDown = keyDown;
                return;
            }
            if (keyDown) {
                if (!perspectiveToggled) {
                    originalYaw = getPlayerYaw();
                    originalPitch = getPlayerPitch();
                    cameraYaw = originalYaw;
                    cameraPitch = originalPitch;
                    enterPerspective();
                }
            } else {
                if (hold.getValue() && perspectiveToggled) {
                    resetPerspective();
                    perspectiveToggled = false;
                    setEnabled(false);
                }
            }
            prevKeyDown = keyDown;
            return;
        }

        if (!perspectiveToggled) {
            return;
        }

        if (Mc.currentScreen() != null && hold.getValue()) {
            resetPerspective();
            setEnabled(false);
            return;
        }

        // ── No mouseHelper delta reads/zeroing ──
        // Mouse delta accumulation (applyCameraDelta) runs on the render thread.
        // onTick only handles non-rotation upkeep.

        // Apply custom FOV (Raven: if customFov.isToggled)
        if (customFov.getValue()) {
            setFovSetting(fov.getValue());
        }

        // Ensure third-person view is still active
        if (getThirdPersonView() != 1) {
            applyThirdPersonView(1);
        }
    }

    // ── Camera delta accumulation (FreeLookHook.overrideMouse) ───────────

    /**
     * Apply sensitivity-scaled mouse deltas to the freelook camera
     * (raven Freelook.overrideMouse parity: both axes use {@code +=}).
     */
    public void applyCameraDelta(float dYaw, float dPitch) {
        if (invertPitch.getValue()) {
            dPitch = -dPitch;
        }
        cameraYaw += dYaw * 0.15f;
        cameraPitch += dPitch * 0.15f;
        if (lockPitch.getValue()) {
            cameraPitch = Math.max(-90.0f, Math.min(90.0f, cameraPitch));
        }
    }

    // ── Packet interceptor (silent rotation — real body yaw/pitch only) ──
    //
    // Freelook camera angles are client-only. Player rotationYaw/rotationPitch stay
    // frozen at the direction you faced when freelook started. Packets always carry
    // that real rotation.
    //
    // Never cancels or re-queues — lag/blink/backtrack modules are unaffected.

    @Override
    public int sendPriority() {
        return 200;
    }

    @Override
    public boolean onSend(Object packet) {
        if (perspectiveToggled && PacketHelper.isPlayerMovement(packet) && packet instanceof C03PacketPlayer) {
            EntityPlayerSP player = Mc.player();
            if (player != null) {
                IAccessorC03PacketPlayer c03 = (IAccessorC03PacketPlayer) packet;
                c03.setYaw(player.rotationYaw);
                c03.setPitch(player.rotationPitch);
            }
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    // ── Render override ──────────────────────────────────────────────────

    @Override
    public void onRender(float partialTicks) {
        if (!perspectiveToggled)
            return;

        RenderManager renderManager = Mc.renderManager();
        if (renderManager != null) {
            IAccessorRenderManager arm = (IAccessorRenderManager) renderManager;
            arm.setPlayerViewY(cameraYaw);
            arm.setPlayerViewX(cameraPitch);
        }
    }

    // ── Public API for FreeLookHook ──────────────────────────────────────

    /** Whether the freelook third-person perspective is currently active. */
    public boolean isPerspectiveActive() {
        return perspectiveToggled;
    }

    /** Current freelook camera yaw (accumulated from mouse deltas). */
    public float getCameraYaw() {
        return cameraYaw;
    }

    /** Current freelook camera pitch (accumulated from mouse deltas). */
    public float getCameraPitch() {
        return cameraPitch;
    }

    // ── Key detection (Raven pattern: LWJGL Keyboard.isKeyDown) ──────────

    private boolean isFreelookKeyHeld() {
        int code = getKeyCode();
        if (code < 0)
            return false;
        try {
            return Keyboard.isKeyDown(code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── Prerequisites ─────────────────────────────────────────────────────

    /** Can we enter freelook right now? (Raven: nullCheck + Freecam guard) */
    private boolean canFreelook() {
        if (!Mc.isInGame())
            return false;
        Module freecam = ModuleManager.INSTANCE.getModule("Freecam");
        if (freecam != null && freecam.isEnabled())
            return false;
        return true;
    }

    // ── Perspective management (Raven parity) ────────────────────────────

    private void enterPerspective() {
        cameraYaw = getPlayerYaw();
        cameraPitch = getPlayerPitch();
        previousPerspective = getThirdPersonView();
        lastFov = getFovSetting();
        applyThirdPersonView(1);
        perspectiveToggled = true;
    }

    private void resetPerspective() {
        perspectiveToggled = false;

        // Snap freelook camera back to the real body rotation so first-person
        // returns to where you were looking before freelook (F5-style).
        cameraYaw = getPlayerYaw();
        cameraPitch = getPlayerPitch();

        applyThirdPersonView(previousPerspective);

        // Raven: grab mouse cursor if in-game and no screen
        if (Mc.currentScreen() == null && Mc.isInGame()) {
            try {
                MouseHelper mouseHelper = Mc.accessor().getMouseHelper();
                if (mouseHelper != null) {
                    mouseHelper.grabMouseCursor();
                }
            } catch (Throwable t) {
                // ignore
            }
        }

        // Raven FOV restore
        boolean shouldRestoreFov = hold.getValue()
                || getFovSetting() == lastFov
                || customFov.getValue();
        if (shouldRestoreFov) {
            setFovSetting(lastFov);
        }

        // No rotation restore needed — Path B never writes player rotation.
    }

    private void applyThirdPersonView(int view) {
        if (view < 0) view = 0;
        if (view > 2) view = 2;

        setThirdPersonView(view);

        Minecraft mc = Mc.mc();
        if (mc != null) {
            IAccessorMinecraft accessor = Mc.accessor();
            EntityRenderer entityRenderer = accessor.getEntityRenderer();
            if (entityRenderer != null) {
                if (view == 0) {
                    Entity rve = Mc.renderViewEntity();
                    if (rve != null) {
                        entityRenderer.loadEntityShader(rve);
                    }
                } else if (view == 1) {
                    entityRenderer.loadEntityShader(null);
                }
            }

            RenderGlobal renderGlobal = accessor.getRenderGlobal();
            if (renderGlobal != null) {
                renderGlobal.setDisplayListEntitiesDirty();
            }
        }
    }

    // ── Typed helpers ─────────────────────────────────────────────────────

    private float getPlayerYaw() {
        EntityPlayerSP player = Mc.player();
        return player != null ? player.rotationYaw : 0f;
    }

    private float getPlayerPitch() {
        EntityPlayerSP player = Mc.player();
        return player != null ? player.rotationPitch : 0f;
    }

    private int getThirdPersonView() {
        return ((IAccessorGameSettings) Mc.settings()).getThirdPersonView();
    }

    private void setThirdPersonView(int view) {
        ((IAccessorGameSettings) Mc.settings()).setThirdPersonView(view);
    }

    private float getFovSetting() {
        return ((IAccessorGameSettings) Mc.settings()).getFovSetting();
    }

    private void setFovSetting(float value) {
        ((IAccessorGameSettings) Mc.settings()).setFovSetting(value);
    }
}
