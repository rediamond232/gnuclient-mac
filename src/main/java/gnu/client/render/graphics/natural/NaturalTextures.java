package gnu.client.render.graphics.natural;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.HashSet;
import java.util.Set;

/**
 * OptiFine natural textures: random 90° UV rotation / flip from block position.
 */
public final class NaturalTextures {

    private static final Set<String> BLOCKS = new HashSet<String>();

    private NaturalTextures() {}

    public static void reload() {
        BLOCKS.clear();
        PropertiesFile p = GraphicsPackRoots.loadProperties("natural.properties");
        for (String name : PropertyValues.parseList(p.get("blocks", ""))) {
            BLOCKS.add(name);
            if (!name.contains(":")) {
                BLOCKS.add("minecraft:" + name);
            }
        }
        if (BLOCKS.isEmpty()) {
            BLOCKS.add("minecraft:grass");
            BLOCKS.add("minecraft:dirt");
            BLOCKS.add("minecraft:stone");
            BLOCKS.add("minecraft:sand");
            BLOCKS.add("minecraft:gravel");
            BLOCKS.add("minecraft:netherrack");
        }
    }

    public static int rotation(Block block, BlockPos pos, EnumFacing face) {
        if (!GraphicsModule.naturalTextures() || block == null || pos == null) {
            return 0;
        }
        net.minecraft.util.ResourceLocation rl = Block.blockRegistry.getNameForObject(block);
        String name = rl == null ? "" : rl.toString();
        if (!BLOCKS.isEmpty() && !BLOCKS.contains(name) && !BLOCKS.contains(rl == null ? "" : rl.getResourcePath())) {
            return 0;
        }
        int h = pos.getX() * 3129871 ^ pos.getZ() * 116129781 ^ pos.getY() ^ (face == null ? 0 : face.ordinal());
        h = h * 42317861 + h;
        return (h >>> 16) & 3;
    }
}
