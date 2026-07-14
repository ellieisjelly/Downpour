package io.github.haykam821.downpour.game.phase;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.map.DownpourMap;
import io.github.haykam821.downpour.game.map.DownpourMapBuilder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DownpourWaitingPhase {
	private final GameSpace gameSpace;
	private final ServerLevel level;
	private final DownpourMap map;
	private final DownpourConfig config;

	public DownpourWaitingPhase(GameSpace gameSpace, ServerLevel level, DownpourMap map, DownpourConfig config) {
		this.gameSpace = gameSpace;
		this.level = level;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<DownpourConfig> context) {
		DownpourMapBuilder mapBuilder = new DownpourMapBuilder(context.config());
		DownpourMap map = mapBuilder.create();

		RuntimeLevelConfig levelConfig = new RuntimeLevelConfig()
			.setGenerator(map.createGenerator(context.server()));

		return context.openWithLevel(levelConfig, (activity, level) -> {
			DownpourWaitingPhase phase = new DownpourWaitingPhase(activity.getGameSpace(), level, map, context.config());
			GameWaitingLobby.addTo(activity, context.config().getPlayerConfig());

			DownpourActivePhase.setRules(activity);
			activity.deny(GameRuleType.PVP);

			// Listeners
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			activity.listen(GameActivityEvents.REQUEST_START, phase::requestStart);
		});
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, DownpourActivePhase.getCenterSpawnPos(this.map)).thenRunForEach(player -> {
			player.setGameMode(GameType.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		DownpourActivePhase.open(this.gameSpace, this.level, this.map, this.config);
		return GameResult.ok();
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		DownpourActivePhase.spawnAtCenter(this.level, this.map, player);
		return EventResult.DENY;
	}
}