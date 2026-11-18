package local.alcovanish.mixin;

import local.alcovanish.NearbyWorkshopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class PlayerOutlineMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void alcovanish$outlineNearbyPlayer(Entity entity, CallbackInfoReturnable<Boolean> result) {
		var config = NearbyWorkshopClient.CONFIG;
		var localPlayer = Minecraft.getInstance().player;
		boolean enabled = entity instanceof Player && config.highlightEnabled;
		if (enabled && localPlayer != null
				&& entity != localPlayer && entity.distanceToSqr(localPlayer) <= config.highlightRadius * config.highlightRadius) {
			result.setReturnValue(true);
		}
	}
}
