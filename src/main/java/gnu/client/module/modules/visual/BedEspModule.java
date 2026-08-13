package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.ui.ClientTheme;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights bed blocks near the player with configurable max distance (default 64).
 */
public final class BedEspModule extends Module {

    private static final int Y_RANGE = 4;
    private static final int SCAN_INTERVAL = 200;

    private final BoolSetting useTheme = addSetting(new BoolSetting("Use Theme Colors", true));
    private final SliderSetting r = addSetting(new SliderSetting("Red", 255.0f, 0.0f, 255.0f));
    private final SliderSetting g = addSetting(new SliderSetting("Green", 85.0f, 0.0f, 255.0f));
    private final SliderSetting b = addSetting(new SliderSetting("Blue", 85.0f, 0.0f, 255.0f));
    private final SliderSetting maxDist = addSetting(new SliderSetting("Max Distance", 64.0f, 8.0f, 64.0f));

    private int ticksSinceScan = SCAN_INTERVAL;
    private final List<long[]> bedCache = new ArrayList<>();

    public BedEspModule() {
        super("BedESP", "Highlight nearby beds with configurable max distance", Category.VISUALS);
        r.visibleWhen(() -> !useTheme.getValue());
        g.visibleWhen(() -> !useTheme.getValue());
        b.visibleWhen(() -> !useTheme.getValue());
    }

    @Override
    public void onEnable() {
        ticksSinceScan = SCAN_INTERVAL;
        bedCache.clear();
    }

    @Override
    public void onDisable() {
        bedCache.clear();
    }

    @Override
    public void onTick() {
        ticksSinceScan++;
        if (ticksSinceScan < SCAN_INTERVAL)
            return;
        ticksSinceScan = 0;

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null)
            return;

        double px = player.posX;
        double py = player.posY;
        double pz = player.posZ;
        double maxDistSq = maxDist.getValue() * maxDist.getValue();
        int hr = (int) Math.ceil(maxDist.getValue());

        int ipx = (int) Math.floor(px);
        int ipy = (int) Math.floor(py);
        int ipz = (int) Math.floor(pz);

        bedCache.clear();

        for (int y = ipy - Y_RANGE; y <= ipy + Y_RANGE; y++) {
            for (int x = ipx - hr; x <= ipx + hr; x++) {
                for (int z = ipz - hr; z <= ipz + hr; z++) {
                    double dx = x + 0.5 - px;
                    double dy = y + 0.5 - py;
                    double dz = z + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > maxDistSq)
                        continue;

                    BlockPos bp = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(bp);
                    if (state == null)
                        continue;
                    Block block = state.getBlock();
                    if (block instanceof BlockBed) {
                        bedCache.add(packXYZ(x, y, z));
                    }
                }
            }
        }
    }

    private static long[] packXYZ(int x, int y, int z) {
        return new long[] {
                ((long) x << 32) | (y & 0xFFFFFFFFL),
                ((long) z << 32)
        };
    }

    @Override
    public void onRender(float partialTicks) {
        if (bedCache.isEmpty())
            return;

        if (!Mc.isInGame())
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        double rvpX = vp[0];
        double rvpY = vp[1];
        double rvpZ = vp[2];

        float[] rgb = resolveColor();
        float fr = rgb[0];
        float fg = rgb[1];
        float fb = rgb[2];

        RenderHelper.begin();

        for (long[] packed : bedCache) {
            int bx = (int) (packed[0] >> 32);
            int by = (int) (packed[0]);
            int bz = (int) (packed[1] >> 32);

            double minX = bx - rvpX;
            double minY = by - rvpY;
            double minZ = bz - rvpZ;
            double maxX = minX + 1.0;
            double maxY = minY + 0.5625;
            double maxZ = minZ + 1.0;

            EspDraw.fill(minX, minY, minZ, maxX, maxY, maxZ, fr, fg, fb);
        }

        RenderHelper.end();
    }

    private float[] resolveColor() {
        if (useTheme.getValue()) {
            return ClientTheme.rgbFloats(ClientTheme.getFadeColor(0));
        }
        return new float[] {
                r.getValue() / 255.0f,
                g.getValue() / 255.0f,
                b.getValue() / 255.0f
        };
    }
}
