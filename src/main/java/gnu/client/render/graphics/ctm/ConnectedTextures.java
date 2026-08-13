package gnu.client.render.graphics.ctm;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OptiFine connected textures. Methods: ctm, horizontal, vertical, random, repeat, fixed.
 *
 * @see <a href="https://optifine.readthedocs.io/ctm.html">CTM</a>
 */
public final class ConnectedTextures {

    private static final List<CtmRule> RULES = new ArrayList<CtmRule>();
    private static final Map<String, List<CtmRule>> BY_BLOCK = new HashMap<String, List<CtmRule>>();

    private ConnectedTextures() {}

    public static void reload() {
        RULES.clear();
        BY_BLOCK.clear();
        if (!GraphicsModule.connectedTextures()) {
            return;
        }
        for (ResourceLocation loc : GraphicsPackRoots.listProperties("ctm")) {
            CtmRule rule = CtmRule.parse(loc);
            if (rule != null) {
                RULES.add(rule);
                for (String name : rule.matchBlocks) {
                    List<CtmRule> list = BY_BLOCK.get(name);
                    if (list == null) {
                        list = new ArrayList<CtmRule>();
                        BY_BLOCK.put(name, list);
                    }
                    list.add(rule);
                }
            }
        }
    }

    public static void stitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.map;
        for (CtmRule rule : RULES) {
            rule.sprites = new TextureAtlasSprite[rule.tilePaths.size()];
            for (int i = 0; i < rule.tilePaths.size(); i++) {
                String path = rule.tilePaths.get(i);
                if ("<skip>".equals(path) || "<default>".equals(path)) {
                    continue;
                }
                ResourceLocation loc = spriteLocation(path);
                rule.sprites[i] = map.registerSprite(loc);
            }
        }
    }

    public static boolean render(IBlockAccess world, IBlockState state, BlockPos pos,
            WorldRenderer wr, boolean checkSides) {
        if (!GraphicsModule.connectedTextures() || RULES.isEmpty() || state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block == null || block == Blocks.air) {
            return false;
        }
        CtmRule rule = findRule(block, state);
        if (rule == null || rule.sprites == null) {
            return false;
        }
        boolean rendered = false;
        for (EnumFacing face : EnumFacing.values()) {
            if (checkSides && !block.shouldSideBeRendered(world, pos.offset(face), face)) {
                continue;
            }
            TextureAtlasSprite sprite = rule.spriteFor(world, pos, state, face);
            if (sprite == null) {
                continue;
            }
            CtmMesh.putFace(wr, world, pos, face, sprite, block.getMixedBrightnessForBlock(world, pos));
            rendered = true;
        }
        return rendered;
    }

    private static CtmRule findRule(Block block, IBlockState state) {
        String name = nameOf(block);
        List<CtmRule> list = BY_BLOCK.get(name);
        if (list == null) {
            list = BY_BLOCK.get(Integer.toString(Block.getIdFromBlock(block)));
        }
        if (list == null) {
            return null;
        }
        int meta = block.getMetaFromState(state);
        for (CtmRule rule : list) {
            if (rule.matchesMeta(meta)) {
                return rule;
            }
        }
        return null;
    }

    static String nameOf(Block block) {
        ResourceLocation rl = Block.blockRegistry.getNameForObject(block);
        return rl == null ? "" : rl.toString();
    }

    static ResourceLocation spriteLocation(String path) {
        String s = path.replace('\\', '/');
        if (s.startsWith("~/")) {
            s = s.substring(2);
        }
        if (s.endsWith(".png")) {
            s = s.substring(0, s.length() - 4);
        }
        if (s.startsWith("assets/minecraft/")) {
            s = s.substring("assets/minecraft/".length());
        }
        if (s.startsWith("textures/")) {
            s = s.substring("textures/".length());
        }
        if (s.startsWith("minecraft:")) {
            return new ResourceLocation(s);
        }
        if (s.startsWith("optifine/") || s.startsWith("mcpatcher/")) {
            return new ResourceLocation("minecraft", s);
        }
        return new ResourceLocation("minecraft", "optifine/" + s);
    }

    static final class CtmRule {
        final String method;
        final List<String> matchBlocks;
        final List<Integer> metadatas;
        final List<String> tilePaths;
        final int width;
        final int height;
        final String connect;
        TextureAtlasSprite[] sprites;

        CtmRule(String method, List<String> matchBlocks, List<Integer> metadatas,
                List<String> tilePaths, int width, int height, String connect) {
            this.method = method;
            this.matchBlocks = matchBlocks;
            this.metadatas = metadatas;
            this.tilePaths = tilePaths;
            this.width = width;
            this.height = height;
            this.connect = connect;
        }

        static CtmRule parse(ResourceLocation loc) {
            PropertiesFile p = GraphicsPackRoots.loadProperties(loc);
            String method = p.get("method", "ctm").toLowerCase(Locale.ROOT);
            List<String> blocks = PropertyValues.parseList(p.get("matchBlocks"));
            if (blocks.isEmpty()) {
                String tiles = p.get("matchTiles");
                if (tiles != null) {
                    for (String t : PropertyValues.parseList(tiles)) {
                        Block b = Block.getBlockFromName(t);
                        if (b != null) {
                            ResourceLocation n = Block.blockRegistry.getNameForObject(b);
                            if (n != null) {
                                blocks.add(n.toString());
                            }
                        } else {
                            blocks.add("minecraft:" + t);
                        }
                    }
                }
            }
            if (blocks.isEmpty()) {
                return null;
            }
            List<Integer> metas = new ArrayList<Integer>();
            for (String m : PropertyValues.parseList(p.get("metadata", p.get("metadatas")))) {
                try {
                    metas.add(Integer.parseInt(m));
                } catch (NumberFormatException ignored) {
                }
            }
            String tilesRaw = p.get("tiles", "0");
            List<String> tilePaths = expandTiles(tilesRaw, loc);
            int w = PropertyValues.parseInt(p.get("width"), 1);
            int h = PropertyValues.parseInt(p.get("height"), 1);
            String connect = p.get("connect", "block");
            return new CtmRule(method, blocks, metas, tilePaths, w, h, connect);
        }

        boolean matchesMeta(int meta) {
            if (metadatas.isEmpty()) {
                return true;
            }
            return metadatas.contains(Integer.valueOf(meta));
        }

        TextureAtlasSprite spriteFor(IBlockAccess world, BlockPos pos, IBlockState state, EnumFacing face) {
            if (sprites == null || sprites.length == 0) {
                return null;
            }
            if ("random".equals(method) || "fixed".equals(method)) {
                int idx = "fixed".equals(method) ? 0
                        : Math.abs(hash(pos, face)) % sprites.length;
                return sprites[idx];
            }
            if ("repeat".equals(method)) {
                int x = Math.abs(pos.getX()) % Math.max(1, width);
                int y = Math.abs(axisCoord(pos, face)) % Math.max(1, height);
                int idx = y * Math.max(1, width) + x;
                if (idx >= 0 && idx < sprites.length) {
                    return sprites[idx];
                }
                return sprites[0];
            }
            if ("horizontal".equals(method)) {
                boolean left = connects(world, pos, state, horizLeft(face));
                boolean right = connects(world, pos, state, horizRight(face));
                int idx = (left ? 1 : 0) | (right ? 2 : 0);
                return pick(idx);
            }
            if ("vertical".equals(method)) {
                boolean up = connects(world, pos, state, EnumFacing.UP);
                boolean down = connects(world, pos, state, EnumFacing.DOWN);
                int idx = (up ? 1 : 0) | (down ? 2 : 0);
                return pick(idx);
            }
            int mask = connectionMask(world, pos, state, face);
            return pick(CtmIndex.index47(mask));
        }

        private TextureAtlasSprite pick(int idx) {
            if (idx < 0 || idx >= sprites.length || sprites[idx] == null) {
                return sprites[0];
            }
            return sprites[idx];
        }

        private boolean connects(IBlockAccess world, BlockPos pos, IBlockState self, EnumFacing dir) {
            if (dir == null) {
                return false;
            }
            IBlockState other = world.getBlockState(pos.offset(dir));
            if (other == null) {
                return false;
            }
            if ("material".equals(connect)) {
                return other.getBlock().getMaterial() == self.getBlock().getMaterial();
            }
            if ("tile".equals(connect)) {
                return other.getBlock() == self.getBlock()
                        && other.getBlock().getMetaFromState(other) == self.getBlock().getMetaFromState(self);
            }
            return other.getBlock() == self.getBlock();
        }

        private int connectionMask(IBlockAccess world, BlockPos pos, IBlockState self, EnumFacing face) {
            EnumFacing n = faceUp(face);
            EnumFacing e = faceRight(face);
            EnumFacing s = n == null ? null : n.getOpposite();
            EnumFacing w = e == null ? null : e.getOpposite();
            boolean bn = connects(world, pos, self, n);
            boolean be = connects(world, pos, self, e);
            boolean bs = connects(world, pos, self, s);
            boolean bw = connects(world, pos, self, w);
            boolean bne = bn && be && connects(world, pos, self, n, e);
            boolean bse = bs && be && connects(world, pos, self, s, e);
            boolean bsw = bs && bw && connects(world, pos, self, s, w);
            boolean bnw = bn && bw && connects(world, pos, self, n, w);
            int mask = 0;
            if (bn) mask |= 1;
            if (be) mask |= 2;
            if (bs) mask |= 4;
            if (bw) mask |= 8;
            if (bne) mask |= 16;
            if (bse) mask |= 32;
            if (bsw) mask |= 64;
            if (bnw) mask |= 128;
            return mask;
        }

        private boolean connects(IBlockAccess world, BlockPos pos, IBlockState self, EnumFacing a, EnumFacing b) {
            if (a == null || b == null) {
                return false;
            }
            return connects(world, pos.offset(a), self, b);
        }

        private static EnumFacing faceUp(EnumFacing face) {
            if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
                return EnumFacing.NORTH;
            }
            return EnumFacing.UP;
        }

        private static EnumFacing faceRight(EnumFacing face) {
            switch (face) {
                case NORTH:
                    return EnumFacing.WEST;
                case SOUTH:
                    return EnumFacing.EAST;
                case WEST:
                    return EnumFacing.SOUTH;
                case EAST:
                    return EnumFacing.NORTH;
                case UP:
                case DOWN:
                    return EnumFacing.EAST;
                default:
                    return EnumFacing.EAST;
            }
        }

        private static EnumFacing horizLeft(EnumFacing face) {
            EnumFacing r = faceRight(face);
            return r == null ? null : r.getOpposite();
        }

        private static EnumFacing horizRight(EnumFacing face) {
            return faceRight(face);
        }

        private static int axisCoord(BlockPos pos, EnumFacing face) {
            if (face == EnumFacing.EAST || face == EnumFacing.WEST) {
                return pos.getZ();
            }
            if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
                return pos.getX();
            }
            return pos.getZ();
        }

        private static int hash(BlockPos pos, EnumFacing face) {
            int h = pos.getX() * 3129871 ^ pos.getZ() * 116129781 ^ pos.getY();
            h = h * 42317861 + face.ordinal();
            return h;
        }

        private static List<String> expandTiles(String raw, ResourceLocation propsLoc) {
            List<String> out = new ArrayList<String>();
            String base = propsLoc.getResourcePath();
            int slash = base.lastIndexOf('/');
            String dir = slash >= 0 ? base.substring(0, slash + 1) : "";
            for (String tok : PropertyValues.parseList(raw)) {
                int dash = tok.indexOf('-');
                if (dash > 0) {
                    try {
                        int a = Integer.parseInt(tok.substring(0, dash));
                        int b = Integer.parseInt(tok.substring(dash + 1));
                        int step = a <= b ? 1 : -1;
                        for (int i = a; i != b + step; i += step) {
                            out.add(dir + i);
                        }
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (tok.matches("\\d+")) {
                    out.add(dir + tok);
                } else {
                    out.add(tok);
                }
            }
            return out;
        }
    }
}
