package com.entropymod.network;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.PickRecord;
import com.entropymod.entropy.RunOutcome;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server -&gt; Client. "The run is over. Here is everything the end screen shows."
 *
 * <p>Sent from {@code EntropyManager.endRun} to every player, once, on the
 * transition into {@code RunState.ENDED}.
 *
 * <p><b>Everything on it is real server state, and none of it is derivable
 * client-side.</b> The client has no entropy sync between picks (see
 * {@code EntropyHud} -- it is a cache, not live state), has never been told the
 * pick history unless it asked, and has never been told the death count at all.
 * So the whole screen is fed from here rather than assembled from what the
 * client happens to remember.
 *
 * <p><b>The history entries reuse {@link HistoryResponsePayload.Entry}</b> rather
 * than declaring a parallel record. That type already carries exactly what a
 * history row needs, already has a verified codec, and already converts from
 * {@link PickRecord} via {@code Entry.from}. Two records with the same six fields
 * would be two codecs to keep in step for no gain.
 *
 * <p><b>Total picks is not a field.</b> It is {@code history.size()} -- the
 * recorded architecture is explicit that the end screen reuses the existing
 * history rather than adding tracking, and a separate count could drift from the
 * list it is supposed to describe.
 *
 * <p>The outcome travels <b>by name</b>, like {@code EffectPhase} and
 * {@code RunState}, so reordering {@link RunOutcome} cannot silently turn a loss
 * into a win on the wire.
 *
 * @param outcome     the {@link RunOutcome} name
 * @param entropy     final entropy
 * @param entropyCap  the cap it was measured against -- sent because the client
 *                    cannot know a non-default cap otherwise, the same reason
 *                    {@code OpenChoicePayload} carries it
 * @param deathCount  player deaths this run, all causes
 * @param history     every pick, oldest first
 */
public record RunEndPayload(
		String outcome,
		int entropy,
		int entropyCap,
		int deathCount,
		List<HistoryResponsePayload.Entry> history
) implements CustomPacketPayload {

	public static final Identifier ID = EntropyMod.id("run_end");
	public static final CustomPacketPayload.Type<RunEndPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	// Three adjacent ints, so the harness round-trips this field by field with
	// distinct values rather than with a record equals -- a swapped pair that
	// cancelled out would pass an equality check. Same hazard OpenChoicePayload
	// records for entropy/entropyCap.
	public static final StreamCodec<ByteBuf, RunEndPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RunEndPayload::outcome,
			ByteBufCodecs.VAR_INT, RunEndPayload::entropy,
			ByteBufCodecs.VAR_INT, RunEndPayload::entropyCap,
			ByteBufCodecs.VAR_INT, RunEndPayload::deathCount,
			HistoryResponsePayload.Entry.CODEC.apply(ByteBufCodecs.list()), RunEndPayload::history,
			RunEndPayload::new
	);

	public static RunEndPayload of(RunOutcome outcome, int entropy, int entropyCap,
								   int deathCount, List<PickRecord> history) {
		return new RunEndPayload(outcome.name(), entropy, entropyCap, deathCount,
				history.stream().map(HistoryResponsePayload.Entry::from).toList());
	}

	/**
	 * The outcome as an enum, or {@code null} if unrecognised.
	 *
	 * <p>Null rather than a fallback, deliberately: there is no safe default
	 * outcome, and guessing would tell the player they won a run they lost. The
	 * end screen renders the unknown case rather than inventing a result.
	 */
	public RunOutcome parsedOutcome() {
		return RunOutcome.parse(outcome);
	}

	/** Total picks made. Derived from the history, never tracked separately. */
	public int pickCount() {
		return history.size();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
