package com.entropymod.entropy.behavior;

import com.entropymod.entropy.EffectContext;
import com.entropymod.entropy.EffectBehavior;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Twin Fate (GOOD / META, entropy 40-50) -- taking it grants <b>both</b> of the
 * other two blessings it was offered alongside.
 *
 * <p>It has no effect of its own on the player. Its whole content is
 * {@link #expandGrant}, the loop-level hook on {@link EffectBehavior}, which turns
 * one clicked card into the full set the pick grants. Everything after that point
 * -- the acquired set, no-repeat, anti-stacking, the history rows, {@code apply}
 * -- runs uniformly over that list, which is what keeps a multi-grant from needing
 * a special case in any of them.
 *
 * <h2>The three properties that were at risk, and how each is held</h2>
 *
 * <ul>
 *   <li><b>No-repeat.</b> Every granted id goes through the same
 *       {@code AcquiredEffects.add}, so all of them are excluded from every future
 *       roll. Nothing is applied "on the side".</li>
 *   <li><b>Entropy and pick count.</b> Both move exactly once, at the end of
 *       {@code onChoiceMade}, outside the loop over granted effects. This hook
 *       cannot reach them. <b>The pick costs 1 entropy and 1 pick, not 3.</b></li>
 *   <li><b>History.</b> One {@code PickRecord} per effect added to the acquired
 *       set, all sharing this pick's number and its {@code entropyAtPick}. That is
 *       a single uniform rule rather than a Twin Fate carve-out: the loop grants a
 *       set, and history records the set. Twin Fate gets a row too, because it is
 *       acquired too -- so the history still shows what the player clicked as well
 *       as what they ended up holding.</li>
 * </ul>
 *
 * <p><b>Twin Fate is itself acquired</b>, which is what subjects it to no-repeat
 * like everything else -- exempting it would make it the only effect in the
 * registry outside the project's hard rule. The cost is that it occupies META for
 * anti-stacking; see {@code EffectRegistry}'s meta block for why that is accepted
 * across all five meta effects rather than worked around here.
 *
 * <h2>Small pools are a normal outcome, not a failure</h2>
 *
 * <p>{@code offeredIds} carries the ids actually rolled, which may be fewer than
 * three -- a partial result is the honest answer when the pool is small and is
 * deliberately not flagged as a fallback. So Twin Fate can grant two effects, one,
 * or in the degenerate case none but itself. It is not treated as an error: the
 * pick still resolves and still costs its entropy. What it must not do is
 * duplicate, hence the {@link LinkedHashSet}.
 *
 * <p>Insertion order is preserved and the chosen id is placed first, so the
 * history rows read in the order the player would describe them ("I took Twin
 * Fate, and got these two").
 *
 * <p>apply()/remove() are empty, but this implements {@link EffectBehavior}
 * directly rather than extending {@code HookEffectBehavior} -- that class's
 * {@code apply} is {@code final}, and keeping the option open costs nothing while
 * making it obvious that this effect's content is not in {@code apply} at all.
 */
public final class TwinFateBehavior implements EffectBehavior {

	public static final String ID = "twin_fate";

	@Override
	public void apply(EffectContext ctx) {
		// Nothing. The grant happened in expandGrant, before anything was acquired.
	}

	@Override
	public void remove(EffectContext ctx) {
		// Nothing to undo -- the effects it granted are ordinary acquired effects
		// and are removed (or not) on their own terms.
	}

	/**
	 * Everything that was offered, with the chosen card first.
	 *
	 * <p>Returns the chosen id alone when nothing else was on the table, so the
	 * caller's contract ("the chosen id is always present") holds in every case.
	 */
	@Override
	public List<String> expandGrant(List<String> offeredIds, String chosenId) {
		LinkedHashSet<String> granted = new LinkedHashSet<>();
		granted.add(chosenId);
		if (offeredIds != null) {
			for (String id : offeredIds) {
				if (id != null && !id.isEmpty()) {
					granted.add(id);
				}
			}
		}
		return new ArrayList<>(granted);
	}
}
