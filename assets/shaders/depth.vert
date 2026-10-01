// Shadow map pass: the same clay wobble as clay.vert, seen from the light.
#ifdef GL_ES
precision highp float;
#endif

attribute vec3 a_position;
attribute vec3 a_normal;

uniform mat4 u_world;
uniform mat4 u_lightProjView;
uniform float u_wobble;
uniform float u_seed;

varying float v_depth;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

void main() {
    vec3 p = a_position;
    float n = noise(p * 2.7 + vec3(u_seed * 7.31, u_seed * 3.17, u_seed * 5.03)) - 0.5;
    p += a_normal * n * u_wobble;
    vec4 pos = u_lightProjView * (u_world * vec4(p, 1.0));
    v_depth = pos.z / pos.w * 0.5 + 0.5;
    gl_Position = pos;
}
