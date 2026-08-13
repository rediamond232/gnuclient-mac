package gnu.client.mixin.impl.world;

import gnu.client.module.modules.player.GhostBlocksModule;
import gnu.client.render.graphics.lights.DynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Client-side ghost block collision: lets the local player stand on phantom
 * blocks placed by the GhostBlocks module. Only affects the client player
 * entity, never the integrated server or other entities.
 */
@Mixin(World.class)
public class MixinWorld {

    @Inject(method = "getCollidingBoundingBoxes", at = @At("RETURN"))
    public void gnu$ghostBlocksCollision(Entity entityIn, AxisAlignedBB bb, CallbackInfoReturnable<List<AxisAlignedBB>> cir) {
        if (entityIn == null || !GhostBlocksModule.isActive())
            return;
        if (entityIn != Minecraft.getMinecraft().thePlayer)
            return;
        GhostBlocksModule.addCollisionBoxes(cir.getReturnValue(), bb);
    }

    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void gnu$dynamicLights(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue() == null ? 0 : cir.getReturnValue().intValue();
        cir.setReturnValue(Integer.valueOf(
                DynamicLights.getCombinedLight((World) (Object) this, pos, vanilla)));
    }
}
