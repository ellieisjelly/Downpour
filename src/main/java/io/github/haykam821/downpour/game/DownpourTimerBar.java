package io.github.haykam821.downpour.game;

import io.github.haykam821.downpour.game.phase.DownpourActivePhase;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;

public class DownpourTimerBar {
	private static final Component TITLE = Component.translatable("gameType.downpour.downpour").withStyle(ChatFormatting.AQUA);

	private final BossBarWidget bar;

	public DownpourTimerBar(GlobalWidgets widgets) {
		this.bar = widgets.addBossBar(TITLE, BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
	}

	public void tick(DownpourActivePhase phase) {
		float percent = phase.getTimerBarPercent();
		this.bar.setProgress(percent);
	}
}
