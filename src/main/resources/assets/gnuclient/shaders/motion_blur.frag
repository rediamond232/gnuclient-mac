#version 120

uniform sampler2D CurSampler;
uniform sampler2D AccumSampler;
uniform vec2 StreakVec;
uniform vec2 TanHalfFov;
uniform float Samples;
uniform float HistoryWeight;

varying vec2 vTexCoord;

const int MAX_TAPS = 48;
const float EDGE_GAIN_CLAMP = 3.0;

// Cheap gamma 2.0. Averaging gamma-encoded pixels darkens the result and turns
// bright trails muddy; light adds linearly, so accumulation must too.
vec3 toLinear(vec3 c) {
    return c * c;
}

vec3 toGamma(vec3 c) {
    return sqrt(max(c, vec3(0.0)));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = vTexCoord;

    // Intra-frame streak: integrates the camera sweep across one frame interval.
    // Accumulation alone shows countable discrete ghosts; this fills the gaps
    // between them so the trail is continuous.
    vec3 cur;
    if (dot(StreakVec, StreakVec) < 1.0e-9) {
        cur = toLinear(texture2D(CurSampler, uv).rgb);
    } else {
        // A pinhole camera sweeps the screen edges further than the center for
        // the same rotation: d(screen)/d(angle) grows with 1 + tan(theta)^2.
        vec2 tanTheta = TanHalfFov * (2.0 * uv - 1.0);
        vec2 v = StreakVec * min(1.0 + tanTheta * tanTheta, vec2(EDGE_GAIN_CLAMP));

        int taps = int(max(Samples, 1.0));
        float inv = 1.0 / float(taps);
        // Static per-pixel tap offset: breaks tap banding into grain without
        // shimmering frame to frame.
        float jitter = hash(uv * 1024.0) - 0.5;
        vec3 sum = vec3(0.0);
        for (int i = 0; i < MAX_TAPS; i++) {
            if (i >= taps) {
                break;
            }
            float t = clamp((float(i) + 0.5 + jitter) * inv, 0.0, 1.0);
            sum += toLinear(texture2D(CurSampler, uv - v * t).rgb);
        }
        // Box weights: a shutter integrates its open interval uniformly.
        cur = sum * inv;
    }

    vec3 hist = toLinear(texture2D(AccumSampler, uv).rgb);
    vec3 blended = toGamma(mix(cur, hist, HistoryWeight));
    // The accumulation buffer is RGBA8. Once a ghost fades to within one
    // quantization step, rounding pins it there forever. A fixed per-pixel
    // sub-step bias lets those last steps round down and decay. It must be
    // frame-invariant, or static parts of the image shimmer.
    float d = (hash(uv * 512.0) - 0.5) / 255.0;
    gl_FragColor = vec4(clamp(blended + d, 0.0, 1.0), 1.0);
}
