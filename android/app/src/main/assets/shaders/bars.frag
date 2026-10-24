/* Port of shaders/glava/bars/1.frag; defaults from bars.glsl live in Settings.kt.
   The Shield gives a mono mix, so the stereo layout (low frequencies in the
   center) uses the same buffer on both sides. */

#include "common.glsl"

uniform float bar_width;   /* BAR_WIDTH (pixels) */
uniform float bar_gap;     /* BAR_GAP (pixels) */
uniform int bar_outline;   /* BAR_OUTLINE_WIDTH 1 or 0 */
uniform int mirror;        /* 1: mirrored from the center, 0: low to high, left to right */
uniform int flip;          /* FLIP */

/* Amplify magnitude of the results each bar displays */
#define AMPLIFY 300.0
/* How quickly the gradient transitions, in pixels */
#define GRADIENT 80.0
/* Bar color: gradient from `color_lo` to `color_hi` */
#define COLOR palette(p, clamp(d / GRADIENT, 0.0, 1.0))
/* Outline color. By default this provides a 'glint' outline based on the bar color */
#define BAR_OUTLINE vec4(COLOR.rgb * 1.5, COLOR.a)

void main() {
    float width = float(screen.x);
    float height = float(screen.y);

    float dx = mirror == 1 ? gl_FragCoord.x - (width / 2.0) : gl_FragCoord.x;
    float d = flip == 0 ? gl_FragCoord.y : height - gl_FragCoord.y;
    float outline_w = bar_outline == 1 ? 1.0 : 0.0;

    float section = bar_width + bar_gap;     /* size of section for each bar (including gap) */
    float center = section / 2.0;            /* half section, distance to center             */
    float m = abs(mod(dx, section));         /* position in section                          */
    float md = m - center;                   /* position in section from center line         */
    float nbars = floor((width * 0.5) / section) * 2.0;
    if (md < ceil(bar_width / 2.0) && md >= -floor(bar_width / 2.0)) { /* if not in gap */
        float s = dx / section;
        float p = (sign(s) == 1.0 ? ceil(s) : floor(s));
        p /= mirror == 1 ? nbars / 2.0 : nbars;
        p += sign(p) * ((0.5 + center) / width); /* index center of bar position */
        /* ignore out of bounds values */
        if (p > 1.0 || p < -1.0) {
            fragment = vec4(0.0);
            return;
        }
        float v;
        if (p > 0.0) {
            v = smooth_audio(audio_r, audio_sz, p);
        } else {
            p = abs(p);
            v = smooth_audio(audio_l, audio_sz, p);
        }

        v *= AMPLIFY * gain;           /* amplify result                                  */
        if (d < v - outline_w) {       /* if within range of the reported frequency, draw */
            if (md < ceil(bar_width / 2.0) - outline_w && md >= -floor(bar_width / 2.0) + outline_w)
                fragment = COLOR;
            else
                fragment = BAR_OUTLINE;
            return;
        }
        if (d <= v) {
            fragment = BAR_OUTLINE;
            return;
        }
    }
    fragment = vec4(0.0); /* default frag color */
}
