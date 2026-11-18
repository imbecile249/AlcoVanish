package local.alcovanish.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class OutlinePostChainMixin {
	@ModifyArg(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"),
			index = 0)
	private Identifier alcovanish$useDepthAwareOutline(Identifier id) {
		if (!id.equals(Identifier.withDefaultNamespace("entity_outline"))) return id;
		return Identifier.fromNamespaceAndPath("alcovanish", "entity_outline");
	}
}
