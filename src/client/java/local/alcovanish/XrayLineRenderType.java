package local.alcovanish;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import local.alcovanish.mixin.RenderPipelinesAccessor;
import local.alcovanish.mixin.RenderTypeInvoker;

/** Lines render without depth testing, so blocks remain outlined behind opaque walls. */
final class XrayLineRenderType {
	static final RenderType TYPE = create();

	private XrayLineRenderType() { }

	private static RenderType create() {
		RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccessor.alcovanish$getLinesSnippet())
				.withLocation(Identifier.fromNamespaceAndPath("alcovanish", "pipeline/xray_lines"))
				.withFragmentShader(Identifier.fromNamespaceAndPath("alcovanish", "core/xray_lines"))
				.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
				.build();
		RenderSetup setup = RenderSetup.builder(pipeline)
				.setLayeringTransform(net.minecraft.client.renderer.rendertype.LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
				.createRenderSetup();
		return RenderTypeInvoker.alcovanish$create("alcovanish_xray_lines", setup);
	}
}
