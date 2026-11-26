#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform sampler2D uMaskSampler;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 source = texture2D(uTexSampler, vTexSamplingCoord);
  // The mask rasterizer uses Android's top-left origin; GL texture coordinates are bottom-left.
  float maskAlpha = texture2D(uMaskSampler, vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y)).r;
  gl_FragColor = vec4(source.rgb, source.a * maskAlpha);
}
