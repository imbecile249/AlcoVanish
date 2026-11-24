package local.alcovanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class ExtraFeatures {
	private static final int TOTEM_REFILL_WINDOW_TICKS = 20;
	private static LocalPlayer totemRefillPlayer;
	private static int totemRefillTicks;

	private ExtraFeatures() { }

	public static boolean selectTool(Minecraft client, BlockPos pos) {
		if (!NearbyWorkshopClient.CONFIG.autoTool || client.player == null || client.level == null
				|| client.player.isCreative() || client.player.isSpectator()) return false;
		var state = client.level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(client.level, pos) < 0) return false;
		var inventory = client.player.getInventory();
		int selected = inventory.getSelectedSlot();
		int bestSlot = selected;
		var current = inventory.getSelectedItem();
		float bestSpeed = current.getDestroySpeed(state);
		boolean bestHarvest = !state.requiresCorrectToolForDrops() || current.isCorrectToolForDrops(state);
		for (int slot = 0; slot < 9; slot++) {
			var stack = inventory.getItem(slot);
			if (stack.isEmpty()) continue;
			float speed = stack.getDestroySpeed(state);
			boolean harvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
			if ((harvest && !bestHarvest) || (harvest == bestHarvest && speed > bestSpeed)) {
				bestSlot = slot;
				bestSpeed = speed;
				bestHarvest = harvest;
			}
		}
		if (bestSlot == selected) return false;
		inventory.setSelectedSlot(bestSlot);
		return true;
	}

	public static void onTotemUsed(LocalPlayer player) {
		if (!NearbyWorkshopClient.CONFIG.autoTotem) return;
		totemRefillPlayer = player;
		totemRefillTicks = TOTEM_REFILL_WINDOW_TICKS;
	}

	static void tickTotem(Minecraft client) {
		if (totemRefillPlayer == null) return;
		var player = client.player;
		if (!NearbyWorkshopClient.CONFIG.autoTotem || player != totemRefillPlayer
				|| !player.isAlive() || player.isSpectator() || totemRefillTicks-- <= 0) {
			totemRefillPlayer = null;
			return;
		}
		// The inventory update that removes the consumed totem can arrive after the event.
		if (client.gameMode == null || client.gui.screen() != null
				|| player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
				|| player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
		totemRefillPlayer = null;
		var inventory = player.getInventory();
		for (int index = 0; index < 36; index++) {
			if (!inventory.getItem(index).is(Items.TOTEM_OF_UNDYING)) continue;
			var slot = player.inventoryMenu.findSlot(inventory, index);
			if (slot.isEmpty()) continue;
			client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot.getAsInt(),
					Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
			return;
		}
	}
}
