package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Earthquake (BAD / DEBUFF, Tier 2) -- the ground shakes on a random timer: Slowness
 * III for the tremor, a brief blindness, two half-heart hits of true damage, and the
 * ground itself thrown up as dust.
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
	 * constraint and from the top by what the Slowness costs.
	 *
	 * <p><b>The floor is {@code LivingEntity.INVULNERABLE_DURATION}.</b> In
	 * {@code hurtServer}, a hit sets {@code invulnerableTime = 20}, and while that
	 * counter is still above 10 a second hit of equal or smaller size is
	 * <em>discarded outright</em> rather than queued. So two 1.0-damage hits closer
	 * than <b>10 ticks</b> apart are one hit, and an effect that promised two would
	 * silently deliver one. Any tremor shorter than about a second cannot express its
	 * own damage.
	 *
	 * <p><b>The ceiling is playability.</b> The Slowness is a 45% movement penalty (see
	 * {@link #SLOWNESS_AMPLIFIER}); it is a real tactical window -- you cannot outrun a
	 * creeper or reposition out of a fight during one -- and at much beyond four seconds
	 * it starts defining the minute rather than interrupting it, given the tremor
	 * arrives every 45-180s. <b>This duration is unchanged by the Slowness III retune</b>,
	 * and so are the two damage ticks: the strengthening happens inside the window
	 * rather than by widening it.
	 *
	 * <p>80 ticks gives the two hits an 80-tick separation, eight times the discard
	 * window, so they are unambiguously two events with no reliance on the margin.
	 */
	public static final int TREMOR_TICKS = 80;

	/**
	 * Slowness <b>III</b>. Amplifier 2 is displayed level III -- see the Phoenix grants
	 * for the {@code "potion.potency." + amplifier} convention.
	 *
	 * <h2>The real number, and why the squared rule does NOT apply here</h2>
	 *
	 * <p>Vanilla registers Slowness as {@code MOVEMENT_SPEED},
	 * <b>-0.15000000596046448</b>, {@code ADD_MULTIPLIED_TOTAL}, and
	 * {@code AttributeTemplate.create} scales by {@code amplifier + 1}. So:
	 *
	 * <table border="1">
	 *   <caption>what each level actually multiplies speed by</caption>
	 *   <tr><th></th><th>modifier</th><th>speed x</th><th>walking</th><th>sprinting</th></tr>
	 *   <tr><td>none</td><td>--</td><td>1.00</td><td>4.3172 b/s</td><td>5.6123 b/s</td></tr>
	 *   <tr><td>Slowness II (was)</td><td>-0.30</td><td>0.70</td><td>3.0220 b/s</td><td>3.9286 b/s</td></tr>
	 *   <tr><td><b>Slowness III</b></td><td><b>-0.45</b></td><td><b>0.55</b></td><td><b>2.3745 b/s</b></td><td><b>3.0868 b/s</b></td></tr>
	 * </table>
	 *
	 * <p><b>CLAUDE.md 1e/1l's "real speed goes as the SQUARE of the attribute" is a MOB
	 * property and is not relevant here.</b> It holds because {@code Mob.setSpeed} also
	 * calls {@code setZza}, so a mob's forward input is its speed value and
	 * {@code moveRelative} multiplies the two. A player's forward input comes from
	 * {@code ClientInput} as a normalised 1.0, so player ground speed is <b>linear</b>
	 * in the attribute and the attribute multiplier <em>is</em> the speed multiplier --
	 * exactly as recorded for Phoenix Chambered Heart's Speed III grant. Squaring here
	 * would have predicted 0.3025x and been wrong by nearly half.
	 *
	 * <p><b>Why III rather than II: 2.70 blocks/second.</b> That is a creeper's real
	 * chasing speed (attribute 0.25, and <em>there</em> the squared rule does apply:
	 * {@code 0.25^2 / 0.454}). Under Slowness II a walking player manages 3.02 b/s and
	 * still pulls away from one; under Slowness III they manage <b>2.3745 b/s and no
	 * longer can</b>. The step from II to III is precisely the threshold where walking
	 * stops being an escape and sprinting becomes mandatory -- which is what makes the
	 * tremor a tactical event rather than an inconvenience.
	 */
	public static final int SLOWNESS_AMPLIFIER = 2;

	/**
	 * How long the player is blinded: <b>2 seconds</b>, half the tremor.
	 *
	 * <h2>"Brief" has a hard floor, and it is about 20 ticks</h2>
	 *
	 * <p>Blindness is not a step function. {@code BlindnessFogEnvironment.setupFog},
	 * javap-verified, is:
	 *
	 * <pre>
	 * float f = instance.isInfiniteDuration() ? 5.0f
	 *         : Mth.lerp(Math.min(1.0f, instance.getDuration() / 20.0f), renderDistance, 5.0f);
	 * fogData.environmentalEnd = f;   // ...and start = f * 0.25, skyEnd/cloudEnd = f * 0.8
	 * </pre>
	 *
	 * <p>and {@code getModifiedDarkness} does the same thing with
	 * {@code endsWithin(19)}. <b>The lerp factor is driven by REMAINING duration</b>, so
	 * blindness snaps to full strength instantly and then eases back out over its final
	 * 20 ticks.
	 *
	 * <p><b>The consequence: a blindness of 20 ticks or fewer is entirely fade-out.</b>
	 * It starts at factor {@code duration / 20 < 1} and decays from there, so it never
	 * reaches full darkness at all -- a 10-tick blindness peaks at half strength and
	 * reads as a flicker, not as being blinded. The shortest grant that ever reaches
	 * full strength is 21 ticks. So "brief" cannot mean "a few ticks" here; below the
	 * fade window it stops being brief and becomes merely weak.
	 *
	 * <p>40 ticks is the smallest value that is unambiguously the effect rather than the
	 * fade: <b>20 ticks at full darkness, then the 20-tick ease-out</b>.
	 *
	 * <p><b>It starts with the tremor, at tick 0</b>, and that is a timing decision
	 * about the two damage hits rather than a default. Beginning it at 0 puts it on the
	 * same tick as the opening half-heart, so the blindness is the thing that explains
	 * the hit. Ending it at 40 -- the halfway point -- hands the player <b>two full
	 * seconds of restored vision before the closing hit at tick 80</b>, so the second
	 * hit is one they can see coming and position for. Blinding through the whole window
	 * would make both hits unavoidable and quietly cost this effect its
	 * {@code counterplay = true}; offsetting the start would mean blindness arriving
	 * mid-tremor with no triggering event, which reads as a bug rather than as a tremor.
	 *
	 * <p>Amplifier is 0 and could not be anything else: Blindness is registered as a
	 * bare {@code MobEffect(HARMFUL, ...)} with no {@code addAttributeModifier} call at
	 * all, so nothing reads its amplifier -- already established for Phoenix Chambered
	 * Heart and asserted at amplifiers 0-9 there.
	 */
	public static final int BLINDNESS_TICKS = 40;

	/** Blindness has no attribute template, so the amplifier is meaningless. */
	public static final int BLINDNESS_AMPLIFIER = 0;

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

	// ==================================================================
	// Earth-shattering particles
	// ==================================================================

	/**
	 * How often a dust burst is thrown out during the tremor: <b>every half second</b>.
	 *
	 * <p>The brief asked for something sized to a four-second effect rather than a
	 * single instant burst. One burst at tick 0 would be an impact, not a tremor, and
	 * would be over before the Slowness the player is still feeling; a burst every tick
	 * would be 80 packets and a wall of dust. Every 10 ticks gives <b>nine bursts</b>
	 * across the window, which reads as sustained shaking rather than as either a pulse
	 * or a fog bank.
	 */
	public static final int PARTICLE_INTERVAL = 10;

	/** Burst intensity on an ordinary tick of the tremor. */
	public static final int PARTICLE_COUNT = 2;

	/**
	 * Burst intensity on the two damage ticks.
	 *
	 * <p>Deliberately heavier, so the particles <em>reinforce</em> the half-heart hits
	 * rather than running uniformly underneath them. The tremor then has a visible
	 * shape -- big, small small small, big -- matching exactly what the damage is doing.
	 */
	public static final int PARTICLE_COUNT_ON_HIT = 5;

	/** True if a dust burst is thrown on this tick of the tremor. */
	public static boolean isParticleTick(int elapsed) {
		return elapsed >= 0 && elapsed <= TREMOR_TICKS && elapsed % PARTICLE_INTERVAL == 0;
	}

	/** Burst intensity for this tick -- heavier on the two damage ticks. */
	public static int particleCountFor(int elapsed) {
		return isDamageTick(elapsed) ? PARTICLE_COUNT_ON_HIT : PARTICLE_COUNT;
	}

	/** True while the player is still blinded. */
	public static boolean isBlinded(int elapsed) {
		return elapsed >= 0 && elapsed < BLINDNESS_TICKS;
	}
}
