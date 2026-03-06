#version 430 compatibility

// Static variant of basicEffect_tbo — uses transform matrix instead of bone SSBO.

out vec3 vertColour;
out vec3 vertNormal;
out vec2 texCoords;

uniform mat4 transform;
uniform float DepthBias;
uniform vec2 UVScale = vec2(1,1);

void main()
{
	vec4 position = vec4(gl_Vertex.xyz, 1.0);
	vec4 normal = vec4(gl_Normal.xyz, 0.0);

	texCoords = gl_MultiTexCoord0.st * UVScale.xy;

	vertNormal = (transform * normal).xyz;
	vertColour = vec3(1.0);

	vec4 o = gl_ModelViewProjectionMatrix * transform * position;
	o.z -= DepthBias;
	gl_Position = o;
}
