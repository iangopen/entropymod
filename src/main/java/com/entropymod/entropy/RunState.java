package com.entropymod.entropy;

/**
 * Where a run is in its lifecycle.
 *
 * <p>The full architecture, {@code NOT_STARTED -> IN_PROGRESS -> ENDED}, is now
 * built. {@code ENDED} was deliberately absent until something could produce it
 * -- a constant nothing can reach invites code to branch on an impossible state
 * -- and it arrived in the same session as the two conditions that reach it.
 *
 * <p><b>{@code EntropyManager.gameOver} is gone, and this enum is what replaced
 * it.</b> That boolean was the original entropy-cap stop and predated this enum;
 * for a while the project carried two unreconciled notions of "the run is over".
 * There is now one. The persisted {@code game_over} key is still <em>read</em>,
 * once, to migrate an old save -- see {@code EntropyManager}'s codec constructor
 * -- but nothing writes it and no field holds it.
 *
 * <p>Persisted <b>by name</b> in {@code EntropyManager}'s codec, the same rule
 * {@code EffectPhase} follows on the wire: reordering or inserting a constant
 * must not silently change what a saved run means.
 */
public enum RunState {

	/**
	 * A world exists but the player has not started the run. The entropy loop is
	 * hard-gated off -- no interval counting, no picks.
	 */
	NOT_STARTED,

	/** The run is live. The loop behaves exactly as it always has. */
	IN_PROGRESS,

	/**
	 * The run is over, win or lose. Which condition fired is
	 * {@code EntropyManager.getOutcome()}, a {@link RunOutcome}.
	 *
	 * <p><b>This stops the entropy loop and nothing else.</b> The world stays
	 * fully playable, by decision rather than by omission, and every effect the
	 * player acquired stays in force -- effects are permanent, and
	 * {@code AcquiredEffects} remains the single source of truth for them. Only
	 * {@code EntropyManager.tick} checks this state; the eight per-tick effect
	 * services deliberately do not. See CLAUDE.md for why gating only the
	 * tick-driven subset would make a player's permanent effects half-work.
	 *
	 * <p>Terminal: nothing transitions out of it. {@code endRun} is idempotent, so
	 * a second condition firing after the first is a no-op rather than a
	 * re-decision -- a dragon killed on the same tick entropy caps out cannot
	 * overwrite the loss with a win, or the reverse.
	 */
	ENDED;

	/**
	 * Parses a persisted or wire name, falling back to {@code NOT_STARTED} for
	 * anything unrecognised.
	 *
	 * <p>Degrading rather than throwing matters in both places this is used: a
	 * hand-edited or newer save must not make the world unloadable, and a wire
	 * value must not throw inside a network handler. {@code NOT_STARTED} is the
	 * safe fallback specifically because it stops the loop rather than starting
	 * one the player never asked for -- and it stays the right fallback now that
	 * {@link #ENDED} exists, because falling back to a terminal state would end a
	 * run on nothing more than an unreadable string.
	 */
	public static RunState parse(String name) {
		if (name != null) {
			for (RunState state : values()) {
				if (state.name().equals(name)) {
					return state;
				}
			}
		}
		return NOT_STARTED;
	}
}
