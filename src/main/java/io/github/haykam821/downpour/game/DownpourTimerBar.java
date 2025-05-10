package io.github.haykam821.downpour.game;

import io.github.haykam821.downpour.game.phase.DownpourActivePhase;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;

public class DownpourTimerBar {
	private static final Text TITLE = Text.translatable("gameType.downpour.downpour").formatted(Formatting.AQUA);

	private final BossBarWidget bar;

	public DownpourTimerBar(GlobalWidgets widgets) {
		this.bar = widgets.addBossBar(TITLE, BossBar.Color.BLUE, BossBar.Style.PROGRESS);
	}

	public void tick(DownpourActivePhase phase) {
		float percent = phase.getTimerBarPercent();
		this.bar.setProgress(percent);
	}
}
