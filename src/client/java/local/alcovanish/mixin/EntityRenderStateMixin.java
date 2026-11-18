package local.alcovanish.mixin;

import local.alcovanish.OverheadHealthState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
abstract class EntityRenderStateMixin implements OverheadHealthState {
	@Unique private Display alcovanish$healthDisplay;

	@Override
	public Display alcovanish$getHealthDisplay() {
		return alcovanish$healthDisplay;
	}

	@Override
	public void alcovanish$setHealthDisplay(Display display) {
		alcovanish$healthDisplay = display;
	}
}
