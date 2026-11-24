package local.alcovanish.mixin;

import local.alcovanish.NearbyWorkshopClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	@Shadow private boolean needsUpdate;
	@Unique private boolean alcovanish$lastNightVision;
	@Unique private boolean alcovanish$lastNoDarkness;

	@Inject(method = "extract", at = @At("HEAD"))
	private void alcovanish$refreshLightmapOnToggle(LightmapRenderState state, float partialTick, CallbackInfo callback) {
		var config = NearbyWorkshopClient.CONFIG;
		// A lightmap cache can skip ticks even though the mod's settings have changed.
		if (config.nightVision != alcovanish$lastNightVision || config.noDarkness != alcovanish$lastNoDarkness) {
			needsUpdate = true;
		}
	}

	@Redirect(method = "extract", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float alcovanish$darknessBlend(LocalPlayer player, Holder<MobEffect> effect, float partialTick) {
		if (NearbyWorkshopClient.CONFIG.noDarkness && effect == MobEffects.DARKNESS) return 0.0f;
		return player.getEffectBlendFactor(effect, partialTick);
	}

	@Inject(method = "extract", at = @At("TAIL"))
	private void alcovanish$enableNightVision(LightmapRenderState state, float partialTick, CallbackInfo callback) {
		var config = NearbyWorkshopClient.CONFIG;
		alcovanish$lastNightVision = config.nightVision;
		alcovanish$lastNoDarkness = config.noDarkness;
		if (config.nightVision) {
			state.nightVisionEffectIntensity = 1.0f;
		}
	}
}
