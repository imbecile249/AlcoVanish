package local.alcovanish.mixin;

import local.alcovanish.NearbyWorkshopClient;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
abstract class MobEffectFogMixin {
	@Shadow public abstract Holder<MobEffect> getMobEffect();

	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void alcovanish$ignoreDarknessFog(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> result) {
		if (NearbyWorkshopClient.CONFIG.noDarkness && getMobEffect() == MobEffects.DARKNESS) {
			result.setReturnValue(false);
		}
	}
}
