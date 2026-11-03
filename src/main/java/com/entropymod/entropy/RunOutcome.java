package com.entropymod.entropy;

/**
 * How a run ended -- which of the recorded paths into {@link RunState#ENDED}
 * fired, and whether it was a win.
 *
 * <p><b>An enum rather than a boolean, for the reason {@link RerollState}
 * records:</b> the end screen must say <em>which</em> condition triggered, not
 * just "you lost", and a single {@code won} flag cannot carry that. Every path
 * into {@code ENDED} names itself here, so a new path is one constant plus one
 * {@code endRun} call rather than a new field.
 *
 * <p>Persisted <b>by name</b> in {@code EntropyManager}'s codec, same rule
 * {@link RunState} and {@link EffectPhase} follow: reordering the constants must
 * not silently rewrite what a saved run means.
 *
 * <h2>The third path is deliberately absent</h2>
 *
 * <p>The recorded architecture has three paths to {@code ENDED}; two exist. The
 * third -- death while Become Hardcore is held -- has <b>no constant here</b>,
 * on exactly the discipline that kept {@code ENDED} itself out of
 * {@link RunState} until something could produce it: a constant nothing can
 * reach invites code to branch on a state that cannot occur, and makes the build
 * status of that half ambiguous to read. The harness asserts its absence.
 *
 * <p><b>That is not a gap Become Hardcore will have to work around.</b> The
 * mechanism it plugs into is finished: {@code EntropyManager.endRun(server, outcome)}
 * is idempotent, persists the outcome, stops the loop, voids any pending pick and
 * pushes the end screen. Building that effect is one constant here
 * ({@code LOSS_HARDCORE_DEATH}, {@code win = false}) and one {@code endRun} call
 * from the death listener that already exists for the death counter. <b>No
 * architecture in this file or in {@code EntropyManager} needs to change.</b>
 */
public enum RunOutcome {

	/**
	 * The Ender Dragon was defeated for the first time in this world. Win.
	 *
	 * <p>"First time" is vanilla's own record, not a parallel one -- see
	 * {@code RunEndDetector} for why {@code EnderDragonFight.hasPreviouslyKilledDragon()}
	 * can be trusted at the moment the death event fires.
	 */
	WIN_DRAGON(true, "The Ender Dragon is dead. You beat the entropy."),

	/**
	 * Entropy reached the configured cap. Loss.
	 *
	 * <p>This is the condition the retired {@code gameOver} boolean used to carry
	 * on its own, before {@link RunState#ENDED} existed to hold it.
	 */
	LOSS_ENTROPY_CAP(false, "Entropy reached the cap. The world got away from you.");

	private final boolean win;
	private final String description;

	RunOutcome(boolean win, String description) {
		this.win = win;
		this.description = description;
	}

	public boolean isWin() {
		return win;
	}

	/** One line naming which condition fired. Shown on the end screen. */
	public String description() {
		return description;
	}

	/** "Victory" / "Defeat" -- the end screen's headline. */
	public String headline() {
		return win ? "Victory" : "Defeat";
	}

	/**
	 * Parses a persisted or wire name, returning {@code null} for anything
	 * unrecognised rather than throwing.
	 *
	 * <p>Null rather than a fallback constant, and that is deliberate: every other
	 * parse in this project degrades to a safe default, but there is no safe
	 * default outcome -- guessing would tell the player they won a run they lost.
	 * A run whose outcome cannot be read is treated as having no outcome, which the
	 * end screen and {@code /entropystatus} both handle.
	 */
	public static RunOutcome parse(String name) {
		if (name != null) {
			for (RunOutcome outcome : values()) {
				if (outcome.name().equals(name)) {
					return outcome;
				}
			}
		}
		return null;
	}
}
