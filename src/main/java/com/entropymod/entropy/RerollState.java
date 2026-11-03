package com.entropymod.entropy;

/**
 * What the client should draw for Second Guess on a pending pick.
 *
 * <p><b>An enum, not a boolean</b>, because "no button" and "spent button"
 * are different things to a player and a single {@code rerollAvailable} flag
 * cannot tell them apart. A player who never took Second Guess should see
 * nothing; a player who took it and used it should see that it is gone, or the
 * effect looks like it silently stopped existing.
 *
 * <p>It grew from three states to five when All In's banked
 * {@code rerollCharges} and No Do-Overs' permanent ban arrived. Those are
 * <b>separate</b> run state from Second Guess's {@code rerollUsed}, which is
 * untouched -- see {@code EntropyManager.rerollState} for the precedence, and note
 * it deliberately matches the order {@code requestReroll} spends them in, so the
 * button never advertises one resource and consume another.
 *
 * <p>This is the shape CLAUDE.md's {@code EffectDuration} note argues for: when
 * one value carries several meanings, make it an enum so every call site is
 * forced by the compiler to handle each case, rather than an int or a boolean
 * whose meaning has to be remembered.
 *
 * <p>Sent on {@code OpenChoicePayload} <b>by name</b> (see {@code EntropyCodecs})
 * so reordering these constants cannot silently change what the client renders.
 */
public enum RerollState {

	/** The player does not have Second Guess. No button, and no space reserved for one. */
	NOT_OWNED,

	/** Owned and unspent, with a pick pending. The button is live. */
	AVAILABLE,

	/**
	 * Banked reroll charges are available. The button is live and is labelled with
	 * the count, because charges are what a reroll spends first.
	 *
	 * <p>Outranks {@link #AVAILABLE} in the derivation even when Second Guess is
	 * also unspent. That is not cosmetic: a button reading "Second Guess" that
	 * quietly consumed a charge would lie about which resource the player just
	 * spent, and Second Guess's one-shot is the one they plan around.
	 */
	CHARGED,

	/**
	 * No Do-Overs is held. Every kind of reroll is refused for the rest of the run,
	 * <b>including charges banked before it was picked</b>. Drawn disabled, for the
	 * rest of the run, whenever the player has anything it is taking away.
	 */
	FORBIDDEN,

	/** Owned but already spent this run. The button is drawn disabled, for the rest of the run. */
	SPENT
}
