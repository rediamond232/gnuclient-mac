package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.ctm.ConnectedTextures;
import gnu.client.render.graphics.ctm.CtmMesh;
import gnu.client.render.graphics.grass.BetterGrass;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public class MixinBlockModelRenderer {

    @Inject(
            method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void gnu$ctmOrBetterGrass(IBlockAccess world, IBakedModel model, IBlockState state,
            BlockPos pos, WorldRenderer wr, boolean checkSides, CallbackInfoReturnable<Boolean> cir) {
        if (ConnectedTextures.render(world, state, pos, wr, checkSides)) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        if (state != null && state.getBlock() instanceof BlockGrass && GraphicsModule.betterGrass()) {
            TextureAtlasSprite top = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/grass_top");
            TextureAtlasSprite side = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/grass_side");
            TextureAtlasSprite bot = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/dirt");
            Block block = state.getBlock();
            int light = block.getMixedBrightnessForBlock(world, pos);
            for (EnumFacing face : EnumFacing.values()) {
                if (checkSides && !block.shouldSideBeRendered(world, pos.offset(face), face)) {
                    continue;
                }
                TextureAtlasSprite sprite = face == EnumFacing.UP ? top
                        : face == EnumFacing.DOWN ? bot
                        : (BetterGrass.useTopSide(world, pos, state, face) ? top : side);
                CtmMesh.putFace(wr, world, pos, face, sprite, light);
            }
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
