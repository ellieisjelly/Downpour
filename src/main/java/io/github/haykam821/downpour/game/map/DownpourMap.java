package io.github.haykam821.downpour.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.level.generator.TemplateChunkGenerator;

public class DownpourMap {
	private final MapTemplate template;
	private final BlockBounds bounds;
	private final BlockBounds shelterBounds;

	public DownpourMap(MapTemplate template, BlockBounds bounds, BlockBounds shelterBounds) {
		this.template = template;
		this.bounds = bounds;
		this.shelterBounds = shelterBounds;
	}

	public BlockBounds getBounds() {
		return this.bounds;
	}

	public BlockBounds getShelterBounds() {
		return this.shelterBounds;
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}