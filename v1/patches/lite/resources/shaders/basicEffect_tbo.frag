#version 430 compatibility

// BONE_TBO: GLSL 430 fragment shader for SSBO-based character rendering.
// Self-contained - no #include of shared GLSL 110/120 util files to avoid link errors.

in vec3 vertColour;
in vec3 vertNormal;
in vec2 texCoords;

out vec4 fragColor;

uniform sampler2D Texture;
uniform float Alpha;
uniform float LightingAmount;

uniform vec3 TintColour;

uniform vec3 AmbientColour;
uniform vec3 Light0Direction;
uniform vec3 Light0Colour;
uniform vec3 Light1Direction;
uniform vec3 Light1Colour;
uniform vec3 Light2Direction;
uniform vec3 Light2Colour;
uniform vec3 Light3Direction;
uniform vec3 Light3Colour;
uniform vec3 Light4Direction;
uniform vec3 Light4Colour;
uniform float HueChange;

// Inlined from util/math.glsl (avoids linking GLSL 110 shader objects into a 430 program)
vec3 rgb2hsv(vec3 c)
{
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Inlined from util/hueShift.glsl
vec3 hueShift(vec3 col, float amount)
{
    vec3 col3 = rgb2hsv(col);
    col3.r += amount;
    while(col3.r > 1.0) { col3.r -= 2.0; }
    while(col3.r < -1.0) { col3.r += 2.0; }
    return hsv2rgb(col3);
}

void main()
{
	vec3 normal = normalize(vertNormal);

	vec4 texSample = texture(Texture, texCoords);
	if(texSample.w < 0.01)
	{
	    discard;
	}

	vec3 col = texSample.xyz;

    if(HueChange != 0.0)
    {
        col = hueShift(col, HueChange);
    }

	vec3 lighting = vec3(0.0);

	lighting += Light0Colour * max(dot(normal, normalize(Light0Direction)), 0.0);
	lighting += Light1Colour * max(dot(normal, normalize(Light1Direction)), 0.0);
	lighting += Light2Colour * max(dot(normal, normalize(Light2Direction)), 0.0);
	lighting += Light3Colour * max(dot(normal, normalize(Light3Direction)), 0.0);
	lighting += Light4Colour * max(dot(normal, normalize(Light4Direction)), 0.0);

	lighting += AmbientColour;
	lighting = min(lighting, vec3(1.0));

	vec3 tintColour = TintColour;
	col = col * tintColour * lighting;

	fragColor = vec4(col * vertColour, Alpha * texSample.w);
}
