// Play-dough lighting, after the clay filter of toyquaise.com: soft matte diffuse with a wide
// wrap, a low broad highlight, fine fingertip grain, and teal-tinted soft shadows.
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

uniform vec4 u_color;
uniform vec3 u_lightDir;
uniform vec3 u_eye;
uniform sampler2D u_grainTex;
uniform float u_grain;
uniform float u_grainScale;
uniform sampler2D u_shadowMap;
uniform float u_shadowTexel;
uniform float u_shadows;
uniform float u_emissive;
uniform vec3 u_flash;

varying vec3 v_normal;
varying vec3 v_worldPos;
varying vec3 v_objPos;
varying vec3 v_objNormal;
varying vec4 v_color;
varying vec4 v_lightPos;

float unpackDepth(vec4 c) {
    return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}

float lit(vec2 uv, float depth, float bias) {
    return depth - bias > unpackDepth(texture2D(u_shadowMap, uv)) ? 0.0 : 1.0;
}

/** 1 in light, 0 in shadow; a 3x3 percentage-closer filter softens the edge. */
float shadow(float ndotl) {
    vec3 p = v_lightPos.xyz / v_lightPos.w * 0.5 + 0.5;
    if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) return 1.0;
    float bias = mix(0.0035, 0.0012, clamp(ndotl, 0.0, 1.0));
    float s = 0.0;
    float t = u_shadowTexel * 1.5;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            s += lit(p.xy + vec2(float(x), float(y)) * t, p.z, bias);
        }
    }
    return s / 9.0;
}

void main() {
    vec3 n = normalize(v_normal);

    // Fingertip grain: a small tileable noise, projected along the three object axes.
    vec3 w = abs(normalize(v_objNormal));
    w = w * w * w * w;
    w /= (w.x + w.y + w.z);
    vec3 q = v_objPos * u_grainScale;
    vec3 gx = texture2D(u_grainTex, q.yz).xyz - 0.5;
    vec3 gy = texture2D(u_grainTex, q.zx).xyz - 0.5;
    vec3 gz = texture2D(u_grainTex, q.xy).xyz - 0.5;
    vec3 g = gx * w.x + gy * w.y + gz * w.z;
    n = normalize(n + g * u_grain);
    // ...and a little uneven colour, as kneaded dough never mixes perfectly.
    float blotch = texture2D(u_grainTex, q.xz * 0.11 + q.yy * 0.07).w - 0.5;

    vec3 base = u_color.rgb * v_color.rgb * (1.0 + blotch * 0.10);
    base = pow(base, vec3(2.2));

    vec3 l = normalize(u_lightDir);
    vec3 v = normalize(u_eye - v_worldPos);
    float ndotl = dot(n, l);
    float wrap = 0.3;
    float diffuse = max(0.0, (ndotl + wrap) / (1.0 + wrap));
    diffuse = diffuse * diffuse * (3.0 - 2.0 * diffuse);
    float sh = mix(1.0, shadow(ndotl), u_shadows);

    // Sky from above (mint), bounce from the table below (warm), little of either: the dough
    // should be modelled by the light, as in the site's clay filter.
    float up = n.y * 0.5 + 0.5;
    vec3 ambient = mix(vec3(0.13, 0.13, 0.12), vec3(0.26, 0.31, 0.31), up);
    vec3 sun = vec3(1.0, 0.95, 0.88) * 1.0;

    vec3 h = normalize(l + v);
    float spec = pow(max(dot(n, h), 0.0), 12.0) * 0.14;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.05;

    // Shadows lean teal, like the site's drop shadow (rgb 0.03 0.16 0.15).
    vec3 shadowTint = mix(vec3(0.42, 0.62, 0.62), vec3(1.0), sh);
    vec3 col = base * (ambient * shadowTint + sun * diffuse * sh) + vec3(spec * sh + rim);
    col = mix(col, base * 1.25, u_emissive);
    col = pow(max(col, vec3(0.0)), vec3(1.0 / 2.2)) + u_flash;
    gl_FragColor = vec4(col, u_color.a * v_color.a);
}
