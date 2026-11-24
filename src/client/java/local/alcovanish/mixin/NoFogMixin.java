package local.alcovanish.mixin;

import local.alcovanish.NearbyWorkshopClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class NoFogMixin {
	@Redirect(method = "computeFogColor", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean alcovanish$ignoreDarknessInFogColor(LivingEntity entity, Holder<MobEffect> effect) {
		return !(NearbyWorkshopClient.CONFIG.noDarkness && effect == MobEffects.DARKNESS) && entity.hasEffect(effect);
	}

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void alcovanish$removeFog(Camera camera, int distance, DeltaTracker delta, float darken,
			ClientLevel level, CallbackInfoReturnable<FogData> result) {
		if (!NearbyWorkshopClient.CONFIG.noFog) return;
		FogData fog = result.getReturnValue();
		fog.environmentalStart = 1_000_000;
		fog.environmentalEnd = 1_000_001;
		fog.renderDistanceStart = 1_000_000;
		fog.renderDistanceEnd = 1_000_001;
		fog.skyEnd = 1_000_001;
		fog.cloudEnd = 1_000_001;
	}
}
