package local.alcovanish.mixin;

import local.alcovanish.ExtraFeatures;
import local.alcovanish.SignAutomation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.world.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleOpenSignEditor", at = @At("HEAD"), cancellable = true)
	private void alcovanish$submitSignText(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
		if (!SignAutomation.onOpenSignEditor(packet.getPos(), packet.isFrontText())) return;
		ci.cancel();
	}

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void alcovanish$onTotemUsed(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.level != null && packet.getEntity(client.level) == client.player) {
			ExtraFeatures.onTotemUsed(client.player);
		}
	}
}

