package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Earthquake (BAD / DEBUFF, Tier 2) -- the ground shakes on a random timer: Slowness
 * IV for the tremor, a brief blindness, two half-heart hits of true damage, and the
 * ground itself thrown up as dust with a matching impact sound.
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
	 * <p><b>The ceiling is playability.</b> The Slowness is a 60% movement penalty (see
	 * {@link #SLOWNESS_AMPLIFIER}); it is a real tactical window -- you cannot outrun a
	 * creeper even sprinting, or reposition out of a fight, during one -- and at much
	 * beyond four seconds it starts defining the minute rather than interrupting it,
	 * given the tremor arrives every 45-180s. <b>This duration is unchanged by the
	 * Slowness IV retune</b>, and so are the two damage ticks: the strengthening happens
	 * inside the window rather than by widening it. That ceiling argument is what makes
	 * the window the thing that must NOT move as the severity goes up -- a 60% penalty
	 * for four seconds is an interruption, and for ten it would be a punishment.
	 *
	 * <p>80 ticks gives the two hits an 80-tick separation, eight times the discard
	 * window, so they are unambiguously two events with no reliance on the margin.
	 */
	public static final int TREMOR_TICKS = 80;

	/**
	 * Slowness <b>IV</b>. Amplifier 3 is displayed level IV -- see the Phoenix grants
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
	 *   <tr><td>Slowness III (was)</td><td>-0.45</td><td>0.55</td><td>2.3745 b/s</td><td>3.0868 b/s</td></tr>
	 *   <tr><td><b>Slowness IV</b></td><td><b>-0.60</b></td><td><b>0.40</b></td><td><b>1.7269 b/s</b></td><td><b>2.2449 b/s</b></td></tr>
	 * </table>
	 *
	 * <p><b>CLAUDE.md 1e/1l/1z's "real speed goes as the SQUARE of the attribute" is a
	 * MOB property and is not relevant here.</b> It holds because {@code Mob.setSpeed}
	 * also calls {@code setZza}, so a mob's forward input is its speed value and
	 * {@code moveRelative} multiplies the two. A player's forward input comes from
	 * {@code ClientInput} as a normalised 1.0, so player ground speed is <b>linear</b>
	 * in the attribute and the attribute multiplier <em>is</em> the speed multiplier --
	 * exactly as recorded for Phoenix Chambered Heart's Speed III grant. Squaring here
	 * would have predicted 0.16x and been wrong by well over half.
	 *
	 * <h2>Why IV, and the threshold it buys: the SAME creeper, but sprinting</h2>
	 *
	 * <p>A creeper's real chasing speed is <b>2.7533 b/s</b> -- {@code MOVEMENT_SPEED}
	 * {@code 0.25}, read out of {@code Creeper.createAttributes} rather than assumed, and
	 * <em>there</em> the squared rule does apply ({@code 0.25^2 / 0.454}, x20 for
	 * b/tick to b/s).
	 *
	 * <p>Slowness III already put <em>walking</em> below that line (2.3745 &lt; 2.7533),
	 * which is what the III retune was for. But it left <b>sprinting at 3.0868 b/s, still
	 * comfortably clear</b> -- so the tremor's answer was always the same one button, and
	 * a player holding sprint barely noticed the difference between II and III.
	 *
	 * <p><b>Slowness IV puts sprinting at 2.2449 b/s, below the creeper too.</b> That is a
	 * real second threshold rather than "further past the first one": under IV there is no
	 * movement speed available to the player that outruns a chasing creeper, so during the
	 * tremor <em>disengaging is not an option at all</em> and the answer has to be terrain,
	 * a block, or fighting. Walking at 1.7269 b/s is below every hostile in the game worth
	 * running from.
	 *
	 * <p><b>What it does not do is approach a clamp.</b> 0.40x is a long way from zero;
	 * the modifier only reaches -1.0 (total immobility) at amplifier 6. The four-second
	 * window and both damage ticks are unchanged -- the strengthening happens inside the
	 * window rather than by widening it, for the third retune running.
	 */
	public static final int SLOWNESS_AMPLIFIER = 3;

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

	/**
	 * Burst intensity on an ordinary tick of the tremor.
	 *
	 * <h2>THIS NUMBER IS A PARTICLE COUNT, AND THE FIRST SHIP GOT IT 30x TOO SMALL</h2>
	 *
	 * <p>Earthquake shipped with {@code PARTICLE_COUNT = 2} and rendered <b>nothing the
	 * player could see</b>. Nothing was broken: the mixin-free dispatch was correct, the
	 * packet was broadcast, and the client handled it. The {@code data} int was simply
	 * read as if it were an abstract "intensity" dial when it is very nearly a literal
	 * particle count. {@code ParticleUtils.spawnSmashAttackParticles} is two loops:
	 *
	 * <pre>
	 * for (i = 0; i &lt; data / 3.0f;  i++)   // column at the player's feet, gaussian/2 spread
	 * for (i = 0; i &lt; data / 1.5f;  i++)   // ring at radius 3.5, angle = i RADIANS
	 * </pre>
	 *
	 * <p>so the two loops emit {@code data/3 + data/1.5 == data} particles between them.
	 * <b>Vanilla's only caller, {@code MaceItem.knockback}, passes 750.</b> At 2 that is
	 * <em>three</em> particles, of which the ring contributes two -- and the ring sits
	 * 3.5 blocks away, so exactly one particle per burst was anywhere near the player, for
	 * a few ticks, some of it while they were blinded. Not a subtlety: invisible.
	 *
	 * <p>60 gives 20 in the central column and 40 in the ring, which at 3.5 blocks is
	 * dense enough to close into a ring rather than read as four stray specks. See
	 * {@link #PARTICLE_COUNT_ON_HIT} for the whole-tremor budget.
	 *
	 * <p><b>The general lesson, because it is not specific to this event:</b> a
	 * {@code levelEvent} {@code data} int is an opaque per-event encoding with no type and
	 * no units. The only way to know what it means is to read the client's case for that
	 * id <em>and</em> the magnitude vanilla's own caller passes. Picking a small number
	 * because it "looks like an intensity" is guessing.
	 */
	public static final int PARTICLE_COUNT = 60;

	/**
	 * Burst intensity on the two damage ticks.
	 *
	 * <p>Deliberately heavier, so the particles <em>reinforce</em> the half-heart hits
	 * rather than running uniformly underneath them. The tremor then has a visible
	 * shape -- big, small small small, big -- matching exactly what the damage is doing.
	 *
	 * <p>The whole-tremor budget is the sanity check that this is scaled to the game
	 * rather than to itself: seven ordinary bursts at 60 plus two heavy ones at 150 is
	 * <b>720 particles across four seconds</b>, against the <b>750 a single mace smash
	 * emits in one frame</b>. So a tremor is about one mace smash's worth of dust, spread
	 * over its window -- visible for the same reason vanilla's is, and no denser.
	 */
	public static final int PARTICLE_COUNT_ON_HIT = 150;

	/**
	 * What vanilla's own smash-attack caller passes as {@code data}, kept here so the
	 * harness can state the shipped values as a fraction of it rather than as bare
	 * numbers. {@code MaceItem.knockback}, javap-verified: {@code sipush 750}.
	 */
	public static final int VANILLA_MACE_SMASH_INTENSITY = 750;

	/** True if a dust burst is thrown on this tick of the tremor. */
	public static boolean isParticleTick(int elapsed) {
		return elapsed >= 0 && elapsed <= TREMOR_TICKS && elapsed % PARTICLE_INTERVAL == 0;
	}

	/** Burst intensity for this tick -- heavier on the two damage ticks. */
	public static int particleCountFor(int elapsed) {
		return isDamageTick(elapsed) ? PARTICLE_COUNT_ON_HIT : PARTICLE_COUNT;
	}

	// ==================================================================
	// The sound cue
	// ==================================================================

	/**
	 * Volume of an ordinary burst.
	 *
	 * <h2>Which sound, and why it is not a name match</h2>
	 *
	 * <p>The sound is {@code SoundEvents.MACE_SMASH_GROUND}, and on the two damage ticks
	 * {@code MACE_SMASH_GROUND_HEAVY}. Surveying all 1815 constants on
	 * {@code SoundEvents}, the plausible candidates for a ground impact are
	 * {@code DECORATED_POT_SHATTER}, {@code GOAT_RAM_IMPACT},
	 * {@code LIGHTNING_BOLT_IMPACT}, {@code WARDEN_ATTACK_IMPACT} and the three
	 * {@code MACE_SMASH_*}. The mace pair wins for the same reason {@code DUST_PILLAR}
	 * did: <b>it is what vanilla already plays alongside event 2013.</b>
	 * {@code MaceItem.knockback}'s caller picks between {@code MACE_SMASH_GROUND} and
	 * {@code MACE_SMASH_GROUND_HEAVY} on {@code fallDistance &gt; 5.0} and fires the
	 * particle event on the same path -- so the audio and the visual here are vanilla's
	 * own matched pair, not two things chosen separately because their names sounded
	 * right. It also hands the light/heavy split the cadence already needs for free.
	 *
	 * <h2>Volume and pitch</h2>
	 *
	 * <p>Vanilla plays the mace at 1.0/1.0 <em>once</em>. This plays nine times in four
	 * seconds, so an ordinary burst is pulled down to 0.6 and pitched to
	 * {@link #SOUND_PITCH} -- below 1.0 the sample plays slower and deeper, which is what
	 * turns a weapon impact into a rumble. The two damage ticks get the heavy sample at
	 * full volume.
	 *
	 * <p><b>Range is not at risk from the quiet value.</b> {@code SoundEvent.getRange(v)}
	 * is {@code v > 1 ? 16*v : 16}, so a sub-1.0 volume does not shrink the 16-block
	 * broadcast radius -- and the player is standing at the source.
	 */
	public static final float SOUND_VOLUME = 0.6f;

	/** Volume on the two damage ticks -- vanilla's own mace volume. */
	public static final float SOUND_VOLUME_ON_HIT = 1.0f;

	/**
	 * Pitch for every burst: <b>0.6</b>, deliberately deep.
	 *
	 * <p>The client's mixer clamps pitch to {@code [0.5, 2.0]}, so this is inside the
	 * range with margin rather than sitting on the floor where a later nudge downward
	 * would silently do nothing. Both bursts share one pitch so the heavy hits read as
	 * <em>the same tremor, louder</em> rather than as a different event.
	 */
	public static final float SOUND_PITCH = 0.6f;

	/** Volume for this tick -- louder on the two damage ticks, matching the dust. */
	public static float soundVolumeFor(int elapsed) {
		return isDamageTick(elapsed) ? SOUND_VOLUME_ON_HIT : SOUND_VOLUME;
	}

	/**
	 * True if this tick's burst should use the heavy sample.
	 *
	 * <p>Deliberately the same predicate the particle count uses, so the sound and the
	 * dust cannot drift into different cadences: there is exactly one notion of "this is
	 * a big tick" and both read it.
	 */
	public static boolean isHeavySoundTick(int elapsed) {
		return isDamageTick(elapsed);
	}

	/** True while the player is still blinded. */
	public static boolean isBlinded(int elapsed) {
		return elapsed >= 0 && elapsed < BLINDNESS_TICKS;
	}
}
