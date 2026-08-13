package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Vampire Rules (GOOD / SURVIVAL, Tier 2) -- damage you deal feeds you.
 *
 * <h2>The hook is a DIFFERENT direction from every damage effect before it</h2>
 *
 * <p>Iron Skin, Fragile, Giant Size, Flamboyant and Crouch Invincibility all hook
 * damage <em>taken</em>, at {@code LivingEntity.hurtServer} / {@code ServerPlayer
 * .hurtServer} HEAD, where the player is the victim. <b>None of that reverses.</b>
 * The obvious mirror -- {@code Player.attack}'s {@code hurtOrSimulate} argument,
 * which is what Behemoth Gauntlets modifies -- is the wrong number for this effect:
 * it is the damage <em>sent</em>, before the target's armour, enchantments,
 * resistance and absorption have touched it. Feeding off it would pay the player the
 * same amount for hitting an unarmoured zombie and a fully-enchanted-netherite one.
 *
 * <p>Nor is {@code LivingEntity.actuallyHurt(ServerLevel, DamageSource, float)}'s
 * parameter the answer, which was the second guess and is also wrong:
 * javap shows {@code actuallyHurt} calls {@code getDamageAfterArmorAbsorb} and
 * {@code getDamageAfterMagicAbsorb} <em>itself</em>, so its argument is still the raw
 * amount. The mitigated value only exists as a local.
 *
 * <p><b>The real choke point is the {@code CombatTracker.recordDamage(DamageSource,
 * float)} call inside {@code actuallyHurt}</b>, immediately before
 * {@code setHealth(getHealth() - f)}. By then armour, enchantment protection,
 * Resistance and absorption have all been applied and the early return for
 * {@code f <= 0} has already been taken, so its float argument is exactly the health
 * the target is about to lose. See {@code LivingEntityDamageDealtMixin}.
 *
 * <h2>Hunger: the "normal cap" is real, and it is not where you would guess</h2>
 *
 * <p>Verified in {@code FoodData}'s bytecode:
 *
 * <pre>
 * private void add(int nutrition, float saturation) {
 *     this.foodLevel       = Mth.clamp(nutrition + this.foodLevel, 0, 20);
 *     this.saturationLevel = Mth.clamp(saturation + this.saturationLevel, 0.0f, (float) this.foodLevel);
 * }
 * public void eat(int n, float mod) { add(n, FoodConstants.saturationByModifier(n, mod)); }
 * public void setFoodLevel(int i)    { this.foodLevel = i; }        // NO clamp
 * public void setSaturation(float f) { this.saturationLevel = f; }  // NO clamp
 * </pre>
 *
 * <p>So the cap the brief calls "normal" is <b>saturation clamped to the current food
 * level</b>, i.e. at most 20 and usually less -- and it lives in the private
 * {@code add}, which is the only thing {@code eat} does. <b>The two public setters do
 * not clamp at all</b>, and nothing re-clamps afterwards: {@code FoodData.tick} only
 * ever <em>drains</em> saturation (1.0 per 4.0 exhaustion), it never bounds it, and
 * {@code ClientboundSetHealthPacket} carries saturation as a plain float. Overflow
 * above 20 is therefore genuinely stable, survives a relog through
 * {@code addAdditionalSaveData}, and is not a trick.
 *
 * <p>So this effect must <b>not</b> route through {@code eat}, which would silently
 * discard everything above the food level. It writes the two setters directly.
 *
 * <h2>Nothing is lost to rounding, and that is why the fraction goes to saturation</h2>
 *
 * <p>{@code foodLevel} is an {@code int}. At {@value #DAMAGE_PER_FOOD_POINT} damage
 * per food point a 3-damage hit is worth 0.75 of a point, and truncating per hit
 * would throw away most of the value of ordinary weak hits -- the same
 * rounding-eats-small-values trap {@code EffectHooks.scaleAmount} exists for on the XP
 * side. Rather than carry a per-player fractional remainder (which would be run state
 * needing persistence, for a cosmetic amount of nutrition), <b>the whole part goes to
 * food and the fractional part goes to saturation</b>, which is a float. Total
 * nutrition is conserved exactly, with no state of any kind.
 */
public final class VampireRulesBehavior extends HookEffectBehavior {

	public static final String ID = "vampire_rules";

	/**
	 * Damage dealt per point of hunger restored.
	 *
	 * <p>The brief's rate is "8 attack damage restores 1 hunger shank". <b>A shank is
	 * one drumstick icon on the HUD, which is two food points</b> -- the bar is 20
	 * points drawn as 10 icons -- so 8 damage is 2 points and one point costs 4.0
	 * damage. Recorded explicitly because "shank" and "food point" differ by a factor
	 * of two and reading it the other way would halve the effect.
	 *
	 * <p>For scale: killing a 20 HP zombie outright returns <b>5 food points, i.e.
	 * 2.5 shanks</b>, which is more than the 0.6 points a few sword swings cost in
	 * exhaustion. The effect is a net gain in any real fight, which is the point.
	 */
	public static final float DAMAGE_PER_FOOD_POINT = 4.0f;

	/** Vanilla's food ceiling. {@code add} clamps to it; the setters do not. */
	public static final int MAX_FOOD_LEVEL = 20;

	/**
	 * How far saturation may overflow: <b>twice vanilla's own ceiling</b>.
	 *
	 * <p>The overflow is unbounded in vanilla -- nothing re-clamps
	 * {@code saturationLevel} once {@code setSaturation} has written it -- so this is
	 * a decision rather than a limit discovered in the code. A long grinding session
	 * would otherwise bank hours of hunger and delete food as a resource for the rest
	 * of the run, which is a bigger change than the effect is asking for. 40.0 is
	 * roughly 27 minutes of walking (exhaustion 0.0 per tick idle, 0.1 per metre
	 * sprinted at 1.0 saturation per 4.0 exhaustion), which is a generous buffer that
	 * still ends.
	 */
	public static final float MAX_SATURATION = 40.0f;

	/**
	 * The new (food, saturation) pair after feeding on {@code damage}.
	 *
	 * <p><b>Free of Minecraft types on purpose</b>, same discipline as
	 * {@code MovementScramble}, {@code CropSchedule} and {@code CompanionBuffs}: the
	 * overflow rule is precisely the sort of arithmetic that looks right, plays subtly
	 * wrong, and cannot be told from working by watching a hunger bar. The harness
	 * drives this directly.
	 *
	 * @param damage the post-mitigation damage the target actually lost
	 * @return {@code [foodLevel, saturationLevel]}
	 */
	public static float[] feed(float damage, int foodLevel, float saturationLevel) {
		if (damage <= 0.0f) {
			return new float[] {foodLevel, saturationLevel};
		}
		float credit = damage / DAMAGE_PER_FOOD_POINT;
		int whole = (int) credit;
		float fraction = credit - whole;

		int combined = foodLevel + whole;
		int newFood = Math.min(combined, MAX_FOOD_LEVEL);
		// Whole points that did not fit spill into saturation rather than vanishing,
		// which is the "overflow above the normal cap" half of the design. The
		// fraction rides along for the rounding reason in the class javadoc.
		float spill = combined - newFood;
		float newSaturation = Math.min(saturationLevel + fraction + spill, MAX_SATURATION);
		return new float[] {newFood, newSaturation};
	}
}
