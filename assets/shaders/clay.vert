// Play-dough surface, after the clay filter of toyquaise.com (art/scenes.mjs): outlines warped a
// little by noise, so nothing is perfectly machine-made.
#ifdef GL_ES
precision highp float;
#endif

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;

uniform mat4 u_projView;
uniform mat4 u_world;
uniform mat3 u_normalMatrix;
uniform mat4 u_lightProjView;
uniform float u_wobble;
uniform float u_seed;

varying vec3 v_normal;
varying vec3 v_worldPos;
varying vec3 v_objPos;
varying vec3 v_objNormal;
varying vec4 v_color;
varying vec4 v_lightPos;

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
    vec4 world = u_world * vec4(p, 1.0);
    v_worldPos = world.xyz;
    v_objPos = a_position;
    v_objNormal = a_normal;
    v_normal = normalize(u_normalMatrix * a_normal);
    v_color = a_color;
    v_lightPos = u_lightProjView * world;
    gl_Position = u_projView * world;
}
