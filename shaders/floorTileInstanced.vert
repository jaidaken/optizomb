#version 430 compatibility

// Shared quad vertex (per-vertex, divisor 0)
layout(location = 0) in vec2 aQuadPos;  // (0,0)/(1,0)/(1,1)/(0,1)

// Per-instance data (divisor 1)
layout(location = 4) in vec4 aTilePos;      // worldX, worldY, worldZ, flags
layout(location = 5) in vec4 aUVRect;       // u0, v0, u1, v1
layout(location = 6) in vec4 aSizeOffset;   // texW, texH, combinedOffsetX, combinedOffsetY
layout(location = 7) in vec4 aVertColors;   // col0(TL), col1(TR), col2(BR), col3(BL) as packed ABGR floats

// Uniforms
uniform mat4 uMVP;

uniform vec2 uCamOffset;      // globalOffsetX, globalOffsetY
uniform vec2 uTileScale;      // (32*TileScale, 16*TileScale)
uniform float uZScale;        // 96*TileScale

// Iso padding (uniform border)
uniform float uIsoPadBorder;
uniform float uIsoPadUV;

// De-diamond padding
uniform float uDeDiamondPadUp;
uniform float uDeDiamondPadDown;
uniform float uDeDiamondPadLR;
uniform float uDeDiamondPadUVFrac;

// Debug toggles
uniform bool uIsoPaddingEnabled;
uniform bool uDeDiamondPaddingEnabled;
uniform bool uDiamondMeshCutdown;

out vec4 vertColour;
out vec2 texCoords;

void main() {
    float worldX = aTilePos.x;
    float worldY = aTilePos.y;
    float worldZ = aTilePos.z;
    int flags = floatBitsToInt(aTilePos.w);
    bool isDiamond = (flags & 1) != 0;

    // Isometric projection to screen space
    float screenX = (worldX - worldY) * uTileScale.x + uCamOffset.x;
    float screenY = (worldX + worldY) * uTileScale.y - worldZ * uZScale + uCamOffset.y;

    // Apply combined offset (tex.offsetX + sprite.soffX - object.offsetX, etc.)
    screenX += aSizeOffset.z;
    screenY += aSizeOffset.w;

    // Quad bounds with floor/ceil snap (matches SpriteRenderer behavior)
    float x0 = floor(screenX);
    float y0 = floor(screenY);
    float x1 = ceil(screenX + aSizeOffset.x);
    float y1 = ceil(screenY + aSizeOffset.y);

    // UV corners from texture
    float u0 = aUVRect.x, v0 = aUVRect.y;
    float u1 = aUVRect.z, v1 = aUVRect.w;

    // Apply iso padding (uniform border expansion)
    if (uIsoPaddingEnabled) {
        float quadW = x1 - x0;
        float quadH = y1 - y0;
        if (quadW > 0.0 && quadH > 0.0) {
            float uRange = u1 - u0;
            float vRange = v1 - v0;
            float uvPadU = uRange * uIsoPadBorder / quadW;
            float uvPadV = vRange * uIsoPadBorder / quadH;
            float actualUVPadU = uIsoPadUV * uvPadU;
            float actualUVPadV = uIsoPadUV * uvPadV;

            x0 -= uIsoPadBorder;
            y0 -= uIsoPadBorder;
            x1 += uIsoPadBorder;
            y1 += uIsoPadBorder;
            u0 -= actualUVPadU;
            v0 -= actualUVPadV;
            u1 += actualUVPadU;
            v1 += actualUVPadV;
        }
    }

    // De-diamond padding (different amounts per side, only for non-diamond tiles)
    if (!isDiamond && uDeDiamondPaddingEnabled) {
        float quadW = x1 - x0;
        float quadH = y1 - y0;
        if (quadW > 0.0 && quadH > 0.0) {
            float uRange = u1 - u0;
            float vRange = v1 - v0;
            float uvPadLR = uRange * uDeDiamondPadLR / quadW;
            float uvPadUp = vRange * uDeDiamondPadUp / quadH;
            float uvPadDown = vRange * uDeDiamondPadDown / quadH;
            float actualUVPadLR = uDeDiamondPadUVFrac * uvPadLR;
            float actualUVPadUp = uDeDiamondPadUVFrac * uvPadUp;
            float actualUVPadDown = uDeDiamondPadUVFrac * uvPadDown;

            x0 -= uDeDiamondPadLR;
            y0 -= uDeDiamondPadUp;
            x1 += uDeDiamondPadLR;
            y1 += uDeDiamondPadDown;
            u0 -= actualUVPadLR;
            v0 -= actualUVPadUp;
            u1 += actualUVPadLR;
            v1 += actualUVPadDown;
        }
    }

    // Compute per-vertex position and UV from unit quad
    vec2 pos;
    pos.x = mix(x0, x1, aQuadPos.x);
    pos.y = mix(y0, y1, aQuadPos.y);
    vec2 uv;
    uv.x = mix(u0, u1, aQuadPos.x);
    uv.y = mix(v0, v1, aQuadPos.y);

    // Diamond transform (midpoint vertices and UVs)
    if (isDiamond && uDiamondMeshCutdown) {
        float midX = (x0 + x1) * 0.5;
        float midY = (y0 + y1) * 0.5;
        float midU = (u0 + u1) * 0.5;
        float midV = (v0 + v1) * 0.5;
        // TL(0,0)->top-center, TR(1,0)->right-center, BR(1,1)->bottom-center, BL(0,1)->left-center
        if (aQuadPos.x < 0.5 && aQuadPos.y < 0.5)      { pos = vec2(midX, y0); uv = vec2(midU, v0); }
        else if (aQuadPos.x > 0.5 && aQuadPos.y < 0.5)  { pos = vec2(x1, midY); uv = vec2(u1, midV); }
        else if (aQuadPos.x > 0.5 && aQuadPos.y > 0.5)  { pos = vec2(midX, y1); uv = vec2(midU, v1); }
        else                                              { pos = vec2(x0, midY); uv = vec2(u0, midV); }
    }

    gl_Position = uMVP * vec4(pos, 0.0, 1.0);
    texCoords = uv;

    // Select per-vertex color based on quad corner and unpack from packed ABGR float
    int vertIdx = int(aQuadPos.x + 0.5) + int(aQuadPos.y + 0.5) * 2;
    float packedColor;
    if (vertIdx == 0)      packedColor = aVertColors.x;  // TL
    else if (vertIdx == 1) packedColor = aVertColors.y;  // TR
    else if (vertIdx == 2) packedColor = aVertColors.w;  // BL
    else                   packedColor = aVertColors.z;  // BR

    int bits = floatBitsToInt(packedColor);
    vertColour = vec4(
        float(bits & 0xFF) / 255.0,
        float((bits >> 8) & 0xFF) / 255.0,
        float((bits >> 16) & 0xFF) / 255.0,
        float((bits >> 24) & 0xFF) / 255.0
    );
}
