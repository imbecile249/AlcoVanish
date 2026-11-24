package local.alcovanish.mixin;

import local.alcovanish.BrokenContainerCounter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
abstract class ContainerBreakMixin {
	@Redirect(method = "destroyBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean alcovanish$watchContainerBreak(Level level, BlockPos pos, BlockState state, int flags) {
		var blockEntity = level.getBlockEntity(pos);
		boolean container = blockEntity instanceof Container || blockEntity instanceof EnderChestBlockEntity
				|| blockEntity instanceof LecternBlockEntity;
		var oldBlock = container ? level.getBlockState(pos).getBlock() : null;
		boolean removed = level.setBlock(pos, state, flags);
		if (removed && oldBlock != null && level instanceof ClientLevel clientLevel) {
			BrokenContainerCounter.expectBreak(clientLevel, pos, oldBlock);
		}
		return removed;
	}
}
