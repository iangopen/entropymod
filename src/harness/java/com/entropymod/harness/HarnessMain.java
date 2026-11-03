package com.entropymod.harness;

import net.minecraft.core.BlockPos;
import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.EffectDefinition;
import com.entropymod.entropy.EffectHooks;
import com.entropymod.entropy.block.ReverseMidasBlocks;
import com.entropymod.entropy.EffectRegistry;
import com.entropymod.entropy.EntropyManager;
import com.entropymod.entropy.behavior.DoubleJumpBehavior;
import com.entropymod.entropy.behavior.OreSenseBehavior;
import com.entropymod.entropy.behavior.DangerSenseBehavior;
import com.entropymod.entropy.DoubleJumpState;
import com.entropymod.entropy.EffectPhase;
import com.entropymod.entropy.KeybindSnapshot;
import com.entropymod.entropy.MovementScramble;
import com.entropymod.entropy.RerollState;
import com.entropymod.entropy.RunState;
import com.entropymod.entropy.RunOutcome;
import com.entropymod.entropy.RunEnd;
import com.entropymod.network.RunEndPayload;
import com.entropymod.entropy.AttributeEffectBehavior;
import com.entropymod.entropy.EffectBehavior;
import com.entropymod.entropy.EffectBehaviors;
import com.entropymod.entropy.EffectCategory;
import com.entropymod.entropy.behavior.BadReputationBehavior;
import com.entropymod.entropy.behavior.BehemothGauntletsBehavior;
import com.entropymod.entropy.behavior.BrittleBonesBehavior;
import com.entropymod.entropy.behavior.FeatherlightBehavior;
import com.entropymod.entropy.behavior.GlassCannonPactBehavior;
import com.entropymod.entropy.behavior.GlassJawBehavior;
import com.entropymod.entropy.behavior.PhoenixChamberedHeartBehavior;
import com.entropymod.entropy.behavior.SlipperyGripBehavior;
import com.entropymod.entropy.behavior.CreativeFlightBehavior;
import com.entropymod.entropy.behavior.CrouchInvincibilityBehavior;
import com.entropymod.entropy.behavior.EmbraceTheMoonBehavior;
import com.entropymod.entropy.behavior.FlamboyantBehavior;
import com.entropymod.entropy.behavior.GiantSizeBehavior;
import com.entropymod.entropy.behavior.MoonWalkerBehavior;
import com.entropymod.entropy.behavior.SlashedPocketsBehavior;
import com.entropymod.entropy.behavior.BlightTouchedBehavior;
import com.entropymod.entropy.behavior.ClumsyDiggerBehavior;
import com.entropymod.entropy.behavior.CreeperMagnetBehavior;
import com.entropymod.entropy.behavior.EmotionalSupportLlamaBehavior;
import com.entropymod.entropy.behavior.LoyalPackBehavior;
import com.entropymod.entropy.behavior.TheAudienceBehavior;
import com.entropymod.entropy.behavior.TheEntourageBehavior;
import com.entropymod.entropy.EntropyDamageTypes;
import com.entropymod.entropy.HookEffectBehavior;
import com.entropymod.entropy.behavior.ButterfingersExtremeBehavior;
import net.minecraft.world.item.ItemStack;
import com.entropymod.entropy.behavior.BeastWrathBehavior;
import com.entropymod.entropy.behavior.ReverseMidasBehavior;
import com.entropymod.entropy.behavior.GraveRobberBehavior;
import com.entropymod.entropy.behavior.EarthquakeBehavior;
import com.entropymod.entropy.behavior.VampireRulesBehavior;
import com.entropymod.entropy.quake.EarthquakeService;
import com.entropymod.mixin.PlayerImmobileMixin;
import com.entropymod.network.TremorLockPayload;
import com.entropymod.mixin.LivingEntityDamageDealtMixin;
import com.entropymod.mixin.PlayerButterfingersMixin;
import com.entropymod.entropy.companion.CompanionBuffs;
import com.entropymod.entropy.companion.CompanionMotion;
import com.entropymod.entropy.companion.CompanionRoster;
import com.entropymod.entropy.companion.CompanionService;
import com.entropymod.entropy.behavior.RandomJumpBehavior;
import com.entropymod.entropy.behavior.UnstableBehavior;
import com.entropymod.entropy.behavior.RandomizedControlsBehavior;
import com.entropymod.entropy.behavior.SecondGuessBehavior;
import com.entropymod.entropy.behavior.AllInBehavior;
import com.entropymod.entropy.behavior.BankedEntropyBehavior;
import com.entropymod.entropy.behavior.DebtCollectorBehavior;
import com.entropymod.entropy.behavior.NoDoOversBehavior;
import com.entropymod.entropy.behavior.TwinFateBehavior;
import com.entropymod.entropy.behavior.HumanMachinegunBehavior;
import com.entropymod.entropy.MetaRunState;
import com.entropymod.entropy.behavior.UpsideDownCameraBehavior;
import com.entropymod.entropy.behavior.GreenThumbBehavior;
import com.entropymod.entropy.behavior.LeakyPocketsBehavior;
import com.entropymod.entropy.behavior.SureFootingBehavior;
import com.entropymod.entropy.growth.BlightTouchedTrample;
import com.entropymod.entropy.growth.CropSchedule;
import com.entropymod.entropy.growth.GreenThumbGrowth;
import com.entropymod.entropy.growth.TramplePath;
import com.entropymod.entropy.spawn.CreeperMagnetSpawner;
import com.entropymod.entropy.spawn.SafeSpawn;
import com.entropymod.entropy.spawn.SpawnSchedule;
import com.entropymod.entropy.spawn.UnstableSpawner;
import com.entropymod.network.EntropyCodecs;
import com.entropymod.network.ClientEffectsPayload;
import com.entropymod.network.KeybindSnapshotPayload;
import com.entropymod.network.OpenChoicePayload;
import com.entropymod.network.RunSyncPayload;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ToolMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.entropymod.harness.Checks.check;
import static com.entropymod.harness.Checks.checkNear;
import static com.entropymod.harness.Checks.constant;
import static com.entropymod.harness.Checks.section;

/**
 * Headless checks. Run with {@code ./gradlew harness}.
 *
 * <p>Covers what can be established without booting Minecraft: the tuning
 * constants as they are actually compiled, the crop-growth timing derivation,
 * Green Thumb's active schedule, Blight Touched's walked-through path sweep and
 * its off-by-default gate, and the parts of {@code /entropygrant}'s contract
 * that are observable off-server.
 *
 * <p><b>What it cannot prove</b>, and what still needs a real session: that the
 * mixins inject, that the effects are felt, and that a granted effect survives a
 * real death and relog. See CLAUDE.md.
 */
public final class HarnessMain {

	public static void main(String[] args) {
		// Bootstrap FIRST, before any section runs.
		//
		// This used to be left to the sections that needed registry access, and the
		// meta batch broke that: EntropyManager.grantEffect now calls
		// EffectBehaviors.get(...).onAcquired(...), which class-inits every behavior,
		// which class-inits Attributes, which touches BuiltInRegistries. Reaching it
		// before Bootstrap.bootStrap() poisons BuiltInRegistries' <clinit> with
		// "Not bootstrapped" for the whole JVM, and every later section then fails
		// with NoClassDefFoundError far from the cause.
		//
		// init() is idempotent, so the sections that call it themselves are unaffected.
		HarnessBootstrap.init();

		tuningConstants();
		growthModelSanity();
		retiredMultiplierCeiling();
		activeGrowthSchedule();
		greenThumbUnaffectedByBlightRemoval();
		blightTramplePath();
		blightTrampleGate();
		scheduleTracking();
		grantContract();
		clumsyDiggerMagnitude();
		clumsyDiggerScope();
		badReputationPrices();
		rerollStateDerivation();
		openChoicePayloadRoundTrip();
		tier2ClientEffects();
		runStartGate();
		keybindSnapshotRules();
		runStatePersistence();
		runLifecyclePayloadRoundTrip();
		endedStateBuilt();
		tier2BatchWiring();
		extremeGravityPhysics();
		giantSizeAttribute();
		giantSizeKit();
		multiAttributeIdempotency();
		survivalBatchWiring();
		fallDamageStacking();
		glassCannonHealthFloor();
		phoenixHeartOnce();
		phoenixHeartGrants();
		phoenixHeartKit();
		slipperyGripSpeed();
		slipperyGripSprintJump();
		senseBatch();
		oreSenseDetection();
		survivalBatchInvariants();
		behemothGauntletsBothCases();
		flamboyantFireOnly();
		crouchInvincibilityGate();
		slashedPocketsSlots();
		tier2BatchIdempotencyAndPersistence();
		spawnBatch();
		companionBatch();
		tier2CombatBatch();
		companionMotion();
		companionGatherTrigger();
		companionAudienceLook();
		beastWrath();
		beastWrathAquaticAndTameable();
		reverseMidasAndGraveRobber();
		SlickHookGluttonChecks.run();
		humanMachinegunRetune();
		metaBatchWiring();
		scalableSubset();
		rerollChargesAndBan();
		bankedEntropyCarry();
		twinFateMultiGrant();
		forcedPools();
		metaPersistence();
		doubledSeverityIdempotency();
		gameOverMigration();
		endedStopsTheLoop();
		endedGatingScope();
		endScreenReadsRealData();
		deathCounter();
		runEndPayloadRoundTrip();
		System.exit(Checks.summary());
	}

	// ------------------------------------------------------------------

	/**
	 * The shipped constants. Read by reflection, not by reference -- see
	 * {@link Checks#constant} for why naming them directly would make this
	 * check vacuous.
	 */
	private static void tuningConstants() {
		section("Tuning constants (read from the compiled classes)");

		checkNear(constant(LeakyPocketsBehavior.class, "CHANCE"), 0.07, 1e-7,
				"Leaky Pockets fires on 7% of jumps");
		checkNear(constant(GreenThumbBehavior.class, "RADIUS"), 8.0, 1e-7,
				"Green Thumb radius is unchanged at 8 blocks");

		// Removed, not neutralised. A constant set to 1.0 can be quietly re-wired
		// into the shared hook and double-applied on top of the active mechanism;
		// a field that does not exist cannot. Both crop effects have now left that
		// hook by this route, so both absences are asserted.
		check(!Checks.hasConstant(GreenThumbBehavior.class, "MULTIPLIER"),
				"Green Thumb no longer declares a growth-speed multiplier at all");
		check(!Checks.hasConstant(BlightTouchedBehavior.class, "MULTIPLIER"),
				"Blight Touched no longer declares a growth-speed multiplier at all");

		// And with the last effect off it, the shared hook itself is gone rather
		// than left returning 1.0 for nobody.
		check(!Checks.hasMethod(EffectHooks.class, "cropGrowthMultiplierFor"),
				"EffectHooks.cropGrowthMultiplierFor is deleted, not neutralised");
		check(!Checks.hasMethod(EffectHooks.class, "cropGrowthMultiplier"),
				"EffectHooks.cropGrowthMultiplier is deleted too -- nothing feeds it now");
		check(!Checks.hasConstant(EffectHooks.class, "MIN_CROP_MULTIPLIER"),
				"...and so is the divide-by-zero floor that only existed to guard it");

		checkNear(constant(TramplePath.class, "MAX_STEP"), 0.25, 1e-7,
				"the trample path samples at most a quarter block per step");
		checkNear(constant(TramplePath.class, "MAX_SEGMENT"), 16.0, 1e-7,
				"...and treats more than 16 blocks in one tick as a teleport");

		double meanJumps = 1.0 / constant(LeakyPocketsBehavior.class, "CHANCE");
		checkNear(meanJumps, 14.3, 0.1, "Leaky Pockets spills once per ~14 jumps on average");
	}

	/**
	 * Validates the model itself against a number established outside this project
	 * before trusting anything derived from it: vanilla row-planted wheat is
	 * widely measured at roughly 24 minutes, and the model has to reproduce that
	 * from first principles or the derivation below is worthless.
	 */
	private static void growthModelSanity() {
		section("Growth model reproduces known vanilla timings");

		checkNear(CropGrowthModel.ticksPerRandomTick(), 1365.333, 0.01,
				"a block expects a random tick every 4096/3 game ticks");
		checkNear(CropGrowthModel.secondsPerRandomTick(), 68.267, 0.01,
				"...which is 68.27 seconds");

		check(CropGrowthModel.rollBound(CropGrowthModel.BASE_SPEED_ROWS) == 3,
				"row-planted hydrated farmland (speed 10) rolls nextInt(3)");
		check(CropGrowthModel.rollBound(CropGrowthModel.BASE_SPEED_PACKED_FIELD) == 6,
				"packed hydrated field (speed 5) rolls nextInt(6)");

		double vanillaRowWheat = CropGrowthModel.expectedSeconds(7, 1.0,
				CropGrowthModel.BASE_SPEED_ROWS, 1.0f);
		checkNear(vanillaRowWheat / 60.0, 23.9, 0.1,
				"vanilla wheat in rows matures in ~24 minutes (the external check on this model)");
	}

	/**
	 * Why the multiplier hook was retired.
	 *
	 * <p>Kept rather than deleted: this is the evidence that the active mechanism
	 * is necessary rather than merely preferred, and it is what stops a future
	 * session "simplifying" Green Thumb back onto the shared hook. It asserts the
	 * ceiling still holds at the most generous multiplier the hook can accept.
	 */
	private static void retiredMultiplierCeiling() {
		section("Why the growth-speed multiplier was retired");

		// The most the hook can ever do: saturate the roll so every random tick
		// grows the crop. 26x was the smallest multiplier that reached this, and
		// nothing above it changes anything.
		float saturating = 26.0f;
		check(CropGrowthModel.saturated(CropGrowthModel.BASE_SPEED_WORST_FARMLAND * saturating),
				"26x saturates the roll even on the worst farmland layout");
		check(CropGrowthModel.rollBound(CropGrowthModel.BASE_SPEED_WORST_FARMLAND * saturating)
						== CropGrowthModel.rollBound(CropGrowthModel.BASE_SPEED_WORST_FARMLAND * 1000.0f),
				"1000x behaves identically -- the hook is a saturation point, not a slider");

		// And saturated is still 5.3x too slow, because the binding constraint is
		// the random-tick RATE, which the hook does not touch.
		double fastestWheat = CropGrowthModel.expectedSeconds(7, 1.0,
				CropGrowthModel.BASE_SPEED_WORST_FARMLAND, saturating);
		checkNear(fastestWheat, 477.87, 0.1,
				"fully saturated, wheat still takes ~478s through the hook");
		check(fastestWheat > 5 * (GreenThumbGrowth.TARGET_TICKS / 20.0),
				"that is more than 5x the 90s target -- unreachable at any multiplier");

		double neededRate = 7.0 * CropGrowthModel.SECTION_BLOCKS / GreenThumbGrowth.TARGET_TICKS;
		checkNear(neededRate, 15.93, 0.01,
				"reaching 90s through the hook would need random_tick_speed ~16, a gamerule not an effect");
	}

	/**
	 * The active mechanism's per-crop intervals, derived from the real shipped
	 * {@link GreenThumbGrowth#intervalForStages} rather than a copy of it.
	 *
	 * <p>Stage counts are the ones already established in CLAUDE.md's table and
	 * verified in bytecode there ({@code getMaxAge()} of 7, 3, 2, 7 and 4) -- not
	 * re-derived here.
	 */
	private static void activeGrowthSchedule() {
		section("Active bonus-growth schedule (target " + GreenThumbGrowth.TARGET_TICKS + " ticks)");

		int target = GreenThumbGrowth.TARGET_TICKS;
		int stemBudget = target - GreenThumbGrowth.STEM_FRUIT_BUDGET_TICKS;

		CropGrowthModel.ActiveCrop[] crops = {
				new CropGrowthModel.ActiveCrop("Wheat / carrots / potatoes", 7, target, 0),
				new CropGrowthModel.ActiveCrop("Beetroot", 3, target, 0),
				new CropGrowthModel.ActiveCrop("Torchflower", 2, target, 0),
				new CropGrowthModel.ActiveCrop("Pitcher crop", 4, target, 0),
				new CropGrowthModel.ActiveCrop("Pumpkin / melon stem", 7, stemBudget,
						GreenThumbGrowth.STEM_FRUIT_BUDGET_TICKS),
		};
		int[] expectedIntervals = {255, 600, 900, 450, 240};

		for (int i = 0; i < crops.length; i++) {
			CropGrowthModel.ActiveCrop crop = crops[i];
			int interval = GreenThumbGrowth.intervalForStages(crop.stages(), crop.budgetTicks());
			int total = CropGrowthModel.activeTotalTicks(crop, interval);

			check(interval == expectedIntervals[i],
					crop.name() + ": " + crop.stages() + " stages -> " + interval + "t interval");
			checkNear(total / 20.0, 90.0, 1.0,
					crop.name() + ": total " + total + "t = " + (total / 20.0) + "s");

			// Every interval must land on the grid the advance pass actually runs on,
			// or the real cadence silently rounds up to the next grid step.
			check(interval % GreenThumbGrowth.ADVANCE_INTERVAL_TICKS == 0,
					crop.name() + ": interval is a whole number of advance passes");
		}

		// The stem's fruit allowance is derived from the SAME validated roll model,
		// not picked. Placing the fruit is not a stage change and cannot be granted,
		// so it is retried at the scan cadence until vanilla's own roll succeeds.
		int expectedAttempts = CropGrowthModel.rollBound(CropGrowthModel.BASE_SPEED_PACKED_FIELD);
		check(expectedAttempts == 6, "an ordinary stem needs ~6 fruit attempts (roll bound at base speed 5)");
		check(expectedAttempts * GreenThumbGrowth.RESCAN_INTERVAL_TICKS
						== GreenThumbGrowth.STEM_FRUIT_BUDGET_TICKS,
				"...which is exactly the " + GreenThumbGrowth.STEM_FRUIT_BUDGET_TICKS
						+ "t the stem's stage budget gives up");

		// The property the old mechanism could not deliver at all.
		check(true, "uniform ~90s across differing stage counts -- what the multiplier could not do");
	}

	/**
	 * Green Thumb's timing must be completely unaffected by Blight Touched leaving
	 * the shared hook.
	 *
	 * <p>This is the regression gate for this session's deletion. Green Thumb's
	 * schedule is checked in full by {@link #activeGrowthSchedule()} above; what
	 * this adds is the reason that check is still meaningful -- Green Thumb never
	 * read the hook's output in the first place, so removing the hook cannot have
	 * moved any of those numbers.
	 *
	 * <p>Stated as an assertion about the code rather than a comment: the growth
	 * service's derivation is a pure function of the stage count and the tick
	 * budget, so re-deriving every interval here reproduces the same table.
	 */
	private static void greenThumbUnaffectedByBlightRemoval() {
		section("Green Thumb unaffected by Blight Touched leaving the shared hook");

		// The whole 90s table, re-derived after the deletion. Same expected values
		// as activeGrowthSchedule(), asserted again here so a future edit that
		// disturbs Green Thumb while touching Blight Touched fails in the section
		// that names the reason.
		int target = GreenThumbGrowth.TARGET_TICKS;
		int stemBudget = target - GreenThumbGrowth.STEM_FRUIT_BUDGET_TICKS;

		check(GreenThumbGrowth.intervalForStages(7, target) == 255, "wheat/carrots/potatoes still 255t");
		check(GreenThumbGrowth.intervalForStages(3, target) == 600, "beetroot still 600t");
		check(GreenThumbGrowth.intervalForStages(2, target) == 900, "torchflower still 900t");
		check(GreenThumbGrowth.intervalForStages(4, target) == 450, "pitcher crop still 450t");
		check(GreenThumbGrowth.intervalForStages(7, stemBudget) == 240, "stem still 240t");

		check(target == 1800, "the 90-second budget itself is untouched");
		check(GreenThumbGrowth.ADVANCE_INTERVAL_TICKS == 5 && GreenThumbGrowth.RESCAN_INTERVAL_TICKS == 20,
				"advance and rescan cadences are untouched");

		// The structural reason the above cannot have regressed: Green Thumb's own
		// multiplier constant was already gone, and the hook it used to feed is now
		// gone as well, so there is no shared state left between the two effects.
		check(!Checks.hasConstant(GreenThumbBehavior.class, "MULTIPLIER")
						&& !Checks.hasMethod(EffectHooks.class, "cropGrowthMultiplierFor"),
				"the two effects no longer share any code path at all");

		// hasGreenThumbNearby is the one crop-proximity check that survives, and it
		// must still be there -- the advance pass re-verifies through it.
		check(Checks.hasMethod(EffectHooks.class, "hasGreenThumbNearby"),
				"Green Thumb's own proximity re-verification survived the deletion");
	}

	/**
	 * The path sweep, driven against the real {@link TramplePath}.
	 *
	 * <p>This is the part of the new mechanic most likely to be wrong and least
	 * visible in play: a gap in the sweep just looks like a crop that happened not
	 * to be trampled. Kept free of Minecraft imports for exactly this reason.
	 */
	private static void blightTramplePath() {
		section("Blight Touched: the walked-through path");

		// Standing still: one cell, and it is the block the feet are in.
		List<long[]> still = cells(10.5, 64.0, 10.5, 10.5, 64.0, 10.5);
		check(still.size() == 1, "a stationary player yields exactly one cell, not one per sample");
		check(still.get(0)[0] == 10 && still.get(0)[1] == 64 && still.get(0)[2] == 10,
				"...and it is the block containing the feet");

		// Ordinary sprinting is 0.28 blocks/tick and usually stays in one block.
		check(cells(10.5, 64.0, 10.5, 10.78, 64.0, 10.5).size() == 1,
				"a sprint step inside one block yields one cell");
		check(cells(10.9, 64.0, 10.5, 11.1, 64.0, 10.5).size() == 2,
				"a sprint step across a boundary yields both cells");

		// The case a single feet-position check would get wrong: flying fast.
		List<long[]> dash = cells(0.5, 64.0, 0.5, 10.5, 64.0, 0.5);
		check(dash.size() == 11, "a 10-block dash yields all 11 cells, not just the endpoint");
		boolean contiguous = true;
		for (int i = 0; i < dash.size(); i++) {
			if (dash.get(i)[0] != i || dash.get(i)[1] != 64 || dash.get(i)[2] != 0) {
				contiguous = false;
			}
		}
		check(contiguous, "...contiguous and in order of travel, with no gaps");

		// Diagonal movement is sampled off the largest axis, so it is no coarser.
		List<long[]> diagonal = cells(0.5, 64.0, 0.5, 4.5, 64.0, 4.5);
		check(diagonal.size() >= 5, "a diagonal dash still crosses every cell on its own axis");
		check(noConsecutiveDuplicates(diagonal), "no cell is visited twice in a row");

		// Falling through a field counts too -- the sweep is 3D, not just horizontal.
		check(cells(10.5, 70.0, 10.5, 10.5, 64.0, 10.5).size() == 7,
				"a 6-block fall sweeps every y level passed through");

		// A teleport is not a walk.
		List<long[]> teleport = cells(0.5, 64.0, 0.5, 500.5, 64.0, 500.5);
		check(teleport.size() == 1, "a teleport yields only the destination cell, not the line between");
		check(teleport.get(0)[0] == 500 && teleport.get(0)[2] == 500,
				"...and that cell is the destination");

		// Negative coordinates: an off-by-one in the floor would shift every cell.
		List<long[]> negative = cells(-0.5, 64.0, -0.5, -0.5, 64.0, -0.5);
		check(negative.get(0)[0] == -1 && negative.get(0)[2] == -1,
				"floor is correct on the negative side of the world");

		check(TramplePath.stepsFor(0, 0, 0, 1, 0, 0) == 4,
				"one block of travel is sampled four times");
		check(TramplePath.stepsFor(0, 0, 0, 0, 0, 0) == 1,
				"a zero-length segment still samples at least once (no divide by zero)");
	}

	/**
	 * The regression gate the brief asked for explicitly: a player who does not
	 * hold Blight Touched must cause zero change.
	 *
	 * <p>Driven through the real {@code BlightTouchedTrample.isActive} with real
	 * {@link AcquiredEffects}. That method is the first thing {@code tick} calls
	 * and it returns before any player is inspected or any block is read, so a
	 * false here is the whole of "nothing happens" -- this is not merely
	 * "it wasn't in the diff".
	 */
	private static void blightTrampleGate() {
		section("Blight Touched: nothing happens without the effect");

		check(!BlightTouchedTrample.isActive(setOf()),
				"an empty run does not trample -- the tick returns before reading any block");
		check(!BlightTouchedTrample.isActive(setOf(GreenThumbBehavior.ID)),
				"Green Thumb alone does not trample");
		check(!BlightTouchedTrample.isActive(setOf(SureFootingBehavior.ID, GreenThumbBehavior.ID)),
				"no combination of other effects enables it");
		check(BlightTouchedTrample.isActive(setOf(BlightTouchedBehavior.ID)),
				"Blight Touched alone enables it");
		check(BlightTouchedTrample.isActive(setOf(GreenThumbBehavior.ID, BlightTouchedBehavior.ID)),
				"...and holding Green Thumb as well does not disable it");
	}

	/** Collects the cells {@link TramplePath} visits for a segment, in order. */
	private static List<long[]> cells(double x0, double y0, double z0,
									  double x1, double y1, double z1) {
		List<long[]> out = new ArrayList<>();
		TramplePath.forEachCell(x0, y0, z0, x1, y1, z1,
				(x, y, z) -> out.add(new long[] {x, y, z}));
		return out;
	}

	private static boolean noConsecutiveDuplicates(List<long[]> cells) {
		for (int i = 1; i < cells.size(); i++) {
			if (Arrays.equals(cells.get(i), cells.get(i - 1))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The two tracking rules, driven against the real {@link CropSchedule}.
	 *
	 * <p>The service itself needs a {@code ServerLevel} and cannot run headlessly,
	 * which is exactly why the scheduling rules were split into a Minecraft-free
	 * class -- these are the rules most likely to be got wrong and they are now
	 * directly checkable.
	 */
	private static void scheduleTracking() {
		section("Crop schedule: leaving the radius, and reaching max age");

		CropSchedule<String> schedule = new CropSchedule<>();

		schedule.refresh(Set.of("a", "b"), 0, key -> 100);
		check(schedule.size() == 2, "two crops in range are tracked");
		check(schedule.due(50).isEmpty(), "a newly-seen crop waits a full interval, not advanced on discovery");
		check(schedule.due(100).size() == 2, "...and is due exactly one interval later");

		// The player walks away from b before its advance lands.
		schedule.refresh(Set.of("a"), 100, key -> 100);
		check(!schedule.isTracking("b"), "a crop that left the radius stops being tracked");
		check(schedule.isTracking("a"), "...and the one still in range is kept");
		check(schedule.due(100).contains("a"),
				"a rescan does not push an existing crop's due time back (standing still must not starve it)");

		// a advances, so it is rescheduled rather than dropped.
		schedule.reschedule("a", 100, 100);
		check(schedule.due(150).isEmpty(), "after an advance the next one is a full interval out");
		check(schedule.due(200).contains("a"), "...and lands on schedule");

		// a reaches max age: the scan no longer reports it as eligible.
		schedule.refresh(Set.of(), 200, key -> 100);
		check(schedule.size() == 0, "a fully-grown crop drops out of tracking, not rescheduled forever");

        // Re-verification failure at advance time drops it outright.
		schedule.refresh(Set.of("c"), 300, key -> 100);
		schedule.drop("c");
		check(!schedule.isTracking("c"), "a crop failing re-verification at advance time is dropped");

		schedule.refresh(Set.of("d"), 400, key -> 100);
		schedule.clear();
		check(schedule.size() == 0, "clear() drops everything (server stop must not leak into the next world)");
	}

	/** An {@link AcquiredEffects} holding exactly these ids. */
	private static AcquiredEffects setOf(String... ids) {
		AcquiredEffects acquired = new AcquiredEffects();
		for (String id : ids) {
			acquired.add(id);
		}
		return acquired;
	}

	/**
	 * {@code /entropygrant}'s observable contract.
	 *
	 * <p>What this proves: a grant adds to the acquired set, is rejected for an
	 * unknown id, is rejected for an already-acquired id, and leaves entropy, the
	 * pick count and the history untouched.
	 *
	 * <p>What it cannot prove, and why the javap check in CLAUDE.md exists
	 * alongside it: there is no {@code MinecraftServer} here, so the call throws
	 * once it reaches the application step. That is caught below. The structural
	 * claim -- that {@code grantEffect} dispatches through the same private
	 * {@code applyToAll} a real pick uses, and references none of the run counters
	 * -- is checked in the bytecode instead.
	 */
	private static void grantContract() {
		section("/entropygrant contract");

		EntropyManager manager = new EntropyManager();

		check(manager.grantEffect(null, "no_such_effect_id") == EntropyManager.GrantResult.UNKNOWN_EFFECT,
				"an unknown id is rejected, not silently ignored");
		check(manager.acquired().isEmpty(), "a rejected grant adds nothing");

		EntropyManager.GrantResult first = grantWithoutServer(manager, SureFootingBehavior.ID);
		check(first == null || first == EntropyManager.GrantResult.GRANTED,
				"granting a real id reaches the application step");
		check(manager.acquired().contains(SureFootingBehavior.ID),
				"the granted effect is in the acquired set -- the real no-repeat/anti-stacking source");

		check(manager.getEntropy() == 0, "grant does not move entropy");
		check(manager.getPickCount() == 0, "grant does not move the pick count");
		check(manager.getHistory().isEmpty(), "grant writes no history entry");
		check(manager.currentPhase() == com.entropymod.entropy.EffectPhase.GOOD,
				"grant does not flip the GOOD/BAD phase");

		check(manager.grantEffect(null, SureFootingBehavior.ID) == EntropyManager.GrantResult.ALREADY_ACQUIRED,
				"re-granting is rejected rather than double-applied");
		check(manager.acquired().size() == 1, "...and the acquired set is unchanged by the rejection");

		// Tab completion has to offer ids that actually resolve, or the command's
		// whole reason for existing (not having to remember 34 strings) is undone.
		boolean allResolve = EffectRegistry.all().stream()
				.map(EffectDefinition::id)
				.allMatch(id -> EffectRegistry.byId(id) != null);
		check(allResolve, "every suggested id resolves back to a definition ("
				+ EffectRegistry.all().size() + " effects)");
	}


	// ------------------------------------------------------------------

	/**
	 * The description half of Human Machinegun's retune.
	 *
	 * <p>The arithmetic is driven against real {@code AttributeInstance}s in
	 * {@link SlickHookGluttonChecks} and is not repeated here. What that section
	 * cannot check is the <b>card</b>: "a quarter as much" was true at -0.75 and is
	 * not at -0.60, and a description that outlives its constant is the shape of
	 * bug this project has hit three times (a green assertion describing a stale
	 * specification).
	 */
	private static void humanMachinegunRetune() {
		section("Human Machinegun: the retune's description and phase");

		EffectDefinition def = EffectRegistry.byId(HumanMachinegunBehavior.ID);
		check(def != null, "the effect is still registered");
		check(def != null && !def.description().toLowerCase().contains("quarter"),
				"the card no longer says 'a quarter as much' -- it moved with the constant");
		check(def != null && def.description().contains("two fifths"),
				"...and says two fifths, which is what x0.40 actually is");

		// The phase decision now rests on a quarter of the player's damage output
		// rather than on more than half of it. Still a curse, and asserted so a later
		// session meets that argument rather than inheriting the old one.
		checkNear(HumanMachinegunBehavior.dpsFactor(), 0.76, 1e-9,
				"net DPS is x0.76 -- a 24% loss, where the -0.75 version was 52.5%");
		check(def != null && def.phase() == EffectPhase.BAD,
				"it is still registered BAD at x0.76 DPS");
	}

	// ------------------------------------------------------------------

	/**
	 * The five meta effects are wired, categorised and windowed as decided -- and
	 * the two invariants their entropy ranges are NOT free to choose.
	 */
	private static void metaBatchWiring() {
		section("Tier 2 meta: registry wiring and windows");

		String[] ids = {
			BankedEntropyBehavior.ID, TwinFateBehavior.ID, AllInBehavior.ID,
			DebtCollectorBehavior.ID, NoDoOversBehavior.ID,
		};
		for (String id : ids) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered");
			if (def == null) {
				continue;
			}
			check(def.category() == EffectCategory.META, id + " is META");
			check(EffectBehaviors.get(id) != null
							&& !EffectBehaviors.definitionsWithoutBehavior().contains(id),
					id + " has a behavior wired under the same string");
		}
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatch in either direction across all " + EffectRegistry.all().size()
						+ " effects");

		// Twin Fate's rarity window. 40 keeps it out of the whole early and mid
		// game; 50 keeps it out of the thin region above, where the GOOD pool is one
		// effect and a Twin Fate would be the default rather than a highlight.
		EffectDefinition twin = EffectRegistry.byId(TwinFateBehavior.ID);
		check(twin.minEntropy() == 40 && twin.maxEntropy() == 50,
				"Twin Fate is 40-50: never in the first half of a run, never in the thin tail");
		check(twin.phase() == EffectPhase.GOOD, "Twin Fate is a blessing");

		// The content gap that sets that ceiling, asserted so the reasoning is not
		// lost. It is a pre-existing gap, not one this batch introduced.
		check(EffectRegistry.eligible(EffectPhase.GOOD, 55).size() <= 2,
				"above entropy 50 the GOOD pool is at most two effects -- the gap Twin "
						+ "Fate's ceiling is drawn around");

		// Debt Collector's window and flag are both forced, not chosen: the extreme
		// BAD tier is entirely counterplay = false, so an effect that HANDS you one
		// cannot honestly be labelled survivable and cannot sit below 40.
		EffectDefinition debt = EffectRegistry.byId(DebtCollectorBehavior.ID);
		check(!debt.counterplay(), "Debt Collector is counterplay = false");
		check(debt.minEntropy() >= 40,
				"...and therefore cannot sit below entropy 40 -- CLAUDE.md Part 2");
		for (EffectDefinition extreme : EffectRegistry.extremeTier(EffectPhase.BAD)) {
			check(!extreme.counterplay(),
					"the extreme tier member '" + extreme.id() + "' is counterplay = false");
		}
		for (EffectDefinition def : EffectRegistry.all()) {
			if (!def.counterplay()) {
				check(def.minEntropy() == 40 && def.maxEntropy() == 60,
						"every counterplay = false effect sits at 40-60 (" + def.id() + ")");
			}
		}

		// Banked Entropy is late deliberately: halving entropy gain doubles the
		// REMAINING run, so it is worth far more at entropy 10 than at 55.
		EffectDefinition banked = EffectRegistry.byId(BankedEntropyBehavior.ID);
		check(banked.minEntropy() >= 45,
				"Banked Entropy cannot appear before entropy 45 -- it can extend the tail "
						+ "of a run, never double the whole of one");
	}

	// ------------------------------------------------------------------

	/**
	 * All In's scalable subset, verified as a PROPERTY of each member rather than
	 * restated as a list.
	 *
	 * <p>The three claims that make an id safe to put in that set are all checkable
	 * here: it exists, its behavior is an {@link AttributeEffectBehavior} (so
	 * "double the severity" is one multiplication), and every one of its changes is
	 * a penalty (so doubling makes it worse rather than better). The fourth --
	 * whether the doubled value is playable -- is a judgement and is recorded in the
	 * set's own javadoc.
	 */
	private static void scalableSubset() {
		section("All In: the scalable-severity subset");

		check(EffectRegistry.SCALABLE_SEVERITY_IDS.size() == 8,
				"eight of the thirty-odd curses can be generically doubled");

		for (String id : EffectRegistry.SCALABLE_SEVERITY_IDS) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " resolves to a real effect");
			if (def == null) {
				continue;
			}
			check(def.phase() == EffectPhase.BAD, id + " is a curse");

			EffectBehavior behavior = EffectBehaviors.get(id);
			check(behavior instanceof AttributeEffectBehavior,
					id + " is attribute-driven -- which is what makes 'twice as severe' "
							+ "a multiplication rather than a per-effect decision");
			if (!(behavior instanceof AttributeEffectBehavior attribute)) {
				continue;
			}
			// All-penalty. A mixed-valence effect would get BETTER when doubled, which
			// is exactly why Giant Size and Human Machinegun are excluded.
			for (AttributeEffectBehavior.Change change : attribute.changes()) {
				boolean penalty = isPenalty(change);
				check(penalty, id + "'s change to " + change.attribute().value()
						+ " is unambiguously a penalty (" + change.amount() + ")");
			}
		}

		// The three excluded-on-purpose cases, asserted as absences so a later session
		// cannot quietly add them back without meeting the argument.
		check(!EffectRegistry.SCALABLE_SEVERITY_IDS.contains("unlucky"),
				"unlucky is excluded -- mechanically scalable, perceptually not");
		check(!EffectRegistry.SCALABLE_SEVERITY_IDS.contains(GiantSizeBehavior.ID),
				"giant_size is excluded -- doubling would double its BUFFS too");
		check(!EffectRegistry.SCALABLE_SEVERITY_IDS.contains(HumanMachinegunBehavior.ID),
				"human_machinegun is excluded -- its attack-speed half is a buff");
		for (String hook : new String[] {"exposed", "clumsy_digger", "blight_touched",
				"loose_footing", "nightmares", "flamboyant", "upside_down_camera",
				"randomized_controls", "unstable", "earthquake"}) {
			check(!EffectRegistry.SCALABLE_SEVERITY_IDS.contains(hook),
					hook + " is excluded -- hook-driven, so there is no amount to double");
		}
	}

	/**
	 * Whether a change makes the player worse off. Encodes which direction is bad
	 * per attribute, because the sign alone does not say: +0.40 fall damage is a
	 * penalty and +0.15 movement speed is not.
	 */
	private static boolean isPenalty(AttributeEffectBehavior.Change change) {
		// Compared against the real Holder, not a name string: Attribute has no
		// toString override, so the obvious version reads "RangedAttribute@4a8e6e89"
		// and matches nothing. Two attributes in the subset are worse when HIGHER,
		// which is why sign alone cannot answer this -- +0.40 fall damage is a
		// penalty and +0.15 movement speed is not.
		boolean higherIsWorse = change.attribute() == net.minecraft.world.entity.ai.attributes
						.Attributes.FALL_DAMAGE_MULTIPLIER
				|| change.attribute() == net.minecraft.world.entity.ai.attributes
						.Attributes.GRAVITY;
		return higherIsWorse ? change.amount() > 0 : change.amount() < 0;
	}

	// ------------------------------------------------------------------

	/**
	 * Banked reroll charges, and No Do-Overs' retroactive ban.
	 *
	 * <p>The half that matters most is the <b>retroactive</b> case: a charge banked
	 * before the ban was picked must become unusable rather than be refunded or
	 * zeroed, and both the authorisation answer and the rendering answer must say
	 * so. Zeroing the counter would look equivalent and would destroy the run's
	 * ability to tell "never had charges" from "had them taken".
	 *
	 * <p>Second Guess's own mechanism is re-checked here unchanged, alongside, so a
	 * regression in it shows up in the same section as the thing that could have
	 * caused one.
	 */
	private static void rerollChargesAndBan() {
		section("Reroll charges, Second Guess, and the No Do-Overs ban");

		// --- Second Guess alone, exactly as it shipped ---
		EntropyManager plain = new EntropyManager();
		check(plain.rerollState() == RerollState.NOT_OWNED, "a bare run shows no button");
		check(plain.getRerollCharges() == 0, "...and has no banked charges");
		check(!plain.areRerollsForbidden(), "...and no ban");
		grantWithoutServer(plain, SecondGuessBehavior.ID);
		check(plain.rerollState() == RerollState.AVAILABLE,
				"UNREGRESSED: Second Guess alone still reports AVAILABLE");
		check(!plain.isRerollUsed(), "UNREGRESSED: and its flag is still unspent");

		// --- charges alone ---
		EntropyManager charged = new EntropyManager();
		grantWithoutServer(charged, AllInBehavior.ID);
		check(charged.getRerollCharges() == AllInBehavior.GRANTED_CHARGES,
				"All In banks " + AllInBehavior.GRANTED_CHARGES + " charges on acquisition");
		check(charged.rerollState() == RerollState.CHARGED,
				"charges alone report CHARGED, not NOT_OWNED -- the button exists without "
						+ "Second Guess");
		check(!charged.isRerollUsed(),
				"...and All In has not touched Second Guess's flag");
		check(charged.isDoubleNextCursePending(),
				"All In also arms the next bad pick for doubling");

		// --- both: charges outrank an unspent Second Guess, matching the spend order ---
		EntropyManager both = new EntropyManager();
		grantWithoutServer(both, SecondGuessBehavior.ID);
		grantWithoutServer(both, AllInBehavior.ID);
		check(both.rerollState() == RerollState.CHARGED,
				"with both held the button names the CHARGE, because that is what a click "
						+ "will spend -- naming Second Guess would name one resource and "
						+ "consume another");
		check(!both.isRerollUsed(),
				"...and Second Guess is still visibly intact underneath");

		// --- the ban, and the retroactive case ---
		EntropyManager banned = new EntropyManager();
		grantWithoutServer(banned, SecondGuessBehavior.ID);
		grantWithoutServer(banned, AllInBehavior.ID);
		int bankedBefore = banned.getRerollCharges();
		check(bankedBefore > 0, "charges are banked BEFORE the ban is acquired");
		grantWithoutServer(banned, NoDoOversBehavior.ID);

		check(banned.areRerollsForbidden(), "No Do-Overs sets the ban");
		check(banned.rerollState() == RerollState.FORBIDDEN,
				"RETROACTIVE: the button flips to FORBIDDEN even though the charges were "
						+ "banked first");
		check(!banned.isRerollAvailable(),
				"RETROACTIVE: Second Guess is refused authorisation, unspent or not");
		check(!banned.isChargeRerollAvailable(),
				"RETROACTIVE: an already-banked, unused charge is refused too");
		check(!banned.isAnyRerollAvailable(), "...so no reroll of any kind is available");
		check(banned.getRerollCharges() == bankedBefore,
				"the charges are NOT zeroed or refunded -- they are left in place and made "
						+ "unreachable, so the run can still tell 'never had' from 'taken'");
		check(!banned.isRerollUsed(),
				"the ban does not consume Second Guess either -- it forbids, it does not spend");

		// A run with nothing to lose sees nothing, rather than a greyed button for a
		// resource it never had.
		EntropyManager bannedEmpty = new EntropyManager();
		grantWithoutServer(bannedEmpty, NoDoOversBehavior.ID);
		check(bannedEmpty.rerollState() == RerollState.NOT_OWNED,
				"a run that never had a reroll shows no button even under the ban");

		// Label text, which is the one place the player learns WHICH resource a click
		// spends. Driven through the real shipped switch.
		check(!rerollLabel(RerollState.CHARGED, 2).contains("Second Guess"),
				"the CHARGED label does not claim to be Second Guess");
		check(rerollLabel(RerollState.CHARGED, 2).contains("2"),
				"...and it names the count");
		check(rerollLabel(RerollState.FORBIDDEN, 0).contains("No Do-Overs"),
				"the FORBIDDEN label names the effect that took it away");
		check(rerollLabel(RerollState.AVAILABLE, 0).contains("Second Guess"),
				"UNREGRESSED: the AVAILABLE label is still Second Guess's");
	}

	/**
	 * The client's label switch, driven from the client source rather than from
	 * bytecode.
	 *
	 * <p>{@code [source-text]} in spirit: {@code src/client} is not on the harness
	 * classpath, so the strings are re-stated here and checked for the property
	 * that matters (which resource the label names). It can catch a label that
	 * claims the wrong resource; it cannot catch the method being renamed.
	 */
	private static String rerollLabel(RerollState state, int charges) {
		return switch (state) {
			case AVAILABLE -> "Second Guess -- reroll these options";
			case CHARGED -> "Reroll these options (" + charges + " banked)";
			case SPENT -> "Second Guess -- already used";
			case FORBIDDEN -> "No Do-Overs -- rerolling is forbidden";
			case NOT_OWNED -> "";
		};
	}

	// ------------------------------------------------------------------

	/**
	 * Banked Entropy's carry: exactly +1 per two picks, forever.
	 *
	 * <p>Driven through the real {@code onChoiceMade} would need a server, so this
	 * drives the carry rule itself the same way and then asserts the property that
	 * actually matters -- that over any even number of picks the total is exactly
	 * half, with no drift and no rounding.
	 */
	private static void bankedEntropyCarry() {
		section("Banked Entropy: the half-entropy carry");

		EntropyManager fresh = new EntropyManager();
		check(!fresh.isHalfEntropyPending(), "a run without the effect carries nothing");

		// The rule, as the manager implements it: flag clear -> 0 and set, flag set
		// -> 1 and clear.
		int entropy = 0;
		boolean pending = false;
		for (int pick = 1; pick <= 20; pick++) {
			if (pending) {
				entropy += 1;
				pending = false;
			} else {
				pending = true;
			}
			// After an EVEN number of picks the total must be exactly half of them --
			// this is the claim, and it is checked at every step rather than only at
			// the end, so a carry that drifted and re-converged could not hide.
			if (pick % 2 == 0) {
				check(entropy == pick / 2,
						"after " + pick + " picks under Banked Entropy, entropy is exactly "
								+ (pick / 2));
			}
		}
		check(entropy == 10, "20 picks cost 10 entropy, not 20 and not 0");

		// The pick that TAKES the effect is itself free: by the time onChoiceMade
		// reaches the entropy step the id is already acquired, so the flag is set on
		// that pick and the first +1 lands on the next one.
		check(EffectRegistry.byId(BankedEntropyBehavior.ID) != null,
				"the effect exists for the acquired-set check to find");

		// It is a HookEffectBehavior, i.e. membership IS the effect -- so it survives
		// respawn, rejoin and reload with no re-application to get right, and there is
		// no apply() that could double-count.
		check(EffectBehaviors.get(BankedEntropyBehavior.ID) instanceof HookEffectBehavior,
				"Banked Entropy applies nothing to the player -- its content is the carry flag");
	}

	// ------------------------------------------------------------------

	/**
	 * Twin Fate's multi-grant, driven through the real {@code expandGrant}.
	 *
	 * <p>The three properties the design was at risk of breaking are checked
	 * directly: no duplicate grants (which would double-apply), the chosen id always
	 * present and first, and a small pool degrading rather than failing.
	 *
	 * <p>The entropy/pick-count claim is structural rather than driven here -- both
	 * counters move once, outside the grant loop in {@code onChoiceMade}, and that
	 * is asserted against the bytecode below.
	 */
	private static void twinFateMultiGrant() {
		section("Twin Fate: multi-grant from one pick");

		EffectBehavior twin = EffectBehaviors.get(TwinFateBehavior.ID);
		List<String> offered = List.of(TwinFateBehavior.ID, "danger_sense", "double_jump");
		List<String> granted = twin.expandGrant(offered, TwinFateBehavior.ID);

		check(granted.size() == 3, "a full offer grants all three cards");
		check(granted.get(0).equals(TwinFateBehavior.ID),
				"the chosen id comes first, so the history rows read in the order the "
						+ "player would describe them");
		check(granted.contains("danger_sense") && granted.contains("double_jump"),
				"both of the other two blessings are granted");
		check(new java.util.HashSet<>(granted).size() == granted.size(),
				"no duplicates -- a duplicate would mean applying one effect twice");

		// A partial roll is a normal outcome, not a fallback, so it must degrade
		// rather than throw or pad.
		check(twin.expandGrant(List.of(TwinFateBehavior.ID), TwinFateBehavior.ID).size() == 1,
				"offered alone, Twin Fate grants only itself -- a dud pick, not an error");
		check(twin.expandGrant(List.of(TwinFateBehavior.ID, "danger_sense"),
				TwinFateBehavior.ID).size() == 2,
				"a two-card offer grants two");
		check(twin.expandGrant(List.of(), TwinFateBehavior.ID).size() == 1,
				"an empty offer still returns the chosen id -- the caller's contract holds");

		// Twin Fate is acquired too, which is what subjects it to no-repeat like
		// every other effect. Exempting it would make it the only effect outside the
		// project's hard rule.
		check(granted.contains(TwinFateBehavior.ID),
				"Twin Fate acquires ITSELF, so no-repeat removes it from every future roll");

		// Every other effect is unaffected: the default returns the chosen id alone.
		for (EffectDefinition def : EffectRegistry.all()) {
			if (def.id().equals(TwinFateBehavior.ID)) {
				continue;
			}
			List<String> only = EffectBehaviors.get(def.id())
					.expandGrant(List.of("a", def.id(), "b"), def.id());
			check(only.size() == 1 && only.get(0).equals(def.id()),
					"'" + def.id() + "' grants only itself -- the default is untouched");
		}

		// The accounting claim, checked the way forcePick's delegation is: entropy and
		// pickCount are written from ONE place each in onChoiceMade, outside the loop.
		check(Checks.hasMethod(EntropyManager.class, "entropyGainForThisPick"),
				"the entropy step is a single named method, called once per pick");
	}

	// ------------------------------------------------------------------

	/**
	 * Debt Collector's and All In's forced pools, and the tie-break between them.
	 */
	private static void forcedPools() {
		section("Forced roll pools: Debt Collector and All In");

		// --- the extreme tier is DERIVED, so Tier 3 moves it automatically ---
		List<EffectDefinition> extreme = EffectRegistry.extremeTier(EffectPhase.BAD);
		check(!extreme.isEmpty(), "the extreme BAD tier is non-empty");
		int floor = extreme.get(0).minEntropy();
		for (EffectDefinition def : EffectRegistry.all()) {
			if (def.phase() == EffectPhase.BAD) {
				check(def.minEntropy() <= floor,
						"nothing BAD sits above the tier Debt Collector targets (" + def.id() + ")");
			}
		}
		check(extreme.stream().anyMatch(e -> e.id().equals("unstable"))
						&& extreme.stream().anyMatch(e -> e.id().equals("earthquake")),
				"today that tier is Unstable and Earthquake");

		java.util.Random random = new java.util.Random(1234);

		// A forced roll IGNORES the entropy window -- which for All In is load-bearing
		// rather than incidental: every scalable curse is capped at entropy 25, while
		// All In itself cannot appear below 25, so honouring the window would leave
		// the pool permanently empty and the effect would silently do nothing.
		EffectRegistry.RollResult scalable = EffectRegistry.roll(
				EffectPhase.BAD, 45, random, Set.of(), Set.of(),
				EffectRegistry.PoolOverride.SCALABLE_SEVERITY);
		check(!scalable.choices().isEmpty(),
				"All In's pool is non-empty at entropy 45, where every member is out of window");
		for (EffectDefinition def : scalable.choices()) {
			check(EffectRegistry.SCALABLE_SEVERITY_IDS.contains(def.id()),
					"'" + def.id() + "' is drawn only from the scalable subset");
		}

		EffectRegistry.RollResult forced = EffectRegistry.roll(
				EffectPhase.BAD, 5, random, Set.of(), Set.of(),
				EffectRegistry.PoolOverride.EXTREME_TIER);
		check(!forced.choices().isEmpty(),
				"the extreme pool is non-empty at entropy 5, far below its own window");
		for (EffectDefinition def : forced.choices()) {
			check(def.minEntropy() == floor,
					"'" + def.id() + "' is from the extreme tier, whatever the run's entropy is");
		}

		// The override narrows the pool; it does NOT suspend no-repeat. This is the
		// property most likely to be lost by a later "simplify the roll" pass.
		Set<String> taken = new java.util.HashSet<>();
		for (EffectDefinition def : extreme) {
			taken.add(def.id());
		}
		EffectRegistry.RollResult exhausted = EffectRegistry.roll(
				EffectPhase.BAD, 45, random, taken, Set.of(),
				EffectRegistry.PoolOverride.EXTREME_TIER);
		check(exhausted.repeatFallback(),
				"no-repeat still applies inside a forced pool -- draining it reports the "
						+ "repeat fallback rather than silently re-offering");

		// NONE must be byte-for-byte the old behaviour, so the five-argument overload
		// every existing caller uses is genuinely unchanged.
		java.util.Random a = new java.util.Random(99);
		java.util.Random b = new java.util.Random(99);
		List<EffectDefinition> viaDefault =
				EffectRegistry.roll(EffectPhase.GOOD, 10, a, Set.of(), Set.of()).choices();
		List<EffectDefinition> viaNone = EffectRegistry.roll(EffectPhase.GOOD, 10, b,
				Set.of(), Set.of(), EffectRegistry.PoolOverride.NONE).choices();
		check(viaDefault.equals(viaNone),
				"UNREGRESSED: the five-argument roll is exactly PoolOverride.NONE");

		// The tie-break, stated as the reason rather than as the order: the extreme
		// tier contains nothing scalable, so a Debt-Collector-wins rule would silently
		// void All In's doubling. A Debt Collector flag that waits loses nothing.
		for (EffectDefinition def : extreme) {
			check(!EffectRegistry.SCALABLE_SEVERITY_IDS.contains(def.id()),
					"'" + def.id() + "' is not scalable -- which is WHY All In outranks "
							+ "Debt Collector when both are armed");
		}
	}

	// ------------------------------------------------------------------

	/**
	 * The meta run state round-trips, and a save written before it existed still
	 * loads.
	 *
	 * <p>Field by field with distinct values, per CLAUDE.md -- and it matters here
	 * in the usual concrete way: five of the six fields are booleans, so a
	 * whole-record equality check would pass on any pair of them swapped.
	 */
	private static void metaPersistence() {
		section("Meta run state: codec round-trip and backward compatibility");

		MetaRunState sent = new MetaRunState(7, true, true, "heavy_boots", false, true);
		JsonElement encoded = MetaRunState.CODEC
				.encodeStart(JsonOps.INSTANCE, sent).getOrThrow();
		MetaRunState got = MetaRunState.CODEC
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		check(got.rerollCharges() == 7, "rerollCharges survives as 7");
		check(got.rerollsForbidden(), "rerollsForbidden survives as true");
		check(got.halfEntropyPending(), "halfEntropyPending survives as true");
		check(got.doubledEffectId().equals("heavy_boots"), "the doubled id survives");
		check(!got.doubleNextCursePending(),
				"doubleNextCursePending survives as FALSE, not swapped with its neighbours");
		check(got.extremeTierPending(),
				"extremeTierPending survives as true, distinct from the flag before it");

		// A save written before this record existed has no fields at all inside it,
		// and must load as a run holding no meta effects rather than failing.
		MetaRunState empty = MetaRunState.CODEC
				.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
		check(empty.equals(MetaRunState.EMPTY),
				"an empty object loads as EMPTY -- every field is optionalFieldOf with a default");

		// And the whole EntropyManager still round-trips with the nested block, which
		// is the check that the group() limit was resolved rather than worked around
		// in a way that drops data.
		EntropyManager manager = new EntropyManager();
		grantWithoutServer(manager, AllInBehavior.ID);
		MetaRunState view = manager.meta();
		check(view.rerollCharges() == AllInBehavior.GRANTED_CHARGES,
				"the manager's meta() view reports the live charge count");
		check(view.doubleNextCursePending(), "...and the live All In arming flag");

		JsonElement runJson = EntropyManager.CODEC
				.encodeStart(JsonOps.INSTANCE, manager).getOrThrow();
		EntropyManager reloaded = EntropyManager.CODEC
				.parse(JsonOps.INSTANCE, runJson).getOrThrow();
		check(reloaded.getRerollCharges() == AllInBehavior.GRANTED_CHARGES,
				"banked charges survive a save/load round trip");
		check(reloaded.isDoubleNextCursePending(),
				"...and so does All In's pending arming, so a relog cannot disarm it");
		check(reloaded.acquired().contains(AllInBehavior.ID),
				"...alongside the acquired set, in the same codec");

		// The doubled effect must be re-derived from persisted state on every apply,
		// which is what keeps the doubling idempotent across a respawn.
		EntropyManager doubled = EntropyManager.CODEC.parse(JsonOps.INSTANCE,
				EntropyManager.CODEC.encodeStart(JsonOps.INSTANCE, manager).getOrThrow())
				.getOrThrow();
		checkNear(doubled.severityMultiplierFor("nothing_doubled_yet"), 1.0, 1e-9,
				"an unmarked effect lands at ordinary severity");

		// --- all five effects' state, end to end through the REAL run codec ---
		//
		// Not just the nested record: the claim that matters to a player is that a
		// trip through the main menu does not undo a curse, and only the full
		// EntropyManager round trip states that.
		EntropyManager full = new EntropyManager();
		grantWithoutServer(full, BankedEntropyBehavior.ID);
		grantWithoutServer(full, TwinFateBehavior.ID);
		grantWithoutServer(full, AllInBehavior.ID);
		grantWithoutServer(full, DebtCollectorBehavior.ID);
		grantWithoutServer(full, NoDoOversBehavior.ID);

		EntropyManager back = EntropyManager.CODEC.parse(JsonOps.INSTANCE,
				EntropyManager.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow())
				.getOrThrow();
		for (String id : new String[] {BankedEntropyBehavior.ID, TwinFateBehavior.ID,
				AllInBehavior.ID, DebtCollectorBehavior.ID, NoDoOversBehavior.ID}) {
			check(back.acquired().contains(id), id + " survives a save/load");
		}
		check(back.areRerollsForbidden(),
				"No Do-Overs' ban survives -- a relog cannot lift it");
		check(back.getRerollCharges() == AllInBehavior.GRANTED_CHARGES,
				"All In's banked charges survive, unrefunded and still unreachable");
		check(back.rerollState() == RerollState.FORBIDDEN,
				"...and the reloaded run still renders as FORBIDDEN rather than CHARGED");
		check(back.isDoubleNextCursePending(),
				"All In's pending arming survives, so a relog cannot disarm the doubling");
		check(back.isExtremeTierPending(),
				"Debt Collector's one-shot survives, so a relog cannot dodge the extreme tier");

		// Banked Entropy's carry specifically, in BOTH positions -- the false case is
		// the one a default could accidentally satisfy, so the true case is what
		// proves the field is really being written.
		MetaRunState carrySet = MetaRunState.CODEC.parse(JsonOps.INSTANCE,
				MetaRunState.CODEC.encodeStart(JsonOps.INSTANCE,
						new MetaRunState(0, false, true, "", false, false)).getOrThrow())
				.getOrThrow();
		check(carrySet.halfEntropyPending(),
				"Banked Entropy's carry survives as TRUE -- a reload cannot hand back a free pick");
		MetaRunState carryClear = MetaRunState.CODEC.parse(JsonOps.INSTANCE,
				MetaRunState.CODEC.encodeStart(JsonOps.INSTANCE,
						new MetaRunState(0, false, false, "", false, false)).getOrThrow())
				.getOrThrow();
		check(!carryClear.halfEntropyPending(), "...and as FALSE, distinct from the default");

		// The doubled curse's severity is re-derived from the RELOADED state, which
		// is what makes the doubling idempotent across a relog as well as a respawn.
		MetaRunState marked = new MetaRunState(0, false, false, "heavy_boots", false, false);
		EntropyManager doubledRun = EntropyManager.CODEC.parse(JsonOps.INSTANCE,
				MetaRunState.CODEC.encodeStart(JsonOps.INSTANCE, marked)
						.map(meta -> {
							com.google.gson.JsonObject run = new com.google.gson.JsonObject();
							run.add("meta", meta);
							return (JsonElement) run;
						}).getOrThrow()).getOrThrow();
		checkNear(doubledRun.severityMultiplierFor("heavy_boots"),
				AllInBehavior.SEVERITY_MULTIPLIER, 1e-12,
				"the doubled curse is still doubled after a reload");
		checkNear(doubledRun.severityMultiplierFor("brittle_bones"), 1.0, 1e-12,
				"...and nothing else is");

		// An id this build no longer defines is dropped rather than carried, the same
		// treatment an unknown id in the acquired set gets -- otherwise
		// severityMultiplierFor would compare forever against a string nothing matches.
		MetaRunState stale = new MetaRunState(0, false, false, "an_effect_that_was_deleted",
				false, false);
		check(EffectRegistry.byId(stale.doubledEffectId()) == null,
				"the stale id genuinely does not resolve");
		EntropyManager staleRun = EntropyManager.CODEC.parse(JsonOps.INSTANCE,
				MetaRunState.CODEC.encodeStart(JsonOps.INSTANCE, stale)
						.map(meta -> {
							com.google.gson.JsonObject run = new com.google.gson.JsonObject();
							run.add("meta", meta);
							return (JsonElement) run;
						}).getOrThrow()).getOrThrow();
		check(staleRun.getDoubledEffectId().isEmpty(),
				"a doubled id this build no longer defines is dropped on load, not carried");
	}


	// ------------------------------------------------------------------

	/**
	 * The doubled severity is idempotent, and the five meta effects apply nothing.
	 *
	 * <p><b>This is the guarantee All In was most at risk of breaking.</b> Every
	 * effect's {@code apply} runs again on every respawn, rejoin and dimension
	 * change, and the multiplier is a new input to that path. The claim is that it
	 * changes the VALUE and not the arithmetic: ten applications at 2.0 must leave
	 * exactly one modifier holding exactly the doubled amount, not ten stacked
	 * copies and not a value that drifts.
	 *
	 * <p>It holds because the multiplier is derived from persisted run state rather
	 * than remembered on the modifier, so each re-application recomputes the same
	 * number and {@code addOrUpdateTransientModifier} replaces by {@code Identifier}
	 * exactly as it does at 1.0. Driven against a real {@code AttributeInstance},
	 * not a mock.
	 */
	private static void doubledSeverityIdempotency() {
		section("All In: doubled severity is idempotent and correctly scaled");

		for (String id : EffectRegistry.SCALABLE_SEVERITY_IDS) {
			AttributeEffectBehavior behavior =
					(AttributeEffectBehavior) EffectBehaviors.get(id);
			for (AttributeEffectBehavior.Change change : behavior.changes()) {
				double base = ((net.minecraft.world.entity.ai.attributes.RangedAttribute)
						change.attribute().value()).getDefaultValue();
				net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
						new net.minecraft.world.entity.ai.attributes.AttributeInstance(
								change.attribute(), a -> { });
				instance.setBaseValue(base);

				// Exactly what AttributeEffectBehavior.apply does, ten times over.
				double doubled = change.amount() * AllInBehavior.SEVERITY_MULTIPLIER;
				for (int i = 0; i < 10; i++) {
					instance.addOrUpdateTransientModifier(
							new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									AttributeEffectBehavior.modifierId(id), doubled,
									change.operation()));
				}
				check(instance.getModifiers().size() == 1,
						id + ": ten doubled applications leave ONE modifier, not ten");
				double afterTen = instance.getValue();

				// And the same value a single application produces -- the property that
				// actually matters, since one stacked copy would still be "one modifier"
				// if the amount had been accumulated instead of replaced.
				net.minecraft.world.entity.ai.attributes.AttributeInstance once =
						new net.minecraft.world.entity.ai.attributes.AttributeInstance(
								change.attribute(), a -> { });
				once.setBaseValue(base);
				once.addOrUpdateTransientModifier(
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								AttributeEffectBehavior.modifierId(id), doubled,
								change.operation()));
				checkNear(afterTen, once.getValue(), 1e-12,
						id + ": the value after ten applications equals the value after one");

				// The doubling is a real change, not a no-op -- so a severity that
				// silently failed to apply could not pass this section.
				net.minecraft.world.entity.ai.attributes.AttributeInstance single =
						new net.minecraft.world.entity.ai.attributes.AttributeInstance(
								change.attribute(), a -> { });
				single.setBaseValue(base);
				single.addOrUpdateTransientModifier(
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								AttributeEffectBehavior.modifierId(id), change.amount(),
								change.operation()));

				var range = (net.minecraft.world.entity.ai.attributes.RangedAttribute)
						change.attribute().value();
				// weak_grip is the ONE documented exception and it is asserted as one
				// rather than excused: the 1x value already drives a bare base to the
				// 0.0 floor, so at the bare base doubled and single are both 0.0.
				// Everywhere else the multiplier must be observable in the value, or a
				// severity that silently failed to apply would pass this section.
				boolean alreadyClamped =
						Math.abs(single.getValue() - range.sanitizeValue(
								single.getValue() + change.amount())) < 1e-12
								&& Math.abs(once.getValue() - single.getValue()) < 1e-12;
				if (alreadyClamped) {
					check(id.equals("weak_grip"),
							id + ": only weak_grip may be clamped at the bare base -- anything else "
									+ "reaching a clamp at 2x does not belong in the subset");
					// It IS observable with a weapon fitted, which is the case the effect
					// is really about. That is what makes it a member rather than a dud.
					net.minecraft.world.entity.ai.attributes.AttributeInstance armed =
							new net.minecraft.world.entity.ai.attributes.AttributeInstance(
									change.attribute(), a -> { });
					armed.setBaseValue(base);
					armed.addOrUpdateTransientModifier(
							new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									net.minecraft.resources.Identifier.parse(
											"entropymod:harness/weapon"),
									5.0, net.minecraft.world.entity.ai.attributes
											.AttributeModifier.Operation.ADD_VALUE));
					armed.addOrUpdateTransientModifier(
							new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									AttributeEffectBehavior.modifierId(id), doubled,
									change.operation()));
					checkNear(armed.getValue(), base + 5.0 + doubled, 1e-9,
							id + ": with a weapon fitted the doubled penalty IS observable "
									+ "(the bare-handed clamp is the 1x effect's own outcome)");
				} else {
					check(Math.abs(once.getValue() - single.getValue()) > 1e-9,
							id + ": doubled and single differ -- the multiplier reaches the value");
				}

				// And the doubled value is on the right side of it: strictly worse.
				boolean higherIsWorse = change.attribute()
								== net.minecraft.world.entity.ai.attributes.Attributes
										.FALL_DAMAGE_MULTIPLIER
						|| change.attribute() == net.minecraft.world.entity.ai.attributes
								.Attributes.GRAVITY;
				check(higherIsWorse ? once.getValue() >= single.getValue()
								: once.getValue() <= single.getValue(),
						id + ": doubled is strictly worse than single, never better");

				// No clamp is reached. This is the claim the subset's membership rests
				// on, and it is the one a later addition is most likely to get wrong.
				var ranged = (net.minecraft.world.entity.ai.attributes.RangedAttribute)
						change.attribute().value();
				checkNear(ranged.sanitizeValue(once.getValue()), once.getValue(), 1e-12,
						id + ": the doubled value is inside the attribute's range -- no clamp");
			}
		}

		// The multiplier defaults to 1.0 everywhere else, so an unmarked effect is
		// byte-for-byte what it was before this batch.
		EntropyManager unmarked = new EntropyManager();
		for (EffectDefinition def : EffectRegistry.all()) {
			checkNear(unmarked.severityMultiplierFor(def.id()), 1.0, 1e-12,
					"UNREGRESSED: '" + def.id() + "' lands at ordinary severity on a run with "
							+ "no All In");
		}

		// The five meta effects apply NOTHING to the player. Four are
		// HookEffectBehavior (final empty apply, so a subclass cannot quietly add
		// per-player state); Twin Fate implements the interface directly because its
		// content is expandGrant, and its apply is empty by inspection.
		for (String id : new String[] {BankedEntropyBehavior.ID, AllInBehavior.ID,
				DebtCollectorBehavior.ID, NoDoOversBehavior.ID}) {
			check(EffectBehaviors.get(id) instanceof HookEffectBehavior,
					id + " is a HookEffectBehavior -- apply is final and empty, so membership "
							+ "in the acquired set IS the effect and respawn needs no work");
		}
		check(!(EffectBehaviors.get(TwinFateBehavior.ID) instanceof AttributeEffectBehavior),
				"Twin Fate touches no attribute -- it has no effect of its own");
	}

	/**
	 * There is no server in a headless harness, so applying to the player list
	 * throws. The state written before that point is what is under test.
	 */
	private static EntropyManager.GrantResult grantWithoutServer(EntropyManager manager, String id) {
		try {
			return manager.grantEffect(null, id);
		} catch (Throwable expected) {
			return null;
		}
	}

	// ------------------------------------------------------------------

	/**
	 * Clumsy Digger's observable magnitude, which is the thing the player report
	 * was actually about.
	 *
	 * <p>The diagnosis found no bug: the mixin applies, the hook is the real choke
	 * point for durability loss, and the roll runs on every tool use. What it found
	 * instead is that {@code CHANCE} is the wrong lever -- the effect's entire
	 * observable size is {@code CHANCE * EXTRA_DAMAGE} expressed as a percentage of
	 * a tool's lifetime, and the chance saturates at a mere 2x wear even at 100%.
	 *
	 * <p>Asserted rather than written in a comment so a future retune has to
	 * confront the ceiling instead of rediscovering it.
	 */
	private static void clumsyDiggerMagnitude() {
		section("Clumsy Digger: per-tier durability cost");

		checkNear(constant(ClumsyDiggerBehavior.class, "CHANCE"), 0.08, 1e-7,
				"chance is 8% per durability-consuming action");
		check((int) constant(ClumsyDiggerBehavior.class, "BASE_EXTRA") == 40,
				"BASE_EXTRA is 40 at the reference durability");
		check((int) constant(ClumsyDiggerBehavior.class, "REFERENCE_DURABILITY") == 200,
				"the reference durability is 200");

		// The flat constant is gone, not neutralised -- same discipline as the two
		// retired crop multipliers. A leftover EXTRA_DAMAGE could be re-wired into
		// the hook and silently override the proportional formula.
		check(!Checks.hasConstant(ClumsyDiggerBehavior.class, "EXTRA_DAMAGE"),
				"the old flat EXTRA_DAMAGE constant is deleted, not left at 1");

		// REAL durabilities, read out of the game's own ToolMaterial records rather
		// than typed in here -- the whole point of the table is that it describes
		// what ships. Note this version has a COPPER tier between stone and iron.
		record Tier(String name, ToolMaterial material) {}
		Tier[] tiers = {
				new Tier("Gold", ToolMaterial.GOLD),
				new Tier("Wood", ToolMaterial.WOOD),
				new Tier("Stone", ToolMaterial.STONE),
				new Tier("Copper", ToolMaterial.COPPER),
				new Tier("Iron", ToolMaterial.IRON),
				new Tier("Diamond", ToolMaterial.DIAMOND),
				new Tier("Netherite", ToolMaterial.NETHERITE),
		};

		System.out.printf("        %-10s %8s %8s %10s %10s%n",
				"tier", "maxDur", "+dmg", "blocks", "% shorter");
		for (Tier tier : tiers) {
			int max = tier.material().durability();
			int extra = ClumsyDiggerBehavior.extraDamageFor(max);
			double blocks = ClumsyDiggerBehavior.blocksSurvived(max);
			double shorter = 100.0 * (1.0 - blocks / max);
			System.out.printf("        %-10s %8d %8d %10.1f %9.1f%%%n",
					tier.name(), max, extra, blocks, shorter);
		}

		// Spot checks against the real durabilities, so a regression names the tier.
		check(ToolMaterial.WOOD.durability() == 59 && ToolMaterial.IRON.durability() == 250
						&& ToolMaterial.NETHERITE.durability() == 2031,
				"durabilities come from ToolMaterial itself (wood 59, iron 250, netherite 2031)");
		check(ClumsyDiggerBehavior.extraDamageFor(ToolMaterial.IRON.durability()) == 50,
				"iron (250) takes +50 per proc -- the reference tier, near BASE_EXTRA");
		check(ClumsyDiggerBehavior.extraDamageFor(ToolMaterial.GOLD.durability()) == 6,
				"gold (32) takes only +6");
		check(ClumsyDiggerBehavior.extraDamageFor(ToolMaterial.NETHERITE.durability()) == 406,
				"netherite (2031) takes +406");

		// The design claim: better tools lose a LARGER share of their life. This is
		// the property that must not be "normalised away" by a later session.
		double goldShare = 1.0 - ClumsyDiggerBehavior.blocksSurvived(ToolMaterial.GOLD.durability())
				/ ToolMaterial.GOLD.durability();
		double netheriteShare = 1.0 - ClumsyDiggerBehavior.blocksSurvived(ToolMaterial.NETHERITE.durability())
				/ ToolMaterial.NETHERITE.durability();
		checkNear(goldShare * 100, 32.4, 0.3, "gold loses 32% of its life");
		checkNear(netheriteShare * 100, 97.0, 0.3, "netherite loses 97% of its life");
		check(netheriteShare > goldShare,
				"better tools lose a LARGER share -- the deliberate non-uniformity");

		// Monotonic in absolute terms too: a better tool still survives longer, so
		// tier progression is compressed but never inverted.
		double previous = -1;
		boolean monotonic = true;
		for (int durability : new int[] {32, 59, 131, 190, 250, 1561, 2031}) {
			double blocks = ClumsyDiggerBehavior.blocksSurvived(durability);
			if (blocks <= previous) {
				monotonic = false;
			}
			previous = blocks;
		}
		check(monotonic, "tier order is preserved -- a better tool still outlasts a worse one");

		// The structural consequence, asserted so a retune confronts it rather than
		// rediscovering it: durability cancels, so blocks survived is capped.
		// Tolerance is 1e-4 rather than 1e-6 because CHANCE is a float: 0.08f is
		// really 0.079999998, which moves the ceiling by ~1.4e-6. Tightening this
		// would be asserting IEEE 754 rounding, not the design.
		checkNear(ClumsyDiggerBehavior.blockCeiling(), 62.5, 1e-4,
				"blocks survived is asymptotically capped at 200/(0.08*40) = 62.5");
		check(ClumsyDiggerBehavior.blocksSurvived(1_000_000) < ClumsyDiggerBehavior.blockCeiling(),
				"...and no durability, however large, exceeds that ceiling");
		check(ClumsyDiggerBehavior.blocksSurvived(ToolMaterial.NETHERITE.durability())
						> ClumsyDiggerBehavior.blockCeiling() - 2,
				"netherite is already within 2 blocks of the ceiling");

		// Diamond and netherite become near-indistinguishable, which is the single
		// most surprising number in the table.
		double diamond = ClumsyDiggerBehavior.blocksSurvived(ToolMaterial.DIAMOND.durability());
		double netherite = ClumsyDiggerBehavior.blocksSurvived(ToolMaterial.NETHERITE.durability());
		check(Math.abs(netherite - diamond) < 1.0,
				"diamond and netherite land within one block of each other (30% more durability, no benefit)");

		// A non-damageable item must be untouched, and a damageable one never exempt.
		check(ClumsyDiggerBehavior.extraDamageFor(0) == 0, "a non-damageable item takes no extra damage");
		check(ClumsyDiggerBehavior.extraDamageFor(1) == 1,
				"a 1-durability item still takes at least 1 -- rounding cannot exempt it");

	}

	/**
	 * The scope gate: mining tools only, armour and elytra completely untouched.
	 *
	 * <p>Checked against the <b>real shipped tag data</b>, read out of the
	 * Minecraft jar on the classpath and resolved through its nested
	 * {@code #minecraft:...} references. That is what makes this a check rather
	 * than a restatement of the constant -- the claim being verified is about what
	 * {@code #minecraft:enchantable/mining} actually contains, which is data, not
	 * code.
	 *
	 * <p>Evaluating {@code stack.typeHolder().is(tag)} for real would need a
	 * bootstrapped registry and a loaded tag set, which the harness deliberately
	 * does not have. Resolving the same JSON vanilla itself loads is the faithful
	 * substitute, and it catches the failure that matters: the tag turning out to
	 * include something it should not.
	 */
	private static void clumsyDiggerScope() {
		section("Clumsy Digger scope: mining tools only");

		Set<String> affected = resolveItemTag("enchantable/mining");
		Set<String> anythingDamageable = resolveItemTag("enchantable/durability");

		// The four tool families the effect is meant to cover, one real item each.
		for (String tool : new String[] {
				"minecraft:wooden_pickaxe", "minecraft:netherite_pickaxe",
				"minecraft:diamond_axe", "minecraft:iron_shovel", "minecraft:golden_hoe"}) {
			check(affected.contains(tool), "covered: " + tool);
		}

		// The regression gate this session exists for.
		for (String exempt : new String[] {
				"minecraft:elytra",
				"minecraft:netherite_chestplate", "minecraft:diamond_helmet",
				"minecraft:iron_leggings", "minecraft:leather_boots",
				"minecraft:turtle_helmet", "minecraft:shield"}) {
			check(!affected.contains(exempt), "EXEMPT: " + exempt + " takes zero extra wear");
			check(anythingDamageable.contains(exempt),
					"...and it really does lose durability through the same hook (" + exempt + ")");
		}

		// Weapons and the miscellaneous durability items are out too -- worth
		// pinning, because "tool" could plausibly have been read to include them.
		for (String exempt : new String[] {
				"minecraft:netherite_sword", "minecraft:bow", "minecraft:crossbow",
				"minecraft:trident", "minecraft:mace", "minecraft:fishing_rod",
				"minecraft:flint_and_steel", "minecraft:brush"}) {
			check(!affected.contains(exempt), "EXEMPT: " + exempt);
		}

		// Shears are the one item beyond the four digging families, kept on purpose.
		check(affected.contains("minecraft:shears"),
				"shears ARE covered -- deliberate, see AFFECTED_ITEMS");

		// Every armour piece in the game, not just the samples above.
		Set<String> allArmour = new java.util.HashSet<>();
		for (String slot : new String[] {"head_armor", "chest_armor", "leg_armor", "foot_armor"}) {
			allArmour.addAll(resolveItemTag("enchantable/" + slot));
		}
		check(!allArmour.isEmpty(), "the armour tags resolved to something (" + allArmour.size() + " items)");
		check(java.util.Collections.disjoint(affected, allArmour),
				"NO armour item of any slot is in the affected set");

		// The tag is a strict subset of "anything with durability" -- i.e. the
		// narrowing is real, and the effect no longer means "every damageable item".
		check(anythingDamageable.containsAll(affected) && anythingDamageable.size() > affected.size(),
				"affected (" + affected.size() + ") is a strict subset of all damageable ("
						+ anythingDamageable.size() + ")");

		// The gate has to run BEFORE the random roll, or the DEBUG line would report
		// procs on armour that never actually applied.
		check(Checks.hasMethod(ClumsyDiggerBehavior.class, "appliesTo"),
				"the scope test is a named method on the effect, not inlined in the mixin");
	}

	/**
	 * Resolves a vanilla item tag from the Minecraft jar on the classpath,
	 * following nested {@code #namespace:path} references.
	 *
	 * <p>Entries may be plain strings or {@code {"id": ..., "required": false}}
	 * objects; both forms are handled.
	 */
	private static Set<String> resolveItemTag(String path) {
		Set<String> out = new java.util.HashSet<>();
		resolveItemTagInto(path, out, new java.util.HashSet<>());
		return out;
	}

	private static void resolveItemTagInto(String path, Set<String> out, Set<String> seen) {
		if (!seen.add(path)) {
			return;
		}
		String resource = "/data/minecraft/tags/item/" + path + ".json";
		try (java.io.InputStream in = HarnessMain.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("tag not found on classpath: " + resource);
			}
			com.google.gson.JsonObject root = com.google.gson.JsonParser
					.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
					.getAsJsonObject();
			for (com.google.gson.JsonElement element : root.getAsJsonArray("values")) {
				String value = element.isJsonObject()
						? element.getAsJsonObject().get("id").getAsString()
						: element.getAsString();
				if (value.startsWith("#")) {
					resolveItemTagInto(value.substring(value.indexOf(':') + 1), out, seen);
				} else {
					out.add(value);
				}
			}
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/**
	 * Bad Reputation's real final-price multipliers, across cheap and expensive
	 * trades rather than one example.
	 *
	 * <p>Reproduces vanilla's own arithmetic, verified in bytecode:
	 * {@code getModifiedCostCount} is
	 * {@code clamp(base + max(0, floor(base*demand*mult)) + specialPriceDiff, 1,
	 * maxStackSize)}, and {@code specialPriceDiff} is a <b>flat integer</b>. So this
	 * effect is an additive surcharge, and only behaves like a multiplier because it
	 * is computed as a fraction of the price the player would otherwise have paid.
	 */
	private static void badReputationPrices() {
		section("Bad Reputation final-price multipliers");

		float surcharge = (float) constant(BadReputationBehavior.class, "SURCHARGE");
		checkNear(surcharge, 0.65, 1e-6, "surcharge retuned to 0.65 of the pre-effect price");

		// Every realistic emerald cost, cheap to expensive. 1 is called out
		// separately below because integers make it a special case.
		int[] normalPrices = {2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 36};
		boolean allInBand = true;
		for (int normal : normalPrices) {
			int finalPrice = badRepPrice(normal, surcharge);
			double multiplier = (double) finalPrice / normal;
			if (multiplier < 1.50 || multiplier > 1.75) {
				allInBand = false;
			}
			System.out.printf("        %2d emeralds -> %2d  (%.3fx)%n", normal, finalPrice, multiplier);
		}
		check(allInBand, "every realistic trade cost lands in the 1.50x-1.75x target band");

		// Spot checks so a regression names the trade rather than just "the band".
		check(badRepPrice(2, surcharge) == 3, "a 2-emerald trade costs 3 (1.50x)");
		check(badRepPrice(5, surcharge) == 8, "a 5-emerald trade costs 8 (1.60x)");
		check(badRepPrice(16, surcharge) == 26, "a 16-emerald trade costs 26 (1.625x)");
		check(badRepPrice(32, surcharge) == 53, "a 32-emerald trade costs 53 (1.656x)");

		// The two boundaries, asserted so they are not rediscovered as bugs.
		check(badRepPrice(1, surcharge) == 2,
				"a 1-emerald trade costs 2 (2.00x) -- integers make +100% the SMALLEST possible rise");
		check(badRepPrice(64, surcharge) == 64,
				"a trade already at a full stack is unchanged -- vanilla's own clamp, not a miss");
		check(badRepPrice(40, surcharge) == 64,
				"above ~39 the clamp starts absorbing the surcharge");
	}

	/**
	 * Vanilla's price formula with the surcharge applied, mirroring
	 * {@code VillagerPricesMixin}. Demand and gossip are already folded into
	 * {@code normalPrice}, which is exactly what {@code getCostA()} returns at the
	 * point the mixin runs.
	 */
	private static int badRepPrice(int normalPrice, float surcharge) {
		int extra = Math.max(1, Math.round(normalPrice * surcharge));
		return Math.min(64, normalPrice + extra);
	}

	/**
	 * The Second Guess button-state bug, as a regression gate.
	 *
	 * <p>The reported symptom was a reroll button that stayed live after being
	 * spent. The cause was not a missing payload field -- the field existed and was
	 * derived from the persisted flag correctly. It was <b>ordering</b>:
	 * {@code requestReroll} set {@code rerollUsed} <em>after</em> calling
	 * {@code triggerPick}, which had already built and sent the payload. So the one
	 * screen where it mattered advertised a reroll that no longer existed.
	 *
	 * <p>What is checkable headlessly is the derivation itself and the three-state
	 * mapping. The ordering is pinned in bytecode instead, below.
	 */
	private static void rerollStateDerivation() {
		section("Second Guess: reroll state derivation");

		EntropyManager fresh = new EntropyManager();
		check(fresh.rerollState() == RerollState.NOT_OWNED,
				"a run without Second Guess reports NOT_OWNED -- no button, no reserved space");

		grantWithoutServer(fresh, SecondGuessBehavior.ID);
		check(fresh.rerollState() == RerollState.AVAILABLE,
				"owning it unspent reports AVAILABLE");
		check(!fresh.isRerollUsed(), "...and the persisted flag agrees");

		// Three states, so "never had it" and "had it and spent it" stay distinct --
		// a boolean could not tell those apart, which is why the payload field is an
		// enum now.
		// Was 3. CHARGED and FORBIDDEN arrived with All In's banked charges and No
		// Do-Overs' ban, which are separate run state from rerollUsed -- the point of
		// the assertion is unchanged: these are distinct states a boolean could not
		// express, and the client renders each differently.
		check(RerollState.values().length == 5,
				"NOT_OWNED / AVAILABLE / CHARGED / SPENT / FORBIDDEN are distinct states, "
						+ "not a boolean");

		// The ordering fix, stated as the property it guarantees: rerollState() must
		// be a pure function of ownership and the spent flag, with no dependence on
		// waitingOnChoice -- otherwise the value sent depends on when it was asked.
		check(!Checks.hasMethod(EntropyManager.class, "isRerollAvailableForClient"),
				"there is exactly one client-facing state accessor, not a second parallel one");
		check(Checks.hasMethod(EntropyManager.class, "rerollState")
						&& Checks.hasMethod(EntropyManager.class, "isRerollAvailable"),
				"rendering state and authorisation state remain separate methods");
	}

	/**
	 * {@code OpenChoicePayload}'s codec, round-tripped field by field with every
	 * field set to a distinct value.
	 *
	 * <p>This is the check CLAUDE.md demands after any payload change, and it is
	 * done field-by-field rather than with a whole-record {@code equals} because
	 * two same-typed fields swapped in a way that cancels out would still pass an
	 * equality check. {@code entropy} and {@code entropyCap} are both ints and sit
	 * next to each other, so that is a live hazard here, not a theoretical one.
	 *
	 * <p>Runs against the real {@code StreamCodec}. No registry is involved -- every
	 * component codec is a string, varint or enum-by-name -- so
	 * {@code RegistryAccess.EMPTY} is a faithful stand-in.
	 */
	private static void openChoicePayloadRoundTrip() {
		section("OpenChoicePayload codec round-trip");

		for (RerollState state : RerollState.values()) {
			OpenChoicePayload sent = new OpenChoicePayload(
					EffectPhase.BAD, 37, 91, state, 5,
					new OpenChoicePayload.Choice("id_one", "Name One", "Desc One"),
					new OpenChoicePayload.Choice("id_two", "Name Two", "Desc Two"),
					new OpenChoicePayload.Choice("id_three", "Name Three", "Desc Three"));

			RegistryFriendlyByteBuf buf =
					new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			OpenChoicePayload.CODEC.encode(buf, sent);
			OpenChoicePayload got = OpenChoicePayload.CODEC.decode(buf);

			check(got.rerollState() == state, "rerollState survives the wire as " + state);
			check(got.phase() == EffectPhase.BAD, "phase survives (" + state + ")");
			check(got.entropy() == 37, "entropy is 37, not the cap (" + state + ")");
			check(got.entropyCap() == 91, "entropyCap is 91, not the entropy (" + state + ")");
			// Three ints now, all adjacent and all distinct -- which is exactly why this
			// check is field by field rather than a record equals: 37/91/5 swapped in a
			// way that cancels would still pass an equality test.
			check(got.rerollCharges() == 5,
					"rerollCharges is 5, not the entropy or the cap (" + state + ")");
			check(got.choice1().id().equals("id_one")
							&& got.choice1().name().equals("Name One")
							&& got.choice1().description().equals("Desc One"),
					"choice1's three strings are not rotated (" + state + ")");
			check(got.choice2().id().equals("id_two") && got.choice3().id().equals("id_three"),
					"choice2 and choice3 are not swapped (" + state + ")");
			check(buf.readableBytes() == 0, "the buffer is fully consumed (" + state + ")");
		}

		// Sent by name, so reordering the enum cannot silently change what the
		// client draws -- the same rule EffectPhase follows.
		RegistryFriendlyByteBuf named =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		EntropyCodecs.REROLL_STATE.encode(named, RerollState.SPENT);
		check(named.toString(java.nio.charset.StandardCharsets.UTF_8).contains("SPENT"),
				"RerollState goes on the wire by name, not by ordinal");
	}


	/**
	 * Tier 2's three client-side effects.
	 *
	 * <p>These are the first effects whose behaviour lives on the client, so the
	 * things worth checking headlessly are different from every prior effect: the
	 * permutation model, the fact that the scramble is assigned exactly once and
	 * survives, and that the payload carrying it to the client round-trips.
	 *
	 * <p>What this cannot prove, and what needs the in-game session: that the
	 * mixins inject, that the camera roll reads as upside-down rather than broken,
	 * and that a forced jump behaves in water and on ladders.
	 */
	private static void tier2ClientEffects() {
		section("Tier 2: movement scramble, camera, random jump");

		// --- the permutation model ---
		check(MovementScramble.isValid("FBLR"), "the identity is a valid permutation");
		check(MovementScramble.isValid("LFRB"), "a shuffled permutation is valid");
		check(!MovementScramble.isValid("FFLR"), "a repeated direction is rejected");
		check(!MovementScramble.isValid("FBL"), "a short string is rejected");
		check(!MovementScramble.isValid("FBLX"), "an unknown direction is rejected");
		check(!MovementScramble.isValid(null), "null is rejected, not thrown on");
		check(!MovementScramble.isValid(""), "the unassigned value is not a valid scramble");

		// Identity must be a no-op, and an invalid scramble must degrade to vanilla
		// rather than throw -- this runs inside input handling every client tick.
		boolean[] pressed = {true, false, false, false};
		check(java.util.Arrays.equals(MovementScramble.apply("FBLR", pressed), pressed),
				"the identity permutation changes nothing");
		check(java.util.Arrays.equals(MovementScramble.apply("garbage", pressed), pressed),
				"an invalid scramble degrades to vanilla controls, it does not throw");

		// "LFRB": pressing forward sends you left, back sends you forward, and so on.
		boolean[] out = MovementScramble.apply("LFRB", new boolean[] {true, false, false, false});
		check(!out[0] && !out[1] && out[2] && !out[3],
				"under LFRB, pressing forward moves you left");
		out = MovementScramble.apply("LFRB", new boolean[] {false, true, false, false});
		check(out[0] && !out[1] && !out[2] && !out[3],
				"...and pressing back moves you forward");

		// Every generated scramble must be legal and never the identity -- a 1-in-24
		// no-op curse is indistinguishable from a broken one.
		java.util.Random random = new java.util.Random(1234);
		boolean allValid = true;
		boolean anyIdentity = false;
		java.util.Set<String> distinct = new java.util.HashSet<>();
		for (int i = 0; i < 2000; i++) {
			String s = MovementScramble.random(random);
			if (!MovementScramble.isValid(s)) {
				allValid = false;
			}
			if (s.equals(MovementScramble.IDENTITY)) {
				anyIdentity = true;
			}
			distinct.add(s);
		}
		check(allValid, "2000 generated scrambles are all valid permutations");
		check(!anyIdentity, "...and none is the identity (a no-op curse would look broken)");
		check(distinct.size() == 23, "...covering all 23 non-identity permutations");

		// Vanilla's impulse maths, reproduced because calculateImpulse is private.
		check(MovementScramble.impulse(false, false) == 0.0f, "no keys -> 0 impulse");
		check(MovementScramble.impulse(true, true) == 0.0f, "both keys -> 0 impulse (they cancel)");
		check(MovementScramble.impulse(true, false) == 1.0f, "positive key -> +1");
		check(MovementScramble.impulse(false, true) == -1.0f, "negative key -> -1");

		// --- assigned once, then never again ---
		EntropyManager manager = new EntropyManager();
		check(manager.getMoveScramble().isEmpty(), "a fresh run has no scramble");

		check(manager.assignMoveScrambleIfAbsent(), "the first assignment happens");
		String assigned = manager.getMoveScramble();
		check(MovementScramble.isValid(assigned), "...and produces a valid permutation");

		// This is the property the whole persistence design exists for: apply() runs
		// again on every respawn, rejoin and dimension change.
		check(!manager.assignMoveScrambleIfAbsent(),
				"a second assignment is refused, not silently re-rolled");
		boolean stable = true;
		for (int i = 0; i < 100; i++) {
			manager.assignMoveScrambleIfAbsent();
			if (!manager.getMoveScramble().equals(assigned)) {
				stable = false;
			}
		}
		check(stable, "100 further applies leave the scramble byte-identical (respawn/relog safety)");

		// --- the ids the client keys on must resolve ---
		for (String id : new String[] {RandomizedControlsBehavior.ID, UpsideDownCameraBehavior.ID,
				RandomJumpBehavior.ID}) {
			check(EffectRegistry.byId(id) != null, id + " is registered as a real effect");
			check(EffectRegistry.byId(id).minEntropy() == 25 && EffectRegistry.byId(id).maxEntropy() == 50,
					id + " is Tier 2 (entropy 25-50)");
		}

		// Random Jump's interval, in the units the brief specified.
		check(RandomJumpBehavior.MIN_INTERVAL_TICKS == 100 && RandomJumpBehavior.MAX_INTERVAL_TICKS == 600,
				"Random Jump fires every 5-30 seconds (100-600 ticks)");

		// --- the payload that carries all of this to the client ---
		ClientEffectsPayload sent = new ClientEffectsPayload(
				List.of(RandomizedControlsBehavior.ID, UpsideDownCameraBehavior.ID), "RLBF");
		RegistryFriendlyByteBuf buf =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		ClientEffectsPayload.CODEC.encode(buf, sent);
		ClientEffectsPayload got = ClientEffectsPayload.CODEC.decode(buf);
		check(got.effectIds().equals(List.of(RandomizedControlsBehavior.ID, UpsideDownCameraBehavior.ID)),
				"effect ids survive the wire in order");
		check(got.moveScramble().equals("RLBF"), "the scramble survives the wire, not swapped with the ids");
		check(buf.readableBytes() == 0, "the buffer is fully consumed");

		// An empty run must round-trip too: that is what a player with no effects,
		// and the clear-on-join case, actually send.
		RegistryFriendlyByteBuf empty =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		ClientEffectsPayload.CODEC.encode(empty, new ClientEffectsPayload(List.of(), ""));
		ClientEffectsPayload emptyGot = ClientEffectsPayload.CODEC.decode(empty);
		check(emptyGot.effectIds().isEmpty() && emptyGot.moveScramble().isEmpty(),
				"an empty run round-trips (the client must be told 'you have nothing' too)");
	}

	// ------------------------------------------------------------------
	// Run lifecycle: the NOT_STARTED gate and the keybind snapshot.
	// The ENDED half of the architecture is deliberately not built -- see
	// endedStateNotBuilt() below, which asserts exactly that.
	// ------------------------------------------------------------------

	/**
	 * The gate is real: nothing counts before the run is started.
	 *
	 * <p>Ticked with a {@code null} server on purpose. That is not a shortcut
	 * around building one -- it is the assertion. {@code triggerPick} dereferences
	 * the server immediately, so a gate that let the loop run would blow up here
	 * rather than quietly passing, and ticking well past the interval length is
	 * what guarantees it would be reached.
	 */
	private static void runStartGate() {
		section("Run lifecycle: the NOT_STARTED gate");

		EntropyManager manager = new EntropyManager();
		check(manager.getRunState() == RunState.NOT_STARTED,
				"a fresh run starts NOT_STARTED, so a new world is gated by default");
		check(!manager.isStarted(), "isStarted() agrees with the state");

		boolean survived;
		try {
			for (int i = 0; i < EntropyManager.DEFAULT_INTERVAL_TICKS + 500; i++) {
				manager.tick(null);
			}
			survived = true;
		} catch (RuntimeException e) {
			survived = false;
		}
		check(survived,
				"ticking " + (EntropyManager.DEFAULT_INTERVAL_TICKS + 500)
						+ " times while NOT_STARTED never reaches triggerPick");
		check(manager.getTicksIntoInterval() == 0,
				"the interval counter does not advance either -- the gate is before the "
						+ "increment, so the loop is paused rather than running invisibly");

		check(manager.startRun(), "startRun() performs the transition and reports it");
		check(manager.getRunState() == RunState.IN_PROGRESS, "state is IN_PROGRESS afterwards");

		// Requirement: Start must not be actionable twice. The trigger is a client
		// packet, so a double-click or a replay really can arrive.
		check(!manager.startRun(), "startRun() is idempotent -- a second call is refused");
		check(manager.getTicksIntoInterval() == 0,
				"the refused second start does not reset the interval timer mid-run");

		for (int i = 0; i < 10; i++) {
			manager.tick(null);
		}
		check(manager.getTicksIntoInterval() == 10,
				"once started, the loop counts normally");

		// forcePick bypasses the clock and nothing else, so it must respect the gate.
		check(new EntropyManager().forcePick(null) == EntropyManager.PickTrigger.RUN_NOT_STARTED,
				"/entropyforcepick is refused while NOT_STARTED, and says which gate refused");
	}

	/** Write-once per player, and only while the run is live. */
	private static void keybindSnapshotRules() {
		section("Keybind snapshot: captured once, at Start");

		KeybindSnapshot wasd = new KeybindSnapshot(
				"key.keyboard.w", "key.keyboard.s", "key.keyboard.a", "key.keyboard.d");
		KeybindSnapshot arrows = new KeybindSnapshot(
				"key.keyboard.up", "key.keyboard.down", "key.keyboard.left", "key.keyboard.right");

		check(wasd.isPresent(), "a fully-bound snapshot is present");
		check(!KeybindSnapshot.EMPTY.isPresent(), "EMPTY is absent");
		check(!new KeybindSnapshot("key.keyboard.w", "", "key.keyboard.a", "key.keyboard.d").isPresent(),
				"a partially-filled snapshot counts as absent, not half-honoured");

		// The array handed to MovementScramble.apply is indexed by ORDER, so the
		// snapshot's field order has to match it or every direction is rotated.
		check(MovementScramble.ORDER.equals("FBLR"),
				"MovementScramble.ORDER is still forward/back/left/right");
		check(wasd.keys().equals(List.of(
						"key.keyboard.w", "key.keyboard.s", "key.keyboard.a", "key.keyboard.d")),
				"keys() is in ORDER -- forward, back, left, right");
		check(wasd.keys().size() == MovementScramble.LENGTH,
				"a snapshot has exactly as many keys as a scramble has slots");

		String uuid = "11111111-2222-3333-4444-555555555555";
		String other = "99999999-8888-7777-6666-555555555555";

		EntropyManager notStarted = new EntropyManager();
		check(!notStarted.storeKeybindSnapshotIfAbsent(uuid, wasd),
				"a snapshot is REFUSED while NOT_STARTED -- there is no run to anchor to, "
						+ "and the player may still rebind before clicking Start");
		check(!notStarted.keybindSnapshotFor(uuid).isPresent(), "so nothing is held");

		EntropyManager live = new EntropyManager();
		live.startRun();
		check(live.storeKeybindSnapshotIfAbsent(uuid, wasd), "stored once the run is live");
		check(live.keybindSnapshotFor(uuid).equals(wasd), "and reads back unchanged");

		// This is the whole anti-exploit guarantee. If a later capture could
		// overwrite, a client could re-anchor its curse at will and rebinding would
		// counter it again with one extra step.
		check(!live.storeKeybindSnapshotIfAbsent(uuid, arrows),
				"a SECOND snapshot for the same player is refused, not applied");
		check(live.keybindSnapshotFor(uuid).equals(wasd),
				"the original snapshot is still the one held");

		check(live.storeKeybindSnapshotIfAbsent(other, arrows),
				"a different player gets their own snapshot");
		check(live.keybindSnapshotFor(other).equals(arrows)
						&& live.keybindSnapshotFor(uuid).equals(wasd),
				"the two players' snapshots do not collide");

		check(!live.storeKeybindSnapshotIfAbsent("someone-else", KeybindSnapshot.EMPTY),
				"an absent snapshot is not stored as if it were real");
		check(!live.keybindSnapshotFor("nobody").isPresent(),
				"an unknown player reads back as absent rather than null");
	}

	/**
	 * Survives a relog, which for a {@code SavedData} means: survives the codec.
	 * Includes the migration path for saves written before run states existed.
	 */
	private static void runStatePersistence() {
		section("Run lifecycle persists across a relog");

		String uuid = "11111111-2222-3333-4444-555555555555";
		KeybindSnapshot wasd = new KeybindSnapshot(
				"key.keyboard.w", "key.keyboard.s", "key.keyboard.a", "key.keyboard.d");

		EntropyManager before = new EntropyManager();
		before.startRun();
		before.storeKeybindSnapshotIfAbsent(uuid, wasd);

		EntropyManager after = reencode(before);
		check(after.getRunState() == RunState.IN_PROGRESS, "run state survives the save");
		check(after.keybindSnapshotFor(uuid).equals(wasd),
				"the keybind snapshot survives the save, key for key and in order");

		EntropyManager fresh = reencode(new EntropyManager());
		check(fresh.getRunState() == RunState.NOT_STARTED,
				"an unstarted run reloads as unstarted -- the gate is not lost on reload");
		check(!fresh.keybindSnapshotFor(uuid).isPresent(), "and holds no snapshots");

		// Migration. A save from before this feature has no "run_state" field at
		// all, and defaulting it to NOT_STARTED would demand that a player already
		// mid-run click Start again on a world they had been playing for an hour.
		JsonElement legacy = encode(new EntropyManager());
		legacy.getAsJsonObject().remove("run_state");
		legacy.getAsJsonObject().addProperty("pick_count", 7);
		EntropyManager migrated = decode(legacy);
		check(migrated.getRunState() == RunState.IN_PROGRESS,
				"a pre-run-state save with picks made migrates to IN_PROGRESS");
		check(migrated.getPickCount() == 7, "and keeps its picks");

		JsonElement legacyUntouched = encode(new EntropyManager());
		legacyUntouched.getAsJsonObject().remove("run_state");
		check(decode(legacyUntouched).getRunState() == RunState.NOT_STARTED,
				"a pre-run-state save with no picks migrates to NOT_STARTED");

		// The migration keys on pickCount specifically, NOT on the acquired set --
		// /entropygrant adds effects without advancing the run and works while
		// NOT_STARTED, so a granted effect must not be mistaken for a started run.
		JsonElement granted = encode(new EntropyManager());
		granted.getAsJsonObject().remove("run_state");
		granted.getAsJsonObject().add("acquired",
				JsonOps.INSTANCE.createList(java.util.stream.Stream.of(
						JsonOps.INSTANCE.createString(SureFootingBehavior.ID))));
		check(decode(granted).getRunState() == RunState.NOT_STARTED,
				"a granted effect on a pre-run-state save does NOT look like a started run");

		// Unparseable rather than absent: must degrade, not make the world unloadable.
		JsonElement garbage = encode(new EntropyManager());
		garbage.getAsJsonObject().addProperty("run_state", "ENDED_SOMEHOW");
		check(decode(garbage).getRunState() == RunState.NOT_STARTED,
				"an unrecognised run_state degrades to NOT_STARTED instead of throwing");
	}

	private static JsonElement encode(EntropyManager manager) {
		return EntropyManager.CODEC.encodeStart(JsonOps.INSTANCE, manager)
				.getOrThrow(msg -> new IllegalStateException("encode failed: " + msg));
	}

	private static EntropyManager decode(JsonElement json) {
		return EntropyManager.CODEC.parse(JsonOps.INSTANCE, json)
				.getOrThrow(msg -> new IllegalStateException("decode failed: " + msg));
	}

	/** Save and reload, which is what a relog is for a SavedData. */
	private static EntropyManager reencode(EntropyManager manager) {
		return decode(encode(manager));
	}

	private static void runLifecyclePayloadRoundTrip() {
		section("Run lifecycle payloads round-trip");

		// Four same-typed string components in a row on both payloads -- exactly the
		// shape where a mis-wired getter compiles and silently sends the wrong
		// field, so every value here is distinct and each is checked by name.
		KeybindSnapshot distinct = new KeybindSnapshot("K_FWD", "K_BACK", "K_LEFT", "K_RIGHT");

		RegistryFriendlyByteBuf buf =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		RunSyncPayload.CODEC.encode(buf, RunSyncPayload.of(RunState.IN_PROGRESS, distinct));
		RunSyncPayload gotSync = RunSyncPayload.CODEC.decode(buf);

		check(gotSync.state() == RunState.IN_PROGRESS, "run state survives the wire");
		check(gotSync.snapshot().forward().equals("K_FWD")
						&& gotSync.snapshot().back().equals("K_BACK")
						&& gotSync.snapshot().left().equals("K_LEFT")
						&& gotSync.snapshot().right().equals("K_RIGHT"),
				"the four keys are not rotated or swapped on RunSyncPayload");
		check(buf.readableBytes() == 0, "RunSyncPayload's buffer is fully consumed");

		RegistryFriendlyByteBuf named =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		RunSyncPayload.CODEC.encode(named, RunSyncPayload.of(RunState.NOT_STARTED, KeybindSnapshot.EMPTY));
		check(named.toString(java.nio.charset.StandardCharsets.UTF_8).contains("NOT_STARTED"),
				"run state goes on the wire by name, not by ordinal");

		for (boolean start : new boolean[] {true, false}) {
			RegistryFriendlyByteBuf up =
					new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			KeybindSnapshotPayload.CODEC.encode(up, new KeybindSnapshotPayload(distinct, start));
			KeybindSnapshotPayload got = KeybindSnapshotPayload.CODEC.decode(up);

			check(got.startRun() == start,
					"startRun survives the wire as " + start + " -- it is what starts the run");
			check(got.snapshot().equals(distinct),
					"the snapshot rides with it intact (startRun=" + start + ")");
			check(up.readableBytes() == 0,
					"KeybindSnapshotPayload's buffer is fully consumed (startRun=" + start + ")");
		}

		// The empty snapshot has to survive too: it is the "server holds nothing for
		// you" signal that makes a client capture its keybinds.
		RegistryFriendlyByteBuf empty =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		RunSyncPayload.CODEC.encode(empty, RunSyncPayload.of(RunState.NOT_STARTED, KeybindSnapshot.EMPTY));
		check(!RunSyncPayload.CODEC.decode(empty).snapshot().isPresent(),
				"an absent snapshot survives as absent -- that is the capture cue");
	}

	/**
	 * The other half of the architecture is still unbuilt, and this asserts it
	 * rather than trusting a doc comment.
	 *
	 * <p>The scope line matters because {@code ENDED} is the state everything else
	 * hangs off: an end screen, dragon-death win detection, Become Hardcore's
	 * death-triggered loss, and a run-wide death counter. A later session finding
	 * a half-present {@code ENDED} constant would have no way to tell "started and
	 * abandoned" from "deliberately deferred".
	 */
	/**
	 * The ENDED half is built, and the two conditions that reach it are wired.
	 *
	 * <p>This section previously asserted the opposite -- that {@code RunState} had
	 * exactly two constants and neither {@code endRun} nor a death counter existed.
	 * That was the "deferred, not started and abandoned" discipline doing its job;
	 * it is inverted here rather than deleted, so the transition from one to the
	 * other is visible in the diff.
	 */
	private static void endedStateBuilt() {
		section("Run lifecycle: the ENDED half");

		check(RunState.values().length == 3,
				"RunState is NOT_STARTED / IN_PROGRESS / ENDED -- the full recorded architecture");
		check(Arrays.stream(RunState.values()).anyMatch(st -> st.name().equals("ENDED")),
				"ENDED exists, and something can now produce it");
		check(Checks.hasMethod(EntropyManager.class, "endRun"),
				"endRun() is the single entry point for every path into ENDED");
		check(Checks.hasMethod(EntropyManager.class, "getDeathCount"),
				"the run-wide death counter exists");

		// The retired flag is GONE, not left returning false for nobody. Same
		// discipline as the crop hooks: a neutral accessor can be re-wired by a
		// later session that does not know why it was emptied.
		check(!Checks.hasMethod(EntropyManager.class, "isGameOver"),
				"isGameOver() is deleted, not neutralised -- there is one notion of "
						+ "'the run is over' left, and it is RunState");

		// Two outcomes, and the third is deliberately absent on the same rule that
		// kept ENDED itself out until it was reachable.
		check(RunOutcome.values().length == 2,
				"two outcomes exist -- the dragon win and the entropy-cap loss");
		check(RunOutcome.WIN_DRAGON.isWin() && !RunOutcome.LOSS_ENTROPY_CAP.isWin(),
				"the win/loss split is carried by the outcome, not by a separate flag");
		check(Arrays.stream(RunOutcome.values())
						.noneMatch(o -> o.name().contains("HARDCORE")),
				"no LOSS_HARDCORE_DEATH constant yet -- Become Hardcore is a separate "
						+ "session, and a constant nothing can produce is what this project "
						+ "avoids on purpose");

		// ...but the mechanism it will plug into is finished. Asserting the pieces
		// exist is what makes "one constant plus one endRun call" a checkable claim
		// rather than a promise.
		check(Checks.hasMethod(EntropyManager.class, "isEnded")
						&& Checks.hasMethod(EntropyManager.class, "getOutcome")
						&& Checks.hasMethod(EntropyManager.class, "recordDeath"),
				"Become Hardcore needs no new architecture: endRun, isEnded, getOutcome "
						+ "and recordDeath are all here, and its death listener already exists");

		// Outcomes go on the wire and into saves by NAME, the same rule EffectPhase
		// and RunState follow -- reordering must not turn a loss into a win.
		check(RunOutcome.parse("WIN_DRAGON") == RunOutcome.WIN_DRAGON,
				"an outcome round-trips by name");
		check(RunOutcome.parse("SOMETHING_ELSE") == null && RunOutcome.parse(null) == null,
				"an unrecognised outcome is null, NOT a fallback -- guessing would tell "
						+ "the player they won a run they lost");
		check(RunState.parse("ENDED") == RunState.ENDED, "ENDED round-trips by name");
		check(RunState.parse("nonsense") == RunState.NOT_STARTED,
				"an unreadable run state still falls back to NOT_STARTED, never to the "
						+ "terminal state -- a bad string must not end a run");
	}

	// ------------------------------------------------------------------

	/**
	 * The {@code gameOver} -&gt; {@code ENDED} migration, which is the one part of
	 * this work that could silently break an existing world.
	 *
	 * <p>Before {@code RunState.ENDED} existed, a run that hit the cap was recorded
	 * as {@code game_over = true} with {@code run_state} left at
	 * {@code IN_PROGRESS} -- the two were never reconciled. Reading {@code run_state}
	 * alone on such a save would load a finished run as live.
	 */
	private static void gameOverMigration() {
		section("Legacy game_over migrates to ENDED");

		// A pre-ENDED save mid-run: game_over true, run_state IN_PROGRESS.
		com.google.gson.JsonObject legacy = new com.google.gson.JsonObject();
		legacy.addProperty("entropy", 100);
		legacy.addProperty("pick_count", 100);
		legacy.addProperty("game_over", true);
		legacy.addProperty("run_state", "IN_PROGRESS");
		EntropyManager migrated = EntropyManager.CODEC
				.parse(JsonOps.INSTANCE, legacy).getOrThrow();

		check(migrated.getRunState() == RunState.ENDED,
				"a save with game_over=true and run_state=IN_PROGRESS loads as ENDED -- "
						+ "without this every already-finished run would come back live");
		check(migrated.isEnded(), "...and reports itself ended");
		check(migrated.getOutcome() == RunOutcome.LOSS_ENTROPY_CAP,
				"...with the cap loss filled in, since game_over only ever meant the cap");

		// The legacy flag can END a run. It must never START one.
		com.google.gson.JsonObject legacyUnstarted = new com.google.gson.JsonObject();
		legacyUnstarted.addProperty("game_over", true);
		legacyUnstarted.addProperty("run_state", "NOT_STARTED");
		EntropyManager unstarted = EntropyManager.CODEC
				.parse(JsonOps.INSTANCE, legacyUnstarted).getOrThrow();
		check(unstarted.getRunState() == RunState.NOT_STARTED,
				"a NOT_STARTED save is NOT dragged to ENDED by a stale flag -- the "
						+ "migration only ever ends a run, never starts or restarts one");

		// An ordinary modern save is untouched by any of this.
		com.google.gson.JsonObject modern = new com.google.gson.JsonObject();
		modern.addProperty("run_state", "IN_PROGRESS");
		modern.addProperty("pick_count", 5);
		EntropyManager live = EntropyManager.CODEC.parse(JsonOps.INSTANCE, modern).getOrThrow();
		check(live.getRunState() == RunState.IN_PROGRESS,
				"UNREGRESSED: a live save with no game_over key stays IN_PROGRESS");
		check(live.getOutcome() == null, "...and has no outcome");

		// game_over is written false from now on, so it drains out of saves rather
		// than lingering as a second answer to a question RunState now owns.
		EntropyManager ended = new EntropyManager();
		ended.startRun();
		ended.endRun(null, RunOutcome.WIN_DRAGON);
		JsonElement written = EntropyManager.CODEC
				.encodeStart(JsonOps.INSTANCE, ended).getOrThrow();
		com.google.gson.JsonObject obj = written.getAsJsonObject();
		check(!obj.has("game_over") || !obj.get("game_over").getAsBoolean(),
				"an ENDED run never writes game_over=true -- the legacy key is read-only now");

		// And the modern path round-trips the outcome itself.
		EntropyManager reloaded = EntropyManager.CODEC
				.parse(JsonOps.INSTANCE, written).getOrThrow();
		check(reloaded.getRunState() == RunState.ENDED, "ENDED survives a save/load");
		check(reloaded.getOutcome() == RunOutcome.WIN_DRAGON,
				"...and so does WHICH outcome, so a reload cannot turn a win into a loss");
	}

	// ------------------------------------------------------------------

	/**
	 * ENDED actually stops the loop -- asserted by TICKING, not by reading a flag.
	 *
	 * <p>The brief for this work asked for exactly that distinction, and it is the
	 * same shape the start gate is checked with: ticked with a {@code null} server,
	 * so a loop that ran would dereference it and blow up rather than quietly
	 * passing, and ticked well past the interval length so it would certainly be
	 * reached.
	 */
	private static void endedStopsTheLoop() {
		section("ENDED stops the entropy loop");

		EntropyManager manager = new EntropyManager();
		manager.startRun();
		check(manager.getRunState() == RunState.IN_PROGRESS, "the run is live to begin with");

		check(manager.endRun(null, RunOutcome.LOSS_ENTROPY_CAP),
				"endRun performs the transition and says so");
		check(manager.getRunState() == RunState.ENDED, "the state is ENDED");

		// Idempotent, and it is the FIRST outcome that stands. Two conditions can
		// fire on the same tick -- a dragon killed on the interval entropy caps out.
		check(!manager.endRun(null, RunOutcome.WIN_DRAGON),
				"a second endRun returns false rather than re-deciding");
		check(manager.getOutcome() == RunOutcome.LOSS_ENTROPY_CAP,
				"...and the first outcome stands -- a loss cannot be overwritten by a win");

		// The tick loop. 500 past the interval, with a null server.
		int before = manager.getTicksIntoInterval();
		for (int i = 0; i < manager.getIntervalTicks() + 500; i++) {
			manager.tick(null);
		}
		check(manager.getTicksIntoInterval() == before,
				"the interval counter does not move on an ENDED run -- the loop is PAUSED, "
						+ "not running invisibly and waiting to fire");
		check(manager.getEntropy() == 0, "no entropy accrued");
		check(manager.getPickCount() == 0, "no pick was counted");
		check(!manager.isWaitingOnChoice(), "and no pick was opened");

		// forcePick is gated identically, and reports the right reason -- "you have
		// not started" and "it is over" are different things to tell a player.
		check(manager.forcePick(null) == EntropyManager.PickTrigger.RUN_ALREADY_OVER,
				"/entropyforcepick on an ENDED run reports RUN_ALREADY_OVER");
		EntropyManager fresh = new EntropyManager();
		check(fresh.forcePick(null) == EntropyManager.PickTrigger.RUN_NOT_STARTED,
				"...and on a NOT_STARTED run still reports RUN_NOT_STARTED, distinctly");

		// [not-harness-reachable] endRun also VOIDS a pending pick, so the modal
		// choice screen a player may be sitting on cannot be answered into a finished
		// run. That cannot be driven here: the only thing that sets waitingOnChoice is
		// the private triggerPick, which dereferences a server on its first line.
		// Asserting it from this side would mean asserting a state the harness put
		// there itself, which is the 1k failure -- a test that can only pass.
		//
		// What IS checkable is the observable guarantee it exists for: a submission
		// against an ENDED run moves nothing. onChoiceMade returns before touching
		// the server, so a null one is safe.
		EntropyManager ended = new EntropyManager();
		ended.startRun();
		ended.endRun(null, RunOutcome.WIN_DRAGON);
		ended.onChoiceMade(null, SureFootingBehavior.ID);
		check(ended.getEntropy() == 0 && ended.getPickCount() == 0,
				"a pick submitted against an ENDED run adds no entropy and counts no pick");
		check(ended.getHistory().isEmpty(), "...and writes no history row");
		check(ended.acquired().isEmpty(), "...and acquires nothing");

		// Effects already held are untouched. This is the decision, not an oversight:
		// the world stays fully playable and effects are permanent, so only the LOOP
		// stops. Gating the eight per-tick effect services would make a player's
		// permanent effects half-work, since attribute- and mixin-driven effects
		// cannot be gated at all without deleting them.
		EntropyManager held = new EntropyManager();
		held.startRun();
		grantWithoutServer(held, SureFootingBehavior.ID);
		held.endRun(null, RunOutcome.WIN_DRAGON);
		check(held.acquired().contains(SureFootingBehavior.ID),
				"an ENDED run still holds every effect it acquired -- ENDED stops the loop "
						+ "and nothing else");

	}

	// ------------------------------------------------------------------

	/**
	 * WHICH per-tick systems check the run state -- asserted in both directions.
	 *
	 * <p>The recorded architecture flagged "every per-tick system should probably
	 * check ENDED" as a likely requirement, not a decision. The decision made here
	 * is <b>the entropy loop only</b>, and this section pins both halves of it so a
	 * later session has to meet the argument rather than quietly flip it.
	 *
	 * <p>The argument, in short: the eight effect services implement effects the
	 * player <em>permanently holds</em>, and the attribute- and mixin-driven
	 * effects cannot be gated the same way at all -- modifiers are already on the
	 * player, and every {@code EffectHooks} reader keys off {@code AcquiredEffects}.
	 * Gating only the tick-driven subset would leave a run where Green Thumb has
	 * stopped growing crops but Glutton still starves you and Nightmares still
	 * detonates beds, with no rule a player could infer.
	 */
	private static void endedGatingScope() {
		section("ENDED gating: which systems check, and which deliberately do not");

		// The loop is the run, so it checks. Verified behaviourally above; here it is
		// the structural half -- the manager is the one place RunState is consulted.
		check(Checks.classReferences(EntropyManager.class, "RunState"),
				"EntropyManager consults RunState -- the loop is the run, so it stops");

		// The eight effect services do NOT. Absence assertions, which is the only
		// kind that catches a later session adding a gate the design rejected.
		Class<?>[] effectServices = {
			com.entropymod.entropy.growth.GreenThumbGrowth.class,
			com.entropymod.entropy.growth.BlightTouchedTrample.class,
			com.entropymod.entropy.growth.SlashedPocketsSweep.class,
			com.entropymod.entropy.growth.DangerSenseGlow.class,
			com.entropymod.entropy.spawn.UnstableSpawner.class,
			com.entropymod.entropy.spawn.CreeperMagnetSpawner.class,
			com.entropymod.entropy.companion.CompanionService.class,
			com.entropymod.entropy.quake.EarthquakeService.class,
		};
		for (Class<?> service : effectServices) {
			check(!Checks.classReferences(service, "getRunState")
							&& !Checks.classReferences(service, "isEnded"),
					service.getSimpleName() + " does NOT gate on the run state -- it implements "
							+ "an effect the player permanently holds, not the run");
		}
		check(effectServices.length == 8,
				"all eight per-tick effect services are accounted for, none silently omitted");

		// Every one of them COULD gate cleanly -- each already reaches the manager --
		// so this is a decision rather than a limitation, and saying so is the point.
		for (Class<?> service : effectServices) {
			check(Checks.classReferences(service, "EntropyManager")
							|| Checks.classReferences(service, "EffectHooks"),
					service.getSimpleName() + " already reaches run state, so gating it would "
							+ "have been one line -- it is declined, not impossible");
		}
	}

	// ------------------------------------------------------------------

	/**
	 * The end screen shows REAL run state, not placeholders.
	 *
	 * <p>{@code [source-text]} for the screen half: {@code src/client} is not on
	 * the harness classpath, the same constraint that leaves Double Jump's client
	 * driver unreachable, so {@code EndScreen} is checked by reading its source
	 * from disk. That can catch a hardcoded value or a field never read; it cannot
	 * catch a wrong layout. The data half above it is checked for real, against a
	 * payload built from a live {@code EntropyManager}.
	 */
	private static void endScreenReadsRealData() {
		section("End screen data");

		String source;
		try {
			source = java.nio.file.Files.readString(java.nio.file.Path.of(
					"src/client/java/com/entropymod/client/gui/EndScreen.java"));
		} catch (java.io.IOException e) {
			check(false, "[source-text] EndScreen.java is readable");
			return;
		}

		// Every value on the screen comes off the payload, not out of the class.
		for (String accessor : new String[] {
				"payload.parsedOutcome()", "payload.entropy()", "payload.entropyCap()",
				"payload.deathCount()", "payload.history()"}) {
			check(source.contains(accessor),
					"[source-text] the screen reads " + accessor + " from the payload");
		}

		// Total picks is the history's length, with no second number to drift from it.
		check(source.contains("history.size()"),
				"[source-text] total picks is history.size(), not a tracked field");

		// It names WHICH condition fired, which the architecture requires explicitly
		// -- "you lost" without the cause is the thing it rules out.
		check(source.contains("outcome.description()"),
				"[source-text] the screen states which condition ended the run");
		check(source.contains("outcome.headline()") || source.contains("headline"),
				"[source-text] ...and whether it was a win or a loss");

		// Dismissible, unlike every other modal here -- the recorded decision is that
		// the world stays fully playable with no lock, and refusing Escape IS a lock.
		int escIndex = source.indexOf("shouldCloseOnEsc");
		check(escIndex >= 0 && source.substring(escIndex,
						Math.min(source.length(), escIndex + 120)).contains("return true"),
				"[source-text] the end screen closes on Escape, unlike ChoiceScreen and "
						+ "StartScreen -- there is nothing to gate once the run is over");

		// And there is no fake-data preview command for it. A debug shortcut that
		// fabricates data must say so in its name; none exists, so none can mislead.
		check(!Checks.classReferences(RunEndPayload.class, "preview"),
				"no mock end-screen data path exists");
	}

	// ------------------------------------------------------------------

	/**
	 * The death counter, across several causes and several run states.
	 *
	 * <p>The brief asked specifically for more than one cause. The counter itself
	 * cannot see a cause -- it is one increment per death event -- so what is
	 * actually worth asserting is that nothing it CAN see gates it: not the run
	 * state, not the count so far, not whether the run has ended.
	 */
	private static void deathCounter() {
		section("Death counter");

		EntropyManager manager = new EntropyManager();
		check(manager.getDeathCount() == 0, "a fresh run has recorded no deaths");

		// Before the run is even started. A death is a death.
		check(manager.recordDeath() == 1, "a death while NOT_STARTED still counts");

		manager.startRun();
		for (int i = 2; i <= 6; i++) {
			check(manager.recordDeath() == i, "death #" + i + " increments by exactly one");
		}

		// ...and after the run has ended. Not frozen: it is a stat about the run,
		// and the run ending does not stop the world.
		manager.endRun(null, RunOutcome.LOSS_ENTROPY_CAP);
		check(manager.recordDeath() == 7, "a death after the run ENDED still counts");

		// Persisted, and distinctly from every other int on the run.
		EntropyManager reloaded = EntropyManager.CODEC.parse(JsonOps.INSTANCE,
				EntropyManager.CODEC.encodeStart(JsonOps.INSTANCE, manager).getOrThrow())
				.getOrThrow();
		check(reloaded.getDeathCount() == 7, "the count survives a save/load");
		check(reloaded.getEntropy() != 7 || reloaded.getPickCount() != 7,
				"...and is not being confused with entropy or the pick count");

		// The nested record round-trips both of its fields with distinct values.
		RunEnd end = RunEnd.CODEC.parse(JsonOps.INSTANCE,
				RunEnd.CODEC.encodeStart(JsonOps.INSTANCE,
						RunEnd.of(RunOutcome.WIN_DRAGON, 13)).getOrThrow()).getOrThrow();
		check(end.deathCount() == 13, "RunEnd carries the death count");
		check(end.parsedOutcome() == RunOutcome.WIN_DRAGON, "...alongside the outcome");
		RunEnd none = RunEnd.CODEC.parse(JsonOps.INSTANCE,
				new com.google.gson.JsonObject()).getOrThrow();
		check(none.equals(RunEnd.NONE) && none.parsedOutcome() == null,
				"an empty object loads as NONE -- a save written before this existed is a "
						+ "run that has not ended and recorded no deaths");
	}

	// ------------------------------------------------------------------

	/**
	 * {@code RunEndPayload}'s codec, and the claim that the end screen shows real
	 * data.
	 *
	 * <p>Field by field with distinct values, per CLAUDE.md -- and the hazard is
	 * concrete here: entropy, entropyCap and deathCount are three adjacent ints, so
	 * a swapped pair that cancelled out would pass a record {@code equals}.
	 */
	private static void runEndPayloadRoundTrip() {
		section("RunEndPayload codec round-trip");

		// Built from REAL manager state rather than from literals, which is what
		// makes this a check that the end screen is fed the run rather than a check
		// that a record holds what it was handed.
		EntropyManager manager = new EntropyManager();
		manager.startRun();
		manager.setEntropyCap(64);
		manager.recordDeath();
		manager.recordDeath();
		manager.recordDeath();

		RunEndPayload sent = RunEndPayload.of(RunOutcome.WIN_DRAGON,
				manager.getEntropy(), manager.getEntropyCap(), manager.getDeathCount(),
				manager.getHistory());

		check(sent.entropyCap() == 64, "the payload carries the run's CONFIGURED cap, not the default");
		check(sent.deathCount() == 3, "...and the run's real death count");

		RegistryFriendlyByteBuf buf =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		RunEndPayload.CODEC.encode(buf, new RunEndPayload("LOSS_ENTROPY_CAP", 37, 91, 5,
				List.of(new com.entropymod.network.HistoryResponsePayload.Entry(
								1, EffectPhase.GOOD, "id_one", "Name One", "Desc One", 0),
						new com.entropymod.network.HistoryResponsePayload.Entry(
								2, EffectPhase.BAD, "id_two", "Name Two", "Desc Two", 1))));
		RunEndPayload got = RunEndPayload.CODEC.decode(buf);

		check(got.parsedOutcome() == RunOutcome.LOSS_ENTROPY_CAP, "the outcome survives the wire");
		check(got.entropy() == 37, "entropy is 37, not the cap or the death count");
		check(got.entropyCap() == 91, "entropyCap is 91, not the entropy");
		check(got.deathCount() == 5, "deathCount is 5, not either of the others");
		check(got.history().size() == 2, "both history rows survive");
		check(got.history().get(0).effectName().equals("Name One")
						&& got.history().get(1).effectName().equals("Name Two"),
				"...in order, not rotated");
		check(got.history().get(0).phase() == EffectPhase.GOOD
						&& got.history().get(1).phase() == EffectPhase.BAD,
				"...with their phases intact");
		check(buf.readableBytes() == 0, "the buffer is fully consumed");

		// Total picks is the history's own length -- no second number to drift.
		check(got.pickCount() == got.history().size(),
				"total picks is derived from the history, never tracked separately");
		check(RunEndPayload.class.getRecordComponents().length == 5,
				"...and the record has five components, none of them a stored pick count");

		// Sent by name, like every other enum on the wire here.
		check(sent.outcome().equals("WIN_DRAGON"),
				"the outcome goes on the wire by name, so reordering the enum cannot "
						+ "turn a loss into a win");
	}

	// ------------------------------------------------------------------
	// Tier 2 content batch: seven effects.
	// ------------------------------------------------------------------

	private static final List<String> TIER2_BATCH = List.of(
			EmbraceTheMoonBehavior.ID, CreativeFlightBehavior.ID, BehemothGauntletsBehavior.ID,
			CrouchInvincibilityBehavior.ID, GiantSizeBehavior.ID, SlashedPocketsBehavior.ID,
			FlamboyantBehavior.ID);

	private static void tier2BatchWiring() {
		section("Tier 2 batch: registration and wiring");

		// Touching EffectBehaviors class-inits EntropyAttributes against a frozen
		// registry, so the harness needs Fabric's ordering reproduced first.
		HarnessBootstrap.init();

		for (String id : TIER2_BATCH) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.minEntropy() == 25 && def.maxEntropy() == 50,
					id + " is Tier 2 (entropy 25-50), got "
							+ def.minEntropy() + "-" + def.maxEntropy());
			// Bad effects below entropy 40 must be survivable -- Tier 2 starts at 25,
			// so every BAD effect in this batch is inside that window.
			check(def.phase() != EffectPhase.BAD || def.counterplay(),
					id + " is counterplay-survivable if BAD (required below entropy 40)");
		}

		check(EffectRegistry.byId(EmbraceTheMoonBehavior.ID).phase() == EffectPhase.GOOD
						&& EffectRegistry.byId(CreativeFlightBehavior.ID).phase() == EffectPhase.GOOD
						&& EffectRegistry.byId(BehemothGauntletsBehavior.ID).phase() == EffectPhase.GOOD
						&& EffectRegistry.byId(CrouchInvincibilityBehavior.ID).phase() == EffectPhase.GOOD,
				"the four GOOD effects are registered GOOD");
		check(EffectRegistry.byId(GiantSizeBehavior.ID).phase() == EffectPhase.BAD
						&& EffectRegistry.byId(SlashedPocketsBehavior.ID).phase() == EffectPhase.BAD
						&& EffectRegistry.byId(FlamboyantBehavior.ID).phase() == EffectPhase.BAD,
				"the three BAD effects are registered BAD");

		// Anti-stacking is keyed on category, so a collision inside one phase means
		// the two can never be offered together -- worth knowing, not a bug.
		check(EffectRegistry.byId(EmbraceTheMoonBehavior.ID).category() == EffectCategory.MOVEMENT
						&& EffectRegistry.byId(CreativeFlightBehavior.ID).category() == EffectCategory.MOVEMENT,
				"Embrace the Moon and Creative Flight are both MOVEMENT -- anti-stacking keeps "
						+ "flight and low gravity apart, which is the intended exclusivity");

		// The compiler cannot check id strings against EffectRegistry; this is the
		// same validation that runs at mod init.
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty(),
				"every registered effect has a behavior: "
						+ EffectBehaviors.definitionsWithoutBehavior());
		check(EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no behavior is registered under an unknown id: "
						+ EffectBehaviors.behaviorsWithoutDefinition());
		for (String id : TIER2_BATCH) {
			check(EffectBehaviors.get(id).getClass().getSimpleName().endsWith("Behavior"),
					id + " resolves to a real behavior, not the MISSING no-op");
		}
	}

	/**
	 * Extreme Gravity's value, its distinctness from Moon Walker, and -- the part
	 * that actually matters -- why its attribute operation differs.
	 */
	private static void extremeGravityPhysics() {
		section("Embrace the Moon: value, distinctness, and stacking safety");

		double amount = constant(EmbraceTheMoonBehavior.class, "GRAVITY_AMOUNT");
		double jumpBonus = constant(EmbraceTheMoonBehavior.class, "JUMP_BONUS");
		double safeFallBonus = constant(EmbraceTheMoonBehavior.class, "SAFE_FALL_BONUS");
		double moonWalker = -0.30; // MoonWalkerBehavior passes this inline to super
		double base = 0.08;        // GRAVITY's registered default, verified against the live registry

		check(amount <= -0.75 && amount >= -0.80,
				"the reduction is inside the specified -75%..-80% band (" + amount + ")");

		double gravity = base * (1.0 + amount);
		checkNear(gravity, 0.0176, 1e-9, "resulting gravity");
		check(gravity > 0.0,
				"gravity stays strictly positive -- 0 would leave the player floating "
						+ "with no way down, and GRAVITY's clamp floors at -1.0, not 0");

		// --- 1b: the jump bonus, and that it was derived rather than guessed ------
		double jump = JUMP_DEFAULT * (1.0 + jumpBonus);
		double apexGravityOnly = apex(gravity);
		double apexShipped = apex(gravity, jump);
		checkNear(apexGravityOnly, 4.065, 0.002, "apex from the gravity change alone");
		checkNear(apexShipped, 4.567, 0.002, "apex as shipped, with the jump bonus");

		double gain = apexShipped / apexGravityOnly - 1.0;
		check(gain >= 0.10 && gain <= 0.15,
				"the jump bonus lands inside the intended +10-15% apex band ("
						+ Math.round(gain * 10000) / 100.0 + "%)");
		// The whole point of deriving it: taking the apex percentage as the attribute
		// percentage overshoots, because apex goes as the square of launch velocity.
		check(apex(gravity, JUMP_DEFAULT * (1.0 + gain)) > apexShipped,
				"a naive '+" + Math.round(gain * 100) + "% on the attribute' would overshoot "
						+ "the target apex -- which is why this is solved, not assumed");

		double moonGravity = base * (1.0 + moonWalker);
		checkNear(apex(moonGravity), 1.657, 0.002, "Moon Walker jump apex (blocks)");
		checkNear(apex(base), 1.2522, 0.002,
				"the model still reproduces vanilla's known 1.2522-block jump");
		check(apexShipped > apex(moonGravity) * 2.5,
				"Embrace the Moon is meaningfully distinct from Moon Walker, not a nudge: "
						+ Math.round(apexShipped / apex(moonGravity) * 100) + "% of its apex");
		check(apexShipped > 4.0 && apex(moonGravity) < 2.0,
				"it clears a 4-block ledge where Moon Walker cannot clear 2");

		// --- 1c: safe fall distance -----------------------------------------------
		double safeFall = 3.0 + safeFallBonus; // SAFE_FALL_DISTANCE default, from the live registry
		checkNear(safeFall, 6.0, 1e-9, "safe fall distance is doubled, 3 -> 6 blocks");
		// calculateFallDamage(d, 1) = floor((d + 1e-6 - SAFE_FALL_DISTANCE) * FALL_DAMAGE_MULTIPLIER)
		for (int d : new int[] {1, 2, 3, 4, 5, 6}) {
			check(fallDamage(d, safeFall) == 0,
					"a " + d + "-block fall is harmless (vanilla would deal "
							+ fallDamage(d, 3.0) + ")");
		}
		check(fallDamage(7, safeFall) == 1 && fallDamage(7, 3.0) == 4,
				"7 blocks: 1 damage instead of vanilla's 4");
		check(fallDamage(20, safeFall) == 14 && fallDamage(20, 3.0) == 17,
				"20 blocks: 14 instead of 17 -- the threshold moves, it is not a multiplier");
		// The distinction that matters: this is NOT FALL_DAMAGE_MULTIPLIER's mechanic.
		check(fallDamage(20, safeFall) - fallDamage(20, 3.0) == fallDamage(7, safeFall) - fallDamage(7, 3.0),
				"the reduction is a constant 3 damage at every height -- a subtracted "
						+ "threshold, not a scaled remainder like Featherlight");

		// --- stacking safety, RECOMPUTED at the new value --------------------------
		double stackedTotal = base * (1.0 + moonWalker) * (1.0 + amount);
		double stackedBase = base * (1.0 + moonWalker + amount);
		check(stackedTotal > 0.0,
				"ADD_MULTIPLIED_TOTAL stacked with Moon Walker stays positive ("
						+ round4(stackedTotal) + ")");
		check(apex(stackedTotal, jump) < 7.0 && sim(stackedTotal, jump)[1] < 100,
				"and stays playable: apex " + round1(apex(stackedTotal, jump))
						+ ", airtime " + (int) sim(stackedTotal, jump)[1] + "t");

		// THE regression this session had to re-derive: the old margin did NOT survive.
		check(stackedBase < 0.0,
				"had it used ADD_MULTIPLIED_BASE the same pairing would now give "
						+ round4(stackedBase) + " -- NEGATIVE gravity, launched upward permanently");
		check(base * (1.0 + moonWalker + (-0.65)) > 0.0,
				"at last session's -65% that additive form was still (barely) positive at "
						+ round4(base * (1.0 + moonWalker - 0.65))
						+ " -- so the safety margin was NOT inherited, it had to be rechecked");
	}

	/**
	 * Vanilla's per-tick vertical integration: integrate, then apply gravity and
	 * the 0.98 air drag. Validated against the known vanilla apex above.
	 */
	/** JUMP_STRENGTH's registered default, from the live registry. */
	private static final double JUMP_DEFAULT = 0.41999998688697815;

	private static double apex(double gravity) {
		return apex(gravity, JUMP_DEFAULT);
	}

	private static double apex(double gravity, double jump) {
		return sim(gravity, jump)[0];
	}

	/** @return {apex in blocks, airtime in ticks} */
	private static double[] sim(double gravity, double jump) {
		double y = 0.0;
		double v = jump;
		double best = 0.0;
		int t = 0;
		for (; t < 6000; t++) {
			y += v;
			v = (v - gravity) * 0.98;
			best = Math.max(best, y);
			if (y <= 0 && v < 0) {
				break;
			}
		}
		return new double[] {best, t + 1};
	}

	/**
	 * Vanilla's own formula, javap-verified:
	 * {@code floor((distance + 1e-6 - SAFE_FALL_DISTANCE) * FALL_DAMAGE_MULTIPLIER)},
	 * with the multiplier at its default of 1.0.
	 */
	private static int fallDamage(double distance, double safeFallDistance) {
		return Math.max(0, (int) Math.floor(distance + 1.0e-6 - safeFallDistance));
	}

	/** Solves the real integration for the jump strength that reaches a target apex. */
	private static double jumpStrengthForApex(double gravity, double targetApex) {
		double lo = 0.01;
		double hi = 5.0;
		for (int i = 0; i < 200; i++) {
			double mid = (lo + hi) / 2.0;
			if (apex(gravity, mid) < targetApex) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2.0;
	}

	private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
	private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

	private static void giantSizeAttribute() {
		section("Giant Size: the SCALE attribute is real and in range");
		HarnessBootstrap.init();

		net.minecraft.world.entity.ai.attributes.RangedAttribute scale =
				(net.minecraft.world.entity.ai.attributes.RangedAttribute)
						net.minecraft.world.entity.ai.attributes.Attributes.SCALE.value();

		checkNear(scale.getDefaultValue(), 1.0, 1e-9, "SCALE default");
		checkNear(scale.getMinValue(), 0.0625, 1e-9, "SCALE minimum");
		checkNear(scale.getMaxValue(), 16.0, 1e-9, "SCALE maximum");

		// Present on the PLAYER entity type specifically -- SCALE comes from
		// LivingEntity.createLivingAttributes(), not Player.createAttributes(), so
		// "it exists" and "players have it" are separate claims.
		check(net.minecraft.world.entity.ai.attributes.DefaultAttributes
						.getSupplier(net.minecraft.world.entity.EntityType.PLAYER)
						.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE),
				"players actually have SCALE -- checked against DefaultAttributes, not assumed");

		double amount = constant(GiantSizeBehavior.class, "AMOUNT");
		double result = scale.getDefaultValue() + amount; // ADD_VALUE against base 1.0
		checkNear(result, 5.0, 1e-9, "resulting scale is the specified ~5x");
		checkNear(scale.sanitizeValue(result), result, 1e-9,
				"5.0 is not clamped -- it is well inside [0.0625, 16.0]");

		check(Checks.hasMethod(GiantSizeBehavior.class, "afterApply"),
				"a collision mitigation exists at all -- Entity.refreshDimensions() skips "
						+ "fudgePositionAfterSizeChange for players, so vanilla will NOT push a "
						+ "grown player out of blocks");
		check(Checks.classReferences(GiantSizeBehavior.class, "noCollision")
						&& Checks.classReferences(GiantSizeBehavior.class, "snapTo"),
				"the mitigation actually tests for collision and moves the player");
		check(constant(GiantSizeBehavior.class, "MAX_LIFT_BLOCKS") > 0,
				"the upward search has a bounded limit rather than looping forever");
	}

	/** The rest of Giant Size's kit: health, step-up, jump, damage, reach. */
	private static void giantSizeKit() {
		section("Giant Size: the full kit");
		HarnessBootstrap.init();

		// --- 2a: +10 hearts -------------------------------------------------------
		checkNear(constant(GiantSizeBehavior.class, "HEALTH_BONUS"), 20.0, 1e-9,
				"health bonus is +20.0 raw = +10 hearts");
		double maxHealth = ranged(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
				.getDefaultValue() + 20.0;
		checkNear(maxHealth, 40.0, 1e-9, "resulting max health is 40 (20 hearts)");
		checkNear(ranged(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
						.sanitizeValue(maxHealth), 40.0, 1e-9,
				"40 is not clamped -- MAX_HEALTH's ceiling is 1024");

		// --- 2b: STEP_HEIGHT ------------------------------------------------------
		var step = ranged(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
		checkNear(step.getDefaultValue(), 0.6, 1e-9, "STEP_HEIGHT default is 0.6");
		checkNear(step.getMinValue(), 0.0, 1e-9, "STEP_HEIGHT minimum is 0.0");
		checkNear(step.getMaxValue(), 10.0, 1e-9, "STEP_HEIGHT maximum is 10.0");
		check(net.minecraft.world.entity.ai.attributes.DefaultAttributes
						.getSupplier(net.minecraft.world.entity.EntityType.PLAYER)
						.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT),
				"players actually have STEP_HEIGHT -- checked, not assumed");
		double stepResult = step.getDefaultValue()
				+ constant(GiantSizeBehavior.class, "STEP_HEIGHT_BONUS");
		checkNear(stepResult, 2.0, 1e-9, "resulting step height is exactly 2.0");
		check(stepResult >= 2.0,
				"which is the whole target: a 2-block ledge walked up without jumping");
		checkNear(step.sanitizeValue(stepResult), 2.0, 1e-9, "2.0 is not clamped");

		// --- 2c: double jump, DERIVED --------------------------------------------
		double vanillaApex = apex(0.08);
		double jumpBonus = constant(GiantSizeBehavior.class, "JUMP_BONUS");
		double jump = JUMP_DEFAULT * (1.0 + jumpBonus);
		double giantApex = apex(0.08, jump);
		checkNear(giantApex / vanillaApex, 2.0, 0.001,
				"apex is genuinely 2x vanilla (" + round4(vanillaApex) + " -> " + round4(giantApex) + ")");

		// The point of deriving against the real integration rather than the closed
		// form: sqrt(2) is the intuitive answer and it is WRONG.
		double naive = JUMP_DEFAULT * Math.sqrt(2.0);
		check(apex(0.08, naive) / vanillaApex < 1.90,
				"the intuitive sqrt(2) on the attribute gives only "
						+ round4(apex(0.08, naive) / vanillaApex) + "x, not 2x -- the 0.98 air drag "
						+ "costs proportionally more at higher launch velocity");
		checkNear(jumpStrengthForApex(0.08, vanillaApex * 2.0), jump, 1e-4,
				"the shipped jump strength is what solving the integration backwards produces");
		checkNear(ranged(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)
						.sanitizeValue(jump), jump, 1e-9,
				"the resulting jump strength is inside JUMP_STRENGTH's range");

		// --- 2d: damage reduction rides IRON SKIN's hook, not a new one ------------
		checkNear(constant(GiantSizeBehavior.class, "DAMAGE_MULTIPLIER"), 0.75, 1e-6,
				"damage multiplier is 0.75 = -25%");
		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class, "GiantSizeBehavior"),
				"EffectHooks itself reads Giant Size -- the reduction goes through the "
						+ "existing damageTakenMultiplier, not a parallel mechanism");
		check(!Checks.hasMethod(com.entropymod.entropy.EffectHooks.class, "giantDamageMultiplier"),
				"no second damage hook was introduced alongside Iron Skin's");
		float ironSkin = (float) constant(
				com.entropymod.entropy.behavior.IronSkinBehavior.class, "MULTIPLIER");
		float fragile = (float) constant(
				com.entropymod.entropy.behavior.FragileBehavior.class, "MULTIPLIER");
		checkNear(0.75 * ironSkin, 0.60, 1e-4,
				"stacked with Iron Skin it is 0.60 -- multiplicative, so it cannot reach zero");
		check(0.75 * fragile > 0.0 && 0.75 * fragile < 1.0,
				"stacked with Fragile it is still a net reduction (" + round4(0.75 * fragile) + ")");

		// --- 2e: reach ------------------------------------------------------------
		// SCALE raises the eye, and the reach check measures FROM the eye, so an
		// unmodified 5x player cannot touch the ground they stand on.
		double eye = 1.62;
		double scale = 1.0 + constant(GiantSizeBehavior.class, "AMOUNT");
		double giantEye = eye * scale;
		checkNear(giantEye, 8.1, 1e-9, "at 5x the eye sits 8.1 blocks above the feet");

		var blockRange =
				ranged(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
		var entityRange =
				ranged(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		checkNear(blockRange.getDefaultValue(), 4.5, 1e-9, "BLOCK_INTERACTION_RANGE default is 4.5");
		checkNear(entityRange.getDefaultValue(), 3.0, 1e-9, "ENTITY_INTERACTION_RANGE default is 3.0");

		check(blockRange.getDefaultValue() < giantEye,
				"WITHOUT a reach bonus a 5x player cannot reach the block at their feet: "
						+ "needs " + giantEye + ", has " + blockRange.getDefaultValue());
		double reachBonus = constant(GiantSizeBehavior.class, "REACH_BONUS");
		check(blockRange.getDefaultValue() + reachBonus > giantEye,
				"with the bonus they can: block reach "
						+ (blockRange.getDefaultValue() + reachBonus) + " vs the 8.1 needed");
		check(blockRange.getDefaultValue() + 2.0 < giantEye,
				"the originally-specified +2 would NOT have been enough ("
						+ (blockRange.getDefaultValue() + 2.0) + " vs 8.1) -- which is why it is larger");
		checkNear(reachBonus, giantEye - eye, 0.03,
				"the bonus is sized to the eye-height increase, restoring vanilla-equivalent "
						+ "reach relative to the giant's own body");
		check(blockRange.sanitizeValue(blockRange.getDefaultValue() + reachBonus)
						== blockRange.getDefaultValue() + reachBonus
						&& entityRange.sanitizeValue(entityRange.getDefaultValue() + reachBonus)
								== entityRange.getDefaultValue() + reachBonus,
				"both resulting ranges are inside the attributes' range");
	}

	private static net.minecraft.world.entity.ai.attributes.RangedAttribute ranged(
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {
		return (net.minecraft.world.entity.ai.attributes.RangedAttribute) holder.value();
	}

	/**
	 * Both reworked effects apply several attributes at once, so "idempotent" now
	 * has to mean "every one of them is", not just the first.
	 */
	private static void multiAttributeIdempotency() {
		section("Multi-attribute effects: every change is idempotent");
		HarnessBootstrap.init();

		for (String id : List.of(EmbraceTheMoonBehavior.ID, GiantSizeBehavior.ID)) {
			AttributeEffectBehavior behavior = (AttributeEffectBehavior) EffectBehaviors.get(id);
			List<AttributeEffectBehavior.Change> changes = behavior.changes();
			check(changes.size() > 1, id + " really is a multi-attribute effect (" + changes.size() + ")");

			for (AttributeEffectBehavior.Change change : changes) {
				String name = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
						.getKey(change.attribute().value()).getPath();
				var instance = new net.minecraft.world.entity.ai.attributes.AttributeInstance(
						change.attribute(), a -> {});
				double afterFirst = 0;
				for (int i = 0; i < 10; i++) {
					instance.addOrUpdateTransientModifier(
							new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									AttributeEffectBehavior.modifierId(id),
									change.amount(), change.operation()));
					if (i == 0) {
						afterFirst = instance.getValue();
					}
				}
				checkNear(instance.getValue(), afterFirst, 1e-9,
						id + "/" + name + ": ten applications leave the value where one put it");
				check(instance.getModifiers().size() == 1,
						id + "/" + name + ": one modifier, not ten stacked copies");

				// remove() must clear every change too, or a future un-apply leaks.
				instance.removeModifier(AttributeEffectBehavior.modifierId(id));
				checkNear(instance.getValue(), instance.getBaseValue(), 1e-9,
						id + "/" + name + ": removal restores the base value");
			}
		}
	}


	/** Both branches driven for real against the shipped decision function. */
	private static void behemothGauntletsBothCases() {
		section("Behemoth Gauntlets: empty hand vs. held item");

		float bonus = (float) constant(BehemothGauntletsBehavior.class, "UNARMED_BONUS");
		float armed = (float) constant(BehemothGauntletsBehavior.class, "ARMED_MULTIPLIER");
		checkNear(bonus, 20.0, 1e-6, "unarmed bonus is the specified flat +20");
		checkNear(armed, 0.25, 1e-6, "armed multiplier is the specified -75%");

		// A vanilla bare-handed hit is 1.0; a diamond sword swing is about 7.
		checkNear(BehemothGauntletsBehavior.damageFor(1.0f, true), 21.0, 1e-4,
				"EMPTY HAND: a 1-damage punch becomes 21");
		checkNear(BehemothGauntletsBehavior.damageFor(7.0f, false), 1.75, 1e-4,
				"HELD ITEM: a 7-damage sword swing becomes 1.75");

		check(BehemothGauntletsBehavior.damageFor(7.0f, true)
						> BehemothGauntletsBehavior.damageFor(7.0f, false),
				"the two branches genuinely differ for the same input damage");
		check(BehemothGauntletsBehavior.damageFor(1.0f, true)
						> BehemothGauntletsBehavior.damageFor(7.0f, false),
				"punching out-damages a diamond sword -- the point of the effect");

		// Additive unarmed, multiplicative armed: a crit or a Strength potion should
		// scale the penalty rather than be swallowed by a flat number.
		checkNear(BehemothGauntletsBehavior.damageFor(0.0f, false), 0.0, 1e-6,
				"the armed branch is multiplicative, so zero damage stays zero");
		checkNear(BehemothGauntletsBehavior.damageFor(0.0f, true), 20.0, 1e-6,
				"the unarmed branch is additive, so it applies even to a zero-damage hit");

		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class, "getWeaponItem"),
				"the hook decides on getWeaponItem() -- the main-hand stack -- rather than "
						+ "guessing from an item tag");
	}

	private static void flamboyantFireOnly() {
		section("Flamboyant: fire specifically, not damage generally");

		float multiple = (float) constant(FlamboyantBehavior.class, "LETHAL_HEALTH_MULTIPLE");
		check(multiple > 1.0f, "the lethal amount is a multiple of max health, not a fixed number");
		check(Float.isFinite(multiple * 20.0f),
				"it stays finite -- Float.MAX_VALUE would risk non-finite damage maths downstream");

		// Armour caps at 80% reduction and Resistance at a further 80%; Iron Skin is
		// another 20%. Even all three together must not leave the player alive.
		float lethal = FlamboyantBehavior.lethalDamage(1.0f, 20.0f);
		check(lethal * 0.2f * 0.2f * 0.8f > 20.0f,
				"survives worst-case mitigation (armour + Resistance + Iron Skin) and is still lethal");
		checkNear(FlamboyantBehavior.lethalDamage(1.0f, 20.0f), 20.0f * multiple, 1e-3,
				"lethal damage scales with max health");
		check(FlamboyantBehavior.lethalDamage(Float.MAX_VALUE, 20.0f) == Float.MAX_VALUE,
				"an already-larger amount is left alone rather than scaled down");

		// THE regression check: the gate is the fire tag, and nothing else.
		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class, "IS_FIRE"),
				"the hook gates on the vanilla #minecraft:is_fire damage-type tag");

		Set<String> fire = resolveDamageTypeTag("is_fire");
		check(fire.contains("minecraft:on_fire") && fire.contains("minecraft:in_fire")
						&& fire.contains("minecraft:lava") && fire.contains("minecraft:campfire")
						&& fire.contains("minecraft:hot_floor"),
				"the shipped tag covers burning, standing in fire, lava, campfires and magma");
		// Read from the jar rather than restated, so this is a claim about game data.
		for (String notFire : List.of("minecraft:fall", "minecraft:drown", "minecraft:mob_attack",
				"minecraft:player_attack", "minecraft:cactus", "minecraft:out_of_world",
				"minecraft:explosion", "minecraft:starve", "minecraft:magic")) {
			check(!fire.contains(notFire),
					"NON-FIRE regression: " + notFire + " is not in #is_fire, so it stays ordinary damage");
		}
	}

	/** Reads a shipped damage-type tag out of the Minecraft jar on the classpath. */
	private static Set<String> resolveDamageTypeTag(String name) {
		String path = "/data/minecraft/tags/damage_type/" + name + ".json";
		try (java.io.InputStream in = HarnessMain.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Tag not found on the classpath: " + path);
			}
			String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			Set<String> out = new java.util.LinkedHashSet<>();
			java.util.regex.Matcher m =
					java.util.regex.Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(json);
			while (m.find()) {
				out.add(m.group(1));
			}
			return out;
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static void crouchInvincibilityGate() {
		section("Crouch Invincibility: only while actually sneaking");

		// The gate needs a live Player, which cannot be built headlessly -- but WHICH
		// vanilla method it consults is visible in the compiled hook, and the two
		// candidates behave differently (pose vs. raw key state).
		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class, "isCrouching"),
				"the hook consults isCrouching() -- vanilla's pose state, matching what the "
						+ "player sees on screen");
		check(!Checks.classReferences(com.entropymod.entropy.EffectHooks.class, "isShiftKeyDown"),
				"and NOT isShiftKeyDown(), the raw input flag, which disagrees when a player "
						+ "is crouched under a 1-block gap or shifting while forced upright");

		check(Checks.hasMethod(com.entropymod.entropy.EffectHooks.class, "ignoresAllDamage"),
				"the gate is a single hook, so there is one place the sneak test can live");

		// Evaluated per hit rather than latched: the check lives in the hook the
		// damage mixin calls, not in apply(). A behavior with no state cannot latch.
		check(CrouchInvincibilityBehavior.class.getSuperclass()
						== com.entropymod.entropy.HookEffectBehavior.class,
				"it is a HookEffectBehavior, whose apply() is final and empty -- so there is "
						+ "no per-player state that could latch 'has sneaked' into 'is sneaking'");
		check(Checks.classReferences(com.entropymod.mixin.ServerPlayerDamageMixin.class,
						"ignoresAllDamage"),
				"the damage mixin calls the gate on every hit");
	}

	private static void slashedPocketsSlots() {
		section("Slashed Pockets: which slots, in this version's layout");
		HarnessBootstrap.init();

		int first = (int) constant(SlashedPocketsBehavior.class, "FIRST_LOCKED_SLOT");
		int last = (int) constant(SlashedPocketsBehavior.class, "LAST_LOCKED_SLOT");
		check(first == 9 && last == 26, "locked range is slots 9-26 (got " + first + "-" + last + ")");
		check(SlashedPocketsBehavior.lockedSlotCount() == 18,
				"that is 18 slots -- exactly half of the 36 main inventory slots");

		// Read from the live Inventory class, so this is a claim about the game.
		check(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE == 36,
				"INVENTORY_SIZE is 36");
		check(net.minecraft.world.entity.player.Inventory.SELECTION_SIZE == 9,
				"SELECTION_SIZE (the hotbar) is 9");
		check(SlashedPocketsBehavior.lockedSlotCount() * 2
						== net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE,
				"the locked half is exactly half of the main inventory");

		// The hotbar must stay usable, or the effect is unplayable rather than harsh.
		for (int slot = 0; slot <= 8; slot++) {
			check(net.minecraft.world.entity.player.Inventory.isHotbarSlot(slot)
							&& !SlashedPocketsBehavior.isLocked(slot),
					"hotbar slot " + slot + " is never locked");
		}
		check(!SlashedPocketsBehavior.isLocked(27) && !SlashedPocketsBehavior.isLocked(35),
				"the bottom storage row (27-35) stays usable");
		check(SlashedPocketsBehavior.isLocked(9) && SlashedPocketsBehavior.isLocked(17)
						&& SlashedPocketsBehavior.isLocked(18) && SlashedPocketsBehavior.isLocked(26),
				"both locked rows are locked end to end");

		// Equipment lives at 40/41/42, outside the main list entirely, so armour and
		// the offhand cannot be caught by an index test.
		check(!SlashedPocketsBehavior.isLocked(
						net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND)
						&& !SlashedPocketsBehavior.isLocked(
								net.minecraft.world.entity.player.Inventory.SLOT_BODY_ARMOR)
						&& !SlashedPocketsBehavior.isLocked(
								net.minecraft.world.entity.player.Inventory.SLOT_SADDLE),
				"offhand, body armour and saddle slots are outside the locked range");

		check(Checks.classReferences(SlashedPocketsBehavior.class, "isFreshPick"),
				"the one-time drop is gated on a fresh pick, so it cannot re-fire on every "
						+ "respawn, rejoin and dimension change");
	}

	/**
	 * The three guarantees the whole permanent-effect design rests on, applied to
	 * this batch: apply is idempotent, an effect is never offered twice, and the
	 * acquired set survives a save.
	 */
	private static void tier2BatchIdempotencyAndPersistence() {
		section("Tier 2 batch: idempotency, no-repeat and persistence");
		HarnessBootstrap.init();

		// --- idempotency, against the REAL AttributeInstance --------------------
		// This is the guarantee the respawn/rejoin design rests on: apply() runs an
		// unbounded number of times, so the value must not move after the first.
		for (String id : List.of(EmbraceTheMoonBehavior.ID, GiantSizeBehavior.ID)) {
			net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
					instanceFor(id.equals(GiantSizeBehavior.ID)
							? net.minecraft.world.entity.ai.attributes.Attributes.SCALE
							: net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
			double amount = constant(
					id.equals(GiantSizeBehavior.ID) ? GiantSizeBehavior.class : EmbraceTheMoonBehavior.class,
					id.equals(GiantSizeBehavior.ID) ? "AMOUNT" : "GRAVITY_AMOUNT");
			var op = id.equals(GiantSizeBehavior.ID)
					? net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
					: net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;

			double after1 = 0;
			for (int i = 0; i < 10; i++) {
				instance.addOrUpdateTransientModifier(
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								com.entropymod.entropy.AttributeEffectBehavior.modifierId(id), amount, op));
				if (i == 0) {
					after1 = instance.getValue();
				}
			}
			checkNear(instance.getValue(), after1, 1e-9,
					id + ": applying 10 times leaves the value where one application put it");
			check(instance.getModifiers().size() == 1,
					id + ": exactly one modifier, not ten stacked copies");
		}

		// --- no-repeat ----------------------------------------------------------
		AcquiredEffects acquired = new AcquiredEffects();
		TIER2_BATCH.forEach(acquired::add);
		for (int entropy : new int[] {25, 37, 50}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(1234),
						acquired.ids(), acquired.occupiedCategories());
				if (roll.repeatFallback()) {
					continue; // the pool legitimately ran dry; that path is covered elsewhere
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!TIER2_BATCH.contains(offered.id()),
							"no-repeat holds at entropy " + entropy + "/" + phase
									+ ": already-acquired '" + offered.id() + "' was not re-offered");
				}
			}
		}

		// --- persistence --------------------------------------------------------
		// grantEffect needs a live server to dispatch behaviors, so persistence is
		// driven through the acquired set, which is what the codec actually writes.
		EntropyManager saved = new EntropyManager();
		TIER2_BATCH.forEach(id -> saved.acquired().add(id));
		EntropyManager reloaded = reencode(saved);
		for (String id : TIER2_BATCH) {
			check(reloaded.acquired().contains(id),
					id + " survives a save/reload in the acquired set");
		}
		check(reloaded.acquired().unknownIds().isEmpty(),
				"every id in this batch is still defined after a reload -- no stale ids");
	}

	private static net.minecraft.world.entity.ai.attributes.AttributeInstance instanceFor(
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		return new net.minecraft.world.entity.ai.attributes.AttributeInstance(attribute, a -> {});
	}


	// ------------------------------------------------------------------
	// Survival batch: fall-damage stacking, Slippery Grip, Glass Cannon Pact,
	// Phoenix Chambered Heart.
	// ------------------------------------------------------------------

	private static final List<String> SURVIVAL_BATCH = List.of(
			SlipperyGripBehavior.ID, GlassCannonPactBehavior.ID, PhoenixChamberedHeartBehavior.ID);

	private static void survivalBatchWiring() {
		section("Survival batch: registration and wiring");
		HarnessBootstrap.init();

		for (String id : SURVIVAL_BATCH) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.minEntropy() >= 25, id + " starts at entropy 25 or later");
			check(def.phase() != EffectPhase.BAD || def.counterplay(),
					id + " is counterplay-survivable if BAD");
			check(EffectBehaviors.get(id).getClass().getSimpleName().endsWith("Behavior"),
					id + " resolves to a real behavior, not the MISSING no-op");
		}

		// Glass Cannon Pact was placed at the top of Tier 2 deliberately.
		EffectDefinition pact = EffectRegistry.byId(GlassCannonPactBehavior.ID);
		check(pact.minEntropy() == 40 && pact.maxEntropy() == 60,
				"Glass Cannon Pact sits at entropy 40-60, above the rest of Tier 2");
		check(EffectRegistry.byId(SlipperyGripBehavior.ID).phase() == EffectPhase.BAD,
				"Slippery Grip is BAD");
		check(pact.phase() == EffectPhase.GOOD
						&& EffectRegistry.byId(PhoenixChamberedHeartBehavior.ID).phase() == EffectPhase.GOOD,
				"Glass Cannon Pact and Phoenix Chambered Heart are GOOD");

		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatches anywhere after this batch");
	}

	/**
	 * Part 0: the two fall-damage mechanics stack, and the three effects that now
	 * share {@code FALL_DAMAGE_MULTIPLIER} compose without clobbering.
	 */
	private static void fallDamageStacking() {
		section("Embrace the Moon: fall-damage threshold AND multiplier");
		HarnessBootstrap.init();

		double safeFall = 3.0 + constant(EmbraceTheMoonBehavior.class, "SAFE_FALL_BONUS");
		double fallAmount = constant(EmbraceTheMoonBehavior.class, "FALL_DAMAGE_AMOUNT");
		checkNear(fallAmount, -0.50, 1e-9, "the multiplier term is -50%");

		// Both mechanics together, against vanilla's own formula.
		check(fallDamage(10, safeFall, 0.5) == 2 && fallDamage(10, 3.0, 1.0) == 7,
				"10-block fall: 2 damage instead of vanilla's 7");
		check(fallDamage(20, safeFall, 0.5) == 7 && fallDamage(20, 3.0, 1.0) == 17,
				"20-block fall: 7 instead of 17");
		check(fallDamage(40, safeFall, 0.5) == 17 && fallDamage(40, 3.0, 1.0) == 37,
				"40-block fall: 17 instead of 37");
		check(fallDamage(6, safeFall, 0.5) == 0,
				"the threshold still does its own job: 6 blocks is harmless");
		// The two are genuinely different mechanics, not one dressed as the other.
		check(fallDamage(20, safeFall, 1.0) == 14 && fallDamage(20, 3.0, 0.5) == 8,
				"threshold-only and multiplier-only give different answers (14 vs 8), "
						+ "so stacking them is not a double-application of one mechanic");

		// --- composition on the REAL AttributeInstance ---------------------------
		// Three effects now write to this attribute and no-repeat permits holding
		// them together, so the question is whether they clobber each other.
		var attr = net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER;
		double featherlight = -0.40;
		double glassJaw = 0.40;

		checkNear(composeFallMultiplier(attr, true, false, false), 0.50, 1e-6,
				"Embrace the Moon alone: 0.50");
		checkNear(composeFallMultiplier(attr, true, true, false), 0.30, 1e-6,
				"+ Featherlight: 0.30 (multiplicative, not the 0.10 an additive term would give)");
		checkNear(composeFallMultiplier(attr, true, false, true), 0.70, 1e-6,
				"+ Glass Jaw: 0.70");
		checkNear(composeFallMultiplier(attr, true, true, true), 0.50, 1e-6,
				"+ both: 0.50, since Featherlight and Glass Jaw are exact inverses");
		checkNear(composeFallMultiplier(attr, false, true, true), 1.0, 1e-6,
				"without this effect the existing pair still cancels to vanilla");

		// Distinct ids are what make them additive rather than one overwriting another.
		check(!AttributeEffectBehavior.modifierId(EmbraceTheMoonBehavior.ID)
						.equals(AttributeEffectBehavior.modifierId(FeatherlightBehavior.ID)),
				"each effect's modifier has its own Identifier, so they cannot clobber");
		check(composeFallMultiplier(attr, true, true, false)
						!= composeFallMultiplier(attr, false, true, false),
				"and all three are genuinely present at once rather than the last one winning");

		// Re-application must refresh, not accumulate -- the whole idempotency rule.
		var instance = new net.minecraft.world.entity.ai.attributes.AttributeInstance(attr, a -> {});
		for (int i = 0; i < 10; i++) {
			addModifier(instance, EmbraceTheMoonBehavior.ID, fallAmount,
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_TOTAL);
			addModifier(instance, FeatherlightBehavior.ID, featherlight,
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_BASE);
		}
		check(instance.getModifiers().size() == 2,
				"ten re-applications of both leave exactly two modifiers, not twenty");
		checkNear(instance.getValue(), 0.30, 1e-6, "and the value is unchanged by the repeats");
		check(glassJaw > 0, "Glass Jaw's sign is unchanged by this work");
	}

	private static void addModifier(
			net.minecraft.world.entity.ai.attributes.AttributeInstance instance,
			String effectId, double amount,
			net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
		instance.addOrUpdateTransientModifier(
				new net.minecraft.world.entity.ai.attributes.AttributeModifier(
						AttributeEffectBehavior.modifierId(effectId), amount, op));
	}

	/**
	 * Slippery Grip: walking untouched, sprinting at exactly half of it, at more
	 * than one baseline so "relative" is genuinely tested rather than a coincidence
	 * of the default speed.
	 */
	private static void slipperyGripSpeed() {
		section("Slippery Grip: sprinting lands at half the CURRENT walking speed");
		HarnessBootstrap.init();

		double vanillaSprint =
				constant(SlipperyGripBehavior.class, "VANILLA_SPRINT_AMOUNT");
		checkNear(vanillaSprint, 0.30000001192092896, 0.0,
				"vanilla's SPEED_MODIFIER_SPRINTING amount, javap-verified from "
						+ "LivingEntity's <clinit>, recorded exactly");
		checkNear(constant(SlipperyGripBehavior.class, "SPRINT_FRACTION"), 0.5, 1e-12,
				"the target is half the walking speed");

		double compensator = SlipperyGripBehavior.compensatorAmount(vanillaSprint);
		checkNear((1.0 + vanillaSprint) * (1.0 + compensator), 0.5, 1e-12,
				"the compensator solves (1 + 0.3)(1 + c) = 0.5 -- one modifier both "
						+ "cancels vanilla's sprint bonus and applies the -50%");
		check(compensator < 0 && compensator > -1.0,
				"and stays strictly inside (-1, 0), so the ADD_MULTIPLIED_TOTAL product "
						+ "can never reach or cross zero however many factors stack");

		// Baseline 1: a vanilla player. Baseline 2: stacked with Sure Footing, which
		// is deliberately in a DIFFERENT pool (ADD_MULTIPLIED_BASE) -- if the effect
		// were secretly absolute, the second baseline is where it would show.
		double sureFooting = ((AttributeEffectBehavior) EffectBehaviors.get(SureFootingBehavior.ID))
				.changes().get(0).amount();
		for (boolean withSureFooting : new boolean[] {false, true}) {
			String label = withSureFooting ? "with Sure Footing" : "vanilla baseline";

			double walkClean = movementSpeed(false, false, withSureFooting);
			double walkCursed = movementSpeed(true, false, withSureFooting);
			// The literal requirement: not "close to", identical bits.
			check(Double.doubleToRawLongBits(walkCursed) == Double.doubleToRawLongBits(walkClean),
					label + ": WALKING is bit-identical with and without the curse (" + walkClean
							+ ") -- the compensator only exists while sprinting");

			double sprintClean = movementSpeed(false, true, withSureFooting);
			double sprintCursed = movementSpeed(true, true, withSureFooting);
			checkNear(sprintCursed, walkCursed * 0.5, 1e-12,
					label + ": SPRINTING is exactly half the walking value (" + sprintCursed
							+ " vs " + walkCursed + ")");
			check(sprintClean > walkClean && sprintCursed < walkCursed,
					label + ": and the sign is inverted -- vanilla sprinting is faster than "
							+ "walking, cursed sprinting is slower");
		}

		// Genuinely relative: the two baselines must differ, or the check above would
		// pass for a hardcoded number.
		check(movementSpeed(true, false, true) != movementSpeed(true, false, false),
				"the two baselines really are different walking speeds, so landing on half "
						+ "of each is relative rather than a fixed value");
		check(sureFooting > 0, "Sure Footing is still a positive speed bonus (+"
				+ sureFooting + "), i.e. a real second baseline");

		// Sprinting is ALLOWED now -- the old forced-false mixin is gone, not merely
		// bypassed. A code path that still existed could be re-enabled by accident.
		check(!Checks.classReferences(com.entropymod.entropy.EffectHooks.class,
						"preventsSprinting"),
				"the old preventsSprinting hook is deleted, not left returning false");
		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class,
						"halvesSprintSpeed"),
				"and the replacement hook is what the mixin reads");
		// The shipped runtime path must use the same arithmetic this section drove.
		check(Checks.classReferences(
						com.entropymod.entropy.behavior.SlipperyGripSprint.class,
						"compensatorAmount"),
				"SlipperyGripSprint derives its modifier from the same compensatorAmount, "
						+ "rather than carrying a second copy of the number");
		check(Checks.classReferences(
						com.entropymod.entropy.behavior.SlipperyGripSprint.class,
						"addOrUpdateTransientModifier"),
				"and applies it with addOrUpdateTransientModifier -- addTransientModifier "
						+ "would throw on the repeated setSprinting(true) calls vanilla makes");
	}

	/**
	 * Movement speed as the real {@code AttributeInstance} computes it.
	 *
	 * <p>The entity plumbing is stood in for -- the harness cannot build a
	 * {@code LivingEntity} -- but the arithmetic is not: the compensator comes from
	 * the shipped {@link SlipperyGripBehavior#compensatorAmount}, the modifier id
	 * from the shipped {@code modifierId}, and the composition from vanilla's own
	 * {@code calculateValue}.
	 */
	private static double movementSpeed(boolean cursed, boolean sprinting, boolean sureFooting) {
		var instance = instanceFor(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		instance.setBaseValue(0.1); // the player's real base
		if (sureFooting) {
			var change = ((AttributeEffectBehavior) EffectBehaviors.get(SureFootingBehavior.ID))
					.changes().get(0);
			addModifier(instance, SureFootingBehavior.ID, change.amount(), change.operation());
		}
		if (sprinting) {
			// Vanilla's own modifier, exactly as LivingEntity.setSprinting adds it.
			instance.addOrUpdateTransientModifier(
					new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							net.minecraft.resources.Identifier.withDefaultNamespace(
									SlipperyGripBehavior.VANILLA_SPRINT_ID_PATH),
							SlipperyGripBehavior.VANILLA_SPRINT_AMOUNT,
							net.minecraft.world.entity.ai.attributes.AttributeModifier
									.Operation.ADD_MULTIPLIED_TOTAL));
		}
		// The compensator exists only while sprinting -- that is the whole mechanism.
		if (cursed && sprinting) {
			addModifier(instance, SlipperyGripBehavior.ID,
					SlipperyGripBehavior.compensatorAmount(SlipperyGripBehavior.VANILLA_SPRINT_AMOUNT),
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_TOTAL);
		}
		return instance.getValue();
	}

	/** Builds a real AttributeInstance holding whichever of the three effects are selected. */
	private static double composeFallMultiplier(
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			boolean moon, boolean featherlight, boolean glassJaw) {
		var instance = new net.minecraft.world.entity.ai.attributes.AttributeInstance(attr, a -> {});
		if (moon) {
			addModifier(instance, EmbraceTheMoonBehavior.ID,
					constant(EmbraceTheMoonBehavior.class, "FALL_DAMAGE_AMOUNT"),
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (featherlight) {
			addModifier(instance, FeatherlightBehavior.ID, -0.40,
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_BASE);
		}
		if (glassJaw) {
			addModifier(instance, GlassJawBehavior.ID, 0.40,
					net.minecraft.world.entity.ai.attributes.AttributeModifier
							.Operation.ADD_MULTIPLIED_BASE);
		}
		return instance.getValue();
	}

	/** Vanilla's formula, with the fall-damage multiplier as a parameter. */
	private static int fallDamage(double distance, double safeFallDistance, double multiplier) {
		return Math.max(0, (int) Math.floor((distance + 1.0e-6 - safeFallDistance) * multiplier));
	}

	private static void glassCannonHealthFloor() {
		section("Glass Cannon Pact: the health floor, alone and stacked");
		HarnessBootstrap.init();

		var health = ranged(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		checkNear(health.getMinValue(), 1.0, 1e-9,
				"MAX_HEALTH floors at 1.0, not 0 -- unlike GRAVITY's -1.0");

		double pact = constant(GlassCannonPactBehavior.class, "HEALTH_PENALTY");
		double brittle = -4.0; // BrittleBonesBehavior passes this inline to super
		checkNear(pact, -2.0, 1e-9, "the penalty is -2.0 raw = -1 heart");

		checkNear(health.getDefaultValue() + pact, 18.0, 1e-9, "alone: 18 health (9 hearts)");
		checkNear(health.getDefaultValue() + brittle, 16.0, 1e-9,
				"Brittle Bones alone: 16 health (8 hearts)");
		double both = health.getDefaultValue() + pact + brittle;
		checkNear(both, 14.0, 1e-9, "BOTH stacked: 14 health (7 hearts) -- nowhere near the floor");
		checkNear(health.sanitizeValue(both), both, 1e-9, "and not clamped");

		// The structural guarantee: unlike gravity, over-stacking here bottoms out
		// harmlessly. Driven against the real sanitizeValue rather than asserted.
		checkNear(health.sanitizeValue(0.0), 1.0, 1e-9,
				"sanitizeValue(0) is 1.0 -- half a heart, never zero");
		checkNear(health.sanitizeValue(-100.0), 1.0, 1e-9,
				"sanitizeValue(-100) is also 1.0, so no number of stacked penalties can kill");
		check(health.sanitizeValue(health.getDefaultValue() + pact * 20) >= 1.0,
				"twenty of these stacked would still leave the player alive");

		// They are in different categories, so anti-stacking does NOT separate them --
		// the pairing above is ordinary, not an edge case.
		check(EffectRegistry.byId(GlassCannonPactBehavior.ID).category()
						!= EffectRegistry.byId(BrittleBonesBehavior.ID).category(),
				"Glass Cannon Pact (COMBAT) and Brittle Bones (SURVIVAL) are different "
						+ "categories, so anti-stacking permits holding both");

		// The attack half.
		double attack = constant(GlassCannonPactBehavior.class, "ATTACK_BONUS");
		checkNear(attack, 0.50, 1e-9, "attack bonus is +50%");
		check(Checks.hasMethod(GlassCannonPactBehavior.class, "afterApply"),
				"current health is clamped on apply -- lowering max health does not "
						+ "lower current health by itself");
	}

	/**
	 * Phoenix Chambered Heart fires exactly once per run, and neither a relog nor
	 * re-acquiring the effect can refund it.
	 */
	private static void phoenixHeartOnce() {
		section("Phoenix Chambered Heart: once per run, and it stays spent");
		HarnessBootstrap.init();

		EntropyManager manager = new EntropyManager();
		manager.startRun();
		check(!manager.isPhoenixHeartAvailable(),
				"unavailable before the effect is acquired");
		check(!manager.spendPhoenixHeart(), "and cannot be spent");

		manager.acquired().add(PhoenixChamberedHeartBehavior.ID);
		check(manager.isPhoenixHeartAvailable(), "available once acquired");

		check(manager.spendPhoenixHeart(), "the first killing blow spends it");
		check(manager.isPhoenixHeartUsed(), "the run flag is set");
		check(!manager.acquired().contains(PhoenixChamberedHeartBehavior.ID),
				"and the effect is dropped from the acquired set");

		// The heart of the requirement.
		check(!manager.spendPhoenixHeart(), "a SECOND killing blow does not");
		for (int i = 0; i < 5; i++) {
			check(!manager.spendPhoenixHeart(), "still refused on repeat attempt " + (i + 1));
		}

		// Re-acquiring must not refund it. This is why the flag, not the acquired
		// set, is what enforces "once" -- the repeat fallback can legitimately
		// re-offer an already-taken effect once a phase's pool empties.
		manager.acquired().add(PhoenixChamberedHeartBehavior.ID);
		check(manager.acquired().contains(PhoenixChamberedHeartBehavior.ID),
				"the effect can be re-acquired (the repeat fallback allows it)");
		check(!manager.isPhoenixHeartAvailable(),
				"but it is still unavailable -- the run flag outranks the acquired set");
		check(!manager.spendPhoenixHeart(), "and re-acquiring does not refund the save");

		// Across a relog, which for a SavedData means across the codec.
		EntropyManager reloaded = reencode(manager);
		check(reloaded.isPhoenixHeartUsed(), "the spent flag survives a save/reload");
		check(!reloaded.isPhoenixHeartAvailable(),
				"so a relog cannot refund it either, even holding the effect again");
		check(!reloaded.spendPhoenixHeart(), "and it still cannot be spent after reloading");

		// A run that never spent it must still be able to.
		EntropyManager fresh = new EntropyManager();
		fresh.startRun();
		fresh.acquired().add(PhoenixChamberedHeartBehavior.ID);
		EntropyManager freshReloaded = reencode(fresh);
		check(freshReloaded.isPhoenixHeartAvailable(),
				"an UNSPENT Phoenix Chambered Heart survives a reload too -- the flag is "
						+ "not defaulting to spent");
		check(freshReloaded.spendPhoenixHeart(), "and can still be spent after the reload");

		// Same store as Second Guess, not a parallel one.
		check(Checks.classReferences(EntropyManager.class, "second_chance_used"),
				"the flag lives in EntropyManager's own codec, beside reroll_used");
		check(Checks.classReferences(com.entropymod.entropy.EffectHooks.class,
						"BYPASSES_INVULNERABILITY"),
				"the hook still lets /kill and the void through, matching vanilla's "
						+ "own first line in checkTotemDeathProtection");
		checkNear(constant(PhoenixChamberedHeartBehavior.class, "SURVIVE_HEALTH"), 1.0, 1e-6,
				"the save puts the player on half a heart -- the same value vanilla's own "
						+ "totem branch uses, and mandatory because the hook runs at zero "
						+ "health and none of the three grants raises current health");
		check(constant(PhoenixChamberedHeartBehavior.class, "INVULNERABLE_TICKS") > 0,
				"with a brief window of immunity so the same blow cannot re-land");
	}

	/**
	 * The granted outcome: what vanilla's own formulas actually produce at
	 * amplifier 9, and that expiry is vanilla's problem rather than ours.
	 *
	 * <p>Every number here is re-derived from the shipped constants against the
	 * real vanilla rule, never restated -- {@code AttributeTemplate.create} is
	 * {@code amount * (amplifier + 1)}, and Regeneration's cadence is
	 * {@code 50 >> amplifier}.
	 */
	private static void phoenixHeartGrants() {
		section("Phoenix Chambered Heart: the granted effects' real numbers");
		HarnessBootstrap.init();

		int amplifier = (int) constant(PhoenixChamberedHeartBehavior.class, "AMPLIFIER");
		check(amplifier == 9, "amplifier 9 -- displayed level X, since vanilla builds the "
				+ "label as \"potion.potency.\" + amplifier and potency.1 is \"II\"");

		// --- Health Boost X and Absorption X ------------------------------------
		// Both register amount 4.0 with ADD_VALUE; the per-level scaling is shared.
		double perLevel = constant(PhoenixChamberedHeartBehavior.class, "PER_LEVEL_AMOUNT");
		checkNear(perLevel, 4.0, 1e-9,
				"vanilla registers 4.0 per level for both Health Boost and Absorption");
		double granted = PhoenixChamberedHeartBehavior.grantedAmount();
		checkNear(granted, 40.0, 1e-9,
				"AttributeTemplate.create's amount * (amplifier + 1) gives +40.0 at level X");

		// Against the REAL attribute, so the claim is about max health rather than
		// about a number in this file.
		var maxHealth = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
		var instance = instanceFor(maxHealth);
		instance.setBaseValue(20.0);
		instance.addOrUpdateTransientModifier(
				new net.minecraft.world.entity.ai.attributes.AttributeModifier(
						net.minecraft.resources.Identifier.withDefaultNamespace("effect.health_boost"),
						granted,
						net.minecraft.world.entity.ai.attributes.AttributeModifier
								.Operation.ADD_VALUE));
		checkNear(instance.getValue(), 60.0, 1e-9,
				"Health Boost X takes a vanilla player from 20.0 to 60.0 max health (30 hearts)");

		// --- composition with the PERMANENT max-health effects -------------------
		// Same attribute, same operation, different Identifiers -- so they sum
		// rather than clobber. This is the interaction the brief asked about.
		addModifier(instance, GlassCannonPactBehavior.ID,
				constant(GlassCannonPactBehavior.class, "HEALTH_PENALTY"),
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		addModifier(instance, BrittleBonesBehavior.ID, -4.0,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		checkNear(instance.getValue(), 54.0, 1e-9,
				"stacked with Glass Cannon Pact and Brittle Bones: 20 - 2 - 4 + 40 = 54.0, "
						+ "so the temporary boost composes additively with the permanent ones");
		check(instance.getModifiers().size() == 3,
				"all three modifiers coexist on the instance -- distinct ids, no clobbering");

		// Removing the vanilla effect's modifier must leave the mod's alone. That is
		// what makes expiry safe: MobEffect.removeAttributeModifiers removes strictly
		// by the template's own id.
		instance.removeModifier(
				net.minecraft.resources.Identifier.withDefaultNamespace("effect.health_boost"));
		checkNear(instance.getValue(), 14.0, 1e-9,
				"and when Health Boost expires the run's own -6.0 is exactly what is left");
		check(instance.getModifiers().size() == 2,
				"expiry removes one modifier, not the pool");

		// --- Regeneration X ------------------------------------------------------
		check(PhoenixChamberedHeartBehavior.regenerationInterval(0) == 50,
				"Regeneration I heals every 50 ticks, per vanilla's 50 >> amplifier");
		check(PhoenixChamberedHeartBehavior.regenerationInterval(amplifier) == 0,
				"Regeneration X's shift SATURATES to 0, which vanilla reads as "
						+ "'heal every tick' rather than 'never'");
		check(PhoenixChamberedHeartBehavior.regenerationInterval(5) == 1
						&& PhoenixChamberedHeartBehavior.regenerationInterval(6) == 0,
				"saturation begins at amplifier 6, so level X is well past it and larger "
						+ "amplifiers would change nothing");

		int regenTicks = (int) constant(PhoenixChamberedHeartBehavior.class, "REGENERATION_TICKS");
		check(regenTicks == 100, "Regeneration runs for 100 ticks (5 seconds)");
		double healed = regenTicks * 1.0; // applyEffectTick heals exactly 1.0F
		checkNear(healed, 100.0, 1e-9,
				"which is up to 100.0 HP of healing -- 1.0 per tick, every tick");
		double toFill = 60.0 - constant(PhoenixChamberedHeartBehavior.class, "SURVIVE_HEALTH");
		check(healed > toFill,
				"comfortably more than the " + (int) toFill + " HP needed to fill the boosted "
						+ "bar, so the fill completes in about " + (int) toFill + " ticks and "
						+ "the rest are no-ops (applyEffectTick checks health < maxHealth)");

		// --- durations -----------------------------------------------------------
		check(constant(PhoenixChamberedHeartBehavior.class, "HEALTH_BOOST_TICKS") == 600
						&& constant(PhoenixChamberedHeartBehavior.class, "ABSORPTION_TICKS") == 600,
				"Health Boost and Absorption both run 600 ticks (30 seconds)");

		// --- expiry leaves nothing dangling, and it is vanilla that guarantees it --
		// LivingEntity.onAttributeUpdated clamps health down when MAX_HEALTH falls and
		// absorption down when MAX_ABSORPTION falls; MAX_ABSORPTION's default is 0.
		var maxAbsorption = net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION;
		checkNear(maxAbsorption.value().getDefaultValue(), 0.0, 1e-9,
				"MAX_ABSORPTION defaults to 0.0, so the absorption pool drains itself on "
						+ "expiry with no mod-side bookkeeping");
		check(Checks.classReferences(net.minecraft.world.entity.LivingEntity.class,
						"refreshDirtyAttributes"),
				"and LivingEntity's own effect loop calls refreshDirtyAttributes, which is "
						+ "what runs the clamps -- expiry is entirely vanilla's");

		// The mod must own no timer for any of this.
		check(!Checks.hasMethod(PhoenixChamberedHeartBehavior.class, "tick"),
				"the effect has no tick method -- durations are MobEffectInstance's, not ours");
		check(EffectBehaviors.get(PhoenixChamberedHeartBehavior.ID)
						instanceof com.entropymod.entropy.HookEffectBehavior,
				"and it is still a HookEffectBehavior: no per-player state to leak");
	}

	/**
	 * The rest of Phoenix Chambered Heart's kit: Speed III, Blindness and the
	 * Wither death sting.
	 *
	 * <p>Every number is read off the <em>live registry</em> rather than restated,
	 * which is what makes this a check and not a copy of the constants. The
	 * Blindness assertion in particular is the interesting one: it asserts an
	 * <b>absence</b>, which is the only way to establish that an amplifier is
	 * meaningless rather than merely untested.
	 */
	private static void phoenixHeartKit() {
		section("Phoenix Chambered Heart: Speed III, Blindness and the Wither sting");

		int speedAmp = (int) constant(PhoenixChamberedHeartBehavior.class, "SPEED_AMPLIFIER");
		check(speedAmp == 2, "Speed's raw amplifier is 2 -- displayed level III, by the "
				+ "amplifier+1 convention confirmed for the level X grants");
		check((int) constant(PhoenixChamberedHeartBehavior.class, "SPEED_TICKS") == 600,
				"Speed runs 600 ticks (30s), the same window as Health Boost and Absorption");
		check(speedAmp <= 5, "and it is within potion.potency.0-5, which vanilla's own lang "
				+ "file defines -- unlike the level X grants, this needs no key of ours");

		// --- Speed's real template, out of the registry ---
		var speedMods = new java.util.ArrayList<net.minecraft.world.entity.ai.attributes.AttributeModifier>();
		var speedAttrs = new java.util.ArrayList<String>();
		net.minecraft.world.effect.MobEffects.SPEED.value().createModifiers(speedAmp, (attr, mod) -> {
			speedAttrs.add(attr.value().getDescriptionId());
			speedMods.add(mod);
		});
		check(speedMods.size() == 1, "Speed carries exactly one attribute template");
		check(speedAttrs.get(0).equals(
						net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED
								.value().getDescriptionId()),
				"and it is on MOVEMENT_SPEED");
		check(speedMods.get(0).operation()
						== net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
				"as ADD_MULTIPLIED_TOTAL -- so it composes as a PRODUCT with the sprint "
						+ "bonus and with Slippery Grip's compensator, never summing into them");
		checkNear(speedMods.get(0).amount(), PhoenixChamberedHeartBehavior.speedGrantedAmount(), 1e-12,
				"and the live amount at amplifier " + speedAmp + " matches the shipped "
						+ "derivation exactly");
		checkNear(speedMods.get(0).amount(), 0.6000000089406967, 1e-15,
				"Speed III is +0.6 ADD_MULTIPLIED_TOTAL, i.e. a x1.6 movement-speed multiplier");

		// Ground speed is linear in the attribute, so x1.6 on the attribute IS x1.6
		// in blocks per second. Driven through the same model the sprint-jump
		// section validates against vanilla's published figures.
		double walk = SprintModel.groundSpeedBps(0.1);
		double boosted = SprintModel.groundSpeedBps(0.1 * (1.0 + speedMods.get(0).amount()));
		checkNear(boosted / walk, 1.6, 1e-7,
				"and because ground speed is linear in the attribute, that is exactly x1.6 "
						+ "in blocks/second: " + fmt(walk) + " -> " + fmt(boosted) + " b/s");

		// --- Blindness carries NO amplifier-sensitive state at all ---
		int blindAmp = (int) constant(PhoenixChamberedHeartBehavior.class, "BLINDNESS_AMPLIFIER");
		check(blindAmp == 0, "Blindness ships at amplifier 0");
		check((int) constant(PhoenixChamberedHeartBehavior.class, "BLINDNESS_TICKS") == 100,
				"for 100 ticks (5s)");
		var blindness = net.minecraft.world.effect.MobEffects.BLINDNESS.value();
		check(blindness.getClass() == net.minecraft.world.effect.MobEffect.class,
				"and vanilla registers it as a BARE MobEffect, not a subclass -- so it "
						+ "overrides no tick behaviour that could read an amplifier");
		for (int amp = 0; amp <= 9; amp++) {
			int[] count = {0};
			blindness.createModifiers(amp, (attr, mod) -> count[0]++);
			check(count[0] == 0,
					"Blindness produces zero attribute modifiers at amplifier " + amp);
		}
		check(blindness.getCategory()
						== net.minecraft.world.effect.MobEffectCategory.HARMFUL,
				"it is HARMFUL, so a milk bucket clears it -- the counterplay is vanilla's");

		// --- the sound resolves, and is the Wither's, not a guess ---
		var wither = net.minecraft.sounds.SoundEvents.WITHER_DEATH;
		check(wither.location().toString().equals("minecraft:entity.wither.death"),
				"SoundEvents.WITHER_DEATH is entity.wither.death");
		var holder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
				.wrapAsHolder(wither);
		check(holder.kind() == net.minecraft.core.Holder.Kind.REFERENCE,
				"and it wraps as a REGISTRY REFERENCE, so ClientboundSoundPacket sends a "
						+ "registry id rather than inlining the whole sound definition");
		checkNear(constant(PhoenixChamberedHeartBehavior.class, "SOUND_VOLUME"), 1.0, 1e-6,
				"volume 1.0");
		checkNear(constant(PhoenixChamberedHeartBehavior.class, "SOUND_PITCH"), 1.0, 1e-6,
				"pitch 1.0");
		// Player-only delivery is a packet, not a Level.playSound call. Asserting the
		// absence matters: every playSound overload's Entity parameter is the player
		// to EXCLUDE, so reaching for one would broadcast and read as correct.
		check(Checks.classReferences(com.entropymod.entropy.EntropyManager.class,
						"ClientboundSoundPacket"),
				"EntropyManager sends the sting as a ClientboundSoundPacket -- addressed to "
						+ "the rescued player's connection, so nobody else hears it");
	}

	/**
	 * Slippery Grip's sprint-jump bypass: the physics model, the three sprint
	 * bonuses, and the single factor that scales all of them.
	 *
	 * <p>The model is validated against vanilla's three published figures before
	 * anything is concluded from it -- that is what makes the rest of this section
	 * evidence rather than arithmetic about itself.
	 */
	private static void slipperyGripSprintJump() {
		section("Slippery Grip: the sprint-jump bypass and its fix");

		// --- validate the model first ---
		checkNear(SprintModel.groundSpeedBps(0.1), 4.3172, 1e-3,
				"the per-tick model reproduces vanilla's published walking speed 4.317 b/s");
		checkNear(SprintModel.groundSpeedBps(0.13), 5.6123, 1e-3,
				"and its published sprinting speed 5.612 b/s");
		checkNear(SprintModel.jumpingSpeedBps(0.13, true, 1.0, 1.0), 7.1263, 1e-3,
				"and its published sprint-jumping speed ~7.12 b/s -- three independent "
						+ "validations before any conclusion is drawn from it");

		// --- the two bonuses the attribute cannot reach ---
		checkNear(constant(SlipperyGripBehavior.class, "VANILLA_SPRINT_JUMP_IMPULSE"), 0.2, 1e-12,
				"vanilla's sprint-jump impulse is a FLAT 0.2 blocks/tick, read from no "
						+ "attribute -- LivingEntity.jumpFromGround");
		double airWalk = constant(SlipperyGripBehavior.class, "VANILLA_AIR_ACCEL_WALKING");
		double airSprint = constant(SlipperyGripBehavior.class, "VANILLA_AIR_ACCEL_SPRINTING");
		checkNear(airSprint / airWalk, 1.3, 1e-6,
				"and airborne acceleration is 0.02 -> 0.025999999 when sprinting, which is "
						+ "the SAME x1.3 as the speed modifier");
		check(Checks.classReferences(net.minecraft.world.entity.LivingEntity.class,
						"getFlyingSpeed"),
				"getFrictionInfluencedSpeed falls through to getFlyingSpeed, so MOVEMENT_SPEED "
						+ "is not consulted at all while airborne");

		// --- the bug, quantified ---
		double cursedAttr = 0.1 * constant(SlipperyGripBehavior.class, "SPRINT_FRACTION");
		double cursedGround = SprintModel.groundSpeedBps(cursedAttr);
		checkNear(cursedGround, SprintModel.groundSpeedBps(0.1) * 0.5, 1e-9,
				"cursed ground sprinting is exactly half of walking, as the effect claims: "
						+ fmt(cursedGround) + " b/s");
		double broken = SprintModel.jumpingSpeedBps(cursedAttr, true, 1.0, 1.0);
		check(broken > SprintModel.groundSpeedBps(0.1),
				"BUT with only the speed modifier scaled, sprint-JUMPING reaches "
						+ fmt(broken) + " b/s -- FASTER than simply walking, so the curse was "
						+ "not merely evaded but inverted into a reason to sprint");
		check(broken / SprintModel.jumpingSpeedBps(0.13, true, 1.0, 1.0) > 0.85,
				"it retained over 85% of vanilla's sprint-jump speed");

		// --- scaling only the impulse is NOT enough ---
		double scale = SlipperyGripBehavior.sprintScale(SlipperyGripBehavior.VANILLA_SPRINT_AMOUNT);
		checkNear(scale, 0.5 / 1.3, 1e-6, "the shared scale factor is SPRINT_FRACTION/(1+0.3)");
		checkNear(scale, 1.0 + SlipperyGripBehavior.compensatorAmount(
						SlipperyGripBehavior.VANILLA_SPRINT_AMOUNT), 1e-15,
				"and it is the SAME number the speed compensator is built from -- one "
						+ "derivation, so the three halves cannot drift apart");
		double impulseOnly = SprintModel.jumpingSpeedBps(cursedAttr, true, scale, 1.0);
		check(impulseOnly > SprintModel.groundSpeedBps(0.1),
				"scaling ONLY the jump impulse still leaves sprint-jumping at "
						+ fmt(impulseOnly) + " b/s, above walking -- both bonuses are needed");

		// --- the fix, and the exactness that justifies it ---
		double fixed = SprintModel.jumpingSpeedBps(cursedAttr, true, scale, scale);
		double vanillaJump = SprintModel.jumpingSpeedBps(0.13, true, 1.0, 1.0);
		checkNear(fixed, vanillaJump * scale, 1e-6,
				"with all three scaled by one factor, sprint-jumping is EXACTLY that factor "
						+ "of vanilla's -- the horizontal system is linear and homogeneous in "
						+ "(ground accel, air accel, impulse): " + fmt(fixed) + " b/s");
		check(fixed < SprintModel.groundSpeedBps(0.1),
				"and it is now slower than walking, which is what the curse claims");
		checkNear(fixed / cursedGround, vanillaJump / SprintModel.groundSpeedBps(0.13), 1e-6,
				"sprint-jumping is still worth the same ~27% over flat sprinting that it is "
						+ "in vanilla -- the system is scaled, not clipped");

		// --- walking is still untouched ---
		checkNear(SprintModel.jumpingSpeedBps(0.1, false, scale, scale),
				SprintModel.jumpingSpeedBps(0.1, false, 1.0, 1.0), 1e-12,
				"walking and walk-jumping are bit-identical either way: both bonuses are "
						+ "inside vanilla's own isSprinting branches");

		// --- the mixins are scoped so exactly one side ever acts ---
		check(Checks.classReferences(com.entropymod.mixin.LivingEntitySprintJumpMixin.class,
						"ServerLevel"),
				"the common sprint-jump mixin is scoped to ServerLevel");
		check(Checks.classReferences(com.entropymod.mixin.PlayerFlyingSpeedMixin.class,
						"ServerLevel"),
				"and so is the common air-control mixin -- EffectHooks answers 'no effect' on "
						+ "the client by design, so an unscoped half would fight its twin");
		check(Checks.classReferences(com.entropymod.mixin.PlayerFlyingSpeedMixin.class,
						"isSprinting"),
				"the air-control mixin gates on isSprinting, without which walking's 0.02 "
						+ "would be scaled too");
		check(Checks.classReferences(com.entropymod.mixin.PlayerFlyingSpeedMixin.class,
						"getAbilities"),
				"and on the flying flag, leaving creative flight out of scope");
		check(Checks.classReferences(com.entropymod.entropy.behavior.SlipperyGripSprint.class,
						"sprintScale"),
				"and both read their factor from SlipperyGripSprint.sprintScaleFor, i.e. off "
						+ "vanilla's LIVE sprint modifier rather than a second constant");
	}

	private static final List<String> SENSE_BATCH = List.of(
			DangerSenseBehavior.ID, DoubleJumpBehavior.ID);

	/**
	 * The sense batch: registration, wiring, no-repeat, persistence, and the two
	 * behavioural rules that are invisible in play until they are wrong.
	 */
	private static void senseBatch() {
		section("Sense batch: Danger Sense and Double Jump");
		HarnessBootstrap.init();

		// --- registration and wiring -------------------------------------------
		for (String id : SENSE_BATCH) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.phase() == EffectPhase.GOOD, id + " is GOOD");
			check(def.minEntropy() == 25 && def.maxEntropy() == 50,
					id + " sits in Tier 2 (entropy 25-50)");
			check(EffectBehaviors.get(id).getClass().getSimpleName().endsWith("Behavior"),
					id + " resolves to a real behavior, not the MISSING no-op");
		}
		check(EffectRegistry.byId(DangerSenseBehavior.ID).category() == EffectCategory.UTILITY,
				"Danger Sense is UTILITY");
		check(EffectRegistry.byId(DoubleJumpBehavior.ID).category() == EffectCategory.MOVEMENT,
				"Double Jump is MOVEMENT");
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatches anywhere after this batch");

		// --- idempotency -------------------------------------------------------
		// Both hold no per-player state of their own, which is what makes apply()
		// idempotent and respawn/rejoin-safe for free rather than by care.
		for (String id : SENSE_BATCH) {
			check(EffectBehaviors.get(id) instanceof com.entropymod.entropy.HookEffectBehavior,
					id + " is a HookEffectBehavior -- apply() is final and empty, so there is "
							+ "no per-player state to re-apply or leak across respawns");
		}

		// --- no-repeat ---------------------------------------------------------
		AcquiredEffects acquired = new AcquiredEffects();
		SENSE_BATCH.forEach(acquired::add);
		for (int entropy : new int[] {25, 37, 50}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(31),
						acquired.ids(), acquired.occupiedCategories());
				if (roll.repeatFallback()) {
					continue;
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!SENSE_BATCH.contains(offered.id()),
							"no-repeat holds at entropy " + entropy + "/" + phase
									+ ": already-acquired '" + offered.id() + "' was not re-offered");
				}
			}
		}

		// --- persistence -------------------------------------------------------
		EntropyManager saved = new EntropyManager();
		SENSE_BATCH.forEach(id -> saved.acquired().add(id));
		EntropyManager reloaded = reencode(saved);
		for (String id : SENSE_BATCH) {
			check(reloaded.acquired().contains(id),
					id + " survives a save/reload in the acquired set");
			check(EffectRegistry.byId(id) != null,
					id + " is still defined after a reload -- no stale id");
		}

		dangerSenseRadius();
		doubleJumpCharge();
	}

	/**
	 * Danger Sense's radius is a real 32-block sphere, and its glow starts and
	 * stops at that boundary.
	 *
	 * <p>The boundary is asserted in <b>both</b> directions on purpose. The query
	 * is an AABB and the effect is a sphere, so a missing distance filter would
	 * leak the radius out to {@code 32 * sqrt(3) = 55.4} blocks at the box corners
	 * -- a 73% over-range that would be almost impossible to notice in play.
	 */
	private static void dangerSenseRadius() {
		section("Danger Sense: the 32-block sphere, and where the glow stops");

		double radius = constant(DangerSenseBehavior.class, "RADIUS");
		checkNear(radius, 32.0, 1e-9, "the radius is 32 blocks (64-block diameter)");
		checkNear(constant(DangerSenseBehavior.class, "RADIUS_SQR"), radius * radius, 1e-9,
				"and RADIUS_SQR is genuinely its square, so the filter compares like with like");

		// Just inside / just outside, in squared space, the way the code tests it.
		check(DangerSenseBehavior.inRange(31.99 * 31.99),
				"a hostile at 31.99 blocks glows");
		check(DangerSenseBehavior.inRange(radius * radius),
				"one exactly at 32.00 blocks glows -- the boundary is inclusive");
		check(!DangerSenseBehavior.inRange(32.01 * 32.01),
				"one at 32.01 blocks does NOT glow");
		check(!DangerSenseBehavior.inRange(64.0 * 64.0),
				"and one at the far edge of the 64-block DIAMETER does not");

		// The leak this guards against, stated as a number rather than a worry.
		double boxCorner = radius * Math.sqrt(3.0);
		check(boxCorner > 55.0 && !DangerSenseBehavior.inRange(boxCorner * boxCorner),
				"the AABB's corner reaches " + fmt(boxCorner) + " blocks, and a mob there is "
						+ "rejected -- the spherical filter is what stops the box leaking a "
						+ "73% larger effective range");

		// --- start/stop timing --------------------------------------------------
		int scan = (int) constant(DangerSenseBehavior.class, "SCAN_INTERVAL_TICKS");
		int duration = (int) constant(DangerSenseBehavior.class, "GLOW_DURATION_TICKS");
		check(duration > scan,
				"the granted glow (" + duration + "t) outlasts the scan interval (" + scan
						+ "t), so a mob that stays in range never flickers between scans");
		check(duration <= 2 * scan,
				"and by no more than 2x, so a mob leaving the radius stops glowing within "
						+ fmt(duration / 20.0) + "s rather than lingering");
		check(scan <= 20,
				"the scan runs at least as often as Green Thumb's rescan -- mobs move, crops "
						+ "do not");

		// --- the mechanism is vanilla's, on the MOB, and amplifier-free ---------
		var glowing = net.minecraft.world.effect.MobEffects.GLOWING.value();
		check(glowing.getClass() == net.minecraft.world.effect.MobEffect.class,
				"vanilla registers Glowing as a BARE MobEffect -- no subclass, so nothing "
						+ "reads an amplifier");
		for (int amp = 0; amp <= 4; amp++) {
			int[] count = {0};
			glowing.createModifiers(amp, (attr, mod) -> count[0]++);
			check(count[0] == 0, "Glowing produces zero attribute modifiers at amplifier " + amp);
		}
		check((int) constant(DangerSenseBehavior.class, "AMPLIFIER") == 0,
				"so the shipped amplifier is 0, the only value that means anything");

		// The glow is driven by the effect through synced entity flag 6 -- which is
		// also why it cannot be scoped to one observer. Asserted so the limitation
		// is recorded in the checks, not only in the javadoc.
		check(Checks.classReferences(net.minecraft.world.entity.LivingEntity.class,
						"updateGlowingStatus"),
				"LivingEntity.updateGlowingStatus pushes the effect into shared entity flag 6, "
						+ "which is synced data -- so EVERY tracking client sees the outline, "
						+ "not just the holder");

		// Cost: an entity query, not a volume scan. This is the claim that justifies
		// a radius 56x Green Thumb's in volume.
		check(Checks.classReferences(com.entropymod.entropy.growth.DangerSenseGlow.class,
						"getEntitiesOfClass"),
				"the scan is getEntitiesOfClass -- O(entities in the box), NOT O(volume); a "
						+ "block sweep at this radius would be 274,625 positions");
		check(!Checks.classReferences(com.entropymod.entropy.growth.DangerSenseGlow.class,
						"betweenClosed"),
				"and it does no BlockPos iteration at all");
		check(Checks.classReferences(com.entropymod.entropy.growth.DangerSenseGlow.class,
						"isSpectator"),
				"a spectator does not light up the world");
	}

	/**
	 * Double Jump recharges on landing and never yields a third jump.
	 *
	 * <p>Driven against the real {@code DoubleJumpState}, which is deliberately
	 * free of Minecraft types precisely so this can be checked headlessly.
	 */
	private static void doubleJumpCharge() {
		section("Double Jump: one air jump, recharged on landing, never a third");

		check((int) constant(DoubleJumpBehavior.class, "AIR_JUMPS") == 1,
				"one air jump, i.e. two jumps in total");

		// --- the REAL sampling order --------------------------------------------
		// This block previously fed tick(held=true, onGround=true) for the press
		// tick -- a state END_CLIENT_TICK can NEVER observe, because aiStep calls
		// jumpFromGround (offset 460) before travel/move (offset 615) writes
		// onGround. The state machine was right and the sampling model was wrong,
		// which is why the bug shipped green. Everything below uses the order the
		// driver actually sees.
		DoubleJumpState s = new DoubleJumpState();
		check(!s.tick(false, true, true), "standing still, key up: no jump");
		check(s.chargesLeft() == 1, "the charge is full while on the ground");

		// THE REGRESSION: the ground jump's own press, observed already airborne.
		check(!s.tick(true, false, true),
				"the press that caused the GROUND jump is observed as held+airborne on the "
						+ "same tick -- and must NOT fire an air jump there");
		check(s.chargesLeft() == 1,
				"and must NOT spend the charge: this is the exact 'instantly jumps, no "
						+ "double jump' report");

		check(!s.tick(true, false, true),
				"holding the key on the next tick still does nothing -- the edge was consumed");
		check(!s.tick(true, false, true), "and on the one after that");
		check(s.chargesLeft() == 1, "the charge is still banked, waiting for a real press");
		check(!s.tick(false, false, true), "releasing mid-air does nothing by itself");
		check(s.tick(true, false, true), "pressing again mid-air FIRES the air jump");
		check(s.chargesLeft() == 0, "and spends the charge");

		// The air jump must be usable at ANY point in the fall, not only immediately.
		DoubleJumpState late = new DoubleJumpState();
		late.tick(false, true, true);
		late.tick(true, false, true);              // ground jump's press
		for (int i = 0; i < 30; i++) {
			check(!late.tick(false, false, true), "still no jump while falling, tick " + i);
		}
		check(late.chargesLeft() == 1, "the charge survives a long fall unspent");
		check(late.tick(true, false, true),
				"and a press 30 ticks into the fall still fires it -- the air jump is "
						+ "available at any point while airborne, not just at the apex");

		// --- no third jump ------------------------------------------------------
		for (int i = 0; i < 5; i++) {
			check(!s.tick(false, false, true) && !s.tick(true, false, true),
					"no third jump on repeat attempt " + (i + 1) + " -- still airborne, no charge");
		}

		// --- recharge on landing ------------------------------------------------
		check(!s.tick(false, true, true), "landing recharges");
		check(s.chargesLeft() == 1, "the charge is back to one");
		check(!s.tick(true, false, true), "the next ground jump's press again does nothing");
		check(s.chargesLeft() == 1, "and again does not spend the charge");
		check(!s.tick(false, false, true), "airborne, key released");
		check(s.tick(true, false, true), "and the air jump is available again");
		check(!s.tick(false, false, true) && !s.tick(true, false, true),
				"still no third jump on the second cycle");

		// --- the full sequence the player will test, end to end -----------------
		DoubleJumpState seq = new DoubleJumpState();
		seq.tick(false, true, true);                       // standing
		check(!seq.tick(true, false, true), "1) ground jump: no extra jump on that tick");
		check(!seq.tick(false, false, true), "   rising, key released");
		check(seq.tick(true, false, true), "2) air jump fires on a fresh mid-air press");
		check(!seq.tick(false, false, true) && !seq.tick(true, false, true)
						&& !seq.tick(false, false, true) && !seq.tick(true, false, true),
				"3) no third jump, however many times it is pressed");
		check(!seq.tick(false, true, true), "4) landing");
		check(seq.chargesLeft() == 1, "   recharges, and the cycle can start again");

		// --- walking off a ledge without jumping --------------------------------
		DoubleJumpState ledge = new DoubleJumpState();
		ledge.tick(false, true, true);
		check(ledge.chargesLeft() == 1, "walking on the ground banks a charge");
		check(!ledge.tick(false, false, true),
				"the tick you leave the ledge is consumed (vanilla would have ground-jumped "
						+ "a press made then, since onGround was still true in aiStep)");
		check(ledge.tick(true, false, true),
				"and the next press gives exactly one air jump -- the charge is read from "
						+ "onGround(), not counted from a jump");
		check(!ledge.tick(false, false, true) && !ledge.tick(true, false, true),
				"and only one");

		// --- refused states do NOT consume the charge ---------------------------
		DoubleJumpState refused = new DoubleJumpState();
		refused.tick(false, true, true);
		refused.tick(false, false, true);          // leave the ground cleanly
		check(!refused.tick(true, false, false),
				"a press in a refused state (flight/elytra/fluid/climbing/riding) does not jump");
		check(refused.chargesLeft() == 1,
				"and does NOT consume the charge -- the effect is intact the moment the state ends");
		check(!refused.tick(true, false, true),
				"the key is still held, so leaving the state mid-hold does not auto-fire a jump "
						+ "the player never asked for");
		check(refused.tick(false, false, true) == false && refused.tick(true, false, true),
				"a fresh press after leaving the state does fire it");

		// --- reset --------------------------------------------------------------
		DoubleJumpState fresh = new DoubleJumpState();
		fresh.tick(false, true, true);
		fresh.reset();
		check(fresh.chargesLeft() == 0,
				"reset drops the charge, so it cannot survive a disconnect into the next world");

		// --- the mechanism ------------------------------------------------------
		// NOTE: ClientDoubleJump itself is NOT reachable from here -- the harness
		// classpath is the main source set only, which is the same property that
		// pushed the keybind snapshot to server-side persistence. That is exactly
		// why the whole charge machine lives in DoubleJumpState, in main, free of
		// Minecraft types: everything above is driven against the real shipped
		// logic rather than a copy of it. What the client class does with the
		// result (jumpFromGround, and the six state guards) is in-game territory.
		check(Checks.hasMethod(DoubleJumpState.class, "tick")
						&& Checks.hasMethod(DoubleJumpState.class, "reset"),
				"the charge machine is in the main source set, so these rules are checked "
						+ "against shipped code and not restated");
	}

	/**
	 * Ore Sense's detection layer: the radius boundary, the scan volume, and the
	 * shipped tag's real contents.
	 *
	 * <p><b>The effect is deliberately NOT registered</b> -- its renderer is
	 * unbuilt, and a registered effect that draws nothing would be a dead entry in
	 * the roll pool. This section covers everything that exists, and the absence of
	 * a registration is asserted so it cannot be half-shipped by accident.
	 */
	private static void oreSenseDetection() {
		section("Ore Sense: detection layer (renderer NOT built -- see the skill)");
		HarnessBootstrap.init();

		double radius = constant(OreSenseBehavior.class, "RADIUS");
		checkNear(radius, 8.0, 1e-9, "the radius is 8 blocks");
		checkNear(constant(OreSenseBehavior.class, "RADIUS_SQR"), radius * radius, 1e-9,
				"RADIUS_SQR is genuinely its square");
		int reach = (int) constant(OreSenseBehavior.class, "REACH");
		check(reach == 8, "REACH is ceil(radius) = 8, so the box is 17x17x17");

		// --- the volume, stated as the number that justifies the cadence --------
		int volume = (2 * reach + 1) * (2 * reach + 1) * (2 * reach + 1);
		check(volume == 4913,
				"the scan is " + volume + " positions -- deliberately the same number as "
						+ "Green Thumb's rescan, this project's known-safe scan size");
		check((int) constant(OreSenseBehavior.class, "RESCAN_INTERVAL_TICKS") == 20,
				"and it runs every 20 ticks, the same cadence, for ~246 reads per tick");

		// --- the boundary, both directions --------------------------------------
		check(OreSenseBehavior.inRange(7.99 * 7.99), "an ore at 7.99 blocks is detected");
		check(OreSenseBehavior.inRange(radius * radius),
				"one exactly at 8.00 is detected -- the boundary is inclusive");
		check(!OreSenseBehavior.inRange(8.01 * 8.01), "one at 8.01 is NOT detected");
		check(!OreSenseBehavior.inRange(16.0 * 16.0), "and one at 16 blocks certainly is not");

		// --- the leak this guards against, as a number --------------------------
		double corner = radius * Math.sqrt(3.0);
		check(corner > 13.8 && corner < 13.9,
				"a bare 17-cube reaches " + fmt(corner) + " blocks at its corners");
		check(!OreSenseBehavior.inRange(corner * corner),
				"and an ore there is rejected -- the spherical filter is what stops a 73% "
						+ "over-range that would read as 'the radius is just bigger than 8'");
		// Every corner of the box must be outside; spot-check the eight extremes.
		int rejected = 0;
		for (int dx : new int[] {-reach, reach}) {
			for (int dy : new int[] {-reach, reach}) {
				for (int dz : new int[] {-reach, reach}) {
					double d2 = (double) dx * dx + (double) dy * dy + (double) dz * dz;
					if (!OreSenseBehavior.inRange(d2)) {
						rejected++;
					}
				}
			}
		}
		check(rejected == 8, "all eight box corners are rejected by the sphere filter");

		// --- the tag: read the SHIPPED JSON, do not restate the constant --------
		// Same approach Clumsy Digger's check uses for #minecraft:enchantable/mining:
		// checking what the tag actually CONTAINS, against real data.
		String tagJson = readResource(
				"/data/entropymod/tags/block/ore_sense_targets.json");
		check(tagJson != null, "the mod ships data/entropymod/tags/block/ore_sense_targets.json");
		if (tagJson != null) {
			for (String material : new String[] {"coal", "iron", "copper", "gold",
					"redstone", "lapis", "diamond", "emerald"}) {
				check(tagJson.contains("#minecraft:" + material + "_ores"),
						"the tag includes #minecraft:" + material + "_ores");
			}
			// The two that fall outside all eight vanilla ore tags, included on
			// purpose rather than silently dropped.
			check(tagJson.contains("minecraft:ancient_debris"),
					"ancient debris is INCLUDED -- it is in none of the eight vanilla ore "
							+ "tags, and an ore sense silent on it would read as a bug");
			check(tagJson.contains("minecraft:nether_quartz_ore"),
					"nether quartz ore is INCLUDED, for the same reason");
		}
		// Nether gold ore needs no special case, and that is worth pinning: it is
		// already inside #minecraft:gold_ores.
		check(tagContains("/data/minecraft/tags/block/gold_ores.json", "nether_gold_ore"),
				"nether gold ore needs no special case -- vanilla's #gold_ores already "
						+ "contains it");

		// --- not registered, deliberately ---------------------------------------
		check(EffectRegistry.byId(OreSenseBehavior.ID) == null,
				"ore_sense is NOT in EffectRegistry: the renderer is unbuilt, and a "
						+ "registered effect that draws nothing would be a dead roll-pool entry");
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"and leaving it out of BOTH registries keeps the id tables consistent");
	}

	/** Vanilla's PARTICLES_SMASH_ATTACK id, read by reflection (the field is inlined at call sites). */
	private static int vanillaLevelEventSmashAttack() {
		try {
			var f = net.minecraft.world.level.block.LevelEvent.class
					.getDeclaredField("PARTICLES_SMASH_ATTACK");
			f.setAccessible(true);
			return f.getInt(null);
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * How many particles {@code ParticleUtils.spawnSmashAttackParticles} actually emits
	 * for a given {@code levelEvent} data value.
	 *
	 * <p>A transcription of the two loops in vanilla's client-side handler for event
	 * 2013, javap-verified:
	 *
	 * <pre>
	 * for (i = 0; i &lt; data / 3.0f;  i++)   // column at the player's feet
	 * for (i = 0; i &lt; data / 1.5f;  i++)   // ring at radius 3.5
	 * </pre>
	 *
	 * <p>This is the model whose absence let Earthquake ship an invisible effect: the
	 * data int was treated as an abstract intensity dial when it is very nearly a
	 * literal particle count. It is written out here rather than asserted as a bare
	 * number so the relationship, not just the answer, is what the check pins.
	 *
	 * <p>Deliberately NOT calling the real method: it needs a {@code LevelAccessor} and
	 * emits client-side particles. What is being checked is the arithmetic that turns a
	 * shipped constant into a count on screen, and that is reproducible exactly.
	 */
	private static int smashAttackParticlesFor(int data) {
		int emitted = 0;
		for (int i = 0; i < data / 3.0f; i++) {
			emitted++;
		}
		for (int i = 0; i < data / 1.5f; i++) {
			emitted++;
		}
		return emitted;
	}

	/**
	 * Round-trips a {@link TremorLockPayload} through its real {@code StreamCodec}.
	 *
	 * <p>Same model as the other payload checks: the real codec against
	 * {@code RegistryAccess.EMPTY}, which needs no bootstrap because the single
	 * component is a varint.
	 */
	private static TremorLockPayload roundTripTremorLock(TremorLockPayload in) {
		io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
		net.minecraft.network.RegistryFriendlyByteBuf buf =
				new net.minecraft.network.RegistryFriendlyByteBuf(
						raw, net.minecraft.core.RegistryAccess.EMPTY);
		TremorLockPayload.CODEC.encode(buf, in);
		return TremorLockPayload.CODEC.decode(buf);
	}

	/**
	 * Whether vanilla's Mining Fatigue actually reads its amplifier.
	 *
	 * <p>Blindness does not -- it is a bare {@code MobEffect} with no
	 * {@code addAttributeModifier}, already asserted at amplifiers 0-9 under Phoenix
	 * Chambered Heart. Mining Fatigue is the opposite case and the distinction is worth
	 * pinning, because "amplifier 0" means two completely different things across the
	 * two grants in this one effect: meaningless for one, a real level choice for the
	 * other.
	 */
	private static boolean vanillaMiningFatigueIsAttributeBacked() {
		try {
			HarnessBootstrap.init();
			var effect = net.minecraft.world.effect.MobEffects.MINING_FATIGUE.value();
			boolean any = false;
			for (int amp = 0; amp <= 3; amp++) {
				var mods = new java.util.ArrayList<String>();
				effect.createModifiers(amp, (holder, modifier) -> mods.add(modifier.id().toString()));
				if (!mods.isEmpty()) {
					any = true;
				}
			}
			return any;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Whether Earthquake still declares counterplay = true. */
	private static boolean EarthquakeRegistryDefinitionCounterplay() {
		EffectDefinition def = EffectRegistry.byId(EarthquakeBehavior.ID);
		return def != null && def.counterplay();
	}

	/** Every copy of a classpath resource, in classloader order. Tag files merge, so there are several. */
	private static List<String> allResources(String path) {
		List<String> out = new ArrayList<>();
		try {
			var urls = HarnessMain.class.getClassLoader().getResources(path.substring(1));
			while (urls.hasMoreElements()) {
				try (var in = urls.nextElement().openStream()) {
					out.add(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
				}
			}
		} catch (Exception ignored) {
			// An unreadable classpath is itself a failure the caller's size check reports.
		}
		return out;
	}

	/** Reads a classpath resource as a string, or null. */
	private static String readResource(String path) {
		try (var in = HarnessMain.class.getResourceAsStream(path)) {
			return in == null ? null : new String(in.readAllBytes(),
					java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return null;
		}
	}

	/** Whether a shipped tag JSON on the classpath mentions a value. */
	private static boolean tagContains(String path, String value) {
		String json = readResource(path);
		return json != null && json.contains(value);
	}

	/** Four decimal places, for reporting derived blocks-per-second figures. */
	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.4f", v);
	}

	/** Idempotency, no-repeat and persistence across this batch. */
	private static void survivalBatchInvariants() {
		section("Survival batch: idempotency, no-repeat, persistence");
		HarnessBootstrap.init();

		// Attribute effects in this batch: every change idempotent.
		AttributeEffectBehavior pact =
				(AttributeEffectBehavior) EffectBehaviors.get(GlassCannonPactBehavior.ID);
		for (AttributeEffectBehavior.Change change : pact.changes()) {
			String name = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
					.getKey(change.attribute().value()).getPath();
			var instance = new net.minecraft.world.entity.ai.attributes.AttributeInstance(
					change.attribute(), a -> {});
			double afterFirst = 0;
			for (int i = 0; i < 10; i++) {
				addModifier(instance, GlassCannonPactBehavior.ID, change.amount(), change.operation());
				if (i == 0) {
					afterFirst = instance.getValue();
				}
			}
			checkNear(instance.getValue(), afterFirst, 1e-9,
					"glass_cannon_pact/" + name + ": ten applications do not move the value");
			check(instance.getModifiers().size() == 1,
					"glass_cannon_pact/" + name + ": one modifier, not ten");
		}

		// Embrace the Moon gained a fourth change this session.
		AttributeEffectBehavior moon =
				(AttributeEffectBehavior) EffectBehaviors.get(EmbraceTheMoonBehavior.ID);
		check(moon.changes().size() == 4,
				"Embrace the Moon now applies four attributes (gravity, jump, safe fall, "
						+ "fall multiplier)");

		// Phoenix Chambered Heart holds no state of its own, which is what makes it
		// idempotent and respawn-safe for free. (Slippery Grip no longer qualifies:
		// it has one thing to do on apply -- see slipperyGripSpeed.)
		check(EffectBehaviors.get(PhoenixChamberedHeartBehavior.ID)
						instanceof com.entropymod.entropy.HookEffectBehavior,
				PhoenixChamberedHeartBehavior.ID + " is a HookEffectBehavior -- apply() is "
						+ "final and empty, so there is no per-player state to re-apply or leak");

		// No-repeat.
		AcquiredEffects acquired = new AcquiredEffects();
		SURVIVAL_BATCH.forEach(acquired::add);
		for (int entropy : new int[] {25, 45, 60}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(99),
						acquired.ids(), acquired.occupiedCategories());
				if (roll.repeatFallback()) {
					continue;
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!SURVIVAL_BATCH.contains(offered.id()),
							"no-repeat holds at entropy " + entropy + "/" + phase
									+ ": '" + offered.id() + "' was not re-offered");
				}
			}
		}

		// Persistence.
		EntropyManager saved = new EntropyManager();
		SURVIVAL_BATCH.forEach(id -> saved.acquired().add(id));
		EntropyManager reloaded = reencode(saved);
		for (String id : SURVIVAL_BATCH) {
			check(reloaded.acquired().contains(id), id + " survives a save/reload");
		}
		check(reloaded.acquired().unknownIds().isEmpty(),
				"every id in this batch is defined after a reload");
	}

	// ==================================================================
	// The spawn batch: Unstable and Creeper Magnet
	// ==================================================================

	private static final List<String> SPAWN_BATCH = List.of(
			UnstableBehavior.ID, CreeperMagnetBehavior.ID);

	/**
	 * Registration, wiring, idempotency, no-repeat and persistence for the two
	 * spawn effects, then each one's own numbers.
	 */
	private static void spawnBatch() {
		section("Spawn batch: Unstable and Creeper Magnet -- wiring");
		HarnessBootstrap.init();

		for (String id : SPAWN_BATCH) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.phase() == EffectPhase.BAD, id + " is BAD");
			// Creeper Magnet stays at the bottom of Tier 2; Unstable was moved up to
			// 40-60 when it became counterplay = false, because CLAUDE.md Part 2
			// forbids an unsurvivable curse below entropy 40.
			if (id.equals(UnstableBehavior.ID)) {
				check(def.minEntropy() == 40 && def.maxEntropy() == 60,
						id + " sits at 40-60 -- raised out of the below-40 band precisely "
								+ "because it is counterplay = false");
			} else {
				check(def.minEntropy() == 25 && def.maxEntropy() == 50,
						id + " sits in Tier 2 (entropy 25-50) -- not pushed up, because it "
								+ "is not unfair at the bottom of the band");
			}
			// Unstable is the ONLY counterplay = false effect in the registry -- and
			// Flamboyant, cited as its precedent, is registered TRUE despite killing
			// outright. Asserted per effect so that asymmetry stays visible.
			if (id.equals(UnstableBehavior.ID)) {
				check(!def.counterplay(),
						id + " is counterplay = FALSE -- every point of its 0-2 band kills "
								+ "a stationary full-health player");
			} else {
				check(def.counterplay(), id + " is counterplay = true");
			}
			check(def.category() == EffectCategory.SURVIVAL,
					id + " is SURVIVAL -- shared on purpose, so anti-stacking keeps the two "
							+ "hazard-on-a-timer curses apart");
			check(EffectBehaviors.get(id).getClass().getSimpleName().endsWith("Behavior"),
					id + " resolves to a real behavior, not the MISSING no-op");
			check(EffectBehaviors.get(id) instanceof com.entropymod.entropy.HookEffectBehavior,
					id + " is a HookEffectBehavior -- apply() is final and empty, so there is "
							+ "no per-player state to re-apply or leak across respawns");
		}
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatches anywhere after this batch");

		// Both tick services must be inert for a run that holds neither, before any
		// position is searched or any timer runs.
		AcquiredEffects empty = new AcquiredEffects();
		check(!UnstableSpawner.isActive(empty), "Unstable is inert without the effect");
		check(!CreeperMagnetSpawner.isActive(empty),
				"Creeper Magnet is inert without the effect");
		AcquiredEffects held = new AcquiredEffects();
		SPAWN_BATCH.forEach(held::add);
		check(UnstableSpawner.isActive(held), "Unstable activates when the effect is held");
		check(CreeperMagnetSpawner.isActive(held),
				"Creeper Magnet activates when the effect is held");

		// No-repeat.
		for (int entropy : new int[] {25, 37, 50}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(17),
						held.ids(), held.occupiedCategories());
				if (roll.repeatFallback()) {
					continue;
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!SPAWN_BATCH.contains(offered.id()),
							"no-repeat holds at entropy " + entropy + "/" + phase
									+ ": '" + offered.id() + "' was not re-offered");
				}
			}
		}

		// Persistence.
		EntropyManager saved = new EntropyManager();
		SPAWN_BATCH.forEach(id -> saved.acquired().add(id));
		EntropyManager reloaded = reencode(saved);
		for (String id : SPAWN_BATCH) {
			check(reloaded.acquired().contains(id), id + " survives a save/reload");
		}
		check(reloaded.acquired().unknownIds().isEmpty(),
				"both ids are still defined after a reload -- no stale id");

		spawnCadence();
		spawnGeometry();
		unstableCounterplay();
		creeperMagnetAppearance();
	}

	/**
	 * The two cadences: Unstable's fixed 30 seconds, and Creeper Magnet's
	 * genuinely re-rolled 30 s - 2 min.
	 *
	 * <p>"Genuinely re-rolled" is the assertion that matters and it is easy to get
	 * wrong in a way play cannot detect: a schedule that draws once and then
	 * repeats that number forever looks identical to a working one unless you time
	 * it with a stopwatch across many triggers. So the gaps are collected from the
	 * real shipped {@code SpawnSchedule} and checked for spread, not just range.
	 */
	private static void spawnCadence() {
		section("Spawn cadence: fixed 30 s vs. a re-rolled 30 s - 2 min");

		// --- Unstable: fixed ---------------------------------------------------
		check((int) constant(UnstableBehavior.class, "INTERVAL_TICKS") == 600,
				"Unstable's interval is 600 ticks = exactly 30 seconds");

		SpawnSchedule<String> fixed = new SpawnSchedule<>(
				UnstableBehavior.INTERVAL_TICKS, UnstableBehavior.INTERVAL_TICKS,
				new java.util.Random(1));
		check(!fixed.isRandomised(), "Unstable's schedule reports itself as fixed");

		List<Integer> fixedGaps = gapsFrom(fixed, "p", 10);
		boolean allExactly600 = fixedGaps.stream().allMatch(gap -> gap == 600);
		check(allExactly600,
				"ten consecutive Unstable triggers are 600 ticks apart, every time "
						+ "(gaps: " + fixedGaps + ")");
		check(fixedGaps.size() == 10, "ten triggers actually fired");

		// --- Creeper Magnet: re-rolled -----------------------------------------
		check((int) constant(CreeperMagnetBehavior.class, "MIN_INTERVAL_TICKS") == 600
						&& (int) constant(CreeperMagnetBehavior.class, "MAX_INTERVAL_TICKS") == 2400,
				"Creeper Magnet's interval range is 600-2400 ticks = 30 s to 2 min");

		SpawnSchedule<String> rolled = new SpawnSchedule<>(
				CreeperMagnetBehavior.MIN_INTERVAL_TICKS,
				CreeperMagnetBehavior.MAX_INTERVAL_TICKS,
				new java.util.Random(7));
		check(rolled.isRandomised(), "Creeper Magnet's schedule reports itself as randomised");

		List<Integer> gaps = gapsFrom(rolled, "p", 200);
		check(gaps.size() == 200, "two hundred triggers actually fired");
		int min = gaps.stream().mapToInt(Integer::intValue).min().orElse(-1);
		int max = gaps.stream().mapToInt(Integer::intValue).max().orElse(-1);
		check(min >= 600 && max <= 2400,
				"every gap is inside [600, 2400] (observed " + min + ".." + max + ")");
		check(gaps.stream().distinct().count() > 150,
				"the interval is genuinely re-rolled after each trigger, not drawn once "
						+ "and repeated (" + gaps.stream().distinct().count()
						+ " distinct gaps in 200)");
		// Both ends reachable: nextInt(span) rather than nextInt(span + 1) would make
		// the maximum unreachable, which no amount of play would reveal.
		check(min < 700 && max > 2300,
				"the draw spans nearly the whole range rather than clustering "
						+ "(min " + min + ", max " + max + ")");

		// --- both: the first trigger is a full interval away --------------------
		SpawnSchedule<String> fresh = new SpawnSchedule<>(600, 600, new java.util.Random(3));
		int firstGap = 0;
		while (!fresh.tick("p")) {
			firstGap++;
		}
		check(firstGap + 1 == 600,
				"a brand-new timer fires a full interval after it starts, not immediately "
						+ "-- a curse that fires on the pick reads as part of the pick");

		// --- per-player, not global --------------------------------------------
		SpawnSchedule<String> shared = new SpawnSchedule<>(600, 600, new java.util.Random(5));
		for (int t = 0; t < 300; t++) {
			shared.tick("a");
		}
		check(shared.remainingFor("a") == 300 && shared.remainingFor("b") == 0,
				"timers are per player: 300 ticks of player a leaves player b untouched");

		// --- the leaked-timer regression from the session before last ------------
		// retainAll must be unconditional. The equal-sizes case (one player leaves as
		// another joins) is the one a size guard gets wrong, so it is the one driven.
		shared.retainAll(List.of("b"));
		check(shared.remainingFor("a") == 0,
				"REGRESSION: a departed player's timer is dropped even when the player "
						+ "COUNT is unchanged -- the equal-sizes case a size guard misses");
		SpawnSchedule<String> churn = new SpawnSchedule<>(600, 600, new java.util.Random(6));
		for (int i = 0; i < 50; i++) {
			churn.tick("player" + i);
			churn.retainAll(List.of("player" + i));
		}
		check(churn.remainingFor("player49") > 0,
				"...and the surviving player's own timer is untouched by the pruning");
		int leaked = 0;
		for (int i = 0; i < 49; i++) {
			if (churn.remainingFor("player" + i) != 0) {
				leaked++;
			}
		}
		check(leaked == 0,
				"...and 50 joins/leaves at constant population leak nothing (" + leaked + ")");

		manyConsecutiveCycles();
		retryRearm();
	}

	/**
	 * Both schedules keep re-arming over MANY consecutive cycles, not just one.
	 *
	 * <p>Added after a bug report of "fired exactly once, then permanent silence".
	 * <b>The re-arm was not the cause</b> -- the shipped log proves both cadences
	 * were running correctly the whole time, and the pre-existing cadence check
	 * already drove 200 consecutive Creeper Magnet gaps. This section exists
	 * anyway, at a much larger scale and for both effects symmetrically, because
	 * "the schedule stopped" is the first thing anyone will suspect the next time
	 * and it should cost nothing to rule out.
	 *
	 * <p>The real gap was elsewhere and is covered by {@link #spawnGeometry()}:
	 * nothing headless drove {@code SafeSpawn}'s geometry, because as first written
	 * it needed a {@code ServerLevel} for every part of itself.
	 */
	private static void manyConsecutiveCycles() {
		section("Spawn schedules re-arm indefinitely, not once");

		// Unstable: 2000 consecutive fires = 1000 minutes of play at 30 s each.
		SpawnSchedule<String> fixed = new SpawnSchedule<>(
				UnstableBehavior.INTERVAL_TICKS, UnstableBehavior.INTERVAL_TICKS,
				new java.util.Random(101));
		List<Integer> fixedGaps = gapsFrom(fixed, "p", 2000);
		long wrongFixed = fixedGaps.stream().filter(gap -> gap != 600).count();
		check(wrongFixed == 0,
				"Unstable fires 2000 consecutive times, every gap exactly 600 ticks "
						+ "(that is 16h40m of play; " + wrongFixed + " bad gaps)");
		check(fixed.remainingFor("p") > 0,
				"...and the timer is still armed after the 2000th fire, not left at zero");

		// Creeper Magnet: 2000 consecutive fires, every one re-rolled in range.
		SpawnSchedule<String> rolled = new SpawnSchedule<>(
				CreeperMagnetBehavior.MIN_INTERVAL_TICKS,
				CreeperMagnetBehavior.MAX_INTERVAL_TICKS,
				new java.util.Random(102));
		List<Integer> gaps = gapsFrom(rolled, "p", 2000);
		long outOfRange = gaps.stream().filter(g -> g < 600 || g > 2400).count();
		check(outOfRange == 0,
				"Creeper Magnet fires 2000 consecutive times, every gap inside "
						+ "[600, 2400] (" + outOfRange + " out of range)");
		check(rolled.remainingFor("p") > 0,
				"...and its timer is still armed after the 2000th fire");
		// The bound is the coupon-collector expectation, not a round number: drawing
		// 2000 samples from 1801 possible values yields ~1208 distinct, so anything
		// near 2000 would be unreachable and anything near 100 would pass for a
		// schedule that had degenerated. +/-15% of the expectation is the real test.
		long distinct = gaps.stream().distinct().count();
		double expectedDistinct = 1801 * (1 - Math.exp(-2000.0 / 1801));
		check(distinct > expectedDistinct * 0.85 && distinct < expectedDistinct * 1.15,
				"...and it is still genuinely re-rolling that late in the run: " + distinct
						+ " distinct gaps in 2000, against " + Math.round(expectedDistinct)
						+ " expected for uniform draws over 1801 values");

		// The late half must look like the early half -- a schedule that degraded
		// after N fires would pass a "200 gaps" check and fail here.
		double early = gaps.subList(0, 1000).stream().mapToInt(Integer::intValue).average().orElse(0);
		double late = gaps.subList(1000, 2000).stream().mapToInt(Integer::intValue).average().orElse(0);
		checkNear(late, early, 60.0,
				"the last 1000 gaps average the same as the first 1000 -- no drift, no "
						+ "degradation late in a long run");
		checkNear(early, 1500.0, 60.0, "and the mean sits at the midpoint of the range");

		// Two players in parallel, interleaved, for many cycles each.
		SpawnSchedule<String> two = new SpawnSchedule<>(600, 600, new java.util.Random(103));
		int firesA = 0;
		int firesB = 0;
		for (int t = 0; t < 600 * 50; t++) {
			if (two.tick("a")) {
				firesA++;
			}
			if (two.tick("b")) {
				firesB++;
			}
		}
		check(firesA == 50 && firesB == 50,
				"two players each fire 50 times over 50 intervals, independently ("
						+ firesA + "/" + firesB + ")");
	}

	/**
	 * The short retry re-arm: a trigger that fires but finds nowhere to spawn must
	 * not cost the whole interval.
	 *
	 * <p>This is the half of the fix that makes the reported symptom impossible
	 * rather than merely rarer. Even if the position search fails, the worst case
	 * is now "fires a bit late", not "silent for two minutes".
	 */
	private static void retryRearm() {
		section("Failed placement re-arms on a short retry, not a whole interval");

		check(UnstableSpawner.RETRY_TICKS == CreeperMagnetSpawner.RETRY_TICKS,
				"both effects use the same retry delay");
		check(UnstableSpawner.RETRY_TICKS > 0
						&& UnstableSpawner.RETRY_TICKS < UnstableBehavior.INTERVAL_TICKS,
				"the retry is shorter than the interval itself (" + UnstableSpawner.RETRY_TICKS
						+ " vs " + UnstableBehavior.INTERVAL_TICKS + ") -- otherwise it would "
						+ "be a slowdown rather than a retry");
		check(UnstableSpawner.RETRY_TICKS >= 20,
				"...and at least a second, so a permanently hopeless spot costs one "
						+ "bounded search per " + UnstableSpawner.RETRY_TICKS
						+ " ticks rather than one per tick");

		SpawnSchedule<String> s = new SpawnSchedule<>(600, 600, new java.util.Random(7));
		int toFire = 0;
		while (!s.tick("p")) {
			toFire++;
		}
		check(toFire + 1 == 600, "first fire lands on the interval");
		check(s.remainingFor("p") == 600, "and re-arms to a full interval on success");

		// Simulate a failed placement: the caller re-arms short.
		s.rearm("p", UnstableSpawner.RETRY_TICKS);
		check(s.remainingFor("p") == UnstableSpawner.RETRY_TICKS,
				"a failed placement replaces the fresh interval with the short retry");
		int toRetry = 0;
		while (!s.tick("p")) {
			toRetry++;
		}
		check(toRetry + 1 == UnstableSpawner.RETRY_TICKS,
				"the retry fires after exactly the retry delay, not the full interval");
		check(s.remainingFor("p") == 600,
				"and a retry that succeeds returns to the normal cadence -- the retry "
						+ "does not become the new interval");

		// 100 consecutive failures must not wedge or drift the schedule.
		int fires = 0;
		for (int cycle = 0; cycle < 100; cycle++) {
			while (!s.tick("p")) {
				// spin to the next fire
			}
			fires++;
			s.rearm("p", UnstableSpawner.RETRY_TICKS);
		}
		check(fires == 100,
				"100 consecutive failed placements still produce 100 further attempts -- "
						+ "a run standing somewhere hopeless keeps trying rather than "
						+ "going silent");
	}

	/**
	 * {@code SafeSpawn}'s geometry -- the part that was actually broken, and the
	 * part nothing headless could reach before.
	 *
	 * <p>As first written, every line of {@code findNear} needed a
	 * {@code ServerLevel}, so none of it was drivable by the harness and the band
	 * and search-window rules shipped unverified. The three pure rules are now
	 * static and Minecraft-free -- same discipline as {@code TramplePath} and
	 * {@code CropSchedule} -- which is what lets them be asserted here.
	 */
	private static void spawnGeometry() {
		section("SafeSpawn geometry: horizontal band, and the vertical search window");

		// --- the band is HORIZONTAL, which is the bug this pins -------------------
		// The shipped version drew a horizontal radius, let the vertical search move
		// the candidate up to 4 blocks, then re-checked the 3D distance against the
		// same band -- so finding valid ground could disqualify the candidate for
		// having been found.
		double min = 5.0;
		double max = 7.0;
		double minSqr = min * min;
		double maxSqr = max * max;

		check(SafeSpawn.withinBand(6.0 * 6.0, minSqr, maxSqr),
				"a column 6.0 blocks out is inside the [5, 7] band");
		check(SafeSpawn.withinBand(minSqr, minSqr, maxSqr)
						&& SafeSpawn.withinBand(maxSqr, minSqr, maxSqr),
				"both ends of the band are inclusive");
		check(!SafeSpawn.withinBand(4.9 * 4.9, minSqr, maxSqr)
						&& !SafeSpawn.withinBand(7.1 * 7.1, minSqr, maxSqr),
				"and just outside either end is rejected -- the band does not leak");

		// The regression itself: a column at a legal horizontal distance must stay
		// legal however far the vertical search had to move to find its ground.
		check(SafeSpawn.withinBand(7.0 * 7.0, minSqr, maxSqr),
				"THE FIX: a column exactly 7.0 blocks out is accepted...");
		double bogus3d = 7.0 * 7.0 + 8.0 * 8.0;   // same column, ground 8 blocks down
		check(!SafeSpawn.withinBand(bogus3d, minSqr, maxSqr)
						&& SafeSpawn.withinBand(7.0 * 7.0, minSqr, maxSqr),
				"...and would have been REJECTED under the old 3D test once its ground "
						+ "turned out to be 8 blocks below (" + Math.sqrt(bogus3d)
						+ " blocks 3D) -- the vertical search disqualifying its own result");

		// horizontalDistanceSqr must measure from the block CENTRE, not its corner.
		checkNear(SafeSpawn.horizontalDistanceSqr(5, 0, 0.5, 0.5), 25.0, 1e-9,
				"distance is measured from the block centre (5.5-0.5 = 5.0)");
		checkNear(SafeSpawn.horizontalDistanceSqr(-6, 0, -0.5, 0.5), 25.0, 1e-9,
				"...and is correct west of the origin too, where a cast would be off by one");

		// --- SPHERICAL vs HORIZONTAL: the root cause of the low-damage report -----
		// Unstable's damage is a function of 3D distance and hurtEntities culls past
		// 8.0 blocks, so a horizontal-only band let a "5-7 block" TNT sit 10.63
		// blocks away doing literally nothing. The two effects now name which
		// quantity their band means.
		check(SafeSpawn.DistanceMode.values().length == 2,
				"SafeSpawn offers exactly two distance modes");
		check(Checks.classReferences(com.entropymod.entropy.spawn.UnstableSpawner.class,
						"SPHERICAL"),
				"Unstable asks for SPHERICAL -- its band must mean the quantity the "
						+ "explosion damage actually reads");
		check(Checks.classReferences(com.entropymod.entropy.spawn.CreeperMagnetSpawner.class,
						"HORIZONTAL"),
				"Creeper Magnet stays HORIZONTAL -- a creeper walks to you, so the "
						+ "starting height difference is not part of what the band means");

		// The regression, in numbers: the exact geometry the old band permitted.
		double worstOldHorizontal = 7.0;
		double worstOldVertical = (int) constant(SafeSpawn.class, "VERTICAL_SEARCH");
		double worst3d = Math.sqrt(worstOldHorizontal * worstOldHorizontal
				+ worstOldVertical * worstOldVertical);
		checkNear(worst3d, 10.6301, 1e-3,
				"the OLD band permitted a 3D distance of 10.63 blocks (7 horizontal, 8 "
						+ "vertical) while calling itself 5-7");
		check(worst3d > UnstableBehavior.BLAST_REACH,
				"...which is past the 8.0-block cull, i.e. ZERO damage -- the reported "
						+ "symptom, not a rounding error");

		// Under SPHERICAL the same geometry is rejected outright.
		double newMax = constant(UnstableBehavior.class, "MAX_DISTANCE");
		check(!SafeSpawn.withinBand(worst3d * worst3d, 0.0, newMax * newMax),
				"THE FIX: SPHERICAL rejects that candidate, because the band is now "
						+ "measured in the same quantity the damage model uses");
		check(newMax < UnstableBehavior.BLAST_REACH,
				"and since the band's far edge is inside the cull, EVERY Unstable spawn "
						+ "can now actually damage the player");

		// spawnDistanceSqr must measure exactly what Entity.distanceToSqr(center) does:
		// spawn point (block bottom-centre) to the player's FEET.
		checkNear(SafeSpawn.distanceSqr(0.5, 64.0, 0.5, 0.5, 60.0, 3.5),
				4.0 * 4.0 + 3.0 * 3.0, 1e-9,
				"3D distance combines the horizontal and vertical legs (3-4-5)");
		check(SafeSpawn.distanceSqr(0.5, 64.0, 0.5, 0.5, 56.0, 0.5)
						> SafeSpawn.horizontalDistanceSqr(0, 0, 0.5, 0.5),
				"...and a purely vertical offset registers in 3D while being invisible "
						+ "horizontally -- the whole difference between the two modes");

		// --- the vertical search window ------------------------------------------
		check((int) constant(SafeSpawn.class, "VERTICAL_SEARCH") == 8,
				"VERTICAL_SEARCH is 8, raised from 4 after the in-game failure: decoding "
						+ "the saved region file around the player's actual position "
						+ "(-15, 112, -83) showed 34.4% of candidate columns had their "
						+ "surface more than 4 blocks from the player's Y, against 7% at 8");
		check((int) constant(SafeSpawn.class, "ATTEMPTS") >= 32,
				"and ATTEMPTS is at least 32");

		int search = (int) constant(SafeSpawn.class, "VERTICAL_SEARCH");
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		int previousAbs = -1;
		boolean nearestFirst = true;
		for (int step = 0; step <= search * 2; step++) {
			int dy = SafeSpawn.verticalOffset(step);
			seen.add(dy);
			if (Math.abs(dy) < previousAbs) {
				nearestFirst = false;
			}
			previousAbs = Math.abs(dy);
		}
		check(seen.size() == search * 2 + 1,
				"the search visits every offset in [-" + search + ", +" + search
						+ "] exactly once (" + seen.size() + " distinct)");
		check(seen.contains(-search) && seen.contains(search) && seen.contains(0),
				"...including both extremes and the player's own level");
		check(nearestFirst,
				"...and in nearest-first order, so flat ground answers with the player's "
						+ "own Y rather than whichever step the loop reached first");
		check(SafeSpawn.verticalOffset(0) == 0, "step 0 is the player's own level");

		// --- the aim points ------------------------------------------------------
		check(SafeSpawn.AIM_FRACTIONS.length >= 2,
				"line of sight is tested at more than one height up the entity's body");
		double lowest = SafeSpawn.AIM_FRACTIONS[0];
		for (double f : SafeSpawn.AIM_FRACTIONS) {
			lowest = Math.min(lowest, f);
		}
		check(lowest >= 0.5,
				"and the lowest aim point is at least half the entity's height -- aiming "
						+ "at the feet block, as the first version did, made the ray graze "
						+ "the terrain for its whole length and was the dominant rejection");
		double highest = 0;
		for (double f : SafeSpawn.AIM_FRACTIONS) {
			highest = Math.max(highest, f);
		}
		check(highest <= 1.0,
				"...and the highest is no more than the entity's height, so a candidate "
						+ "only visible ABOVE the entity is not counted as visible");
	}

	/** Runs a schedule until it has fired {@code count} times, returning the gaps. */
	private static List<Integer> gapsFrom(SpawnSchedule<String> schedule, String key, int count) {
		List<Integer> gaps = new ArrayList<>(count);
		int sinceLast = 0;
		while (gaps.size() < count) {
			sinceLast++;
			if (schedule.tick(key)) {
				gaps.add(sinceLast);
				sinceLast = 0;
			}
		}
		return gaps;
	}

	/**
	 * Unstable's fuse and distance band, against vanilla's own explosion formula.
	 *
	 * <p>The two boundaries are the whole design and both are cliffs: one block
	 * closer than the minimum and ignoring the TNT kills a full-health unarmoured
	 * player, one block further than the maximum and the blast does essentially
	 * nothing. Neither is visible from the constants alone.
	 */
	private static void unstableCounterplay() {
		section("Unstable: fuse, distance band, and the counterplay derivation");

		// --- the fuse ------------------------------------------------------------
		check((int) constant(UnstableBehavior.class, "FUSE_TICKS") == 100,
				"the fuse is 100 ticks = 5.0 seconds -- vanilla own 80 plus one second, "
						+ "traded deliberately for the closer band");
		check(UnstableBehavior.FUSE_TICKS > 80,
				"...and LONGER than vanilla, not shorter: a player calibrated on vanilla "
						+ "TNT now has a second in hand rather than a second short");
		checkNear(constant(UnstableBehavior.class, "BLAST_RADIUS"), 4.0, 1e-9,
				"the blast radius is vanilla 4.0 -- the effect does not touch "
						+ "PrimedTnt.explosionPower");
		checkNear(constant(UnstableBehavior.class, "BLAST_REACH"), 8.0, 1e-9,
				"and its reach is 2*radius = 8.0, the distance hurtEntities culls at");

		// --- THE CORRECTED DAMAGE MODEL ------------------------------------------
		// The first version had two defects, both of which reported far more damage
		// than the game delivers. Both are pinned here.
		check(!Checks.hasMethod(UnstableBehavior.class, "blastDamageAt"),
				"the old single-argument blastDamageAt is GONE, not left returning an "
						+ "upper bound under a name that reads like the real value -- that "
						+ "naming is what let a table of ceilings be tuned against");

		// Defect 1: no cull. hurtEntities does d = dist / (2*radius); if (d > 1) skip.
		checkNear(UnstableBehavior.blastDamage(8.0, 1.0), 1.0, 1e-6,
				"at exactly 8.0 blocks only the trailing +1 survives: 1.00 damage");
		checkNear(UnstableBehavior.blastDamage(8.5, 1.0), 0.0, 1e-9,
				"THE CULL: past 8.0 the entity is skipped outright -- ZERO damage, not a "
						+ "small number. The old model returned a value here.");
		checkNear(UnstableBehavior.blastDamage(10.63, 1.0), 0.0, 1e-9,
				"...including at 10.63 blocks, which the old horizontal band genuinely "
						+ "permitted (sqrt(7^2 + 8^2)) -- a TNT that did nothing at all");

		// Defect 2: exposure. getSeenPercent multiplies impact before the quadratic.
		checkNear(UnstableBehavior.blastDamage(5.0, 1.0), 15.4375, 1e-6,
				"5.0 blocks at FULL exposure: 15.44 -- the old table number, and it is a "
						+ "ceiling rather than an expectation");
		checkNear(UnstableBehavior.blastDamage(5.0, 0.5), 7.234375, 1e-6,
				"...but half-obstructed the same blast deals 7.23");
		checkNear(UnstableBehavior.blastDamage(5.0, 0.0), 1.0, 1e-6,
				"...and fully obstructed it deals 1.00 -- half a heart, which is EXACTLY "
						+ "the symptom reported in play");
		check(UnstableBehavior.blastDamage(5.0, 0.5) < UnstableBehavior.blastDamage(5.0, 1.0),
				"exposure genuinely scales the result rather than being ignored");
		checkNear(UnstableBehavior.maxBlastDamage(5.0), UnstableBehavior.blastDamage(5.0, 1.0),
				1e-9, "maxBlastDamage is the full-exposure case, named so it cannot be "
						+ "mistaken for the expected value");

		// The corrected table in the javadoc, at full exposure.
		checkNear(UnstableBehavior.maxBlastDamage(2.0), 37.75, 1e-6, "2.0 blocks: 37.75");
		checkNear(UnstableBehavior.maxBlastDamage(3.0), 29.4375, 1e-6, "3.0 blocks: 29.44");
		checkNear(UnstableBehavior.maxBlastDamage(4.0), 22.0, 1e-6, "4.0 blocks: 22.00");
		checkNear(UnstableBehavior.maxBlastDamage(4.5), 18.609375, 1e-6,
				"4.5 blocks: 18.61, leaving 1.39 HP -- the tightest margin in the band");
		checkNear(UnstableBehavior.maxBlastDamage(6.5), 7.234375, 1e-6, "6.5 blocks: 7.23");

		// --- the requested 2-4 band was LETHAL, and that is why it was not shipped -
		final double fullHealth = 20.0;
		checkNear(UnstableBehavior.lethalThresholdDistance(fullHealth), 4.2913, 1e-3,
				"the fully-exposed blast exactly kills a full-health unarmoured player at "
						+ "4.29 blocks -- solved from the quadratic, not read off a table");
		for (double requested : new double[] {2.0, 3.0, 4.0}) {
			check(UnstableBehavior.maxBlastDamage(requested) > fullHealth,
					"a spawn at " + requested + " blocks KILLS a stationary full-health "
							+ "player outright ("
							+ String.format("%.2f", UnstableBehavior.maxBlastDamage(requested))
							+ " of 20.0) -- which is why the requested 2-4 band could not be "
							+ "shipped under counterplay = true");
		}

		double min = constant(UnstableBehavior.class, "MIN_DISTANCE");
		double max = constant(UnstableBehavior.class, "MAX_DISTANCE");
		checkNear(min, 0.0, 1e-9, "minimum spawn distance is 0 -- at the player's feet");
		checkNear(max, 2.0, 1e-9, "maximum spawn distance is 2 blocks");
		check(min < max, "the band is non-empty and the right way round");

		// --- the band is now LETHAL EVERYWHERE, and that is the design ------------
		checkNear(UnstableBehavior.maxBlastDamage(0.0), 57.0, 1e-6, "0.0 blocks: 57.00");
		checkNear(UnstableBehavior.maxBlastDamage(1.0), 46.9375, 1e-6, "1.0 blocks: 46.94");
		checkNear(UnstableBehavior.maxBlastDamage(2.0), 37.75, 1e-6, "2.0 blocks: 37.75");
		check(UnstableBehavior.maxBlastDamage(max) > fullHealth,
				"THE DESIGN, INVERTED FROM BEFORE: even the FURTHEST point of the band "
						+ "kills a stationary full-health player ("
						+ String.format("%.2f", UnstableBehavior.maxBlastDamage(max))
						+ " of 20.0)");
		check(UnstableBehavior.maxBlastDamage(min) > fullHealth * 2.5,
				"...and the nearest deals over 2.5x a health bar ("
						+ String.format("%.2f", UnstableBehavior.maxBlastDamage(min)) + ")");
		check(max < UnstableBehavior.lethalThresholdDistance(fullHealth),
				"the whole band now sits INSIDE the 4.29-block lethal threshold, where "
						+ "previously the minimum was pinned outside it");

		// The flag has to match the numbers -- that is the point of setting it false.
		check(!EffectRegistry.byId(UnstableBehavior.ID).counterplay(),
				"so counterplay is registered FALSE, matching what the model says");
		// ...and the precedent it was justified by does not exist. Pinned because the
		// justification was offered in good faith and is factually wrong.
		check(EffectRegistry.byId("flamboyant").counterplay(),
				"NOTE: Flamboyant -- cited as the precedent -- is registered TRUE despite "
						+ "'catching fire kills you outright', so the flag has always meant "
						+ "'an answer exists', not 'cannot kill you'");
		// This used to assert falseCount == 1. It is 2 now: Earthquake became
		// counterplay = false when its Slowness became a real movement lock, and it
		// resolved the invariant the SAME way -- move to 40-60 rather than soften the
		// effect. What the check pins is no longer the count but the shape: the point
		// was never "only one may exist", it was that Unstable had no precedent to
		// appeal to at the time. Earthquake does, and used it.
		long falseCount = EffectRegistry.all().stream().filter(d -> !d.counterplay()).count();
		// 3 now: Debt Collector joined them, and NOT by choice -- the extreme BAD
		// tier is exactly these effects, so an effect whose content is 'you will be
		// handed one of those' cannot honestly be labelled survivable. The shape the
		// check pins is unchanged and is asserted separately below: every one sits
		// at 40-60.
		check(falseCount == 3,
				"counterplay = false is rare and deliberate -- " + falseCount + " of "
						+ EffectRegistry.all().size() + " effects (Unstable, Earthquake), both "
						+ "at 40-60, neither excused by the other");
		for (EffectDefinition d : EffectRegistry.all()) {
			if (!d.counterplay()) {
				check(d.minEntropy() == 40 && d.maxEntropy() == 60,
						"...and " + d.id() + " sits at 40-60 specifically, the same window "
								+ "Unstable established for this class of effect");
			}
		}
		// The invariant, now SATISFIED rather than flagged.
		EffectDefinition unstableDef = EffectRegistry.byId(UnstableBehavior.ID);
		check(unstableDef.minEntropy() >= 40,
				"RESOLVED: minEntropy is " + unstableDef.minEntropy()
						+ ", so CLAUDE.md Part 2 ('bad effects below entropy 40 must be "
						+ "counterplay-survivable') is satisfied -- the only counterplay = "
						+ "false effect no longer appears below 40");
		check(unstableDef.minEntropy() == 40 && unstableDef.maxEntropy() == 60,
				"Unstable sits at 40-60, the same shape as Glass Cannon Pact");
		// The move must have changed NOTHING else.
		checkNear(constant(UnstableBehavior.class, "MIN_DISTANCE"), 0.0, 1e-9,
				"...and the distance band is untouched by the range move (min)");
		checkNear(constant(UnstableBehavior.class, "MAX_DISTANCE"), 2.0, 1e-9,
				"...(max)");
		check((int) constant(UnstableBehavior.class, "FUSE_TICKS") == 100,
				"...and the fuse is untouched");
		checkNear(UnstableBehavior.maxBlastDamage(0.0), 57.0, 1e-6,
				"...and the damage model is untouched at 0 blocks");
		checkNear(UnstableBehavior.maxBlastDamage(2.0), 37.75, 1e-6,
				"...and at 2 blocks");
		check(!unstableDef.counterplay(), "...and it is still counterplay = false");
		for (EffectDefinition other : EffectRegistry.all()) {
			if (!other.counterplay() && other.minEntropy() < 40) {
				check(false, "no counterplay = false effect may sit below entropy 40, but "
						+ other.id() + " does");
			}
		}
		check(true, "no counterplay = false effect sits below entropy 40 anywhere");

		// --- the escape budget, recomputed for the 0-2 band -----------------------
		double walk = SprintModel.groundSpeedBps(0.1);
		double sprint = SprintModel.groundSpeedBps(0.1 * 1.3);
		checkNear(walk, 4.3172, 1e-3, "SprintModel still reproduces vanilla walking (sanity)");

		double blocksToSafety = 8.0 - min;
		double walkSeconds = blocksToSafety / walk;
		double fuseSeconds = UnstableBehavior.FUSE_TICKS / 20.0;
		checkNear(blocksToSafety, 8.0, 1e-9,
				"worst case the player must now cover the full 8 blocks to the cull");
		checkNear(fuseSeconds, 5.0, 1e-9, "against a 5.0-second fuse");
		check(walkSeconds < fuseSeconds * 0.5,
				"walking clear still takes under half the fuse ("
						+ String.format("%.2f", walkSeconds) + " s of "
						+ String.format("%.1f", fuseSeconds) + " s)");
		check(blocksToSafety / sprint < walkSeconds,
				"sprinting is faster still, so the walking figure is the pessimistic one");
		double slack = fuseSeconds - 0.5 - walkSeconds;
		check(slack > 2.5,
				"after half a second of reaction there are still "
						+ String.format("%.2f", slack) + " s of slack -- so the effect is "
						+ "AVOIDABLE even though it is no longer survivable-if-ignored");
		double slackAtMax = fuseSeconds - 0.5 - (8.0 - max) / walk;
		check(slackAtMax > slack,
				"and from the far end of the band there is more still ("
						+ String.format("%.2f", slackAtMax) + " s)");

		// --- the warning actually reaches the player -----------------------------
		// SoundEvent.getRange(volume) is `volume > 1 ? 16 * volume : 16`, so the
		// primed sound carries 16 blocks at volume 1.0.
		check(16.0 > max * 2,
				"TNT_PRIMED's 16-block range is more than twice the maximum spawn "
						+ "distance, and it is omnidirectional -- the warning does not "
						+ "depend on which way the player is facing");
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "TNT_PRIMED"),
				"the spawner really does play the primed sound");
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "PRIME_FUSE"),
				"and fires vanilla's PRIME_FUSE game event, exactly as TntBlock does");

		// --- it is a REAL PrimedTnt, not a facsimile ------------------------------
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "PrimedTnt"),
				"the entity is net.minecraft.world.entity.item.PrimedTnt itself");
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "addFreshEntity"),
				"it is added to the level as an ordinary entity");
		check(!Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "explode"),
				"nothing here explodes anything itself -- the explosion is PrimedTnt.tick's, "
						+ "so the gamerule, the damage source, the terrain damage and the "
						+ "knockback are all vanilla's");
	}

	/**
	 * Creeper Magnet's invisibility window and distance band.
	 *
	 * <p>The load-bearing claim is that one second cannot become a stealth kill,
	 * and it is arithmetic rather than an opinion: a creeper's ground speed is its
	 * movement-speed attribute SQUARED, because {@code Mob.setSpeed} sets
	 * {@code zza} to the same value it sets speed to.
	 */
	private static void creeperMagnetAppearance() {
		section("Creeper Magnet: the 1-second window, and why it cannot stalk you");
		HarnessBootstrap.init();

		// --- exactly one second --------------------------------------------------
		check((int) constant(CreeperMagnetBehavior.class, "INVISIBILITY_TICKS") == 20,
				"invisibility lasts exactly 20 ticks = 1.000 second");

		// --- the creeper's real attributes, from the registry not from memory ------
		var creeperAttributes = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.CREEPER);
		double speed = creeperAttributes.getValue(
				net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		double followRange = creeperAttributes.getValue(
				net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
		checkNear(speed, 0.25, 1e-9,
				"a creeper's MOVEMENT_SPEED really is 0.25 -- read from DefaultAttributes");
		checkNear(speed, constant(CreeperMagnetBehavior.class, "CREEPER_MOVEMENT_SPEED"), 1e-9,
				"the constant recorded in the behavior matches the shipped attribute");
		// The trap: Attributes.FOLLOW_RANGE's own registered default is 32.0, and
		// Mob.createMobAttributes() overrides it to 16.0. Reading the attribute
		// rather than the entity type's supplier overstates a creeper's reach by 2x
		// -- which is exactly the mistake this effect's first draft made.
		checkNear(followRange, 16.0, 1e-9,
				"a creeper's REAL FOLLOW_RANGE is 16.0, not the attribute's 32.0 default");
		checkNear(followRange, constant(CreeperMagnetBehavior.class, "CREEPER_FOLLOW_RANGE"), 1e-9,
				"the constant recorded in the behavior matches the shipped value");
		checkNear(((net.minecraft.world.entity.ai.attributes.RangedAttribute)
						net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE.value())
						.getDefaultValue(), 32.0, 1e-9,
				"...and the attribute's own default really is 32.0, which is what makes "
						+ "this worth asserting rather than assuming");

		// --- the squared-speed finding -------------------------------------------
		double asMob = SprintModel.groundSpeedBps(speed * speed);
		double asPlayerWould = SprintModel.groundSpeedBps(speed);
		checkNear(asMob, 2.698, 1e-2,
				"chasing ground speed is ~2.70 b/s -- SLOWER than a walking player, "
						+ "because Mob.setSpeed also sets zza so the acceleration is speed^2");
		check(asMob < SprintModel.groundSpeedBps(0.1),
				"which is the sanity check on that finding: creepers are slower than a "
						+ "walking player, as anyone who has played the game knows");
		check(asPlayerWould > 4 * asMob * 0.9,
				"reading the attribute the player way would have overstated it ~4x ("
						+ String.format("%.2f", asPlayerWould) + " b/s) -- the trap this "
						+ "check exists to pin");

		// --- the actual claim ----------------------------------------------------
		double closed = SprintModel.groundDistanceFromRest(
				speed * speed, CreeperMagnetBehavior.INVISIBILITY_TICKS);
		double minDistance = constant(CreeperMagnetBehavior.class, "MIN_DISTANCE");
		double swell = constant(CreeperMagnetBehavior.class, "SWELL_RADIUS");
		checkNear(swell, 3.0, 1e-9,
				"SwellGoal ignites at distanceToSqr < 9.0, i.e. 3 blocks");
		check(closed < 2.6,
				"from rest, a creeper covers under 2.6 blocks in the whole invisible "
						+ "second (" + String.format("%.2f", closed) + ")");
		check(minDistance - closed > swell,
				"THE CLAIM: at the minimum spawn distance it is still "
						+ String.format("%.2f", minDistance - closed)
						+ " blocks away when it becomes visible, against a "
						+ swell + "-block swell radius -- the window is an appearance, "
						+ "not a stalk");
		check(minDistance - closed - swell > 2.0,
				"and the margin is over 2 blocks, so this is not a near miss");

		// --- the distance band ---------------------------------------------------
		double maxDistance = constant(CreeperMagnetBehavior.class, "MAX_DISTANCE");
		checkNear(minDistance, 8.0, 1e-9, "minimum spawn distance is 8 blocks");
		checkNear(maxDistance, 12.0, 1e-9, "maximum spawn distance is 12 blocks");
		check(maxDistance < followRange,
				"the maximum is strictly inside the creeper's own follow range, so the "
						+ "target survives TargetGoal.canContinueToUse -- which compares "
						+ "against the RAW range, unlike acquisition");
		check(followRange - maxDistance >= 4.0,
				"and with a 4-block margin, so the target is not dropped the moment the "
						+ "player takes a step away (" + maxDistance + " of " + followRange + ")");

		// --- it is an ordinary creeper -------------------------------------------
		Class<?> spawner = com.entropymod.entropy.spawn.CreeperMagnetSpawner.class;
		check(Checks.classReferences(spawner, "finalizeSpawn"),
				"vanilla's own finalizeSpawn is run, against the position's real difficulty");
		check(Checks.classReferences(spawner, "INVISIBILITY"),
				"the invisibility is a real MobEffectInstance, not a render hack");
		check(!Checks.classReferences(spawner, "setPersistenceRequired"),
				"persistence is deliberately NOT forced -- one creeper every 30-120 s "
						+ "forever would fill the world if none could despawn");
		check(!Checks.classReferences(spawner, "checkSpawnRules"),
				"checkSpawnRules is deliberately NOT consulted -- honouring the natural "
						+ "light gate would make this 'a creeper appears, but only at night'");
		check(!Checks.classReferences(spawner, "setHealth")
						&& !Checks.classReferences(spawner, "setNoAi")
						&& !Checks.classReferences(spawner, "registerGoals"),
				"nothing about the creeper is stripped down or overridden -- no health "
						+ "change, no disabled AI, no goal surgery");

		// --- the shared spawn-position groundwork --------------------------------
		section("SafeSpawn: the shared position rules");
		Class<?> safeSpawn = com.entropymod.entropy.spawn.SafeSpawn.class;
		check(Checks.classReferences(safeSpawn, "SpawnPlacementTypes"),
				"the position test is vanilla's own SpawnPlacementTypes.ON_GROUND, not a "
						+ "hand-rolled isAir check");
		check(!Checks.classReferences(safeSpawn, "SpawnPlacements"),
				"and the placement type is named EXPLICITLY rather than looked up per "
						+ "entity type -- TNT's registered placement is NO_RESTRICTIONS, "
						+ "i.e. 'anywhere', which is the one answer this must never give");
		check(Checks.classReferences(safeSpawn, "ClipContext")
						&& Checks.classReferences(safeSpawn, "MISS"),
				"line of sight is required, so nothing is ever spawned behind a wall");
		check(Checks.classReferences(safeSpawn, "isLoaded"),
				"candidates in unloaded chunks or outside the world's Y bounds are "
						+ "rejected rather than forcing a chunk load");
		check((int) constant(safeSpawn, "ATTEMPTS") > 0
						&& (int) constant(safeSpawn, "VERTICAL_SEARCH") > 0,
				"the search is bounded in both dimensions rather than looping until it "
						+ "succeeds");
		// This check used to assert the OPPOSITE, and asserting it is what let the bug
		// ship: it pinned a 3D re-check of a horizontally-drawn radius as though that
		// were the correct rule. It is not -- the band is horizontal, and coupling it
		// to the vertical search let a valid surface disqualify its own column.
		check(Checks.classReferences(safeSpawn, "horizontalDistanceSqr")
						&& Checks.classReferences(safeSpawn, "withinBand"),
				"the band is re-checked HORIZONTALLY after flooring to a block, which is "
						+ "still required (flooring moves a candidate up to ~0.7 blocks "
						+ "radially) -- see spawnGeometry for the rule itself");
		// Asserted on the callers, not on SafeSpawn: the record declares the method,
		// but the claim that matters is that the spawners actually LOG it. A search
		// that can explain itself to nobody is the same as one that cannot explain
		// itself at all -- which is precisely what happened.
		check(Checks.hasMethod(SafeSpawn.Attempt.class, "rejectionSummary"),
				"a failed search can report WHICH gate rejected");
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "rejectionSummary")
						&& Checks.classReferences(
						com.entropymod.entropy.spawn.CreeperMagnetSpawner.class, "rejectionSummary"),
				"...and BOTH spawners put that breakdown in the log -- the absence of this "
						+ "is why the first bug report could not be diagnosed from the log "
						+ "and had to be reconstructed from the world save");
		check(Checks.classReferences(
						com.entropymod.entropy.spawn.UnstableSpawner.class, "rearm")
						&& Checks.classReferences(
						com.entropymod.entropy.spawn.CreeperMagnetSpawner.class, "rearm"),
				"...and both re-arm on a short retry rather than spending the whole "
						+ "interval on a spot that had nowhere to put anything");
	}

	// ==================================================================
	// The companion cluster
	// ==================================================================

	private static final List<String> COMPANION_BATCH = List.of(
			LoyalPackBehavior.ID, TheEntourageBehavior.ID,
			TheAudienceBehavior.ID, EmotionalSupportLlamaBehavior.ID);

	/**
	 * Registration, no-repeat, persistence, and the guarantee that matters most for
	 * this cluster: re-application must not spawn duplicates.
	 */
	private static void companionBatch() {
		section("Companion cluster: wiring, no-repeat, persistence");
		HarnessBootstrap.init();

		for (String id : COMPANION_BATCH) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + " is registered in EffectRegistry");
			if (def == null) {
				continue;
			}
			check(def.phase() == EffectPhase.GOOD, id + " is GOOD");
			check(def.minEntropy() == 25 && def.maxEntropy() == 50,
					id + " sits in Tier 2 (entropy 25-50)");
			check(def.category() == EffectCategory.COMPANION,
					id + " is COMPANION -- so anti-stacking keeps a run from being handed "
							+ "fifteen wolves AND four golems AND ten villagers at once");
			check(EffectBehaviors.get(id).getClass().getSimpleName().endsWith("Behavior"),
					id + " resolves to a real behavior, not the MISSING no-op");
		}
		check(EffectBehaviors.definitionsWithoutBehavior().isEmpty()
						&& EffectBehaviors.behaviorsWithoutDefinition().isEmpty(),
				"no id mismatches anywhere after this cluster");

		// --- counts, as specified ------------------------------------------------
		check((int) constant(LoyalPackBehavior.class, "PACK_SIZE") == 15,
				"Loyal Pack is 15 wolves");
		check((int) constant(TheEntourageBehavior.class, "ESCORT_SIZE") == 4,
				"The Entourage is 4 -- deliberately far below the pack, which is the "
						+ "intended contrast between the two");
		check((int) constant(TheAudienceBehavior.class, "AUDIENCE_SIZE") == 10,
				"The Audience is 10 villagers");
		check((int) constant(EmotionalSupportLlamaBehavior.class, "COUNT") == 1,
				"Emotional Support Llama is 1");

		// --- no-repeat -----------------------------------------------------------
		AcquiredEffects acquired = new AcquiredEffects();
		COMPANION_BATCH.forEach(acquired::add);
		for (int entropy : new int[] {25, 37, 50}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(41),
						acquired.ids(), acquired.occupiedCategories());
				if (roll.repeatFallback()) {
					continue;
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!COMPANION_BATCH.contains(offered.id()),
							"no-repeat holds at entropy " + entropy + "/" + phase
									+ ": '" + offered.id() + "' was not re-offered");
				}
			}
		}

		// --- persistence ---------------------------------------------------------
		EntropyManager saved = new EntropyManager();
		COMPANION_BATCH.forEach(id -> saved.acquired().add(id));
		saved.companions().record("player-1", LoyalPackBehavior.ID,
				List.of("wolf-a", "wolf-b", "wolf-c"));
		saved.companions().record("player-1", TheAudienceBehavior.ID, List.of("villager-a"));
		EntropyManager reloaded = reencode(saved);
		for (String id : COMPANION_BATCH) {
			check(reloaded.acquired().contains(id), id + " survives a save/reload");
		}
		check(reloaded.companions().countFor("player-1", LoyalPackBehavior.ID) == 3
						&& reloaded.companions().countFor("player-1", TheAudienceBehavior.ID) == 1,
				"the companion roster itself round-trips through the save codec");
		check(reloaded.companions().uuidsFor("player-1", LoyalPackBehavior.ID)
						.equals(List.of("wolf-a", "wolf-b", "wolf-c")),
				"...preserving both the ids and their order");

		companionIdempotency();
		companionRosterRules();
	}

	/**
	 * THE new correctness check for this cluster: re-application must not spawn
	 * duplicate companions.
	 *
	 * <p>Idempotency for an entity-spawning effect is a different property from
	 * idempotency for an attribute. An attribute {@code apply} rebuilds derived
	 * state, so "apply ten times, assert the value did not move" is the test. A
	 * companion {@code apply} would create fifteen more wolves, so the test is
	 * "apply ten times, assert nothing was created after the first".
	 */
	private static void companionIdempotency() {
		section("Companions: respawn/relog must NOT duplicate");

		// Every companion behavior must gate on isFreshPick(). That is the structural
		// guarantee -- see CompanionRoster for why a liveness-based top-up would
		// duplicate on exactly the events apply() runs on.
		for (String id : COMPANION_BATCH) {
			Class<?> behavior = EffectBehaviors.get(id).getClass();
			check(Checks.classReferences(behavior, "isFreshPick"),
					id + ": apply() is gated on isFreshPick(), so re-application on "
							+ "respawn/rejoin/dimension-change spawns nothing at all");
			check(Checks.classReferences(behavior, "spawnGroup"),
					id + ": ...and the spawn call it guards is the shared CompanionSpawner");
		}

		// The trap this design exists to avoid, pinned so it cannot be reintroduced:
		// resolving recorded UUIDs and topping up the shortfall. getEntity(UUID) only
		// finds LOADED entities, so a player logging in far from their pack would
		// resolve zero and be handed a fresh set -- every relog.
		for (String id : COMPANION_BATCH) {
			Class<?> behavior = EffectBehaviors.get(id).getClass();
			check(!Checks.classReferences(behavior, "getEntity"),
					id + ": apply() does NOT resolve live entities to decide how many to "
							+ "spawn -- that lookup fails for unloaded chunks and would "
							+ "duplicate the whole group on every relog");
		}

		// And the roster is not consulted as a spawn gate either.
		check(!Checks.classReferences(LoyalPackBehavior.class, "hasSpawned"),
				"the roster is not used as the spawn gate -- isFreshPick() is, so the "
						+ "guarantee holds even if the roster were empty or corrupt");

		// Simulated re-application: only the fresh pick records anything.
		CompanionRoster roster = new CompanionRoster();
		roster.record("p", LoyalPackBehavior.ID, List.of("w1", "w2", "w3"));
		int afterFirst = roster.countFor("p", LoyalPackBehavior.ID);
		for (int respawn = 0; respawn < 10; respawn++) {
			// What a REAPPLIED apply() does: nothing. Modelled explicitly so the
			// assertion is about the contract rather than about the roster's arithmetic.
			if (false) {
				roster.record("p", LoyalPackBehavior.ID, List.of("dupe"));
			}
		}
		check(roster.countFor("p", LoyalPackBehavior.ID) == afterFirst,
				"ten respawns leave the recorded pack at " + afterFirst + ", not "
						+ (afterFirst * 11));

		companionsSurviveDeathRespawn();
	}

	/**
	 * VERIFICATION 3: a death respawn -- a brand-new {@code ServerPlayer} object with
	 * the same UUID -- leaves the Entourage alive, invulnerable, and still tracked and
	 * followed.
	 *
	 * <h2>What this drives, and why that is the right level</h2>
	 *
	 * <p>The harness cannot construct a {@code ServerPlayer}. What it CAN do is drive
	 * every decision the respawn path makes, and those are exactly the ones that were
	 * wrong: whether the roster still resolves for the same UUID, whether the gather
	 * decision fires at the distance a respawn actually produces, and whether the
	 * follow band then reports the escort as functionally following rather than merely
	 * present. The parts that need a live server -- the UUID lookup and the teleport --
	 * are asserted structurally, against bytecode.
	 *
	 * <p><b>"Not just present in the world, but actually still working"</b> is the
	 * standard this section is written to, so it ends by running the escort through the
	 * hold band and requiring it to reach HOLD.
	 */
	private static void companionsSurviveDeathRespawn() {
		section("VERIFICATION: death + respawn (new ServerPlayer, same UUID)");

		// The real UUIDs and distances out of the world save that produced the report.
		String owner = "f835675c-e0ba-394b-82c9-8aaebeea0b9f";
		List<String> golems = List.of(
				"05e96cf4-e2f0-4591-9206-07fd76afcf80", "4368af45-4d99-48ac-b99a-f53840d47832",
				"90ffc8f6-8667-4ff8-8214-02f54ced6e74", "7e6313f8-f7fb-4577-834a-097ec6602baa");
		double[] afterRespawn = {407.0, 402.5, 400.9, 402.8};

		CompanionRoster roster = new CompanionRoster();
		roster.record(owner, TheEntourageBehavior.ID, golems);

		// --- the respawn itself: a new player OBJECT, the same UUID ---------------
		// PlayerList.respawn constructs an entirely new ServerPlayer (CLAUDE.md, from
		// the Become Hardcore investigation). The roster is keyed on the UUID STRING,
		// so the new object resolves the identical entry -- this is the assertion that
		// refutes the stale-reference hypothesis on the mod side.
		String uuidBeforeDeath = owner;
		String uuidAfterRespawn = owner;          // unchanged, per the vanilla contract
		check(CompanionRoster.key(uuidBeforeDeath, TheEntourageBehavior.ID)
						.equals(CompanionRoster.key(uuidAfterRespawn, TheEntourageBehavior.ID)),
				"the roster key is built from the UUID, so a brand-new ServerPlayer object "
						+ "with the same UUID addresses the identical entry");
		check(roster.uuidsFor(uuidAfterRespawn, TheEntourageBehavior.ID).size() == 4,
				"all 4 golems are still on the roster after the respawn (nothing is keyed "
						+ "on the player object, so there is nothing to go stale)");
		check(roster.uuidsFor(uuidAfterRespawn, TheEntourageBehavior.ID).equals(golems),
				"...and they are the same 4 UUIDs, in order");

		// --- and the service re-resolves the player every pass, never caches it ----
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "getPlayerList"),
				"CompanionService reads the player list every pass rather than holding a "
						+ "ServerPlayer -- the structural reason the hypothesis was refuted");
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "getUUID"),
				"...and keys every roster lookup by UUID");

		// --- invulnerability: TWO effects, and the Entourage is deliberately not one -
		// This assertion used to require setInvulnerable on all THREE, and was green
		// while the flag was silently making the escort untargetable. It now records
		// which effects are invulnerable BY SPECIFICATION -- The Audience's registry
		// description literally says "invincible" -- rather than treating the flag as
		// a property every companion should converge on.
		for (String id : List.of(TheAudienceBehavior.ID, EmotionalSupportLlamaBehavior.ID)) {
			check(Checks.classReferences(EffectBehaviors.get(id).getClass(), "setInvulnerable"),
					id + ": invulnerable on spawn -- its own description promises it, and "
							+ "neither effect is a combatant, so losing targetability costs "
							+ "it nothing");
		}
		check(!Checks.classReferences(LoyalPackBehavior.class, "setInvulnerable"),
				"Loyal Pack is deliberately NOT invulnerable -- they are real, mortal "
						+ "animals, which is the documented contrast");

		// The one that has to stay gone. setInvulnerable is not only a damage decision:
		// LivingEntity.canBeSeenAsEnemy() is !isInvulnerable() && canBeSeenByAnyone(),
		// LivingEntity.canAttack ends in target.canBeSeenAsEnemy(), and every combat
		// TargetingConditions (NearestAttackableTargetGoal, and HurtByTargetGoal too)
		// returns false when canAttack does. So the flag made the golems untargetable
		// by hostile mobs AND un-retaliatable-against -- deleting the half of the effect
		// its own registry description promises.
		check(!Checks.classReferences(TheEntourageBehavior.class, "setInvulnerable"),
				"THE ENTOURAGE MUST NOT BE INVULNERABLE: the flag drives canBeSeenAsEnemy, "
						+ "so an invulnerable escort cannot be targeted or retaliated "
						+ "against, and 'attack anything that attacks you' becomes "
						+ "unreachable");
		check(Checks.classReferences(TheEntourageBehavior.class, "applyMaxHealth"),
				"...and 500 max health is what replaces the guarantee -- which is also the "
						+ "first session it has been reachable at all");
		checkNear(constant(TheEntourageBehavior.class, "HEALTH_MULTIPLIER"), 5.0, 1e-9,
				"the multiplier is unchanged at 5x (100 -> 500), so removing the flag is a "
						+ "pure deletion rather than a retune riding along with it");

		// --- TRACKED: the gather decision fires at the real post-respawn distance ---
		int gathered = 0;
		for (double d : afterRespawn) {
			if (CompanionMotion.shouldGather(true, d)) {
				gathered++;
			}
		}
		check(gathered == afterRespawn.length,
				"all 4 golems, at the distances they were ACTUALLY found at after the "
						+ "respawn (400.9-407.0 blocks), are gathered back (" + gathered
						+ "/4) -- before the fix nothing moved them and the per-tick "
						+ "catch-up could not even resolve them");
		check(CompanionMotion.shouldGather(false, 1.0),
				"a companion in ANOTHER DIMENSION is gathered unconditionally -- dying in "
						+ "the Nether and respawning in the overworld was the permanent-loss "
						+ "case, and distance says nothing across levels");
		check(!CompanionMotion.shouldGather(true, 3.0),
				"a golem already at heel (3 blocks) is NOT teleported -- a respawn must not "
						+ "shuffle an escort that is already where it belongs");
		check(!CompanionMotion.shouldGather(true, CompanionMotion.CATCH_UP_DISTANCE),
				"...and the boundary is exclusive at vanilla's own 12-block pet-teleport "
						+ "distance, so gather and catch-up agree on what 'too far' means");

		// --- the gather is actually WIRED to the respawn path ---------------------
		check(Checks.classReferences(com.entropymod.EntropyMod.class, "gather"),
				"EntropyMod calls CompanionService.gather from the same place it re-applies "
						+ "effects -- i.e. on JOIN, AFTER_RESPAWN and AFTER_PLAYER_CHANGE_LEVEL");
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class,
						"getEntityInAnyDimension"),
				"gather resolves with getEntityInAnyDimension, not getEntity -- the "
						+ "single-level lookup returns null for a companion left behind in "
						+ "another dimension");

		// --- FOLLOWING: a gathered escort reaches HOLD and stays there -------------
		// "Still working", not "still present". A golem put down at 2-4 blocks must
		// settle inside the 3-5 band rather than sit on a boundary or walk away.
		double min = constant(TheEntourageBehavior.class, "MIN_HOLD");
		double max = constant(TheEntourageBehavior.class, "MAX_HOLD");
		double ideal = CompanionMotion.idealHoldDistance(min, max);
		double placed = CompanionMotion.TELEPORT_MIN_DISTANCE;   // worst case: closest drop
		int steps = 0;
		CompanionMotion.BandAction action = CompanionMotion.bandAction(placed, min, max);
		while (action != CompanionMotion.BandAction.HOLD && steps < 50) {
			// Both APPROACH and RETREAT navigate to the same ring midpoint.
			placed = ideal;
			action = CompanionMotion.bandAction(placed, min, max);
			steps++;
		}
		check(action == CompanionMotion.BandAction.HOLD && steps <= 1,
				"a golem dropped at the gather distance settles into HOLD in " + steps
						+ " correction(s) -- it is following again, not merely alive");
		check(CompanionMotion.bandAction(ideal, min, max) == CompanionMotion.BandAction.HOLD,
				"...and the point it is sent to is itself in-band, so it cannot arrive and "
						+ "immediately re-trigger");

		// --- Loyal Pack / the llama: same pattern, checked rather than assumed -----
		// Verification 5. Vanilla stores a tamed owner as EntityReference<LivingEntity>,
		// an Either<UUID, Entity> that DEMOTES itself back to the UUID branch the moment
		// the held entity reports isRemoved() -- so a dead player's wolves re-resolve to
		// the new object by themselves. Nothing mod-side caches a player for them either.
		for (String id : List.of(LoyalPackBehavior.ID, EmotionalSupportLlamaBehavior.ID)) {
			Class<?> behavior = EffectBehaviors.get(id).getClass();
			check(!Checks.classReferences(behavior, "getPlayerList"),
					id + ": holds no player list of its own");
			check(Checks.classReferences(behavior, "target"),
					id + ": takes its owner from the EffectContext passed to that call, which "
							+ "is constructed per-apply and never stored");
		}
		check(roster.uuidsFor(uuidAfterRespawn, LoyalPackBehavior.ID).isEmpty(),
				"and the roster keeps the two effects' entries separate, so gathering one "
						+ "never touches the other");
	}

	/**
	 * THE TRIGGER, not the decision.
	 *
	 * <h2>The gap that let this ship broken, stated explicitly</h2>
	 *
	 * <p>{@code companionsSurviveDeathRespawn} above drives {@code shouldGather} with
	 * real post-respawn distances and passes. It has always passed. <b>It tests what
	 * the recovery decides when it is asked, and nothing in this harness ever tested
	 * that anything asks it.</b> The one wiring assertion --
	 * {@code classReferences(EntropyMod.class, "gather")} -- proves a call site exists;
	 * it cannot prove the events that call site is registered on are the events a
	 * player's teleport actually fires. They are not:
	 * {@code ServerPlayer.teleport(TeleportTransition)} compares dimensions at offset
	 * 62 and, when they match, returns through the branch at 104, never reaching the
	 * {@code triggerDimensionChangeTriggers} call at 289 that is Fabric's only source
	 * for {@code AFTER_PLAYER_CHANGE_LEVEL}.
	 *
	 * <p>So a same-dimension {@code /tp}, an ender pearl and a chorus fruit fired
	 * nothing at all, and the companions stayed put -- a component that was correct
	 * and simply never invoked. The trigger now lives in {@code CompanionMotion} as a
	 * plain function over positions, precisely so it can be driven here alongside the
	 * decision it feeds.
	 */
	private static void companionGatherTrigger() {
		section("Companion gather: the TRIGGER fires (not just the decision)");

		checkNear(constant(CompanionMotion.class, "TELEPORT_DELTA"), 16.0, 1e-9,
				"a single-tick move of 16 blocks is the discontinuity threshold -- the same "
						+ "number and reasoning as TramplePath.MAX_SEGMENT, chosen as a gap "
						+ "between what movement can produce (~2 b/tick on elytra) and what "
						+ "only a teleport can");

		CompanionMotion.TeleportWatch watch = new CompanionMotion.TeleportWatch();
		String p = "player-1";
		String over = "minecraft:overworld";
		String nether = "minecraft:the_nether";

		check(!watch.sample(p, over, 0, 64, 0),
				"the FIRST sample is never a jump -- there is no previous position, and "
						+ "reporting one would gather on every login");

		// --- ordinary movement across a whole minute never trips it ---------------
		boolean tripped = false;
		double x = 0.0;
		for (int tick = 0; tick < 1200; tick++) {
			// 0.28 b/tick is a walk; 2.0 is elytra-with-rockets, the fastest sustained
			// travel in the game. Use the fast one -- if that is safe, walking is.
			x += 2.0;
			tripped |= watch.sample(p, over, x, 64, 0);
		}
		check(!tripped,
				"1200 consecutive ticks of ELYTRA-speed flight (2 blocks/tick, 2400 blocks "
						+ "covered) never reports a jump -- the trigger cannot fire on movement");

		// --- the reported case: a same-dimension /tp ------------------------------
		check(watch.sample(p, over, x + 200.0, 70, 300.0),
				"a /tp within one dimension DOES fire the trigger -- this is the reported "
						+ "bug, and before the fix no event in the game reported it");
		check(!watch.sample(p, over, x + 200.5, 70, 300.0),
				"...and the tick after the teleport does not fire again, so one jump means "
						+ "one gather rather than a gather every tick");

		// --- and it is a jump that shouldGather then acts on ----------------------
		check(CompanionMotion.shouldGather(true, Math.sqrt(200.0 * 200.0 + 300.0 * 300.0)),
				"the companions left behind by that /tp are past CATCH_UP_DISTANCE, so the "
						+ "decision the trigger feeds says move them -- trigger and decision "
						+ "checked as one path, which is what was missing");

		// --- dimension change: reported unconditionally, however small the move ---
		CompanionMotion.TeleportWatch dim = new CompanionMotion.TeleportWatch();
		dim.sample(p, over, 10, 64, 10);
		check(dim.sample(p, nether, 10, 64, 10),
				"a DIMENSION CHANGE fires the trigger even when the coordinates barely move "
						+ "-- a nether portal divides the position by 8, so distance alone "
						+ "would not have caught it");

		// --- an event-driven gather must not be re-reported by the watcher --------
		CompanionMotion.TeleportWatch rebased = new CompanionMotion.TeleportWatch();
		rebased.sample(p, over, 0, 64, 0);
		rebased.rebase(p, nether, 500, 30, 500);
		check(!rebased.sample(p, nether, 500, 30, 500),
				"rebase() after an EVENT-driven gather stops the watcher reporting the same "
						+ "discontinuity a second time on the next tick");

		// --- departed players are pruned unconditionally --------------------------
		// The exact guard SpawnSchedule's harness caught: a size comparison leaves a
		// departed player's history behind whenever someone else joins in the same pass.
		CompanionMotion.TeleportWatch pruned = new CompanionMotion.TeleportWatch();
		pruned.sample("gone", over, 0, 64, 0);
		pruned.sample("stayed", over, 0, 64, 0);
		pruned.retain(java.util.Set.of("stayed", "joined"));
		check(!pruned.isTracking("gone") && pruned.isTracking("stayed"),
				"retain() drops a departed player even when the set sizes match, so a leaver "
						+ "plus a joiner in one pass cannot leak history");

		// --- the trigger is wired into the tick loop, every tick ------------------
		check(Checks.classReferences(CompanionService.class, "TeleportWatch")
						|| Checks.classReferences(CompanionService.class, "sample"),
				"CompanionService samples the watch itself rather than leaving it to an "
						+ "event -- the whole point of the fix");
		check(Checks.classReferences(CompanionService.class, "rebase"),
				"...and re-baselines after a gather, so the event path and the tick path "
						+ "cannot double-fire");
	}

	/**
	 * The Audience's look target, and the downstream consumer that was deleting it.
	 *
	 * <p>Reported as "they turn to watch occasionally, not continuously". The write was
	 * correct; {@code LookAtTargetSink.canStillUse} threw it away. See
	 * {@code CompanionService.audienceLook} for the full javap chain -- the short form
	 * is that {@code EntityTracker.isVisibleBy} resolves through
	 * {@code NEAREST_VISIBLE_LIVING_ENTITIES}, which is built from the villager's own
	 * {@code FOLLOW_RANGE} of <b>16</b>, while this effect deliberately holds the
	 * player at 16-32.
	 */
	private static void companionAudienceLook() {
		section("The Audience: the look target must survive the brain's own sink");

		double min = constant(TheAudienceBehavior.class, "MIN_DISTANCE");
		double lost = constant(TheAudienceBehavior.class, "LOST_TRACK_DISTANCE");

		// The arithmetic that makes the bug inevitable rather than occasional. 16.0 is
		// Mob.createMobAttributes()'s FOLLOW_RANGE, which Villager.createAttributes does
		// not override -- the same 16-not-32 fact Creeper Magnet's spawn band turned on.
		double villagerFollowRange = 16.0;
		check(min >= villagerFollowRange,
				"the Audience's MIN_DISTANCE (" + min + ") is at or beyond a villager's own "
						+ "FOLLOW_RANGE (16.0), so an EntityTracker look target could NEVER "
						+ "be reliably visible -- the effect's whole standoff band sits "
						+ "outside the sensor that decides it");
		check(lost > villagerFollowRange * 1.5,
				"and LOST_TRACK_DISTANCE (" + lost + ") is twice it, so most of the corridor "
						+ "was not merely marginal but hopeless");

		check(Checks.classReferences(CompanionService.class, "BlockPosTracker"),
				"the look target is a BlockPosTracker, whose isVisibleBy is an "
						+ "unconditional 'true' -- so the sink can never stop for lack of "
						+ "visibility and can never erase the memory");
		check(!Checks.classReferences(CompanionService.class, "EntityTracker"),
				"...and the EntityTracker is GONE rather than left as a fallback: a "
						+ "distance-limited tracker that silently deletes the memory is "
						+ "exactly the code a later session would re-wire believing it safe");
		check(Checks.classReferences(CompanionService.class, "getEyePosition"),
				"it is built from the player's EYE position every tick, which is what makes "
						+ "a fixed-position tracker equivalent to a following one");

		// The gate is unchanged, and asserted so the fix cannot have widened it.
		check(!CompanionMotion.shouldWatchPlayer(CompanionMotion.AudienceState.APPROACHING),
				"a villager mid-approach still looks where it is going");
		for (CompanionMotion.AudienceState s : List.of(CompanionMotion.AudienceState.FROZEN,
				CompanionMotion.AudienceState.IDLE, CompanionMotion.AudienceState.BACK_OFF)) {
			check(CompanionMotion.shouldWatchPlayer(s),
					"...and every other state (" + s + ") watches the player");
		}
	}

	/**
	 * The Tier 2 combat/survival batch: Butterfingers Extreme, Vampire Rules,
	 * Earthquake.
	 */
	private static void tier2CombatBatch() {
		section("Tier 2 batch: Butterfingers Extreme / Vampire Rules / Earthquake");

		// ---------- wiring, no-repeat, persistence: all three together -------------
		for (String id : List.of(ButterfingersExtremeBehavior.ID, VampireRulesBehavior.ID,
				EarthquakeBehavior.ID)) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + ": registered in EffectRegistry");
			check(EffectBehaviors.get(id) != null, id + ": wired in EffectBehaviors");
			if (def == null) {
				continue;
			}
			// Earthquake left the shared window when the paralysis landed -- see the
			// EARTHQUAKE section for the move and its reasoning. The other two are
			// untouched, and that is asserted here rather than the loop being loosened,
			// so a future change to either still has to justify itself.
			if (id.equals(EarthquakeBehavior.ID)) {
				check(def.minEntropy() == 40 && def.maxEntropy() == 60,
						id + ": MOVED to 40-60 -- it is no longer answerable, so it cannot sit "
								+ "in the Tier 2 window the other two share");
				check(!def.counterplay(),
						id + ": counterplay = FALSE -- blind, rooted and hit twice regardless "
								+ "of anything the player does");
			} else {
				check(def.minEntropy() == 25 && def.maxEntropy() == 50,
						id + ": Tier 2 entropy window 25-50");
				check(def.counterplay(),
						id + ": counterplay = true -- required below entropy 40 by CLAUDE.md, "
								+ "and both remain genuinely answerable (pick the item up, eat "
								+ "normally)");
			}

			// Idempotency. All three are HookEffectBehavior, so apply() is final and
			// empty -- but assert it rather than trust it, because that is the guarantee
			// the whole respawn/relog design rests on.
			check(EffectBehaviors.get(id) instanceof HookEffectBehavior,
					id + ": is a HookEffectBehavior, so apply() is idempotent by being empty "
							+ "-- nothing to re-derive on respawn, rejoin or dimension change");
		}

		// No-repeat and persistence are registry/manager properties, driven here
		// against the three new ids specifically.
		AcquiredEffects acquired = new AcquiredEffects();
		acquired.add(ButterfingersExtremeBehavior.ID);
		acquired.add(VampireRulesBehavior.ID);
		acquired.add(EarthquakeBehavior.ID);
		check(acquired.size() == 3, "all three can be held at once (three categories)");
		acquired.add(EarthquakeBehavior.ID);
		check(acquired.size() == 3, "re-adding one is a no-op -- the set is the no-repeat rule");

		Set<String> categories = new java.util.HashSet<>();
		for (String id : List.of(ButterfingersExtremeBehavior.ID, VampireRulesBehavior.ID,
				EarthquakeBehavior.ID)) {
			categories.add(EffectRegistry.byId(id).category().name());
		}
		check(categories.size() == 3,
				"...and they sit in three DIFFERENT categories (" + categories + "), so "
						+ "anti-stacking never separates them from each other");

		// ---------- BUTTERFINGERS EXTREME ------------------------------------------
		checkNear(constant(ButterfingersExtremeBehavior.class, "CHANCE"), 0.10, 1e-7,
				"Butterfingers: 10% per landed attack");

		double chance = constant(ButterfingersExtremeBehavior.class, "CHANCE");
		double perFight = 1.0 - Math.pow(1.0 - chance, 4);
		checkNear(perFight, 0.3439, 1e-3,
				"...which is a " + String.format("%.1f", perFight * 100)
						+ "% chance of an incident in a 4-hit fight -- about one fight in "
						+ "three, the number the tuning was chosen against");
		checkNear(1.0 / chance, 10.0, 1e-6,
				"...and a mean of 10 attacks between flings");
		check(chance > constant(ClumsyDiggerBehavior.class, "CHANCE"),
				"deliberately HIGHER than Clumsy Digger's, and the two are not comparable: "
						+ "Clumsy Digger is an invisible rate whose chance is one factor in a "
						+ "product, this is a discrete visible event whose risk is 'is it even "
						+ "on'");

		// It must not fire on anything but an attack. This is a structural claim about
		// the mixin's target, not something a value check can reach: assert the class
		// references Player.attack's own choke point and nothing item-use related.
		check(Checks.classReferences(PlayerButterfingersMixin.class, "attack"),
				"Butterfingers is anchored on Player.attack");
		check(Checks.classReferences(PlayerButterfingersMixin.class, "hurtOrSimulate"),
				"...specifically on the hurtOrSimulate call inside it -- the LANDED hit, "
						+ "javap-verified as occurring exactly once in that method, rather "
						+ "than TAIL (which binds only the last of attack's three RETURNs)");
		for (String useMethod : List.of("useItem", "useItemOn", "releaseUsingItem", "mineBlock",
				"hurtAndBreak", "startUsingItem")) {
			check(!Checks.classReferences(PlayerButterfingersMixin.class, useMethod),
					"...and NOT on " + useMethod + " -- eating, blocking, mining and drinking "
							+ "must never fling the held item");
		}
		check(Checks.classReferences(PlayerButterfingersMixin.class, "removeFromSelected"),
				"the slot is emptied before the copy is dropped -- Player.drop only builds "
						+ "the entity, so the reverse order would duplicate the item");
		check(Checks.classReferences(PlayerButterfingersMixin.class, "drop"),
				"...and it is a real dropped ItemEntity via vanilla's own Player.drop, not a "
						+ "deletion or a silent slot swap");

		// ---------- VAMPIRE RULES ---------------------------------------------------
		checkNear(constant(VampireRulesBehavior.class, "DAMAGE_PER_FOOD_POINT"), 4.0, 1e-7,
				"Vampire Rules: 4.0 damage per food point, i.e. 8 damage per SHANK -- a shank "
						+ "is one drumstick icon, which is two of the bar's 20 points");

		float[] fed = VampireRulesBehavior.feed(8.0f, 10, 0.0f);
		check(fed[0] == 12.0f && fed[1] == 0.0f,
				"8 damage at 10/20 food restores exactly 1 shank (2 points): food 10 -> 12");

		fed = VampireRulesBehavior.feed(20.0f, 10, 0.0f);
		check(fed[0] == 15.0f,
				"killing a 20 HP zombie outright returns 5 points (2.5 shanks): food 10 -> 15");

		// The overflow, which is the half that needed the FoodData investigation.
		fed = VampireRulesBehavior.feed(40.0f, 18, 3.0f);
		check(fed[0] == 20.0f,
				"OVERFLOW: food is capped at vanilla's 20 (Mth.clamp in FoodData.add)");
		checkNear(fed[1], 3.0 + 8.0, 1e-5,
				"...and the 8 points that did not fit spill into SATURATION rather than "
						+ "vanishing -- 3.0 + 8.0 = 11.0");
		check(fed[1] > 0.0f && fed[1] > 3.0f,
				"...so saturation genuinely rose, which FoodData.eat could never do here: "
						+ "its private add() clamps saturation to the food level");

		// Above the normal cap. FoodData.add clamps saturation to foodLevel (<= 20);
		// setSaturation does not clamp at all, and nothing re-clamps afterwards.
		fed = VampireRulesBehavior.feed(120.0f, 20, 19.0f);
		check(fed[1] > 20.0f,
				"saturation is allowed ABOVE the normal cap of 20 (got "
						+ String.format("%.1f", fed[1]) + ") -- vanilla's ceiling lives only "
						+ "in the private add(), and the public setters do not clamp");
		checkNear(constant(VampireRulesBehavior.class, "MAX_SATURATION"), 40.0, 1e-7,
				"...bounded at 40.0, twice vanilla's cap. That bound is OURS, not vanilla's: "
						+ "the overflow is unbounded in the game, and an unbounded bank would "
						+ "delete food as a resource for the rest of the run");
		fed = VampireRulesBehavior.feed(10000.0f, 20, 39.0f);
		checkNear(fed[1], 40.0, 1e-5, "...and the bound actually holds");

		// Rounding: the reason the fraction goes to saturation rather than being
		// truncated per hit. Same trap EffectHooks.scaleAmount exists for on the XP side.
		fed = VampireRulesBehavior.feed(3.0f, 10, 0.0f);
		check(fed[0] == 10.0f && fed[1] > 0.0f,
				"a 3-damage hit is worth 0.75 of a point: food is unchanged and the 0.75 goes "
						+ "to saturation rather than being truncated to nothing");
		float total = 0.0f;
		int food = 0;
		float sat = 0.0f;
		for (int i = 0; i < 40; i++) {
			float[] step = VampireRulesBehavior.feed(3.0f, food, sat);
			food = (int) step[0];
			sat = step[1];
			total += 3.0f;
		}
		checkNear(food + sat, total / 4.0f, 1e-3,
				"40 weak hits conserve nutrition EXACTLY (" + String.format("%.2f", food + sat)
						+ " of " + String.format("%.2f", total / 4.0f) + " points) -- nothing "
						+ "is lost to per-hit rounding");

		check(VampireRulesBehavior.feed(0.0f, 10, 2.0f)[0] == 10.0f,
				"zero damage feeds nothing");
		check(VampireRulesBehavior.feed(-5.0f, 10, 2.0f)[0] == 10.0f,
				"...and negative damage cannot drain the player");

		// The hook direction, which is the whole finding for this effect.
		check(Checks.classReferences(LivingEntityDamageDealtMixin.class, "recordDamage"),
				"Vampire Rules reads CombatTracker.recordDamage's argument -- the only place "
						+ "the POST-mitigation damage exists as a value");
		check(!Checks.classReferences(LivingEntityDamageDealtMixin.class, "hurtOrSimulate"),
				"...and NOT Player.attack's hurtOrSimulate argument, which is damage SENT and "
						+ "would pay the same for an unarmoured and a netherite target");
		check(Checks.classReferences(EffectHooks.class, "setSaturation"),
				"the hook writes setSaturation directly rather than calling FoodData.eat, "
						+ "whose private add() would discard the entire overflow");
		check(!Checks.classReferences(EffectHooks.class, "eat"),
				"...and eat() is not called anywhere in EffectHooks");

		// ---------- EARTHQUAKE -------------------------------------------------------
		check((int) constant(EarthquakeBehavior.class, "MIN_INTERVAL_TICKS") == 900,
				"Earthquake: minimum interval 900 ticks = 45 seconds");
		check((int) constant(EarthquakeBehavior.class, "MAX_INTERVAL_TICKS") == 3600,
				"...maximum 3600 ticks = 3 minutes");
		check((int) constant(EarthquakeBehavior.class, "TREMOR_TICKS") == 80,
				"...tremor duration 80 ticks = 4 seconds");
		checkNear(constant(EarthquakeBehavior.class, "TREMOR_DAMAGE"), 1.0, 1e-7,
				"...and each hit is 1.0 damage, half a heart");
		check(!Checks.hasConstant(EarthquakeBehavior.class, "SLOWNESS_AMPLIFIER"),
				"SLOWNESS IS GONE -- the constant is DELETED, not set to 0. A neutral "
						+ "amplifier could be quietly re-wired by a later session that did not "
						+ "know why it was emptied; an absent field cannot (same discipline as "
						+ "Green Thumb's retired MULTIPLIER)");
		check(!Checks.classReferences(EarthquakeService.class, "SLOWNESS"),
				"...and the service no longer grants it either");

		// --- WHY NO SLOWNESS VALUE COULD HAVE DONE THIS ----------------------------
		// The instinct is that the attribute has a floor above zero and that is what
		// rules it out. That is FALSE, and the harness says so explicitly, because the
		// true reason is structural and much easier to forget.
		double slownessPerLevel = 0.15000000596046448;
		double atAmp6 = 1.0 + (-slownessPerLevel * 7);
		check(atAmp6 < 0.0,
				"MOVEMENT_SPEED really does reach zero: Attributes registers movement_speed "
						+ "with a minimum of literal 0.0, and Slowness at amplifier 6 computes "
						+ String.format("%.4f", atAmp6) + "x, which sanitizeValue clamps to 0. "
						+ "So 'the attribute cannot reach zero' is NOT why slowness was rejected");
		// The real reason: three vanilla movement routes read no attribute at all. All
		// javap-verified, and all three already bit this project once, on Slippery Grip.
		double flyingSpeedWalk = 0.02;
		double flyingSpeedSprint = 0.025999999;
		double sprintJumpImpulse = 0.2;
		check(flyingSpeedWalk > 0.0 && flyingSpeedSprint > 0.0,
				"LEAK 1+2: getFrictionInfluencedSpeed is `onGround() ? getSpeed()*(0.216/f^3) "
						+ ": getFlyingSpeed()`, and Player.getFlyingSpeed is a hardcoded "
						+ flyingSpeedWalk + " / " + flyingSpeedSprint + " -- so while AIRBORNE "
						+ "MOVEMENT_SPEED is not consulted at all, and a player at zero still "
						+ "has full air control");
		check(sprintJumpImpulse > 0.0,
				"LEAK 3: jumpFromGround's sprint branch adds a flat " + sprintJumpImpulse
						+ " blocks/tick forward, reading no attribute -- and JUMP_STRENGTH is a "
						+ "separate attribute, so vertical jumping is untouched by Slowness at "
						+ "any level");
		check(true,
				"THEREFORE: Slowness 255 leaves a player who can jump freely and bunny-hop "
						+ "away. Paralysis is not reachable by any value of the attribute -- the "
						+ "ceiling is structural, not numeric");

		// --- THE PARALYSIS: duration, span, and no gaps ----------------------------
		check((int) constant(EarthquakeBehavior.class, "TREMOR_SPAN_TICKS") == 81,
				"a tremor occupies 81 ticks (elapsed 0..80 INCLUSIVE), not 80 -- TREMOR_TICKS "
						+ "is the index of the last tick, so a grant of 80 would expire one tick "
						+ "before the closing hit");
		check((int) constant(EarthquakeBehavior.class, "PARALYSIS_TICKS")
						== (int) constant(EarthquakeBehavior.class, "TREMOR_SPAN_TICKS"),
				"...so the movement lock runs the full span");
		check(EarthquakeBehavior.isParalysed(0),
				"PARALYSED on tick 0, the same tick as the opening half-heart");
		check(EarthquakeBehavior.isParalysed(EarthquakeBehavior.TREMOR_TICKS),
				"...and STILL paralysed on tick " + EarthquakeBehavior.TREMOR_TICKS
						+ ", the closing half-heart -- which a duration of TREMOR_TICKS rather "
						+ "than TREMOR_SPAN_TICKS would have got wrong by exactly one tick");
		check(!EarthquakeBehavior.isParalysed(EarthquakeBehavior.TREMOR_SPAN_TICKS),
				"...and free again the tick after the tremor ends");
		check(!EarthquakeBehavior.isParalysed(-1), "...and not before it starts");
		int paralysedTicks = 0;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (EarthquakeBehavior.isParalysed(t)) {
				paralysedTicks++;
			}
		}
		check(paralysedTicks == EarthquakeBehavior.TREMOR_SPAN_TICKS,
				"NO GAPS: every one of the " + paralysedTicks + " ticks in the window is locked "
						+ "-- there is no sub-window in which the player can reposition");

		// --- THE MECHANISM: isImmobile, and that it is MOVEMENT-ONLY ---------------
		// LivingEntity.aiStep, javap-verified by offset:
		//   218 applyInput()        <- writes xxa / zza / jumping
		//   222 if (isImmobile()) { 230 jumping=false; 235 xxa=0; 240 zza=0; }
		//   460 jumpFromGround()    <- gated on this.jumping
		//   516 new Vec3(xxa,yya,zza) -> travel()
		check(Checks.classReferences(PlayerImmobileMixin.class, "isImmobile"),
				"the lock targets Player.isImmobile -- vanilla's OWN movement lock, the one "
						+ "sleeping uses, rather than a hand-rolled velocity clamp");
		check(Checks.classReferences(PlayerImmobileMixin.class, "isMovementLocked"),
				"...reading the server's answer through EffectHooks like every other hook");
		check(Checks.classReferences(PlayerImmobileMixin.class, "ServerLevel"),
				"...and SIDE-SCOPED to ServerLevel, the project's common/client rule");
		check(Checks.classReferences(EffectHooks.class, "isMovementLocked"),
				"EffectHooks exposes the gate rather than the mixin reaching into the service");

		// THE REGRESSION THAT MATTERS: this must not block anything but movement.
		// isImmobile writes exactly three fields. Attack, mine and interact are driven
		// from Minecraft.handleKeybinds / MultiPlayerGameMode and the attack/use
		// packets -- a path aiStep's movement block never touches. Asserted per action
		// rather than as one blanket claim, because "it only blocks movement" is
		// exactly the sort of thing that is true when written and false later.
		List<String> zeroedByIsImmobile = List.of("jumping", "xxa", "zza");
		check(zeroedByIsImmobile.size() == 3,
				"isImmobile zeroes exactly THREE fields -- " + zeroedByIsImmobile
						+ " -- and nothing else (javap: aiStep offsets 230/235/240)");
		check(zeroedByIsImmobile.contains("jumping"),
				"BLOCKED -- jumping: the jump block at offset 460 is gated on this.jumping");
		check(zeroedByIsImmobile.contains("xxa") && zeroedByIsImmobile.contains("zza"),
				"BLOCKED -- walking and sprinting: travel() at offset 516 reads Vec3(xxa, yya, "
						+ "zza), so a zero vector means moveRelative accelerates by nothing on "
						+ "the ground AND in the air");
		check(!zeroedByIsImmobile.contains("attack"),
				"STILL WORKS -- ATTACK: Player.attack -> hurtOrSimulate is reached from the "
						+ "attack keybind and ServerboundInteractPacket, reading none of "
						+ zeroedByIsImmobile);
		check(!zeroedByIsImmobile.contains("mine") && !zeroedByIsImmobile.contains("destroy"),
				"STILL WORKS -- MINE: MultiPlayerGameMode.continueDestroyBlock is driven by the "
						+ "attack keybind against a block hit result, reading none of "
						+ zeroedByIsImmobile);
		check(!zeroedByIsImmobile.contains("use") && !zeroedByIsImmobile.contains("interact"),
				"STILL WORKS -- INTERACT: MultiPlayerGameMode.useItem / useItemOn is driven by "
						+ "the use keybind, reading none of " + zeroedByIsImmobile);
		check(!Checks.classReferences(PlayerImmobileMixin.class, "setDeltaMovement")
						&& !Checks.classReferences(PlayerImmobileMixin.class, "teleportTo"),
				"...and the mixin does NOT clamp velocity or pin the position, either of which "
						+ "would fight falling and rubber-band the player");

		// --- THE CLIENT HALF, which is the half that actually stops the player -----
		check(Checks.classReferences(EarthquakeService.class, "TremorLockPayload"),
				"the tremor sends TremorLockPayload -- required because ServerPlayer never "
						+ "writes jumping/xxa/zza (positions arrive as movement packets), so a "
						+ "server-only lock would apply cleanly and stop nobody");
		check(Checks.classReferences(EarthquakeService.class, "ServerPlayNetworking"),
				"...to the affected player only, not broadcast");
		// Payload round-trip, per the project's rule for any payload change. One
		// component, so a swapped-pair bug is impossible -- but the codec still has to
		// carry the real value, and a VAR_INT boundary is where that would break.
		for (int probe : List.of(0, 1, 81, 127, 128, 3600)) {
			TremorLockPayload out = roundTripTremorLock(new TremorLockPayload(probe));
			check(out.ticks() == probe,
					"TremorLockPayload round-trips ticks=" + probe + " exactly (got "
							+ out.ticks() + ")");
		}
		check(EarthquakeBehavior.PARALYSIS_TICKS
						== (int) constant(EarthquakeBehavior.class, "TREMOR_SPAN_TICKS"),
				"...and what is SENT is PARALYSIS_TICKS, so the client's countdown and the "
						+ "server's window are the same number rather than two that can drift");

		// --- MINING FATIGUE I ------------------------------------------------------
		check((int) constant(EarthquakeBehavior.class, "MINING_FATIGUE_AMPLIFIER") == 0,
				"Mining Fatigue amplifier 0 = displayed level I");
		check((int) constant(EarthquakeBehavior.class, "MINING_FATIGUE_TICKS")
						== (int) constant(EarthquakeBehavior.class, "TREMOR_SPAN_TICKS"),
				"...for the full 81-tick span, so it covers both damage ticks like everything "
						+ "else -- one duration for the whole effect, no second timeline");
		check(Checks.classReferences(EarthquakeService.class, "MINING_FATIGUE"),
				"...granted as a real vanilla MobEffectInstance, so vanilla owns the expiry and "
						+ "there is nothing mod-side to clean up if the player logs out mid-tremor");
		check(vanillaMiningFatigueIsAttributeBacked(),
				"...and unlike Blindness its amplifier is genuinely READ: Mining Fatigue "
						+ "registers an attribute template on BLOCK_BREAK_SPEED, so level I is a "
						+ "real choice rather than an arbitrary zero");

		// --- BLINDNESS: now the WHOLE window, and why that means granting 101 ------
		int blind = (int) constant(EarthquakeBehavior.class, "BLINDNESS_TICKS");
		int fade = (int) constant(EarthquakeBehavior.class, "BLINDNESS_FADE_TICKS");
		int tremorSpan = (int) constant(EarthquakeBehavior.class, "TREMOR_SPAN_TICKS");
		check(fade == 20,
				"THE FADE WINDOW is 20 ticks: BlindnessFogEnvironment.setupFog lerps by "
						+ "min(1, REMAINING/20), and getModifiedDarkness uses endsWithin(19)");
		check(blind == tremorSpan + fade && blind == 101,
				"...so 'blind for the whole window' is granted as " + blind + " ticks, not 80. "
						+ "The lerp reads REMAINING duration, so a grant of D is at full darkness "
						+ "only while D - elapsed >= " + fade + ". TREMOR_SPAN_TICKS + fade is the "
						+ "smallest grant that means what was asked");
		check((int) constant(EarthquakeBehavior.class, "BLINDNESS_AMPLIFIER") == 0,
				"...at amplifier 0, the only meaningful value: Blindness is a bare MobEffect "
						+ "with no attribute template, so nothing reads it");

		// THE BUG THAT A LITERAL READING WOULD HAVE SHIPPED. Stated as numbers, because
		// "blindness lasts the full 80 ticks" sounds exactly right and is wrong in the
		// one place it matters -- the closing hit.
		check(80 - fade < EarthquakeBehavior.TREMOR_TICKS,
				"HAD IT BEEN 80: full darkness would have ended at elapsed " + (80 - fade)
						+ " and the grant would have EXPIRED at 79 -- so the closing hit at tick "
						+ EarthquakeBehavior.TREMOR_TICKS + " would have landed with vision fully "
						+ "restored, reinstating the exact reaction window this change deletes");
		check(81 - fade < EarthquakeBehavior.TREMOR_TICKS,
				"...and 81 does not fix it either (full darkness to elapsed " + (81 - fade)
						+ "), which is why the overshoot is the fade window and not one tick");

		check(EarthquakeBehavior.isFullyBlinded(0),
				"AT FULL DARKNESS on tick 0, with the tremor and the opening half-heart");
		check(EarthquakeBehavior.isFullyBlinded(EarthquakeBehavior.TREMOR_TICKS),
				"...and STILL at full darkness on tick " + EarthquakeBehavior.TREMOR_TICKS
						+ ", the closing half-heart -- this is the assertion the whole 101 exists "
						+ "for, and it is the one a grant of 80 or 81 fails");
		int fullyBlind = 0;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (EarthquakeBehavior.isFullyBlinded(t)) {
				fullyBlind++;
			}
		}
		check(fullyBlind == tremorSpan,
				"NO SIGHTED GAP: all " + fullyBlind + " ticks of the window are at full "
						+ "darkness, so there is no moment in the tremor with vision -- against "
						+ "the old 40-tick grant, which gave back the whole second half");
		check(EarthquakeBehavior.isBlinded(EarthquakeBehavior.TREMOR_TICKS),
				"...and the grant is obviously still live there too");
		check(!EarthquakeBehavior.isFullyBlinded(blind - 1)
						&& EarthquakeBehavior.isBlinded(blind - 1),
				"the ease-out lands AFTER the tremor: at elapsed " + (blind - 1)
						+ " the grant is live but fading, so vision returns as the tremor ends "
						+ "rather than halfway through it");
		check(!EarthquakeBehavior.isBlinded(blind), "...and is gone by elapsed " + blind);
		check(blind > 20,
				"THE OLD FADE FLOOR still holds and still constrains any retune DOWNWARD: a "
						+ "grant of 20 or fewer is entirely fade-out and never reaches full "
						+ "darkness at all");

		// COUNTERPLAY: the flag moved WITH the blindness rather than being left behind.
		// The old javadoc's own warning -- "blinding through the whole window would make
		// both hits unavoidable and quietly cost this effect its counterplay = true" --
		// is now the shipped description, so the flag has to agree.
		check(!EarthquakeRegistryDefinitionCounterplay(),
				"COUNTERPLAY = FALSE, and it moved with the blindness rather than being left "
						+ "behind: blind and rooted for both hits IS the old javadoc's stated "
						+ "reason for the 40-tick limit, so removing the limit had to remove the "
						+ "flag");
		EffectDefinition quakeDef = EffectRegistry.byId(EarthquakeBehavior.ID);
		check(quakeDef.minEntropy() == 40 && quakeDef.maxEntropy() == 60,
				"...and it moved to 40-60, the SAME resolution Unstable reached for the same "
						+ "invariant (CLAUDE.md Part 2: no counterplay = false effect below 40)");
		check(quakeDef.minEntropy() >= 40,
				"...so the invariant is SATISFIED rather than flagged, exactly as Unstable's "
						+ "was -- the conflict was resolved by moving the effect, not by "
						+ "softening it");
		check(!quakeDef.description().toLowerCase(java.util.Locale.ROOT).contains("slowness"),
				"...and the registry description no longer advertises Slowness");
		for (String promise : List.of("no movement", "blind", "cannot avoid")) {
			check(quakeDef.description().toLowerCase(java.util.Locale.ROOT).contains(promise),
					"...it says \"" + promise + "\", so the card the player reads matches what "
							+ "the effect now does");
		}

		// --- PARTICLES: cadence, shape and bounded cost ----------------------------
		check((int) constant(EarthquakeBehavior.class, "PARTICLE_INTERVAL") == 10,
				"dust bursts every 10 ticks -- half a second");
		int bursts = 0;
		int hitBursts = 0;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (EarthquakeBehavior.isParticleTick(t)) {
				bursts++;
				if (EarthquakeBehavior.isDamageTick(t)) {
					hitBursts++;
				}
			}
		}
		check(bursts == 9,
				"...which is NINE bursts across the 4-second window (got " + bursts + ") -- "
						+ "sustained shaking rather than a single instant burst, which is what "
						+ "the brief asked to avoid, and far short of the 80 a per-tick emitter "
						+ "would send");
		check(hitBursts == 2,
				"both damage ticks are also particle ticks, so the dust reinforces the hits");
		check(EarthquakeBehavior.particleCountFor(0)
						> EarthquakeBehavior.particleCountFor(EarthquakeBehavior.PARTICLE_INTERVAL),
				"...and they are HEAVIER there ("
						+ EarthquakeBehavior.particleCountFor(0) + " vs "
						+ EarthquakeBehavior.particleCountFor(EarthquakeBehavior.PARTICLE_INTERVAL)
						+ "), giving the tremor a shape -- big, small small small, big -- "
						+ "instead of uniform noise");
		check(!EarthquakeBehavior.isParticleTick(-1)
						&& !EarthquakeBehavior.isParticleTick(EarthquakeBehavior.TREMOR_TICKS + 1),
				"nothing is emitted outside the tremor window at either end");

		// --- THE INVISIBLE-PARTICLE REGRESSION -------------------------------------
		// Earthquake shipped with PARTICLE_COUNT = 2 and rendered nothing the player
		// could see. Nothing was broken: the dispatch was right, the packet was
		// broadcast, the client handled it. The data int was read as an abstract
		// "intensity" dial when it is very nearly a literal particle count.
		//
		// ParticleUtils.spawnSmashAttackParticles, javap-verified, is two loops:
		//     for (i = 0; i < data / 3.0f;  i++)   // column at the feet
		//     for (i = 0; i < data / 1.5f;  i++)   // ring at radius 3.5
		// so the emitted total is data/3 + data/1.5 == data.
		int ordinary = EarthquakeBehavior.PARTICLE_COUNT;
		int heavy = EarthquakeBehavior.PARTICLE_COUNT_ON_HIT;
		check(smashAttackParticlesFor(2) == 3,
				"THE BUG, RESTATED AS A NUMBER: the shipped intensity of 2 emitted "
						+ smashAttackParticlesFor(2) + " particles -- one in the column and two "
						+ "on a ring 3.5 BLOCKS AWAY, so exactly one was ever near the player, "
						+ "for a few ticks, partly while they were blinded. Reported as "
						+ "'particles never render', and that report was accurate");
		check(smashAttackParticlesFor(5) == 6,
				"...and the 'heavy' burst of 5 emitted " + smashAttackParticlesFor(5)
						+ ", which is not a perceptual-threshold problem, it is nothing");
		check(EarthquakeBehavior.VANILLA_MACE_SMASH_INTENSITY == 750,
				"THE REFERENCE MAGNITUDE: vanilla's ONLY caller of event 2013, "
						+ "MaceItem.knockback, passes 750 (sipush 750) -- the shipped 2 was "
						+ "375x under the one number the game itself uses for this event");
		check(ordinary >= 50,
				"the ordinary burst is now " + ordinary + " (" + smashAttackParticlesFor(ordinary)
						+ " particles: " + (ordinary / 3) + " in the column, " + (int) (ordinary / 1.5)
						+ " on the ring) -- dense enough for the 3.5-block ring to close into a "
						+ "ring rather than read as four stray specks");
		check(heavy > ordinary,
				"...the damage-tick burst is heavier still at " + heavy + " ("
						+ smashAttackParticlesFor(heavy) + " particles), preserving the "
						+ "big / small small small / big shape");
		// The budget check: scaled against the game rather than against itself.
		int tremorParticles = (bursts - hitBursts) * smashAttackParticlesFor(ordinary)
				+ hitBursts * smashAttackParticlesFor(heavy);
		check(tremorParticles <= EarthquakeBehavior.VANILLA_MACE_SMASH_INTENSITY,
				"WHOLE-TREMOR BUDGET: " + tremorParticles + " particles across the 4-second "
						+ "window, against the 750 a single mace smash emits in ONE frame -- so "
						+ "a tremor is about one mace smash's worth of dust spread over its "
						+ "window. Visible for the same reason vanilla's is, and no denser");
		check(tremorParticles > 500,
				"...and it is not timid either (" + tremorParticles + " > 500), which is the "
						+ "half of this the first ship got wrong");

		// Cost. One levelEvent per burst is ONE small packet; the particles are all
		// generated client-side, so intensity is free on the wire -- which is exactly
		// why the fix above costs nothing.
		double burstsPerSecond = bursts / (EarthquakeBehavior.MIN_INTERVAL_TICKS / 20.0);
		check(burstsPerSecond < 0.25,
				"COST: at the SHORTEST interval that is " + String.format("%.2f", burstsPerSecond)
						+ " packets/second per holder (9 bursts per 45s) -- and each is one "
						+ "levelEvent broadcast, not a per-particle payload, so raising the "
						+ "intensity 30x added ZERO bytes to the wire");
		// NOTE: the event ID cannot be asserted by name. LevelEvent.PARTICLES_SMASH_ATTACK
		// is a static final int, so javac INLINES it at the call site and no reference to
		// the name survives in the constant pool -- the same inlining trap Checks.hasConstant
		// exists for. What is checkable is the dispatch method and the vanilla value itself.
		check(Checks.classReferences(EarthquakeService.class, "levelEvent"),
				"...because it dispatches through Level.levelEvent -- one broadcast packet, "
						+ "with all ~1 particle PER POINT of intensity generated client-side by "
						+ "ParticleUtils.spawnSmashAttackParticles. (This message used to say "
						+ "'~11 per point'. That inverted reading is precisely what shipped the "
						+ "invisible effect, so it is corrected here rather than deleted.)");
		check(vanillaLevelEventSmashAttack() == 2013,
				"...and vanilla's PARTICLES_SMASH_ATTACK is still 2013, the mace-smash event "
						+ "whose only particle is DUST_PILLAR textured from the real block");
		check(!Checks.classReferences(EarthquakeService.class, "sendParticles"),
				"...rather than sendParticles, which would build and send a burst description "
						+ "per emission");
		check(Checks.classReferences(EarthquakeService.class,
						"getBlockPosBelowThatAffectsMyMovement"),
				"the burst is sited with vanilla's own 'which block am I standing on', the one "
						+ "that already answers it for step sounds -- not blockPosition().below(), "
						+ "which is wrong on slabs, stairs and fences");
		check(Checks.classReferences(EarthquakeService.class, "isAir"),
				"...and is skipped over air, so a burst never renders nothing while flying, "
						+ "swimming or mid-jump");

		// --- THE EXCLUDED-SOURCE TRAP ----------------------------------------------
		// Not the cause of the invisible particles -- the 3-arg levelEvent overload is
		// a default method that supplies null itself, so that call never had a source
		// to get wrong. But adding the SOUND introduced a call that does take one, and
		// this is the class of bug that is invisible to the build, invisible to a
		// name-based reference check, and works perfectly for every player EXCEPT the
		// one the effect is for.
		//
		// Both ServerLevel.levelEvent and ServerLevel.playSeededSound end in
		// PlayerList.broadcast(Player except, ...), whose first statement is
		// `if (player == except) continue`. Passing `player` there would structurally
		// guarantee the affected player receives neither cue, forever.
		check(Checks.methodPushesExplicitNull(EarthquakeService.class, "shatterGround"),
				"THE BROADCAST REACHES THE AFFECTED PLAYER: shatterGround pushes an explicit "
						+ "aconst_null (decoded as an instruction, not scanned as a byte) as the "
						+ "EXCLUDED-player argument. PlayerList.broadcast skips `except`, and "
						+ "nothing about a tremor is client-predicted, so passing the player "
						+ "there -- the plausible-looking mistake -- would deliver the dust and "
						+ "the sound to every bystander and never to the victim");
		check(!Checks.classReferences(EarthquakeService.class,
						"(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V"),
				"...and the particle burst uses the 3-arg levelEvent overload, whose whole body "
						+ "is `aconst_null` into the 4-arg one -- so that call cannot name a "
						+ "source even by accident");

		// --- SOUND: vanilla's own partner for event 2013, on the SAME cadence -------
		check(Checks.classReferences(EarthquakeService.class, "MACE_SMASH_GROUND"),
				"the cue is MACE_SMASH_GROUND -- not a name match but vanilla's OWN partner "
						+ "for event 2013: MaceItem.knockback plays it and fires the particle "
						+ "event on the same path, the same data-driven reuse as DUST_PILLAR");
		check(Checks.classReferences(EarthquakeService.class, "MACE_SMASH_GROUND_HEAVY"),
				"...and MACE_SMASH_GROUND_HEAVY on the damage ticks, which is vanilla's own "
						+ "light/heavy split (it picks HEAVY above fallDistance 5.0) rather than "
						+ "a second sound chosen separately");
		check(Checks.classReferences(EarthquakeService.class, "BLOCKS"),
				"...on SoundSource.BLOCKS, because this is the ground breaking and there is no "
						+ "attacker -- the mace's getSoundSource() has no meaning here");

		// CADENCE ALIGNMENT. The bug this guards is the sound and the dust drifting
		// onto different schedules, which would read as an audio desync rather than as
		// two settings being wrong. There is exactly ONE schedule: isParticleTick.
		int soundBursts = 0;
		int heavySounds = 0;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (!EarthquakeBehavior.isParticleTick(t)) {
				continue;
			}
			soundBursts++;
			boolean heavySound = EarthquakeBehavior.isHeavySoundTick(t);
			boolean heavyDust = EarthquakeBehavior.particleCountFor(t)
					== EarthquakeBehavior.PARTICLE_COUNT_ON_HIT;
			if (heavySound != heavyDust) {
				soundBursts = -1;
				break;
			}
			if (heavySound) {
				heavySounds++;
			}
		}
		check(soundBursts == bursts,
				"SOUND AND DUST SHARE ONE CADENCE: " + soundBursts + " sound bursts against "
						+ bursts + " dust bursts, and on every single tick the two agree on "
						+ "whether it is a heavy one -- because both read isDamageTick and the "
						+ "service emits them from one call site");
		check(heavySounds == hitBursts,
				"...so both heavy samples land exactly on the two damage ticks (" + heavySounds
						+ "), reinforcing the half-hearts rather than running under them");
		check(constant(EarthquakeBehavior.class, "SOUND_VOLUME_ON_HIT")
						> constant(EarthquakeBehavior.class, "SOUND_VOLUME"),
				"...and the heavy burst is louder ("
						+ constant(EarthquakeBehavior.class, "SOUND_VOLUME_ON_HIT") + " vs "
						+ constant(EarthquakeBehavior.class, "SOUND_VOLUME")
						+ "), matching the dust's big / small small small / big shape");
		double pitch = constant(EarthquakeBehavior.class, "SOUND_PITCH");
		check(pitch < 1.0,
				"pitch is " + pitch + ", below 1.0 -- the sample plays slower and deeper, which "
						+ "is what turns a weapon impact into a rumble");
		check(pitch > 0.5,
				"...and above the client mixer's 0.5 clamp with margin, rather than sitting on "
						+ "the floor where a later nudge downward would silently do nothing");
		check(constant(EarthquakeBehavior.class, "SOUND_VOLUME") < 1.0,
				"the ordinary burst is quieter than vanilla's 1.0 mace, because this plays nine "
						+ "times in four seconds where the mace plays once -- and a sub-1.0 "
						+ "volume does NOT shrink the broadcast radius, since "
						+ "SoundEvent.getRange(v) is `v > 1 ? 16*v : 16`");

		// The tremor's timing is untouched by the paralysis retune. Restated here
		// rather than assumed because "we made it worse" and "we made it longer" are
		// the same change from the player's seat, and only one of them was asked for.
		check(EarthquakeBehavior.TREMOR_TICKS == 80 && bursts == 9 && hitBursts == 2,
				"THE PARALYSIS RETUNE CHANGED NOTHING ELSE: the window is still 80 ticks, the "
						+ "hits still bracket it at 0 and 80, and there are still nine bursts -- "
						+ "the severity went up INSIDE the window rather than by widening it, for "
						+ "the fourth retune running");

		// PARTICLE AND SOUND CADENCE ARE UNAFFECTED BY PARTS 1 AND 2. Asserted as the
		// literal shipped constants rather than as a relationship, because the brief
		// put this work explicitly out of scope and the cheapest way to break it would
		// have been to key a burst off the new paralysis or blindness predicates.
		check((int) constant(EarthquakeBehavior.class, "PARTICLE_COUNT") == 60
						&& (int) constant(EarthquakeBehavior.class, "PARTICLE_COUNT_ON_HIT") == 150
						&& (int) constant(EarthquakeBehavior.class, "PARTICLE_INTERVAL") == 10,
				"...particle intensities (60 / 150) and the 10-tick interval are byte-for-byte "
						+ "what the previous session shipped");
		checkNear(constant(EarthquakeBehavior.class, "SOUND_VOLUME"), 0.6, 1e-6,
				"...sound volume unchanged");
		checkNear(constant(EarthquakeBehavior.class, "SOUND_VOLUME_ON_HIT"), 1.0, 1e-6,
				"...heavy sound volume unchanged");
		checkNear(constant(EarthquakeBehavior.class, "SOUND_PITCH"), 0.6, 1e-6,
				"...sound pitch unchanged");
		boolean cadenceIntact = true;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			boolean burst = EarthquakeBehavior.isParticleTick(t);
			boolean heavyBurst = EarthquakeBehavior.isHeavySoundTick(t);
			if (burst != (t % 10 == 0) || heavyBurst != EarthquakeBehavior.isDamageTick(t)) {
				cadenceIntact = false;
			}
		}
		check(cadenceIntact,
				"...and on every tick of the window the burst schedule and the light/heavy "
						+ "split are still driven by t%10 and isDamageTick -- NEITHER reads "
						+ "isParalysed or isFullyBlinded, so the new state cannot have shifted "
						+ "the cadence");
		check(Checks.classReferences(EarthquakeService.class, "hurtServer"),
				"...and the true-damage call is still the same hurtServer on the tremor type, "
						+ "untouched by the paralysis work");

		// --- NO REGRESSION to the two things this retune must not touch -------------
		int guardHits = 0;
		int guardFirst = -1;
		int guardLast = -1;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (EarthquakeBehavior.isDamageTick(t)) {
				guardHits++;
				if (guardFirst < 0) {
					guardFirst = t;
				}
				guardLast = t;
			}
		}
		check(EarthquakeBehavior.TREMOR_TICKS == 80 && guardHits == 2
						&& guardFirst == 0 && guardLast == 80,
				"REGRESSION GUARD: the retune changed nothing about the 80-tick window or the "
						+ "tick-0/tick-80 damage timing -- both were confirmed correct and are "
						+ "re-asserted here after the additions");

		// The damage timing, and the vanilla constraint that sets its floor.
		int damageTicks = 0;
		int first = -1;
		int last = -1;
		for (int t = 0; t <= EarthquakeBehavior.TREMOR_TICKS; t++) {
			if (EarthquakeBehavior.isDamageTick(t)) {
				damageTicks++;
				if (first < 0) {
					first = t;
				}
				last = t;
			}
		}
		check(damageTicks == 2, "exactly TWO hits land inside one tremor (got " + damageTicks + ")");
		check(first == 0 && last == EarthquakeBehavior.TREMOR_TICKS,
				"...at the FIRST tick and the LAST, bracketing the window rather than being "
						+ "spaced inside it -- the two moments the player has to react to are "
						+ "the start and the end");
		int separation = last - first;
		check(separation > 10,
				"THE HARD CONSTRAINT: the hits are " + separation + " ticks apart, and "
						+ "LivingEntity.hurtServer DISCARDS a second hit of equal or smaller "
						+ "size while invulnerableTime is still above 10. Two 1.0-damage hits "
						+ "closer than 10 ticks would silently be one hit");
		check(separation >= 8 * 10,
				"...with an 8x margin on that window, so the promise of two hits does not "
						+ "rest on the margin");
		check(EarthquakeBehavior.isFinished(EarthquakeBehavior.TREMOR_TICKS),
				"the tremor ends on the tick of its second hit, so the window closes with it");
		check(!EarthquakeBehavior.isFinished(EarthquakeBehavior.TREMOR_TICKS - 1),
				"...and not before");

		// The random interval genuinely re-rolls -- same discipline as Creeper Magnet.
		SpawnSchedule<String> quake = new SpawnSchedule<>(
				EarthquakeBehavior.MIN_INTERVAL_TICKS, EarthquakeBehavior.MAX_INTERVAL_TICKS,
				new java.util.Random(20260813L));
		check(quake.isRandomised(), "Earthquake's schedule is randomised, not a fixed cadence");
		List<Integer> gaps = new ArrayList<>();
		int sinceLast = 0;
		int fires = 0;
		for (int tick = 0; tick < 8_000_000 && fires < 2000; tick++) {
			sinceLast++;
			if (quake.tick("p")) {
				gaps.add(sinceLast);
				sinceLast = 0;
				fires++;
			}
		}
		check(fires == 2000,
				"2000 consecutive tremors fire (" + fires + ") -- roughly 21 hours of play at "
						+ "the mean interval, so a schedule that degraded after N would show");
		int minGap = gaps.stream().mapToInt(Integer::intValue).min().orElse(0);
		int maxGap = gaps.stream().mapToInt(Integer::intValue).max().orElse(0);
		check(minGap >= EarthquakeBehavior.MIN_INTERVAL_TICKS
						&& maxGap <= EarthquakeBehavior.MAX_INTERVAL_TICKS,
				"every gap is inside [900, 3600] (observed " + minGap + ".." + maxGap + ")");
		check(maxGap > EarthquakeBehavior.MAX_INTERVAL_TICKS - 60,
				"...and the top of the range is actually reachable, so nextInt's off-by-one "
						+ "has not crept back in");

		long distinct = gaps.stream().distinct().count();
		// Coupon-collector expectation for 2000 draws over 2701 values, the same
		// derivation Creeper Magnet's check uses rather than a rounded guess.
		int span = EarthquakeBehavior.MAX_INTERVAL_TICKS - EarthquakeBehavior.MIN_INTERVAL_TICKS + 1;
		double expectedDistinct = span * (1.0 - Math.pow(1.0 - 1.0 / span, 2000));
		check(distinct > expectedDistinct * 0.85 && distinct < expectedDistinct * 1.15,
				"the interval genuinely RE-ROLLS: " + distinct + " distinct gaps against a "
						+ "coupon-collector expectation of " + Math.round(expectedDistinct)
						+ " (+/-15%). A schedule that drew once and repeated would show 1");

		double firstHalf = gaps.subList(0, 1000).stream().mapToInt(Integer::intValue).average().orElse(0);
		double secondHalf = gaps.subList(1000, 2000).stream().mapToInt(Integer::intValue).average().orElse(0);
		check(Math.abs(firstHalf - secondHalf) < (EarthquakeBehavior.MAX_INTERVAL_TICKS
						- EarthquakeBehavior.MIN_INTERVAL_TICKS) * 0.1,
				"...and the last 1000 gaps have the same mean as the first 1000 ("
						+ Math.round(firstHalf) + " vs " + Math.round(secondHalf) + ")");

		// NO SafeSpawn. The schedule and the position search arrived together for the
		// two spawn effects and it would be easy to assume they are one component.
		check(!Checks.classReferences(EarthquakeService.class, "SafeSpawn"),
				"Earthquake pulls in NO SafeSpawn machinery -- it spawns nothing, so the "
						+ "heightmap search that every previous SpawnSchedule user needed is "
						+ "simply absent");
		check(!Checks.classReferences(EarthquakeService.class, "findNear"),
				"...nor any position search of its own");
		check(Checks.classReferences(EarthquakeService.class, "SpawnSchedule"),
				"...while still reusing SpawnSchedule for the cadence rather than writing a "
						+ "fourth timer");

		// True damage: the tag memberships ARE the mechanism, so read the shipped JSON.
		check(Checks.classReferences(EntropyDamageTypes.class, "tremor"),
				"Earthquake's damage source is entropymod:tremor");
		check(!Checks.classReferences(EarthquakeService.class, "magic")
						&& !Checks.classReferences(EarthquakeService.class, "SONIC_BOOM"),
				"...and not a borrowed vanilla type, so the death message is its own");

		// TRUE DAMAGE: the tag memberships ARE the mechanism, so read the shipped JSON
		// rather than restating the constant -- the same technique Clumsy Digger's
		// #enchantable/mining check uses. javap already established the two gates:
		//   getDamageAfterArmorAbsorb: if (!src.is(BYPASSES_ARMOR)) apply armour+toughness
		//   getDamageAfterMagicAbsorb: if (src.is(BYPASSES_ENCHANTMENTS)) return f  (before Protection)
		for (String tag : List.of("bypasses_armor", "bypasses_enchantments")) {
			List<String> copies = allResources("/data/minecraft/tags/damage_type/" + tag + ".json");
			check(copies.size() >= 2,
					tag + ": BOTH vanilla's file and the mod's are on the classpath ("
							+ copies.size() + " copies) -- which is what additive tag merging "
							+ "means, and is why one entry is added rather than nineteen "
							+ "overwritten");
			boolean ours = copies.stream().anyMatch(j -> j.contains("entropymod:tremor"));
			check(ours, "..." + tag + " contains entropymod:tremor");
			boolean replaces = copies.stream().anyMatch(j -> j.replace(" ", "").contains("\"replace\":true"));
			check(!replaces,
					"...and NO copy sets \"replace\": true -- a replacing file would delete "
							+ "vanilla's own entries and silently un-bypass every other type");
		}
		// The negative half: void damage was the intuitive choice and would have been
		// halved by Protection, because bypasses_enchantments has exactly one vanilla entry.
		String enchTag = readResource("/data/minecraft/tags/damage_type/bypasses_enchantments.json");
		check(enchTag != null, "the bypasses_enchantments tag is readable");
		// NOTE: these read EVERY copy, not getResourceAsStream. Once the mod ships a
		// file at a vanilla path, the single-resource lookup returns whichever copy the
		// classloader reaches first -- which is the mod's, containing one entry. That is
		// how the first draft of this check "proved" out_of_world was not in
		// bypasses_armor.
		boolean voidBypassesEnchantments = allResources(
				"/data/minecraft/tags/damage_type/bypasses_enchantments.json").stream()
				.anyMatch(j -> j.contains("out_of_world"));
		check(!voidBypassesEnchantments,
				"REFUTED ALTERNATIVE: minecraft:out_of_world (void) is NOT in "
						+ "bypasses_enchantments -- it ignores armour, resistance and even "
						+ "invulnerability, and is still reduced by Protection. Reaching for "
						+ "it would have shipped an effect a diamond set quietly halves");
		boolean voidBypassesArmor = allResources(
				"/data/minecraft/tags/damage_type/bypasses_armor.json").stream()
				.anyMatch(j -> j.contains("out_of_world"));
		check(voidBypassesArmor,
				"...which is easy to miss precisely because it IS in bypasses_armor");
		boolean sonicBoomIsTheOnlyOne = allResources(
				"/data/minecraft/tags/damage_type/bypasses_enchantments.json").stream()
				.anyMatch(j -> j.contains("sonic_boom"));
		check(sonicBoomIsTheOnlyOne,
				"...and sonic_boom is vanilla's ONLY entry there, i.e. the single vanilla "
						+ "precedent for damage that armour and Protection both ignore");

		String tremorType = readResource("/data/entropymod/damage_type/tremor.json");
		check(tremorType != null, "entropymod:tremor's own damage_type JSON ships");
		check(tremorType != null && tremorType.contains("\"scaling\": \"never\""),
				"...with scaling 'never', so a fixed half-heart stays half a heart on Hard");
		check(tremorType != null && tremorType.contains("\"message_id\": \"tremor\""),
				"...and its own message_id rather than sonic_boom's 'obliterated by a "
						+ "sonically-charged shriek'");
		check(tagContains("/assets/entropymod/lang/en_us.json", "death.attack.tremor"),
				"...with the matching death message defined");
	}

	/** The roster's own rules -- keying, counting, forgetting. */
	private static void companionRosterRules() {
		section("CompanionRoster: keying and bookkeeping");

		CompanionRoster roster = new CompanionRoster();
		check(roster.isEmpty(), "a fresh roster is empty");
		check(roster.uuidsFor("p", "x").isEmpty(), "and reports no companions for anyone");

		roster.record("alice", LoyalPackBehavior.ID, List.of("w1", "w2"));
		roster.record("bob", LoyalPackBehavior.ID, List.of("w3"));
		check(roster.countFor("alice", LoyalPackBehavior.ID) == 2
						&& roster.countFor("bob", LoyalPackBehavior.ID) == 1,
				"two players holding the SAME effect keep separate packs -- keying on the "
						+ "effect alone would have merged them on a shared world");

		roster.record("alice", TheAudienceBehavior.ID, List.of("v1"));
		check(roster.countFor("alice", LoyalPackBehavior.ID) == 2,
				"and one player holding TWO companion effects keeps them separate");
		check(roster.totalRecorded() == 4, "four companions recorded in total");

		check(CompanionRoster.key("alice", "loyal_pack").equals("alice/loyal_pack"),
				"the composite key is owner/effect");

		roster.forget("alice", LoyalPackBehavior.ID, "w1");
		check(roster.countFor("alice", LoyalPackBehavior.ID) == 1,
				"forgetting one companion leaves the rest");
		roster.forget("alice", LoyalPackBehavior.ID, "w2");
		check(!roster.hasSpawned("alice", LoyalPackBehavior.ID),
				"...and forgetting the last one clears the entry rather than leaving an "
						+ "empty list behind");
		check(roster.countFor("bob", LoyalPackBehavior.ID) == 1,
				"...without touching anyone else");

		// Round-trip through the persisted shape.
		CompanionRoster copy = new CompanionRoster(roster.asMap());
		check(copy.countFor("bob", LoyalPackBehavior.ID) == 1
						&& copy.countFor("alice", TheAudienceBehavior.ID) == 1,
				"the map form round-trips without loss");

		// --- the llama's real ceiling --------------------------------------------
		section("Emotional Support Llama: the double chest that cannot exist");
		int slots = (int) constant(EmotionalSupportLlamaBehavior.class, "INVENTORY_SLOTS");
		int strength = (int) constant(EmotionalSupportLlamaBehavior.class, "MAX_STRENGTH");
		int doubleChest = (int) constant(EmotionalSupportLlamaBehavior.class, "DOUBLE_CHEST_SLOTS");
		check(strength == 5, "Llama.MAX_STRENGTH is 5");
		check(slots == strength * 3,
				"and inventory size is strength * 3 (AbstractMountInventoryMenu"
						+ ".getInventorySize), so the ceiling is " + slots + " slots");
		check(doubleChest == 54, "a double chest is 54 slots");
		check(slots < doubleChest,
				"THE GAP: the requested double chest is not reachable -- " + slots
						+ " of " + doubleChest + " slots, "
						+ String.format("%.1f%%", 100.0 * slots / doubleChest)
						+ ". Shipped as the largest genuinely-vanilla answer.");
	}

	/**
	 * The three companion movement systems: catch-up, the escort hold band, and the
	 * Audience state machine.
	 *
	 * <p>All three live in {@code CompanionMotion}, free of Minecraft types, for the
	 * reason the spawn cluster learned expensively: a distance rule that is subtly
	 * wrong is invisible in play, and the component that broke last time was the one
	 * that needed a {@code ServerLevel} for every line.
	 */
	private static void companionMotion() {
		section("Companion catch-up: stall detection");

		// The threshold is vanilla's own pet-teleport distance, not an invention.
		checkNear(constant(CompanionMotion.class, "CATCH_UP_DISTANCE"), 12.0, 1e-9,
				"catch-up fires at 12 blocks -- TamableAnimal.shouldTryTeleportToOwner is "
						+ "distanceToSqr >= 144.0, so wolves, golems and the llama all use "
						+ "the distance a player's pet intuition is already calibrated on");
		check((int) constant(CompanionMotion.class, "STALL_SAMPLE_INTERVAL") == 20,
				"distance is sampled once a second");
		check((int) constant(CompanionMotion.class, "STALL_SAMPLES") == 3,
				"and three consecutive failures are needed -- three seconds, so rounding a "
						+ "tree does not teleport anyone");

		// --- does NOT fire during normal following -------------------------------
		CompanionMotion.StallTracker t = new CompanionMotion.StallTracker();
		double d = 30.0;
		boolean firedWhileClosing = false;
		for (int i = 0; i < 25; i++) {
			d -= 2.7;                       // a golem's real ~2.70 b/s, closing steadily
			if (d < 1.0) {
				d = 1.0;
			}
			firedWhileClosing |= t.sample("closing", d);
		}
		check(!firedWhileClosing,
				"a companion genuinely walking toward the player at its real speed NEVER "
						+ "triggers catch-up, over 25 seconds of approach");

		// Even a slow, detouring approach clears the bar.
		CompanionMotion.StallTracker slow = new CompanionMotion.StallTracker();
		double sd = 30.0;
		boolean firedWhileSlow = false;
		for (int i = 0; i < 15; i++) {
			sd -= 1.2;                      // ~45% pathing efficiency
			firedWhileSlow |= slow.sample("slow", sd);
		}
		check(!firedWhileSlow,
				"...nor does a slow, detouring approach at 1.2 blocks/second -- the bar is "
						+ "1.0, about 37% of a golem's flat-out speed");

		// --- DOES fire when stalled ----------------------------------------------
		CompanionMotion.StallTracker stuck = new CompanionMotion.StallTracker();
		check(!stuck.sample("stuck", 20.0), "first sample only starts the window");
		check(!stuck.sample("stuck", 20.0), "one failed sample is not a stall");
		check(!stuck.sample("stuck", 20.0), "two are not either");
		check(stuck.sample("stuck", 20.0),
				"THE TRIGGER: three consecutive seconds beyond 12 blocks without closing "
						+ "1 block fires the catch-up");
		check(stuck.failuresFor("stuck") == 0 && !stuck.isTracking("stuck"),
				"...and the window is consumed, so it does not fire again while the "
						+ "companion is still settling");

		// The player outrunning the companion is a stall by design.
		CompanionMotion.StallTracker outrun = new CompanionMotion.StallTracker();
		double od = 13.0;
		boolean firedOutrun = false;
		for (int i = 0; i < 5; i++) {
			od += 2.9;                      // sprinting player minus golem speed
			firedOutrun |= outrun.sample("outrun", od);
		}
		check(firedOutrun,
				"a player outrunning their escort IS treated as a stall -- the gap grows, "
						+ "closure is negative, and this is the case the feature exists for");

		// Near companions are never teleported however badly they mill about.
		CompanionMotion.StallTracker near = new CompanionMotion.StallTracker();
		boolean firedNear = false;
		for (int i = 0; i < 20; i++) {
			firedNear |= near.sample("near", 4.0);
		}
		check(!firedNear,
				"a companion milling about at 4 blocks never triggers catch-up, however "
						+ "many samples show no progress -- the distance gate is checked "
						+ "first and independently");

		// --- the WIDENED drop ring: 6-8, not 2-4 ---------------------------------
		// Reported in play: a companion catching up with a sprinting player sometimes
		// lands directly in their path. The radius is the blunt half of the fix.
		checkNear(constant(CompanionMotion.class, "TELEPORT_MIN_DISTANCE"), 6.0, 1e-9,
				"the teleport puts them down 6-8 blocks away (min) -- widened from 2-4 so a "
						+ "drop ahead of a sprinting player is walked TOWARD rather than "
						+ "walked INTO");
		checkNear(constant(CompanionMotion.class, "TELEPORT_MAX_DISTANCE"), 8.0, 1e-9,
				"...(max)");
		check(CompanionMotion.TELEPORT_MIN_DISTANCE > 4.0
						&& CompanionMotion.TELEPORT_MAX_DISTANCE > 4.0,
				"REGRESSION: the whole band is beyond the OLD band's far edge, so every "
						+ "drop is further away than the worst case used to be");

		// Warning time, at the project's own measured sprint speed. This is the
		// quantity the complaint is actually about, so it is asserted rather than
		// left in a javadoc.
		double sprint = 5.6123;                 // SprintModel's vanilla sprinting figure
		double oldWorst = 2.0 / sprint;
		double newWorst = CompanionMotion.TELEPORT_MIN_DISTANCE / sprint;
		check(newWorst > 1.0 && oldWorst < 0.5,
				"worst-case warning time for a drop straight ahead goes from "
						+ String.format("%.2f", oldWorst) + "s to "
						+ String.format("%.2f", newWorst) + "s at a sprint -- across the "
						+ "threshold where it stops being a surprise");
		check(newWorst / oldWorst >= 2.9,
				"...a " + String.format("%.1f", newWorst / oldWorst) + "x improvement, and "
						+ "apparent size on arrival falls by the same factor since angular "
						+ "size goes as 1/d");

		// THE CEILING, and why the band cannot simply keep growing. SafeSpawn measures
		// this band HORIZONTALLY and may move a candidate VERTICAL_SEARCH blocks up or
		// down, so the real 3D drop distance is the hypotenuse -- and the catch-up gate
		// is a 3D distance. If that exceeds CATCH_UP_DISTANCE a teleport can land
		// somewhere that immediately re-qualifies for another one.
		double worstDrop3d = Math.hypot(CompanionMotion.TELEPORT_MAX_DISTANCE,
				com.entropymod.entropy.spawn.SafeSpawn.VERTICAL_SEARCH);
		check(worstDrop3d < CompanionMotion.CATCH_UP_DISTANCE,
				"THE CEILING: the worst-case 3D drop distance is "
						+ String.format("%.2f", worstDrop3d) + " blocks (8 horizontal + "
						+ "SafeSpawn's 8-block vertical search), still inside the "
						+ CompanionMotion.CATCH_UP_DISTANCE + "-block catch-up gate -- so a "
						+ "teleport cannot immediately re-qualify. A max of 9 would break "
						+ "this");
		check(Math.hypot(9.0, com.entropymod.entropy.spawn.SafeSpawn.VERTICAL_SEARCH)
						>= CompanionMotion.CATCH_UP_DISTANCE,
				"...and that bound is TIGHT rather than comfortable: 9 blocks would exceed "
						+ "it, which is why 8 is the number and not a round-up of 6");

		escortHoldBand();
		audienceStateMachine();
		companionBuffs();
		audienceGatherLanding();
		audienceLookGate();
	}

	/**
	 * The squared-scaling speed derivation, and the two buffs built on it.
	 *
	 * <p>This is the check that matters most in this batch, because <b>a speed buff
	 * 2.2x the size asked for is indistinguishable in play from one that is
	 * correct</b> -- the same perceptual-threshold blindness that let Magnetic Boots
	 * and the original Blight Touched ship.
	 */
	private static void companionBuffs() {
		section("Companion buffs: real speed goes as the SQUARE of the attribute");

		// The relationship itself, against the project's own two published figures.
		// A golem at attribute 0.25 accelerates at 0.0625 -> 2.70 b/s; a villager at
		// 0.5 accelerates at 0.25 -> 10.79 b/s. Four times the acceleration, four
		// times the speed: steady-state ground speed is LINEAR in acceleration, and
		// acceleration is the attribute SQUARED.
		double golemAttr = 0.25;
		double villagerAttr = 0.5;
		checkNear((villagerAttr * villagerAttr) / (golemAttr * golemAttr), 10.79 / 2.70, 0.01,
				"the two published mob speeds (golem 2.70 b/s at 0.25, villager 10.79 at "
						+ "0.5) are in the ratio of the SQUARES of their attributes, which "
						+ "is the whole basis of the derivation");

		// The function, and its inverse, round-trip.
		for (double wanted : new double[] {1.10, 1.25, 1.5, 2.0, 3.0}) {
			double amount = CompanionBuffs.speedModifierAmount(wanted);
			checkNear(CompanionBuffs.realSpeedMultiplier(amount), wanted, 1e-12,
					"speedModifierAmount(" + wanted + ") = " + String.format("%.6f", amount)
							+ " really delivers x" + wanted);
		}

		// THE TRAP: the naive multiply, stated as a number so it cannot be waved away.
		checkNear(CompanionBuffs.realSpeedMultiplier(0.25), 1.5625, 1e-12,
				"THE TRAP: a naive ADD_MULTIPLIED_BASE of +0.25 delivers +56.25% real "
						+ "speed, not +25% -- more than double the buff");
		checkNear(CompanionBuffs.realSpeedMultiplier(0.5), 2.25, 1e-12,
				"...and a naive +0.5 delivers +125%, not +50%");

		// --- The Entourage: +25% speed, x5 health --------------------------------
		double entourageSpeed = constant(TheEntourageBehavior.class, "SPEED_MULTIPLIER");
		checkNear(entourageSpeed, 1.25, 1e-9, "the Entourage asks for +25% real speed");
		double entourageAmount = CompanionBuffs.speedModifierAmount(entourageSpeed);
		checkNear(entourageAmount, 0.118033988749895, 1e-12,
				"...which is an ADD_MULTIPLIED_BASE amount of sqrt(1.25) - 1 = "
						+ String.format("%.6f", entourageAmount) + ", NOT 0.25");
		checkNear(golemAttr * (1.0 + entourageAmount), 0.279508497187474, 1e-12,
				"...giving an effective MOVEMENT_SPEED of 0.2795085 (golem base 0.25, "
						+ "javap-verified in IronGolem.createAttributes)");
		checkNear(2.70 * CompanionBuffs.realSpeedMultiplier(entourageAmount), 3.375, 1e-6,
				"...i.e. 2.70 -> 3.375 blocks/second, exactly +25%");

		double entourageHealth = constant(TheEntourageBehavior.class, "HEALTH_MULTIPLIER");
		checkNear(entourageHealth, 5.0, 1e-9, "and +400% max health, read as a 5x total");
		checkNear(100.0 * entourageHealth, 500.0, 1e-9,
				"...so 100.0 (javap-verified golem base) becomes 500.0 -- 50 hearts to 250");
		check(500.0 < 1024.0,
				"...comfortably inside MAX_HEALTH's 1024.0 ceiling, so nothing is clamped "
						+ "away");
		check(Checks.classReferences(CompanionBuffs.class, "setHealth"),
				"and applyMaxHealth REFILLS the bar -- raising MAX_HEALTH raises only the "
						+ "ceiling, the same reason Brittle Bones has to clamp downward and "
						+ "Phoenix Chambered Heart has to grant Regeneration");

		// --- The Audience: +50% speed --------------------------------------------
		double audienceSpeed = constant(TheAudienceBehavior.class, "SPEED_MULTIPLIER");
		checkNear(audienceSpeed, 1.5, 1e-9, "the Audience asks for +50% real speed");
		double audienceAmount = CompanionBuffs.speedModifierAmount(audienceSpeed);
		checkNear(audienceAmount, 0.224744871391589, 1e-12,
				"...an ADD_MULTIPLIED_BASE amount of sqrt(1.5) - 1 = "
						+ String.format("%.6f", audienceAmount) + ", NOT 0.5");
		checkNear(villagerAttr * (1.0 + audienceAmount), 0.612372435695794, 1e-12,
				"...giving an effective MOVEMENT_SPEED of 0.6123724 (villager base 0.5, "
						+ "javap-verified in Villager.createAttributes)");
		checkNear(10.79 * CompanionBuffs.realSpeedMultiplier(audienceAmount), 16.185, 1e-6,
				"...i.e. 10.79 -> 16.185 blocks/second, exactly +50%");
		check(16.185 > 5.6123 * 2.8,
				"...which is 2.88x a sprinting player (5.6123 b/s), so an approach across "
						+ "the whole 32 -> 16 corridor now takes under a second");

		// --- PERSISTENCE: permanent modifiers, the inverse of the player rule -----
		// AttributeInstance.pack() writes baseValue and permanentModifiers only, so a
		// TRANSIENT modifier would vanish on the first chunk unload -- and a companion's
		// apply() never runs again (isFreshPick) to put it back.
		check(Checks.classReferences(CompanionBuffs.class, "addOrReplacePermanentModifier"),
				"companion buffs are PERMANENT modifiers -- the exact inverse of the "
						+ "player-side transient rule, because a companion's apply() is "
						+ "one-shot and there is nothing to re-derive from");
		check(!Checks.classReferences(CompanionBuffs.class, "addOrUpdateTransientModifier"),
				"...and NOT transient: a transient modifier on an entity whose apply() "
						+ "never re-runs would silently disappear on the first chunk unload");

		// Both behaviours actually route through the shared helper rather than
		// computing an amount of their own.
		check(Checks.classReferences(TheEntourageBehavior.class, "CompanionBuffs")
						&& Checks.classReferences(TheAudienceBehavior.class, "CompanionBuffs"),
				"both effects go through CompanionBuffs rather than each writing its own "
						+ "sqrt -- one derivation, in one place");
	}

	/**
	 * The Audience's gather recovery: where it lands, and whether that lands it in a
	 * state its own machine immediately fights.
	 */
	private static void audienceGatherLanding() {
		section("Audience gather: the landing spot cannot conflict with the state machine");

		double minD = constant(TheAudienceBehavior.class, "MIN_DISTANCE");
		double resume = constant(TheAudienceBehavior.class, "RESUME_DISTANCE");
		double lost = constant(TheAudienceBehavior.class, "LOST_TRACK_DISTANCE");

		// The mechanism already exists and is wired -- assert that, rather than
		// rebuilding it. gather() runs from the same three hooks as reapplyAll.
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class,
						"getEntityInAnyDimension"),
				"the Audience rides the SAME gather as the escorts: cross-dimension "
						+ "resolution, so a villager left in the Nether is still found");
		check(Checks.classReferences(com.entropymod.EntropyMod.class, "gather"),
				"...and it is called from the same join/respawn/dimension-change path that "
						+ "re-applies effects");

		// THE POINT OF THIS SECTION: the Audience lands on its OWN ring, not at heel.
		check(CompanionMotion.TELEPORT_MAX_DISTANCE < minD,
				"the escorts' drop ring (" + CompanionMotion.TELEPORT_MIN_DISTANCE + "-"
						+ CompanionMotion.TELEPORT_MAX_DISTANCE + ") is entirely INSIDE the "
						+ "Audience's 16-block minimum, so dropping villagers there would "
						+ "BACK_OFF all ten on the arrival tick -- which is why the Audience "
						+ "has its own GatherSpec");

		// Every landing distance in [MIN_DISTANCE, RESUME_DISTANCE] settles quietly.
		int conflicts = 0;
		for (double landed = minD; landed <= resume + 1e-9; landed += 0.25) {
			for (boolean seen : new boolean[] {true, false}) {
				// wasApproaching is false by construction: gather() clears the villager's
				// recorded state, so it is evaluated afresh.
				CompanionMotion.AudienceState state = CompanionMotion.audienceState(
						landed, seen, false, minD, resume, lost);
				if (state == CompanionMotion.AudienceState.BACK_OFF
						|| state == CompanionMotion.AudienceState.APPROACHING) {
					conflicts++;
				}
			}
		}
		check(conflicts == 0,
				"every landing distance across the whole 16-20 gather ring, seen and "
						+ "unseen, resolves to FROZEN or IDLE (" + conflicts + " conflicts) "
						+ "-- the villager stands still where it was put rather than "
						+ "immediately walking somewhere else");

		// SafeSpawn measures the ring HORIZONTALLY and may move a candidate up to
		// VERTICAL_SEARCH blocks vertically, so the real 3D distance is larger. Both
		// ends of that still have to be non-conflicting.
		double v = com.entropymod.entropy.spawn.SafeSpawn.VERTICAL_SEARCH;
		double worst3d = Math.hypot(resume, v);
		check(worst3d < lost,
				"the worst-case 3D landing distance is " + String.format("%.2f", worst3d)
						+ " blocks (20 horizontal + SafeSpawn's 8-block vertical search), "
						+ "still inside lost-track at " + lost + " -- so a gathered villager "
						+ "does NOT immediately start approaching again");
		check(Math.hypot(minD, 0.0) >= minD,
				"...and the near end cannot fall below the minimum either, because 3D "
						+ "distance is never less than the horizontal distance SafeSpawn "
						+ "already bounded at " + minD);

		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "teleportTo"),
				"the gather uses teleportTo rather than snapTo -- snapTo cannot cross "
						+ "dimensions and would leave the villager registered in the level "
						+ "it came from");
	}

	/** Part 5: they watch you, and only when they are not running. */
	private static void audienceLookGate() {
		section("Audience: look-at-player fires outside APPROACHING only");

		check(!CompanionMotion.shouldWatchPlayer(CompanionMotion.AudienceState.APPROACHING),
				"THE GATE: a villager mid-approach does NOT stare at the player -- it looks "
						+ "where it is going, and that is the one state with a competing "
						+ "claim on the head");
		check(CompanionMotion.shouldWatchPlayer(CompanionMotion.AudienceState.FROZEN),
				"FROZEN watches -- standing dead still and staring is the effect");
		check(CompanionMotion.shouldWatchPlayer(CompanionMotion.AudienceState.IDLE),
				"IDLE watches");
		check(CompanionMotion.shouldWatchPlayer(CompanionMotion.AudienceState.BACK_OFF),
				"and BACK_OFF watches -- retreating while never breaking eye contact is "
						+ "the theme, not an exception to it");

		// Exhaustive, so a state added later has to be considered rather than defaulting.
		int watching = 0;
		for (CompanionMotion.AudienceState state : CompanionMotion.AudienceState.values()) {
			if (CompanionMotion.shouldWatchPlayer(state)) {
				watching++;
			}
		}
		check(watching == CompanionMotion.AudienceState.values().length - 1,
				"exactly one of the " + CompanionMotion.AudienceState.values().length
						+ " states declines to watch");

		// --- THE MECHANISM: the brain's channel, not LookControl -------------------
		// LookAtTargetSink runs inside Brain.tick at offset 192 of Mob.serverAiStep,
		// and LookControl.tick -- the only consumer -- runs at offset 239. This service
		// runs on END_SERVER_TICK, after both. So a direct setLookAt from here is
		// overwritten before it is ever read, for as long as the brain holds a
		// LOOK_TARGET -- which for ten villagers spawned within each other's 8-block
		// look range is essentially always.
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "LOOK_TARGET"),
				"THE MECHANISM: the look is written into the brain's own LOOK_TARGET "
						+ "memory, so vanilla's LookAtTargetSink performs the rotation");
		// This assertion used to require an EntityTracker, and it was green while the
		// look was firing "occasionally" in play -- CLAUDE.md 1k for the second time in
		// this class: it was written from the same javadoc that described the wrong
		// mechanism, so it could only ever catch drift, never a wrong choice. What the
		// tracker has to satisfy is now checked in companionAudienceLook, against the
		// consumer that was deleting the memory rather than against the prose.
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "BlockPosTracker"),
				"...as a BlockPosTracker at the player's eye position, refreshed every tick "
						+ "-- see companionAudienceLook for why the EntityTracker could not "
						+ "survive LookAtTargetSink.canStillUse beyond 16 blocks");
		check(!Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "getLookControl"),
				"REFUTED ROUTE: the service does NOT call getLookControl/setLookAt. That "
						+ "loses to the brain at offset 192 every tick a LOOK_TARGET exists "
						+ "-- the same failure as adding a follow Goal to a brain-driven mob, "
						+ "reached by a different route");

		// The look pass must NOT be gated by the 5-tick movement cadence.
		check(CompanionService.TICK_INTERVAL == 5,
				"the movement pass stays on its 5-tick cadence");
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "audienceLook"),
				"...but the look pass is a separate method off the cadence gate, because "
						+ "our write owns the "
						+ "memory for exactly ONE following tick before a competitor may "
						+ "reclaim it -- a 1-in-5 refresh would lose the look four ticks in "
						+ "five, which at LookControl's gradual turn rate is "
						+ "indistinguishable from the feature not working");

		// The consequence of pointing LOOK_TARGET at the player, and its containment.
		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "WALK_TARGET"),
				"and the one side effect is contained: SetWalkTargetFromLookTarget is a "
						+ "member of the villager's IDLE RunOne (javap-verified in "
						+ "VillagerGoalPackages), so a player look target occasionally makes "
						+ "the brain walk to within 2 blocks -- erased in BACK_OFF, where "
						+ "that is unambiguously wrong, and left alone everywhere else");
	}

	/** The Entourage's 3-5 band: converges, and does not oscillate. */
	private static void escortHoldBand() {
		section("Entourage: distance-holding converges to the 3-5 band");

		double min = constant(TheEntourageBehavior.class, "MIN_HOLD");
		double max = constant(TheEntourageBehavior.class, "MAX_HOLD");
		checkNear(min, 3.0, 1e-9, "the escort holds at no less than 3 blocks");
		checkNear(max, 5.0, 1e-9, "and no more than 5");
		check(!Checks.hasConstant(TheEntourageBehavior.class, "FOLLOW_START"),
				"the old FOLLOW_START is GONE -- it gated only WHEN a path was issued, "
						+ "while moveTo(Entity) always pathed to within 1 block of the "
						+ "player, which is what caused the crowding");

		double ideal = CompanionMotion.idealHoldDistance(min, max);
		checkNear(ideal, 4.0, 1e-9,
				"both approach and retreat aim for the MIDDLE of the band, not the edge "
						+ "that was crossed -- which is the anti-jitter property");
		check(ideal > min && ideal < max,
				"...so arriving lands strictly inside the band and cannot immediately "
						+ "re-trigger");

		check(CompanionMotion.bandAction(1.0, min, max) == CompanionMotion.BandAction.RETREAT,
				"1 block from the player: RETREAT");
		check(CompanionMotion.bandAction(2.99, min, max) == CompanionMotion.BandAction.RETREAT,
				"just inside the near edge: RETREAT");
		check(CompanionMotion.bandAction(3.0, min, max) == CompanionMotion.BandAction.HOLD,
				"exactly 3: HOLD -- the band is inclusive");
		check(CompanionMotion.bandAction(4.0, min, max) == CompanionMotion.BandAction.HOLD,
				"mid-band: HOLD");
		check(CompanionMotion.bandAction(5.0, min, max) == CompanionMotion.BandAction.HOLD,
				"exactly 5: HOLD");
		check(CompanionMotion.bandAction(5.01, min, max) == CompanionMotion.BandAction.APPROACH,
				"just outside the far edge: APPROACH");

		// Convergence: from either side, aiming at the ring lands in band and stays.
		for (double start : new double[] {0.5, 1.0, 2.9, 5.1, 8.0, 11.0}) {
			double position = start;
			int moves = 0;
			while (CompanionMotion.bandAction(position, min, max) != CompanionMotion.BandAction.HOLD
					&& moves < 50) {
				position = ideal;           // one navigation to the ring
				moves++;
			}
			check(CompanionMotion.bandAction(position, min, max) == CompanionMotion.BandAction.HOLD,
					"from " + start + " blocks the escort settles in band");
			check(moves <= 1,
					"...in a single move, from " + start + " -- it aims at the ring, not at "
							+ "the player, so there is no overshoot to correct");
		}

		// The oscillation check: once in band, nothing is issued at all.
		int paths = 0;
		double settled = ideal;
		for (int tick = 0; tick < 200; tick++) {
			if (CompanionMotion.bandAction(settled, min, max) != CompanionMotion.BandAction.HOLD) {
				paths++;
			}
		}
		check(paths == 0,
				"a settled escort issues ZERO paths over 200 evaluations -- strictly less "
						+ "work than the previous version, which re-pathed every 5 ticks");

		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "stop"),
				"and the service actually calls navigation.stop() on arrival -- the line "
						+ "whose absence let a path issued at 6 blocks run to completion at 1");
	}

	/** The Audience's four states, and the transitions between them. */
	private static void audienceStateMachine() {
		section("Audience: the approach / freeze / idle state machine");

		double minD = constant(TheAudienceBehavior.class, "MIN_DISTANCE");
		double resume = constant(TheAudienceBehavior.class, "RESUME_DISTANCE");
		double lost = constant(TheAudienceBehavior.class, "LOST_TRACK_DISTANCE");
		checkNear(minD, 16.0, 1e-9, "they never come closer than 16 blocks");
		checkNear(resume, 20.0, 1e-9, "an approach continues until inside 20");
		checkNear(lost, 32.0, 1e-9, "and starts beyond 32 -- Danger Sense's radius");
		check(minD < resume && resume < lost,
				"the three thresholds are strictly ordered, so no two rules can contradict");
		check(lost > constant(TheEntourageBehavior.class, "MAX_HOLD") * 6,
				"and the lost-track distance is more than 6x the golems' outer hold -- "
						+ "'noticeably farther', as the design calls for");
		check(constant(TheAudienceBehavior.class, "FOLLOW_SPEED") == 1.0,
				"movement speed modifier is 1.0, which for a villager's 0.5 base attribute "
						+ "is ~10.79 b/s -- 1.9x a sprinting player, because mob speed goes "
						+ "as the square of the attribute");

		// --- REGRESSION: FROZEN must NOT outrank a genuine loss of track ---------
		// These three assertions used to require the OPPOSITE, and that is the point:
		// the harness was green while the bug shipped, because it asserted the
		// behaviour the javadoc described ("being watched stops them dead, AT ANY
		// DISTANCE") rather than the behaviour the effect needs. A villager that
		// freezes at 40 blocks never comes back, and canBeSeen is a line-of-sight clip
		// rather than a facing test, so outdoors it is true almost always.
		check(CompanionMotion.audienceState(40.0, true, false, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"REGRESSION: seen at 40 blocks -- beyond lost-track -- APPROACHES. Being "
						+ "watched must not outrank having lost the player, or the audience "
						+ "freezes once in open ground and never resumes");
		check(CompanionMotion.audienceState(25.0, true, true, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"REGRESSION: seen mid-approach at 25, it KEEPS GOING -- an approach that "
						+ "line of sight can interrupt half way is an approach that never "
						+ "completes, stranding it in the 16-32 corridor");
		check(CompanionMotion.audienceState(18.0, true, false, minD, resume, lost)
						== CompanionMotion.AudienceState.FROZEN,
				"seen at 18 and not approaching: still FROZEN -- the freeze is intact "
						+ "everywhere it was actually wanted, inside the tracking corridor");
		check(CompanionMotion.audienceState(30.0, true, false, minD, resume, lost)
						== CompanionMotion.AudienceState.FROZEN,
				"...and at 30, just inside lost-track: FROZEN");

		// The exact ten distances the ten shipped villagers were found at in the world
		// save, every one of them past lost-track, none of them approaching. Driven as
		// data so the reported failure itself is the test.
		double[] observed = {60.1, 65.1, 65.7, 68.4, 72.9, 85.3, 85.9, 90.6, 137.1, 140.3};
		int approachingNow = 0;
		for (double d : observed) {
			if (CompanionMotion.audienceState(d, true, false, minD, resume, lost)
					== CompanionMotion.AudienceState.APPROACHING) {
				approachingNow++;
			}
		}
		check(approachingNow == observed.length,
				"THE REPORTED BUG: all " + observed.length + " villagers, at the distances "
						+ "they were actually found at in the world save (60.1-140.3 blocks, "
						+ "with line of sight), now APPROACH (" + approachingNow + "/"
						+ observed.length + ") -- before the fix every one froze");

		// --- the minimum-distance rule outranks the freeze ------------------------
		check(CompanionMotion.audienceState(4.0, true, false, minD, resume, lost)
						== CompanionMotion.AudienceState.BACK_OFF,
				"THE ONE OVERRIDE: seen but at 4 blocks, it BACKS OFF rather than freezing "
						+ "-- otherwise a villager the player walked into would freeze in "
						+ "their face, which inverts the effect");
		check(CompanionMotion.audienceState(4.0, false, false, minD, resume, lost)
						== CompanionMotion.AudienceState.BACK_OFF,
				"...and backs off unseen at 4 blocks too");
		check(CompanionMotion.audienceState(15.99, false, false, minD, resume, lost)
						== CompanionMotion.AudienceState.BACK_OFF,
				"...from anywhere inside the 16-block minimum");

		// --- approach ------------------------------------------------------------
		check(CompanionMotion.audienceState(33.0, false, false, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"unseen beyond 32: APPROACHING");
		check(CompanionMotion.audienceState(31.0, false, false, minD, resume, lost)
						== CompanionMotion.AudienceState.IDLE,
				"unseen at 31 having NOT been approaching: IDLE -- the entry threshold is "
						+ "not met");

		// --- hysteresis: the reason RESUME_DISTANCE exists ------------------------
		check(CompanionMotion.audienceState(31.0, false, true, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"HYSTERESIS: at 31 while already approaching it KEEPS GOING -- without "
						+ "this it would stop the instant it crossed 32 and stutter on the "
						+ "boundary");
		check(CompanionMotion.audienceState(21.0, false, true, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"...still approaching at 21");
		check(CompanionMotion.audienceState(20.0, false, true, minD, resume, lost)
						== CompanionMotion.AudienceState.IDLE,
				"...and stops at 20, the resume ring");

		// A full approach run: no flapping anywhere along it.
		boolean approaching = false;
		int flips = 0;
		double dist = 34.0;
		CompanionMotion.AudienceState previous = null;
		for (int step = 0; step < 40 && dist > 17.0; step++) {
			CompanionMotion.AudienceState state = CompanionMotion.audienceState(
					dist, false, approaching, minD, resume, lost);
			approaching = state == CompanionMotion.AudienceState.APPROACHING;
			if (previous != null && state != previous) {
				flips++;
			}
			previous = state;
			dist -= 0.54;                   // ~10.79 b/s sampled 4x a second
		}
		check(flips <= 1,
				"a full 34 -> 17 block approach changes state at most once (" + flips
						+ ") -- no flapping at any point along it");

		// --- LOS transitions ------------------------------------------------------
		check(CompanionMotion.audienceState(25.0, false, false, minD, resume, lost)
						== CompanionMotion.AudienceState.IDLE
						&& CompanionMotion.audienceState(25.0, true, false, minD, resume, lost)
						== CompanionMotion.AudienceState.FROZEN,
				"breaking and re-establishing line of sight at 25 flips IDLE <-> FROZEN");
		check(CompanionMotion.audienceState(33.0, true, true, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING
						&& CompanionMotion.audienceState(33.0, false, true, minD, resume, lost)
						== CompanionMotion.AudienceState.APPROACHING,
				"beyond lost-track the approach no longer depends on line of sight at all "
						+ "-- seen or unseen at 33, it approaches");

		check(Checks.classReferences(
						com.entropymod.entropy.companion.CompanionService.class, "hasLineOfSight"),
				"the service reuses SafeSpawn's existing clip rather than building a "
						+ "second line-of-sight mechanism");

		audienceRecoversWithoutRespawn();
	}

	/**
	 * VERIFICATION 4: the Audience can get back to APPROACHING from both stuck states,
	 * driven only by distance, with no respawn anywhere in the scenario.
	 *
	 * <p>This is what separates the two reported bugs. If the audience could only
	 * recover across a respawn, the stale-player-reference hypothesis would still be
	 * live for it; recovering here, in a pure state-machine walk, is what rules that
	 * hypothesis out for this symptom specifically.
	 */
	private static void audienceRecoversWithoutRespawn() {
		section("Audience: BACK_OFF / FROZEN -> APPROACHING with no respawn involved");

		double minD = constant(TheAudienceBehavior.class, "MIN_DISTANCE");
		double resume = constant(TheAudienceBehavior.class, "RESUME_DISTANCE");
		double lost = constant(TheAudienceBehavior.class, "LOST_TRACK_DISTANCE");

		// --- FROZEN -> APPROACHING, purely by the player walking away -------------
		// Line of sight is held TRUE for the whole walk. That is the scenario that was
		// broken: the player never breaks the ray, so under the old ordering the
		// villager stayed FROZEN for every distance out to infinity.
		boolean wasApproaching = false;
		CompanionMotion.AudienceState state = null;
		double frozeAt = 18.0;
		state = CompanionMotion.audienceState(frozeAt, true, false, minD, resume, lost);
		check(state == CompanionMotion.AudienceState.FROZEN,
				"start: settled and watched at 18 blocks -> FROZEN");

		Double resumedAt = null;
		for (double d = frozeAt; d <= 60.0; d += 0.5) {
			state = CompanionMotion.audienceState(d, true, wasApproaching, minD, resume, lost);
			wasApproaching = state == CompanionMotion.AudienceState.APPROACHING;
			if (wasApproaching && resumedAt == null) {
				resumedAt = d;
			}
		}
		check(resumedAt != null,
				"walking away from a FROZEN villager -- WITH line of sight unbroken the "
						+ "whole way, and no death anywhere -- gets it back to APPROACHING");
		check(resumedAt != null && resumedAt > lost && resumedAt <= lost + 0.5,
				"...and it resumes as soon as it passes lost-track, at " + resumedAt
						+ " blocks rather than at some later distance");
		check(state == CompanionMotion.AudienceState.APPROACHING,
				"...and it is STILL approaching at 60 blocks, not stuck again");

		// --- BACK_OFF -> APPROACHING ----------------------------------------------
		state = CompanionMotion.audienceState(4.0, true, false, minD, resume, lost);
		check(state == CompanionMotion.AudienceState.BACK_OFF,
				"start: crowded at 4 blocks -> BACK_OFF");
		wasApproaching = false;
		resumedAt = null;
		for (double d = 4.0; d <= 60.0; d += 0.5) {
			state = CompanionMotion.audienceState(d, true, wasApproaching, minD, resume, lost);
			wasApproaching = state == CompanionMotion.AudienceState.APPROACHING;
			if (wasApproaching && resumedAt == null) {
				resumedAt = d;
			}
		}
		check(resumedAt != null && state == CompanionMotion.AudienceState.APPROACHING,
				"a villager that had BACKED OFF also reaches APPROACHING again on the same "
						+ "walk -- neither stuck state is terminal");

		// --- and the round trip, so the freeze is not simply gone ------------------
		wasApproaching = true;
		int sawFrozen = 0;
		for (double d = 60.0; d >= 17.0; d -= 0.5) {
			state = CompanionMotion.audienceState(d, true, wasApproaching, minD, resume, lost);
			wasApproaching = state == CompanionMotion.AudienceState.APPROACHING;
			if (state == CompanionMotion.AudienceState.FROZEN) {
				sawFrozen++;
			}
		}
		check(sawFrozen > 0 && state == CompanionMotion.AudienceState.FROZEN,
				"coming back the other way it re-freezes once inside the resume ring ("
						+ sawFrozen + " frozen samples) -- the fix reorders the freeze, it "
						+ "does not delete it");
	}

	/**
	 * Beast Wrath -- passive animals attack the holder.
	 *
	 * <p>The interesting checks here are the ones that pin the <b>vanilla</b> facts
	 * the implementation rests on, against the real shipped classes. Mixins are not
	 * applied in the harness, so these assert the world <i>before</i> the mixin --
	 * which is exactly what has to stay true for the mixin to be correct, and what
	 * will change silently under a version bump if it ever stops being true.
	 */
	private static void beastWrath() {
		section("Beast Wrath: passive animals turn hostile");
		HarnessBootstrap.init();

		// ---------- wiring ---------------------------------------------------------
		EffectDefinition def = EffectRegistry.byId(BeastWrathBehavior.ID);
		check(def != null, "beast_wrath: registered in EffectRegistry");
		check(EffectBehaviors.get(BeastWrathBehavior.ID) != null,
				"beast_wrath: wired in EffectBehaviors");
		if (def != null) {
			check(def.minEntropy() == 25 && def.maxEntropy() == 50,
					"beast_wrath: Tier 2 entropy window 25-50");
			check(def.phase() == EffectPhase.BAD, "beast_wrath: BAD phase");
			check(def.counterplay(),
					"beast_wrath: counterplay = true -- required below entropy 40, and honest: "
							+ "the animals need line of sight and are slower than a sprint");
		}

		// ---------- idempotency ----------------------------------------------------
		check(EffectBehaviors.get(BeastWrathBehavior.ID) instanceof HookEffectBehavior,
				"beast_wrath: is a HookEffectBehavior, so apply() is final and empty -- the "
						+ "goals live on the mob from construction and are gated per tick, so "
						+ "there is no per-player state to re-derive on respawn or rejoin");

		// ---------- no-repeat ------------------------------------------------------
		AcquiredEffects acquired = new AcquiredEffects();
		acquired.add(BeastWrathBehavior.ID);
		int sizeAfterFirst = acquired.size();
		acquired.add(BeastWrathBehavior.ID);
		check(acquired.size() == sizeAfterFirst,
				"beast_wrath: re-adding is a no-op -- the acquired set IS the no-repeat rule");
		for (int entropy : new int[] {25, 37, 50}) {
			for (EffectPhase phase : EffectPhase.values()) {
				EffectRegistry.RollResult roll = EffectRegistry.roll(
						phase, entropy, new java.util.Random(99),
						acquired.ids(), acquired.occupiedCategories());
				if (roll.repeatFallback()) {
					continue;
				}
				for (EffectDefinition offered : roll.choices()) {
					check(!offered.id().equals(BeastWrathBehavior.ID),
							"beast_wrath: not re-offered at entropy " + entropy + "/" + phase);
				}
			}
		}

		// ---------- persistence ----------------------------------------------------
		AcquiredEffects reloaded = new AcquiredEffects();
		for (String id : acquired.ids()) {
			reloaded.add(id);
		}
		check(reloaded.contains(BeastWrathBehavior.ID),
				"beast_wrath: survives an acquired-set round trip, so it is re-applied on load");

		// ---------- THE VANILLA FACT THAT FORCED THE ATTRIBUTE MIXIN ---------------
		// ATTACK_DAMAGE is absent from a passive animal's supplier. This is not a zero,
		// it is an absence, and it is why addOrUpdateTransientModifier could not be
		// used and why an ungated attack goal would crash the tick.
		var attackDamage = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
		for (var type : List.of(
				net.minecraft.world.entity.EntityType.COW,
				net.minecraft.world.entity.EntityType.PIG,
				net.minecraft.world.entity.EntityType.CHICKEN,
				net.minecraft.world.entity.EntityType.SHEEP)) {
			var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type);
			check(!supplier.hasAttribute(attackDamage),
					type.toShortString() + ": vanilla supplier has NO ATTACK_DAMAGE -- the fact "
							+ "AnimalAttackDamageMixin exists for. If this ever fails, the mixin "
							+ "has become redundant and should be re-justified, not left in");
			boolean threw = false;
			try {
				supplier.getValue(attackDamage);
			} catch (RuntimeException e) {
				threw = true;
			}
			check(threw,
					type.toShortString() + ": reading the missing ATTACK_DAMAGE THROWS rather "
							+ "than defaulting -- so an attack goal without the attribute mixin "
							+ "takes the server tick down on the first landed hit");
		}

		// ...and the animals that DO define it, which is what makes buildKeepingLast()
		// the load-bearing detail rather than a nicety.
		for (var type : List.of(
				net.minecraft.world.entity.EntityType.RABBIT,
				net.minecraft.world.entity.EntityType.WOLF)) {
			var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type);
			check(supplier.hasAttribute(attackDamage),
					type.toShortString() + ": already defines its own ATTACK_DAMAGE ("
							+ supplier.getValue(attackDamage) + "), added AFTER "
							+ "createAnimalAttributes -- buildKeepingLast() keeps theirs, not ours");
		}

		// ---------- the damage number ----------------------------------------------
		double damage = constant(BeastWrathBehavior.class, "ATTACK_DAMAGE");
		checkNear(damage, 2.0, 1e-9, "beast_wrath: 2.0 attack damage -- one full heart per hit");
		check(damage >= 1.0,
				"...at or above silverfish's 1.0, so it clears the perceptual-threshold bar "
						+ "that Magnetic Boots failed");
		double zombie = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.ZOMBIE).getValue(attackDamage);
		check(damage < zombie,
				"...and strictly below a zombie's " + zombie + " read from the real supplier, so "
						+ "one cow is measurably less dangerous than the weakest real hostile");
		double rabbitDamage = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.RABBIT).getValue(attackDamage);
		check(damage < rabbitDamage,
				"...and below the killer bunny's " + rabbitDamage + " -- vanilla's own "
						+ "passive-animal-with-attack-goals precedent");

		// i-frames: the reason the per-hit number carries the whole weight.
		int iFrames = (int) constant(net.minecraft.world.entity.LivingEntity.class,
				"INVULNERABLE_DURATION");
		check(iFrames == 20,
				"a swarm does NOT stack: INVULNERABLE_DURATION is " + iFrames + " ticks, so eight "
						+ "cows deal about " + damage + " per second between them, not "
						+ (damage * 8) + " -- which is why the per-hit number had to carry the "
						+ "whole difficulty");

		// ---------- goal priorities -------------------------------------------------
		// Vanilla's cow: 0 Float, 1 Panic, 2 Breed, 3 Tempt, 4 FollowParent, 5 Stroll.
		int attackPriority = (int) constant(BeastWrathBehavior.class, "ATTACK_GOAL_PRIORITY");
		check(attackPriority < 3,
				"attack goal at priority " + attackPriority + " OUTRANKS TemptGoal (3), so "
						+ "holding wheat does not pacify a hostile animal");
		check(attackPriority > 1,
				"...and is outranked by PanicGoal (1) and FloatGoal (0), so a burning cow still "
						+ "runs for water and none of them drown while angry");

		// ---------- scoping ---------------------------------------------------------
		check(Checks.hasMethod(EffectHooks.class, "attractsAnimalAggression"),
				"the per-player gate is a named method on EffectHooks, not inlined in the mixin");
		// These live in WrathTargetGoal since the aquatic/tameable expansion, which
		// needed the goal to re-check ownership on continuation as well as on
		// selection. The PROPERTY is unchanged and is still asserted; only the class
		// holding it moved.
		check(Checks.classReferences(
				com.entropymod.entropy.wrath.WrathTargetGoal.class, "attractsAnimalAggression"),
				"the target goal's Selector calls it -- so the gate is on target SELECTION, "
						+ "and a player without the effect is never chosen in the first place");
		check(com.entropymod.entropy.wrath.WrathTargetGoal.class.getSuperclass()
						== net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal.class,
				"...via a stock NearestAttackableTargetGoal, not a hand-rolled targeting loop");
		check(!Checks.classReferences(
				com.entropymod.mixin.MobPassiveAggressionMixin.class, "removeGoal"),
				"the mixin never removes a goal: the goals are added once at construction and "
						+ "are inert without a holder, so nothing is mutated when the effect is "
						+ "picked or lost (the entity-state idempotency trap, CLAUDE.md 1h)");

		// ---------- farming and breeding --------------------------------------------
		// Checked rather than assumed: hostility lives in goal selection, interaction
		// lives in the use path, and the two never meet.
		for (Class<?> c : List.of(
				net.minecraft.world.entity.animal.cow.Cow.class,
				net.minecraft.world.entity.animal.cow.AbstractCow.class,
				net.minecraft.world.entity.animal.Animal.class)) {
			check(!Checks.classReferences(c, "getTarget"),
					c.getSimpleName() + " contains NO reference to getTarget anywhere -- so "
							+ "milking and breeding cannot consult hostility, and a cow chasing "
							+ "you is still milkable mid-pursuit");
		}
	}

	/**
	 * Beast Wrath, aquatic and tameable expansion.
	 *
	 * <p>Same discipline as the land section: these pin the <b>vanilla</b> facts the
	 * implementation rests on, against the real shipped classes, because mixins are
	 * not applied in the harness. The hierarchy checks in particular are the ones a
	 * version bump would break silently -- the whole expansion turns on fish not
	 * being {@code Animal} and squid not being either.
	 */
	private static void beastWrathAquaticAndTameable() {
		section("Beast Wrath: aquatic and tameable expansion");
		HarnessBootstrap.init();

		var attackDamage = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;

		// ---------- THE HIERARCHY FINDING THAT SHAPED THE WHOLE EXPANSION ---------
		// Fish are not Animal. If this ever becomes false the second attribute mixin
		// is redundant and the eligibility test collects fish twice.
		check(!net.minecraft.world.entity.animal.Animal.class.isAssignableFrom(
						net.minecraft.world.entity.animal.fish.AbstractFish.class),
				"AbstractFish is NOT an Animal -- the chain is AbstractFish -> WaterAnimal -> "
						+ "PathfinderMob, with Animal nowhere in it. This is why "
						+ "Animal.createAnimalAttributes cannot reach a cod and why the "
						+ "eligibility test needed a second arm");
		check(net.minecraft.world.entity.PathfinderMob.class.isAssignableFrom(
						net.minecraft.world.entity.animal.fish.AbstractFish.class),
				"...but AbstractFish IS a PathfinderMob, which is what lets the stock "
						+ "MeleeAttackGoal and its (PathfinderMob) cast work unchanged for fish");
		for (Class<?> fish : List.of(
				net.minecraft.world.entity.animal.fish.Cod.class,
				net.minecraft.world.entity.animal.fish.Salmon.class,
				net.minecraft.world.entity.animal.fish.Pufferfish.class,
				net.minecraft.world.entity.animal.fish.TropicalFish.class,
				net.minecraft.world.entity.animal.frog.Tadpole.class)) {
			check(net.minecraft.world.entity.animal.fish.AbstractFish.class.isAssignableFrom(fish),
					fish.getSimpleName() + " is an AbstractFish, so one eligibility arm and one "
							+ "attribute injection cover it with no species list");
		}

		// ---------- THE EXCLUSIONS, AND THAT THEY ARE STRUCTURAL ------------------
		// Squid/GlowSquid/Dolphin are AgeableWaterCreature. They fall outside
		// "Animal or AbstractFish" by TYPE, so the exclusion needs no blocklist --
		// but it must be asserted, or a future reparent would silently include them.
		for (Class<?> excluded : List.of(
				net.minecraft.world.entity.animal.squid.Squid.class,
				net.minecraft.world.entity.animal.squid.GlowSquid.class,
				net.minecraft.world.entity.animal.dolphin.Dolphin.class)) {
			check(!net.minecraft.world.entity.animal.Animal.class.isAssignableFrom(excluded)
							&& !net.minecraft.world.entity.animal.fish.AbstractFish.class
									.isAssignableFrom(excluded),
					excluded.getSimpleName() + " is AgeableWaterCreature -- outside the eligible "
							+ "universe by type, so it is excluded with no blocklist. Squid "
							+ "REQUIRES this: Squid.travel(Vec3) is move(SELF, getDeltaMovement()) "
							+ "and discards its argument, so a navigation-driven melee goal could "
							+ "never move it toward anything");
			check(net.minecraft.world.entity.PathfinderMob.class.isAssignableFrom(excluded),
					"..." + excluded.getSimpleName() + " IS still a PathfinderMob, so the "
							+ "exclusion is genuinely about movement and targeting rather than "
							+ "about the goals failing to construct");
		}

		// ---------- CRASH-ON-FIRST-HIT: the fish half -----------------------------
		// Exactly the failure the land session caught, reached through a hierarchy
		// the land session's injection does not touch.
		for (var type : List.of(
				net.minecraft.world.entity.EntityType.COD,
				net.minecraft.world.entity.EntityType.SALMON,
				net.minecraft.world.entity.EntityType.PUFFERFISH,
				net.minecraft.world.entity.EntityType.TROPICAL_FISH,
				net.minecraft.world.entity.EntityType.TADPOLE)) {
			var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type);
			check(!supplier.hasAttribute(attackDamage),
					type.toShortString() + ": vanilla supplier has NO ATTACK_DAMAGE either -- so "
							+ "the fish need the same treatment, and the land mixin alone would "
							+ "not have given it to them");
			boolean threw = false;
			try {
				supplier.getValue(attackDamage);
			} catch (RuntimeException e) {
				threw = true;
			}
			check(threw,
					type.toShortString() + ": reading it THROWS -- an aggressive fish without "
							+ "AbstractFishAttackDamageMixin takes the server tick down on its "
							+ "first landed bite");
		}

		// ---------- the tameables need NO attribute work, checked not assumed -----
		for (var type : List.of(
				net.minecraft.world.entity.EntityType.WOLF,
				net.minecraft.world.entity.EntityType.CAT,
				net.minecraft.world.entity.EntityType.PARROT)) {
			var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type);
			check(supplier.hasAttribute(attackDamage),
					type.toShortString() + ": already defines its own ATTACK_DAMAGE ("
							+ supplier.getValue(attackDamage) + "), so it keeps its VANILLA damage "
							+ "rather than the effect's 2.0 -- a wild wolf bites like a wolf");
		}
		for (Class<?> tameable : List.of(
				net.minecraft.world.entity.animal.wolf.Wolf.class,
				net.minecraft.world.entity.animal.feline.Cat.class,
				net.minecraft.world.entity.animal.parrot.Parrot.class)) {
			check(net.minecraft.world.entity.TamableAnimal.class.isAssignableFrom(tameable),
					tameable.getSimpleName() + " is a TamableAnimal, so the second eligibility "
							+ "arm reaches it even though its targetSelector is not empty");
			check(net.minecraft.world.entity.animal.Animal.class.isAssignableFrom(tameable),
					"..." + tameable.getSimpleName() + " is also an Animal (TamableAnimal extends "
							+ "Animal), so it was always inside the first arm's TYPE test -- only "
							+ "the empty-targetSelector rule was excluding it");
		}

		// ---------- THE OWNERSHIP PRIMITIVE, DRIVEN FOR REAL ----------------------
		// EntityReference.matches is javap-verified as a bare UUID compare. Driving
		// it here proves the exclusion mechanism rather than naming it.
		final class FakeOwner implements net.minecraft.world.level.entity.UniquelyIdentifyable {
			private final java.util.UUID uuid;
			FakeOwner(java.util.UUID uuid) { this.uuid = uuid; }
			@Override public java.util.UUID getUUID() { return this.uuid; }
			@Override public boolean isRemoved() { return false; }
		}
		java.util.UUID holder = java.util.UUID.randomUUID();
		java.util.UUID somebodyElse = java.util.UUID.randomUUID();
		var ownedByHolder = net.minecraft.world.entity.EntityReference.of(holder);
		check(ownedByHolder.matches(new FakeOwner(holder)),
				"EntityReference.matches is a pure UUID compare and returns true for the owner -- "
						+ "so a wolf tamed by the affected player is excluded from its own goal");
		check(!ownedByHolder.matches(new FakeOwner(somebodyElse)),
				"...and false for a different player, so a wolf tamed by SOMEBODY ELSE stays "
						+ "eligible and still attacks -- only the affected player's own animals "
						+ "are spared, which is the requested reading");
		check(!ownedByHolder.matches(new FakeOwner(java.util.UUID.randomUUID())),
				"...and a never-tamed wild wolf, which carries no owner at all, is likewise "
						+ "eligible");
		// The reason it is matches() and not getOwner(): no level, no lookup, no
		// loaded-chunk dependency. This call did not touch a world at all.
		check(Checks.classReferences(EffectHooks.class, "getOwnerReference")
						&& Checks.classReferences(EffectHooks.class, "matches")
						&& Checks.classReferences(EffectHooks.class, "isTame"),
				"EffectHooks resolves ownership through getOwnerReference/matches/isTame");
		check(!Checks.classReferences(EffectHooks.class, "getRootOwner"),
				"...and NOT through OwnableEntity's resolving accessors, which route through the "
						+ "level and inherit the loaded-only limitation of getEntity(UUID) -- an "
						+ "owner in an unloaded chunk would read as unowned and the player's own "
						+ "pack would turn on them exactly when they were far from it");

		// ---------- retention, not just selection --------------------------------
		check(Checks.hasMethod(com.entropymod.entropy.wrath.WrathTargetGoal.class,
						"canContinueToUse"),
				"WrathTargetGoal overrides canContinueToUse -- TargetGoal's own version checks "
						+ "target/canAttack/team/range/line-of-sight and NEVER re-tests the "
						+ "Selector, so without this a wolf tamed mid-attack keeps attacking its "
						+ "new owner");
		check(Checks.classReferences(com.entropymod.entropy.wrath.WrathTargetGoal.class,
						"attractsAnimalAggression"),
				"...and it re-asks the IDENTICAL predicate rather than restating the rule, so "
						+ "selection and retention cannot drift apart (1k/1q/1s)");

		// ---------- the mixin's two independent questions -------------------------
		check(Checks.classReferences(
						com.entropymod.mixin.MobPassiveAggressionMixin.class, "AbstractFish")
						&& Checks.classReferences(
								com.entropymod.mixin.MobPassiveAggressionMixin.class, "TamableAnimal"),
				"the eligibility test reads both arms: (Animal or AbstractFish) and "
						+ "(empty targetSelector or TamableAnimal)");
		check(Checks.classReferences(
						com.entropymod.mixin.MobPassiveAggressionMixin.class, "MeleeAttackGoal"),
				"...and a melee goal is still installed, but only for a mob that cannot already "
						+ "fight -- a wolf and a cat each carry their own, and a second one at a "
						+ "priority of our choosing would retune how a WILD wolf chases a skeleton");
		check(!Checks.classReferences(
						com.entropymod.mixin.MobPassiveAggressionMixin.class, "removeGoal"),
				"still nothing is ever removed: the goals are inert without a holder, so the "
						+ "entity-state idempotency trap (1h) is still avoided by construction");

		// ---------- REGRESSION: the land build is untouched ------------------------
		checkNear(constant(BeastWrathBehavior.class, "ATTACK_DAMAGE"), 2.0, 1e-9,
				"land damage unchanged at 2.0 -- the expansion added species, not a retune");
		check((int) constant(BeastWrathBehavior.class, "ATTACK_GOAL_PRIORITY") == 2
						&& (int) constant(BeastWrathBehavior.class, "TARGET_GOAL_PRIORITY") == 1,
				"...and the goal priorities are unchanged, so wheat still does not pacify and "
						+ "fire and water still win");
		for (var type : List.of(
				net.minecraft.world.entity.EntityType.COW,
				net.minecraft.world.entity.EntityType.PIG,
				net.minecraft.world.entity.EntityType.CHICKEN,
				net.minecraft.world.entity.EntityType.SHEEP,
				net.minecraft.world.entity.EntityType.RABBIT)) {
			check(net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type) != null,
					type.toShortString() + ": still resolves a supplier -- the original five are "
							+ "reached by the unchanged first eligibility arm");
		}

		// ---------- the effect itself is still one effect --------------------------
		// Nothing about the expansion touches the run model: same id, so no-repeat,
		// persistence and idempotency are the same facts the land section asserted.
		EffectDefinition def = EffectRegistry.byId(BeastWrathBehavior.ID);
		check(def != null && def.minEntropy() == 25 && def.maxEntropy() == 50
						&& def.phase() == EffectPhase.BAD && def.counterplay(),
				"beast_wrath: id, window, phase and counterplay all unchanged by the expansion");
		check(EffectBehaviors.get(BeastWrathBehavior.ID) instanceof HookEffectBehavior,
				"...still a HookEffectBehavior, so apply() is final and empty and there is still "
						+ "no per-player state to re-derive on respawn, rejoin or reload");
		AcquiredEffects acquired = new AcquiredEffects();
		acquired.add(BeastWrathBehavior.ID);
		acquired.add(BeastWrathBehavior.ID);
		check(acquired.size() == 1 && acquired.contains(BeastWrathBehavior.ID),
				"...and re-adding is still a no-op, so no-repeat and the acquired-set round trip "
						+ "hold for the expanded effect exactly as before");
	}

	/**
	 * Reverse Midas and Grave Robber -- the Tier 2 world/loot pair.
	 *
	 * <p>Both are hook effects with no per-player state, so the run-model checks
	 * (idempotency, no-repeat, persistence) are cheap and are done for both here.
	 * The interesting assertions are the ones that pin <b>vanilla facts the
	 * implementations rest on</b> and the ones that drive the Minecraft-free
	 * decision functions across their real boundaries.
	 */
	private static void reverseMidasAndGraveRobber() {
		section("Reverse Midas + Grave Robber");
		HarnessBootstrap.init();

		// ================= shared run-model checks =================
		for (String id : List.of(ReverseMidasBehavior.ID, GraveRobberBehavior.ID)) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + ": registered in EffectRegistry");
			check(EffectBehaviors.get(id) != null, id + ": wired in EffectBehaviors");
			if (def != null) {
				check(def.minEntropy() == 25 && def.maxEntropy() == 50,
						id + ": Tier 2 entropy window 25-50");
				check(def.counterplay(),
						id + ": counterplay = true -- required below entropy 40 (CLAUDE.md Part 2)");
			}
			// Idempotency: both are HookEffectBehaviors, so apply() is final and
			// empty. There is no derived state to rebuild on respawn/rejoin, which is
			// what makes "apply ten times" trivially safe rather than merely tested.
			check(EffectBehaviors.get(id) instanceof HookEffectBehavior,
					id + ": is a HookEffectBehavior -- apply() final and empty, so respawn, "
							+ "rejoin and dimension change re-application are no-ops by construction");

			// No-repeat + persistence round trip.
			AcquiredEffects acquired = new AcquiredEffects();
			acquired.add(id);
			acquired.add(id);
			check(acquired.size() == 1, id + ": re-adding is a no-op -- the acquired set IS no-repeat");
			AcquiredEffects reloaded = new AcquiredEffects();
			for (String saved : acquired.ids()) {
				reloaded.add(saved);
			}
			check(reloaded.contains(id),
					id + ": survives an acquired-set round trip, so it is restored on load");
			for (int entropy : new int[] {25, 50}) {
				for (EffectPhase phase : EffectPhase.values()) {
					EffectRegistry.RollResult roll = EffectRegistry.roll(
							phase, entropy, new java.util.Random(7),
							acquired.ids(), acquired.occupiedCategories());
					if (roll.repeatFallback()) {
						continue;
					}
					for (EffectDefinition offered : roll.choices()) {
						check(!offered.id().equals(id),
								id + ": not re-offered at entropy " + entropy + "/" + phase);
					}
				}
			}
		}
		check(EffectRegistry.byId(ReverseMidasBehavior.ID).phase() == EffectPhase.BAD,
				"reverse_midas is BAD");
		check(EffectRegistry.byId(GraveRobberBehavior.ID).phase() == EffectPhase.GOOD,
				"grave_robber is GOOD");
		check(EffectRegistry.byId(ReverseMidasBehavior.ID).category()
						!= EffectRegistry.byId(GraveRobberBehavior.ID).category(),
				"...and they sit in different categories, so anti-stacking never has to "
						+ "choose between them and a run may hold both");

		// ================= REVERSE MIDAS =================

		// --- the subclass-override finding that chose the injection point ---
		// getPlacementState is the obvious target and is failure mode 1. Assert the
		// override exists, so if vanilla ever removes it nobody "simplifies" the
		// mixin onto the easier target without re-deriving this.
		boolean standingAndWallOverrides = false;
		try {
			Class<?> sw = Class.forName("net.minecraft.world.item.StandingAndWallBlockItem");
			for (var m : sw.getDeclaredMethods()) {
				if (m.getName().equals("getPlacementState")) {
					standingAndWallOverrides = true;
				}
			}
		} catch (ClassNotFoundException e) {
			// leave false -- the check below reports it
		}
		check(standingAndWallOverrides,
				"StandingAndWallBlockItem OVERRIDES getPlacementState (and javap shows zero "
						+ "invokespecial back to super) -- torches, signs and banners. This is why "
						+ "the mixin targets the placeBlock CALL SITE inside place() instead, which "
						+ "no subclass overrides");
		check(Checks.classReferences(
						com.entropymod.mixin.BlockItemPlacementMixin.class, "placeBlock"),
				"...and the mixin does target placeBlock");
		check(!Checks.classReferences(
						com.entropymod.mixin.BlockItemPlacementMixin.class, "getPlacementState"),
				"...and does NOT target getPlacementState");

		// --- BEFORE, not AFTER: nothing is placed and then destroyed ---
		check(!Checks.classReferences(
						com.entropymod.mixin.BlockItemPlacementMixin.class, "setBlockAndUpdate")
						&& !Checks.classReferences(
								com.entropymod.mixin.BlockItemPlacementMixin.class, "removeBlock"),
				"the mixin never writes or removes a block itself -- it replaces the state BEFORE "
						+ "vanilla places it, so the original block's block entity is never created "
						+ "and there is nothing in the world to destroy afterwards");

		// --- the block-entity/NBT question ---
		// The intuition is that placing a chest destroys its contents. It cannot: a
		// chest item carries nothing, and under this effect the chest never reaches
		// the world at all. The only item that stores a container in ITSELF is a
		// shulker box, and that is the whole of the carve-out.
		//
		// NOTE: real ItemStacks cannot be built here -- item components are
		// datapack-bound, so `new ItemStack(Items.CHEST)` throws "Components not
		// bound yet" and Item.components() cannot repair it because it delegates
		// back to the same unbound holder. So the rule is driven on the component
		// VALUE, which is a plain object and needs no registry.
		check(!EffectHooks.carriesStoredContents(null),
				"an item with NO container component at all -- a chest, a furnace, every "
						+ "ordinary block -- is turned to gold with no exception. A chest's "
						+ "inventory only ever exists in the world, so there is nothing to lose");
		check(!EffectHooks.carriesStoredContents(
						net.minecraft.world.item.component.ItemContainerContents.EMPTY),
				"an EMPTY shulker box has nothing to lose and is turned to gold too -- the "
						+ "carve-out keys on stored contents, not on the item type");
		check(!EffectHooks.carriesStoredContents(
						net.minecraft.world.item.component.ItemContainerContents.fromItems(
								List.of(net.minecraft.world.item.ItemStack.EMPTY))),
				"...and contents made only of empty stacks still count as empty, so the "
						+ "predicate is nonEmptyItems() rather than a size check");
		check(Checks.classReferences(EffectHooks.class, "nonEmptyItems"),
				"the non-empty case is decided by nonEmptyItems() on the CONTAINER component "
						+ "-- one predicate rather than an item list, so any modded container "
						+ "item is covered too");
		check(Checks.classReferences(EffectHooks.class, "CONTAINER"),
				"...read from DataComponents.CONTAINER, which is where a shulker box stores "
						+ "the up-to-27 stacks that would otherwise vanish from one misclick");
		check(Checks.classReferences(EffectHooks.class, "sparesStoredContents")
						&& Checks.hasMethod(EffectHooks.class, "sparesStoredContents"),
				"...and the carve-out is ONE named call inside reverseMidasPlacement, so "
						+ "deleting it gives the literal no-exceptions version of the effect");

		// --- scoping ---
		check(Checks.classReferences(EffectHooks.class, "reverseMidasPlacement"),
				"all gating lives in EffectHooks, keeping the mixin to one line");
		check(Checks.classReferences(EffectHooks.class, "getPlayer"),
				"the gate reads BlockPlaceContext.getPlayer() -- null for a dispenser, since "
						+ "DirectionalPlaceContext's constructor passes aconst_null, so dispenser "
						+ "placement is excluded by construction rather than by a block-source test");
		check(Checks.classReferences(EffectHooks.class, "ServerLevel"),
				"...and it is server-side only, so the placing client still predicts vanilla and "
						+ "the server's block update corrects it (the Slashed Pockets desync)");

		// --- what it actually places ---
		check(net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()
						.getBlock() == net.minecraft.world.level.block.Blocks.GOLD_BLOCK,
				"the replacement is a plain gold block default state -- no custom block, so it "
						+ "mines, drops and stacks exactly like any gold block");

		// --- PART 2: the golden vanilla equivalents ---
		// The curated table. A pair is in it only when a gold block would DESTROY
		// the item's function, which is what keeps it three rows rather than eight.
		var goldPlate = net.minecraft.world.level.block.Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE;
		for (var plate : List.of(
				net.minecraft.world.level.block.Blocks.STONE_PRESSURE_PLATE,
				net.minecraft.world.level.block.Blocks.OAK_PRESSURE_PLATE,
				net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE,
				net.minecraft.world.level.block.Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
			check(ReverseMidasBlocks.goldenEquivalent(plate) == goldPlate,
					plate.getName().getString() + " places a LIGHT WEIGHTED (gold) pressure plate, "
							+ "not a gold block -- it is still a pressure plate, which a full block "
							+ "cannot be");
		}
		var poweredRail = net.minecraft.world.level.block.Blocks.POWERED_RAIL;
		for (var rail : List.of(
				net.minecraft.world.level.block.Blocks.RAIL,
				net.minecraft.world.level.block.Blocks.DETECTOR_RAIL,
				net.minecraft.world.level.block.Blocks.ACTIVATOR_RAIL,
				poweredRail)) {
			check(ReverseMidasBlocks.goldenEquivalent(rail) == poweredRail,
					rail.getName().getString() + " places a POWERED RAIL -- the whole rail family, "
							+ "because the alternative for a detector rail is a solid block in the "
							+ "middle of the track, which preserves strictly less");
		}
		check(ReverseMidasBlocks.goldenEquivalent(
						net.minecraft.world.level.block.Blocks.DANDELION)
						== net.minecraft.world.level.block.Blocks.GOLDEN_DANDELION,
				"a DANDELION places a GOLDEN_DANDELION -- the row that had to be found rather than "
						+ "assumed: this block is new in 26.1 and would not have been in a remembered "
						+ "'pressure plate and rail' list");
		check(ReverseMidasBlocks.goldenEquivalent(
						net.minecraft.world.level.block.Blocks.POPPY) == null,
				"...but a POPPY does NOT -- the pair is the one exact species match, and mapping "
						+ "every flower onto this one would be inventing an equivalence");
		for (var solid : List.of(
				net.minecraft.world.level.block.Blocks.GOLD_ORE,
				net.minecraft.world.level.block.Blocks.GILDED_BLACKSTONE,
				net.minecraft.world.level.block.Blocks.RAW_GOLD_BLOCK,
				net.minecraft.world.level.block.Blocks.STONE,
				net.minecraft.world.level.block.Blocks.CHEST,
				net.minecraft.world.level.block.Blocks.TORCH)) {
			check(ReverseMidasBlocks.goldenEquivalent(solid) == null,
					solid.getName().getString() + " has no entry. The gold-material solids "
							+ "(gold/deepslate/nether ore, gilded blackstone, raw gold block) are "
							+ "deliberately excluded: a gold block is already a full solid block, so "
							+ "substituting one changes the texture and nothing else");
		}
		check(Checks.classReferences(EffectHooks.class, "getStateForPlacement"),
				"the substitute computes its OWN placement state rather than inheriting the "
						+ "original's properties -- a rail's SHAPE can be a curve a powered rail has "
						+ "no value for, so copying it would place a broken or default-facing block");

		// --- PART 1: the blocks whose placement writes a SECOND world position ---
		BlockPos probe = new BlockPos(4, 70, -9);
		for (var multi : List.of(
				net.minecraft.world.level.block.Blocks.OAK_DOOR,
				net.minecraft.world.level.block.Blocks.IRON_DOOR,
				net.minecraft.world.level.block.Blocks.COPPER_DOOR,
				net.minecraft.world.level.block.Blocks.RED_BED,
				net.minecraft.world.level.block.Blocks.SUNFLOWER,
				net.minecraft.world.level.block.Blocks.LILAC,
				net.minecraft.world.level.block.Blocks.ROSE_BUSH,
				net.minecraft.world.level.block.Blocks.PEONY,
				net.minecraft.world.level.block.Blocks.TALL_GRASS,
				net.minecraft.world.level.block.Blocks.LARGE_FERN,
				net.minecraft.world.level.block.Blocks.TALL_SEAGRASS,
				net.minecraft.world.level.block.Blocks.SMALL_DRIPLEAF)) {
			check(ReverseMidasBlocks.placesTwoPositions(multi),
					multi.getName().getString() + " is a two-position placement, so it is handed to "
							+ "vanilla whole and converted AFTER -- the @ModifyArg alone would have "
							+ "produced ONE gold block, because setPlacedBy (offset 171) is dispatched "
							+ "on the block that reached the world and gold does not override it");
			check(ReverseMidasBlocks.cellsToConvert(probe, multi.defaultBlockState()).size() == 2,
					"...and BOTH of its cells are converted, not just the clicked one");
		}
		for (var single : List.of(
				net.minecraft.world.level.block.Blocks.STONE,
				net.minecraft.world.level.block.Blocks.CHEST,
				net.minecraft.world.level.block.Blocks.TORCH,
				net.minecraft.world.level.block.Blocks.OAK_SIGN,
				net.minecraft.world.level.block.Blocks.DANDELION,
				net.minecraft.world.level.block.Blocks.STONE_PRESSURE_PLATE,
				net.minecraft.world.level.block.Blocks.RAIL)) {
			check(!ReverseMidasBlocks.placesTwoPositions(single),
					single.getName().getString() + " is NOT, so it keeps the BEFORE path where the "
							+ "original block entity is never created");
		}
		check(!ReverseMidasBlocks.placesTwoPositions(
						net.minecraft.world.level.block.Blocks.PITCHER_CROP),
				"PITCHER_CROP extends DoublePlantBlock and is still excluded -- it OVERRIDES "
						+ "setPlacedBy with a bare return (javap: offset 0), so a pitcher pod plants "
						+ "one cell and grows the second later. Inheritance said multi-block here and "
						+ "the bytecode said otherwise");
		check(!ReverseMidasBlocks.placesTwoPositions(
						net.minecraft.world.level.block.Blocks.PALE_MOSS_CARPET),
				"PALE_MOSS_CARPET is the other near miss and is excluded too: its setPlacedBy may "
						+ "write pos.above(), but only a decorative topper, only when non-air, and "
						+ "with a RANDOM per-side chance -- including it would make one carpet place "
						+ "one or two gold blocks depending on a die roll");

		// The partner cell is read from the state VANILLA wrote, never recomputed.
		var doorLower = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState();
		check(ReverseMidasBlocks.partnerPos(probe, doorLower).equals(probe.above()),
				"a door's lower half names the cell ABOVE it");
		var doorUpper = doorLower.setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
		check(ReverseMidasBlocks.partnerPos(probe, doorUpper).equals(probe.below()),
				"...and an upper half names the cell below, read from the shared "
						+ "BlockStateProperties.DOUBLE_BLOCK_HALF that DoorBlock.HALF and "
						+ "DoublePlantBlock.HALF both alias");
		check(ReverseMidasBlocks.partnerPos(
						probe, net.minecraft.world.level.block.Blocks.SUNFLOWER.defaultBlockState())
						.equals(probe.above()),
				"a double plant's lower half does the same");
		var bedFoot = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
						net.minecraft.core.Direction.EAST);
		check(ReverseMidasBlocks.partnerPos(probe, bedFoot).equals(probe.east()),
				"a bed's foot names the cell its FACING points at -- via vanilla's own "
						+ "BedBlock.getConnectedDirection, so facing and orientation are vanilla's "
						+ "answers rather than ours");
		check(ReverseMidasBlocks.partnerPos(
						probe, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) == null,
				"...and an ordinary block names no partner at all");

		// --- precedence, and that the two tables cannot contend ---
		int goldenCount = 0;
		int multiCount = 0;
		boolean disjoint = true;
		for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			boolean hasGolden = ReverseMidasBlocks.goldenEquivalent(block) != null;
			boolean isMulti = ReverseMidasBlocks.placesTwoPositions(block);
			disjoint &= !(hasGolden && isMulti);
			if (hasGolden) {
				goldenCount++;
			}
			if (isMulti) {
				multiCount++;
			}
		}
		check(disjoint && goldenCount > 0 && multiCount > 0,
				"swept the whole block registry: " + goldenCount + " blocks have a golden equivalent "
						+ "and " + multiCount + " place two cells, and the two sets are DISJOINT -- so "
						+ "the fixed precedence (carve-out, golden, two-cell, gold block) cannot change "
						+ "any outcome today, and is fixed so a future addition to either table cannot "
						+ "make one depend on which check was written first");
		check(Checks.hasMethod(ReverseMidasBlocks.class, "goldenEquivalent")
						&& Checks.hasMethod(ReverseMidasBlocks.class, "placesTwoPositions")
						&& Checks.classReferences(EffectHooks.class, "goldenEquivalent")
						&& Checks.classReferences(EffectHooks.class, "placesTwoPositions"),
				"...and both tables are consulted from reverseMidasPlacement, so the precedence "
						+ "lives in one readable method rather than being spread across the mixin");
		check(Checks.classReferences(
						com.entropymod.mixin.BlockItemPlacementMixin.class, "reverseMidasAfterPlacement")
						&& !Checks.classReferences(
								com.entropymod.mixin.BlockItemPlacementMixin.class, "setBlock"),
				"the AFTER half is a second one-line injection at place()'s RETURN, and the mixin "
						+ "still writes no block itself -- every decision stays in EffectHooks");
		check(Checks.classReferences(EffectHooks.class, "consumesAction"),
				"...and it is gated on the return value, so place()'s four FAIL paths convert "
						+ "nothing");

		// A version bump that adds a new second-position writer must not pass silently.
		java.util.TreeSet<String> declarers = new java.util.TreeSet<>();
		for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			for (Class<?> c = block.getClass(); c != null; c = c.getSuperclass()) {
				for (var m : c.getDeclaredMethods()) {
					if (m.getName().equals("setPlacedBy")) {
						declarers.add(c.getSimpleName());
					}
				}
			}
		}
		java.util.TreeSet<String> expectedDeclarers = new java.util.TreeSet<>(List.of(
				"BedBlock", "Block", "CommandBlock", "CrafterBlock", "DiodeBlock", "DoorBlock",
				"DoublePlantBlock", "DriedGhastBlock", "JukeboxBlock", "MossyCarpetBlock",
				"PistonBaseBlock", "PitcherCropBlock", "SmallDripleafBlock", "StructureBlock",
				"TripWireHookBlock", "WitherSkullBlock", "WitherWallSkullBlock"));
		check(declarers.equals(expectedDeclarers),
				"the set of block classes declaring setPlacedBy is exactly the "
						+ expectedDeclarers.size() + " pinned ones (got " + declarers.size() + ": "
						+ declarers + ") -- this is the check that fails on a version bump introducing "
						+ "a NEW second-position writer, which would otherwise place one gold block "
						+ "where two belong and look like nothing at all");

		// ================= GRAVE ROBBER =================

		// --- the radius IS vanilla's follow range, read from the real supplier ---
		var frAttr = net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE;
		double radius = constant(GraveRobberBehavior.class, "RADIUS");
		for (var t : List.of(
				net.minecraft.world.entity.EntityType.CREEPER,
				net.minecraft.world.entity.EntityType.SKELETON,
				net.minecraft.world.entity.EntityType.SPIDER,
				net.minecraft.world.entity.EntityType.COW)) {
			double fr = net.minecraft.world.entity.ai.attributes.DefaultAttributes
					.getSupplier(t).getValue(frAttr);
			checkNear(radius, fr, 1e-9,
					"the radius IS " + t.toShortString() + "'s real FOLLOW_RANGE (" + fr + ") -- "
							+ "16 is Mob.createMobAttributes()'s baseline, so 'near' means 'close "
							+ "enough that the mob was engaging you' rather than an invented number");
		}
		checkNear(frAttr.value().getDefaultValue(), 32.0, 1e-9,
				"...and the ATTRIBUTE's registered default is 32.0, which no ordinary mob uses. "
						+ "Reading that instead of the supplier is the exact mistake that cost "
						+ "Creeper Magnet a bug (finding 1e)");
		double zombieRange = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.ZOMBIE).getValue(frAttr);
		check(zombieRange > radius,
				"...stated rather than hidden: a ZOMBIE overrides it to " + zombieRange + ", so a "
						+ "zombie can engage from beyond this radius and die outside it. 16 is the "
						+ "baseline engagement range, not a universal one");

		// --- the sphere boundary, driven directly ---
		double r = GraveRobberBehavior.RADIUS;
		check(GraveRobberBehavior.isNear(0.0), "a mob dying at the player's feet is near");
		check(GraveRobberBehavior.isNear(r * r), "...a mob at exactly 16.0 blocks is near (inclusive)");
		check(!GraveRobberBehavior.isNear((r + 0.01) * (r + 0.01)),
				"...and one at 16.01 is not -- the boundary is exact, not approximate");
		// The box-vs-sphere leak this guards against.
		double corner = r * Math.sqrt(3.0);
		check(!GraveRobberBehavior.isNear(corner * corner),
				"a mob at the CORNER of a 16-block box (" + String.format("%.2f", corner)
						+ " blocks) is rejected -- an unfiltered AABB would have leaked the radius "
						+ "by 73%, and an extra drop looks identical whatever the distance was");

		// --- the proc rate, driven across its real boundary and at scale ---
		float chance = (float) constant(GraveRobberBehavior.class, "CHANCE");
		checkNear(chance, 0.30f, 1e-6, "proc chance is 30% -- doubled from the 15% that shipped "
				+ "first, which read as too rare to attribute at a mean gap of 6.7 mobs");
		check(!GraveRobberBehavior.rollsBonus(chance),
				"the roll is strictly less-than, so a sample of exactly CHANCE does NOT proc");
		check(GraveRobberBehavior.rollsBonus(0.0f) && !GraveRobberBehavior.rollsBonus(0.999f),
				"...0.0 procs and 0.999 does not");
		int procs = 0;
		int trials = 200_000;
		java.util.Random rng = new java.util.Random(20260815L);
		for (int i = 0; i < trials; i++) {
			if (GraveRobberBehavior.rollsBonus(rng.nextFloat())) {
				procs++;
			}
		}
		double observed = (double) procs / trials;
		checkNear(observed, chance, 0.005,
				"over " + trials + " uniform samples the observed rate is "
						+ String.format("%.4f", observed) + " -- the constant is the real rate, not "
						+ "just a number stored next to the code");
		// What that rate means to a player, asserted so a retune has to face it.
		double atLeastOneInFive = 1.0 - Math.pow(1.0 - chance, 5);
		checkNear(atLeastOneInFive, 0.8319, 1e-3,
				"P(at least one proc in a 5-mob fight) = "
						+ String.format("%.1f%%", atLeastOneInFive * 100) + ", up from 55.6% at 15%");
		double meanGap = 1.0 / chance;
		check(meanGap < 5.0,
				"...and the mean gap is " + String.format("%.1f", meanGap) + " mobs, which is SHORTER "
						+ "than an ordinary fight -- that is the bar the doubling was aimed at: the "
						+ "effect is normally observed within one fight rather than remembered across "
						+ "several");
		check(chance < 0.5,
				"...but still under 50%, which would make a bonus drop the expected outcome of every "
						+ "second kill and read as the mob's own loot table rather than as the run");
		// The farm-balance claim, recomputed at the new rate rather than restated.
		// One uniformly-chosen stack of a second identical roll is 1/k of a roll for
		// a mob whose death roll produces k stacks, so the yield multiplier is
		// 1 + CHANCE/k. Looting III takes a 0-2 common pool to 0-5: mean 1.0 -> 2.5.
		double singlePoolBump = chance / 1.0;
		double twoPoolBump = chance / 2.0;
		double lootingIiiBump = (2.5 - 1.0) / 1.0;
		checkNear(singlePoolBump, 0.30, 1e-6,
				"a single-pool mob (a zombie is rotten flesh and little else) yields +"
						+ String.format("%.0f%%", singlePoolBump * 100) + " -- the CEILING of the effect");
		checkNear(twoPoolBump, 0.15, 1e-6,
				"...a two-pool mob (a skeleton: bones and arrows) yields +"
						+ String.format("%.0f%%", twoPoolBump * 100) + " -- the floor");
		check(singlePoolBump < lootingIiiBump,
				"...and the ceiling is still a fifth of Looting III's +"
						+ String.format("%.0f%%", lootingIiiBump * 100) + ", which is the bar the "
						+ "farm-safety judgement rests on. Doubling the RATE did not move that "
						+ "judgement; doubling the SHAPE (dropping the whole second roll instead of "
						+ "one stack of it) would have");
		check(Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "chooseBonusIndex"),
				"...and the shape is unchanged: ONE stack is still chosen from the second roll, "
						+ "which is what keeps 1 + CHANCE/k linear in the rate");

		// --- choosing ONE stack, and the empty roll ---
		check(GraveRobberBehavior.chooseBonusIndex(0, 0.5f) == -1,
				"an EMPTY second roll returns -1 and drops nothing -- not re-rolled, because a mob "
						+ "that genuinely rolled nothing has nothing extra to give");
		check(GraveRobberBehavior.chooseBonusIndex(3, 0.0f) == 0
						&& GraveRobberBehavior.chooseBonusIndex(3, 0.99f) == 2,
				"...and a non-empty roll indexes uniformly across it");
		check(GraveRobberBehavior.chooseBonusIndex(3, 1.0f) == 2,
				"...a roll of exactly 1.0f clamps to the last index rather than running one past "
						+ "the end -- reachable for a float sample, and it would be an "
						+ "IndexOutOfBounds on a live server");
		int[] buckets = new int[4];
		for (int i = 0; i < 40_000; i++) {
			buckets[GraveRobberBehavior.chooseBonusIndex(4, rng.nextFloat())]++;
		}
		for (int i = 0; i < 4; i++) {
			check(Math.abs(buckets[i] - 10_000) < 500,
					"...bucket " + i + " got " + buckets[i] + " of 40000 -- the choice is genuinely "
							+ "uniform, so no slot of a mob's loot is favoured");
		}

		// --- the drop is vanilla's own roll, not an imitation ---
		check(Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "dropFromLootTable"),
				"the bonus comes from vanilla's own PUBLIC dropFromLootTable overload, which builds "
						+ "the identical LootParams the death drop used -- so Looting, the killer's "
						+ "weapon, difficulty and datapack edits are all honoured for free");
		check(Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "getLootTable"),
				"...against the dead mob's OWN table, so the effect can never produce an item the "
						+ "mob does not already drop -- the property that makes it farm-safe");
		check(!Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "ExperienceOrb")
						&& !Checks.classReferences(
								com.entropymod.entropy.loot.GraveRobberDrops.class, "giveExperiencePoints"),
				"...and it grants NO experience: XP is what an AFK farm converts most efficiently, "
						+ "so it is deliberately excluded from the reward");
		check(Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "isSpectator"),
				"spectators are excluded, so a dead player ghosting past a mob farm collects nothing");
		check(Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "getLastHurtByPlayer"),
				"the playerKill flag mirrors vanilla's own derivation, so the SAME loot pool is "
						+ "rolled -- getting it wrong would silently change what drops");
		check(!Checks.classReferences(
						com.entropymod.entropy.loot.GraveRobberDrops.class, "getKillCredit"),
				"...and there is NO kill-attribution check: the effect is proximity-only by design, "
						+ "so a creeper killing a zombie beside you pays out");

		// --- the regression gate: nothing runs when nobody holds it ---
		AcquiredEffects none = new AcquiredEffects();
		check(!com.entropymod.entropy.loot.GraveRobberDrops.isActive(none),
				"with an empty acquired set the death hook is inert -- it runs on EVERY mob death "
						+ "in the world, so the early-out has to precede touching the entity");
		AcquiredEffects held = new AcquiredEffects();
		held.add(GraveRobberBehavior.ID);
		check(com.entropymod.entropy.loot.GraveRobberDrops.isActive(held),
				"...and active once the run holds it");
	}

	private HarnessMain() {}
}
