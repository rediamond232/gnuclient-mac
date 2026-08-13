package gnu.client.render.graphics.ctm;

/**
 * Standard 47-tile CTM index from an 8-bit neighbor mask
 * (N=1 E=2 S=4 W=8 NE=16 SE=32 SW=64 NW=128).
 */
public final class CtmIndex {

    private CtmIndex() {}

    public static int index47(int mask) {
        boolean n = (mask & 1) != 0;
        boolean e = (mask & 2) != 0;
        boolean s = (mask & 4) != 0;
        boolean w = (mask & 8) != 0;
        boolean ne = (mask & 16) != 0;
        boolean se = (mask & 32) != 0;
        boolean sw = (mask & 64) != 0;
        boolean nw = (mask & 128) != 0;
        int edges = (n ? 1 : 0) | (e ? 2 : 0) | (s ? 4 : 0) | (w ? 8 : 0);
        switch (edges) {
            case 0:
                return 0;
            case 1: // N
                return 1;
            case 2: // E
                return 2;
            case 4: // S
                return 3;
            case 8: // W
                return 4;
            case 3: // N+E
                return ne ? 7 : 5;
            case 6: // E+S
                return se ? 8 : 6;
            case 12: // S+W
                return sw ? 9 : 15;
            case 9: // W+N
                return nw ? 10 : 16;
            case 5: // N+S
                return 21;
            case 10: // E+W
                return 22;
            case 7: // N+E+S
                if (ne && se) {
                    return 11;
                }
                if (ne) {
                    return 23;
                }
                if (se) {
                    return 24;
                }
                return 17;
            case 14: // E+S+W
                if (se && sw) {
                    return 12;
                }
                if (se) {
                    return 25;
                }
                if (sw) {
                    return 26;
                }
                return 18;
            case 13: // S+W+N
                if (sw && nw) {
                    return 13;
                }
                if (sw) {
                    return 27;
                }
                if (nw) {
                    return 28;
                }
                return 19;
            case 11: // W+N+E
                if (nw && ne) {
                    return 14;
                }
                if (nw) {
                    return 29;
                }
                if (ne) {
                    return 30;
                }
                return 20;
            case 15:
                int corners = (ne ? 1 : 0) | (se ? 2 : 0) | (sw ? 4 : 0) | (nw ? 8 : 0);
                switch (corners) {
                    case 15:
                        return 26;
                    case 0:
                        return 46;
                    case 1:
                        return 31;
                    case 2:
                        return 32;
                    case 4:
                        return 33;
                    case 8:
                        return 34;
                    case 3:
                        return 35;
                    case 6:
                        return 36;
                    case 12:
                        return 37;
                    case 9:
                        return 38;
                    case 5:
                        return 39;
                    case 10:
                        return 40;
                    case 7:
                        return 41;
                    case 14:
                        return 42;
                    case 13:
                        return 43;
                    case 11:
                        return 44;
                    default:
                        return 26;
                }
            default:
                return 0;
        }
    }
}
