package gnu.client.render.shaders;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ShaderOptionsTest {

    @Test
    public void parsesBooleanAndQualityDefines() {
        ShaderOptions opts = new ShaderOptions("test");
        opts.scan("#define GODRAYS\n#define SHADOW_QUALITY 1 //[0 1 2 3]\n//#define WAVING_PLANTS\n");
        assertTrue(opts.get("GODRAYS").enabled());
        assertEquals("1", opts.get("SHADOW_QUALITY").value);
        assertFalse(opts.get("WAVING_PLANTS").enabled());
    }

    @Test
    public void parsesConstSlider() {
        ShaderOptions opts = new ShaderOptions("test");
        opts.scan("const float shadowDistance = 120.0; //[60.0 80.0 120.0]\n");
        assertNotNull(opts.get("shadowDistance"));
        assertEquals("120.0", opts.get("shadowDistance").value);
        opts.cycle("shadowDistance");
        assertEquals("60.0", opts.get("shadowDistance").value);
    }

    @Test
    public void applyCommentsOutDisabledDefine() {
        ShaderOptions opts = new ShaderOptions("test");
        opts.scan("#define GODRAYS\n");
        opts.get("GODRAYS").value = "false";
        String out = opts.apply("#version 120\n#define GODRAYS\nvoid main(){}\n");
        assertTrue(out.contains("//#define GODRAYS"));
    }

    @Test
    public void screenCreatesMissingBooleans() {
        ShaderOptions opts = new ShaderOptions("test");
        opts.scanProperties("screen=GODRAYS WAVING_PLANTS\n");
        assertNotNull(opts.get("WAVING_PLANTS"));
        assertFalse(opts.get("WAVING_PLANTS").enabled());
        assertEquals("GODRAYS WAVING_PLANTS", opts.screen(""));
    }

    @Test
    public void colortexClearFalseIsHonored() {
        ShaderOptions opts = new ShaderOptions("test");
        opts.scanProperties("const bool colortex5Clear = false\n");
        assertFalse(opts.colortexClear(5));
        assertTrue(opts.colortexClear(0));
    }
}
