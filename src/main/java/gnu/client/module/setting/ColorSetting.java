package gnu.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * ARGB color with HSV components for ClickGUI pickers.
 */
public final class ColorSetting extends Setting<Integer> {

    private float hue;
    private float saturation;
    private float brightness;

    public ColorSetting(String name, int argb) {
        super(name, argb | 0xFF000000);
        float[] hsb = rgbToHsb(argb);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public int getRgb() {
        Integer v = getValue();
        return v == null ? 0xFFFFFFFF : (v | 0xFF000000);
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setHsb(float h, float s, float b) {
        hue = wrap01(h);
        saturation = clamp01(s);
        brightness = clamp01(b);
        super.setValue(hsbToRgb(hue, saturation, brightness) | 0xFF000000);
    }

    public void setFromPicker(float hue01, float sat, float bri) {
        setHsb(hue01, sat, bri);
    }

    @Override
    public void setValue(Integer value) {
        int argb = value == null ? 0xFFFFFFFF : (value | 0xFF000000);
        float[] hsb = rgbToHsb(argb);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        super.setValue(argb);
    }

    @Override
    public JsonElement serialize() {
        JsonObject o = new JsonObject();
        o.addProperty("rgb", getRgb());
        o.addProperty("h", hue);
        o.addProperty("s", saturation);
        o.addProperty("b", brightness);
        return o;
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            if (element != null && element.isJsonPrimitive())
                setValue(element.getAsInt());
            return;
        }
        JsonObject o = element.getAsJsonObject();
        if (o.has("h") && o.has("s") && o.has("b")) {
            setHsb(o.get("h").getAsFloat(), o.get("s").getAsFloat(), o.get("b").getAsFloat());
        } else if (o.has("rgb")) {
            setValue(o.get("rgb").getAsInt());
        }
    }

    public static int hsbToRgb(float h, float s, float b) {
        h = wrap01(h);
        s = clamp01(s);
        b = clamp01(b);
        if (s <= 0.0001f) {
            int v = Math.round(b * 255f) & 0xFF;
            return 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        float hh = h * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = b * (1f - s);
        float q = b * (1f - s * f);
        float t = b * (1f - s * (1f - f));
        float r;
        float g;
        float bl;
        switch (i % 6) {
            case 0:
                r = b;
                g = t;
                bl = p;
                break;
            case 1:
                r = q;
                g = b;
                bl = p;
                break;
            case 2:
                r = p;
                g = b;
                bl = t;
                break;
            case 3:
                r = p;
                g = q;
                bl = b;
                break;
            case 4:
                r = t;
                g = p;
                bl = b;
                break;
            default:
                r = b;
                g = p;
                bl = q;
                break;
        }
        int ri = Math.round(r * 255f) & 0xFF;
        int gi = Math.round(g * 255f) & 0xFF;
        int bi = Math.round(bl * 255f) & 0xFF;
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    public static float[] rgbToHsb(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0f;
        if (delta > 0.0001f) {
            if (max == r)
                h = ((g - b) / delta) % 6f;
            else if (max == g)
                h = (b - r) / delta + 2f;
            else
                h = (r - g) / delta + 4f;
            h /= 6f;
            if (h < 0f)
                h += 1f;
        }
        float s = max <= 0.0001f ? 0f : delta / max;
        return new float[] { h, s, max };
    }

    private static float clamp01(float v) {
        if (v < 0f)
            return 0f;
        if (v > 1f)
            return 1f;
        return v;
    }

    private static float wrap01(float v) {
        v = v % 1f;
        return v < 0f ? v + 1f : v;
    }
}
