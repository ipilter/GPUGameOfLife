uniform mat4 uMVPMatrix;
attribute vec4 aPosition;
attribute vec2 aTexel;
varying vec2 vTexel;

void main()
{
  vTexel = aTexel;
  gl_Position = uMVPMatrix * aPosition;
}
