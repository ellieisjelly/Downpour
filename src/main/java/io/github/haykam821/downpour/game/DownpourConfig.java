package io.github.haykam821.downpour.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.downpour.game.map.DownpourMapConfig;
import net.minecraft.SharedConstants;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.stats.GameStatisticBundle;

public class DownpourConfig {
	public static final MapCodec<DownpourConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			DownpourMapConfig.CODEC.fieldOf("map").forGetter(DownpourConfig::getMapConfig),
			WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(DownpourConfig::getPlayerConfig),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("ticks_until_close", ConstantIntProvider.create(SharedConstants.TICKS_PER_SECOND * 5)).forGetter(DownpourConfig::getTicksUntilClose),
			SoundEvent.CODEC.optionalFieldOf("lock_sound", SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value()).forGetter(DownpourConfig::getLockSound),
			SoundEvent.CODEC.optionalFieldOf("unlock_sound", SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER).forGetter(DownpourConfig::getUnlockSound),
			Codec.INT.optionalFieldOf("lock_time", 20 * 7).forGetter(DownpourConfig::getLockTime),
			Codec.INT.optionalFieldOf("unlock_time", 20 * 2).forGetter(DownpourConfig::getUnlockTime),
			Codec.INT.optionalFieldOf("no_knockback_rounds", 2).forGetter(DownpourConfig::getNoKnockbackRounds),
			GameStatisticBundle.NAMESPACE_CODEC.optionalFieldOf("statistic_bundle_namespace").forGetter(DownpourConfig::getStatisticBundleNamespace)
		).apply(instance, DownpourConfig::new);
	});

	private final DownpourMapConfig mapConfig;
	private final WaitingLobbyConfig playerConfig;
	private final IntProvider ticksUntilClose;
	private final SoundEvent lockSound;
	private final SoundEvent unlockSound;
	private final int lockTime;
	private final int unlockTime;
	private final int noKnockbackRounds;
	private final Optional<String> statisticBundleNamespace;

	public DownpourConfig(DownpourMapConfig mapConfig, WaitingLobbyConfig playerConfig, IntProvider ticksUntilClose, SoundEvent lockSound, SoundEvent unlockSound, int lockTime, int unlockTime, int noKnockbackRounds, Optional<String> statisticBundleNamespace) {
		this.mapConfig = mapConfig;
		this.playerConfig = playerConfig;
		this.ticksUntilClose = ticksUntilClose;
		this.lockSound = lockSound;
		this.unlockSound = unlockSound;
		this.lockTime = lockTime;
		this.unlockTime = unlockTime;
		this.noKnockbackRounds = noKnockbackRounds;
		this.statisticBundleNamespace = statisticBundleNamespace;
	}

	public DownpourMapConfig getMapConfig() {
		return this.mapConfig;
	}

	public WaitingLobbyConfig getPlayerConfig() {
		return this.playerConfig;
	}

	public IntProvider getTicksUntilClose() {
		return this.ticksUntilClose;
	}

	public SoundEvent getLockSound() {
		return this.lockSound;
	}

	public SoundEvent getUnlockSound() {
		return this.unlockSound;
	}

	public int getLockTime() {
		return this.lockTime;
	}

	public int getUnlockTime() {
		return this.unlockTime;
	}

	public int getNoKnockbackRounds() {
		return this.noKnockbackRounds;
	}

	public Optional<String> getStatisticBundleNamespace() {
		return this.statisticBundleNamespace;
	}

	public GameStatisticBundle getStatisticBundle(GameSpace gameSpace) {
		return this.statisticBundleNamespace
			.map(namespace -> {
				return gameSpace.getStatistics().bundle(namespace);
			})
			.orElse(null);
	}
}