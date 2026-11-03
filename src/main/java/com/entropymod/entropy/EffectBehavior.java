package com.entropymod.entropy;

/**
 * What an effect actually <em>does</em>. One implementation per effect, in
 * {@code com.entropymod.entropy.behavior}, registered by id in
 * {@link EffectBehaviors}.
 *
 * <p>This is the per-effect-class pattern (CLAUDE.md Open Question 9, option b)
 * rather than one big switch: adding effect #47 is a new file plus one line in
 * {@link EffectBehaviors}, and never an edit to an existing behavior.
 *
 * <h2>The contract, now that effects are permanent</h2>
 *
 * <p><b>{@code apply} MUST be idempotent.</b> This is the single most important
 * rule in this interface and the easiest one to get wrong. {@code apply} is
 * called once when the effect is picked and then <em>again every time</em> the
 * player respawns, rejoins, or otherwise has their state rebuilt — an unbounded
 * number of times over a run. Calling it twice must leave the player in exactly
 * the same state as calling it once.
 *
 * <p>Concretely, for attribute modifiers that means
 * {@code addOrUpdateTransientModifier} (replaces by id) and never
 * {@code addTransientModifier} (adds a second copy). {@link AttributeEffectBehavior}
 * already does this correctly — extend it rather than hand-rolling the call.
 * For anything stateful, "set to X" is safe and "increment by X" is not.
 *
 * <p><b>{@code remove} is not an expiry hook any more.</b> Nothing expires.
 * It exists for two narrower cases: tearing an effect down when a run is reset,
 * and being the readable inverse of {@code apply} so the pair documents what the
 * effect touched. It must be safe to call on a player who never had the effect
 * applied — the server can restart mid-run and rebuild state in any order.
 *
 * <p>Both methods receive a specific {@link EffectContext#target()}; do not
 * reach for "the" player.
 *
 * <h2>The two loop-level hooks</h2>
 *
 * <p>{@link #expandGrant} and {@link #onAcquired} exist so a <b>meta</b> effect --
 * one whose content is a change to the pick loop rather than to the player -- can
 * still be one class plus two registration lines. Both have defaults that do
 * nothing, so every effect written before they existed is unaffected and the
 * "adding an effect is exactly three things" rule still holds.
 *
 * <p>They are deliberately <em>not</em> a central switch in {@code EntropyManager}
 * keyed on effect id. That is the same reasoning as {@code apply} itself
 * (Open Question 9, option b): a switch would have to be edited for every new
 * meta effect, which is exactly the fourth-file edit CLAUDE.md calls a signal that
 * the abstraction is missing something.
 */
public interface EffectBehavior {

	/**
	 * Establishes the effect on {@link EffectContext#target()}.
	 *
	 * <p>Called on a fresh pick AND on every respawn/rejoin. See the class javadoc:
	 * this MUST be idempotent. Check {@link EffectContext#isFreshPick()} if you
	 * need to do something once-only (a sound, a one-time grant) — but prefer not
	 * to need it.
	 */
	void apply(EffectContext ctx);

	/**
	 * Undoes exactly what {@link #apply} did, and nothing else. Must tolerate being
	 * called when the effect was never applied.
	 */
	void remove(EffectContext ctx);

	/**
	 * Turns "the player clicked this card" into the full set of effects the pick
	 * grants. Defaults to just the chosen effect, which is every effect but Twin
	 * Fate.
	 *
	 * <p>Called by {@code EntropyManager.onChoiceMade} <b>before</b> anything is
	 * acquired, so an override decides the whole grant in one place rather than
	 * bolting extra acquisitions on afterwards. Everything downstream -- no-repeat,
	 * anti-stacking, the history rows, {@code apply} -- then runs uniformly over
	 * the returned list, which is what keeps a multi-grant from needing special
	 * cases in any of them.
	 *
	 * <p><b>The pick still costs exactly one entropy and one pick count</b>, however
	 * many ids are returned. Those counters move once, at the end of
	 * {@code onChoiceMade}, and this hook cannot reach them.
	 *
	 * @param offeredIds the ids actually rolled for this pick, in the order the
	 *                   cards were built -- may be 1, 2 or 3 entries, since a small
	 *                   pool is a normal outcome and not a fallback
	 * @param chosenId   the card the player clicked; always present in the result
	 * @return the ids to grant. Duplicates and unknown ids are tolerated by the
	 *         caller; ids already acquired are skipped rather than re-applied.
	 */
	default java.util.List<String> expandGrant(java.util.List<String> offeredIds, String chosenId) {
		return java.util.List.of(chosenId);
	}

	/**
	 * Run-level bookkeeping for a meta effect, at the moment it joins
	 * {@code AcquiredEffects}. Defaults to doing nothing.
	 *
	 * <p><b>Called exactly once per effect per run</b>, from both acquisition paths
	 * -- {@code onChoiceMade} and {@code /entropygrant} -- so a granted meta effect
	 * arms itself exactly as a picked one does. That is deliberate and is what makes
	 * the debug command able to test these at all.
	 *
	 * <p>Distinct from {@link #apply}, and the distinction is the reason this exists
	 * rather than being folded into it: {@code apply} runs again on every respawn,
	 * rejoin and dimension change, so it is the wrong place for anything that is not
	 * idempotent. "Bank two reroll charges" is not idempotent. This is called from
	 * the one place that happens once.
	 */
	default void onAcquired(EntropyManager manager) {}
}
