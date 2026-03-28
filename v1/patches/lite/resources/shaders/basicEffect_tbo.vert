#version 430 compatibility

// BONE_TBO: SSBO-based vertex shader for character rendering.
// Reads bone matrices from a Shader Storage Buffer Object instead of uniform array.

out vec3 vertColour;
out vec3 vertNormal;
out vec2 texCoords;

in vec4 boneIndices;
in vec4 boneWeights;
uniform float DepthBias;
uniform vec2 UVScale = vec2(1,1);

// SSBO bone data: 4 vec4s per mat4, stored row-major (same as Matrix4f.store()).
// Shader transposes to column-major, matching glUniformMatrix4fv(transpose=true).
layout(std430, binding = 0) buffer BoneData {
	vec4 boneTexels[];
};
uniform int boneBaseIndex;

mat4 getBoneMatrix(int boneIdx) {
	int base = boneBaseIndex + boneIdx * 4;
	vec4 row0 = boneTexels[base];
	vec4 row1 = boneTexels[base + 1];
	vec4 row2 = boneTexels[base + 2];
	vec4 row3 = boneTexels[base + 3];
	// Transpose: convert rows to columns
	return mat4(
		vec4(row0.x, row1.x, row2.x, row3.x),
		vec4(row0.y, row1.y, row2.y, row3.y),
		vec4(row0.z, row1.z, row2.z, row3.z),
		vec4(row0.w, row1.w, row2.w, row3.w)
	);
}

void main()
{
	vec4 position = vec4(gl_Vertex.xyz, 1.0);
	vec4 normal = vec4(gl_Normal.xyz, 0.0);

	texCoords = gl_MultiTexCoord0.st * UVScale.xy;

	mat4 boneEffect = mat4(0.0);
	if(boneWeights.x > 0.0)
		boneEffect += getBoneMatrix(int(boneIndices.x)) * boneWeights.x;
	if(boneWeights.y > 0.0)
		boneEffect += getBoneMatrix(int(boneIndices.y)) * boneWeights.y;
	if(boneWeights.z > 0.0)
		boneEffect += getBoneMatrix(int(boneIndices.z)) * boneWeights.z;
	if(boneWeights.w > 0.0)
		boneEffect += getBoneMatrix(int(boneIndices.w)) * boneWeights.w;

	normal = boneEffect * normal;
	vertNormal = normal.xyz;

	vertColour = vec3(1.0);

	vec4 o = gl_ModelViewProjectionMatrix * boneEffect * position;
	o.z -= DepthBias;
	gl_Position = o;
}
