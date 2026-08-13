package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Earthquake (BAD / DEBUFF, Tier 2) -- the ground shakes on a random timer: Slowness
 * II for the tremor, and two half-heart hits of true damage inside it.
 *
 * <h2>Third shape, and NO SafeSpawn</h2>
 *
 * <p>This is a tick service like Green Thumb and the two spawn effects, and it reuses
 * {@code SpawnSchedule} for the cadence -- but <b>nothing here spawns anything</b>, so
 * none of the position-finding machinery is pulled in. {@code SafeSpawn} is not
 * referenced by this effect at all, which the harness asserts rather than leaving to
 * inspection: the schedule and the placement search arrived together for Unstable and
 * Creeper Magnet, and there is no reason a timer should drag a heightmap search along
 * behind it.
 *
 * <h2>True damage: vanilla's precedent is SONIC BOOM, and it is NOT the void</h2>
 *
 * <p>The obvious candidate is void damage, and it is wrong. Read out of the shipped
 * tag JSON, the mitigation pipeline is exactly two gates:
 *
 * <pre>
 * getDamageAfterArmorAbsorb(src, f):  if (!src.is(BYPASSES_ARMOR)) f = armour+toughness reduction
 * getDamageAfterMagicAbsorb(src, f):  if (src.is(BYPASSES_EFFECTS))     return f;
 *                                     if (RESISTANCE && !src.is(BYPASSES_RESISTANCE)) f = reduce
 *                                     if (src.is(BYPASSES_ENCHANTMENTS)) return f;
 *                                     f = protection-enchantment reduction
 * </pre>
 *
 * <p>{@code #minecraft:bypasses_armor} has nineteen entries, {@code out_of_world}
 * among them. <b>{@code #minecraft:bypasses_enchantments} has exactly one:
 * {@code minecraft:sonic_boom}.</b> So the Warden's sonic boom is the only vanilla
 * damage type that is unmitigated by both armour and Protection -- and void damage,
 * despite ignoring armour, resistance and even invulnerability, <b>is still reduced by
 * Protection</b>. Reaching for it would have shipped an effect a diamond set quietly
 * halves.
 *
 * <p>Rather than borrow {@code sonic_boom} -- whose death message is "was obliterated
 * by a sonically-charged shriek" -- this ships its own data-driven damage type,
 * {@code entropymod:tremor}, and adds it to both vanilla tags. Tag files merge
 * additively across namespaces, the same property the mod's {@code potion.potency.*}
 * lang entries already rely on. See {@code EntropyDamageTypes}.
 *
 * <p><b>Stated, not hidden -- what this still does NOT bypass:</b> Resistance
 * (deliberate: {@code #bypasses_resistance} holds only the two kill-everything types,
 * and a potion effect the player chose to drink should count), and the mod's own
 * {@code EffectHooks.damageTakenMultiplier}, so Iron Skin and Giant Size still scale
 * it and Crouch Invincibility still cancels it outright. Those are effect-vs-effect
 * interactions, not armour.
 *
 * <p>apply()/remove() are empty by design -- see {@link HookEffectBehavior}.
 */
public final class EarthquakeBehavior extends HookEffectBehavior {

	public static final String ID = "earthquake";

	/** Shortest gap between tremors: 45 seconds. */
	public static final int MIN_INTERVAL_TICKS = 900;

	/** Longest gap between tremors: 3 minutes. */
	public static final int MAX_INTERVAL_TICKS = 3600;

	/**
	 * How long one tremor lasts: <b>4 seconds</b>.
	 *
	 * <p>The brief left this open. It is set from the bottom by a hard vanilla
	 * constraint and from the top by what Slowness II costs.
	 *
	 * <p><b>The floor is {@code LivingEntity.INVULNERABLE_DURATION}.</b> In
	 * {@code hurtServer}, a hit sets {@code invulnerableTime = 20}, and while that
	 * counter is still above 10 a second hit of equal or smaller size is
	 * <em>discarded outright</em> rather than queued. So two 1.0-damage hits closer
	 * than <b>10 ticks</b> apart are one hit, and an effect that promised two would
	 * silently deliver one. Any tremor shorter than about a second cannot express its
	 * own damage.
	 *
	 * <p><b>The ceiling is playability.</b> Slowness II is a 30% movement penalty; it
	 * is a real tactical window -- you cannot outrun a creeper or reposition out of a
	 * fight during one -- and at much beyond four seconds it starts defining the
	 * minute rather than interrupting it, given the tremor arrives every 45-180s.
	 *
	 * <p>80 ticks gives the two hits an 80-tick separation, eight times the discard
	 * window, so they are unambiguously two events with no reliance on the margin.
	 */
	public static final int TREMOR_TICKS = 80;

	/** Slowness II. Amplifier 1 is displayed level II -- see the Phoenix grants. */
	public static final int SLOWNESS_AMPLIFIER = 1;

	/** Half a heart, twice. */
	public static final float TREMOR_DAMAGE = 1.0f;

	/**
	 * When inside the tremor the two hits land: <b>the first tick and the last</b>.
	 *
	 * <p>Chosen over "evenly spaced" because it makes the tremor's shape legible: the
	 * first hit is what tells the player it has started, the second is what tells them
	 * it is over, and the Slowness in between is bracketed by them. Evenly spacing two
	 * hits inside 80 ticks would put them at 27 and 53 with silence at both ends, so
	 * the beginning and end of the window -- the two moments the player has to react
	 * to -- would be the two moments nothing happens.
	 */
	public static final int[] DAMAGE_TICKS = {0, TREMOR_TICKS};

	/**
	 * True if a hit lands on this tick of the tremor.
	 *
	 * <p>Free of Minecraft types so the harness can drive the real schedule, including
	 * the separation check against the 10-tick discard window.
	 *
	 * @param elapsed ticks since the tremor began, 0 on the tick it starts
	 */
	public static boolean isDamageTick(int elapsed) {
		for (int tick : DAMAGE_TICKS) {
			if (tick == elapsed) {
				return true;
			}
		}
		return false;
	}

	/** True once the tremor has run its course and the tracker should drop it. */
	public static boolean isFinished(int elapsed) {
		return elapsed >= TREMOR_TICKS;
	}
}
