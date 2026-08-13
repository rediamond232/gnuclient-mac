#version 120

uniform sampler2D DiffuseSampler;
uniform vec2 HalfPixel;

varying vec2 vTexCoord;

void main() {
    vec4 sum = texture2D(DiffuseSampler, vTexCoord + vec2(-HalfPixel.x * 2.0, 0.0));
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(-HalfPixel.x, HalfPixel.y)) * 2.0;
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(0.0, HalfPixel.y * 2.0));
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(HalfPixel.x, HalfPixel.y)) * 2.0;
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(HalfPixel.x * 2.0, 0.0));
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(HalfPixel.x, -HalfPixel.y)) * 2.0;
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(0.0, -HalfPixel.y * 2.0));
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(-HalfPixel.x, -HalfPixel.y)) * 2.0;
    gl_FragColor = vec4(sum.rgb * 0.08333333, 1.0);
}
