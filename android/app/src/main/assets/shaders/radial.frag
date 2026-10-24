/* Port of shaders/glava/radial/1.frag (no alpha blending); defaults from
   radial.glsl live in Settings.kt. */

#include "common.glsl"

uniform float radius;      /* C_RADIUS (pixels) */
uniform float nbars;       /* NBARS */
uniform float bar_width;   /* BAR_WIDTH (pixels) */
uniform float rotation;    /* extra rotation (radians), animated by the spin setting */

/* center line thickness (pixels) */
#define C_LINE 2.0
/* outline color */
#define OUTLINE vec4(color_lo * 0.4, 1.0)
/* Amplify magnitude of the results each bar displays */
#define AMPLIFY 300.0
/* How quickly the gradient transitions, in pixels */
#define GRADIENT 95.0
/* Bar color */
#define COLOR palette(pos, clamp(d / GRADIENT, 0.0, 1.0))

void main() {
    float /* translate (x, y) to use (0, 0) as the center of the screen */
        dx = gl_FragCoord.x - (float(screen.x) / 2.0),
        dy = gl_FragCoord.y - (float(screen.y) / 2.0);
    float d = sqrt((dx * dx) + (dy * dy)); /* distance */
    if (d > radius - (C_LINE / 2.0) && d < radius + (C_LINE / 2.0)) {
        fragment = OUTLINE;
        return;
    }
    if (d > radius) {
        /* rotate the frame so bar boundaries spin with the spectrum */
        float theta = atan(dy, dx) - rotation;
        float section = (TWOPI / nbars);              /* range (radians) for each bar */
        float center = section / 2.0;                 /* center line angle */
        float m = mod(theta, section);                /* position in section (radians) */
        float ym = d * sin(center - m);               /* distance from center line (cartesian coords) */
        if (abs(ym) < bar_width / 2.0) {              /* if within width, draw audio */
            float idx = theta + PI / 2.0;             /* position (radians) in texture */
            float dir = mod(abs(idx), TWOPI);         /* absolute position, [0, 2pi) */
            if (dir > PI)
                idx = -sign(idx) * (TWOPI - dir);     /* Re-correct position values to [-pi, pi) */
            idx = -idx;
            float pos = floor(abs(idx) / section) / (nbars / 2.0); /* bar position, [0, 1) */
            float v;
            if (idx > 0.0) v = smooth_audio(audio_l, audio_sz, pos); /* left buffer */
            else           v = smooth_audio(audio_r, audio_sz, pos); /* right buffer */
            v *= AMPLIFY * gain;                                     /* amplify */
            d -= radius + (C_LINE / 2.0);                            /* start bar after circle */
            if (d <= v) {
                fragment = COLOR;
                return;
            }
        }
    }
    fragment = vec4(0.0); /* default frag color */
}
