package gnu.client.mixin.impl.entity;

import com.mojang.authlib.GameProfile;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.mc.Mc;
import gnu.client.event.PostMotionEvent;
import gnu.client.event.PostUpdateEvent;
import gnu.client.event.PreMotionEvent;
import gnu.client.event.PreUpdateEvent;
import gnu.client.utility.RotationUtils;
import net.aspw.viaforgeplus.common.CommonViaForgePlus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer {

    @Shadow @Final public NetHandlerPlayClient sendQueue;
    @Shadow private double lastReportedPosX;
    @Shadow private double lastReportedPosY;
    @Shadow private double lastReportedPosZ;
    @Shadow private float lastReportedYaw;
    @Shadow private float lastReportedPitch;
    @Shadow private int positionUpdateTicks;
    @Shadow private boolean serverSprintState;
    @Shadow private boolean serverSneakState;
    @Shadow
    protected Minecraft mc;

    @Shadow
    protected abstract boolean isCurrentViewEntity();

    public MixinEntityPlayerSP(World world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void onUpdatePre(CallbackInfo c) {
        if (PlayerUpdateHook.onUpdateHead(this)) {
            c.cancel();
            return;
        }
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            RotationUtils.prevRenderPitch = RotationUtils.renderPitch;
            RotationUtils.prevRenderYaw = RotationUtils.renderYaw;
            MinecraftForge.EVENT_BUS.post(new PreUpdateEvent());
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void onUpdatePost(CallbackInfo c) {
        PlayerUpdateHook.onUpdateReturn(this);
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ))) {
            MinecraftForge.EVENT_BUS.post(new PostUpdateEvent());
        }
    }

    @Overwrite
    public void onUpdateWalkingPlayer() {
        PlayerUpdateHook.beforeWalkingPlayer(this);
        PreMotionEvent.setRotations = false;
        PreMotionEvent.setRenderYaw(false);
        RotationUtils.setFakeRotations = false;
        PreMotionEvent preMotionEvent = new PreMotionEvent(
                this.posX,
                this.getEntityBoundingBox().minY,
                this.posZ,
                this.rotationYaw,
                this.rotationPitch,
                this.onGround,
                this.isSprinting(),
                this.isSneaking()
        );

        MinecraftForge.EVENT_BUS.post(preMotionEvent);
        RotationUtils.serverRotations = new float[] { preMotionEvent.getYaw(), preMotionEvent.getPitch() };

        // Route sprint/sneak C0B through Mc so AuraCombatPacketGuard can cancel
        // duplicates (BadPacketsX) and server*State only flips on a real send.
        EntityPlayerSP self = (EntityPlayerSP) (Object) this;
        boolean sprinting = preMotionEvent.isSprinting();
        if (sprinting != this.serverSprintState)
            Mc.sendSprintActionPacket(self, sprinting);

        boolean sneaking = preMotionEvent.isSneaking();
        if (sneaking != this.serverSneakState)
            Mc.sendSneakActionPacket(self, sneaking);

        if (this.isCurrentViewEntity()) {
            if (PreMotionEvent.setRenderYaw()) {
                RotationUtils.setRenderYaw(preMotionEvent.getYaw());
            }

            RotationUtils.renderPitch = preMotionEvent.getPitch();
            RotationUtils.renderYaw = preMotionEvent.getYaw();

            if (RotationUtils.setFakeRotations) {
                RotationUtils.renderPitch = RotationUtils.fakeRotations[1];
                RotationUtils.renderYaw = RotationUtils.fakeRotations[0];
                RotationUtils.setRenderYaw(RotationUtils.renderYaw);
            }
            RotationUtils.setFakeRotations = false;

            double dx = preMotionEvent.getPosX() - this.lastReportedPosX;
            double dy = preMotionEvent.getPosY() - this.lastReportedPosY;
            double dz = preMotionEvent.getPosZ() - this.lastReportedPosZ;
            double dyaw = preMotionEvent.getYaw() - this.lastReportedYaw;
            double dpitch = preMotionEvent.getPitch() - this.lastReportedPitch;
            boolean moved = dx * dx + dy * dy + dz * dz > 9.0E-4 || this.positionUpdateTicks >= 20;
            boolean rotated = dyaw != 0.0 || dpitch != 0.0;

            boolean viaModern = false;
            CommonViaForgePlus manager = CommonViaForgePlus.getManager();
            if (manager != null)
                viaModern = manager.getTargetVersion().getVersion() > 47;

            if (this.ridingEntity == null) {
                if (moved && rotated) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(
                            preMotionEvent.getPosX(), preMotionEvent.getPosY(), preMotionEvent.getPosZ(),
                            preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
                } else if (moved) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                            preMotionEvent.getPosX(), preMotionEvent.getPosY(), preMotionEvent.getPosZ(),
                            preMotionEvent.isOnGround()));
                } else if (rotated) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(
                            preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
                } else if (viaModern) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                            preMotionEvent.getPosX(), preMotionEvent.getPosY(), preMotionEvent.getPosZ(),
                            preMotionEvent.isOnGround()));
                } else {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer(preMotionEvent.isOnGround()));
                }
            } else {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(
                        this.motionX, -999.0D, this.motionZ,
                        preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
                moved = false;
            }

            ++this.positionUpdateTicks;

            if (moved) {
                this.lastReportedPosX = preMotionEvent.getPosX();
                this.lastReportedPosY = preMotionEvent.getPosY();
                this.lastReportedPosZ = preMotionEvent.getPosZ();
                this.positionUpdateTicks = 0;
            }

            if (rotated) {
                this.lastReportedYaw = preMotionEvent.getYaw();
                this.lastReportedPitch = preMotionEvent.getPitch();
            }
        }
        PlayerUpdateHook.onAfterWalkingPlayer(this);
        MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
    }
}
