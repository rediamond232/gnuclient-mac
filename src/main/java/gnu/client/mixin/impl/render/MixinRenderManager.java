package gnu.client.mixin.impl.render;

import gnu.client.runtime.RotationState;
import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OpenMyau {@code MixinRenderManager} — while silent rotations are active, swap
 * the local player's body/head/pitch to the server look for the duration of
 * {@code renderEntityStatic} so F5 / FreeLook show silent combat aim.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RenderManager.class, priority = 9999)
public abstract class MixinRenderManager {

    @Unique
    private float gnu$prevRenderYawOffset;
    @Unique
    private float gnu$renderYawOffset;
    @Unique
    private float gnu$prevRotationYawHead;
    @Unique
    private float gnu$rotationYawHead;
    @Unique
    private float gnu$prevRotationPitch;
    @Unique
    private float gnu$rotationPitch;
    @Unique
    private boolean gnu$swapped;

    @Inject(method = "renderEntityStatic", at = @At("HEAD"))
    private void gnu$silentRotPre(Entity entity, float partialTicks, boolean hideDebugBox,
                                  CallbackInfoReturnable<Boolean> cir) {
        gnu$swapped = false;
        ShaderEngine.INSTANCE.bindEntities();
        if (!(entity instanceof EntityPlayerSP) || !RotationState.isRotated(1))
            return;
        EntityPlayerSP player = (EntityPlayerSP) entity;
        gnu$prevRenderYawOffset = player.prevRenderYawOffset;
        gnu$renderYawOffset = player.renderYawOffset;
        gnu$prevRotationYawHead = player.prevRotationYawHead;
        gnu$rotationYawHead = player.rotationYawHead;
        gnu$prevRotationPitch = player.prevRotationPitch;
        gnu$rotationPitch = player.rotationPitch;
        player.prevRenderYawOffset = RotationState.getPrevRenderYawOffset();
        player.renderYawOffset = RotationState.getRenderYawOffset();
        player.prevRotationYawHead = RotationState.getPrevRotationYawHead();
        player.rotationYawHead = RotationState.getRotationYawHead();
        player.prevRotationPitch = RotationState.getPrevRotationPitch();
        player.rotationPitch = RotationState.getRotationPitch();
        gnu$swapped = true;
    }

    @Inject(method = "renderEntityStatic", at = @At("RETURN"))
    private void gnu$silentRotPost(Entity entity, float partialTicks, boolean hideDebugBox,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!gnu$swapped || !(entity instanceof EntityPlayerSP))
            return;
        EntityPlayerSP player = (EntityPlayerSP) entity;
        player.prevRenderYawOffset = gnu$prevRenderYawOffset;
        player.renderYawOffset = gnu$renderYawOffset;
        player.prevRotationYawHead = gnu$prevRotationYawHead;
        player.rotationYawHead = gnu$rotationYawHead;
        player.prevRotationPitch = gnu$prevRotationPitch;
        player.rotationPitch = gnu$rotationPitch;
        gnu$swapped = false;
    }
}
