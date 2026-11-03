package com.entropymod.entropy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The run state belonging to the Tier 2 <b>meta</b> effects -- the ones whose
 * content is a change to the pick loop rather than to the player.
 *
 * <p><b>This is a persistence shape, not a second source of truth.</b>
 * {@link EntropyManager} still owns every one of these as an ordinary field and is
 * the only thing that reads or writes them; this record exists solely so they
 * round-trip as one nested {@code meta} object instead of six more entries in
 * {@code EntropyManager.CODEC}'s top-level group.
 *
 * <h2>Why nested rather than flat, which is what every other flag here does</h2>
 *
 * <p>A hard limit, not a preference. {@code RecordCodecBuilder.Instance.group()}
 * has overloads up to <b>16</b> fields and the run codec was already at 13. Six
 * more would not compile, and the failure is at the API's edge rather than
 * anywhere it would be obvious. Grouping them costs one level of nesting in the
 * NBT and buys headroom for the next meta effect.
 *
 * <p><b>Every field is still {@code optionalFieldOf} with a default, and the whole
 * record is optional too</b>, defaulting to {@link #EMPTY}. That is what keeps the
 * usual guarantee intact in both directions: a save written before this record
 * existed has no {@code meta} key at all and loads as a run holding no meta
 * effects, which is exactly what it is; and a future field added inside can be
 * added the same safe way. Making either level <em>required</em> would make every
 * existing save unloadable.
 *
 * <p>Deliberately free of Minecraft imports, like {@link PickRecord} -- DFU's
 * {@code Codec} is not a Minecraft type -- so the headless harness can round-trip
 * it against the real class with no bootstrap.
 *
 * @param rerollCharges          banked rerolls from All In; a separate resource
 *                               from Second Guess's one-shot {@code reroll_used},
 *                               which stays where it is and is untouched
 * @param rerollsForbidden       No Do-Overs. One-way: once set, never cleared, and
 *                               it kills banked charges retroactively rather than
 *                               refunding or zeroing them
 * @param halfEntropyPending     Banked Entropy's carry bit -- true means the next
 *                               pick pays the full +1 and clears it
 * @param doubledEffectId        the one curse All In doubled, or {@code ""}
 * @param doubleNextCursePending All In is armed, waiting for the next bad pick
 * @param extremeTierPending     Debt Collector is armed, waiting for the next bad pick
 */
public record MetaRunState(
		int rerollCharges,
		boolean rerollsForbidden,
		boolean halfEntropyPending,
		String doubledEffectId,
		boolean doubleNextCursePending,
		boolean extremeTierPending
) {
	/** A run holding no meta effects. What a pre-meta save loads as. */
	public static final MetaRunState EMPTY =
			new MetaRunState(0, false, false, "", false, false);

	public static final Codec<MetaRunState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("reroll_charges", 0).forGetter(MetaRunState::rerollCharges),
			Codec.BOOL.optionalFieldOf("rerolls_forbidden", false)
					.forGetter(MetaRunState::rerollsForbidden),
			Codec.BOOL.optionalFieldOf("half_entropy_pending", false)
					.forGetter(MetaRunState::halfEntropyPending),
			// "" rather than an Optional: "absent" and "empty" mean the same thing here
			// (no curse is doubled), so a second representation of it would be a
			// distinction with nothing behind it.
			Codec.STRING.optionalFieldOf("doubled_effect", "")
					.forGetter(MetaRunState::doubledEffectId),
			Codec.BOOL.optionalFieldOf("double_next_curse", false)
					.forGetter(MetaRunState::doubleNextCursePending),
			Codec.BOOL.optionalFieldOf("extreme_tier_pending", false)
					.forGetter(MetaRunState::extremeTierPending)
	).apply(instance, MetaRunState::new));
}
