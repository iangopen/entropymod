package com.entropymod.client.gui;

import com.entropymod.client.EntropyColors;
import com.entropymod.entropy.EffectPhase;
import com.entropymod.entropy.RunOutcome;
import com.entropymod.network.HistoryResponsePayload;
import com.entropymod.network.RunEndPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The run is over. Shown once, on the transition into {@code RunState.ENDED}.
 *
 * <p>Built on {@link ChoiceScreen}'s and {@link StartScreen}'s established
 * pattern -- everything sized from {@code this.width}/{@code this.height},
 * centred as one measured block, colours from {@link EntropyColors} -- with
 * <b>one deliberate difference: this screen is dismissible.</b>
 *
 * <h2>Why it closes on Escape when the other two do not</h2>
 *
 * <p>The other modals are gates: a pick is mandatory, and the run must be started
 * deliberately, so neither may be escaped. There is nothing to gate here. The
 * recorded decision is explicit that <b>the world stays fully playable after the
 * run ends, with no lock</b>, so dismissing this does exactly one thing --
 * closes it. It sets no state, sends no packet, and the server neither knows nor
 * cares that it was shown.
 *
 * <p>It follows that {@code isPauseScreen()} is true only so the world is not
 * running underneath while the player reads; that is cosmetic here rather than
 * load-bearing.
 *
 * <p><b>It is shown once and is not re-opened.</b> There is no client-tick guard
 * of the kind {@code StartScreen} needs, because a screen that re-asserted itself
 * would be a lock -- precisely what was decided against. A player who wants the
 * numbers again has {@code /entropystatus} and {@code /entropyhistory}.
 *
 * <h2>Every value on it is real run state</h2>
 *
 * <p>Nothing here is derived or guessed client-side, and it could not be: the
 * client's entropy readout is a cache refreshed only when a pick opens, it is
 * never told the death count at all, and it only holds history if it has asked.
 * All of it arrives on {@link RunEndPayload}. <b>Total picks is
 * {@code history.size()}</b> rather than a separate number, so the count and the
 * list it describes cannot disagree.
 */
public class EndScreen extends Screen {

	private static final int SCREEN_MARGIN = 12;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_MIN_WIDTH = 100;
	private static final int BUTTON_MAX_WIDTH = 200;
	private static final int GAP = 8;
	private static final int RULE_HEIGHT = 2;
	private static final int RULE_MAX_HALF_WIDTH = 180;

	/** History rows are drawn at this scale so a long run still fits. */
	private static final float HISTORY_SCALE = 0.75f;

	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int BODY_COLOR = 0xFFC8C8C8;
	private static final int DIM_COLOR = 0xFF8C8C8C;
	private static final int WIN_COLOR = 0xFF55DD55;
	private static final int LOSS_COLOR = 0xFFDD5555;
	private static final int GOOD_COLOR = 0xFF55DD55;
	private static final int BAD_COLOR = 0xFFDD5555;
	private static final int PANEL_FILL = 0xFF2A2A2A;

	private final RunOutcome outcome;
	private final int entropy;
	private final int entropyCap;
	private final int deathCount;
	private final List<HistoryResponsePayload.Entry> history;

	private int accentColor = TITLE_COLOR;
	private int titleY;
	private int ruleY;
	private int summaryY;
	private int historyTop;
	private int historyBottom;
	private int scaledLineHeight = 1;

	/** How far the history list is scrolled, in rows. */
	private int scrollRows;
	private int visibleRows;

	public EndScreen(RunEndPayload payload) {
		this(payload.parsedOutcome(), payload.entropy(), payload.entropyCap(),
				payload.deathCount(), payload.history());
	}

	public EndScreen(RunOutcome outcome, int entropy, int entropyCap, int deathCount,
					 List<HistoryResponsePayload.Entry> history) {
		super(Component.literal(outcome == null ? "Run Over" : outcome.headline()));
		this.outcome = outcome;
		this.entropy = entropy;
		this.entropyCap = entropyCap;
		this.deathCount = deathCount;
		this.history = List.copyOf(history);
	}

	// ---------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------

	@Override
	protected void init() {
		// The accent is the same ramp the HUD and the pick screen use, read at the
		// entropy the run actually finished on -- so a run that ended at the cap is
		// drawn in the endgame colour rather than a generic red.
		this.accentColor = EntropyColors.colorAt(entropy, entropyCap,
				outcome != null && outcome.isWin() ? EffectPhase.GOOD : EffectPhase.BAD);
		this.scaledLineHeight = Math.max(1, Math.round(this.font.lineHeight * HISTORY_SCALE));

		int buttonWidth = Math.min(BUTTON_MAX_WIDTH,
				Math.max(BUTTON_MIN_WIDTH, this.width - 2 * SCREEN_MARGIN));

		// Fixed chrome is measured first; the history list then takes whatever
		// vertical space is left. That ordering is what keeps the button on screen
		// at any GUI scale -- the list shrinks, never the way out.
		this.titleY = SCREEN_MARGIN;
		this.ruleY = this.titleY + this.font.lineHeight + GAP;
		this.summaryY = this.ruleY + RULE_HEIGHT + GAP;

		int summaryHeight = 3 * this.font.lineHeight + 2;
		this.historyTop = this.summaryY + summaryHeight + GAP;

		int buttonY = Math.max(this.historyTop + scaledLineHeight,
				this.height - SCREEN_MARGIN - BUTTON_HEIGHT);
		this.historyBottom = Math.max(this.historyTop, buttonY - GAP);

		// The header row costs one line; the rest is rows.
		int listHeight = Math.max(0, this.historyBottom - this.historyTop - this.font.lineHeight - 2);
		this.visibleRows = Math.max(1, listHeight / scaledLineHeight);
		this.scrollRows = Math.max(0, Math.min(this.scrollRows, maxScroll()));

		this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
				.bounds(this.width / 2 - buttonWidth / 2, buttonY, buttonWidth, BUTTON_HEIGHT)
				.build());
	}

	private int maxScroll() {
		return Math.max(0, history.size() - visibleRows);
	}

	// ---------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		String headline = outcome == null ? "Run Over" : outcome.headline();
		int headlineColor = outcome == null ? DIM_COLOR : (outcome.isWin() ? WIN_COLOR : LOSS_COLOR);
		graphics.text(this.font, headline,
				this.width / 2 - this.font.width(headline) / 2, this.titleY, headlineColor, true);

		int half = Math.min(RULE_MAX_HALF_WIDTH, Math.max(1, (this.width - 2 * SCREEN_MARGIN) / 2));
		graphics.fill(this.width / 2 - half, this.ruleY,
				this.width / 2 + half, this.ruleY + RULE_HEIGHT, accentColor);

		// Line 1: which of the three conditions fired. The architecture is explicit
		// that the screen must say which, not just win or lose.
		String cause = outcome == null
				? "The run ended, but this build does not recognise how."
				: outcome.description();
		graphics.text(this.font, cause,
				this.width / 2 - this.font.width(cause) / 2, this.summaryY, BODY_COLOR, true);

		// Line 2: the numbers. Picks is the history's own length.
		String stats = "Entropy " + entropy + " / " + entropyCap
				+ "     Picks " + history.size()
				+ "     Deaths " + deathCount;
		graphics.text(this.font, stats,
				this.width / 2 - this.font.width(stats) / 2,
				this.summaryY + this.font.lineHeight + 1, BODY_COLOR, true);

		drawHistory(graphics);
	}

	private void drawHistory(GuiGraphicsExtractor graphics) {
		int listX = Math.max(SCREEN_MARGIN, this.width / 2 - RULE_MAX_HALF_WIDTH);
		int listRight = Math.min(this.width - SCREEN_MARGIN, this.width / 2 + RULE_MAX_HALF_WIDTH);

		String header = history.isEmpty()
				? "No picks were made this run."
				: "Pick history (" + history.size() + ")"
						+ (maxScroll() > 0 ? "  --  scroll to see all" : "");
		graphics.text(this.font, header, listX, this.historyTop, DIM_COLOR, true);
		if (history.isEmpty()) {
			return;
		}

		int top = this.historyTop + this.font.lineHeight + 2;
		graphics.fill(listX - 2, top - 1, listRight, this.historyBottom, PANEL_FILL);

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) listX, (float) top);
		pose.scale(HISTORY_SCALE, HISTORY_SCALE);

		int drawn = 0;
		for (int i = scrollRows; i < history.size() && drawn < visibleRows; i++, drawn++) {
			HistoryResponsePayload.Entry entry = history.get(i);
			int y = Math.round(drawn * scaledLineHeight / HISTORY_SCALE);

			String index = "#" + entry.pickNumber();
			graphics.text(this.font, index, 0, y, DIM_COLOR, true);

			String name = entry.effectName();
			int nameX = Math.round(28 / HISTORY_SCALE * HISTORY_SCALE) + 24;
			graphics.text(this.font, name, nameX, y,
					entry.phase() == EffectPhase.GOOD ? GOOD_COLOR : BAD_COLOR, true);

			String at = "entropy " + entry.entropyAtPick();
			graphics.text(this.font, at, nameX + this.font.width(name) + 8, y, DIM_COLOR, true);
		}
		pose.popMatrix();
	}

	// ---------------------------------------------------------------------
	// Interaction
	// ---------------------------------------------------------------------

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll() > 0) {
			scrollRows = Math.max(0, Math.min(maxScroll(), scrollRows - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/**
	 * Dismissible, unlike every other modal in this mod.
	 *
	 * <p>The recorded decision is that the world stays fully playable after a run
	 * ends, with no lock of any kind. Refusing Escape here would be that lock.
	 */
	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	/**
	 * Closes, and does nothing else -- no packet, no state.
	 *
	 * <p>Stated explicitly because every other screen in this mod that closes
	 * does so as part of a protocol.
	 */
	@Override
	public void onClose() {
		this.minecraft.setScreen(null);
	}

	/** Exposed for the harness: the rows this screen would draw, in order. */
	public List<HistoryResponsePayload.Entry> historyRows() {
		return new ArrayList<>(history);
	}
}
