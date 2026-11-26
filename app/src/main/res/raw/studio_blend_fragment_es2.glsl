#version 100
precision mediump float;
uniform sampler2D uBaseSampler;
uniform sampler2D uLayerSampler;
uniform float uLayerOpacity;
uniform int uBlendMode;
varying vec2 vTexSamplingCoord;
float blendChannel(float s, float b, int m) {
    if (m == 1) return min(1.0, s + b);
    if (m == 2) return s * b;
    if (m == 3) return 1.0 - (1.0 - s) * (1.0 - b);
    if (m == 4) return b <= 0.5 ? 2.0 * s * b : 1.0 - 2.0 * (1.0 - s) * (1.0 - b);
    if (m == 5) return min(s, b);
    if (m == 6) return max(s, b);
    return s;
}
void main() {
    vec4 base = texture2D(uBaseSampler, vTexSamplingCoord);
    vec4 layer = texture2D(uLayerSampler, vTexSamplingCoord);
    float sa = clamp(layer.a * uLayerOpacity, 0.0, 1.0);
    float da = clamp(base.a, 0.0, 1.0);
    float oa = sa + da * (1.0 - sa);
    if (oa <= 0.00001) { gl_FragColor = vec4(0.0); return; }
    vec3 blended = vec3(
        blendChannel(layer.r, base.r, uBlendMode),
        blendChannel(layer.g, base.g, uBlendMode),
        blendChannel(layer.b, base.b, uBlendMode));
    vec3 premul = (1.0-sa)*da*base.rgb + (1.0-da)*sa*layer.rgb + sa*da*blended;
    gl_FragColor = vec4(premul / oa, oa);
}
