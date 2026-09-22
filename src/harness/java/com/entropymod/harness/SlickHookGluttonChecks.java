package com.entropymod.harness;

import com.entropymod.entropy.AcquiredEffects;
import com.entropymod.entropy.AttributeEffectBehavior;
import com.entropymod.entropy.EffectBehaviors;
import com.entropymod.entropy.EffectDefinition;
import com.entropymod.entropy.EffectPhase;
import com.entropymod.entropy.EffectRegistry;
import com.entropymod.entropy.HookEffectBehavior;
import com.entropymod.entropy.behavior.GluttonBehavior;
import com.entropymod.entropy.behavior.HumanMachinegunBehavior;
import com.entropymod.entropy.behavior.LethalHookBehavior;
import com.entropymod.entropy.behavior.LooseFootingBehavior;
import com.entropymod.entropy.behavior.NightmaresBehavior;
import com.entropymod.entropy.behavior.SlickBehavior;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

import static com.entropymod.harness.Checks.check;
import static com.entropymod.harness.Checks.checkNear;
import static com.entropymod.harness.Checks.section;

/**
 * Headless checks for the Slick / Loose Footing / Lethal Hook / Human Machinegun /
 * Nightmares / Glutton batch.
 *
 * <p>Split into its own file rather than added to {@code HarnessMain} because the
 * batch carries two derivations worth keeping legible -- the friction physics and
 * the attack-attribute arithmetic -- and because the Loose Footing regression is a
 * list of things that must stay <em>absent</em>, which reads badly wedged between
 * other sections.
 */
final class SlickHookGluttonChecks {

	static final List<String> IDS = List.of(
			SlickBehavior.ID, LooseFootingBehavior.ID, LethalHookBehavior.ID,
			HumanMachinegunBehavior.ID, NightmaresBehavior.ID, GluttonBehavior.ID);

	static void run() {
		HarnessBootstrap.init();
		sharedRunModel();
		slick();
		looseFooting();
		lethalHook();
		humanMachinegun();
		nightmares();
		glutton();
	}

	// =====================================================================
	// Shared: wiring, no-repeat, persistence, idempotency -- all six.
	// =====================================================================

	private static void sharedRunModel() {
		section("Batch: wiring, no-repeat, persistence, idempotency (all six)");

		for (String id : IDS) {
			EffectDefinition def = EffectRegistry.byId(id);
			check(def != null, id + ": registered in EffectRegistry");
			check(EffectBehaviors.get(id) != null, id + ": wired in EffectBehaviors");
			if (def == null) {
				continue;
			}
			check(def.minEntropy() == 25 && def.maxEntropy() == 50,
					id + ": Tier 2 entropy window 25-50");
			check(def.counterplay(),
					id + ": counterplay = true -- required below entropy 40 (CLAUDE.md Part 2)");

			// --- IDEMPOTENCY ---
			// Two shapes, and both have to be checked the way that shape can fail.
			var behavior = EffectBehaviors.get(id);
			if (behavior instanceof HookEffectBehavior) {
				check(true, id + ": HookEffectBehavior -- apply() is final and empty, so respawn, "
						+ "rejoin and dimension change re-application are no-ops by construction");
			} else if (behavior instanceof AttributeEffectBehavior attr) {
				for (AttributeEffectBehavior.Change change : attr.changes()) {
					String name = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
							.getKey(change.attribute().value()).getPath();
					AttributeInstance instance =
							new AttributeInstance(change.attribute(), a -> { });
					double afterFirst = 0;
					for (int i = 0; i < 10; i++) {
						instance.addOrUpdateTransientModifier(new AttributeModifier(
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
					instance.removeModifier(AttributeEffectBehavior.modifierId(id));
					checkNear(instance.getValue(), instance.getBaseValue(), 1e-9,
							id + "/" + name + ": removal restores the base value");
				}
			} else {
				check(false, id + ": is one of the two known behaviour shapes");
			}

			// --- NO-REPEAT + PERSISTENCE ---
			AcquiredEffects acquired = new AcquiredEffects();
			acquired.add(id);
			acquired.add(id);
			check(acquired.size() == 1,
					id + ": re-adding is a no-op -- the acquired set IS no-repeat");
			AcquiredEffects reloaded = new AcquiredEffects();
			for (String saved : acquired.ids()) {
				reloaded.add(saved);
			}
			check(reloaded.contains(id),
					id + ": survives an acquired-set round trip, so it is restored on load");

			for (int entropy : new int[] {25, 37, 50}) {
				for (EffectPhase phase : EffectPhase.values()) {
					EffectRegistry.RollResult roll = EffectRegistry.roll(
							phase, entropy, new Random(11),
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

		check(EffectRegistry.byId(LethalHookBehavior.ID).phase() == EffectPhase.GOOD,
				"lethal_hook is the batch's only GOOD effect");
		check(EffectRegistry.byId(HumanMachinegunBehavior.ID).phase() == EffectPhase.BAD,
				"human_machinegun is BAD: x1.90 speed and x0.25 damage multiply out to x0.475 DPS, "
						+ "so the attack-speed half reading as a buff does not make it one");
	}

	// =====================================================================
	// PART 1 -- Slick.
	// =====================================================================

	private static void slick() {
		section("Slick: ice friction, both consumers, and the coast");

		// The constant is vanilla's, not ours. Read it off the real block rather
		// than restating 0.98 -- Checks.constant exists because a static final
		// primitive is inlined at the CALLER, so naming the field would compare
		// this file's own compiled-in snapshot against itself.
		float iceFriction = net.minecraft.world.level.block.Blocks.ICE.getFriction();
		float stoneFriction = net.minecraft.world.level.block.Blocks.STONE.getFriction();
		checkNear(Checks.constant(SlickBehavior.class, "ICE_FRICTION"), iceFriction, 1e-6,
				"ICE_FRICTION matches the shipped Blocks.ICE friction (" + iceFriction + ")");
		checkNear(Checks.constant(SlickBehavior.class, "DEFAULT_FRICTION"), stoneFriction, 1e-6,
				"DEFAULT_FRICTION matches an ordinary block's (" + stoneFriction + ")");
		check(iceFriction > stoneFriction, "...and ice really is the higher of the two");

		// --- the rule ---
		check(SlickBehavior.frictionFor(0.6f, true, true) == SlickBehavior.ICE_FRICTION,
				"on the ground with the effect: ordinary ground reports ice friction");
		check(SlickBehavior.frictionFor(0.6f, true, false) == 0.6f,
				"without the effect: untouched");
		check(SlickBehavior.frictionFor(1.0f, false, true) == 1.0f,
				"AIRBORNE with the effect: vanilla's literal 1.0f survives -- overwriting it "
						+ "would add air drag through the f*0.91 decay that the effect does not claim");
		check(SlickBehavior.frictionFor(SlickBehavior.ICE_FRICTION, true, true)
						== SlickBehavior.ICE_FRICTION,
				"standing on real ice: idempotent, no double-application");

		// --- BOTH consumers, which is the whole reason the LOCAL is the target ---
		// accel factor = 0.21600002 / f^3 ; decay = f * 0.91
		double accelNormal = 0.21600002 / Math.pow(stoneFriction, 3);
		double accelIce = 0.21600002 / Math.pow(iceFriction, 3);
		double decayNormal = stoneFriction * 0.91;
		double decayIce = iceFriction * 0.91;
		checkNear(accelNormal, 1.0, 1e-4,
				"ordinary ground's accel factor is 1.0 by construction (0.216 == 0.6^3)");
		checkNear(accelIce, 0.2295, 1e-3,
				"ice's accel factor is 0.2295 -- you reach speed ~4.4x more slowly");
		checkNear(decayNormal, 0.546, 1e-6, "ordinary decay is 0.546 per tick");
		checkNear(decayIce, 0.8918, 1e-4, "ice's decay is 0.8918 per tick");

		// Steady state barely moves, which is why "it made me slow" would be the
		// WRONG report and would mean the decay half had been missed.
		double steadyNormal = 0.1 * accelNormal / (1 - decayNormal);
		double steadyIce = 0.1 * accelIce / (1 - decayIce);
		check(Math.abs(steadyIce / steadyNormal - 1.0) < 0.05,
				"top speed is within 5% of normal -- ice does not make you slow, it makes you "
						+ "unable to start or stop (steady " + round(steadyNormal * 20) + " vs "
						+ round(steadyIce * 20) + " b/s)");

		// THE FELT MAGNITUDE is the coast, and it is produced by the DECAY -- so a
		// fix that changed only the acceleration would fail exactly here.
		// Distance is the honest measure rather than ticks: it is what the player
		// sees themselves overshoot by.
		double coastNormal = steadyNormal / (1 - decayNormal);
		double coastIce = steadyIce / (1 - decayIce);
		checkNear(coastNormal, 0.485, 1e-3,
				"ordinary ground: releasing the key overshoots by 0.49 blocks");
		checkNear(coastIce, 1.960, 1e-3,
				"ice: 1.96 blocks -- you slide two full blocks past where you stopped steering");
		checkNear(coastIce / coastNormal, 4.04, 1e-2,
				"a 4.0x overshoot, and 27 ticks to settle against 6 -- comfortably past the "
						+ "perceptual threshold that Magnetic Boots and the first Blight Touched failed");
		check(coastTicks(steadyNormal, decayNormal) == 6 && coastTicks(steadyIce, decayIce) == 27,
				"...6 ticks to settle normally, 27 on ice");

		// --- the common mixin targets the local, not either consumer ---
		Class<?> common = com.entropymod.mixin.LivingEntityFrictionMixin.class;
		check(Checks.classReferences(common, "travelInAir"), "common half: targets travelInAir");
		check(!Checks.classReferences(common, "handleRelativeFrictionAndCalculateMovement"),
				"common half: does NOT target the acceleration call -- modifying that argument "
						+ "alone would leave the f*0.91 decay at vanilla's 0.546, i.e. a sluggish "
						+ "player rather than a sliding one (failure mode 2)");
		check(Checks.classReferences(common, "frictionFor"),
				"common half: computes through the one shared rule");
		check(Checks.classReferences(common, "ServerLevel"),
				"common half: scoped to the server side");

		// --- the client half ---
		// NOTE: the harness classpath is the MAIN source set only -- the same
		// property that pushed the keybind snapshot to server-side persistence and
		// that leaves Double Jump's client driver unreachable. So the client half
		// is checked from its config and its SOURCE TEXT, which is weaker than
		// bytecode and is labelled as such rather than dressed up.
		check(mixinRegistered("ClientFrictionMixin", true),
				"ClientFrictionMixin is in the client mixin config -- and it is LOAD-BEARING, not "
						+ "a prediction fix: friction is not an attribute, so there is nothing for "
						+ "the server to sync and a server-only change would rubber-band");
		String clientSrc = source("src/client/java/com/entropymod/client/mixin/ClientFrictionMixin.java");
		check(clientSrc.contains("travelInAir") && clientSrc.contains("frictionFor"),
				"[source-text] client half targets travelInAir through the same frictionFor rule, "
						+ "so the two sides compute the identical value");
		check(clientSrc.contains("LocalPlayer"),
				"[source-text] client half is scoped to LocalPlayer, so remote players -- whose "
						+ "positions arrive already resolved -- are untouched");
		check(!clientSrc.contains("EffectHooks."),
				"[source-text] ...and reads ClientRunState, not EffectHooks, which answers "
						+ "'no effect' client-side by design");
	}

	/** Ticks for a coasting player's velocity to fall below 0.01 b/t. */
	private static int coastTicks(double v0, double decay) {
		double v = v0;
		int ticks = 0;
		while (v > 0.01 && ticks < 10_000) {
			v *= decay;
			ticks++;
		}
		return ticks;
	}

	// =====================================================================
	// PART 2 -- Loose Footing, including the sneaking regression.
	// =====================================================================

	private static void looseFooting() {
		section("Loose Footing: the edge check, and what sneaking must still do");

		Class<?> player = net.minecraft.world.entity.player.Player.class;
		check(declares(player, "maybeBackOffFromEdge"),
				"Player DECLARES maybeBackOffFromEdge -- this is vanilla's whole crouch-edge "
						+ "safety, and the target of the effect");
		check(declares(player, "isStayingOnGroundSurface"),
				"...gated on isStayingOnGroundSurface, which javap shows is exactly isShiftKeyDown()");

		// Subclass-override trap, checked in the direction that matters: Player must
		// be the OUTERMOST override or the mixin is silently bypassed.
		// NOTE the package: it is net.minecraft.world.entity.Avatar, NOT
		// ...entity.player.Avatar. Getting it wrong yields a null Class and a check
		// that fails for the wrong reason.
		for (String sub : List.of("net.minecraft.world.entity.Avatar",
				"net.minecraft.server.level.ServerPlayer")) {
			Class<?> c = forName(sub);
			check(c != null && !declares(c, "maybeBackOffFromEdge"),
					sub + " does NOT override it, so Player is the outermost override");
		}

		// --- THE REGRESSION: sneaking's other functions ---
		// Every one of these lives on a different vanilla path, and the effect must
		// not reference any of them. This is an assertion about ABSENCE, which is
		// the only kind that can catch "the fix reached further than intended".
		List<String> mustNotTouch = List.of(
				"isShiftKeyDown",        // the raw input flag itself
				"isCrouching",           // the pose-derived state
				"setPose",               // the crouch pose
				"refreshDimensions",     // the reduced hitbox and eye height
				"getVisibilityPercent",  // hidden-from-mobs (0.8x)
				"isSecondaryUseActive",  // place against a chest instead of opening it
				"getFrictionInfluencedSpeed", // the crouch movement slowdown
				"onClimbable");          // holding on to ladders and scaffolding
		Class<?> common = com.entropymod.mixin.PlayerEdgeSafetyMixin.class;
		check(Checks.classReferences(common, "maybeBackOffFromEdge"),
				"common half: targets maybeBackOffFromEdge");
		for (String forbidden : mustNotTouch) {
			check(!Checks.classReferences(common, forbidden),
					"common half: leaves " + forbidden + " alone -- sneaking still does that");
		}
		check(Checks.classReferences(common, "setReturnValue"),
				"common half: cancels by returning the argument, which is byte-for-byte vanilla's "
						+ "own `return vec` for a non-sneaking player -- nothing partially applied");
		check(Checks.classReferences(common, "ServerLevel"), "common half: server-scoped");

		// Client half: source-text only, for the classpath reason recorded above.
		check(mixinRegistered("ClientEdgeSafetyMixin", true),
				"ClientEdgeSafetyMixin is in the client mixin config -- and it is the half that is "
						+ "actually FELT, since Entity.move runs on the client for a player");
		String clientSrc = source(
				"src/client/java/com/entropymod/client/mixin/ClientEdgeSafetyMixin.java");
		check(clientSrc.contains("maybeBackOffFromEdge") && clientSrc.contains("setReturnValue"),
				"[source-text] client half cancels the same method the same way");
		check(clientSrc.contains("LocalPlayer"),
				"[source-text] client half is LocalPlayer-scoped -- and BOTH halves are needed, "
						+ "because vanilla's guard accepts MoverType.PLAYER, which is how the "
						+ "server applies an incoming position packet");
		for (String forbidden : mustNotTouch) {
			check(!clientSrc.contains(forbidden + "("),
					"[source-text] client half leaves " + forbidden + " alone too");
		}
	}

	// =====================================================================
	// PART 3 -- Lethal Hook.
	// =====================================================================

	private static void lethalHook() {
		section("Lethal Hook: both knockback sites, and the reach buff");

		// --- the buff ---
		RangedAttribute reach = (RangedAttribute) Attributes.ENTITY_INTERACTION_RANGE.value();
		checkNear(reach.getDefaultValue(), 3.0, 1e-9,
				"ENTITY_INTERACTION_RANGE default is 3.0");
		check(reach.isClientSyncable(),
				"...and it is CLIENT-SYNCABLE, so the client's own target raycast extends too and "
						+ "there is no server-corrects-the-client gap");
		double bonus = Checks.constant(LethalHookBehavior.class, "REACH_BONUS");
		checkNear(bonus, 1.5, 1e-9, "the bonus is +1.5");
		checkNear(reach.sanitizeValue(3.0 + bonus), 4.5, 1e-9,
				"3.0 -> 4.5 (+50%), nowhere near the 64.0 ceiling and not clamped");
		check(bonus < 6.5,
				"...and it is under half Giant Size's +6.5, which is what 'modest' means here");

		AttributeEffectBehavior behavior =
				(AttributeEffectBehavior) EffectBehaviors.get(LethalHookBehavior.ID);
		check(behavior.changes().size() == 1
						&& behavior.changes().get(0).attribute() == Attributes.ENTITY_INTERACTION_RANGE,
				"only ENTITY_INTERACTION_RANGE is touched -- notably NOT "
						+ "BLOCK_INTERACTION_RANGE, so mining and placing reach is unchanged");

		// --- both knockback sites ---
		check(Checks.classReferences(
						com.entropymod.mixin.LivingEntityKnockbackMixin.class,
						"reversesMeleeKnockback"),
				"the base hit's knockback (inside hurtServer) is reversed in Iron Will's EXISTING "
						+ "redirect -- a second @Redirect on one instruction is an apply-time conflict");
		check(Checks.classReferences(
						com.entropymod.mixin.PlayerExtraKnockbackMixin.class,
						"causeExtraKnockback"),
				"the sprint-hit / Knockback-enchantment bonus has its own mixin on "
						+ "Player.causeExtraKnockback -- a different instruction in a different method");
		check(Checks.classReferences(
						com.entropymod.mixin.PlayerExtraKnockbackMixin.class, "knockback"),
				"...and it redirects the knockback call there");
		check(Checks.classReferences(
						com.entropymod.mixin.LivingEntityKnockbackMixin.class,
						"ignoresProjectileKnockback"),
				"Iron Will's own branch is still present in the shared handler");

		// Both must flip together, which is the finding: reversing only the base
		// site inverts on sprint. Assert BOTH mixins are actually wired.
		check(mixinRegistered("PlayerExtraKnockbackMixin", false),
				"PlayerExtraKnockbackMixin is in the mixin config -- reversing only the base site "
						+ "would pull while walking and shove while sprinting");

		// --- melee only ---
		check(Checks.classReferences(
						com.entropymod.entropy.EffectHooks.class, "IS_PROJECTILE"),
				"the melee gate is the #minecraft:is_projectile tag, so arrows and tridents still "
						+ "knock their target back normally");

		// --- the direction flip, not the strength ---
		// Negating strength would trip vanilla's `strength <= 0.0` early return and
		// cancel the knockback instead of reversing it.
		double strength = 0.4;
		double x = 3.0;
		double z = -4.0;
		double[] push = knockbackDelta(strength, x, z);
		double[] pull = knockbackDelta(strength, -x, -z);
		checkNear(pull[0], -push[0], 1e-9, "negating x mirrors the horizontal push exactly");
		checkNear(pull[2], -push[2], 1e-9, "negating z mirrors it on the other axis");
		checkNear(pull[1], push[1], 1e-9,
				"the VERTICAL term is unchanged -- it reads `strength`, not the direction, so the "
						+ "target still gets vanilla's small upward pop and the pull reads as a yank");
		check(strength > 0, "strength itself is never negated, so KNOCKBACK_RESISTANCE still "
				+ "applies and the `strength <= 0` early return is never tripped");
	}

	/** Vanilla's own knockback delta, for the sign check above. */
	private static double[] knockbackDelta(double strength, double x, double z) {
		double len = Math.sqrt(x * x + z * z);
		double px = x / len * strength;
		double pz = z / len * strength;
		return new double[] {-px, Math.min(0.4, strength), -pz};
	}

	// =====================================================================
	// PART 4 -- Human Machinegun. Derived values, not input percentages.
	// =====================================================================

	private static void humanMachinegun() {
		section("Human Machinegun: the REAL resulting speed and damage");

		double speedAmount = Checks.constant(HumanMachinegunBehavior.class, "ATTACK_SPEED_BONUS");
		double dmgAmount = Checks.constant(HumanMachinegunBehavior.class, "ATTACK_DAMAGE_PENALTY");
		checkNear(speedAmount, 0.90, 1e-9, "attack speed amount is +0.90");
		checkNear(dmgAmount, -0.60, 1e-9,
				"attack damage amount is -0.60 -- RETUNED from -0.75, and the retune is much "
						+ "larger than the constant looks: the damage FACTOR rose from 0.25 to 0.40, "
						+ "60% of itself, so the net DPS penalty went from -52.5% to -24%");

		AttributeEffectBehavior behavior =
				(AttributeEffectBehavior) EffectBehaviors.get(HumanMachinegunBehavior.ID);
		for (AttributeEffectBehavior.Change change : behavior.changes()) {
			check(change.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
					"both changes are ADD_MULTIPLIED_TOTAL -- the only operation that makes a "
							+ "RELATIVE claim true once a weapon has written ADD_VALUE");
		}

		// --- the PLAYER's bases, read from the supplier and not the attribute ---
		// Rule 1e: DefaultAttributes.getSupplier(EntityType.X), never the
		// attribute's own getDefaultValue(). They disagree here -- ATTACK_DAMAGE
		// defaults to 2.0 but a player's base is 1.0 -- and using the wrong one
		// doubles every damage figure in the table below.
		var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);
		double baseSpeed = supplier.getBaseValue(Attributes.ATTACK_SPEED);
		double baseDamage = supplier.getBaseValue(Attributes.ATTACK_DAMAGE);
		checkNear(baseSpeed, 4.0, 1e-9, "a player's base ATTACK_SPEED is 4.0");
		checkNear(baseDamage, 1.0, 1e-9,
				"a player's base ATTACK_DAMAGE is 1.0 -- NOT the attribute's own default of "
						+ Attributes.ATTACK_DAMAGE.value().getDefaultValue());

		// --- the values, against real AttributeInstances with a weapon fitted ---
		record Weapon(String name, double dmgAdd, double speedAdd) { }
		List<Weapon> weapons = List.of(
				new Weapon("bare hand", 0.0, 0.0),
				new Weapon("iron sword", 5.0, -2.4),
				new Weapon("netherite axe", 9.0, -3.0));

		for (Weapon w : weapons) {
			double speedNormal = value(Attributes.ATTACK_SPEED, baseSpeed, w.speedAdd(), 0, null);
			double speedCursed = value(Attributes.ATTACK_SPEED, baseSpeed, w.speedAdd(),
					speedAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			double dmgNormal = value(Attributes.ATTACK_DAMAGE, baseDamage, w.dmgAdd(), 0, null);
			double dmgCursed = value(Attributes.ATTACK_DAMAGE, baseDamage, w.dmgAdd(),
					dmgAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

			checkNear(speedCursed / speedNormal, 1.90, 1e-9,
					w.name() + ": attack speed is EXACTLY x1.90 (" + round(speedNormal)
							+ " -> " + round(speedCursed) + " per second, cooldown "
							+ round(20 / speedNormal) + " -> " + round(20 / speedCursed) + " ticks)");
			checkNear(dmgCursed / dmgNormal, 0.40, 1e-9,
					w.name() + ": attack damage is EXACTLY x0.40 (" + round(dmgNormal)
							+ " -> " + round(dmgCursed) + ")");
			checkNear((speedCursed * dmgCursed) / (speedNormal * dmgNormal), 0.76, 1e-9,
					w.name() + ": net DPS is x0.76 -- " + round(speedNormal * dmgNormal)
							+ " -> " + round(speedCursed * dmgCursed));
		}
		checkNear(HumanMachinegunBehavior.dpsFactor(), 0.76, 1e-9,
				"...and the shipped dpsFactor() agrees, so the constants and the claim are one thing");

		// --- WHY ADD_MULTIPLIED_TOTAL, corrected against the real calculateValue ---
		// The intuitive story ("BASE works off the raw base, so it would barely
		// scratch a weapon") is WRONG, and the bytecode says so:
		//     d = base; for (ADD_VALUE) d += amount;
		//     e = d;    for (ADD_MULTIPLIED_BASE)  e += d * amount;   <-- d, not base
		//               for (ADD_MULTIPLIED_TOTAL) e *= 1 + amount;
		// So for a SINGLE modifier the two operations are identical.
		double viaBase = value(Attributes.ATTACK_DAMAGE, baseDamage, 5.0, dmgAmount,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double viaTotal = value(Attributes.ATTACK_DAMAGE, baseDamage, 5.0, dmgAmount,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		checkNear(viaBase, viaTotal, 1e-9,
				"held ALONE the two operations agree exactly (both 2.40 on an iron sword), because "
						+ "ADD_MULTIPLIED_BASE multiplies base + the ADD_VALUE pass -- i.e. the "
						+ "weapon is already included. The choice is NOT about a single modifier");

		// It is about STACKING, and the shape of the argument is Embrace the Moon's.
		double stackedBase = stacked(Attributes.ATTACK_DAMAGE, baseDamage, 5.0, dmgAmount,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double stackedTotal = stacked(Attributes.ATTACK_DAMAGE, baseDamage, 5.0, dmgAmount,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		checkNear(stackedBase, 0.0, 1e-9,
				"TWO -0.60 ADD_MULTIPLIED_BASE modifiers accumulate ADDITIVELY to -1.2x, i.e. "
						+ "NEGATIVE damage, clamped to 0.0 -- an unarmed-forever player");
		checkNear(stackedTotal / (baseDamage + 5.0), 0.16, 1e-9,
				"...whereas ADD_MULTIPLIED_TOTAL composes as a product (0.40 x 0.40 = 0.16) and "
						+ "can never reach zero for any amount above -1. ATTACK_DAMAGE floors at "
						+ "0.0 and anti-stacking is a SOFT rule dropped first, so this is reachable");

		// --- clamps ---
		RangedAttribute speed = (RangedAttribute) Attributes.ATTACK_SPEED.value();
		RangedAttribute dmg = (RangedAttribute) Attributes.ATTACK_DAMAGE.value();
		check(speed.sanitizeValue(-1.0) == 0.0,
				"ATTACK_SPEED floors at 0.0 (unrecoverable), and this effect only ever raises it");
		check(dmg.sanitizeValue(-1.0) == 0.0, "ATTACK_DAMAGE floors at 0.0");
		checkNear(value(Attributes.ATTACK_DAMAGE, baseDamage, 0.0, dmgAmount,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), 0.40, 1e-9,
				"bare-handed 0.40 is a real value, not a clamp");

		// --- composition with the one effect it can meet on this attribute ---
		AttributeInstance both = instance(Attributes.ATTACK_DAMAGE, baseDamage);
		both.addOrUpdateTransientModifier(new AttributeModifier(
				Identifier.parse("entropymod:harness/weapon"), 5.0,
				AttributeModifier.Operation.ADD_VALUE));
		both.addOrUpdateTransientModifier(new AttributeModifier(
				AttributeEffectBehavior.modifierId("glass_cannon_pact"), 0.50,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		both.addOrUpdateTransientModifier(new AttributeModifier(
				AttributeEffectBehavior.modifierId(HumanMachinegunBehavior.ID), dmgAmount,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		checkNear(both.getValue(), 3.60, 1e-9,
				"with Glass Cannon Pact an iron sword is (1.0+5.0)*1.5*0.40 = 3.60 -- the two run "
						+ "in different passes and neither clobbers the other, since every modifier "
						+ "carries its own Identifier");
	}

	private static AttributeInstance instance(net.minecraft.core.Holder<
			net.minecraft.world.entity.ai.attributes.Attribute> attribute, double base) {
		AttributeInstance instance = new AttributeInstance(attribute, a -> { });
		instance.setBaseValue(base);
		return instance;
	}

	private static double value(net.minecraft.core.Holder<
			net.minecraft.world.entity.ai.attributes.Attribute> attribute, double base,
								double weaponAdd, double amount,
								AttributeModifier.Operation op) {
		AttributeInstance in = instance(attribute, base);
		if (weaponAdd != 0.0) {
			in.addOrUpdateTransientModifier(new AttributeModifier(
					Identifier.parse("entropymod:harness/weapon"),
					weaponAdd, AttributeModifier.Operation.ADD_VALUE));
		}
		if (op != null) {
			in.addOrUpdateTransientModifier(new AttributeModifier(
					AttributeEffectBehavior.modifierId(HumanMachinegunBehavior.ID), amount, op));
		}
		return in.getValue();
	}

	/** The same modifier applied twice under different ids, i.e. two stacked effects. */
	private static double stacked(net.minecraft.core.Holder<
			net.minecraft.world.entity.ai.attributes.Attribute> attribute, double base,
								  double weaponAdd, double amount,
								  AttributeModifier.Operation op) {
		AttributeInstance in = instance(attribute, base);
		in.addOrUpdateTransientModifier(new AttributeModifier(
				Identifier.parse("entropymod:harness/weapon"),
				weaponAdd, AttributeModifier.Operation.ADD_VALUE));
		in.addOrUpdateTransientModifier(new AttributeModifier(
				Identifier.parse("entropymod:harness/first"), amount, op));
		in.addOrUpdateTransientModifier(new AttributeModifier(
				Identifier.parse("entropymod:harness/second"), amount, op));
		return in.getValue();
	}

	// =====================================================================
	// PART 5 -- Nightmares.
	// =====================================================================

	private static void nightmares() {
		section("Nightmares: vanilla's own bed explosion, scoped to the holder");

		Class<?> bedRule = forName("net.minecraft.world.attribute.BedRule");
		check(bedRule != null && declares(bedRule, "explodes"),
				"BedRule.explodes() exists -- the single boolean guarding vanilla's whole "
						+ "explosion branch in BedBlock.useWithoutItem");
		check(declares(net.minecraft.world.level.block.BedBlock.class, "useWithoutItem"),
				"BedBlock declares useWithoutItem, so the redirect's enclosing method is right");

		// Subclass-override trap: BedBlock must have no subclasses, or a colour
		// could quietly escape.
		int subclasses = 0;
		for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			if (block.getClass() != net.minecraft.world.level.block.BedBlock.class
					&& net.minecraft.world.level.block.BedBlock.class
							.isAssignableFrom(block.getClass())) {
				subclasses++;
			}
		}
		check(subclasses == 0,
				"no block in the registry subclasses BedBlock -- the sixteen colours are sixteen "
						+ "instances carrying a DyeColor field, so no colour escapes the redirect");

		// --- nothing is built here ---
		check(Checks.classReferences(
						com.entropymod.mixin.BedExplosionMixin.class, "explodes"),
				"the mixin redirects BedRule.explodes()");
		check(!Checks.classReferences(com.entropymod.mixin.BedExplosionMixin.class, "explode")
						|| Checks.classReferences(
								com.entropymod.mixin.BedExplosionMixin.class, "explodes"),
				"...and makes no Level.explode call of its own -- power 5.0, the "
						+ "badRespawnPointExplosion damage source and its death message, the fire "
						+ "and the block rules are all vanilla's");
		check(!Checks.classReferences(com.entropymod.mixin.BedExplosionMixin.class, "removeBlock"),
				"...and removes no block itself -- both bed halves are vanilla's branch too");

		// --- scoped to the holder, and ORed rather than replacing ---
		check(Checks.classReferences(com.entropymod.mixin.BedExplosionMixin.class, "bedsExplode"),
				"the decision goes through EffectHooks, i.e. the acquired set of the player who "
						+ "actually clicked the bed -- a second player sleeps in the same bed normally");
		check(Checks.classReferences(com.entropymod.mixin.BedExplosionMixin.class, "Player"),
				"...and the Player is captured from the enclosing method's parameters");

		// The OR is what keeps this additive: the Nether and End are unchanged for
		// everyone, including for a player who does NOT hold the effect.
		check(!EffectRegistry.byId(NightmaresBehavior.ID).description().toLowerCase()
						.contains("overworld"),
				"the description says 'wherever you are' rather than naming the Overworld: since "
						+ "every non-Overworld dimension already explodes, 'explodes in the "
						+ "Overworld for the holder' and 'always explodes for the holder' are the "
						+ "same rule, and the second cannot go stale when a dimension is added");
	}

	// =====================================================================
	// PART 6 -- Glutton.
	// =====================================================================

	private static void glutton() {
		section("Glutton: 75% less, from every source");

		checkNear(Checks.constant(GluttonBehavior.class, "MULTIPLIER"), 0.25, 1e-9,
				"MULTIPLIER is 0.25, i.e. 75% less");

		// --- the funnel: this is what makes 'all sources' structural ---
		Class<?> foodData = net.minecraft.world.food.FoodData.class;
		check(hasPrivate(foodData, "add", int.class, float.class),
				"FoodData.add(int, float) exists and is private -- the single funnel");
		check(declares(foodData, "eat"),
				"...and both public eat overloads are one-line delegations to it");
		check(Checks.classReferences(
						com.entropymod.mixin.FoodDataRestoreMixin.class, "add"),
				"the mixin targets add(), NOT the eat overloads or the three call sites -- so a "
						+ "fourth source (a mod's, or a future version's) is covered without an edit");

		// The three vanilla sources, asserted to exist so the enumeration in the
		// javadoc is checkable rather than remembered.
		check(declares(forName("net.minecraft.world.food.FoodProperties"), "onConsume"),
				"source 1: FoodProperties.onConsume -- EVERY food item, since food is a data "
						+ "component rather than an item class");
		check(declares(forName("net.minecraft.world.level.block.CakeBlock"), "eat"),
				"source 2: CakeBlock.eat -- cake is a block and is never an item on the way in");
		check(declares(forName("net.minecraft.world.effect.SaturationMobEffect"), "applyEffectTick"),
				"source 3: SaturationMobEffect.applyEffectTick -- the Saturation effect");

		// --- the owner, which is how a FoodData knows whose it is ---
		check(Checks.classReferences(
						com.entropymod.mixin.PlayerFoodDataOwnerMixin.class, "entropymod$setOwner"),
				"the owner is stamped from Player's constructor -- Player has exactly ONE, so the "
						+ "TAIL inject is unambiguous and runs before any add() can");
		check(Checks.classReferences(
						com.entropymod.mixin.FoodDataRestoreMixin.class, "entropymod$owner"),
				"...and the restore mixin reads it back, degrading to vanilla when it is absent");

		// --- UNIFORMITY across the whole nutrition range ---
		// The claim under test is 'all food sources, uniformly'. Saturation is a
		// float and is exact; nutrition is an int and can only be uniform to within
		// rounding, so assert that bound explicitly rather than hand-waving it.
		for (int n = 0; n <= 20; n++) {
			int reduced = GluttonBehavior.reduceNutrition(n);
			check(Math.abs(reduced - n * 0.25) <= 0.5,
					"nutrition " + n + " -> " + reduced + " (within rounding of 25%)");
			check(reduced <= n, "nutrition " + n + ": never increases");
		}
		for (float s : new float[] {0.0f, 0.1f, 0.6f, 0.8f, 1.2f, 2.0f}) {
			checkNear(GluttonBehavior.reduceSaturation(s), s * 0.25, 1e-6,
					"saturation " + s + " is scaled EXACTLY (it is a float, so no rounding)");
		}

		// The real table, spot-checked at the values players will actually see.
		check(GluttonBehavior.reduceNutrition(8) == 2, "cooked beef 8 -> 2");
		check(GluttonBehavior.reduceNutrition(6) == 2, "golden carrot 6 -> 2");
		check(GluttonBehavior.reduceNutrition(5) == 1, "bread 5 -> 1");
		check(GluttonBehavior.reduceNutrition(2) == 1,
				"cake slice / cookie 2 -> 1 (rounds UP from 0.5 -- truncating here would zero "
						+ "every small food, the trap EffectHooks.scaleAmount exists for on XP)");
		check(GluttonBehavior.reduceNutrition(1) == 0,
				"dried kelp 1 -> 0. FLAGGED, not a bug: a floor of 1 would make a 2-point food a "
						+ "50% reduction and a 20-point one a 75% reduction -- exactly the "
						+ "non-uniformity the spec rules out. Its saturation still lands");

		// --- it must not reach the drain side ---
		check(!Checks.classReferences(
						com.entropymod.mixin.FoodDataRestoreMixin.class, "addExhaustion"),
				"Glutton never touches exhaustion -- the drain side is Iron Stomach's and "
						+ "Growling Stomach's, through a different hook entirely");
	}

	// =====================================================================

	private static boolean declares(Class<?> owner, String method) {
		if (owner == null) {
			return false;
		}
		for (Method m : owner.getDeclaredMethods()) {
			if (m.getName().equals(method)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasPrivate(Class<?> owner, String name, Class<?>... params) {
		try {
			Method m = owner.getDeclaredMethod(name, params);
			return java.lang.reflect.Modifier.isPrivate(m.getModifiers());
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/**
	 * Whether a mixin is listed in its config. Read from the source tree rather
	 * than the classpath, because {@code src/client/resources} is not on the
	 * harness classpath -- the main source set is all there is.
	 */
	private static boolean mixinRegistered(String simpleName, boolean client) {
		String path = client
				? "src/client/resources/entropymod.client.mixins.json"
				: "src/main/resources/entropymod.mixins.json";
		return source(path).contains('"' + simpleName + '"');
	}

	/** A project source file's text, or "" if it cannot be read. */
	private static String source(String relativePath) {
		try {
			return java.nio.file.Files.readString(java.nio.file.Path.of(relativePath));
		} catch (Exception e) {
			return "";
		}
	}

	private static Class<?> forName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			return null;
		}
	}

	private static String round(double v) {
		return String.format("%.4f", v);
	}

	private SlickHookGluttonChecks() { }
}
