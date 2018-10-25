precision highp float;

uniform sampler2D uTexture;
uniform float scale;
varying vec2 vTexel;

void main()
{
  gl_FragColor = texture2D(uTexture, vTexel);
}
