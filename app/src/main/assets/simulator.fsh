precision mediump float;

uniform sampler2D uTexture;
uniform vec2 uScale;
uniform int uDecideData[18];

int getNeighbour(int x, int y)
{
  return int(texture2D(uTexture, (gl_FragCoord.xy + vec2(x, y)) / uScale).r);
}

void main()
{
  int current = getNeighbour(0, 0);
  int sum = getNeighbour(-1, -1) +
            getNeighbour(-1,  1) +
            getNeighbour( 0, -1) +
            getNeighbour( 0,  1) +
            getNeighbour( 1, -1) +
            getNeighbour( 1,  0) +
            getNeighbour(-1,  0) +
            getNeighbour( 1,  1);
  float newState = float(uDecideData[current * 9 + sum]);
  gl_FragColor = vec4(newState, newState, newState, 1.0);
}
