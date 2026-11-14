package com.entropymod.entropy.probe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Evidence that a mixin handler RUNS, as opposed to merely having applied.
 *
 * <p>"The mixin applied" and "the behaviour changed" are separate claims (CLAUDE.md,
 * "When a mixin applies cleanly and STILL changes nothing"), and there is a third
 * one in between that nothing recorded until now: <b>the handler was reached at
 * all</b>. A handler on a method a subclass overrides without calling super applies
 * perfectly and is never executed. Every handler in both mixin configs calls
 * {@link #hit()} as its first statement, so that question has an answer in the log
 * and in {@code /entropymixins} instead of in an argument about bytecode.
 *
 * <p>One constant per <em>handler</em>, not per class: three mixins carry two
 * handlers each, and one of them firing must not vouch for the other.
 *
 * <h2>What it costs</h2>
 *
 * <p>After the first call, {@link #hit()} is one volatile read and a branch. That
 * matters because several of these sit on per-tick, per-entity paths (friction,
 * visibility, knockback). The first call logs once at INFO and never again.
 *
 * <h2>What it does and does not prove</h2>
 *
 * <p>A FIRED row proves the injection point was reached in this JVM session. It
 * does not prove the effect is <em>felt</em> -- a value can still be re-clamped
 * downstream. A NOT YET row proves nothing by itself: most handlers only run when
 * the game does the thing they hook (a bed is used, a villager reprices), so the
 * row means "exercise that action, then look again".
 *
 * <p>State is per JVM, not per world. In singleplayer the client and the integrated
 * server share one JVM, so client-side handlers show up in the server command too;
 * on a dedicated server the {@link Side#CLIENT} rows can never fire and are
 * reported separately for that reason.
 *
 * <p>The one mixin with no row is {@code LlamaStrengthAccessor}: an {@code @Invoker}
 * has no body to put a probe in. Its evidence is the llama's 15 slots.
 *
 * <p>Free of Minecraft imports so the headless harness can drive it. Lives outside
 * the mixin packages on purpose -- Mixin refuses to let ordinary code load a class
 * from a package declared in a mixin config.
 */
public enum MixinProbe {
	ABSTRACT_FISH_ATTACK_DAMAGE("AbstractFishAttackDamageMixin", "entropymod$giveFishAttackDamage", Side.COMMON),
	ANIMAL_ATTACK_DAMAGE("AnimalAttackDamageMixin", "entropymod$giveAnimalsAttackDamage", Side.COMMON),
	AVOID_ENTITY_GOAL("AvoidEntityGoalMixin", "entropymod$ignoreBeastWhisperer", Side.COMMON),
	BED_EXPLOSION("BedExplosionMixin", "entropymod$nightmares", Side.COMMON),
	BLOCK_ITEM_PLACEMENT__GOLD_BOTH_HALVES("BlockItemPlacementMixin", "entropymod$goldBothHalves", Side.COMMON),
	BLOCK_ITEM_PLACEMENT__PLACE_GOLD_INSTEAD("BlockItemPlacementMixin", "entropymod$placeGoldInstead", Side.COMMON),
	ENTITY_STEP_SOUND("EntityStepSoundMixin", "entropymod$louderFootsteps", Side.COMMON),
	ENTROPY_MOD("EntropyModMixin", "init", Side.COMMON),
	FOOD_DATA_OWNER("FoodDataOwnerMixin", "entropymod$setOwner", Side.COMMON),
	FOOD_DATA_RESTORE__GLUTTON_NUTRITION("FoodDataRestoreMixin", "entropymod$gluttonNutrition", Side.COMMON),
	FOOD_DATA_RESTORE__GLUTTON_SATURATION("FoodDataRestoreMixin", "entropymod$gluttonSaturation", Side.COMMON),
	INVENTORY_FREE_SLOT("InventoryFreeSlotMixin", "entropymod$skipLockedSlots", Side.COMMON),
	INVENTORY_SLOT_LOCK("InventorySlotLockMixin", "entropymod$slashedPockets", Side.COMMON),
	ITEM_STACK_DURABILITY("ItemStackDurabilityMixin", "entropymod$extraToolWear", Side.COMMON),
	LIVING_ENTITY_DAMAGE_DEALT("LivingEntityDamageDealtMixin", "entropymod$vampireRules", Side.COMMON),
	LIVING_ENTITY_DAMAGE("LivingEntityDamageMixin", "entropymod$scaleIncomingDamage", Side.COMMON),
	LIVING_ENTITY_DEATH_PROTECTION("LivingEntityDeathProtectionMixin", "entropymod$phoenixHeart", Side.COMMON),
	LIVING_ENTITY_FRICTION("LivingEntityFrictionMixin", "entropymod$slick", Side.COMMON),
	LIVING_ENTITY_FROST_WALKER("LivingEntityFrostWalkerMixin", "entropymod$innateFrostWalker", Side.COMMON),
	LIVING_ENTITY_KNOCKBACK("LivingEntityKnockbackMixin", "entropymod$knockback", Side.COMMON),
	LIVING_ENTITY_SPRINT_JUMP("LivingEntitySprintJumpMixin", "entropymod$slipperyGripSprintJump", Side.COMMON),
	LIVING_ENTITY_SPRINT("LivingEntitySprintMixin", "entropymod$slipperyGrip", Side.COMMON),
	LIVING_ENTITY_VISIBILITY("LivingEntityVisibilityMixin", "entropymod$amplifyVisibility", Side.COMMON),
	MOB_PASSIVE_AGGRESSION("MobPassiveAggressionMixin", "entropymod$armPassiveAnimals", Side.COMMON),
	PANIC_GOAL("PanicGoalMixin", "entropymod$calmNearBeastWhisperer", Side.COMMON),
	PLAYER_ATTACK_DAMAGE("PlayerAttackDamageMixin", "entropymod$behemothGauntlets", Side.COMMON),
	PLAYER_ATTRIBUTES("PlayerAttributesMixin", "entropymod$addPickupRange", Side.COMMON),
	PLAYER_BUTTERFINGERS("PlayerButterfingersMixin", "entropymod$butterfingers", Side.COMMON),
	PLAYER_EDGE_SAFETY("PlayerEdgeSafetyMixin", "entropymod$looseFooting", Side.COMMON),
	PLAYER_EXHAUSTION("PlayerExhaustionMixin", "entropymod$scaleExhaustion", Side.COMMON),
	PLAYER_EXPERIENCE("PlayerExperienceMixin", "entropymod$scaleExperience", Side.COMMON),
	PLAYER_EXTRA_KNOCKBACK("PlayerExtraKnockbackMixin", "entropymod$lethalHookExtraKnockback", Side.COMMON),
	PLAYER_FLYING_SPEED("PlayerFlyingSpeedMixin", "entropymod$slipperyGripAirControl", Side.COMMON),
	PLAYER_FOOD_DATA_OWNER("PlayerFoodDataOwnerMixin", "entropymod$stampFoodDataOwner", Side.COMMON),
	PLAYER_IMMOBILE("PlayerImmobileMixin", "entropymod$tremorParalysis", Side.COMMON),
	PLAYER_ITEM_PICKUP("PlayerItemPickupMixin", "entropymod$widenPickupBox", Side.COMMON),
	SERVER_PLAYER_DAMAGE__CROUCH_INVINCIBILITY("ServerPlayerDamageMixin", "entropymod$crouchInvincibility", Side.COMMON),
	SERVER_PLAYER_DAMAGE__FLAMBOYANT("ServerPlayerDamageMixin", "entropymod$flamboyant", Side.COMMON),
	SERVER_PLAYER_JUMP("ServerPlayerJumpMixin", "entropymod$spillItemOnJump", Side.COMMON),
	VILLAGER_PRICES("VillagerPricesMixin", "entropymod$overcharge", Side.COMMON),
	CAMERA("CameraMixin", "entropymod$rollCamera", Side.CLIENT),
	CLIENT_EDGE_SAFETY("ClientEdgeSafetyMixin", "entropymod$looseFootingClient", Side.CLIENT),
	CLIENT_FLYING_SPEED("ClientFlyingSpeedMixin", "entropymod$slipperyGripAirControlClient", Side.CLIENT),
	CLIENT_FRICTION("ClientFrictionMixin", "entropymod$slickClient", Side.CLIENT),
	CLIENT_IMMOBILE("ClientImmobileMixin", "entropymod$clientTremorParalysis", Side.CLIENT),
	CLIENT_SPRINT_JUMP("ClientSprintJumpMixin", "entropymod$slipperyGripSprintJumpClient", Side.CLIENT),
	CLIENT_SPRINT("ClientSprintMixin", "entropymod$slipperyGripClient", Side.CLIENT),
	ENTROPY_MOD_CLIENT("EntropyModClientMixin", "init", Side.CLIENT),
	KEYBOARD_INPUT("KeyboardInputMixin", "entropymod$scrambleAndForceJump", Side.CLIENT);

	/** Which mixin config the handler belongs to. */
	public enum Side { COMMON, CLIENT }

	private static final Logger LOGGER = LoggerFactory.getLogger("entropymod/mixin-probe");

	private final String mixinClass;
	private final String handler;
	private final Side side;
	private volatile boolean fired;

	MixinProbe(String mixinClass, String handler, Side side) {
		this.mixinClass = mixinClass;
		this.handler = handler;
		this.side = side;
	}

	/**
	 * Records that this handler ran. Called as the first statement of the handler,
	 * before any gate, so it answers "was the injection point reached" and not "did
	 * the effect's own conditions pass".
	 */
	public void hit() {
		if (fired) {
			return;
		}
		fired = true;
		LOGGER.info("[mixin-probe] FIRED {}#{} ({} of {} handlers seen this session)",
				mixinClass, handler, firedCount(), values().length);
	}

	public boolean hasFired() {
		return fired;
	}

	public String mixinClass() {
		return mixinClass;
	}

	public String handler() {
		return handler;
	}

	public Side side() {
		return side;
	}

	/** {@code Class#handler}, the form the log line and the command both print. */
	public String label() {
		return mixinClass + "#" + handler;
	}

	public static int firedCount() {
		int count = 0;
		for (MixinProbe probe : values()) {
			if (probe.fired) {
				count++;
			}
		}
		return count;
	}

	/** Every probe on {@code side} whose fired state equals {@code fired}, in declaration order. */
	public static List<MixinProbe> list(Side side, boolean fired) {
		List<MixinProbe> result = new ArrayList<>();
		for (MixinProbe probe : values()) {
			if (probe.side == side && probe.fired == fired) {
				result.add(probe);
			}
		}
		return result;
	}

	/** Harness only: forget everything, so a check can start from a known state. */
	public static void resetForTest() {
		for (MixinProbe probe : values()) {
			probe.fired = false;
		}
	}
}