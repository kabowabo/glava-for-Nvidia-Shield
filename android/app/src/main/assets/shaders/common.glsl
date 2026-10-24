/* Shared declarations for the GLava ports (see shaders/glava in the repo root).

   GLava's `smooth_audio` (util/smooth.glsl) is evaluated on the CPU in Dsp.smooth,
   so `audio_l`/`audio_r` already hold smoothed values; this is the equivalent of
   GLava's `_PRE_SMOOTHED_AUDIO` path. 1D textures don't exist in GLES, so the
   audio buffers are 1-pixel-high 2D textures.

   GLava's compile-time `#define` options are uniforms here so they can be
   changed from the remote (see Settings.kt). */

uniform ivec2 screen;
uniform int audio_sz;
uniform sampler2D audio_l;
uniform sampler2D audio_r;
uniform float gain;     /* AMPLIFY multiplier */
uniform vec3 color_lo;  /* gradient start (base of bars / center of wave) */
uniform vec3 color_hi;  /* gradient end */
uniform int rainbow;    /* 1: hue follows frequency (or x position for wave) */

out vec4 fragment;

#define TWOPI 6.28318530718
#define PI 3.14159265359

float smooth_audio(sampler2D tex, int tex_sz, float idx) {
    int i = clamp(int(round(idx * float(tex_sz - 1))), 0, tex_sz - 1);
    return texelFetch(tex, ivec2(i, 0), 0).r;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

/* `pos`: position along the spectrum [0, 1]; `t`: gradient amount [0, 1] */
vec4 palette(float pos, float t) {
    if (rainbow == 1)
        return vec4(mix(hsv2rgb(vec3(pos * 0.85, 0.85, 1.0)), vec3(1.0), t * 0.35), 1.0);
    return vec4(mix(color_lo, color_hi, t), 1.0);
}
