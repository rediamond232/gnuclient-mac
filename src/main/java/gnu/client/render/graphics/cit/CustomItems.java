package gnu.client.render.graphics.cit;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OptiFine Custom Item Textures.
 *
 * @see <a href="https://optifine.readthedocs.io/cit.html">CIT</a>
 */
public final class CustomItems {

    private static final List<CitRule> RULES = new ArrayList<CitRule>();

    private CustomItems() {}

    public static void reload() {
        RULES.clear();
        if (!GraphicsModule.customItems()) {
            return;
        }
        for (ResourceLocation loc : GraphicsPackRoots.listProperties("cit")) {
            CitRule rule = CitRule.parse(loc);
            if (rule != null) {
                RULES.add(rule);
            }
        }
    }

    public static void stitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.map;
        for (CitRule rule : RULES) {
            if (rule.texturePath != null) {
                rule.sprite = map.registerSprite(ConnectedLike.sprite(rule.texturePath));
            }
        }
    }

    public static ResourceLocation textureFor(ItemStack stack) {
        CitRule rule = match(stack);
        return rule == null ? null : rule.resource;
    }

    public static TextureAtlasSprite spriteFor(ItemStack stack) {
        CitRule rule = match(stack);
        return rule == null ? null : rule.sprite;
    }

    private static CitRule match(ItemStack stack) {
        if (!GraphicsModule.customItems() || stack == null || RULES.isEmpty()) {
            return null;
        }
        for (CitRule rule : RULES) {
            if (rule.matches(stack)) {
                return rule;
            }
        }
        return null;
    }

    static final class ConnectedLike {
        static ResourceLocation sprite(String path) {
            String s = path.replace('\\', '/');
            if (s.startsWith("~/")) {
                s = s.substring(2);
            }
            if (s.endsWith(".png")) {
                s = s.substring(0, s.length() - 4);
            }
            if (s.startsWith("textures/")) {
                s = s.substring("textures/".length());
            }
            if (s.contains(":")) {
                return new ResourceLocation(s);
            }
            return new ResourceLocation("minecraft", s.startsWith("optifine/") || s.startsWith("mcpatcher/")
                    ? s : "optifine/" + s);
        }
    }

    static final class CitRule {
        final List<String> items;
        final int damageMin;
        final int damageMax;
        final boolean damagePercent;
        final String nbtName;
        final int enchantId;
        final String texturePath;
        final ResourceLocation resource;
        TextureAtlasSprite sprite;

        CitRule(List<String> items, int damageMin, int damageMax, boolean damagePercent,
                String nbtName, int enchantId, String texturePath, ResourceLocation resource) {
            this.items = items;
            this.damageMin = damageMin;
            this.damageMax = damageMax;
            this.damagePercent = damagePercent;
            this.nbtName = nbtName;
            this.enchantId = enchantId;
            this.texturePath = texturePath;
            this.resource = resource;
        }

        static CitRule parse(ResourceLocation loc) {
            PropertiesFile p = GraphicsPackRoots.loadProperties(loc);
            String type = p.get("type", "item").toLowerCase(Locale.ROOT);
            if (!"item".equals(type) && !"enchantment".equals(type)) {
                return null;
            }
            List<String> items = PropertyValues.parseList(p.get("items", p.get("matchItems")));
            String dmg = p.get("damage");
            int dMin = 0;
            int dMax = Integer.MAX_VALUE;
            boolean pct = false;
            if (dmg != null) {
                pct = dmg.contains("%");
                dmg = dmg.replace("%", "");
                int dash = dmg.indexOf('-');
                if (dash >= 0) {
                    dMin = PropertyValues.parseInt(dmg.substring(0, dash), 0);
                    dMax = PropertyValues.parseInt(dmg.substring(dash + 1), Integer.MAX_VALUE);
                } else {
                    dMin = dMax = PropertyValues.parseInt(dmg, 0);
                }
            }
            String nbtName = p.get("nbt.display.Name", p.get("nbtName"));
            int ench = -1;
            String enchStr = p.get("enchantmentIDs", p.get("enchantments"));
            if (enchStr != null && !enchStr.isEmpty()) {
                List<String> ids = PropertyValues.parseList(enchStr);
                if (!ids.isEmpty()) {
                    ench = PropertyValues.parseInt(ids.get(0), -1);
                }
            }
            String tex = p.get("texture", p.get("source"));
            if (tex == null) {
                String path = loc.getResourcePath();
                tex = path.replace(".properties", ".png");
            }
            ResourceLocation res = ConnectedLike.sprite(tex);
            if (!res.getResourcePath().contains(".")) {
                res = new ResourceLocation(res.getResourceDomain(), res.getResourcePath() + ".png");
            }
            return new CitRule(items, dMin, dMax, pct, nbtName, ench, tex, res);
        }

        boolean matches(ItemStack stack) {
            if (!items.isEmpty()) {
                ResourceLocation name = Item.itemRegistry.getNameForObject(stack.getItem());
                String id = name == null ? "" : name.toString();
                String path = name == null ? "" : name.getResourcePath();
                boolean ok = false;
                for (String it : items) {
                    if (it.equals(id) || it.equals(path) || it.equals("minecraft:" + path)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return false;
                }
            }
            int dmg = stack.getItemDamage();
            if (damagePercent && stack.getMaxDamage() > 0) {
                dmg = dmg * 100 / stack.getMaxDamage();
            }
            if (dmg < damageMin || dmg > damageMax) {
                return false;
            }
            if (nbtName != null && !nbtName.isEmpty()) {
                if (!stack.hasDisplayName() || !stack.getDisplayName().equals(nbtName.replace("pattern:", ""))) {
                    if (!stack.hasDisplayName() || !stack.getDisplayName().contains(nbtName)) {
                        return false;
                    }
                }
            }
            if (enchantId >= 0) {
                Map<Integer, Integer> ench = EnchantmentHelper.getEnchantments(stack);
                if (ench == null || !ench.containsKey(Integer.valueOf(enchantId))) {
                    return false;
                }
            }
            return true;
        }
    }
}
