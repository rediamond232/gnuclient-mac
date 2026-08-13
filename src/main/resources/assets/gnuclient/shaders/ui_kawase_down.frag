#version 120

uniform sampler2D DiffuseSampler;
uniform vec2 HalfPixel;

varying vec2 vTexCoord;

void main() {
    vec4 sum = texture2D(DiffuseSampler, vTexCoord) * 4.0;
    sum += texture2D(DiffuseSampler, vTexCoord - HalfPixel);
    sum += texture2D(DiffuseSampler, vTexCoord + HalfPixel);
    sum += texture2D(DiffuseSampler, vTexCoord + vec2(HalfPixel.x, -HalfPixel.y));
    sum += texture2D(DiffuseSampler, vTexCoord - vec2(HalfPixel.x, -HalfPixel.y));
    gl_FragColor = vec4(sum.rgb * 0.125, 1.0);
}
