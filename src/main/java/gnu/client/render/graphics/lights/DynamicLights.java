package gnu.client.render.graphics.lights;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import gnu.client.render.terrain.GnuTerrainRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OptiFine-style dynamic lights (held / dropped items). Render-only via combined-light mixin
 * plus a coalesced remesh radius when a light source moves to a new block.
 *
 * @see <a href="https://optifine.readthedocs.io/dynamic_lights.html">Dynamic lights</a>
 */
public final class DynamicLights {

    private static final Map<Item, Integer> ITEM_LEVELS = new HashMap<Item, Integer>();
    private static final List<Light> LIGHTS = new ArrayList<Light>();
    private static int lastPlayerBlockX = Integer.MIN_VALUE;
    private static int lastPlayerBlockY = Integer.MIN_VALUE;
    private static int lastPlayerBlockZ = Integer.MIN_VALUE;

    private DynamicLights() {}

    public static void reload() {
        ITEM_LEVELS.clear();
        ITEM_LEVELS.put(Item.getItemFromBlock(Blocks.torch), 14);
        ITEM_LEVELS.put(Item.getItemFromBlock(Blocks.glowstone), 15);
        ITEM_LEVELS.put(Item.getItemFromBlock(Blocks.lit_pumpkin), 15);
        ITEM_LEVELS.put(Item.getItemFromBlock(Blocks.lava), 15);
        ITEM_LEVELS.put(Items.lava_bucket, 15);
        ITEM_LEVELS.put(Item.getItemFromBlock(Blocks.sea_lantern), 15);
        ITEM_LEVELS.put(Items.nether_star, 14);
        PropertiesFile p = GraphicsPackRoots.loadProperties("dynamic_lights.properties");
        for (Map.Entry<String, String> e : p.asMap().entrySet()) {
            if (!e.getKey().startsWith("items.")) {
                continue;
            }
            String name = e.getKey().substring("items.".length()).replace('_', ':');
            Item item = Item.getByNameOrId(name);
            if (item == null) {
                item = Item.getByNameOrId("minecraft:" + name);
            }
            if (item != null) {
                ITEM_LEVELS.put(item, Integer.valueOf(PropertyValues.parseInt(e.getValue(), 14)));
            }
        }
    }

    public static void tick() {
        LIGHTS.clear();
        if (!GraphicsModule.dynamicLights()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        World world = mc.theWorld;
        collect(mc.thePlayer, mc.thePlayer.getHeldItem());
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof Entity)) {
                continue;
            }
            Entity e = (Entity) o;
            if (e instanceof EntityItem) {
                collect(e, ((EntityItem) e).getEntityItem());
            }
        }
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.posY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (px != lastPlayerBlockX || py != lastPlayerBlockY || pz != lastPlayerBlockZ) {
            lastPlayerBlockX = px;
            lastPlayerBlockY = py;
            lastPlayerBlockZ = pz;
            GnuTerrainRenderer.INSTANCE.markRangeDirty(px - 8, py - 8, pz - 8, px + 8, py + 8, pz + 8);
        }
    }

    private static void collect(Entity entity, ItemStack stack) {
        int level = levelOf(stack);
        if (level <= 0) {
            return;
        }
        LIGHTS.add(new Light(entity.posX, entity.posY + entity.getEyeHeight() * 0.5, entity.posZ, level));
    }

    private static int levelOf(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        Integer mapped = ITEM_LEVELS.get(stack.getItem());
        if (mapped != null) {
            return mapped.intValue();
        }
        if (stack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block != null) {
                return block.getLightValue();
            }
        }
        return 0;
    }

    public static int combine(World world, BlockPos pos, int vanilla) {
        if (!GraphicsModule.dynamicLights() || LIGHTS.isEmpty() || pos == null) {
            return vanilla;
        }
        int block = vanilla & 15;
        int sky = vanilla >> 20 & 15;
        int extra = 0;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (int i = 0; i < LIGHTS.size(); i++) {
            Light l = LIGHTS.get(i);
            double dx = l.x - x;
            double dy = l.y - y;
            double dz = l.z - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int v = l.level - (int) dist;
            if (v > extra) {
                extra = v;
            }
        }
        if (extra <= block) {
            return vanilla;
        }
        extra = Math.min(15, extra);
        return (sky << 20) | (extra << 4);
    }

    public static int getCombinedLight(World world, BlockPos pos, int vanilla) {
        return combine(world, pos, vanilla);
    }

    private static final class Light {
        final double x;
        final double y;
        final double z;
        final int level;

        Light(double x, double y, double z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }
}
