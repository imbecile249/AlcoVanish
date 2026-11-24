package local.alcovanish.mixin;

import local.alcovanish.ExtraFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class AutoToolMixin {
	@Shadow private int destroyDelay;

	@Shadow
	private void ensureHasSentCarriedItem() {
		throw new AssertionError();
	}

	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void alcovanish$chooseTool(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> result) {
		alcovanish$selectAndSendTool(pos);
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"))
	private void alcovanish$chooseToolDuringCooldown(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> result) {
		// Select the next tool even while vanilla is waiting to start breaking the next block.
		if (destroyDelay > 0) alcovanish$selectAndSendTool(pos);
	}

	@Unique
	private void alcovanish$selectAndSendTool(BlockPos pos) {
		if (ExtraFeatures.selectTool(Minecraft.getInstance(), pos)) ensureHasSentCarriedItem();
	}
}
