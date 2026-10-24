/* Port of shaders/glava/wave/1.frag; defaults from wave.glsl live in Settings.kt.
   `audio_l` holds the raw waveform after GLava's `wrange` transform. */

#include "common.glsl"

uniform float thickness;   /* MAX_THICKNESS (pixels) */

/* min (vertical) line thickness */
#define MIN_THICKNESS 1.0
/* amplitude */
#define AMPLIFY 500.0

/* GLES has no `pixel_center_integer`, so shift to integer pixel centers */
#define FRAG_X (gl_FragCoord.x - 0.5)
#define FRAG_Y (gl_FragCoord.y - 0.5)

#define index(offset) ((texture(audio_l, vec2((FRAG_X + offset) / float(screen.x), 0.5)).r - 0.5) * AMPLIFY * gain) + 0.5

void main() {
    float
        os   = index(0.0),
        adj0 = index(-1.0),
        adj1 = index(1.0);
    float
        s0 = adj0 - os,
        s1 = adj1 - os;
    float
        dmax = max(s0, s1),
        dmin = min(s0, s1);

    float half_h = float(screen.y) * 0.5;
    float s = (os + half_h - 0.5); /* center to screen coords */
    float diff = FRAG_Y - s;
    if (abs(diff) < clamp(abs(s - half_h) * 6.0, MIN_THICKNESS, max(thickness, MIN_THICKNESS))
        || (diff <= dmax && diff >= dmin)) {
        /* GLava brightens the line with distance from the center */
        fragment = palette(FRAG_X / float(screen.x), clamp(abs(half_h - s) / 40.0, 0.0, 1.0));
    } else {
        fragment = vec4(0.0);
    }
}
