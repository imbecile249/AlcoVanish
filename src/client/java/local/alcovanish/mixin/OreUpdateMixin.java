package local.alcovanish.mixin;

import local.alcovanish.BlockHighlights;
import local.alcovanish.BrokenContainerCounter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class OreUpdateMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("RETURN"))
	private void alcovanish$trackOreUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo callback) {
		ClientLevel level = (ClientLevel) (Object) this;
		BlockHighlights.recordBlockUpdate(level, pos, state);
		BrokenContainerCounter.confirmBlockUpdate(level, pos, state);
	}
}
