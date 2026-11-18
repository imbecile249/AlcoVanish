package local.alcovanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Sign workflow modeled on the attached mod: find an interactable sign, then submit text without opening its editor. */
public final class SignAutomation {
	private record SignKey(BlockPos pos, boolean front) { }

	private static final Map<SignKey, Integer> ASSIGNED = new HashMap<>();
	private static final Map<SignKey, Integer> ATTEMPTS = new HashMap<>();
	private static final Set<SignKey> FAILED = new HashSet<>();
	private static final Set<SignKey> DONE = new HashSet<>();
	private static final Random RANDOM = new Random();
	private static SignKey pending;

	private static BlockHitResult nextHit;
	private static int ticks;
	private static int deadline;
	private static long waitUntil;
	private static long warnedAt;

	private SignAutomation() { }

	static void reset() {
		ASSIGNED.clear();
		ATTEMPTS.clear();
		FAILED.clear();
		DONE.clear();
		pending = null;

		nextHit = null;
		waitUntil = 0;
	}

	static void tick(Minecraft client) {
		var player = client.player;
		var config = NearbyWorkshopClient.CONFIG;
		if (!config.signEnabled || player == null || client.level == null || client.gameMode == null
				|| config.signTemplates == null || config.signTemplates.length == 0) {
			pending = null;

			return;
		}

		ticks++;
		if (pending != null) {
			if (ticks > deadline) {
				fail(pending);
				pending = null;
				delay();
			}
			return;
		}
		if (System.nanoTime() < waitUntil || client.gui.screen() != null || player.isShiftKeyDown()) return;
		if (ticks % 4 != 0) return;
		if (player.getMainHandItem().getItem() instanceof SignApplicator) {
			if (System.currentTimeMillis() - warnedAt > 3000) {
				warnedAt = System.currentTimeMillis();
				player.sendOverlayMessage(Component.literal("Убери табличку из основной руки, чтобы режим заполнения работал."));
			}
			return;
		}

		SignKey target = findTarget(client, player);
		if (target == null) return;
		nextHit = hitFor(player, target.pos());
		interact(player, target);
	}

	public static boolean onOpenSignEditor(BlockPos pos, boolean front) {
		SignKey current = pending;
		if (current == null || !current.pos().equals(pos)) return false;
		pending = null;
		SignKey key = new SignKey(pos.immutable(), front);
		Integer index = ASSIGNED.get(key);
		if (index == null) index = ASSIGNED.get(current);
		String[][] templates = NearbyWorkshopClient.CONFIG.signTemplates;
		if (index == null || index < 0 || index >= templates.length) {
			var client = Minecraft.getInstance();
			var sign = client.level == null ? null : client.level.getBlockEntity(pos);
			int width = sign instanceof SignBlockEntity signBlock ? signBlock.getMaxTextLineWidth() : Integer.MAX_VALUE;
			index = chooseTemplate(client, pos, width);
		}
		if (index < 0 || index >= templates.length) {
			fail(current);
			delay();
			return true;
		}
		ASSIGNED.put(key, index);
		ASSIGNED.put(current, index);
		String[] lines = normalizedLines(templates[index]);
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.getConnection().send(new ServerboundSignUpdatePacket(pos, front, clean(lines[0]), clean(lines[1]),
					clean(lines[2]), clean(lines[3])));
		}
		fail(key);
		delay();
		return true;
	}

	private static SignKey findTarget(Minecraft client, Player player) {
		var config = NearbyWorkshopClient.CONFIG;
		int radius = (int) Math.ceil(player.blockInteractionRange()) + 1;
		BlockPos eyeBlock = BlockPos.containing(player.getEyePosition());
		SignKey closest = null;
		int closestWidth = 0;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(eyeBlock.offset(-radius, -radius, -radius),
				eyeBlock.offset(radius, radius, radius))) {
			if (!(client.level.getBlockEntity(pos) instanceof SignBlockEntity sign) || sign.isWaxed()
					|| !player.isWithinBlockInteractionRange(pos, 0.0)) continue;

			boolean front = sign.isFacingFrontText(player);
			SignKey key = new SignKey(pos.immutable(), front);
			if (FAILED.contains(key) || DONE.contains(key)) continue;
			var text = sign.getText(front);
			if (text.hasAnyClickCommands(player)) continue;

			Integer assigned = ASSIGNED.get(key);
			if (assigned != null && (assigned < 0 || assigned >= config.signTemplates.length
					|| !fits(config.signTemplates[assigned], sign.getMaxTextLineWidth()))) {
				ASSIGNED.remove(key);
				assigned = null;
			}
			if (assigned == null && !hasFittingTemplate(sign.getMaxTextLineWidth())) continue;

			if (assigned != null && matches(text, config.signTemplates[assigned])) {
				ATTEMPTS.remove(key);
				if (!config.signReapply) DONE.add(key);
				continue;
			}

			double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
			if (distance < bestDistance) {
				bestDistance = distance;
				closest = key;
				closestWidth = sign.getMaxTextLineWidth();
			}
		}
		if (closest != null && !ASSIGNED.containsKey(closest)) {
			int assigned = chooseTemplate(client, closest.pos(), closestWidth);
			if (assigned < 0) return null;
			ASSIGNED.put(closest, assigned);
		}
		return closest;
	}

	private static boolean hasFittingTemplate(int maxWidth) {
		String[][] templates = NearbyWorkshopClient.CONFIG.signTemplates;
		for (String[] template : templates) {
			if (fits(template, maxWidth)) return true;
		}
		return false;
	}

	private static int chooseTemplate(Minecraft client, BlockPos target, int maxWidth) {
		String[][] templates = NearbyWorkshopClient.CONFIG.signTemplates;
		if (templates == null || templates.length == 0) return -1;
		var nearbyTexts = new java.util.ArrayList<net.minecraft.world.level.block.entity.SignText>();
		if (client.level != null) {
			for (BlockPos pos : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 3, 3))) {
				if (!pos.equals(target) && pos.distManhattan(target) <= 3
						&& client.level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
					nearbyTexts.add(sign.getText(true));
					nearbyTexts.add(sign.getText(false));
				}
			}
		}
		var fitting = new java.util.ArrayList<Integer>();
		var different = new java.util.ArrayList<Integer>();
		for (int index = 0; index < templates.length; index++) {
			if (!fits(templates[index], maxWidth)) continue;
			fitting.add(index);
			String[] lines = templates[index];
			if (nearbyTexts.stream().noneMatch(text -> matches(text, lines))) different.add(index);
		}
		var choices = different.isEmpty() ? fitting : different;
		return choices.isEmpty() ? -1 : choices.get(RANDOM.nextInt(choices.size()));
	}

	private static boolean fits(String[] lines, int width) {
		boolean hasText = false;
		for (String line : normalizedLines(lines)) {
			String value = clean(line);
			if (!value.isBlank()) hasText = true;
			if (Minecraft.getInstance().font.width(value) > width) return false;
		}
		return hasText;
	}

	private static boolean matches(net.minecraft.world.level.block.entity.SignText text, String[] lines) {
		String[] normalized = normalizedLines(lines);
		for (int i = 0; i < 4; i++) {
			if (!text.getMessage(i, false).getString().equals(clean(normalized[i]))) return false;
		}
		return true;
	}

	private static String[] normalizedLines(String[] lines) {
		String[] result = new String[4];
		for (int i = 0; i < 4; i++) result[i] = lines != null && i < lines.length && lines[i] != null ? lines[i] : "";
		return result;
	}

	private static String clean(String text) {
		String clean = net.minecraft.ChatFormatting.stripFormatting(text == null ? "" : text);
		return clean == null ? "" : clean;
	}

	private static void interact(LocalPlayer player, SignKey key) {
		pending = key;
		deadline = ticks + NearbyWorkshopClient.CONFIG.signOpenTimeoutTicks;
		if (nextHit == null || !nextHit.getBlockPos().equals(key.pos())) {
			nextHit = hitFor(player, key.pos());
		}
		InteractionResult result = Minecraft.getInstance().gameMode.useItemOn(player, InteractionHand.MAIN_HAND, nextHit);
		if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
	}

	private static BlockHitResult hitFor(Player player, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 delta = player.getEyePosition().subtract(center);
		double ax = Math.abs(delta.x), ay = Math.abs(delta.y), az = Math.abs(delta.z);
		Direction side;
		if (ay >= ax && ay >= az) side = delta.y > 0 ? Direction.UP : Direction.DOWN;
		else if (ax >= az) side = delta.x > 0 ? Direction.EAST : Direction.WEST;
		else side = delta.z > 0 ? Direction.SOUTH : Direction.NORTH;
		Vec3 hit = center.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
		return new BlockHitResult(hit, side, pos, false);
	}

	private static void fail(SignKey key) {
		int attempts = ATTEMPTS.merge(key, 1, Integer::sum);
		if (attempts >= NearbyWorkshopClient.CONFIG.signMaxAttempts) FAILED.add(key);
	}

	private static void delay() {
		var config = NearbyWorkshopClient.CONFIG;
		int min = config.signDelayMinMs;
		int max = Math.max(min, config.signDelayMaxMs);
		int millis = max <= min ? min : RANDOM.nextInt(min, max + 1);
		waitUntil = System.nanoTime() + millis * 1_000_000L;
	}
}

