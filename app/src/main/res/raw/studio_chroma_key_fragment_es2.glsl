#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec3 uKeyColor;
uniform float uTolerance;
uniform float uSoftness;
uniform float uDespill;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 source = texture2D(uTexSampler, vTexSamplingCoord);
  float sourcePeak = max(max(source.r, source.g), max(source.b, 0.0001));
  float keyPeak = max(max(uKeyColor.r, uKeyColor.g), max(uKeyColor.b, 0.0001));
  vec3 sourceChroma = source.rgb / sourcePeak;
  vec3 keyChroma = uKeyColor / keyPeak;
  float chromaDistance = distance(sourceChroma, keyChroma);
  float alphaKeep = smoothstep(uTolerance, uTolerance + max(uSoftness, 0.0001), chromaDistance);
  vec3 corrected = source.rgb;
  float keyGreenDominance = step(max(uKeyColor.r, uKeyColor.b), uKeyColor.g);
  float keyBlueDominance = step(max(uKeyColor.r, uKeyColor.g), uKeyColor.b);
  float spillStrength = (1.0 - alphaKeep) * uDespill;
  float greenSpill = max(0.0, corrected.g - max(corrected.r, corrected.b));
  float blueSpill = max(0.0, corrected.b - max(corrected.r, corrected.g));
  corrected.g -= greenSpill * spillStrength * keyGreenDominance;
  corrected.b -= blueSpill * spillStrength * keyBlueDominance;
  gl_FragColor = vec4(corrected, source.a * alphaKeep);
}
