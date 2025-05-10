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
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
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
	private final ServerWorld world;
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

	public DownpourActivePhase(GameSpace gameSpace, ServerWorld world, DownpourMap map, DownpourConfig config, List<PlayerRef> players, GlobalWidgets widgets) {
		this.world = world;
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

	public static void open(GameSpace gameSpace, ServerWorld world, DownpourMap map, DownpourConfig config) {
		gameSpace.setActivity(activity -> {
			GlobalWidgets widgets = GlobalWidgets.addTo(activity);

			List<PlayerRef> players = gameSpace.getPlayers().participants().stream().map(PlayerRef::of).collect(Collectors.toList());
			Collections.shuffle(players);

			DownpourActivePhase phase = new DownpourActivePhase(gameSpace, world, map, config, players, widgets);

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
		this.world.setWeather(0, 0, true, false);

		int index = 0;
		this.singleplayer = this.players.size() == 1;

		DownpourMapConfig mapConfig = this.config.getMapConfig();
		int spawnRadius = (Math.min(mapConfig.getX(), mapConfig.getZ()) - 4) / 2;

		Vec3d center = DownpourActivePhase.getCenterSpawnPos(this.map);

 		for (PlayerRef playerRef : this.players) {
			ServerPlayerEntity player = playerRef.getEntity(this.world);

			if (player != null) {
				this.updateRoundsExperienceLevel(player);
				player.changeGameMode(GameMode.ADVENTURE);

				if (!this.singleplayer && this.statistics != null) {
					this.statistics.forPlayer(player).increment(StatisticKeys.GAMES_PLAYED, 1);
				}

				double theta = ((double) index / this.players.size()) * 2 * Math.PI;
				float yaw = (float) theta * MathHelper.DEGREES_PER_RADIAN + 90;

				double x = center.getX() + Math.cos(theta) * spawnRadius;
				double z = center.getZ() + Math.sin(theta) * spawnRadius;

				Vec3d spawnPos = new Vec3d(x, center.getY(), z);
				DownpourActivePhase.spawn(this.world, spawnPos, yaw, player);
			}

			index++;
		}

		for (ServerPlayerEntity player : this.gameSpace.getPlayers().spectators()) {
			DownpourActivePhase.spawn(this.world, this.map.getBounds().center(), 0, player);
			this.setSpectator(player);
		}
	}

	private void createShelter() {
		BlockPos minPos = this.map.getShelterBounds().min();
		BlockPos maxPos = this.map.getShelterBounds().max();

		int x = this.world.getRandom().nextInt(maxPos.getX() + 1 - minPos.getX()) + minPos.getX();
		int z = this.world.getRandom().nextInt(maxPos.getZ() + 1 - minPos.getZ()) + minPos.getZ();
		int size = Math.max(0, Math.min(4, 4 - this.rounds / 2));

		this.shelter = new Shelter(new BlockPos(x, this.map.getShelterBounds().min().getY(), z), size, false);
		this.shelter.build(this.world);
	}

	private Text getKnockbackEnabledText() {
		return Text.translatable("text.downpour.knockback_enabled").formatted(Formatting.RED);
	}

	private void updateRoundsExperienceLevel(ServerPlayerEntity player) {
		player.setExperienceLevel(this.rounds + 1);
	}

	private void addRounds(int rounds) {
		this.rounds += rounds;

		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
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
				this.shelter.clear(this.world);
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
				this.shelter.build(this.world);

				this.gameSpace.getPlayers().playSound(this.config.getLockSound());
				this.ticksUntilSwitch = this.config.getUnlockTime();
			}
		}

		// Eliminate players that are out of bounds
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			playerRef.ifOnline(this.world, player -> {
				if (!this.map.getBounds().contains(player.getBlockPos())) {
					this.eliminate(player, ".out_of_bounds", false);
					playerIterator.remove();
				} else if (this.shelter != null && this.shelter.isLocked() && !this.shelter.getBox().contains(player.getBlockPos())) {
					this.eliminate(player, ".out_of_shelter", false);
					playerIterator.remove();
				}
			});
		}

		// Determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			
			ServerPlayerEntity winner = this.getWinner();
			if (winner != null) {
				this.applyPlayerFinishStatistics(winner, StatisticKeys.GAMES_WON);
			}

			Text endingMessage = this.getEndingMessage(winner);
			this.gameSpace.getPlayers().sendMessage(endingMessage);

			this.ticksUntilClose = this.config.getTicksUntilClose().get(this.world.getRandom());
		}
	}

	private ServerPlayerEntity getWinner() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.world)) {
				return winnerRef.getEntity(this.world);
			}
		}
		return null;
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private Text getEndingMessage(ServerPlayerEntity winner) {
		if (winner == null) {
			return Text.translatable("text.downpour.no_winners", this.rounds).formatted(Formatting.GOLD);
		} else {
			return Text.translatable("text.downpour.win", winner.getDisplayName(), this.rounds).formatted(Formatting.GOLD);
		}
	}

	private void setSpectator(ServerPlayerEntity player) {
		player.changeGameMode(GameMode.SPECTATOR);
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, this.map.getBounds().center()).thenRunForEach(player -> {
			this.updateRoundsExperienceLevel(player);
			this.setSpectator(player);
		});
	}

	private void removePlayer(ServerPlayerEntity player) {
		this.eliminate(player, true);
	}

	private boolean eliminate(ServerPlayerEntity eliminatedPlayer, String suffix, boolean remove) {
		if (this.isGameEnding()) return false;

		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) {
			return false;
		}

		Text message = Text.translatable("text.downpour.eliminated" + suffix, eliminatedPlayer.getDisplayName()).formatted(Formatting.RED);
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			player.sendMessage(message, false);
		}

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);

		this.applyPlayerFinishStatistics(eliminatedPlayer, StatisticKeys.GAMES_LOST);

		return true;
	}

	private boolean eliminate(ServerPlayerEntity eliminatedPlayer, boolean remove) {
		return this.eliminate(eliminatedPlayer, "", remove);
	}

	public void applyPlayerFinishStatistics(ServerPlayerEntity player, StatisticKey<Integer> finishTypeKey) {
		if (!this.singleplayer && this.statistics != null) {
			this.statistics.forPlayer(player).increment(finishTypeKey, 1);
			this.statistics.forPlayer(player).set(StatisticKeys.LONGEST_TIME, this.ticksElapsed);
		}
	}

	private EventResult onPlayerAttackEntity(ServerPlayerEntity attacker, Hand hand, Entity attacked, EntityHitResult hitResult) {
		if (!this.isGameEnding() && attacker != attacked && this.players.contains(PlayerRef.of(attacker)) && !this.singleplayer && this.statistics != null) {
			ServerPlayerEntity attackedPlayer = (ServerPlayerEntity) attacked;
			if (this.players.contains(PlayerRef.of(attackedPlayer))) {
				this.statistics.forPlayer(attacker).increment(Main.PLAYERS_PUNCHED, 1);
			}
		}

		return EventResult.PASS;
	}

	private EventResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		return this.rounds >= this.config.getNoKnockbackRounds() ? EventResult.ALLOW : EventResult.DENY;
	}

	private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (!this.eliminate(player, true)) {
			DownpourActivePhase.spawnAtCenter(this.world, this.map, player);
		}
		return EventResult.DENY;
	}

	public static void spawn(ServerWorld world, Vec3d pos, float yaw, ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, StatusEffectInstance.INFINITE, 127, true, false));
		player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), yaw, 0, true);
	}

	public static void spawnAtCenter(ServerWorld world, DownpourMap map, ServerPlayerEntity player) {
		Vec3d pos = DownpourActivePhase.getCenterSpawnPos(map);
		DownpourActivePhase.spawn(world, pos, 0, player);
	}

	public static Vec3d getCenterSpawnPos(DownpourMap map) {
		Vec3d center = map.getBounds().center();
		return new Vec3d(center.getX(), map.getShelterBounds().min().getY(), center.getZ());
	}

	public float getTimerBarPercent() {
		if (this.shelter == null) return 0;
		return this.ticksUntilSwitch / (float) (this.shelter.isLocked() ? this.config.getUnlockTime() : this.config.getLockTime());
	}
}