package gnu.client.render.graphics.random;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OptiFine random entity / random mob textures.
 *
 * @see <a href="https://optifine.readthedocs.io/random_entities.html">Random entities</a>
 */
public final class RandomEntities {

    private static final Map<String, List<RandomRule>> RULES = new HashMap<String, List<RandomRule>>();

    private RandomEntities() {}

    public static void reload() {
        RULES.clear();
        if (!GraphicsModule.randomEntities()) {
            return;
        }
        loadFolder("random/entity");
        loadFolder("mob");
    }

    private static void loadFolder(String folder) {
        for (ResourceLocation loc : GraphicsPackRoots.listProperties(folder)) {
            RandomRule rule = RandomRule.parse(loc);
            if (rule == null) {
                continue;
            }
            List<RandomRule> list = RULES.get(rule.entity);
            if (list == null) {
                list = new ArrayList<RandomRule>();
                RULES.put(rule.entity, list);
            }
            list.add(rule);
        }
    }

    public static ResourceLocation textureFor(Entity entity, ResourceLocation vanilla) {
        if (!GraphicsModule.randomEntities() || entity == null || RULES.isEmpty()) {
            return vanilla;
        }
        String name = EntityList.getEntityString(entity);
        if (name == null) {
            name = entity.getClass().getSimpleName();
        }
        name = name.toLowerCase();
        List<RandomRule> list = RULES.get(name);
        if (list == null) {
            return vanilla;
        }
        int y = (int) entity.posY;
        String biome = "";
        if (entity.worldObj != null) {
            net.minecraft.world.biome.BiomeGenBase b =
                    entity.worldObj.getBiomeGenForCoords(new net.minecraft.util.BlockPos(entity));
            if (b != null) {
                biome = b.biomeName;
            }
        }
        for (RandomRule rule : list) {
            ResourceLocation tex = rule.pick(entity.getEntityId(), y, biome);
            if (tex != null) {
                return tex;
            }
        }
        return vanilla;
    }

    static final class RandomRule {
        final String entity;
        final List<ResourceLocation> textures;
        final List<Integer> weights;
        final List<String> biomes;
        final String heights;

        RandomRule(String entity, List<ResourceLocation> textures, List<Integer> weights,
                List<String> biomes, String heights) {
            this.entity = entity;
            this.textures = textures;
            this.weights = weights;
            this.biomes = biomes;
            this.heights = heights;
        }

        static RandomRule parse(ResourceLocation loc) {
            PropertiesFile p = GraphicsPackRoots.loadProperties(loc);
            String path = loc.getResourcePath();
            int slash = path.lastIndexOf('/');
            String file = slash >= 0 ? path.substring(slash + 1) : path;
            String entity = file.replace(".properties", "").toLowerCase();
            List<ResourceLocation> textures = new ArrayList<ResourceLocation>();
            for (Map.Entry<String, String> e : p.asMap().entrySet()) {
                if (e.getKey().startsWith("textures.") || e.getKey().startsWith("skins.")) {
                    textures.add(toTex(e.getValue(), path));
                }
            }
            if (textures.isEmpty()) {
                String t = p.get("textures", p.get("skins"));
                if (t != null) {
                    for (String s : PropertyValues.parseList(t)) {
                        textures.add(toTex(s, path));
                    }
                }
            }
            if (textures.isEmpty()) {
                return null;
            }
            List<Integer> weights = new ArrayList<Integer>();
            for (String w : PropertyValues.parseList(p.get("weights"))) {
                weights.add(Integer.valueOf(PropertyValues.parseInt(w, 1)));
            }
            return new RandomRule(entity, textures, weights,
                    PropertyValues.parseList(p.get("biomes")), p.get("heights"));
        }

        private static ResourceLocation toTex(String s, String propsPath) {
            String v = s.replace('\\', '/');
            if (v.startsWith("~/")) {
                v = "optifine/" + v.substring(2);
            }
            if (!v.endsWith(".png")) {
                v = v + ".png";
            }
            if (v.contains(":")) {
                return new ResourceLocation(v);
            }
            return new ResourceLocation("minecraft", v);
        }

        ResourceLocation pick(int entityId, int y, String biome) {
            if (!biomes.isEmpty()) {
                boolean ok = false;
                for (String b : biomes) {
                    if (b.equalsIgnoreCase(biome) || b.equalsIgnoreCase(biome.replace(' ', '_'))) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return null;
                }
            }
            if (heights != null && !PropertyValues.matchesIntRangeList(heights, y)) {
                return null;
            }
            int n = textures.size();
            if (n == 1) {
                return textures.get(0);
            }
            int total = 0;
            if (weights.size() == n) {
                for (Integer w : weights) {
                    total += Math.max(0, w.intValue());
                }
            } else {
                total = n;
            }
            if (total <= 0) {
                return textures.get(0);
            }
            int slot = Math.abs(entityId) % total;
            if (weights.size() == n) {
                int acc = 0;
                for (int i = 0; i < n; i++) {
                    acc += Math.max(0, weights.get(i).intValue());
                    if (slot < acc) {
                        return textures.get(i);
                    }
                }
            }
            return textures.get(Math.abs(entityId) % n);
        }
    }
}
