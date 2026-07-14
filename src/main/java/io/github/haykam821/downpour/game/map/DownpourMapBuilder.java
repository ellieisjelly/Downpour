package io.github.haykam821.downpour.game.map;

import java.util.Iterator;

import io.github.haykam821.downpour.game.DownpourConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public class DownpourMapBuilder {
	private static final BlockState FLOOR = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState FLOOR_OUTLINE = Blocks.DARK_OAK_PLANKS.defaultBlockState();
	private static final BlockState WALL = Blocks.ANDESITE_WALL.defaultBlockState();
	private static final BlockState WALL_TOP = Blocks.SPRUCE_SLAB.defaultBlockState();
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

	private final DownpourConfig config;

	public DownpourMapBuilder(DownpourConfig config) {
		this.config = config;
	}

	public DownpourMap create() {
		MapTemplate template = MapTemplate.createEmpty();
		DownpourMapConfig mapConfig = this.config.getMapConfig();

		// Must be a biome that allows for rain
		template.setBiome(Biomes.PLAINS);

		BlockBounds bounds = BlockBounds.of(BlockPos.ZERO, new BlockPos(mapConfig.getX() + 1, 4, mapConfig.getZ() + 1));
		this.build(bounds, template, mapConfig);

		BlockBounds shelterBounds = BlockBounds.of(new BlockPos(5, 1, 5), new BlockPos(mapConfig.getX() - 4, 1, mapConfig.getZ() - 4));
		return new DownpourMap(template, bounds, shelterBounds);
	}

	private BlockState getBlockState(BlockPos pos, BlockBounds bounds, DownpourMapConfig mapConfig) {
		int layer = pos.getY() - bounds.min().getY();
		boolean outline = pos.getX() == bounds.min().getX() || pos.getX() == bounds.max().getX() || pos.getZ() == bounds.min().getZ() || pos.getZ() == bounds.max().getZ();

		if (outline) {
			if (layer == 0) {
				return FLOOR_OUTLINE;
			} else if (layer == 1) {
				return WALL;
			} else if (layer == 2) {
				return WALL_TOP;
			} else if (layer >= 3) {
				return BARRIER;
			}
		} else if (layer == 0) {
			return FLOOR;
		}

		return null;
	}

	public void build(BlockBounds bounds, MapTemplate template, DownpourMapConfig mapConfig) {
		Iterator<BlockPos> iterator = bounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();

			BlockState state = this.getBlockState(pos, bounds, mapConfig);
			if (state != null) {
				template.setBlockState(pos, state);
			}
		}
	}
}