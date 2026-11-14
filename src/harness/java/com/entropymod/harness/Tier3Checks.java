package com.entropymod.harness;

import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.AttributeEffectBehavior;
import com.entropymod.entropy.EffectBehavior;
import com.entropymod.entropy.EffectBehaviors;
import com.entropymod.entropy.EffectCategory;
import com.entropymod.entropy.EffectDefinition;
import com.entropymod.entropy.EffectHooks;
import com.entropymod.entropy.EffectPhase;
import com.entropymod.entropy.EffectRegistry;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.HookEffectBehavior;
import com.entropymod.entropy.StatusEffectBehavior;
import com.entropymod.entropy.behavior.BankedEntropyBehavior;
import com.entropymod.entropy.behavior.SunburnBehavior;
import com.entropymod.entropy.probe.MixinProbe;
import com.entropymod.entropy.status.StatusEffectService;
import com.entropymod.entropy.status.SunburnService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static com.entropymod.harness.Checks.check;
import static com.entropymod.harness.Checks.checkNear;
import static com.entropymod.harness.Checks.classReferences;
import static com.entropymod.harness.Checks.constant;
import static com.entropymod.harness.Checks.section;

/**
 * Headless checks for the Tier 3 (entropy 61-100) and Tier 4 (81-100) content, and
 * for the mixin firing probes that shipped with it.
 *
 * <p>The check this file exists for is {@link #walkEveryLevel}: before Tier 3 no
 * effect was eligible above entropy 60, so a run stalled at 61 and the default cap
 * of 100 could not be reached. It walks 0 to the cap and asserts both phases are
 * selectable at every level, then drives whole runs through the real roll.
 *
 * <p>Everything else is the usual discipline applied to 42 new effects: values read
 * from real {@code AttributeInstance}s rather than restated, constants read by
 * reflection, and the stacking cases that could land on a dangerous clamp driven
 * explicitly rather than argued.
 */
final class Tier3Checks {

	static final List<String> T3_GOOD = List.of(
			"titan_hide", "adamant_plating", "executioner", "blur", "quarry_hands", "long_arm",
			"unshakeable", "mountain_goat", "whirlwind", "bottomless_stomach", "prodigy",
			"night_eyes", "salamander_blood", "gills", "stoneskin", "second_wind", "overclocked",
			"village_hero", "featherfall");
	static final List<String> T4_GOOD = List.of("apex_predator", "stormstride", "juggernaut");
	static final List<String> T3_BAD = List.of(
			"withered_frame", "feeble", "molasses", "stone_hands", "short_arms", "crushing_gravity",
			"glass_bones", "rusted_plating", "leaden_swing", "tinderbox", "grounded", "paper_skin",
			"starving", "forgetful", "shadow_blind", "marked", "infested", "sunburn");
	static final List<String> T4_BAD = List.of("last_thread", "venom");

	private static final int TIER3_FLOOR = 61;
	private static final int TIER4_FLOOR = 81;

	static void run() {
		HarnessBootstrap.init();
		wiring();
		walkEveryLevel();
		wholeRuns();
		attributeValues();
		attributeStacking();
		jumpPhysics();
		statusFamily();
		hookMultipliers();
		sunburn();
		persistence();
		mixinProbes();
	}

	private static List<String> allNew() {
		List<String> all = new ArrayList<>();
		all.addAll(T3_GOOD);
		all.addAll(T4_GOOD);
		all.addAll(T3_BAD);
		all.addAll(T4_BAD);
		return all;
	}

	// =====================================================================
	// Wiring.
	// =====================================================================

	private static void wiring() {
		section("Tier 3/4: registry wiring, bands and flags (42 effects)");

		check(allNew().size() == 42 && new TreeSet<>(allNew()).size() == 42,
				"42 distinct new ids: 19 + 3 GOOD, 18 + 2 BAD");

		wiringFor(T3_GOOD, EffectPhase.GOOD, TIER3_FLOOR);
		wiringFor(T4_GOOD, EffectPhase.GOOD, TIER4_FLOOR);
		wiringFor(T3_BAD, EffectPhase.BAD, TIER3_FLOOR);
		wiringFor(T4_BAD, EffectPhase.BAD, TIER4_FLOOR);

		for (String id : T3_BAD) {
			check(EffectRegistry.byId(id).counterplay(),
					id + ": Tier 3 curses are all counterplay = true -- each has a stated answer");
		}
		for (String id : T4_BAD) {
			check(!EffectRegistry.byId(id).counterplay(),
					id + ": Tier 4 curse, counterplay = FALSE and labelled as such");
		}

		long good = EffectRegistry.all().stream().filter(d -> d.phase() == EffectPhase.GOOD).count();
		long bad = EffectRegistry.all().stream().filter(d -> d.phase() == EffectPhase.BAD).count();
		check(EffectRegistry.all().size() == 114, "the registry holds 114 effects (was 72)");
		check(good == 56 && bad == 58, "56 GOOD and 58 BAD (was 34 and 38): " + good + "/" + bad);
		check(EffectRegistry.all().stream().map(EffectDefinition::id).distinct().count() == 114,
				"...and every id is unique");
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatch between EffectRegistry and EffectBehaviors in either direction");

		// Nothing that existed before moved. "Out of scope: rebalancing existing
		// effects" is checkable: every pre-existing effect still closes at or below 60.
		Set<String> fresh = Set.copyOf(allNew());
		boolean untouched = EffectRegistry.all().stream()
				.filter(d -> !fresh.contains(d.id()))
				.allMatch(d -> d.maxEntropy() <= 60);
		check(untouched, "UNREGRESSED: no pre-existing effect's window was extended past 60");
		check(EffectRegistry.all().stream().filter(d -> !fresh.contains(d.id())).count() == 72,
				"UNREGRESSED: the 72 pre-existing effects are all still registered");
	}

	private static void wiringFor(List<String> ids, EffectPhase phase, int floor) {
		for (String id : ids) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + ": registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.phase() == phase, id + ": " + phase);
			check(def.minEntropy() == floor && def.maxEntropy() == 100,
					id + ": window " + floor + "-100");
			EffectBehavior behavior = EffectBehaviors.get(id);
			boolean knownShape = behavior instanceof AttributeEffectBehavior
					|| behavior instanceof HookEffectBehavior
					|| behavior instanceof StatusEffectBehavior;
			check(knownShape && !EffectBehaviors.definitionsWithoutBehavior().contains(id),
					id + ": wired to a behavior of a known shape ("
							+ behavior.getClass().getSuperclass().getSimpleName() + ")");
			check(def.description() != null && !def.description().isBlank()
							&& def.displayName() != null && !def.displayName().isBlank(),
					id + ": has a name and a description for the card");
		}
	}

	// =====================================================================
	// THE WALK: entropy 0 -> cap, an effect selectable at every level.
	// =====================================================================

	private static void walkEveryLevel() {
		section("Walk: entropy 0 to the cap, both phases selectable at every level");

		int cap = (int) constant(EntropyManager.class, "DEFAULT_ENTROPY_CAP");
		check(cap == 100, "the default cap is 100");
		check(new EntropyManager().getEntropyCap() == cap, "...and a fresh run uses it");

		// A pick is rolled at every entropy from 0 to cap - 1. At the cap itself
		// triggerPick ends the run before rolling, so cap - 1 is the last level that
		// needs a pool. Phase alternates on PICK count, not on entropy, and Banked
		// Entropy decouples the two -- so BOTH phases must be available at EVERY level.
		for (int entropy = 0; entropy < cap; entropy++) {
			for (EffectPhase phase : EffectPhase.values()) {
				int pool = EffectRegistry.eligible(phase, entropy).size();
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new Random(entropy * 31L + phase.ordinal()), Set.of(), Set.of());
				check(pool > 0 && !roll.choices().isEmpty(),
						"entropy " + entropy + " / " + phase + ": selectable (pool " + pool
								+ ", offered " + roll.choices().size() + ")");
			}
		}

		// The regression this guards, driven rather than described: take the new
		// effects back out and the pool is empty from 61 up, which is the stall.
		Set<String> fresh = Set.copyOf(allNew());
		for (EffectPhase phase : EffectPhase.values()) {
			boolean emptyWithoutTier3 = true;
			for (int entropy = TIER3_FLOOR; entropy < cap; entropy++) {
				long old = EffectRegistry.eligible(phase, entropy).stream()
						.filter(d -> !fresh.contains(d.id())).count();
				emptyWithoutTier3 &= old == 0;
			}
			check(emptyWithoutTier3,
					"WITHOUT the new effects the " + phase + " pool is empty at every level from 61 "
							+ "to 99 -- the NO_ELIGIBLE_EFFECTS stall this session fixes");
			long oldAt60 = EffectRegistry.eligible(phase, 60).stream()
					.filter(d -> !fresh.contains(d.id())).count();
			check(oldAt60 > 0, "...and it was non-empty at 60, so 61 really was the first dead level ("
					+ phase + ": " + oldAt60 + ")");
		}

		// The bands themselves. Tier 4 is ADDED to Tier 3 at 81, never a replacement:
		// a window that closed at 80 would have reintroduced a thin region.
		check(EffectRegistry.eligible(EffectPhase.GOOD, 61).size() == 19
						&& EffectRegistry.eligible(EffectPhase.BAD, 61).size() == 18,
				"at 61 the pools are 19 GOOD and 18 BAD");
		check(EffectRegistry.eligible(EffectPhase.GOOD, 81).size() == 22
						&& EffectRegistry.eligible(EffectPhase.BAD, 81).size() == 20,
				"at 81 Tier 4 joins them: 22 GOOD and 20 BAD");
		check(EffectRegistry.eligible(EffectPhase.GOOD, 99).size() == 22
						&& EffectRegistry.eligible(EffectPhase.BAD, 99).size() == 20,
				"...and nothing has closed by 99, the last level a pick is rolled at");
	}

	/** What one simulated run reached. */
	private record RunResult(int entropy, int picks, int stalledAt, boolean banked,
							 int lateRepeatFallbacks, int emptyLateOffers) {}

	/**
	 * One run through the real {@code EffectRegistry.roll} and the real
	 * {@code AcquiredEffects}, with the loop's own bookkeeping modelled the way
	 * {@code triggerPick} and {@code onChoiceMade} do it: phase from pick-count
	 * parity, no-repeat and anti-stacking fed from the acquired set, +1 entropy per
	 * pick, and Banked Entropy's alternating 0/+1 once it is held.
	 *
	 * <p>{@code triggerPick} itself cannot be called headlessly -- it sends a packet
	 * to every player on its last line -- so this is the same division the rest of
	 * the harness uses: the decisions are driven for real, the packet is not.
	 */
	private static RunResult simulate(long seed, boolean pickRandomCard, int cap, boolean bankedOffered) {
		Random random = new Random(seed);
		AcquiredEffects acquired = new AcquiredEffects();
		// The counterfactual run: Banked Entropy is treated as already taken for the
		// ROLL only, so it is never offered and its entropy rule never engages.
		Set<String> neverOffered = bankedOffered ? Set.of() : Set.of(BankedEntropyBehavior.ID);
		int entropy = 0;
		int picks = 0;
		boolean halfPending = false;
		int lateRepeats = 0;
		int emptyLate = 0;
		// A real run ends at the cap; the bound is only so a broken loop cannot hang.
		while (entropy < cap && picks < 10 * cap) {
			EffectPhase phase = (picks % 2 == 0) ? EffectPhase.GOOD : EffectPhase.BAD;
			Set<String> taken = new java.util.HashSet<>(acquired.ids());
			taken.addAll(neverOffered);
			EffectRegistry.RollResult roll = EffectRegistry.roll(
					phase, entropy, random, taken, acquired.occupiedCategories());
			// Filtered at the CARDS as well as at the taken set, and the second filter
			// is the one that matters: when a pool is exhausted the repeat fallback
			// re-offers the whole eligible pool, already-taken ids included, so an id
			// that was only marked "taken" comes straight back. (The first draft of
			// this counterfactual leaked exactly that way at entropy 51-60.)
			List<EffectDefinition> cards = roll.choices().stream()
					.filter(d -> !neverOffered.contains(d.id())).toList();
			if (cards.isEmpty()) {
				return new RunResult(entropy, picks, entropy, acquired.contains(BankedEntropyBehavior.ID),
						lateRepeats, emptyLate + 1);
			}
			if (entropy >= TIER3_FLOOR && roll.repeatFallback()) {
				lateRepeats++;
			}
			EffectDefinition chosen = cards.get(pickRandomCard ? random.nextInt(cards.size()) : 0);
			acquired.add(chosen.id());

			int gain = 1;
			if (acquired.contains(BankedEntropyBehavior.ID)) {
				if (halfPending) {
					halfPending = false;
				} else {
					halfPending = true;
					gain = 0;
				}
			}
			entropy += gain;
			picks++;
		}
		return new RunResult(entropy, picks, -1, acquired.contains(BankedEntropyBehavior.ID),
				lateRepeats, emptyLate);
	}

	/** Totals over one batch of simulated runs. */
	private record Batch(int runs, int stalled, int firstStall, int banked, int lateRepeats,
						 int minPicks, int maxPicks) {}

	private static Batch batch(int cap, boolean bankedOffered) {
		int runs = 0;
		int stalled = 0;
		int firstStall = -1;
		int banked = 0;
		int lateRepeats = 0;
		int minPicks = Integer.MAX_VALUE;
		int maxPicks = 0;
		for (long seed = 0; seed < 200; seed++) {
			for (boolean randomCard : new boolean[] {false, true}) {
				RunResult result = simulate(seed, randomCard, cap, bankedOffered);
				runs++;
				if (result.stalledAt() >= 0 || result.entropy() != cap) {
					stalled++;
					if (firstStall < 0) {
						firstStall = result.stalledAt();
					}
				}
				if (result.banked()) {
					banked++;
				}
				lateRepeats += result.lateRepeatFallbacks();
				minPicks = Math.min(minPicks, result.picks());
				maxPicks = Math.max(maxPicks, result.picks());
			}
		}
		return new Batch(runs, stalled, firstStall, banked, lateRepeats, minPicks, maxPicks);
	}

	private static void wholeRuns() {
		section("Walk: 800 whole runs through the real roll, 0 to the cap");

		int cap = (int) constant(EntropyManager.class, "DEFAULT_ENTROPY_CAP");
		check(new EntropyManager().currentPhase() == EffectPhase.GOOD,
				"the model's phase rule matches the manager's: pick 0 is GOOD");

		// --- 1. The run as the registry really plays: 200 seeds x two pick policies.
		Batch real = batch(cap, true);
		check(real.runs() == 400, "400 runs driven: 200 seeds x (always the first card, a random card)");
		check(real.stalled() == 0, "NO run stalls: every one of them reaches entropy " + cap
				+ (real.stalled() == 0 ? "" : " -- first stall at entropy " + real.firstStall()));
		check(real.minPicks() >= cap, "every run made at least " + cap + " picks to get there (fewest: "
				+ real.minPicks() + ", most: " + real.maxPicks() + ")");

		// --- 2. The same runs with Banked Entropy never offered.
		Batch plain = batch(cap, false);
		check(plain.stalled() == 0 && plain.banked() == 0,
				"with Banked Entropy never offered, all 400 runs still reach the cap");
		check(plain.minPicks() == cap && plain.maxPicks() == cap,
				"...in exactly " + cap + " picks each -- one entropy per pick");
		// The pool sizes were chosen for this shape: 10 + 10 GOOD picks against pools
		// of 19 then 12, and 10 + 10 BAD picks against 18 then 10.
		check(plain.lateRepeats() == 0,
				"...and at one entropy per pick the repeat fallback is NEVER reached from 61 up: "
						+ "there is enough Tier 3/4 content for every pick to offer something new");

		// --- What the first batch found, reported rather than assumed.
		//
		// A first draft asserted that some runs take Banked Entropy and some do not.
		// That was wrong, and the reason is a property of the EXISTING registry: the
		// GOOD pool at entropy 51-60 is exactly Glass Cannon Pact and Banked Entropy,
		// and a run makes about five GOOD picks there, so both are taken essentially
		// every time. From then on entropy rises at half speed, the 61-100 stretch
		// takes about twice the picks, and the Tier 3/4 pools ARE drained -- at which
		// point the repeat fallback (PLACEHOLDER policy, Open Question 6) re-offers
		// effects already held. Never a stall; but it is the real shape of a default
		// run, so it is printed here and written up in CLAUDE.md.
		check(EffectRegistry.eligible(EffectPhase.GOOD, 55).size() == 2
						&& EffectRegistry.eligible(EffectPhase.GOOD, 55).stream()
								.anyMatch(d -> d.id().equals(BankedEntropyBehavior.ID)),
				"the cause is pre-existing and untouched here: at 51-60 the GOOD pool is two "
						+ "effects, one of them Banked Entropy");
		System.out.println("  NOTE  as the registry really plays, " + real.banked() + " of " + real.runs()
				+ " runs hold Banked Entropy by 61; picks per run " + real.minPicks() + "-" + real.maxPicks()
				+ "; repeat-fallback picks from 61 up average "
				+ String.format("%.1f", real.lateRepeats() / (double) real.runs()) + " per run");
	}
	// =====================================================================
	// Attributes.
	// =====================================================================

	private static double playerBase(Holder<Attribute> attribute) {
		return DefaultAttributes.getSupplier(EntityType.PLAYER).getBaseValue(attribute);
	}

	/** A player-based instance carrying every change these effects make to one attribute. */
	private static AttributeInstance instanceWith(Holder<Attribute> attribute, double base,
												  Map<String, Double> effectsAndSeverity) {
		AttributeInstance instance = new AttributeInstance(attribute, a -> { });
		instance.setBaseValue(base);
		for (Map.Entry<String, Double> entry : effectsAndSeverity.entrySet()) {
			AttributeEffectBehavior behavior = (AttributeEffectBehavior) EffectBehaviors.get(entry.getKey());
			for (AttributeEffectBehavior.Change change : behavior.changes()) {
				if (change.attribute().equals(attribute)) {
					instance.addOrUpdateTransientModifier(new AttributeModifier(
							AttributeEffectBehavior.modifierId(entry.getKey()),
							change.amount() * entry.getValue(), change.operation()));
				}
			}
		}
		return instance;
	}

	private static double valueWith(Holder<Attribute> attribute, String... effects) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (String effect : effects) {
			map.put(effect, 1.0);
		}
		return instanceWith(attribute, playerBase(attribute), map).getValue();
	}

	private record Expect(String id, Holder<Attribute> attribute, String name, double value) {}

	private static void attributeValues() {
		section("Tier 3/4 attributes: real values on a real player-based AttributeInstance");

		AttributeSupplier player = DefaultAttributes.getSupplier(EntityType.PLAYER);
		double speed = playerBase(Attributes.MOVEMENT_SPEED);
		checkNear(speed, 0.1, 1e-6, "the player's MOVEMENT_SPEED base is 0.1 (the supplier's, not the attribute's 0.7)");
		checkNear(playerBase(Attributes.ATTACK_DAMAGE), 1.0, 1e-9,
				"the player's ATTACK_DAMAGE base is 1.0 (the supplier's, not the attribute's 2.0)");

		List<Expect> table = List.of(
				new Expect("titan_hide", Attributes.MAX_HEALTH, "MAX_HEALTH", 40.0),
				new Expect("adamant_plating", Attributes.ARMOR, "ARMOR", 10.0),
				new Expect("adamant_plating", Attributes.ARMOR_TOUGHNESS, "ARMOR_TOUGHNESS", 6.0),
				new Expect("executioner", Attributes.ATTACK_DAMAGE, "ATTACK_DAMAGE", 2.0),
				new Expect("blur", Attributes.MOVEMENT_SPEED, "MOVEMENT_SPEED", speed * 1.4),
				new Expect("quarry_hands", Attributes.BLOCK_BREAK_SPEED, "BLOCK_BREAK_SPEED", 2.0),
				new Expect("long_arm", Attributes.BLOCK_INTERACTION_RANGE, "BLOCK_INTERACTION_RANGE", 7.5),
				new Expect("long_arm", Attributes.ENTITY_INTERACTION_RANGE, "ENTITY_INTERACTION_RANGE", 6.0),
				new Expect("unshakeable", Attributes.KNOCKBACK_RESISTANCE, "KNOCKBACK_RESISTANCE", 1.0),
				new Expect("unshakeable", Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
						"EXPLOSION_KNOCKBACK_RESISTANCE", 1.0),
				new Expect("mountain_goat", Attributes.STEP_HEIGHT, "STEP_HEIGHT", 1.25),
				new Expect("mountain_goat", Attributes.SAFE_FALL_DISTANCE, "SAFE_FALL_DISTANCE", 10.0),
				new Expect("whirlwind", Attributes.ATTACK_SPEED, "ATTACK_SPEED", 8.0),
				new Expect("apex_predator", Attributes.ATTACK_DAMAGE, "ATTACK_DAMAGE", 3.0),
				new Expect("apex_predator", Attributes.ATTACK_SPEED, "ATTACK_SPEED", 6.0),
				new Expect("stormstride", Attributes.MOVEMENT_SPEED, "MOVEMENT_SPEED", speed * 1.8),
				new Expect("stormstride", Attributes.FALL_DAMAGE_MULTIPLIER, "FALL_DAMAGE_MULTIPLIER", 0.0),
				new Expect("withered_frame", Attributes.MAX_HEALTH, "MAX_HEALTH", 10.0),
				new Expect("feeble", Attributes.ATTACK_DAMAGE, "ATTACK_DAMAGE", 0.4),
				new Expect("molasses", Attributes.MOVEMENT_SPEED, "MOVEMENT_SPEED", speed * 0.6),
				new Expect("stone_hands", Attributes.BLOCK_BREAK_SPEED, "BLOCK_BREAK_SPEED", 0.4),
				new Expect("short_arms", Attributes.BLOCK_INTERACTION_RANGE, "BLOCK_INTERACTION_RANGE", 2.5),
				new Expect("short_arms", Attributes.ENTITY_INTERACTION_RANGE, "ENTITY_INTERACTION_RANGE", 1.5),
				new Expect("crushing_gravity", Attributes.GRAVITY, "GRAVITY", 0.16),
				new Expect("glass_bones", Attributes.FALL_DAMAGE_MULTIPLIER, "FALL_DAMAGE_MULTIPLIER", 3.0),
				new Expect("glass_bones", Attributes.SAFE_FALL_DISTANCE, "SAFE_FALL_DISTANCE", 1.0),
				new Expect("rusted_plating", Attributes.ARMOR, "ARMOR", 0.0),
				new Expect("rusted_plating", Attributes.ARMOR_TOUGHNESS, "ARMOR_TOUGHNESS", 0.0),
				new Expect("leaden_swing", Attributes.ATTACK_SPEED, "ATTACK_SPEED", 2.0),
				new Expect("tinderbox", Attributes.BURNING_TIME, "BURNING_TIME", 4.0),
				new Expect("grounded", Attributes.JUMP_STRENGTH, "JUMP_STRENGTH", 0.0),
				new Expect("last_thread", Attributes.MAX_HEALTH, "MAX_HEALTH", 2.0));

		int changesSeen = 0;
		for (String id : allNew()) {
			if (EffectBehaviors.get(id) instanceof AttributeEffectBehavior attr) {
				changesSeen += attr.changes().size();
			}
		}
		check(changesSeen == table.size(),
				"the expectation table covers every change of every new attribute effect ("
						+ changesSeen + " changes, " + table.size() + " rows)");

		for (Expect row : table) {
			AttributeEffectBehavior behavior = (AttributeEffectBehavior) EffectBehaviors.get(row.id());
			AttributeEffectBehavior.Change change = behavior.changes().stream()
					.filter(c -> c.attribute().equals(row.attribute())).findFirst().orElse(null);
			String label = row.id() + "/" + row.name();
			check(change != null, label + ": the effect really changes this attribute");
			if (change == null) {
				continue;
			}
			// An effect on an attribute the player does not carry is logged and
			// skipped at runtime -- a silent no-op. Checked against the real supplier.
			check(player.hasAttribute(row.attribute()), label + ": the PLAYER entity carries this attribute");

			AttributeInstance instance = new AttributeInstance(row.attribute(), a -> { });
			instance.setBaseValue(playerBase(row.attribute()));
			double afterFirst = 0;
			for (int i = 0; i < 10; i++) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(
						AttributeEffectBehavior.modifierId(row.id()), change.amount(), change.operation()));
				if (i == 0) {
					afterFirst = instance.getValue();
				}
			}
			checkNear(afterFirst, row.value(), 1e-6, label + ": value on a plain player");
			checkNear(instance.getValue(), afterFirst, 1e-12,
					label + ": ten applications leave the value where one put it");
			check(instance.getModifiers().size() == 1, label + ": one modifier, not ten stacked copies");
			instance.removeModifier(AttributeEffectBehavior.modifierId(row.id()));
			checkNear(instance.getValue(), playerBase(row.attribute()), 1e-12,
					label + ": removal restores the base value");
		}

		// Rusted Plating reads as a no-op on a naked player, by design -- it taxes
		// gear. Shown on a full diamond set so the effect is visible rather than
		// hidden behind the 0.0 floor in the table above.
		AttributeInstance armor = new AttributeInstance(Attributes.ARMOR, a -> { });
		armor.setBaseValue(playerBase(Attributes.ARMOR));
		armor.addOrUpdateTransientModifier(new AttributeModifier(
				net.minecraft.resources.Identifier.fromNamespaceAndPath("harness", "diamond_set"),
				20.0, AttributeModifier.Operation.ADD_VALUE));
		AttributeEffectBehavior rusted = (AttributeEffectBehavior) EffectBehaviors.get("rusted_plating");
		armor.addOrUpdateTransientModifier(new AttributeModifier(
				AttributeEffectBehavior.modifierId("rusted_plating"),
				rusted.changes().get(0).amount(), rusted.changes().get(0).operation()));
		checkNear(armor.getValue(), 10.0, 1e-9, "rusted_plating: a full diamond set (20 armor) counts for 10");

		// The two health curses must clamp current health on apply, like Brittle
		// Bones -- lowering the maximum does not lower what the player is holding.
		for (String id : List.of("withered_frame", "last_thread")) {
			check(classReferences(EffectBehaviors.get(id).getClass(), "setHealth"),
					id + ": clamps current health on apply, so the hearts read correctly at once");
		}
	}

	private static void attributeStacking() {
		section("Tier 3/4 attributes: the stacks that could land on a dangerous clamp");

		// MAX_HEALTH. Every health curse in the registry at once.
		double allHealthCurses = valueWith(Attributes.MAX_HEALTH,
				"brittle_bones", "glass_cannon_pact", "withered_frame", "last_thread");
		checkNear(allHealthCurses, 1.0, 1e-9,
				"Brittle Bones + Glass Cannon Pact + Withered Frame + Last Thread bottoms out at "
						+ "MAX_HEALTH's floor of 1.0 -- half a heart, never zero");
		checkNear(valueWith(Attributes.MAX_HEALTH, "withered_frame", "last_thread"), 1.0, 1e-9,
				"Withered Frame x Last Thread is 20 x 0.5 x 0.1 = 1.0");
		checkNear(valueWith(Attributes.MAX_HEALTH, "titan_hide", "last_thread"), 4.0, 1e-9,
				"Titan Hide under Last Thread is a tenth of 40 -- the blessing is worth one more heart");
		checkNear(valueWith(Attributes.MAX_HEALTH, "titan_hide", "thick_hide", "brittle_bones"), 40.0, 1e-9,
				"Titan Hide sums with the Tier 1 health effects in one ADD_VALUE pool: 20 + 20 + 4 - 4");

		// GRAVITY. The floor is the dangerous side; these multiply upward or stay positive.
		Map<String, Double> heavy = new LinkedHashMap<>();
		heavy.put("leaden_legs", 2.0); // All In's doubled form
		heavy.put("crushing_gravity", 1.0);
		double heaviest = instanceWith(Attributes.GRAVITY, playerBase(Attributes.GRAVITY), heavy).getValue();
		checkNear(heaviest, 0.08 * 1.8 * 2.0, 1e-9,
				"Crushing Gravity on a DOUBLED Leaden Legs is 0.288 -- far under GRAVITY's 1.0 ceiling");
		double withMoon = valueWith(Attributes.GRAVITY, "embrace_the_moon", "moon_walker", "crushing_gravity");
		check(withMoon > 0.0,
				"Crushing Gravity stacked on both low-gravity blessings stays POSITIVE ("
						+ String.format("%.5f", withMoon) + ") -- nobody floats away");

		// ATTACK_SPEED. Zero is unrecoverable: the cooldown never refills.
		Map<String, Double> slow = new LinkedHashMap<>();
		slow.put("sluggish_strikes", 2.0);
		slow.put("leaden_swing", 1.0);
		double slowest = instanceWith(Attributes.ATTACK_SPEED, playerBase(Attributes.ATTACK_SPEED), slow).getValue();
		checkNear(slowest, 4.0 * 0.6 * 0.5, 1e-9,
				"Leaden Swing on a DOUBLED Sluggish Strikes is 1.2 swings/s -- slow, and nowhere near 0");

		// ATTACK_DAMAGE. Floors at 0.0, and an additive pool could have reached it.
		double weakest = valueWith(Attributes.ATTACK_DAMAGE, "feeble", "human_machinegun");
		check(weakest > 0.0, "Feeble x Human Machinegun is a product and stays positive ("
				+ String.format("%.3f", weakest) + " bare-handed)");
		checkNear(valueWith(Attributes.ATTACK_DAMAGE, "executioner", "apex_predator"), 6.0, 1e-9,
				"Executioner x Apex Predator is x2 x 3 = x6");
		checkNear(valueWith(Attributes.ATTACK_DAMAGE, "executioner", "feeble"), 0.8, 1e-9,
				"a blessing and a curse on one attribute multiply rather than cancel: x2 x 0.4");

		// MOVEMENT_SPEED.
		double base = playerBase(Attributes.MOVEMENT_SPEED);
		checkNear(valueWith(Attributes.MOVEMENT_SPEED, "sure_footing", "blur"), base * 1.15 * 1.4, 1e-9,
				"Sure Footing (BASE) and Blur (TOTAL) compose as x1.15 x 1.4");
		check(valueWith(Attributes.MOVEMENT_SPEED, "heavy_boots", "molasses") > 0.0,
				"Heavy Boots + Molasses leaves the player moving");

		// Molasses' number is chosen against a threshold, so the threshold is asserted.
		double walk = 4.3172 * 0.6;
		double sprint = 5.6123 * 0.6;
		double creeper = 0.25 * 0.25 / 0.454 * 20.0;
		check(walk < creeper && sprint > creeper,
				"Molasses: walking (" + String.format("%.2f", walk) + " b/s) no longer outruns a creeper ("
						+ String.format("%.2f", creeper) + "), sprinting (" + String.format("%.2f", sprint)
						+ ") still does -- which is what keeps it counterplay = true");

		// Fall damage. Stormstride's zero must survive every increase in the registry.
		checkNear(valueWith(Attributes.FALL_DAMAGE_MULTIPLIER, "glass_jaw", "glass_bones", "stormstride"),
				0.0, 1e-12, "Stormstride x0 beats Glass Jaw and Glass Bones together: still no fall damage");
		double threshold = valueWith(Attributes.SAFE_FALL_DISTANCE, "glass_bones");
		double multiplier = valueWith(Attributes.FALL_DAMAGE_MULTIPLIER, "glass_bones");
		check(Math.floor((2.0 + 1e-6 - threshold) * multiplier) == 3.0
						&& Math.floor((8.0 + 1e-6 - threshold) * multiplier) == 21.0,
				"Glass Bones, by vanilla's own calculateFallDamage: a 2-block drop costs 3 HP and "
						+ "an 8-block one 21 -- lethal from full, where vanilla charges 5");

		// Reach. Short Arms must leave the block underfoot reachable from the eye.
		double reach = valueWith(Attributes.BLOCK_INTERACTION_RANGE, "short_arms");
		check(reach > 1.62, "Short Arms' block reach (" + reach + ") still exceeds eye height (1.62), "
				+ "so the block the player stands on stays reachable");
	}

	/** Apex of a jump by the same per-tick integration the skill validates against vanilla. */
	private static double apex(double jumpStrength, double gravity) {
		double y = 0;
		double v = jumpStrength;
		double best = 0;
		for (int tick = 0; tick < 400 && (v > 0 || y > 0); tick++) {
			y += v;
			best = Math.max(best, y);
			v = (v - gravity) * 0.98;
		}
		return best;
	}

	private static void jumpPhysics() {
		section("Tier 3 jump curses: Crushing Gravity and Grounded, by integration");

		double vanilla = apex(playerBase(Attributes.JUMP_STRENGTH), playerBase(Attributes.GRAVITY));
		checkNear(vanilla, 1.2522, 1e-3,
				"the model reproduces vanilla's known 1.2522-block jump, which is what validates it");

		double crushing = apex(playerBase(Attributes.JUMP_STRENGTH), valueWith(Attributes.GRAVITY, "crushing_gravity"));
		check(crushing < 1.0, "Crushing Gravity: the apex is " + String.format("%.3f", crushing)
				+ " blocks -- a full block is out of reach, as the card says");
		check(crushing > 0.5, "...but a slab is not, and STEP_HEIGHT is untouched");
		check(crushing < apex(playerBase(Attributes.JUMP_STRENGTH), valueWith(Attributes.GRAVITY, "leaden_legs")),
				"...and it is strictly worse than Leaden Legs, the Tier 1 version of the same curse");

		double grounded = valueWith(Attributes.JUMP_STRENGTH, "grounded");
		check(grounded <= 1.0e-5,
				"Grounded: jump power is " + grounded + ", at or under the 1e-5 below which "
						+ "LivingEntity.jumpFromGround returns without doing anything");
		check(valueWith(Attributes.JUMP_STRENGTH, "grounded", "giant_size") <= 1.0e-5,
				"...and Giant Size's jump bonus cannot rescue it: x0 is still 0");
		for (String id : List.of("grounded", "crushing_gravity")) {
			AttributeEffectBehavior behavior = (AttributeEffectBehavior) EffectBehaviors.get(id);
			check(behavior.changes().stream().noneMatch(c -> c.attribute().equals(Attributes.STEP_HEIGHT)),
					id + ": leaves STEP_HEIGHT alone -- slabs and stairs are the counterplay");
		}
		checkNear(valueWith(Attributes.STEP_HEIGHT, "mountain_goat"), 1.25, 1e-9,
				"Mountain Goat is the answer to every jump curse: a 1.25 step walks up a full block");
	}

	// =====================================================================
	// Status effects.
	// =====================================================================

	private record Status(String id, Holder<MobEffect> effect, int amplifier) {}

	private static void statusFamily() {
		section("Tier 3/4 status effects: refreshed vanilla MobEffectInstances");

		List<Status> table = List.of(
				new Status("night_eyes", MobEffects.NIGHT_VISION, 0),
				new Status("salamander_blood", MobEffects.FIRE_RESISTANCE, 0),
				new Status("gills", MobEffects.WATER_BREATHING, 0),
				new Status("stoneskin", MobEffects.RESISTANCE, 1),
				new Status("second_wind", MobEffects.REGENERATION, 0),
				new Status("overclocked", MobEffects.HASTE, 1),
				new Status("village_hero", MobEffects.HERO_OF_THE_VILLAGE, 0),
				new Status("featherfall", MobEffects.SLOW_FALLING, 0),
				new Status("shadow_blind", MobEffects.DARKNESS, 0),
				new Status("marked", MobEffects.BAD_OMEN, 0),
				new Status("infested", MobEffects.INFESTED, 0),
				new Status("venom", MobEffects.POISON, 0));

		long statusCount = allNew().stream()
				.filter(id -> EffectBehaviors.get(id) instanceof StatusEffectBehavior).count();
		check(statusCount == table.size(), "the table covers every status effect (" + statusCount + ")");

		for (Status row : table) {
			EffectBehavior behavior = EffectBehaviors.get(row.id());
			check(behavior instanceof StatusEffectBehavior, row.id() + ": is a StatusEffectBehavior");
			if (!(behavior instanceof StatusEffectBehavior status)) {
				continue;
			}
			check(status.effect().equals(row.effect()), row.id() + ": grants the intended vanilla effect");
			check(status.amplifier() == row.amplifier(),
					row.id() + ": amplifier " + row.amplifier() + " (level " + (row.amplifier() + 1) + ")");
			check(constant(behavior.getClass(), "AMPLIFIER") == row.amplifier(),
					row.id() + ": ...and that is the class's own AMPLIFIER constant");
		}

		int duration = (int) constant(StatusEffectBehavior.class, "DURATION_TICKS");
		int refresh = (int) constant(StatusEffectBehavior.class, "REFRESH_INTERVAL_TICKS");
		check(duration == 600 && refresh == 100, "grants are 600 ticks, renewed every 100");
		int floor = duration - refresh;
		check(floor > 200, "the remaining duration never drops below " + floor
				+ " -- above the 200 at which the client starts blinking Night Vision");
		check(floor > 20, "...and above the 20-tick fade Darkness and Blindness render over (1aa)");
		check(duration != -1, "the grant is finite, never MobEffectInstance.INFINITE_DURATION: "
				+ "an infinite effect lives in the player's NBT and would outlive the mod");

		// Periodic effects, asked of VANILLA's own schedule rather than restated.
		// The duration cycles 600 -> 501 and is reset every 100 ticks.
		MobEffect poison = MobEffects.POISON.value();
		MobEffect regeneration = MobEffects.REGENERATION.value();
		check(poison.shouldApplyEffectTickThisTick(25, 0) && !poison.shouldApplyEffectTickThisTick(24, 0),
				"vanilla's Poison I really does fire on duration % 25 == 0");
		check(regeneration.shouldApplyEffectTickThisTick(50, 0) && !regeneration.shouldApplyEffectTickThisTick(25, 0),
				"vanilla's Regeneration I really does fire on duration % 50 == 0");
		List<Integer> poisonTicks = new ArrayList<>();
		int regenTicks = 0;
		int remaining = duration;
		for (int tick = 0; tick < 1000; tick++) {
			if (tick % refresh == 0) {
				remaining = duration; // the service's renewal
			}
			if (poison.shouldApplyEffectTickThisTick(remaining, 0)) {
				poisonTicks.add(tick);
			}
			if (regeneration.shouldApplyEffectTickThisTick(remaining, 0)) {
				regenTicks++;
			}
			remaining--;
		}
		check(poisonTicks.size() == 40, "Venom lands exactly 40 times in 1000 ticks across ten "
				+ "renewals -- one every 25, as if the effect had never been reset");
		boolean even = true;
		for (int i = 1; i < poisonTicks.size(); i++) {
			even &= poisonTicks.get(i) - poisonTicks.get(i - 1) == 25;
		}
		check(even, "...with every gap exactly 25 ticks: a renewal neither skips nor doubles a hit");
		check(regenTicks == 20, "Second Wind heals exactly 20 times in 1000 ticks -- one every 50");

		// The cadence itself, and the things the service must not do.
		int refreshes = 0;
		for (int tick = 0; tick < 2000; tick++) {
			if (StatusEffectService.isRefreshTick(tick)) {
				refreshes++;
			}
		}
		check(refreshes == 20, "the service renews on exactly one tick in 100");
		check(classReferences(StatusEffectBehavior.class, "addEffect"),
				"apply() grants through vanilla's own addEffect -- which also restores the effect "
						+ "on respawn without waiting for the next renewal");
		check(!classReferences(StatusEffectBehavior.class, "removeEffect"),
				"...and never calls removeEffect, which would strip a potion's copy of the same effect");
		check(!classReferences(StatusEffectService.class, "getRunState")
						&& !classReferences(StatusEffectService.class, "isEnded"),
				"the service does not consult the run state -- ENDED stops the loop and nothing else");
		check(classReferences(StatusEffectService.class, "StatusEffectBehavior"),
				"the service finds its effects by TYPE, so a new status effect needs no edit there");

		// Venom's claim, from vanilla's two numbers.
		check(!EffectRegistry.byId("venom").counterplay(), "Venom is counterplay = false");
		check(80.0 / 25.0 > 1.0, "Poison's 1 HP per 25 ticks outpaces natural regeneration's best "
				+ "1 HP per 80, so the player is held at the 1.0 HP Poison refuses to go below");
	}

	// =====================================================================
	// Hook multipliers.
	// =====================================================================

	private static void hookMultipliers() {
		section("Tier 3/4 hook effects: terms on the existing EffectHooks multipliers");

		Map<String, Double> expected = new LinkedHashMap<>();
		expected.put("bottomless_stomach", 0.0);
		expected.put("prodigy", 3.0);
		expected.put("juggernaut", 0.4);
		expected.put("paper_skin", 2.0);
		expected.put("starving", 3.0);
		expected.put("forgetful", 0.0);

		for (Map.Entry<String, Double> entry : expected.entrySet()) {
			EffectBehavior behavior = EffectBehaviors.get(entry.getKey());
			check(behavior instanceof HookEffectBehavior,
					entry.getKey() + ": a HookEffectBehavior -- membership in the acquired set IS the effect");
			checkNear(constant(behavior.getClass(), "MULTIPLIER"), entry.getValue(), 1e-6,
					entry.getKey() + ": MULTIPLIER");
			check(classReferences(EffectHooks.class, behavior.getClass().getSimpleName()),
					entry.getKey() + ": EffectHooks reads it");
		}

		// Each escalates the Tier 1 effect on the same hook.
		check(constant(EffectBehaviors.get("paper_skin").getClass(), "MULTIPLIER")
						> constant(com.entropymod.entropy.behavior.FragileBehavior.class, "MULTIPLIER"),
				"Paper Skin (x2) is worse than Fragile (x1.25)");
		check(constant(EffectBehaviors.get("juggernaut").getClass(), "MULTIPLIER")
						< constant(com.entropymod.entropy.behavior.IronSkinBehavior.class, "MULTIPLIER"),
				"Juggernaut (x0.4) is better than Iron Skin (x0.8)");
		check(constant(EffectBehaviors.get("starving").getClass(), "MULTIPLIER")
						> constant(com.entropymod.entropy.behavior.GrowlingStomachBehavior.class, "MULTIPLIER"),
				"Starving (x3) is worse than Growling Stomach (x1.25)");
		check(constant(EffectBehaviors.get("prodigy").getClass(), "MULTIPLIER")
						> constant(com.entropymod.entropy.behavior.FastLearnerBehavior.class, "MULTIPLIER"),
				"Prodigy (x3) is better than Fast Learner (x1.5)");

		// The damage product can never reach zero; the other two are zero on purpose.
		double worstDamage = 2.0 * constant(com.entropymod.entropy.behavior.FragileBehavior.class, "MULTIPLIER");
		double bestDamage = 0.4 * constant(com.entropymod.entropy.behavior.IronSkinBehavior.class, "MULTIPLIER")
				* constant(com.entropymod.entropy.behavior.GiantSizeBehavior.class, "DAMAGE_MULTIPLIER");
		check(bestDamage > 0.0 && worstDamage < 10.0,
				"every damage-taken factor is positive: the best stack is x"
						+ String.format("%.2f", bestDamage) + ", the worst x" + String.format("%.2f", worstDamage));
		check(EffectHooks.scaleAmount(7, 0.0f) == 0 && EffectHooks.scaleAmount(1, 3.0f) == 3,
				"through the real rounding helper: Forgetful turns 7 XP into 0, Prodigy turns 1 into 3");

		// The mixins these ride on are the ORIGINAL three, and each still reads its hook.
		check(mixinClassContains("PlayerExhaustionMixin", "exhaustionMultiplier"),
				"PlayerExhaustionMixin still reads exhaustionMultiplier");
		check(mixinClassContains("LivingEntityDamageMixin", "damageTakenMultiplier"),
				"LivingEntityDamageMixin still reads damageTakenMultiplier");
		check(mixinClassContains("PlayerExperienceMixin", "experienceMultiplier"),
				"PlayerExperienceMixin still reads experienceMultiplier");
	}

	// =====================================================================
	// Sunburn.
	// =====================================================================

	private static void sunburn() {
		section("Sunburn: vanilla's undead-burn rule, as one decision function");

		check(EffectBehaviors.get(SunburnBehavior.ID) instanceof HookEffectBehavior,
				"Sunburn applies nothing to the player -- the service acts on membership");

		int igniting = 0;
		for (int mask = 0; mask < 32; mask++) {
			boolean burn = (mask & 1) != 0;
			boolean sky = (mask & 2) != 0;
			boolean wet = (mask & 4) != 0;
			boolean hat = (mask & 8) != 0;
			boolean exempt = (mask & 16) != 0;
			if (SunburnBehavior.shouldIgnite(burn, sky, wet, hat, exempt)) {
				igniting++;
				check(burn && sky && !wet && !hat && !exempt,
						"the one igniting row is: sun up, sky visible, dry, bare-headed, not exempt");
			}
		}
		check(igniting == 1, "exactly 1 of the 32 input combinations ignites");
		check(!SunburnBehavior.shouldIgnite(true, true, false, true, false),
				"a helmet is a complete answer");
		check(!SunburnBehavior.shouldIgnite(true, false, false, false, false), "so is a roof");
		check(!SunburnBehavior.shouldIgnite(true, true, true, false, false), "so is water or rain");
		check(!SunburnBehavior.shouldIgnite(false, true, false, false, false), "so is night");
		check(!SunburnBehavior.shouldIgnite(true, true, false, false, true),
				"creative and spectator players are never set alight");
		check(EffectRegistry.byId(SunburnBehavior.ID).counterplay(),
				"which is four answers, so counterplay = true");

		checkNear(constant(SunburnBehavior.class, "IGNITE_SECONDS"), 8.0, 1e-9,
				"ignites for 8 seconds -- the value Mob.burnUndead passes to igniteForSeconds");
		check((int) constant(SunburnBehavior.class, "CHECK_INTERVAL_TICKS") == 20, "checked once a second");

		for (String name : List.of("MONSTERS_BURN", "isBrightOutside", "canSeeSky", "isInWaterOrRain",
				"igniteForSeconds", "shouldIgnite")) {
			check(classReferences(SunburnService.class, name), "the service reads " + name);
		}
		check(!classReferences(SunburnService.class, "getRunState"),
				"the service does not consult the run state");
	}

	// =====================================================================
	// Persistence.
	// =====================================================================

	private static void persistence() {
		section("Tier 3/4: persistence and no-repeat");

		EntropyManager before = new EntropyManager();
		for (String id : allNew()) {
			before.acquired().add(id);
		}
		JsonElement json = EntropyManager.CODEC.encodeStart(JsonOps.INSTANCE, before)
				.getOrThrow(msg -> new IllegalStateException("encode failed: " + msg));
		EntropyManager after = EntropyManager.CODEC.parse(JsonOps.INSTANCE, json)
				.getOrThrow(msg -> new IllegalStateException("decode failed: " + msg));
		boolean allSurvive = allNew().stream().allMatch(id -> after.acquired().contains(id));
		check(allSurvive && after.acquired().size() == 42,
				"all 42 survive a save and reload through the real EntropyManager codec, so "
						+ "reapplyAll has them to restore");
		check(after.acquired().unknownIds().isEmpty(), "...and none is dropped as an unknown id on load");

		AcquiredEffects held = new AcquiredEffects();
		for (String id : allNew()) {
			held.add(id);
		}
		boolean reoffered = false;
		for (int entropy : new int[] {61, 80, 81, 99}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new Random(7), held.ids(), Set.of());
				// Everything eligible is held, so the only way to offer anything is the
				// repeat fallback -- which must be flagged, never silent.
				reoffered |= !roll.choices().isEmpty() && !roll.repeatFallback();
			}
		}
		check(!reoffered, "no-repeat holds in the new bands: a fully-drained pool reports the "
				+ "repeat fallback rather than silently re-offering");

		Set<EffectCategory> used = java.util.EnumSet.noneOf(EffectCategory.class);
		for (String id : allNew()) {
			used.add(EffectRegistry.byId(id).category());
		}
		check(used.size() >= 7, "the new effects spread across " + used.size()
				+ " of the nine categories: " + used);
	}

	// =====================================================================
	// Mixin firing probes.
	// =====================================================================

	private static void mixinProbes() {
		section("Mixin probes: every handler reports when it RUNS (headless: wiring only)");

		List<String> common = configEntries(resource("/entropymod.mixins.json"), "mixins");
		List<String> client = configEntries(
				readFile("src/client/resources/entropymod.client.mixins.json"), "client");
		check(common.size() == 38 && client.size() == 9,
				"the two configs list 38 common and 9 client mixins: " + common.size() + "/" + client.size());

		// Every config entry has a probe -- with exactly one stated exception.
		for (String entry : common) {
			boolean probed = probesFor(entry, MixinProbe.Side.COMMON) > 0;
			if (entry.equals("LlamaStrengthAccessor")) {
				check(!probed && mixinClassContains(entry, "Invoker"),
						entry + ": the one unprobed mixin -- an @Invoker has no body to probe");
			} else {
				check(probed, entry + ": has a firing probe");
			}
		}
		for (String entry : client) {
			check(probesFor(entry, MixinProbe.Side.CLIENT) > 0, entry + ": has a firing probe (client)");
		}
		for (MixinProbe probe : MixinProbe.values()) {
			List<String> config = probe.side() == MixinProbe.Side.COMMON ? common : client;
			check(config.contains(probe.mixinClass()),
					probe.name() + ": names a mixin that is really in the " + probe.side() + " config");
		}
		check(MixinProbe.values().length == 49,
				"49 probe points: 46 mixin classes, three of which carry two handlers");

		// COMMON: decoded from bytecode. The probe must be the handler's FIRST
		// instructions -- getstatic MixinProbe.X, invokevirtual hit -- so it reports
		// that the injection point was reached, not that the effect's gates passed.
		for (MixinProbe probe : MixinProbe.values()) {
			if (probe.side() != MixinProbe.Side.COMMON) {
				continue;
			}
			Class<?> mixin = loadWithoutInit("com.entropymod.mixin." + probe.mixinClass());
			byte[] code = Checks.codeOf(mixin, probe.handler());
			boolean opensWithProbe = code.length >= 6
					&& (code[0] & 0xFF) == 0xb2   // getstatic
					&& (code[3] & 0xFF) == 0xb6;  // invokevirtual
			check(opensWithProbe && classReferences(mixin, probe.name())
							&& classReferences(mixin, "com/entropymod/entropy/probe/MixinProbe"),
					probe.label() + ": opens with MixinProbe." + probe.name() + ".hit(), before any gate");
		}

		// CLIENT: src/client is not on the harness classpath (finding 1bh), so these
		// are source-text checks and are labelled as the weaker kind they are.
		for (MixinProbe probe : MixinProbe.values()) {
			if (probe.side() != MixinProbe.Side.CLIENT) {
				continue;
			}
			String source = readFile("src/client/java/com/entropymod/client/mixin/"
					+ probe.mixinClass() + ".java").replace("\r\n", "\n");
			int at = source.indexOf(" " + probe.handler() + "(");
			int brace = at < 0 ? -1 : source.indexOf("{\n", at);
			String firstLine = brace < 0 ? "" : source.substring(brace + 2).lines().findFirst().orElse("").trim();
			check(firstLine.equals("MixinProbe." + probe.name() + ".hit();"),
					"[source-text] " + probe.label() + ": opens with MixinProbe." + probe.name() + ".hit()");
		}

		// The probe itself.
		MixinProbe.resetForTest();
		check(MixinProbe.firedCount() == 0, "a fresh session has seen nothing fire");
		MixinProbe sample = MixinProbe.PLAYER_EXHAUSTION;
		check(!sample.hasFired(), "a handler that has not run reports NOT fired");
		sample.hit();
		check(sample.hasFired() && MixinProbe.firedCount() == 1, "hit() records the handler as fired");
		for (int i = 0; i < 1000; i++) {
			sample.hit();
		}
		check(MixinProbe.firedCount() == 1, "...and a thousand more calls change nothing: it is a latch");
		check(MixinProbe.list(MixinProbe.Side.COMMON, true).equals(List.of(sample)),
				"the fired list is exactly the handlers that ran");
		check(MixinProbe.list(MixinProbe.Side.COMMON, false).size()
						+ MixinProbe.list(MixinProbe.Side.CLIENT, false).size() == 48,
				"...and the pending list is everything else, which is the play-test to-do");
		MixinProbe.resetForTest();

		check(!classReferences(MixinProbe.class, "net/minecraft"),
				"MixinProbe has no Minecraft imports, so it loads before bootstrap and in the harness");
		check(!MixinProbe.class.getPackageName().contains("mixin"),
				"...and lives outside the mixin packages, which Mixin forbids ordinary code to load from");
		check(classReferences(com.entropymod.command.EntropyCommands.class, "entropymixins"),
				"/entropymixins is registered, so the state is readable in game and not only in the log");

		System.out.println("  NOTE  the harness does not run the Mixin transformer. These checks prove every "
				+ "handler REPORTS when it runs; that each one actually runs is observed in game with "
				+ "/entropymixins and the '[mixin-probe] FIRED' log lines.");
	}

	private static int probesFor(String mixinClass, MixinProbe.Side side) {
		int count = 0;
		for (MixinProbe probe : MixinProbe.values()) {
			if (probe.side() == side && probe.mixinClass().equals(mixinClass)) {
				count++;
			}
		}
		return count;
	}

	private static List<String> configEntries(String json, String key) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		List<String> entries = new ArrayList<>();
		object.getAsJsonArray(key).forEach(e -> entries.add(e.getAsString()));
		return entries;
	}

	private static Class<?> loadWithoutInit(String name) {
		try {
			return Class.forName(name, false, Tier3Checks.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("No such mixin class " + name, e);
		}
	}

	/** Scans a common mixin's class file without loading the class. */
	private static boolean mixinClassContains(String simpleName, String text) {
		String path = "/com/entropymod/mixin/" + simpleName + ".class";
		try (java.io.InputStream in = Tier3Checks.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("No class file at " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains(text);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static String resource(String path) {
		try (java.io.InputStream in = Tier3Checks.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("No resource at " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static String readFile(String relativePath) {
		try {
			return Files.readString(Path.of(relativePath));
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read " + relativePath, e);
		}
	}

	private Tier3Checks() {}
}
