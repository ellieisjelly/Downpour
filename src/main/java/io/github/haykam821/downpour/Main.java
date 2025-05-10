package io.github.haykam821.downpour;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.phase.DownpourWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.stats.StatisticKey;

public class Main implements ModInitializer {
	private static final String MOD_ID = "downpour";

	private static final Identifier DOWNPOUR_ID = Main.identifier("downpour");
	public static final GameType<DownpourConfig> DOWNPOUR_TYPE = GameType.register(DOWNPOUR_ID, DownpourConfig.CODEC, DownpourWaitingPhase::open);

	private static final Identifier ROUNDS_SURVIVED_ID = Main.identifier("rounds_survived");
	public static final StatisticKey<Integer> ROUNDS_SURVIVED = StatisticKey.intKey(ROUNDS_SURVIVED_ID);

	private static final Identifier PLAYERS_PUNCHED_ID = Main.identifier("players_punched");
	public static final StatisticKey<Integer> PLAYERS_PUNCHED = StatisticKey.intKey(PLAYERS_PUNCHED_ID);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.of(MOD_ID, path);
	}
}