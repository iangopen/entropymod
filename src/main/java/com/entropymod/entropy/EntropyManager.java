package com.entropymod.entropy;

import com.entropymod.entropy.companion.CompanionRoster;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.behavior.AllInBehavior;
import com.entropymod.entropy.behavior.BankedEntropyBehavior;
import com.entropymod.entropy.behavior.PhoenixChamberedHeartBehavior;
import com.entropymod.entropy.behavior.SecondGuessBehavior;
import com.entropymod.network.ClientEffectsPayload;
import com.entropymod.network.OpenChoicePayload;
import com.entropymod.network.RunEndPayload;
import com.entropymod.network.RunSyncPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Runs the core game loop: every intervalTicks, alternate GOOD/BAD, roll 3
 * choices, and broadcast them. One EntropyManager per world.
 *
 * <p><b>This is now a {@link SavedData}</b>, replacing the old in-memory
 * {@code WeakHashMap<MinecraftServer, EntropyManager>}. That mattered as soon as
 * effects became permanent: in singleplayer, quitting to the title screen stops
 * the integrated server, so without this every acquired effect, the entropy
 * count, and the whole run vanished on the trip through the main menu. Effects
 * surviving a rejoin is a stated requirement, and re-application alone cannot
 * deliver it if the list of what to re-apply is gone.
 *
 * <p>Also owns the state built on top of the loop: {@link AcquiredEffects}
 * (which doubles as the active set, the no-repeat set, and the anti-stacking
 * source), the {@link PickRecord} history, and dispatch into
 * {@link EffectBehaviors}. It never contains per-effect logic itself.
 *
 * <p><b>Every mutation must call {@link #setDirty()}</b> or the change is not
 * written to disk. That is the one easy-to-miss rule in this file.
 */
public class EntropyManager extends SavedData {
	// 20 ticks/sec * 60 sec * 3 min = 3600 ticks
	public static final int DEFAULT_INTERVAL_TICKS = 3600;
	public static final int DEFAULT_ENTROPY_CAP = 100;

	/**
	 * Public so a headless harness can round-trip it against the real class. A
	 * broken codec here is not a subtle bug -- it makes the world fail to load --
	 * so it gets the same encode/decode verification the network payloads do.
	 *
	 * <p>Every field is {@code optionalFieldOf} with a default. That is what lets a
	 * save written by an older build load in a newer one that added a field, and it
	 * is why adding a field here is a safe, backward-compatible change. Adding a
	 * <em>required</em> field would make every existing save unloadable.
	 */
	public static final Codec<EntropyManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("entropy", 0).forGetter(m -> m.entropy),
			Codec.INT.optionalFieldOf("pick_count", 0).forGetter(m -> m.pickCount),
			// LEGACY, read-only. This key was the whole of "the run is over" before
			// RunState.ENDED existed. It is still READ so a save written by an older
			// build migrates (see the codec constructor), and always WRITTEN false so
			// it drains out of saves rather than lingering as a second answer to a
			// question RunState now owns. Removing the line entirely would silently
			// revive every already-finished run as IN_PROGRESS on its next load.
			Codec.BOOL.optionalFieldOf("game_over", false).forGetter(m -> false),
			Codec.INT.optionalFieldOf("entropy_cap", DEFAULT_ENTROPY_CAP).forGetter(m -> m.entropyCap),
			Codec.INT.optionalFieldOf("interval_ticks", DEFAULT_INTERVAL_TICKS).forGetter(m -> m.intervalTicks),
			Codec.STRING.listOf().optionalFieldOf("acquired", List.of())
					.forGetter(m -> List.copyOf(m.acquired.ids())),
			PickRecord.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(m -> m.history),
			Codec.BOOL.optionalFieldOf("reroll_used", false).forGetter(m -> m.rerollUsed),
			Codec.STRING.optionalFieldOf("move_scramble", "").forGetter(m -> m.moveScramble),
			// Phoenix Chambered Heart's one-shot, in the SAME store as Second Guess's.
			// See consumePhoenixHeart for why the flag rather than the acquired set is
			// what enforces "once". Position matches the constructor parameter order.
			// The KEY keeps its pre-rename spelling on purpose -- changing it would
			// hand a fresh save back to every world that had already spent one.
			Codec.BOOL.optionalFieldOf("second_chance_used", false)
					.forGetter(m -> m.phoenixHeartUsed),
			// Optional WITHOUT a default, unlike every other field here, so the codec
			// constructor can tell "this save predates run states" from "this save
			// says NOT_STARTED" and migrate the first case. See the constructor.
			Codec.STRING.optionalFieldOf("run_state").forGetter(m -> Optional.of(m.runState.name())),
			Codec.unboundedMap(Codec.STRING, KeybindSnapshot.CODEC)
					.optionalFieldOf("keybind_snapshots", Map.of())
					.forGetter(m -> Map.copyOf(m.keybindSnapshots)),
			// Which entities each companion effect has already spawned, keyed
			// "playerUuid/effectId". Persisted so the run knows what it created across
			// a reload -- NOT so it can decide whether to spawn: that is
			// EffectContext.isFreshPick()'s job, and CompanionRoster records why a
			// liveness-based top-up would duplicate on every relog.
			Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
					.optionalFieldOf("companions", Map.of())
					.forGetter(m -> m.companions.asMap()),
			// The Tier 2 meta effects' run state, nested as one object.
			//
			// Nested rather than six more flat fields because group() tops out at 16
			// and this codec was already at 13 -- see MetaRunState for the full note.
			// Still optionalFieldOf with a default at BOTH levels, so a save written
			// before the meta effects existed loads as a run holding none of them.
			MetaRunState.CODEC.optionalFieldOf("meta", MetaRunState.EMPTY).forGetter(m -> m.meta()),
			// How the run ended, and the death count the end screen reports. Nested
			// for the same hard reason MetaRunState is: group() tops out at 16 and
			// this codec was at 14. See RunEnd.
			RunEnd.CODEC.optionalFieldOf("run_end", RunEnd.NONE).forGetter(m -> m.runEnd())
	).apply(instance, EntropyManager::new));

	private static final SavedDataType<EntropyManager> TYPE = new SavedDataType<>(
			EntropyMod.id("entropy_run"),
			EntropyManager::new,
			CODEC,
			DataFixTypes.LEVEL);

	/**
	 * Stored on the overworld's data storage -- the run is a property of the world,
	 * not of a dimension, so it must not be looked up from whichever level the
	 * caller happens to hold or the Nether would get its own separate run.
	 */
	public static EntropyManager get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private final Random random = new Random();
	private final AcquiredEffects acquired = new AcquiredEffects();
	private final List<PickRecord> history = new ArrayList<>();

	private int entropy = 0;
	private int pickCount = 0;
	/**
	 * How the run ended, or {@code null} while it has not.
	 *
	 * <p>Always {@code null} unless {@link #runState} is {@link RunState#ENDED},
	 * and never null when it is -- {@link #endRun} is the only writer of either
	 * and sets both together, so the pair cannot disagree.
	 */
	private RunOutcome outcome = null;

	/**
	 * Player deaths this run, all causes.
	 *
	 * <p>Counted from the first death onward and <b>not</b> frozen when the run
	 * ends -- it is a stat about the run, and the run ending does not stop the
	 * world. Surfaced only on the end screen and by {@code /entropystatus}.
	 */
	private int deathCount = 0;

	/**
	 * Second Guess's one-shot reroll, spent for the whole run.
	 *
	 * <p><b>Persisted here deliberately, in the same codec as everything else.</b>
	 * A separate store for one boolean would be a second source of truth about the
	 * run, which is the thing this class exists to prevent. Because it lives on the
	 * run rather than on the effect, re-acquiring Second Guess cannot refund it.
	 */
	private boolean rerollUsed = false;

	/**
	 * Randomized Movement Controls' permutation, or "" while unassigned.
	 *
	 * <p>Persisted for the same reason as {@link #rerollUsed}: it is state that
	 * belongs to the <em>run</em>, and a scramble that changed on respawn would be
	 * a different (and unlearnable) effect. Assigned exactly once, by
	 * {@code RandomizedControlsBehavior} on first apply, and never rewritten --
	 * see {@link #assignMoveScrambleIfAbsent}.
	 */
	private String moveScramble = "";

	/**
	 * Banked rerolls, from All In. <b>A separate counter from
	 * {@link #rerollUsed}, and deliberately not a replacement for it.</b>
	 *
	 * <p>Second Guess's one-shot is a property of owning that effect; this is a
	 * spendable resource that exists whether or not the player has ever held Second
	 * Guess. Merging them would mean one number answering two different questions
	 * -- the exact shape {@link RerollState} exists to avoid on the rendering side.
	 *
	 * <p>Spent <b>before</b> Second Guess by {@link #requestReroll}, and
	 * {@link #rerollState} reports {@code CHARGED} while any remain so the button
	 * never advertises one resource and consumes another.
	 */
	private int rerollCharges = 0;

	/**
	 * No Do-Overs. Once true, never false again -- the only one-way flag in this
	 * class.
	 *
	 * <p>Checked first in {@link #rerollState} and {@link #requestReroll}, which is
	 * what makes it retroactive: {@link #rerollCharges} banked before it was set are
	 * left in place and simply become unreachable. Zeroing them instead would look
	 * equivalent and would destroy the run's ability to tell "never had charges"
	 * from "had them taken".
	 */
	private boolean rerollsForbidden = false;

	/**
	 * Banked Entropy's carry bit: true means the next pick pays the full +1 and
	 * clears it, false means the next pick is free and sets it.
	 *
	 * <p>Only consulted while {@link BankedEntropyBehavior#ID} is acquired, so a run
	 * without the effect never touches it. See that class for why the carry is
	 * run-wide rather than scoped to the effect's own pick -- the short version is
	 * that no-repeat makes a second Banked Entropy pick impossible, so a flag only
	 * that pick could clear would never be cleared at all.
	 */
	private boolean halfEntropyPending = false;

	/**
	 * The one effect All In has doubled, or {@code ""}.
	 *
	 * <p>Read by {@link #severityMultiplierFor} on <em>every</em> apply and
	 * re-apply, which is what keeps the doubling idempotent: the amount is derived
	 * from this persisted id rather than baked into a modifier, so a respawn
	 * recomputes the same doubled value and replaces rather than stacks.
	 */
	private String doubledEffectId = "";

	/** All In is armed and waiting for the next bad pick to mark. */
	private boolean doubleNextCursePending = false;

	/** Debt Collector is armed and waiting for the next bad pick to force. */
	private boolean extremeTierPending = false;

	/**
	 * Phoenix Chambered Heart's one-shot, spent for the whole run.
	 *
	 * <p>Deliberately the same shape and the same store as {@link #rerollUsed}
	 * rather than a parallel mechanism. Because it lives on the run, re-acquiring
	 * the effect cannot refund it -- which the acquired set alone could not
	 * guarantee, since the repeat fallback can re-offer an already-taken effect
	 * once a phase's pool empties.
	 *
	 * <p><b>The persisted key stays {@code second_chance_used}</b>, from before the
	 * effect was renamed. Renaming it would refund the save in every world that had
	 * already spent one: {@code optionalFieldOf} would find no value under the new
	 * name and default to false. The field name is cosmetic, the key is not.
	 */
	private boolean phoenixHeartUsed = false;

	/**
	 * Where the run is in its lifecycle. <b>Gates the whole tick loop</b> -- see
	 * {@link #tick}.
	 *
	 * <p>All three states exist. {@code ENDED} <b>replaced</b> the old
	 * {@code gameOver} boolean rather than sitting beside it -- for several
	 * sessions this class carried two unreconciled notions of "the run is over",
	 * and there is now exactly one. {@link #endRun} is the only writer.
	 *
	 * <p><b>This is the ONLY per-tick system in the mod that consults the run
	 * state.</b> The eight effect services do not, deliberately: they implement
	 * effects the player permanently holds, and attribute- and mixin-driven
	 * effects cannot be gated at all. See CLAUDE.md's run-lifecycle section.
	 */
	private RunState runState = RunState.NOT_STARTED;

	/**
	 * Each player's movement keys as they were bound when the run started, keyed
	 * by player UUID string.
	 *
	 * <p><b>Client-captured, server-persisted.</b> Only the client can read
	 * keybinds, and only the server has durable per-world storage -- there is no
	 * client-side equivalent of {@link SavedData} in this version, so this is
	 * where a per-world snapshot can actually live. It is sent back to its owner
	 * on {@code RunSyncPayload}. See CLAUDE.md for the investigation.
	 *
	 * <p>Keyed by UUID <em>string</em> rather than {@link java.util.UUID} so the
	 * codec needs nothing but {@code Codec.STRING}, and so this class and
	 * {@link KeybindSnapshot} stay drivable from the headless harness.
	 *
	 * <p>Write-once per player: see {@link #storeKeybindSnapshotIfAbsent}.
	 */
	private final Map<String, KeybindSnapshot> keybindSnapshots = new HashMap<>();

	/**
	 * Companion entities this run has spawned, keyed "playerUuid/effectId".
	 *
	 * <p>Read by {@code CompanionService} to find the escort. It is deliberately
	 * NOT consulted to decide whether to spawn -- see {@link CompanionRoster}.
	 */
	private CompanionRoster companions = new CompanionRoster();

	// Config, settable at world creation (wire up to a config screen later).
	private int intervalTicks = DEFAULT_INTERVAL_TICKS;
	private int entropyCap = DEFAULT_ENTROPY_CAP;

	/**
	 * Not persisted. Restarting the interval on load is deliberate: saving a
	 * partial countdown would mean a player who reloads repeatedly could sit just
	 * below the threshold forever, and losing at most one interval of progress on
	 * load is a far better failure than that.
	 */
	private int tickCounter = 0;

	/** Also not persisted -- a pending choice cannot survive the client that was answering it. */
	private boolean waitingOnChoice = false;

	/**
	 * The effect ids actually rolled for the pending pick, in card order.
	 *
	 * <p><b>Not persisted, for exactly {@link #waitingOnChoice}'s reason</b> -- a
	 * pending offer cannot outlive the client that was answering it, and a
	 * half-remembered one would be worse than none.
	 *
	 * <p>New with Twin Fate, which is the first effect that needs to know what it
	 * was offered <em>alongside</em>. It also lets {@link #onChoiceMade} reject a
	 * submission naming an effect that was never on the table; the real screen can
	 * only ever send one of the three, so that is a guard against a hand-rolled
	 * client rather than against the game.
	 *
	 * <p>Holds {@code roll.choices()} rather than the payload's three slots.
	 * {@code OpenChoicePayload.fromChoices} pads a short roll by repeating a card,
	 * and the honest list is the one the roll produced.
	 */
	private final List<String> pendingChoiceIds = new ArrayList<>();

	public EntropyManager() {}

	/** Codec constructor. */
	private EntropyManager(int entropy, int pickCount, boolean legacyGameOver, int entropyCap, int intervalTicks,
						   List<String> acquiredIds, List<PickRecord> history, boolean rerollUsed,
						   String moveScramble, boolean phoenixHeartUsed, Optional<String> runState,
						   Map<String, KeybindSnapshot> keybindSnapshots,
						   Map<String, List<String>> companions,
						   MetaRunState meta, RunEnd runEnd) {
		this.companions = new CompanionRoster(companions);
		this.rerollCharges = Math.max(0, meta.rerollCharges());
		this.rerollsForbidden = meta.rerollsForbidden();
		this.halfEntropyPending = meta.halfEntropyPending();
		// An id this build no longer defines is dropped rather than carried, the same
		// treatment an unknown id in the acquired set gets -- a doubled effect that
		// cannot be resolved would make severityMultiplierFor compare against a
		// string nothing can ever match.
		this.doubledEffectId =
				EffectRegistry.byId(meta.doubledEffectId()) != null ? meta.doubledEffectId() : "";
		this.doubleNextCursePending = meta.doubleNextCursePending();
		this.extremeTierPending = meta.extremeTierPending();
		this.entropy = entropy;
		this.pickCount = pickCount;
		this.deathCount = Math.max(0, runEnd.deathCount());
		this.outcome = runEnd.parsedOutcome();
		this.entropyCap = entropyCap;
		this.intervalTicks = intervalTicks;
		acquiredIds.forEach(this.acquired::add);
		this.history.addAll(history);
		this.rerollUsed = rerollUsed;
		this.phoenixHeartUsed = phoenixHeartUsed;
		// A malformed value from a hand-edited or newer save degrades to vanilla
		// controls rather than reaching input handling and throwing every tick.
		this.moveScramble = MovementScramble.isValid(moveScramble) ? moveScramble : "";

		// Migration for saves written before run states existed. Such a save has no
		// "run_state" field at all, and defaulting it to NOT_STARTED would re-gate a
		// run that was already going and demand the player click Start on a world
		// mid-run.
		//
		// pickCount is the right signal and the only reliable one: it moves ONLY in
		// onChoiceMade, i.e. only after a pick was actually offered and answered. It
		// is deliberately not inferred from the acquired set, because /entropygrant
		// adds effects without advancing the run and works while NOT_STARTED -- a
		// granted effect must not be mistaken for a started run.
		RunState parsed = runState
				.map(RunState::parse)
				.orElseGet(() -> pickCount > 0 ? RunState.IN_PROGRESS : RunState.NOT_STARTED);

		// SECOND migration, and it is the one that would have been easy to miss.
		//
		// Before RunState.ENDED existed, a run that hit the entropy cap was recorded
		// as game_over = true while run_state stayed IN_PROGRESS -- the two concepts
		// were never reconciled. Reading run_state alone would therefore load every
		// already-finished run as live, and it would immediately re-fire the cap
		// check and re-announce an ending the player already saw.
		//
		// The legacy flag wins over an IN_PROGRESS state for that reason, and only
		// in that direction: it can end a run, never restart one.
		if (legacyGameOver && parsed != RunState.NOT_STARTED) {
			parsed = RunState.ENDED;
		}
		this.runState = parsed;

		// An ENDED run with no recorded outcome is exactly what a migrated legacy
		// save looks like: game_over only ever meant the cap. Filling it in here
		// keeps the "ENDED implies an outcome" invariant true for old saves too,
		// so the end screen never has to render a run that ended for no reason.
		if (this.runState == RunState.ENDED && this.outcome == null) {
			this.outcome = RunOutcome.LOSS_ENTROPY_CAP;
		}

		keybindSnapshots.forEach((uuid, snapshot) -> {
			if (snapshot != null && snapshot.isPresent()) {
				this.keybindSnapshots.put(uuid, snapshot);
			}
		});
	}

	// ------------------------------------------------------------------
	// Loop
	// ------------------------------------------------------------------

	/**
	 * The heartbeat. <b>Hard-gated on {@link RunState#IN_PROGRESS}</b>: while the
	 * run has not been started, this does not count, does not advance entropy and
	 * cannot open a pick.
	 *
	 * <p>The gate is placed <em>before</em> {@code tickCounter++}, not merely
	 * before {@code triggerPick}. That is the difference between "the loop is
	 * paused" and "the loop is running invisibly and fires the instant the gate
	 * lifts": counting while gated would make a player who spent ten minutes on
	 * the start panel eat an immediate pick on their first second of play.
	 */
	public void tick(MinecraftServer server) {
		if (runState != RunState.IN_PROGRESS) {
			return;
		}
		// The ENDED half of this gate used to be a separate `gameOver` boolean. It
		// is gone: RunState.ENDED is not IN_PROGRESS, so the check above already
		// covers it and there is exactly one notion of "the run is over" left.
		//
		// This is the ONLY per-tick system that checks the run state. The eight
		// effect services in EntropyMod's tick block deliberately do not -- they
		// implement effects the player permanently holds, and attribute- and
		// mixin-driven effects cannot be gated the same way, so gating only the
		// tick-driven subset would make a player's permanent effects half-work.
		// See CLAUDE.md's run-lifecycle section.
		if (waitingOnChoice) {
			return;
		}
		tickCounter++;
		if (tickCounter >= intervalTicks) {
			tickCounter = 0;
			triggerPick(server);
		}
	}

	/**
	 * Why a pick attempt did or didn't open. Returned so callers that aren't the
	 * tick loop -- currently only {@code /entropyforcepick} -- can report the
	 * reason instead of silently doing nothing. {@link #tick} ignores it.
	 */
	public enum PickTrigger {
		OPENED,
		RUN_ALREADY_OVER,
		CHOICE_PENDING,
		RUN_ENDED_NOW,
		NO_ELIGIBLE_EFFECTS,
		/** The run has not been started yet -- the start panel has not been answered. */
		RUN_NOT_STARTED
	}

	/**
	 * Opens a pick right now instead of waiting out the interval. The real path,
	 * not a debug imitation of it -- see CLAUDE.md's debug-command table.
	 */
	public PickTrigger forcePick(MinecraftServer server) {
		// Gated exactly like tick(): forcePick bypasses the CLOCK and nothing else,
		// so it must not be a way to reach a state a real interval firing could not.
		// Two distinct refusals, not one: "you have not started" and "it is over"
		// are different things to report, which is the whole reason PickTrigger
		// exists rather than a boolean.
		if (runState == RunState.ENDED) {
			return PickTrigger.RUN_ALREADY_OVER;
		}
		if (runState != RunState.IN_PROGRESS) {
			return PickTrigger.RUN_NOT_STARTED;
		}
		if (waitingOnChoice) {
			return PickTrigger.CHOICE_PENDING;
		}
		tickCounter = 0;
		return triggerPick(server);
	}

	private PickTrigger triggerPick(MinecraftServer server) {
		if (entropy >= entropyCap) {
			// Path 1 of the three into ENDED. This used to set a `gameOver` boolean
			// that nothing else knew about; it now goes through the same endRun the
			// dragon-death win uses, so both conditions produce one kind of ending
			// -- one broadcast, one end screen, one persisted outcome.
			endRun(server, RunOutcome.LOSS_ENTROPY_CAP);
			return PickTrigger.RUN_ENDED_NOW;
		}

		EffectPhase phase = currentPhase();
		Set<EffectCategory> occupied = acquired.occupiedCategories();
		EffectRegistry.PoolOverride override = poolOverrideFor(phase);
		EffectRegistry.RollResult roll =
				EffectRegistry.roll(phase, entropy, random, acquired.ids(), occupied, override);
		if (override != EffectRegistry.PoolOverride.NONE) {
			EntropyMod.LOGGER.info("Forced pool {} in effect for pick #{} -- the entropy window is "
					+ "overridden for this pick only.", override, pickCount + 1);
		}

		if (roll.repeatFallback()) {
			EntropyMod.LOGGER.warn(
					"[no-repeat] Every eligible {} effect at entropy {} has already been picked -- "
							+ "falling back to re-offering one. PLACEHOLDER policy (CLAUDE.md Open Question 6); "
							+ "the real fix is more content. Acquired: {}",
					phase, entropy, acquired);
		}
		if (roll.categoryFallback()) {
			EntropyMod.LOGGER.warn(
					"[anti-stacking] Every remaining {} effect at entropy {} is in an already-occupied "
							+ "category {} -- offering a colliding effect. PLACEHOLDER policy.",
					phase, entropy, occupied);
		}

		List<EffectDefinition> choices = roll.choices();
		if (choices.isEmpty()) {
			EntropyMod.LOGGER.warn("No eligible {} effects at entropy {} -- add more to EffectRegistry!",
					phase, entropy);
			return PickTrigger.NO_ELIGIBLE_EFFECTS;
		}

		waitingOnChoice = true;
		pendingChoiceIds.clear();
		for (EffectDefinition choice : choices) {
			pendingChoiceIds.add(choice.id());
		}
		EntropyMod.LOGGER.info("Pick #{}: phase={} entropy={} occupied={} choices={}",
				pickCount + 1, phase, entropy, occupied, choices);

		// rerollState() is read AFTER waitingOnChoice is set and after requestReroll
		// has already spent the reroll -- see that method for why the ordering there
		// matters to what this sends.
		OpenChoicePayload payload = OpenChoicePayload.fromChoices(
				phase, entropy, entropyCap, rerollState(), rerollCharges, choices);
		for (ServerPlayer player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, payload);
		}
		return PickTrigger.OPENED;
	}

	/**
	 * True if Second Guess can be spent right now: the effect is owned, the reroll
	 * is unspent, and there is actually a pending offer to replace. Sent to the
	 * client on {@code OpenChoicePayload} so the screen knows whether to show the
	 * button; re-checked server-side in {@link #requestReroll} because a client can
	 * send anything.
	 */
	public boolean isRerollAvailable() {
		return waitingOnChoice && !rerollsForbidden && !rerollUsed
				&& acquired.contains(SecondGuessBehavior.ID);
	}

	/**
	 * True if a banked {@link #rerollCharges} can be spent right now. The
	 * charge-based counterpart to {@link #isRerollAvailable}, and an authorisation
	 * question in exactly the same sense -- re-checked server-side, never trusted
	 * from the wire.
	 */
	public boolean isChargeRerollAvailable() {
		return waitingOnChoice && !rerollsForbidden && rerollCharges > 0;
	}

	/** True if any reroll at all can be spent -- charge or Second Guess. */
	public boolean isAnyRerollAvailable() {
		return isChargeRerollAvailable() || isRerollAvailable();
	}

	public int getRerollCharges() { return rerollCharges; }

	public boolean areRerollsForbidden() { return rerollsForbidden; }

	/**
	 * What the client should draw for the Second Guess button, derived from the
	 * same persisted {@code rerollUsed} flag {@link #isRerollAvailable} reads.
	 *
	 * <p><b>Distinct from {@code isRerollAvailable} on purpose, and not a
	 * replacement for it.</b> This answers a rendering question and is sent on the
	 * wire; that one answers an authorisation question and is re-checked
	 * server-side in {@link #requestReroll}, because a client can send anything.
	 * A client told {@code SPENT} that sends a reroll request anyway is still
	 * rejected.
	 *
	 * <p>Deliberately omits the {@code waitingOnChoice} term the authorisation
	 * check has: this is only ever read while building a pending pick's payload, so
	 * a pick is pending by construction, and leaving it out keeps the state a pure
	 * function of the run rather than of the moment it was asked.
	 */
	public RerollState rerollState() {
		boolean owned = acquired.contains(SecondGuessBehavior.ID);
		// No Do-Overs first, and that ordering IS the retroactive guarantee: a charge
		// banked before it was picked is still counted here, and still reported as
		// forbidden rather than as spendable.
		if (rerollsForbidden) {
			return (owned || rerollCharges > 0) ? RerollState.FORBIDDEN : RerollState.NOT_OWNED;
		}
		// Charges outrank an unspent Second Guess, matching the order requestReroll
		// spends them in. If this reported AVAILABLE while a charge was queued to be
		// spent first, the button would name one resource and consume another.
		if (rerollCharges > 0) {
			return RerollState.CHARGED;
		}
		if (!owned) {
			return RerollState.NOT_OWNED;
		}
		return rerollUsed ? RerollState.SPENT : RerollState.AVAILABLE;
	}

	/**
	 * Spends Second Guess: throws away the pending three options and rolls three
	 * more.
	 *
	 * <p><b>Delegates to {@link #triggerPick} rather than rolling here.</b> That is
	 * the same rule {@code /entropyforcepick} follows and for the same reason -- a
	 * reroll that built its own choices would stop exercising no-repeat,
	 * anti-stacking, the fallback warnings and the payload construction, and would
	 * silently drift from the real path. This method contains exactly one call to
	 * {@code triggerPick} and no roll logic; that is verifiable in its bytecode.
	 *
	 * <p><b>It does not advance the loop.</b> No entropy is spent, no pick is
	 * counted, no history entry is written and the interval timer is untouched --
	 * all of that happens in {@link #onChoiceMade}, which a reroll never reaches.
	 *
	 * <p>The reroll is consumed only if new choices actually opened, so a roll that
	 * produces nothing (an exhausted pool, or the run ending on this tick) leaves
	 * the player their reroll rather than eating it for nothing.
	 *
	 * @return true if new choices were sent
	 */
	public boolean requestReroll(MinecraftServer server) {
		// No Do-Overs is checked before anything else, and before either resource is
		// inspected, so it cannot be reached around by whichever one happens to be
		// available. It is also the only branch here that can be true without a pick
		// pending, which is why it logs rather than returning silently.
		if (rerollsForbidden) {
			EntropyMod.LOGGER.info("Reroll refused -- No Do-Overs forbids every reroll for the rest "
					+ "of the run ({} charge(s) banked and unusable, Second Guess {}).",
					rerollCharges, rerollUsed ? "already spent" : "unspent");
			return false;
		}
		if (isChargeRerollAvailable()) {
			return spendRerollCharge(server);
		}
		return spendSecondGuess(server);
	}

	/**
	 * Spends one banked {@link #rerollCharges}.
	 *
	 * <p>Same shape and same ordering rule as {@link #spendSecondGuess}: the charge
	 * is deducted <em>before</em> {@link #triggerPick} builds the replacement
	 * payload, because that payload reads {@link #rerollState} and would otherwise
	 * advertise a charge that had just been spent -- the exact bug Second Guess's
	 * button had. Refunded if nothing opened.
	 */
	private boolean spendRerollCharge(MinecraftServer server) {
		rerollCharges--;

		PickTrigger result = triggerPick(server);
		if (result != PickTrigger.OPENED) {
			rerollCharges++;
			EntropyMod.LOGGER.warn("Banked reroll produced no new choices ({}) -- not consumed.", result);
			return false;
		}
		setDirty();
		EntropyMod.LOGGER.info("Banked reroll spent -- pick #{} rerolled, {} charge(s) left. "
				+ "No entropy or pick consumed.", pickCount + 1, rerollCharges);
		return true;
	}

	/**
	 * Second Guess's reroll, unchanged from the version that shipped -- the guard,
	 * the spend-then-refund ordering and the delegation to {@link #triggerPick} are
	 * all as they were. It is reached only once banked charges are exhausted.
	 */
	private boolean spendSecondGuess(MinecraftServer server) {
		if (!isRerollAvailable()) {
			return false;
		}

		// Spend the reroll BEFORE rolling, then refund it if nothing opened.
		//
		// This ordering is load-bearing, not stylistic. triggerPick builds the
		// OpenChoicePayload from rerollState(), which reads this flag -- so if the
		// flag were set afterwards, the replacement screen would advertise a reroll
		// that had just been spent, and the button would come back live on the very
		// screen the reroll produced. That was a real, reported bug.
		rerollUsed = true;

		PickTrigger result = triggerPick(server);
		if (result != PickTrigger.OPENED) {
			rerollUsed = false;
			EntropyMod.LOGGER.warn("Second Guess reroll produced no new choices ({}) -- not consumed.", result);
			return false;
		}
		setDirty();
		EntropyMod.LOGGER.info("Second Guess spent -- pick #{} rerolled. No entropy or pick consumed.",
				pickCount + 1);
		return true;
	}

	// ------------------------------------------------------------------
	// Meta-effect run state
	//
	// These are called from EffectBehavior.onAcquired, not from apply() -- apply
	// runs again on every respawn, rejoin and dimension change, and "bank two
	// charges" is not idempotent. onAcquired runs exactly once per run, from both
	// acquisition paths.
	// ------------------------------------------------------------------

	/** All In: bank charges and arm the next bad pick for doubling. */
	public void armAllIn(int charges) {
		rerollCharges += charges;
		doubleNextCursePending = true;
		setDirty();
		EntropyMod.LOGGER.info("All In armed: {} reroll charge(s) banked ({} total), and the next "
				+ "bad pick will be drawn from the scalable pool and doubled.", charges, rerollCharges);
	}

	/** Debt Collector: force the next bad roll to the most extreme tier defined. */
	public void armDebtCollector() {
		extremeTierPending = true;
		setDirty();
		EntropyMod.LOGGER.info("Debt Collector armed: the next bad pick will be drawn from {}.",
				EffectRegistry.extremeTier(EffectPhase.BAD));
	}

	/** No Do-Overs: end every kind of reroll, permanently and retroactively. */
	public void forbidRerolls() {
		rerollsForbidden = true;
		setDirty();
		EntropyMod.LOGGER.info("No Do-Overs: rerolls are forbidden for the rest of the run. "
				+ "{} banked charge(s) are now unusable and are NOT refunded.", rerollCharges);
	}

	/**
	 * Which pool override, if any, the next roll of this phase should use.
	 *
	 * <p><b>All In outranks Debt Collector when both are pending, and the tie-break
	 * matters.</b> The extreme tier contains no scalable effect, so letting Debt
	 * Collector win would silently void All In's doubling with nothing to show for
	 * it; a Debt Collector flag that waits one more bad pick loses nothing. The rule
	 * is "consume the flag that can still be honoured".
	 */
	private EffectRegistry.PoolOverride poolOverrideFor(EffectPhase phase) {
		if (phase != EffectPhase.BAD) {
			return EffectRegistry.PoolOverride.NONE;
		}
		if (doubleNextCursePending) {
			return EffectRegistry.PoolOverride.SCALABLE_SEVERITY;
		}
		if (extremeTierPending) {
			return EffectRegistry.PoolOverride.EXTREME_TIER;
		}
		return EffectRegistry.PoolOverride.NONE;
	}

	/**
	 * How strongly an effect should land: {@link AllInBehavior#SEVERITY_MULTIPLIER}
	 * for the one curse All In marked, {@code 1.0} for everything else.
	 *
	 * <p>Passed into every {@link EffectContext} this class builds, so it is applied
	 * identically on a fresh pick and on every re-application after a respawn. That
	 * is what keeps the doubling idempotent -- the amount is recomputed from
	 * persisted state each time rather than remembered on the modifier.
	 */
	public double severityMultiplierFor(String effectId) {
		return doubledEffectId.equals(effectId) ? AllInBehavior.SEVERITY_MULTIPLIER : 1.0;
	}

	/**
	 * This run's meta state, packaged for the codec.
	 *
	 * <p>Built on demand rather than stored, so {@link MetaRunState} cannot become a
	 * second copy of these fields that drifts from the ones the loop actually reads.
	 * The fields are the truth; this is a view of them.
	 */
	public MetaRunState meta() {
		return new MetaRunState(rerollCharges, rerollsForbidden, halfEntropyPending,
				doubledEffectId, doubleNextCursePending, extremeTierPending);
	}

	/** The effect All In doubled, or {@code ""} if none. */
	public String getDoubledEffectId() { return doubledEffectId; }

	public boolean isDoubleNextCursePending() { return doubleNextCursePending; }

	public boolean isExtremeTierPending() { return extremeTierPending; }

	public boolean isHalfEntropyPending() { return halfEntropyPending; }

	/** The ids on the cards of the pending pick, in card order. Empty if none is pending. */
	public List<String> getPendingChoiceIds() {
		return Collections.unmodifiableList(pendingChoiceIds);
	}

	/**
	 * The movement scramble for this run, or {@code ""} if never assigned.
	 * Sent to the client on {@code ClientEffectsPayload}; input handling is
	 * client-side, so the client is where it is actually used.
	 */
	public String getMoveScramble() {
		return moveScramble;
	}

	/**
	 * Assigns the movement scramble once, and only once.
	 *
	 * <p><b>Idempotent by construction</b>, which is what makes it safe to call
	 * from {@code EffectBehavior.apply} -- that runs again on every respawn,
	 * rejoin and dimension change, and re-rolling there would give the player a
	 * different scramble every death. Returns true only on the assigning call.
	 */
	public boolean assignMoveScrambleIfAbsent() {
		if (MovementScramble.isValid(moveScramble)) {
			return false;
		}
		moveScramble = MovementScramble.random(random);
		setDirty();
		EntropyMod.LOGGER.info("Randomized Movement Controls: scramble for this run is {} (input order {})",
				moveScramble, MovementScramble.ORDER);
		return true;
	}

	// ------------------------------------------------------------------
	// Run lifecycle
	// ------------------------------------------------------------------

	/**
	 * Ends the run: {@code IN_PROGRESS -> ENDED}, with the outcome recorded.
	 *
	 * <p><b>The single entry point for every path into {@link RunState#ENDED}.</b>
	 * The entropy cap, the dragon-death win and (later) Become Hardcore all call
	 * this rather than each setting their own flag -- which is exactly the mistake
	 * the retired {@code gameOver} boolean represented, two unreconciled notions of
	 * "the run is over" that never learned about each other.
	 *
	 * <p><b>Idempotent, and that is a correctness property rather than a nicety.</b>
	 * Two conditions can fire on the same tick -- a dragon killed on the interval
	 * where entropy caps out -- and whichever lands first must stand. A second call
	 * returns false and changes nothing, so a win can never be overwritten by a
	 * loss or the reverse.
	 *
	 * <p>Three things happen besides the state change, and each is load-bearing:
	 *
	 * <ul>
	 *   <li><b>Any pending pick is voided.</b> {@code waitingOnChoice} is cleared
	 *       and the offer forgotten, so the modal choice screen a player may be
	 *       sitting on cannot be answered into a finished run. The end screen
	 *       replaces it client-side.</li>
	 *   <li><b>The end screen is pushed to every player</b>, carrying real run
	 *       state rather than anything the client could guess.</li>
	 *   <li><b>{@code syncRunToAll}</b>, so the client's cached {@link RunState}
	 *       matches -- otherwise a client would still believe the run was live.</li>
	 * </ul>
	 *
	 * <p><b>What it deliberately does NOT do: stop anything but the loop.</b> No
	 * effect is removed, no acquired id dropped, no per-tick effect service
	 * disabled. The world stays fully playable by decision, and effects are
	 * permanent. See CLAUDE.md for why gating the effect services would make a
	 * player's permanent effects half-work.
	 *
	 * @return true only on the call that performed the transition
	 */
	public boolean endRun(MinecraftServer server, RunOutcome outcome) {
		if (runState == RunState.ENDED) {
			EntropyMod.LOGGER.info("Run already ended as {} -- {} ignored, the first outcome stands.",
					this.outcome, outcome);
			return false;
		}
		if (outcome == null) {
			EntropyMod.LOGGER.error("endRun called with no outcome -- refused, the run is untouched.");
			return false;
		}

		runState = RunState.ENDED;
		this.outcome = outcome;
		// The pending offer dies with the run. Cleared BEFORE anything is sent, so
		// nothing downstream can observe a finished run that is still waiting.
		waitingOnChoice = false;
		pendingChoiceIds.clear();
		setDirty();

		EntropyMod.LOGGER.info("Run ENDED: {} ({}). Entropy {}/{}, {} pick(s), {} death(s).",
				outcome, outcome.isWin() ? "win" : "loss", entropy, entropyCap,
				history.size(), deathCount);

		if (server == null) {
			// Reachable only from the headless harness, which drives the transition
			// without a server. The state change above is the part it is checking.
			return true;
		}

		server.getPlayerList().broadcastSystemMessage(
				Component.literal("[Entropy] " + outcome.headline() + " -- " + outcome.description()),
				false);
		RunEndPayload payload = RunEndPayload.of(outcome, entropy, entropyCap, deathCount, history);
		for (ServerPlayer player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, payload);
		}
		syncRunToAll(server);
		return true;
	}

	/**
	 * Records one player death, any cause.
	 *
	 * <p>Called from the {@code ServerLivingEntityEvents.AFTER_DEATH} listener --
	 * see {@code RunEndDetector} for why that event is the reliable hook and a
	 * hand-rolled {@code LivingEntity.die} mixin would have counted zero.
	 *
	 * <p><b>Counts on every death, in every run state.</b> Not gated on
	 * {@code IN_PROGRESS}: a death before the player clicks Start, or after the run
	 * has ended, is still a death this world saw, and gating it would make the
	 * number mean something subtler than the end screen claims.
	 *
	 * @return the new total
	 */
	public int recordDeath() {
		deathCount++;
		setDirty();
		return deathCount;
	}

	public int getDeathCount() {
		return deathCount;
	}

	/** How the run ended, or {@code null} while it has not. */
	public RunOutcome getOutcome() {
		return outcome;
	}

	public boolean isEnded() {
		return runState == RunState.ENDED;
	}

	/**
	 * This run's ending and stats, packaged for the codec.
	 *
	 * <p>Built on demand rather than stored, exactly like {@link #meta()}, so
	 * {@link RunEnd} cannot become a second copy of fields the loop actually reads.
	 */
	public RunEnd runEnd() {
		return RunEnd.of(outcome, deathCount);
	}

	public RunState getRunState() {
		return runState;
	}

	public boolean isStarted() {
		return runState == RunState.IN_PROGRESS;
	}

	/**
	 * Starts the run: {@code NOT_STARTED -> IN_PROGRESS}. This is the moment the
	 * tick loop begins for real.
	 *
	 * <p><b>Idempotent, and that is a guard rather than a nicety.</b> The trigger
	 * is a client packet, so it can arrive twice from a double-click, a replay, or
	 * a second player clicking their own start panel; a run that "restarted" would
	 * silently reset the interval timer mid-run. Returns true only on the
	 * transitioning call, so callers can tell a real start from a duplicate.
	 *
	 * <p>The interval timer is zeroed here rather than left wherever it was.
	 * {@code tickCounter} is not persisted and is gated off while
	 * {@code NOT_STARTED}, so it is already zero in practice -- this makes "the
	 * first interval starts now" true by construction instead of by inference.
	 *
	 * @return true if this call performed the transition
	 */
	public boolean startRun() {
		if (runState == RunState.IN_PROGRESS) {
			return false;
		}
		runState = RunState.IN_PROGRESS;
		tickCounter = 0;
		setDirty();
		EntropyMod.LOGGER.info("Run started. Entropy loop is live: interval {} ticks, cap {}.",
				intervalTicks, entropyCap);
		return true;
	}

	/** This player's frozen movement keys, or {@link KeybindSnapshot#EMPTY} if none is held. */
	public KeybindSnapshot keybindSnapshotFor(ServerPlayer player) {
		return keybindSnapshotFor(player.getUUID().toString());
	}

	/**
	 * By UUID string. Public, and not merely an implementation detail of the
	 * {@code ServerPlayer} overload: the string is the persisted key, and it is
	 * the form the headless harness can drive without constructing a player.
	 */
	public KeybindSnapshot keybindSnapshotFor(String uuid) {
		return keybindSnapshots.getOrDefault(uuid, KeybindSnapshot.EMPTY);
	}

	/**
	 * Records a player's movement keys, once per player per run.
	 *
	 * <p><b>Write-once is the entire point of this feature.</b> Randomized
	 * Movement Controls is anchored to these keys precisely so that rebinding
	 * afterwards cannot unscramble it; if a later capture could overwrite an
	 * existing snapshot, a client could re-anchor the curse at will and the
	 * counter-exploit would be back with an extra step. So this refuses rather
	 * than replaces, exactly like {@link #assignMoveScrambleIfAbsent}.
	 *
	 * <p><b>Refused while {@code NOT_STARTED}</b>, because there is no run to
	 * anchor to yet and the player may still rebind before clicking Start. The
	 * start handler therefore has to call {@link #startRun} first -- an ordering
	 * that is safe because both happen inside one synchronous packet handler.
	 *
	 * @return true if this call stored the snapshot
	 */
	public boolean storeKeybindSnapshotIfAbsent(ServerPlayer player, KeybindSnapshot snapshot) {
		boolean stored = storeKeybindSnapshotIfAbsent(player.getUUID().toString(), snapshot);
		if (stored) {
			EntropyMod.LOGGER.info("Keybind snapshot frozen for {}: {}",
					player.getName().getString(), snapshot.keys());
		}
		return stored;
	}

	/** By UUID string -- see {@link #keybindSnapshotFor(String)} for why this is public. */
	public boolean storeKeybindSnapshotIfAbsent(String uuid, KeybindSnapshot snapshot) {
		if (runState != RunState.IN_PROGRESS || snapshot == null || !snapshot.isPresent()) {
			return false;
		}
		if (keybindSnapshots.containsKey(uuid)) {
			return false;
		}
		keybindSnapshots.put(uuid, snapshot);
		setDirty();
		return true;
	}

	/**
	 * Whether Phoenix Chambered Heart can still save someone: the effect is held
	 * and the run's one save is unspent.
	 */
	public boolean isPhoenixHeartAvailable() {
		return !phoenixHeartUsed && acquired.contains(PhoenixChamberedHeartBehavior.ID);
	}

	/**
	 * Spends Phoenix Chambered Heart to save a player from a killing blow.
	 *
	 * <p>Called from the {@code checkTotemDeathProtection} mixin, at the moment
	 * vanilla has already zeroed the player's health and is deciding whether to
	 * run the death branch.
	 *
	 * <p><b>The {@code setHealth} is mandatory and none of the three granted
	 * effects can replace it.</b> Health Boost raises the ceiling without raising
	 * current health, Absorption is a separate pool that the death check does not
	 * consult, and Regeneration's first tick has not happened yet. Returning true
	 * with the player still on zero leaves them alive on an empty bar and dead
	 * again next tick. Vanilla's own totem branch sets health for the same reason,
	 * to the same value.
	 *
	 * <p>The three grants are plain {@code MobEffectInstance}s -- what a potion or
	 * a beacon hands out -- so the healing, the raised ceiling, the absorption pool
	 * and, crucially, <b>all of the expiry</b> are vanilla's. See
	 * {@link PhoenixChamberedHeartBehavior} for the numbers each one produces and
	 * for why nothing needs cleaning up afterwards.
	 *
	 * <p>Ordering matters and follows the lesson from Second Guess's button bug:
	 * <b>the flag is set before anything else</b>, so nothing downstream -- the
	 * sync, a re-entrant damage event -- can observe an unspent save that has in
	 * fact just fired.
	 *
	 * @return true if the save happened and death should be skipped
	 */
	public boolean consumePhoenixHeart(ServerPlayer player) {
		if (!spendPhoenixHeart()) {
			return false;
		}
		// Alive first, then the effects -- see the javadoc. Order between the three
		// grants themselves does not matter: Health Boost's ceiling is raised the
		// instant it is added, before Regeneration's first tick can run.
		player.setHealth(PhoenixChamberedHeartBehavior.SURVIVE_HEALTH);
		player.invulnerableTime = PhoenixChamberedHeartBehavior.INVULNERABLE_TICKS;
		player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST,
				PhoenixChamberedHeartBehavior.HEALTH_BOOST_TICKS,
				PhoenixChamberedHeartBehavior.AMPLIFIER));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
				PhoenixChamberedHeartBehavior.ABSORPTION_TICKS,
				PhoenixChamberedHeartBehavior.AMPLIFIER));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
				PhoenixChamberedHeartBehavior.REGENERATION_TICKS,
				PhoenixChamberedHeartBehavior.AMPLIFIER));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED,
				PhoenixChamberedHeartBehavior.SPEED_TICKS,
				PhoenixChamberedHeartBehavior.SPEED_AMPLIFIER));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
				PhoenixChamberedHeartBehavior.BLINDNESS_TICKS,
				PhoenixChamberedHeartBehavior.BLINDNESS_AMPLIFIER));
		// Addressed to this player's connection rather than played into the level:
		// every Level.playSound overload broadcasts (its Entity parameter is the
		// player to EXCLUDE), and this mod is singleplayer-scoped. See the behavior
		// class for the one-line broadcast alternative.
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WITHER_DEATH),
				SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(),
				PhoenixChamberedHeartBehavior.SOUND_VOLUME,
				PhoenixChamberedHeartBehavior.SOUND_PITCH,
				player.getRandom().nextLong()));
		player.sendSystemMessage(Component.literal(
				"[Entropy] Phoenix Chambered Heart burns away -- you rise from the ash."));

		// The client's cached acquired set has to lose it too, or a client-side
		// effect would keep acting on an effect the run no longer holds.
		syncToAll(player.level().getServer());
		EntropyMod.LOGGER.info("Phoenix Chambered Heart consumed by {} -- {} effect(s) remain.",
				player.getName().getString(), acquired.size());
		return true;
	}

	/**
	 * The run-state half of {@link #consumePhoenixHeart}, split out so the
	 * once-per-run rule can be driven headlessly against the real shipped code --
	 * the full version needs a live {@code ServerPlayer} the harness cannot build.
	 *
	 * <p>Order is deliberate: the flag is set first, so nothing downstream can
	 * observe an unspent save that has in fact already fired. That is the same
	 * ordering lesson Second Guess's reroll button was fixed with.
	 *
	 * @return true only on the call that actually spends it
	 */
	public boolean spendPhoenixHeart() {
		if (!isPhoenixHeartAvailable()) {
			return false;
		}
		phoenixHeartUsed = true;
		// Dropping the id is presentation, not enforcement -- the flag above is what
		// makes this once-per-run. See AcquiredEffects.remove.
		acquired.remove(PhoenixChamberedHeartBehavior.ID);
		setDirty();
		return true;
	}

	public boolean isPhoenixHeartUsed() {
		return phoenixHeartUsed;
	}

	/** Tells one player the run state and their own frozen keybinds. */
	public void syncRunTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, RunSyncPayload.of(runState, keybindSnapshotFor(player)));
	}

	/**
	 * {@link #syncRunTo} for everybody. Called after the start transition, which is
	 * also what makes every <em>other</em> connected client capture its own
	 * keybinds -- they see {@code IN_PROGRESS} with no snapshot of their own and
	 * send one, with no request packet needed.
	 */
	public void syncRunToAll(MinecraftServer server) {
		for (ServerPlayer player : PlayerLookup.all(server)) {
			syncRunTo(player);
		}
	}

	/**
	 * Sends one player the client-side view of the run: which effects are held and
	 * the movement scramble.
	 *
	 * <p><b>This is new infrastructure, and every client-side effect needs it.</b>
	 * Before Tier 2 every effect was server-authoritative, so the client had no
	 * reason to know the acquired set and was never told. Randomized Controls,
	 * Upside-Down Camera and Random Jump all act on client-only systems (input
	 * handling and the camera), so they cannot work without it.
	 */
	public void syncTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, new ClientEffectsPayload(
				List.copyOf(acquired.ids()), moveScramble));
	}

	/** Sends {@link #syncTo} to everyone. Called after any change to the acquired set. */
	public void syncToAll(MinecraftServer server) {
		for (ServerPlayer player : PlayerLookup.all(server)) {
			syncTo(player);
		}
	}

	/**
	 * Called by the server-side network receiver when a player submits their pick.
	 *
	 * <p><b>One pick may grant several effects</b> (Twin Fate), so the body is a
	 * loop over whatever {@link EffectBehavior#expandGrant} returns rather than a
	 * single acquisition. Everything inside the loop -- no-repeat, the history row,
	 * {@code apply} -- runs uniformly per granted effect, and everything that
	 * belongs to the <em>pick</em> rather than to an effect stays outside it.
	 *
	 * <p><b>The pick costs exactly one entropy and one pick count regardless.</b>
	 * Both counters move once, after the loop. That is the property the multi-grant
	 * design rests on and it is structural rather than checked: there is no path
	 * through this method that touches them more than once.
	 */
	public void onChoiceMade(MinecraftServer server, String chosenEffectId) {
		if (!waitingOnChoice) {
			return; // ignore stray/duplicate submissions
		}
		// A run can end while a pick is open -- the dragon dies mid-choice. endRun
		// clears waitingOnChoice precisely so this cannot happen, but the check is
		// kept because the submission comes from a client and must never be able to
		// add entropy to a finished run.
		if (runState != RunState.IN_PROGRESS) {
			EntropyMod.LOGGER.warn("Pick '{}' submitted while the run is {} -- ignored.",
				chosenEffectId, runState);
			return;
		}
		EffectDefinition chosen = EffectRegistry.byId(chosenEffectId);
		if (chosen == null) {
			EntropyMod.LOGGER.warn("Unknown effect id submitted: {}", chosenEffectId);
			return;
		}
		// The real screen can only ever send one of the three cards, so this guards
		// against a hand-rolled client rather than against the game. It is possible
		// at all only because the pending offer is now remembered -- before Twin
		// Fate, the server kept nothing about what it had offered.
		if (!pendingChoiceIds.isEmpty() && !pendingChoiceIds.contains(chosen.id())) {
			EntropyMod.LOGGER.warn("'{}' was submitted but was not offered (cards were {}) -- ignored.",
					chosen.id(), pendingChoiceIds);
			return;
		}

		// Captured BEFORE anything moves: entropyAtPick is the number the player was
		// looking at when they chose, pickNumber is 1-based, and every row this pick
		// writes shares both.
		int pickNumber = pickCount + 1;
		EffectPhase phase = currentPhase();
		int entropyAtPick = entropy;

		List<String> grantedIds = EffectBehaviors.get(chosen.id())
				.expandGrant(List.copyOf(pendingChoiceIds), chosen.id());

		List<EffectDefinition> granted = new ArrayList<>();
		for (String id : grantedIds) {
			EffectDefinition definition = EffectRegistry.byId(id);
			if (definition == null) {
				EntropyMod.LOGGER.warn("expandGrant returned unknown id '{}' -- skipped.", id);
				continue;
			}
			boolean isNew = acquired.add(definition.id());
			if (!isNew) {
				EntropyMod.LOGGER.warn("'{}' was picked again -- only reachable via the repeat fallback.",
						definition.id());
				// A repeat of the CHOSEN effect is the repeat fallback doing its job and
				// still records and re-applies, exactly as before. A repeat among the
				// EXTRA grants is a no-op: re-applying something already held would
				// exercise -- and could therefore hide -- the idempotency guarantee the
				// whole respawn design rests on, which is the same reason /entropygrant
				// rejects a re-grant rather than silently re-applying.
				if (!definition.id().equals(chosen.id())) {
					continue;
				}
			}
			history.add(new PickRecord(pickNumber, phase, definition.id(),
					definition.displayName(), definition.description(), entropyAtPick));
			granted.add(definition);
		}

		// Loop-level bookkeeping, before the effects land, so a meta effect acquired
		// by this pick is armed for the NEXT one rather than for its own.
		for (EffectDefinition definition : granted) {
			EffectBehaviors.get(definition.id()).onAcquired(this);
		}
		consumePoolOverrides(phase, chosen);

		for (EffectDefinition definition : granted) {
			applyToAll(server, definition, EffectContext.Reason.PICKED);
		}
		// The client's view of the acquired set has to move with the server's, or a
		// client-side effect stays inert until the next relog.
		syncToAll(server);

		// The announcement lives here, not in the behaviors: it is a statement about
		// the RUN ("you chose this"), and behaviors also run on every respawn, where
		// announcing would be pure spam. One line per granted effect, so a Twin Fate
		// pick reads as the three things it actually was.
		for (EffectDefinition definition : granted) {
			server.getPlayerList().broadcastSystemMessage(
					Component.literal("[Entropy] " + definition.displayName()
							+ " -- " + definition.description()),
					false);
		}

		entropy += entropyGainForThisPick();
		pickCount++;
		waitingOnChoice = false;
		pendingChoiceIds.clear();
		setDirty();

		EntropyMod.LOGGER.info("Pick #{} granted {} effect(s); entropy {} -> {}. Acquired {}: {}",
				pickNumber, granted.size(), entropyAtPick, entropy, acquired.size(), acquired);
	}

	/**
	 * How much entropy this pick adds: normally 1, and under Banked Entropy
	 * alternately 0 and 1.
	 *
	 * <p>Half of one, expressed in integers, has to be spread over two picks -- so
	 * the carry bit is what makes it exact rather than rounded. Over any even number
	 * of picks the total is precisely half.
	 *
	 * <p>Called after the acquisition loop, so the pick that <em>takes</em> Banked
	 * Entropy is already holding it and is itself free.
	 */
	private int entropyGainForThisPick() {
		if (!acquired.contains(BankedEntropyBehavior.ID)) {
			return 1;
		}
		if (halfEntropyPending) {
			halfEntropyPending = false;
			return 1;
		}
		halfEntropyPending = true;
		return 0;
	}

	/**
	 * Clears whichever armed pool override this pick just consumed, and records
	 * All In's doubled curse.
	 *
	 * <p>Cleared here -- when the pick is <b>answered</b> -- rather than in
	 * {@link #triggerPick} when it is rolled. That is what makes a forced pool
	 * survive a reroll: a player who spends Second Guess or a banked charge gets
	 * three more cards from the same forced pool instead of escaping back into the
	 * ordinary one. It also means an interval that opens no pick does not burn the
	 * flag.
	 */
	private void consumePoolOverrides(EffectPhase phase, EffectDefinition chosen) {
		if (phase != EffectPhase.BAD) {
			return;
		}
		if (doubleNextCursePending) {
			doubleNextCursePending = false;
			doubledEffectId = chosen.id();
			EntropyMod.LOGGER.info("All In resolved: '{}' is doubled for the rest of the run (x{}).",
					doubledEffectId, AllInBehavior.SEVERITY_MULTIPLIER);
			return;
		}
		if (extremeTierPending) {
			extremeTierPending = false;
			EntropyMod.LOGGER.info("Debt Collector resolved: '{}' was drawn from the extreme tier.",
					chosen.id());
		}
	}

	// ------------------------------------------------------------------
	// Debug grant
	// ------------------------------------------------------------------

	/** Why a {@link #grantEffect} call did or didn't take. */
	public enum GrantResult {
		GRANTED,
		/** No {@link EffectDefinition} with that id. */
		UNKNOWN_EFFECT,
		/** Already in {@link AcquiredEffects} -- granting again would mask an idempotency bug. */
		ALREADY_ACQUIRED
	}

	/**
	 * Adds an effect to the run directly, as {@code /entropygrant} does. Debug tool:
	 * it exists so a specific effect can be tested without re-rolling until it
	 * happens to be offered.
	 *
	 * <p><b>It is the real acquisition path, not an imitation of one.</b> It writes
	 * to the same {@link AcquiredEffects} set, marks the same {@link SavedData}
	 * dirty, and dispatches through the same private {@link #applyToAll} that
	 * {@link #onChoiceMade} uses -- so the effect is persisted, re-applied on
	 * respawn and rejoin, and counts for anti-stacking, exactly as if it had been
	 * picked. Same rule as {@code /entropyforcepick}: if a future edit makes this
	 * build its own {@code EffectContext} or call a behavior directly, that is a
	 * bug, because it would stop testing the thing it exists to test.
	 *
	 * <p><b>It deliberately does not advance the run.</b> Entropy, the pick count,
	 * the interval timer and the history list are all untouched -- this is a debug
	 * tool, not a shortcut through the game loop, and a granted effect should not
	 * pollute the record of what the player actually chose. That claim is
	 * verifiable in this method's bytecode: it reads and writes none of those
	 * fields.
	 *
	 * <p><b>No-repeat is enforced rather than bypassed.</b> Re-granting something
	 * already acquired is rejected instead of silently re-applying, because a
	 * silent double-apply is exactly the shape of the idempotency bug the
	 * respawn/rejoin design rests on not having -- masking it here would make the
	 * debug tool hide the class of bug it is meant to help find.
	 */
	public GrantResult grantEffect(MinecraftServer server, String effectId) {
		EffectDefinition definition = EffectRegistry.byId(effectId);
		if (definition == null) {
			return GrantResult.UNKNOWN_EFFECT;
		}
		if (!acquired.add(definition.id())) {
			return GrantResult.ALREADY_ACQUIRED;
		}
		setDirty();
		// The same loop-level hook onChoiceMade calls, so a granted meta effect arms
		// itself exactly as a picked one does -- which is what makes these testable
		// at all. It moves no run counter, so this method's "does not advance the
		// run" guarantee is untouched.
		EffectBehaviors.get(definition.id()).onAcquired(this);
		applyToAll(server, definition, EffectContext.Reason.PICKED);
		syncToAll(server);
		// Deliberately does not log entropy/pickCount: this method's bytecode
		// referencing neither field at all is the check that it cannot move them.
		EntropyMod.LOGGER.info("Granted '{}' via debug command -- run counters untouched. Acquired {} effect(s): {}",
				definition.id(), acquired.size(), acquired);
		return GrantResult.GRANTED;
	}

	// ------------------------------------------------------------------
	// Application / re-application
	// ------------------------------------------------------------------

	private void applyToAll(MinecraftServer server, EffectDefinition definition, EffectContext.Reason reason) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyTo(server, player, definition, reason);
		}
	}

	private void applyTo(MinecraftServer server, ServerPlayer player, EffectDefinition definition,
						 EffectContext.Reason reason) {
		try {
			EffectBehaviors.get(definition.id())
					.apply(new EffectContext(server, player, definition, reason,
							severityMultiplierFor(definition.id())));
		} catch (RuntimeException e) {
			// One broken effect must not abort the loop or the join handler and leave
			// the player with a half-applied set.
			EntropyMod.LOGGER.error("Effect '{}' threw during apply({}) -- skipped.",
					definition.id(), reason, e);
		}
	}

	/**
	 * Re-establishes every acquired effect on one player. Call on join, on respawn,
	 * and after any dimension change that rebuilds the player entity.
	 *
	 * <p>This is not a safety net, it is the <b>only</b> thing that puts effects
	 * back. Attribute modifiers are applied transiently and are therefore never
	 * saved in player NBT; on top of that, a death respawn constructs a brand-new
	 * {@code ServerPlayer} and vanilla's {@code restoreFrom} only carries permanent
	 * modifiers across -- and only when its boolean parameter is true, which it is
	 * not for a death. Verified in bytecode, see CLAUDE.md.
	 *
	 * <p>Safe to call any number of times: every behavior's {@code apply} is
	 * required to be idempotent, and the attribute ones achieve that by replacing a
	 * modifier keyed on a stable per-effect Identifier rather than adding one.
	 */
	public void reapplyAll(MinecraftServer server, ServerPlayer player) {
		if (acquired.isEmpty()) {
			return;
		}
		List<EffectDefinition> definitions = acquired.definitions();
		for (EffectDefinition definition : definitions) {
			applyTo(server, player, definition, EffectContext.Reason.REAPPLIED);
		}
		EntropyMod.LOGGER.info("Re-applied {} effect(s) to {}", definitions.size(),
				player.getName().getString());

		List<String> unknown = acquired.unknownIds();
		if (!unknown.isEmpty()) {
			EntropyMod.LOGGER.warn("Saved run references {} effect id(s) this build does not define: {}. "
					+ "They were skipped; the rest of the run is intact.", unknown.size(), unknown);
		}
	}

	// ------------------------------------------------------------------
	// Accessors
	// ------------------------------------------------------------------

	/** The phase the NEXT pick will use. Strict GOOD -&gt; BAD alternation. */
	public EffectPhase currentPhase() {
		return (pickCount % 2 == 0) ? EffectPhase.GOOD : EffectPhase.BAD;
	}

	/** The run's acquired effects: active set, no-repeat set, and category source in one. */
	/** The companion roster. Mutating it must be followed by {@link #setDirty()}. */
	public CompanionRoster companions() {
		return companions;
	}

	/**
	 * Records companions a behavior has just spawned, and persists the fact.
	 *
	 * <p>The {@code setDirty()} here is the easy-to-miss half: without it the
	 * roster is rebuilt empty on the next load and the follow service quietly stops
	 * finding anyone.
	 */
	public void recordCompanions(net.minecraft.server.level.ServerPlayer owner,
								 String effectId, java.util.List<String> entityUuids) {
		if (entityUuids.isEmpty()) {
			return;
		}
		companions.record(owner.getUUID().toString(), effectId, entityUuids);
		setDirty();
	}

	public AcquiredEffects acquired() {
		return acquired;
	}

	/** Full pick history for this run, oldest first. Unmodifiable. */
	public List<PickRecord> getHistory() {
		return Collections.unmodifiableList(history);
	}

	public boolean isRerollUsed() { return rerollUsed; }
	public boolean isWaitingOnChoice() { return waitingOnChoice; }
	public int getTicksIntoInterval() { return tickCounter; }
	public int getEntropy() { return entropy; }
	public int getPickCount() { return pickCount; }
	public int getEntropyCap() { return entropyCap; }
	public void setEntropyCap(int cap) { this.entropyCap = cap; setDirty(); }
	public int getIntervalTicks() { return intervalTicks; }
	public void setIntervalTicks(int ticks) { this.intervalTicks = ticks; setDirty(); }
}
