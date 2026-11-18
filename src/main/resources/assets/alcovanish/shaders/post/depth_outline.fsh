#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
in vec2 texCoord;
out vec4 fragColor;

// These are the four player colors with the low blue bit flipped in the render state.
bool isPlayerColor(vec3 color) {
    ivec3 rgb = ivec3(round(color * 255.0));
    return all(equal(rgb, ivec3(255, 255, 254)))
        || all(equal(rgb, ivec3(255, 36, 37)))
        || all(equal(rgb, ivec3(143, 190, 156)))
        || all(equal(rgb, ivec3(198, 154, 242)));
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 center = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    vec4 surface = texelFetch(InSampler, center, 0);
    bool inside = surface.a > 0.0;
    float surfaceDepth = texelFetch(DepthSampler, center, 0).r;
    vec4 nearest = vec4(0.0);
    float nearestDepth = 0.0;
    for (int y = -3; y <= 3; ++y) {
        for (int x = -3; x <= 3; ++x) {
            int radiusSq = x * x + y * y;
            if (radiusSq > 9) continue;
            ivec2 samplePos = clamp(center + ivec2(x, y), ivec2(0), size - 1);
            vec4 color = texelFetch(InSampler, samplePos, 0);
            if (color.a <= 0.0 || (radiusSq > 4 && !isPlayerColor(color.rgb))) continue;
            float depth = texelFetch(DepthSampler, samplePos, 0).r;
            // At a shared edge only a closer silhouette may paint over the far one.
            if (inside && (depth <= surfaceDepth + 0.000001 || all(equal(color.rgb, surface.rgb)))) continue;
            if (depth >= nearestDepth) {
                nearest = color;
                nearestDepth = depth;
            }
        }
    }
    if (nearest.a > 0.0 && isPlayerColor(nearest.rgb)) {
        nearest.rgb = mix(nearest.rgb, vec3(1.0), 0.15);
    }
    fragColor = nearest;
}
