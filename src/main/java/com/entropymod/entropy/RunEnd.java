package com.entropymod.entropy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How this run ended and the stats the end screen reports, as one persisted
 * object.
 *
 * <p><b>A persistence shape, not a second source of truth.</b> {@link EntropyManager}
 * owns both of these as ordinary fields and is the only thing that reads or
 * writes them; this record exists so they round-trip as one nested {@code run_end}
 * object. Same role and same reasoning as {@link MetaRunState}, and for the same
 * hard reason: {@code RecordCodecBuilder.Instance.group()} tops out at <b>16</b>
 * fields, and the run codec was already at 14. Two more flat entries would sit
 * exactly on the ceiling with no headroom for the next feature.
 *
 * <p><b>Both fields are {@code optionalFieldOf} with a default, and the whole
 * record is optional too</b>, defaulting to {@link #NONE}. A save written before
 * this existed has no {@code run_end} key and loads as a run that has not ended
 * and recorded no deaths -- which is exactly what it is.
 *
 * <p>The death count lives here rather than beside {@code pick_count} because the
 * only thing that ever surfaces it is the end screen. It is nonetheless counted
 * throughout the run, from the first death onward, and is <em>not</em> reset or
 * frozen when the run ends.
 *
 * <p>Minecraft-import-free, like {@link PickRecord} and {@link MetaRunState}, so
 * the harness round-trips it against the real class with no bootstrap.
 *
 * @param outcome    the {@link RunOutcome} name, or empty while the run has not
 *                   ended. Stored as a string so an outcome written by a newer
 *                   build cannot make an older one fail to load
 * @param deathCount player deaths this run, all causes
 */
public record RunEnd(Optional<String> outcome, int deathCount) {

	/** A run that has not ended and has recorded no deaths. */
	public static final RunEnd NONE = new RunEnd(Optional.empty(), 0);

	public static final Codec<RunEnd> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("outcome").forGetter(RunEnd::outcome),
			Codec.INT.optionalFieldOf("death_count", 0).forGetter(RunEnd::deathCount)
	).apply(instance, RunEnd::new));

	/** The outcome as an enum, or {@code null} if absent or unrecognised. */
	public RunOutcome parsedOutcome() {
		return outcome.map(RunOutcome::parse).orElse(null);
	}

	public static RunEnd of(RunOutcome outcome, int deathCount) {
		return new RunEnd(outcome == null ? Optional.empty() : Optional.of(outcome.name()),
				deathCount);
	}
}
