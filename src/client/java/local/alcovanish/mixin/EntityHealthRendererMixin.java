package local.alcovanish.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import local.alcovanish.NearbyWorkshopClient;
import local.alcovanish.NearbyWorkshopClient.Config.HealthMode;
import local.alcovanish.OverheadHealthState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(EntityRenderer.class)
abstract class EntityHealthRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void alcovanish$extractHealth(Entity entity, EntityRenderState state, float partialTick, CallbackInfo callback) {
		var healthState = (OverheadHealthState) state;
		healthState.alcovanish$setHealthDisplay(null);
		var client = Minecraft.getInstance();
		var config = NearbyWorkshopClient.CONFIG;
		boolean highlighted = config.highlightEnabled && client.player != null && entity instanceof Player
				&& entity != client.player && entity.distanceToSqr(client.player) <= config.highlightRadius * config.highlightRadius;
		if (highlighted && entity instanceof Player player) {
			// The outline shader recognizes the player palette with its low blue bit flipped.
			state.outlineColor = config.playerOutlineColor(player.getGameProfile().name(), 0xFFFFFFFF) ^ 0x00000001;
		}
		if (client.player == null || !(entity instanceof LivingEntity living) || living == client.player
				|| !living.isAlive() || living.isInvisibleTo(client.player)
				|| (!highlighted && state.distanceToCameraSq > 64 * 64)) return;
		HealthMode mode = living instanceof Player ? config.playerHealthMode
				: living instanceof Mob ? config.mobHealthMode : HealthMode.OFF;
		if (mode == HealthMode.OVERHEAD) {
			healthState.alcovanish$setHealthDisplay(OverheadHealthState.Display.create(
					living.getHealth(), living.getMaxHealth(), living instanceof Player, config.healthHudHearts, highlighted));
		}
	}

	@Inject(method = "submit", at = @At("TAIL"))
	private void alcovanish$submitHealth(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo callback) {
		var display = ((OverheadHealthState) state).alcovanish$getHealthDisplay();
		if (display == null) return;
		var config = NearbyWorkshopClient.CONFIG;
		var anchor = state.nameTagAttachment;
		double baseHeight = state.boundingBoxHeight;
		if (anchor != null && state.nameTag != null) baseHeight = Math.max(baseHeight, anchor.y);
		var style = display.player() ? config.playerOverheadStyle : config.mobOverheadStyle;
		pose.pushPose();
		try {
			pose.translate(0, baseHeight + style.height / 100.0, 0);
			pose.mulPose(camera.orientation);
			float scale = 0.025f * style.scale / 100f;
			pose.scale(scale, -scale, scale);
			Font font = Minecraft.getInstance().font;
			Font.DisplayMode textMode = display.seeThrough() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
			// Packed light with both sky and block light at maximum.
			int light = 0x00F000F0;
			pose.pushPose();
			pose.translate(0, -22, 0);
			float numberScale = OverheadHealthState.NUMBER_SCALE;
			pose.scale(numberScale, numberScale, numberScale);
			collector.submitText(pose, -font.width(display.numbers()) / 2f, 0,
					display.numbers().getVisualOrderText(), false, textMode, light,
					0xFFFFF0FF, 0, 0xFF201028);
			pose.popPose();
			if (display.indicator() != null) {
				collector.submitText(pose, -font.width(display.indicator()) / 2f, -9,
					display.indicator().getVisualOrderText(), false, textMode, light,
					0xFFFFFFFF, 0, 0xFF201028);
			} else {
				float left = -OverheadHealthState.Display.BAR_WIDTH / 2f;
				float right = left + OverheadHealthState.Display.BAR_WIDTH;
				float fill = left + OverheadHealthState.Display.BAR_WIDTH * display.fraction();
				var barType = display.seeThrough() ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.debugQuads();
				collector.submitCustomGeometry(pose, barType, (matrix, out) -> {
					alcovanish$barQuad(matrix, out, left, fill, OverheadHealthState.Display.BAR_FILL);
					alcovanish$barQuad(matrix, out, fill, right, OverheadHealthState.Display.BAR_TRACK);
				});
			}
		} finally {
			pose.popPose();
		}
	}

	private static void alcovanish$barQuad(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer out,
			float left, float right, int color) {
		if (left >= right) return;
		out.addVertex(pose, left, -7, 0).setColor(color);
		out.addVertex(pose, left, -4, 0).setColor(color);
		out.addVertex(pose, right, -4, 0).setColor(color);
		out.addVertex(pose, right, -7, 0).setColor(color);
	}
}
