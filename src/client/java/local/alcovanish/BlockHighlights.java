package local.alcovanish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client-only outline rendering and cache for matching blocks in chunks already loaded by the client. */
public final class BlockHighlights {
	private static final Set<Block> MECHANISM_BLOCKS = Set.of(
			Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REDSTONE_BLOCK,
			Blocks.REPEATER, Blocks.COMPARATOR, Blocks.LEVER, Blocks.PISTON, Blocks.STICKY_PISTON,
			Blocks.PISTON_HEAD, Blocks.OBSERVER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
			Blocks.REDSTONE_LAMP, Blocks.DAYLIGHT_DETECTOR, Blocks.TARGET, Blocks.TRIPWIRE,
			Blocks.TRIPWIRE_HOOK, Blocks.NOTE_BLOCK, Blocks.TNT, Blocks.POWERED_RAIL,
			Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL, Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR
	);
	private static final Set<Block> COAL_ORES = Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
	private static final Set<Block> IRON_ORES = Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
	private static final Set<Block> COPPER_ORES = Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
	private static final Set<Block> GOLD_ORES = Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
	private static final Set<Block> REDSTONE_ORES = Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
	private static final Set<Block> DIAMOND_ORES = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
	private static final Set<Block> QUARTZ_ORES = Set.of(Blocks.NETHER_QUARTZ_ORE);
	private static final Set<Block> EMERALD_ORES = Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
	private static final Set<Block> LAPIS_ORES = Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
	private static final Set<Block> DEBRIS = Set.of(Blocks.ANCIENT_DEBRIS);
	private static final Set<Block> ORES = OreHighlightMode.families().stream()
			.flatMap(mode -> oreBlocks(mode).stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
	private record Highlight(BlockPos pos, int color, boolean updated) { }
	private static final Map<LevelChunk, Set<Long>> updatedOres = new HashMap<>();
	private static ClientLevel updateLevel;

	private static List<Highlight> positions = List.of();
	private static Object lastLevel;
	private static int lastScanTick = Integer.MIN_VALUE;
	private static boolean lastChests, lastRedstone;
	private static Set<OreHighlightMode> lastOreModes = Set.of();

	private BlockHighlights() { }

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(BlockHighlights::submitOutlines);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> updatedOres.remove(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> updatedOres.remove(chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			updatedOres.clear();
			updateLevel = null;
			invalidate();
		});
	}

	public static void recordBlockUpdate(ClientLevel level, BlockPos pos, BlockState state) {
		if (updateLevel != level) {
			updatedOres.clear();
			updateLevel = level;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
		if (chunk == null || chunk.getBlockState(pos) != state) return;
		if (ORES.contains(state.getBlock())) {
			updatedOres.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos.asLong());
		} else {
			var previous = updatedOres.get(chunk);
			if (previous != null) {
				previous.remove(pos.asLong());
				if (previous.isEmpty()) updatedOres.remove(chunk);
			}
		}
	}

	public static void invalidate() {
		lastScanTick = Integer.MIN_VALUE;
		positions = List.of();
	}

	private static boolean isRedstone(BlockState state) {
		return MECHANISM_BLOCKS.contains(state.getBlock()) || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES);
	}

	private static boolean isChest(BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST);
	}

	private static Set<Block> oreBlocks(OreHighlightMode mode) {
		if (mode == null) return Set.of();
		return switch (mode) {
			case ALL, OFF -> Set.of();
			case COAL -> COAL_ORES;
			case IRON -> IRON_ORES;
			case COPPER -> COPPER_ORES;
			case GOLD -> GOLD_ORES;
			case REDSTONE -> REDSTONE_ORES;
			case DIAMOND -> DIAMOND_ORES;
			case ANCIENT_DEBRIS -> DEBRIS;
			case QUARTZ -> QUARTZ_ORES;
			case EMERALD -> EMERALD_ORES;
			case LAPIS -> LAPIS_ORES;

		};
	}

	private static void submitOutlines(LevelRenderContext context) {
		var config = NearbyWorkshopClient.CONFIG;
		if (!config.blockHighlightEnabled) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		if (lastLevel != client.level || lastChests != config.highlightChests || lastRedstone != config.highlightRedstone
				|| !lastOreModes.equals(config.oreHighlightModes)
				|| client.level.getGameTime() - lastScanTick >= 10) {
			scanLoadedChunks(client);
		}
		if (positions.isEmpty()) return;

		var camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null) return;
		PoseStack poseStack = context.poseStack();
		for (Highlight target : positions) {
			BlockPos pos = target.pos();
			double distance = Math.sqrt(camera.pos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			float lineWidth = (float) Math.clamp(36.0 / Math.max(1.0, distance), 1.0, 6.0);
			poseStack.pushPose();
			poseStack.translate(pos.getX() - camera.pos.x, pos.getY() - camera.pos.y, pos.getZ() - camera.pos.z);
			if (target.updated()) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, XrayLineRenderType.TYPE,
						(pose, out) -> drawDashedBox(pose, out, target.color(), lineWidth));
			} else {
				context.submitNodeCollector().submitShapeOutline(poseStack, Shapes.block(), XrayLineRenderType.TYPE,
						target.color(), lineWidth, true);
			}
			poseStack.popPose();
		}
	}

	private static void drawDashedBox(PoseStack.Pose pose, VertexConsumer out, int color, float width) {
		for (int a = 0; a <= 1; a++) {
			for (int b = 0; b <= 1; b++) {
				dashedEdge(pose, out, 0, a, b, 1, 0, 0, color, width);
				dashedEdge(pose, out, a, 0, b, 0, 1, 0, color, width);
				dashedEdge(pose, out, a, b, 0, 0, 0, 1, color, width);
			}
		}
	}

	private static void dashedEdge(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
			float dx, float dy, float dz, int color, float width) {
		for (int i = 0; i < 2; i++) {
			float start = i * 0.6f;
			float end = start + 0.4f;
			out.addVertex(pose, x + dx * start, y + dy * start, z + dz * start)
					.setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
			out.addVertex(pose, x + dx * end, y + dy * end, z + dz * end)
					.setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
		}
	}

	private static void scanLoadedChunks(Minecraft client) {
		var level = client.level;
		if (level == null || client.player == null) return;
		var config = NearbyWorkshopClient.CONFIG;
		boolean chests = config.highlightChests;
		boolean redstone = config.highlightRedstone;
		Set<OreHighlightMode> oreModes = Set.copyOf(NearbyWorkshopClient.CONFIG.oreHighlightModes);
		var oreColors = new java.util.HashMap<Block, Integer>();
		for (var mode : oreModes) {
			for (Block block : oreBlocks(mode)) oreColors.put(block, mode.color());
		}
		int centerX = client.player.chunkPosition().x();
		int centerZ = client.player.chunkPosition().z();
		int renderRadius = client.options.getEffectiveRenderDistance();
		int blockRange = NearbyWorkshopClient.CONFIG.blockHighlightRange;
		int oreRange = NearbyWorkshopClient.CONFIG.oreHighlightRange;
		boolean hasUnlimitedTarget = ((chests || redstone) && blockRange == 0)
				|| (!oreModes.isEmpty() && oreRange == 0);
		int radius = renderRadius;
		if (!hasUnlimitedTarget) radius = Math.min(renderRadius, (Math.max(blockRange, oreRange) + 15) / 16 + 1);
		List<Highlight> found = new ArrayList<>();
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
			for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				Set<Long> updates = updatedOres.getOrDefault(chunk, Set.of());
				LevelChunkSection[] sections = chunk.getSections();
				for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
					LevelChunkSection section = sections[sectionIndex];
					if (section == null || !section.maybeHas(state -> oreColors.containsKey(state.getBlock()) || (chests && isChest(state)) || (redstone && isRedstone(state)))) continue;
					int minY = (chunk.getMinSectionY() + sectionIndex) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState state = section.getBlockState(x, y, z);
								Integer oreColor = oreColors.get(state.getBlock());
								if (oreColor != null || (chests && isChest(state)) || (redstone && isRedstone(state))) {
									mutable.set((chunkX << 4) + x, minY + y, (chunkZ << 4) + z);

									int targetRange = oreColor != null ? oreRange : blockRange;
									if (targetRange == 0 || client.player.distanceToSqr(
											mutable.getX() + 0.5, mutable.getY() + 0.5, mutable.getZ() + 0.5)
										<= (double) targetRange * targetRange) {
										int color = oreColor != null ? oreColor : chests && isChest(state) ? 0xFFFF2F9F : 0xFFFF62DB;
										found.add(new Highlight(mutable.immutable(), color, oreColor != null && updates.contains(mutable.asLong())));
									}
								}
							}
						}
					}
				}
			}
		}
		positions = List.copyOf(found);
		lastLevel = level;
		lastChests = chests;
		lastRedstone = redstone;
		lastOreModes = oreModes;
		lastScanTick = (int) level.getGameTime();
	}
}
