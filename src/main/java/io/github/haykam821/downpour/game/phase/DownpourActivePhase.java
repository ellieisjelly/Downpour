package io.github.haykam821.downpour.game.phase;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.haykam821.downpour.Main;
import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.DownpourTimerBar;
import io.github.haykam821.downpour.game.Shelter;
import io.github.haykam821.downpour.game.map.DownpourMap;
import io.github.haykam821.downpour.game.map.DownpourMapConfig;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.game.stats.GameStatisticBundle;
import xyz.nucleoid.plasmid.api.game.stats.StatisticKey;
import xyz.nucleoid.plasmid.api.game.stats.StatisticKeys;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerAttackEntityEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DownpourActivePhase {
	private final ServerLevel level;
	private final GameSpace gameSpace;
	private final DownpourMap map;
	private final DownpourConfig config;
	private final List<PlayerRef> players;
	private final DownpourTimerBar timerBar;
	private final GameStatisticBundle statistics;
	private boolean singleplayer;
	private int rounds = 0;
	private int ticksElapsed;
	private int ticksUntilSwitch;
	private int ticksUntilClose = -1;
	private Shelter shelter;

	public DownpourActivePhase(GameSpace gameSpace, ServerLevel level, DownpourMap map, DownpourConfig config, List<PlayerRef> players, GlobalWidgets widgets) {
		this.level = level;
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.players = players;
		this.timerBar = new DownpourTimerBar(widgets);
		this.ticksUntilSwitch = this.config.getLockTime();
		this.createShelter();

		this.statistics = config.getStatisticBundle(gameSpace);
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.INTERACTION);
		activity.deny(GameRuleType.PORTALS);
		activity.allow(GameRuleType.PVP);
	}

	public static void open(GameSpace gameSpace, ServerLevel level, DownpourMap map, DownpourConfig config) {
		gameSpace.setActivity(activity -> {
			GlobalWidgets widgets = GlobalWidgets.addTo(activity);

			List<PlayerRef> players = gameSpace.getPlayers().participants().stream().map(PlayerRef::of).collect(Collectors.toList());
			Collections.shuffle(players);

			DownpourActivePhase phase = new DownpourActivePhase(gameSpace, level, map, config, players, widgets);

			DownpourActivePhase.setRules(activity);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
			activity.listen(GamePlayerEvents.REMOVE, phase::removePlayer);
			activity.listen(PlayerAttackEntityEvent.EVENT, phase::onPlayerAttackEntity);
			activity.listen(PlayerDamageEvent.EVENT, phase::onPlayerDamage);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
		});
	}

	private void enable() {
		this.level.getWeatherData().setRaining(true);

		int index = 0;
		this.singleplayer = this.players.size() == 1;

		DownpourMapConfig mapConfig = this.config.getMapConfig();
		int spawnRadius = (Math.min(mapConfig.getX(), mapConfig.getZ()) - 4) / 2;

		Vec3 center = DownpourActivePhase.getCenterSpawnPos(this.map);

 		for (PlayerRef playerRef : this.players) {
			ServerPlayer player = playerRef.getEntity(this.level);

			if (player != null) {
				this.updateRoundsExperienceLevel(player);
				player.setGameMode(GameType.ADVENTURE);

				if (!this.singleplayer && this.statistics != null) {
					this.statistics.forPlayer(player).increment(StatisticKeys.GAMES_PLAYED, 1);
				}

				double theta = ((double) index / this.players.size()) * 2 * Math.PI;
				float yaw = (float) theta * Mth.RAD_TO_DEG + 90;

				double x = center.x() + Math.cos(theta) * spawnRadius;
				double z = center.z() + Math.sin(theta) * spawnRadius;

				Vec3 spawnPos = new Vec3(x, center.y(), z);
				DownpourActivePhase.spawn(this.level, spawnPos, yaw, player);
			}

			index++;
		}

		for (ServerPlayer player : this.gameSpace.getPlayers().spectators()) {
			DownpourActivePhase.spawn(this.level, this.map.getBounds().center(), 0, player);
			this.setSpectator(player);
		}
	}

	private void createShelter() {
		BlockPos minPos = this.map.getShelterBounds().min();
		BlockPos maxPos = this.map.getShelterBounds().max();

		int x = this.level.getRandom().nextInt(maxPos.getX() + 1 - minPos.getX()) + minPos.getX();
		int z = this.level.getRandom().nextInt(maxPos.getZ() + 1 - minPos.getZ()) + minPos.getZ();
		int size = Math.max(0, Math.min(4, 4 - this.rounds / 2));

		this.shelter = new Shelter(new BlockPos(x, this.map.getShelterBounds().min().getY(), z), size, false);
		this.shelter.build(this.level);
	}

	private Component getKnockbackEnabledText() {
		return Component.translatable("text.downpour.knockback_enabled").withStyle(ChatFormatting.RED);
	}

	private void updateRoundsExperienceLevel(ServerPlayer player) {
		player.setExperienceLevels(this.rounds + 1);
	}

	private void addRounds(int rounds) {
		this.rounds += rounds;

		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			this.updateRoundsExperienceLevel(player);
		}

		if (!this.singleplayer && this.statistics != null) {
			for (PlayerRef player : this.players) {
				this.statistics.forPlayer(player).increment(Main.ROUNDS_SURVIVED, rounds);
			}
		}
	}

	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		this.ticksElapsed += 1;

		this.ticksUntilSwitch -= 1;
		this.timerBar.tick(this);
		if (this.ticksUntilSwitch < 0) {
			if (this.shelter.isLocked()) {
				// Unlock
				this.shelter.clear(this.level);
				this.createShelter();

				this.addRounds(1);
				if (this.rounds == this.config.getNoKnockbackRounds()) {
					this.gameSpace.getPlayers().sendMessage(this.getKnockbackEnabledText());
				}
				
				this.gameSpace.getPlayers().playSound(this.config.getUnlockSound());
				this.ticksUntilSwitch = this.config.getLockTime();
			} else {
				// Lock
				this.shelter.setLocked(true);
				this.shelter.build(this.level);

				this.gameSpace.getPlayers().playSound(this.config.getLockSound());
				this.ticksUntilSwitch = this.config.getUnlockTime();
			}
		}

		// Eliminate players that are out of bounds
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			playerRef.ifOnline(this.level, player -> {
				if (!this.map.getBounds().contains(player.blockPosition())) {
					this.eliminate(player, ".out_of_bounds", false);
					playerIterator.remove();
				} else if (this.shelter != null && this.shelter.isLocked() && !this.shelter.getBox().isInside(player.blockPosition())) {
					this.eliminate(player, ".out_of_shelter", false);
					playerIterator.remove();
				}
			});
		}

		// Determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			
			ServerPlayer winner = this.getWinner();
			if (winner != null) {
				this.applyPlayerFinishStatistics(winner, StatisticKeys.GAMES_WON);
			}

			Component endingMessage = this.getEndingMessage(winner);
			this.gameSpace.getPlayers().sendMessage(endingMessage);
			this.gameSpace.getPlayers().playSound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 1, 1);
			this.ticksUntilClose = this.config.getTicksUntilClose().sample(this.level.getRandom());
		}
	}

	private ServerPlayer getWinner() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.level)) {
				return winnerRef.getEntity(this.level);
			}
		}
		return null;
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private Component getEndingMessage(ServerPlayer winner) {
		if (winner == null) {
			return Component.translatable("text.downpour.no_winners", this.rounds).withStyle(ChatFormatting.GOLD);
		} else {
			return Component.translatable("text.downpour.win", winner.getDisplayName(), this.rounds).withStyle(ChatFormatting.GOLD);
		}
	}

	private void setSpectator(ServerPlayer player) {
		player.setGameMode(GameType.SPECTATOR);
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, this.map.getBounds().center()).thenRunForEach(player -> {
			this.updateRoundsExperienceLevel(player);
			this.setSpectator(player);
		});
	}

	private void removePlayer(ServerPlayer player) {
		this.eliminate(player, true);
	}

	private boolean eliminate(ServerPlayer eliminatedPlayer, String suffix, boolean remove) {
		if (this.isGameEnding()) return false;

		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) {
			return false;
		}

		Component message = Component.translatable("text.downpour.eliminated" + suffix, eliminatedPlayer.getDisplayName()).withStyle(ChatFormatting.RED);
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			player.sendSystemMessage(message, false);
		}

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);

		this.applyPlayerFinishStatistics(eliminatedPlayer, StatisticKeys.GAMES_LOST);

		return true;
	}

	private boolean eliminate(ServerPlayer eliminatedPlayer, boolean remove) {
		return this.eliminate(eliminatedPlayer, "", remove);
	}

	public void applyPlayerFinishStatistics(ServerPlayer player, StatisticKey<Integer> finishTypeKey) {
		if (!this.singleplayer && this.statistics != null) {
			this.statistics.forPlayer(player).increment(finishTypeKey, 1);
			this.statistics.forPlayer(player).set(StatisticKeys.LONGEST_TIME, this.ticksElapsed);
		}
	}

	private EventResult onPlayerAttackEntity(ServerPlayer attacker, InteractionHand hand, Entity attacked, EntityHitResult hitResult) {
		if (!this.isGameEnding() && attacker != attacked && this.players.contains(PlayerRef.of(attacker)) && !this.singleplayer && this.statistics != null) {
			ServerPlayer attackedPlayer = (ServerPlayer) attacked;
			if (this.players.contains(PlayerRef.of(attackedPlayer))) {
				this.statistics.forPlayer(attacker).increment(Main.PLAYERS_PUNCHED, 1);
			}
		}

		return EventResult.PASS;
	}

	private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
		return this.rounds >= this.config.getNoKnockbackRounds() ? EventResult.ALLOW : EventResult.DENY;
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		if (!this.eliminate(player, true)) {
			DownpourActivePhase.spawnAtCenter(this.level, this.map, player);
		}
		return EventResult.DENY;
	}

	public static void spawn(ServerLevel world, Vec3 pos, float yaw, ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 127, true, false));
		player.teleportTo(world, pos.x(), pos.y(), pos.z(), Set.of(), yaw, 0, true);
	}

	public static void spawnAtCenter(ServerLevel world, DownpourMap map, ServerPlayer player) {
		Vec3 pos = DownpourActivePhase.getCenterSpawnPos(map);
		DownpourActivePhase.spawn(world, pos, 0, player);
	}

	public static Vec3 getCenterSpawnPos(DownpourMap map) {
		Vec3 center = map.getBounds().center();
		return new Vec3(center.x(), map.getShelterBounds().min().getY(), center.z());
	}

	public float getTimerBarPercent() {
		if (this.shelter == null) return 0;
		return this.ticksUntilSwitch / (float) (this.shelter.isLocked() ? this.config.getUnlockTime() : this.config.getLockTime());
	}
}