#version 120

uniform sampler2D DiffuseSampler;
uniform vec2 u_size;
uniform float u_radius;
uniform float u_alpha;
uniform vec2 u_uv0;
uniform vec2 u_uv1;

varying vec2 vTexCoord;

void main() {
    vec2 local = vTexCoord;
    float dist = length(max((abs(local - 0.5) + 0.5) * u_size - u_size + u_radius, 0.0))
            - u_radius + 0.5;
    float alpha = u_alpha * smoothstep(1.0, 0.0, dist);
    vec2 uv = mix(u_uv0, u_uv1, local);
    vec3 rgb = texture2D(DiffuseSampler, uv).rgb;
    gl_FragColor = vec4(rgb, alpha);
}
