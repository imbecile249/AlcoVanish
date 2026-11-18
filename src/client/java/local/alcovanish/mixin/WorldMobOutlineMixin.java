package local.alcovanish.mixin;

import local.alcovanish.MobHighlights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
abstract class WorldMobOutlineMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void alcovanish$outlineWorldMob(Entity entity, float partialTick,
			CallbackInfoReturnable<EntityRenderState> result) {
		if (entity instanceof Mob mob && MobHighlights.shouldOutline(mob, Minecraft.getInstance().player)) {
			result.getReturnValue().outlineColor = 0xFF000000 | MobHighlights.color(mob);
		}
	}
}
