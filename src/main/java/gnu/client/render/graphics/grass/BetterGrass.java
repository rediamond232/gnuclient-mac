package gnu.client.render.graphics.grass;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockMycelium;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

/**
 * OptiFine Better Grass / Better Snow.
 *
 * @see <a href="https://optifine.readthedocs.io/better_grass.html">Better grass</a>
 */
public final class BetterGrass {

    private static boolean grass = true;
    private static boolean mycelium = true;
    private static boolean podzol = true;

    private BetterGrass() {}

    public static void reload() {
        PropertiesFile p = GraphicsPackRoots.loadProperties("bettergrass.properties");
        grass = PropertyValues.parseBoolean(p.get("grass"), true);
        mycelium = PropertyValues.parseBoolean(p.get("mycelium"), true);
        podzol = PropertyValues.parseBoolean(p.get("podzol"), true);
    }

    public static boolean useTopSide(IBlockAccess world, BlockPos pos, IBlockState state, EnumFacing face) {
        if (!GraphicsModule.betterGrass() || world == null || state == null || face == null) {
            return false;
        }
        if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof BlockGrass && grass) {
            return isGrassLike(world.getBlockState(pos.offset(face)).getBlock())
                    || world.getBlockState(pos.offset(face).down()).getBlock() instanceof BlockGrass;
        }
        if (block instanceof BlockMycelium && mycelium) {
            return world.getBlockState(pos.offset(face)).getBlock() instanceof BlockMycelium
                    || world.getBlockState(pos.offset(face).down()).getBlock() instanceof BlockMycelium;
        }
        if (block == Blocks.dirt && podzol) {
            return world.getBlockState(pos.offset(face).down()).getBlock() == Blocks.dirt;
        }
        return false;
    }

    public static boolean betterSnowLayer(IBlockAccess world, BlockPos pos) {
        if (!GraphicsModule.betterSnow() || world == null) {
            return false;
        }
        IBlockState above = world.getBlockState(pos.up());
        return above.getBlock() == Blocks.snow_layer;
    }

    private static boolean isGrassLike(Block block) {
        return block instanceof BlockGrass || block == Blocks.dirt || block == Blocks.farmland;
    }
}
