package local.alcovanish.mixin;

import local.alcovanish.SignAutomation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fills a sign as soon as the server authorizes editing it, without displaying the editor. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleOpenSignEditor", at = @At("HEAD"), cancellable = true)
	private void alcovanish$submitSignText(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
		if (!SignAutomation.onOpenSignEditor(packet.getPos(), packet.isFrontText())) return;
		ci.cancel();
	}
}

