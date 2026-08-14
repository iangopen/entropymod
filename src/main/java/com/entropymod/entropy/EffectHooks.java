package com.entropymod.entropy;

import com.entropymod.EntropyMod;
import com.entropymod.entropy.behavior.BadReputationBehavior;
import com.entropymod.entropy.behavior.BehemothGauntletsBehavior;
import com.entropymod.entropy.behavior.ButterfingersExtremeBehavior;
import com.entropymod.entropy.behavior.BeastWhispererBehavior;
import com.entropymod.entropy.behavior.ClumsyDiggerBehavior;
import com.entropymod.entropy.behavior.CrouchInvincibilityBehavior;
import com.entropymod.entropy.behavior.EarthquakeBehavior;
import com.entropymod.entropy.behavior.FlamboyantBehavior;
import com.entropymod.entropy.quake.EarthquakeService;
import com.entropymod.entropy.behavior.SlashedPocketsBehavior;
import com.entropymod.entropy.behavior.GreenThumbBehavior;
import com.entropymod.entropy.behavior.LeakyPocketsBehavior;
import com.entropymod.entropy.behavior.FastLearnerBehavior;
import com.entropymod.entropy.behavior.FragileBehavior;
import com.entropymod.entropy.behavior.GiantSizeBehavior;
import com.entropymod.entropy.behavior.FrostWalkerInnateBehavior;
import com.entropymod.entropy.behavior.GrowlingStomachBehavior;
import com.entropymod.entropy.behavior.ExposedBehavior;
import com.entropymod.entropy.behavior.IronSkinBehavior;
import com.entropymod.entropy.behavior.IronStomachBehavior;
import com.entropymod.entropy.behavior.IronWillBehavior;
import com.entropymod.entropy.behavior.PhoenixChamberedHeartBehavior;
import com.entropymod.entropy.behavior.SlipperyGripBehavior;
import com.entropymod.entropy.behavior.SlowLearnerBehavior;
import com.entropymod.entropy.behavior.VampireRulesBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The single door between the mixins and the run state. Every hook-driven effect
 * is a multiplier looked up here.
 *
 * <p>Mixins are the most fragile code in the project, so they are kept to one
 * line each: compute nothing, decide nothing, just multiply by what this returns.
 * All the "does this player have that effect" logic lives here where it is
 * ordinary readable Java.
 *
 * <p><b>Always returns a usable multiplier, never throws, never returns null.</b>
 * A mixin runs inside vanilla's own call stack; an exception thrown from one
 * takes the server down with a stack trace that blames Minecraft rather than
 * this mod. Every path here falls back to {@code 1.0f} (no change).
 *
 * <p><b>Client-side calls return 1.0f.</b> The acquired-effect set only exists
 * on the server, and {@code player.level()} is a {@code ClientLevel} on the
 * client, so the {@code instanceof ServerLevel} check below is what makes these
 * safe to call from a mixin that runs on both sides. The three effects routed
 * through here are all server-authoritative (hunger, damage, XP), so nothing is
 * lost by the client seeing 1.0.
 */
public final class EffectHooks {

	/** Scales hunger drain. Read by the Player.causeFoodExhaustion mixin. */
	public static float exhaustionMultiplier(Player player) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null) {
			return 1.0f;
		}
		float multiplier = 1.0f;
		if (acquired.contains(IronStomachBehavior.ID)) {
			multiplier *= IronStomachBehavior.MULTIPLIER;
		}
		if (acquired.contains(GrowlingStomachBehavior.ID)) {
			multiplier *= GrowlingStomachBehavior.MULTIPLIER;
		}
		return multiplier;
	}

	/** Scales incoming damage. Read by the LivingEntity.hurtServer mixin. */
	public static float damageTakenMultiplier(Player player) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null) {
			return 1.0f;
		}
		float multiplier = 1.0f;
		if (acquired.contains(IronSkinBehavior.ID)) {
			multiplier *= IronSkinBehavior.MULTIPLIER;
		}
		if (acquired.contains(FragileBehavior.ID)) {
			multiplier *= FragileBehavior.MULTIPLIER;
		}
		// Giant Size rides the SAME hook rather than getting its own -- one door
		// between the damage mixin and the run state, so three effects compose
		// multiplicatively instead of three mechanisms racing to clamp one number.
		if (acquired.contains(GiantSizeBehavior.ID)) {
			multiplier *= GiantSizeBehavior.DAMAGE_MULTIPLIER;
		}
		return multiplier;
	}

	/** Scales experience gained. Read by the Player.giveExperiencePoints mixin. */
	public static float experienceMultiplier(Player player) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null) {
			return 1.0f;
		}
		float multiplier = 1.0f;
		if (acquired.contains(FastLearnerBehavior.ID)) {
			multiplier *= FastLearnerBehavior.MULTIPLIER;
		}
		if (acquired.contains(SlowLearnerBehavior.ID)) {
			multiplier *= SlowLearnerBehavior.MULTIPLIER;
		}
		return multiplier;
	}

	/**
	 * True if projectile knockback should be skipped for this player (Iron Will).
	 * Read by the {@code LivingEntity.hurtServer} knockback redirect, which has
	 * already established that the damage source is projectile-based -- this
	 * answers only "does this player have the effect".
	 */
	public static boolean ignoresProjectileKnockback(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null && acquired.contains(IronWillBehavior.ID);
	}

	/** True if this player's water-walking should be granted (Frost Walker Innate). */
	public static boolean hasInnateFrostWalker(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null && acquired.contains(FrostWalkerInnateBehavior.ID);
	}

	/** Scales footstep loudness. Read by the Entity.playStepSound mixin. */
	public static float stepSoundVolumeMultiplier(Player player) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null) {
			return 1.0f;
		}
		return acquired.contains(ExposedBehavior.ID)
				? ExposedBehavior.VOLUME_MULTIPLIER
				: 1.0f;
	}

	/**
	 * Scales how far away mobs notice this player. Read by the
	 * {@code LivingEntity.getVisibilityPercent} mixin, which feeds
	 * {@code TargetingConditions.test} -- the same value vanilla reduces to 0.8
	 * while sneaking.
	 */
	public static float detectionRangeMultiplier(Player player) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null) {
			return 1.0f;
		}
		return acquired.contains(ExposedBehavior.ID)
				? ExposedBehavior.DETECTION_MULTIPLIER
				: 1.0f;
	}

	/**
	 * True if animals should not flee from this player (Beast Whisperer). Read by
	 * both the {@code PanicGoal} and {@code AvoidEntityGoal} mixins.
	 *
	 * <p>Takes an {@code Entity} rather than a {@code Player} because both call
	 * sites hold something more general -- a damage source's attacker, and a
	 * goal's avoid target -- and pushing the {@code instanceof} in here keeps the
	 * mixins to the one-line shape the rest of this class exists to preserve.
	 */
	public static boolean suppressesFleeing(Entity entity) {
		return entity instanceof Player player
				&& acquired(player) != null
				&& acquired(player).contains(BeastWhispererBehavior.ID);
	}

	/**
	 * True if a player holding Green Thumb is within {@link GreenThumbBehavior#RADIUS}
	 * of this block. Read by {@code GreenThumbGrowth} to re-verify a crop at the
	 * moment of each scheduled advance.
	 *
	 * <p>"Nearest" rather than "any" so two players cannot both claim the same
	 * block. This is the last remaining crop-proximity check in the mod: the
	 * {@code cropGrowthMultiplier} hook that used to sit beside it was deleted
	 * along with Blight Touched's growth-slowdown mechanic, since Blight Touched
	 * was the only effect still feeding it. See {@code BlightTouchedTrample}.
	 */
	public static boolean hasGreenThumbNearby(Level level, BlockPos pos) {
		AcquiredEffects acquired = acquired(nearestPlayerTo(level, pos));
		return acquired != null && acquired.contains(GreenThumbBehavior.ID);
	}

	/** The nearest player within the crop radius of a block, or null (including client-side). */
	private static Player nearestPlayerTo(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getNearestPlayer(
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				GreenThumbBehavior.RADIUS, false);
	}

	/** True if this jump should spill an item (Leaky Pockets). Rolls the chance itself. */
	public static boolean rollLeakyPockets(Player player, RandomSource random) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null
				&& acquired.contains(LeakyPocketsBehavior.ID)
				&& random.nextFloat() < LeakyPocketsBehavior.CHANCE;
	}

	/**
	 * Extra durability damage for this use, or 0 (Clumsy Digger). Rolls the chance
	 * itself so the mixin stays a single expression.
	 *
	 * <p>Logs each successful roll at DEBUG. This effect is invisible by
	 * construction -- there is no sound, no message and no on-screen change, only a
	 * durability bar moving marginally faster -- so "is it firing at all" and "is it
	 * just rare" are indistinguishable in play, which is exactly how it was
	 * reported. The log line is what separates them: grep {@code Clumsy Digger} in
	 * {@code run/logs/debug-N.log.gz} and count. It costs nothing for players
	 * without the effect, since both returns above it are taken first.
	 */
	public static int rollClumsyDiggerExtraDamage(Player player, RandomSource random, ItemStack stack) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null || !acquired.contains(ClumsyDiggerBehavior.ID)) {
			return 0;
		}
		// Scope gate, BEFORE the roll. The hook fires for every source of durability
		// loss including armour and the elytra, which this effect must not touch --
		// and gating ahead of the roll is also what keeps the DEBUG line below a
		// truthful record of tool procs only.
		if (!ClumsyDiggerBehavior.appliesTo(stack)) {
			return 0;
		}
		if (random.nextFloat() >= ClumsyDiggerBehavior.CHANCE) {
			return 0;
		}
		int extra = ClumsyDiggerBehavior.extraDamageFor(stack.getMaxDamage());
		EntropyMod.LOGGER.debug("Clumsy Digger: +{} durability damage to {} (max {}) for {}",
				extra, stack.getItemName().getString(), stack.getMaxDamage(),
				player.getName().getString());
		return extra;
	}

	/**
	 * Scales one melee hit for Behemoth Gauntlets: {@code +20} bare-handed,
	 * {@code x0.25} with anything in hand.
	 *
	 * <p>Read by the {@code Player.attack} redirect at the point the fully
	 * computed damage is handed to the target. The empty-hand test is
	 * {@code getWeaponItem().isEmpty()}, which for a player is the main-hand
	 * stack -- see {@link BehemothGauntletsBehavior} for why "holding anything"
	 * rather than "holding a weapon" is the deliberate rule.
	 */
	public static float meleeDamage(Player player, float damage) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null || !acquired.contains(BehemothGauntletsBehavior.ID)) {
			return damage;
		}
		return BehemothGauntletsBehavior.damageFor(damage, player.getWeaponItem().isEmpty());
	}

	/**
	 * True if this hit should be ignored entirely (Crouch Invincibility).
	 *
	 * <p>Gated on {@code isCrouching()} -- vanilla's pose-derived state, matching
	 * what the player sees -- rather than the raw {@code isShiftKeyDown()} input
	 * flag, and evaluated per hit so standing up ends the protection immediately.
	 */
	public static boolean ignoresAllDamage(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null
				&& acquired.contains(CrouchInvincibilityBehavior.ID)
				&& player.isCrouching();
	}

	/**
	 * Amplifies fire damage to a certainly-lethal amount (Flamboyant), or returns
	 * it unchanged.
	 *
	 * <p>Gated on the vanilla {@code #minecraft:is_fire} damage-type tag, so it
	 * covers standing in fire, burning, lava, campfires, magma blocks and both
	 * fireball types -- and nothing else. Every non-fire source passes through
	 * untouched, which the harness asserts explicitly.
	 *
	 * <p>Scaled from max health rather than being a fixed number so that armour,
	 * Resistance, absorption and Iron Skin -- all applied after this point --
	 * cannot leave the player alive on a sliver.
	 */
	public static float fireDamage(Player player, DamageSource source, float damage) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null
				|| !acquired.contains(FlamboyantBehavior.ID)
				|| source == null
				|| !source.is(DamageTypeTags.IS_FIRE)) {
			return damage;
		}
		return FlamboyantBehavior.lethalDamage(damage, player.getMaxHealth());
	}

	/**
	 * True if this inventory slot is locked shut for this player (Slashed
	 * Pockets). Read by the slot-placement and free-slot mixins.
	 */
	public static boolean isInventorySlotLocked(Player player, int slot) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null
				&& acquired.contains(SlashedPocketsBehavior.ID)
				&& SlashedPocketsBehavior.isLocked(slot);
	}

	/**
	 * True if this player is rooted in place by an Earthquake tremor
	 * (server side only). Read by {@code PlayerImmobileMixin}.
	 *
	 * <p><b>This is the SERVER's half of the answer and nothing else.</b> The client
	 * keeps its own countdown in {@code ClientRunState}, fed by
	 * {@code TremorLockPayload}, and reads it from {@code ClientImmobileMixin}. The two
	 * halves are side-scoped rather than sharing a source for the reason this whole
	 * class exists -- {@code EffectHooks} lives in the main source set and cannot see
	 * the client one, and the acquired set does not exist client-side at all.
	 *
	 * <p>Splitting it is safe here in a way it was not for Slippery Grip: both halves
	 * only ever answer <em>true</em>, never remove anything, so there is no ordering
	 * question even if both somehow ran. The scoping is the project's rule rather than a
	 * correctness requirement in this instance.
	 *
	 * <p>Returns false client-side, like every other hook here, which is exactly why the
	 * client half exists.
	 */
	public static boolean isMovementLocked(Player player) {
		if (!(player.level() instanceof ServerLevel)) {
			return false;
		}
		AcquiredEffects acquired = acquired(player);
		return acquired != null
				&& acquired.contains(EarthquakeBehavior.ID)
				&& EarthquakeService.isTremorActive(player.getUUID());
	}

	/** True if this player has Slashed Pockets at all. Cheap gate for the per-tick sweep. */
	public static boolean hasSlashedPockets(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null && acquired.contains(SlashedPocketsBehavior.ID);
	}

	/**
	 * True if sprinting should cost this player speed instead of gaining it
	 * (Slippery Grip). Sprinting itself is not blocked -- see
	 * {@code SlipperyGripSprint} for what happens instead.
	 */
	public static boolean halvesSprintSpeed(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null && acquired.contains(SlipperyGripBehavior.ID);
	}

	/**
	 * Phoenix Chambered Heart's death save. Returns true if the player was rescued and
	 * vanilla should skip its death branch entirely.
	 *
	 * <p>Called from the {@code checkTotemDeathProtection} mixin, so by this point
	 * vanilla has already zeroed the health -- everything that restores it lives in
	 * {@code EntropyManager.consumePhoenixHeart}, which also spends the run flag.
	 *
	 * <p>The {@code BYPASSES_INVULNERABILITY} test mirrors vanilla's own first line
	 * in that method, deliberately: {@code /kill} and the void must still kill. An
	 * effect that survived {@code /kill} would be a debugging trap.
	 */
	public static boolean tryPhoenixHeart(Player player, DamageSource source) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		MinecraftServer server = serverPlayer.level().getServer();
		if (server == null) {
			return false;
		}
		return EntropyManager.get(server).consumePhoenixHeart(serverPlayer);
	}

	/** True if villagers should overcharge this player (Bad Reputation). */
	public static boolean hasBadReputation(Player player) {
		AcquiredEffects acquired = acquired(player);
		return acquired != null && acquired.contains(BadReputationBehavior.ID);
	}

	/**
	 * Butterfingers Extreme: true if this attack should fling the held item.
	 *
	 * <p>Rolls the chance itself so the mixin stays a single expression, and rolls it
	 * <b>only when there is something to fling</b> -- an empty main hand returns false
	 * without consuming a roll, which is what makes bare-handed combat a complete
	 * counterplay rather than an unlucky one.
	 *
	 * <p>Logged at DEBUG for the reason Clumsy Digger is: the outcome is visible, but
	 * "how often" is not, and a rate reported from memory after a play session is not
	 * evidence. Grep {@code Butterfingers} in {@code run/logs/debug-N.log.gz} and count
	 * against {@link ButterfingersExtremeBehavior#CHANCE}.
	 */
	public static boolean rollButterfingers(Player player, RandomSource random) {
		AcquiredEffects acquired = acquired(player);
		if (acquired == null || !acquired.contains(ButterfingersExtremeBehavior.ID)) {
			return false;
		}
		if (player.getMainHandItem().isEmpty()) {
			return false;
		}
		if (random.nextFloat() >= ButterfingersExtremeBehavior.CHANCE) {
			return false;
		}
		EntropyMod.LOGGER.debug("Butterfingers Extreme: flinging {} from {}",
				player.getMainHandItem().getItemName().getString(),
				player.getName().getString());
		return true;
	}

	/**
	 * Vampire Rules: converts damage this player just dealt into food.
	 *
	 * <p>Called from the {@code CombatTracker.recordDamage} hook inside
	 * {@code LivingEntity.actuallyHurt}, so {@code damage} is the post-armour,
	 * post-enchantment, post-resistance, post-absorption amount the victim is about to
	 * lose -- see {@link VampireRulesBehavior} for why none of the earlier, more
	 * obvious hooks carry that number.
	 *
	 * <p>The {@code attacker != victim} guard is load-bearing rather than defensive:
	 * without it, fall damage and any other self-attributed source would feed the
	 * player for hurting themselves.
	 *
	 * <p>Writes {@code setFoodLevel}/{@code setSaturation} directly rather than calling
	 * {@code FoodData.eat}, because {@code eat} routes through the private {@code add},
	 * which clamps saturation to the current food level and would discard the entire
	 * overflow this effect exists to produce.
	 */
	public static void vampireDrain(Entity attacker, Entity victim, float damage) {
		if (!(attacker instanceof Player player) || attacker == victim || damage <= 0.0f) {
			return;
		}
		AcquiredEffects acquired = acquired(player);
		if (acquired == null || !acquired.contains(VampireRulesBehavior.ID)) {
			return;
		}
		FoodData food = player.getFoodData();
		float[] fed = VampireRulesBehavior.feed(damage, food.getFoodLevel(), food.getSaturationLevel());
		food.setFoodLevel((int) fed[0]);
		food.setSaturation(fed[1]);
	}

	/**
	 * The run's acquired set, or null if unavailable.
	 *
	 * <p>The {@code ServerLevel} cast is the accessor pattern recorded in
	 * CLAUDE.md — {@code ServerPlayer.server} is a private field in this mapping,
	 * and there is no {@code serverLevel()} method. Here the {@code instanceof}
	 * doubles as the client-side guard.
	 */
	private static AcquiredEffects acquired(Player player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return null;
		}
		return EntropyManager.get(server).acquired();
	}

	/**
	 * Pairs a multiplier with a whole-number amount without letting rounding eat
	 * small values. {@code (int)(1 * 1.5f)} is 1, so a stream of 1-XP orbs under
	 * Fast Learner would gain nothing at all; rounding instead of truncating makes
	 * it 2 half the time, which is the intended average. Guaranteed non-negative.
	 */
	public static int scaleAmount(int amount, float multiplier) {
		return Math.max(0, Math.round(amount * multiplier));
	}

	private EffectHooks() {}
}
