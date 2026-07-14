package io.github.haykam821.downpour.game;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;

public class Shelter {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FLOOR = Blocks.DYED_TERRACOTTA.red().defaultBlockState();
	private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();
	private static final BlockState GLASS = Blocks.STAINED_GLASS.red().defaultBlockState();

	private final BoundingBox box;
	private final BoundingBox outerBox;
	private boolean locked;

	public Shelter(BlockPos pos, int size, boolean locked) {
		int upperRadius = (int) Math.ceil(size / (float) 2);
		int lowerRadius = (int) Math.floor(size / (float) 2);
		this.box = new BoundingBox(pos.getX() - lowerRadius, pos.getY(), pos.getZ() - lowerRadius, pos.getX() + upperRadius, pos.getY() + 6, pos.getZ() + upperRadius);
		this.outerBox = new BoundingBox(this.box.minX() - 1, this.box.minY(), this.box.minZ() - 1, this.box.maxX() + 1, this.box.maxY(), this.box.maxZ() + 1);

		this.locked = locked;
	}

	public BoundingBox getBox() {
		return this.box;
	}

	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	private Iterable<BlockPos> iterateOuter() {
		return BlockPos.betweenClosed(this.outerBox.minX(), this.outerBox.minY(), this.outerBox.minZ(), this.outerBox.maxX(), this.outerBox.maxY(), this.outerBox.maxZ());
	}

	public void build(ServerLevel level) {
		for (BlockPos pos : this.iterateOuter()) {
			if (!this.box.isInside(pos)) {
				if (this.locked && pos.getY() != this.box.maxY()) {
					level.setBlockAndUpdate(pos, GLASS);
				}
			} else if (pos.getY() == this.box.minY()) {
				level.setBlockAndUpdate(pos, FLOOR);
			} else if (pos.getY() == this.box.maxY()) {
				level.setBlockAndUpdate(pos, ROOF);
			}
		}
	}

	public void clear(ServerLevel level) {
		for (BlockPos pos : this.iterateOuter()) {
			level.setBlockAndUpdate(pos, AIR);
		}
	}
}
