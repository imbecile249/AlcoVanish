package local.alcovanish.mixin;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(RenderPipelines.class)
abstract class OutlineDepthMixin {
	@Redirect(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"),
			slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=pipeline/outline_cull"),
					to = @At(value = "CONSTANT", args = "stringValue=pipeline/lightmap")),
			require = 2, allow = 2)
	private static RenderPipeline alcovanish$depthTestOutlines(RenderPipeline.Builder builder) {
		// Minecraft clears the outline depth buffer to zero and uses reversed depth.
		return builder.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).build();
	}
}
