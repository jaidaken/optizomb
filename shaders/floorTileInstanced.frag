#version 430 compatibility

in vec4 vertColour;
in vec2 texCoords;

uniform sampler2D Texture;

void main() {
    vec4 texSample = texture(Texture, texCoords);
    gl_FragColor = texSample * vertColour;
}
