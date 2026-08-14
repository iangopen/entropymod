package com.entropymod.entropy.behavior;

import com.entropymod.entropy.HookEffectBehavior;

/**
 * Earthquake (BAD / DEBUFF, entropy 40-60, <b>counterplay = false</b>) -- the ground
 * shakes on a random timer and you cannot move: total movement paralysis, blindness and
 * Mining Fatigue for the whole four-second window, two half-heart hits of true damage,
 * and the ground itself thrown up as dust with a matching impact sound.
 *
 * <p><b>This is no longer a survivable interruption and is no longer labelled as one.</b>
 * Slowness IV was replaced by a real movement lock, the blindness was extended from half
 * the window to all of it, and the effect moved to entropy 40-60 with
 * {@code counterplay = false} -- the same resolution Unstable reached, for the same
 * reason: CLAUDE.md Part 2 forbids a {@code counterplay = false} effect below 40. See
 * {@link #PARALYSIS_TICKS} for the mechanism and why no Slowness value could deliver it.
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
	 * constraint and from the top by what the movement lock costs.
	 *
	 * <p><b>The floor is {@code LivingEntity.INVULNERABLE_DURATION}.</b> In
	 * {@code hurtServer}, a hit sets {@code invulnerableTime = 20}, and while that
	 * counter is still above 10 a second hit of equal or smaller size is
	 * <em>discarded outright</em> rather than queued. So two 1.0-damage hits closer
	 * than <b>10 ticks</b> apart are one hit, and an effect that promised two would
	 * silently deliver one. Any tremor shorter than about a second cannot express its
	 * own damage.
	 *
	 * <p><b>The ceiling is playability, and it now binds much harder than it used to.</b>
	 * The player is not slowed, they are <em>rooted</em> (see {@link #PARALYSIS_TICKS}),
	 * blind, and unable to mine at normal speed, for the whole window. Four seconds of
	 * that arriving every 45-180s is an interruption; ten seconds of it would be a
	 * hostage situation, and a creeper that walked up during one would simply end the run
	 * with no input from the player at all.
	 *
	 * <p><b>This duration has survived every retune -- Slowness II, III, IV, and now the
	 * paralysis -- and so have the two damage ticks.</b> That is the pattern worth
	 * copying: when a curse needs to be more severe, raise the severity <em>inside</em>
	 * the window and leave the window alone. Each retune's own ceiling argument was the
	 * thing that stopped the duration drifting upward alongside the strength, which is
	 * how a four-second annoyance becomes a ten-second punishment one step at a time.
	 *
	 * <p>80 ticks gives the two hits an 80-tick separation, eight times the discard
	 * window, so they are unambiguously two events with no reliance on the margin.
	 */
	public static final int TREMOR_TICKS = 80;

	/**
	 * How many ticks a tremor actually occupies: <b>81</b>, not 80.
	 *
	 * <p>{@link #TREMOR_TICKS} is the <em>index of the last tick</em>, not a count.
	 * {@code isFinished(elapsed)} is {@code elapsed >= TREMOR_TICKS} and the tracker
	 * drops the tremor on that tick, so {@code elapsed} really runs
	 * {@code 0, 1, ... 80} -- <b>eighty-one ticks</b>, with a damage hit on the first
	 * and the last.
	 *
	 * <p><b>This distinction is not pedantry, and it is why this constant exists rather
	 * than {@code TREMOR_TICKS} being reused for the grants.</b> A
	 * {@code MobEffectInstance} granted with duration 80 on the tick the tremor begins
	 * is live for {@code elapsed} 0-79 and has <em>already expired</em> by the closing
	 * hit at 80. Every duration in this effect is therefore measured from here, so an
	 * effect that is supposed to cover the tremor covers all of it including both hits.
	 */
	public static final int TREMOR_SPAN_TICKS = TREMOR_TICKS + 1;

	/**
	 * Mining Fatigue <b>I</b> for the whole tremor. Amplifier 0 is displayed level I --
	 * the {@code "potion.potency." + amplifier} convention, and 0 maps to the empty
	 * string so no roman numeral is shown at all.
	 *
	 * <p>Unlike Blindness (whose amplifier is meaningless -- see
	 * {@link #BLINDNESS_AMPLIFIER}) this one is read: vanilla registers Mining Fatigue
	 * with an {@code ADD_MULTIPLIED_TOTAL} template on {@code BLOCK_BREAK_SPEED}, so the
	 * amplifier genuinely scales it. I is deliberate rather than a default -- the
	 * paralysis is the effect, and a level that made mining pointless would remove the
	 * one thing a rooted player can still usefully do.
	 */
	public static final int MINING_FATIGUE_AMPLIFIER = 0;

	/**
	 * How long the mining slowdown lasts: the whole tremor, same as everything else.
	 *
	 * <p>There is exactly one duration in this effect now. Blindness, Mining Fatigue and
	 * the paralysis all run {@link #TREMOR_TICKS}, so there is no second timeline to keep
	 * in step and no window in which one has expired and another has not.
	 */
	public static final int MINING_FATIGUE_TICKS = TREMOR_SPAN_TICKS;

	/**
	 * How long the player is rooted in place: <b>the whole tremor</b>.
	 *
	 * <h2>SLOWNESS IS GONE, AND NO VALUE OF IT COULD HAVE DONE THIS</h2>
	 *
	 * <p>The effect used to grant Slowness IV (amplifier 3, a 60% movement penalty). It
	 * now grants nothing on {@code MOVEMENT_SPEED} at all, and the reason is not that IV
	 * was too weak -- it is that <b>the attribute cannot express paralysis at any
	 * value.</b>
	 *
	 * <p><b>The attribute really does reach zero.</b> {@code Attributes}' {@code <clinit>}
	 * registers {@code movement_speed} as {@code RangedAttribute(..., 0.7, 0.0, 1024.0)}
	 * -- the minimum is a literal {@code dconst_0} -- and Slowness at amplifier 6 gets
	 * there: {@code -0.15000000596046448 x 7 = -1.05}, and {@code ADD_MULTIPLIED_TOTAL}
	 * computes {@code value *= 1 + amount}, i.e. {@code value x -0.05}, which
	 * {@code sanitizeValue} clamps to {@code 0.0}. <b>So "the attribute has a floor above
	 * zero" is false.</b> The floor is not the problem.
	 *
	 * <p><b>The problem is that a player at zero MOVEMENT_SPEED is still mobile</b>, by
	 * three independent routes -- every one of them already javap-verified by this project
	 * for Slippery Grip, which was burned by exactly these:
	 *
	 * <table border="1">
	 *   <caption>what vanilla movement reads, and what it ignores</caption>
	 *   <tr><th>#</th><th>site</th><th>reads MOVEMENT_SPEED?</th></tr>
	 *   <tr><td>1</td><td>{@code getFrictionInfluencedSpeed} =
	 *       {@code onGround() ? getSpeed()*(0.216/f^3) : getFlyingSpeed()}</td>
	 *       <td><b>airborne branch: NO</b></td></tr>
	 *   <tr><td>2</td><td>{@code Player.getFlyingSpeed} =
	 *       {@code isSprinting() ? 0.025999999f : 0.02f}</td>
	 *       <td><b>NO</b> -- hardcoded constants</td></tr>
	 *   <tr><td>3</td><td>{@code jumpFromGround}'s sprint branch: flat
	 *       {@code +0.2} blocks/tick forward</td><td><b>NO</b></td></tr>
	 * </table>
	 *
	 * <p>and {@code JUMP_STRENGTH} is a separate attribute, so vertical jumping is
	 * untouched by Slowness at any level. <b>Slowness 255 leaves a player who can jump
	 * freely and bunny-hop away with full air control.</b> That is the finding: the
	 * ceiling on slowness is not numeric, it is structural.
	 *
	 * <h2>What DOES work: {@code Player.isImmobile()}, vanilla's own movement lock</h2>
	 *
	 * <p>Inside {@code LivingEntity.aiStep}, javap-verified by offset:
	 *
	 * <pre>
	 * 218:  applyInput()              // writes xxa / zza / jumping from the input source
	 * 222:  if (isImmobile()) {
	 * 230:      this.jumping = false;
	 * 235:      this.xxa = 0.0f;      // strafe input
	 * 240:      this.zza = 0.0f;      // forward input
	 *       }
	 * 460:  jumpFromGround()          // gated on this.jumping
	 * 516:  new Vec3(xxa, yya, zza) -> travel()
	 * </pre>
	 *
	 * <p><b>It zeroes three fields and nothing else</b>, and the ordering is exactly
	 * right: the write happens after {@code applyInput} has read the keyboard and before
	 * both consumers. It also closes all three leaks above at once -- with {@code xxa}
	 * and {@code zza} zero, {@code moveRelative} multiplies a zero vector, so the
	 * airborne {@code getFlyingSpeed} branch has nothing to accelerate; with
	 * {@code jumping} false the {@code +0.2} sprint impulse is never reached.
	 *
	 * <p><b>This is a MOVEMENT-only lock by construction, not by care.</b> Attacking,
	 * mining and item use never touch {@code xxa}, {@code zza} or {@code jumping} -- they
	 * run off {@code Minecraft.handleKeybinds} / {@code MultiPlayerGameMode} and the
	 * attack/use packets, on a completely separate path from {@code aiStep}'s movement
	 * block. That is why sleeping, {@code isImmobile}'s only vanilla player use, leaves
	 * them alone. The harness asserts each of the three permitted actions individually.
	 *
	 * <p><b>Why it needs a client half.</b> The zeroing must happen where movement is
	 * <em>simulated</em>, and for a player that is the client: {@code ServerPlayer} never
	 * writes {@code jumping}, {@code xxa} or {@code zza} at all. Vanilla's own design
	 * confirms the shape -- {@code ServerGamePacketListenerImpl.handleMovePlayer} offsets
	 * 333-378 are
	 * {@code if (isSleeping()) { if (distSqr > 1.0) teleport(back); return; }}, a
	 * deliberately generous server-side backstop that exists only because the client-side
	 * input lock already did the work. So this ships as two side-scoped mixins plus
	 * {@code TremorLockPayload}, which is what tells the client a tremor is running.
	 *
	 * <p>Held at {@link #TREMOR_TICKS} so there is one duration for the whole effect.
	 */
	public static final int PARALYSIS_TICKS = TREMOR_SPAN_TICKS;

	/**
	 * True while the player is rooted in place, {@code elapsed} ticks into a tremor.
	 *
	 * <p>Minecraft-free so the harness drives the real predicate, including that it spans
	 * both damage ticks and expires exactly with the window.
	 */
	public static boolean isParalysed(int elapsed) {
		return elapsed >= 0 && elapsed < PARALYSIS_TICKS;
	}

	/**
	 * Vanilla's blindness ease-out window: <b>20 ticks</b>, read out of
	 * {@code BlindnessFogEnvironment.setupFog}'s {@code Mth.lerp(min(1, duration / 20.0f),
	 * ...)} and {@code getModifiedDarkness}'s {@code endsWithin(19)}.
	 *
	 * <p>Named rather than inlined because it appears in two different roles: it is the
	 * floor below which a blindness is <em>entirely</em> fade (so "brief" has a minimum),
	 * and it is the amount by which any grant must OVERSHOOT the window it is meant to
	 * cover at full strength. Both readings are the same 20 and both are load-bearing
	 * here.
	 */
	public static final int BLINDNESS_FADE_TICKS = 20;

	/**
	 * How long the player is blinded: <b>the whole tremor</b>, 4 seconds.
	 *
	 * <h2>THIS DELIBERATELY REMOVES THE REACTION WINDOW</h2>
	 *
	 * <p>It was 40 ticks -- half the window -- and that number was chosen to buy
	 * counterplay: "ending it at 40 hands the player two full seconds of restored vision
	 * before the closing hit at tick 80, so the second hit is one they can see coming and
	 * position for." That argument was correct, and it was load-bearing for the effect's
	 * old {@code counterplay = true}.
	 *
	 * <p><b>It is now 80, and the old javadoc's own warning is the accurate description
	 * of what ships:</b> "blinding through the whole window would make both hits
	 * unavoidable and quietly cost this effect its {@code counterplay = true}." That is
	 * exactly the intent, and the flag moved with it rather than being quietly left
	 * behind -- see {@code EffectRegistry}, where Earthquake is now
	 * {@code counterplay = false} at entropy 40-60.
	 *
	 * <p><b>The resulting experience, stated plainly:</b> the player is blind and rooted
	 * in place from tick 0, takes the opening half-heart on that same tick, cannot walk,
	 * jump or sprint for four seconds, cannot see what is around them for any of it, and
	 * takes the closing half-heart at tick 80 with the blindness still at full strength.
	 * There is no moment in the window with vision, and no moment with mobility. Mining
	 * Fatigue runs underneath the whole of it. <b>There is no reaction window at all</b>
	 * -- attacking and mining still work, but nothing the player does changes the
	 * outcome of the tremor itself.
	 *
	 * <p>One consequence worth stating because it is a real change in feel rather than a
	 * number: the 20-tick fade-out now lands on the <em>closing</em> hit rather than in
	 * the middle of the window, so vision returns as the tremor ends instead of halfway
	 * through it. The tremor reads as one continuous event rather than two halves.
	 *
	 * <h2>"Brief" had a hard floor, and it was about 20 ticks</h2>
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
	 * <h2>AND IT IS WHY "80 TICKS" IS GRANTED AS 101</h2>
	 *
	 * <p>The brief asked for blindness across the full 80-tick window. <b>Granting 80
	 * would not have delivered it, and the failure would have landed exactly on the hit
	 * the change exists to cover.</b> Because the lerp reads REMAINING duration, a grant
	 * of {@code D} at tick 0 is at full darkness only while {@code D - elapsed >= 20}:
	 *
	 * <table border="1">
	 *   <caption>what each grant actually delivers</caption>
	 *   <tr><th>grant</th><th>full darkness for</th><th>still live until</th>
	 *       <th>second hit (elapsed 80) blind?</th></tr>
	 *   <tr><td>80</td><td>elapsed 0-60</td><td>elapsed 79</td>
	 *       <td><b>NO -- expired entirely</b></td></tr>
	 *   <tr><td>81</td><td>elapsed 0-61</td><td>elapsed 80</td>
	 *       <td><b>NO -- fully faded out</b></td></tr>
	 *   <tr><td><b>101</b></td><td><b>elapsed 0-81</b></td><td>elapsed 100</td>
	 *       <td><b>YES, at full strength</b></td></tr>
	 * </table>
	 *
	 * <p>So the literal reading of "blind for the whole window" restores vision precisely
	 * in time for the closing half-heart -- i.e. it reinstates the two-second reaction
	 * window this change exists to delete, just moved. {@code TREMOR_SPAN_TICKS +
	 * BLINDNESS_FADE_TICKS} is the smallest grant that means what was asked.
	 *
	 * <p>The old 40-tick floor is kept on record because it still constrains any future
	 * retune <em>downward</em>.
	 *
	 * <p><b>It starts with the tremor, at tick 0</b>, which is unchanged and is still a
	 * decision rather than a default: beginning it at 0 puts it on the same tick as the
	 * opening half-heart, so the blindness is the thing that explains the hit. Offsetting
	 * the start would mean blindness arriving mid-tremor with no triggering event, which
	 * reads as a bug rather than as a tremor.
	 *
	 * <p>Amplifier is 0 and could not be anything else: Blindness is registered as a
	 * bare {@code MobEffect(HARMFUL, ...)} with no {@code addAttributeModifier} call at
	 * all, so nothing reads its amplifier -- already established for Phoenix Chambered
	 * Heart and asserted at amplifiers 0-9 there.
	 */
	public static final int BLINDNESS_TICKS = TREMOR_SPAN_TICKS + BLINDNESS_FADE_TICKS;

	/** Blindness has no attribute template, so the amplifier is meaningless. */
	public static final int BLINDNESS_AMPLIFIER = 0;

	/** Half a heart, twice. */
	public static final float TREMOR_DAMAGE = 1.0f;

	/**
	 * When inside the tremor the two hits land: <b>the first tick and the last</b>.
	 *
	 * <p>Chosen over "evenly spaced" because it makes the tremor's shape legible: the
	 * first hit is what tells the player it has started, the second is what tells them
	 * it is over, and the paralysis in between is bracketed by them. Evenly spacing two
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

	/** True while the blindness grant is still live at all, fade included. */
	public static boolean isBlinded(int elapsed) {
		return elapsed >= 0 && elapsed < BLINDNESS_TICKS;
	}

	/**
	 * True while the blindness is at <b>full darkness</b> rather than easing out.
	 *
	 * <p>This is the predicate that matters, and it is separate from {@link #isBlinded}
	 * deliberately: the two disagree for the final {@link #BLINDNESS_FADE_TICKS} ticks of
	 * the grant, and the whole point of the 101-tick duration is that both damage ticks
	 * fall inside <em>this</em> one rather than merely inside the other.
	 *
	 * <p>The condition is vanilla's, restated: the fog lerp is at factor 1.0 while the
	 * REMAINING duration is at least the fade window.
	 */
	public static boolean isFullyBlinded(int elapsed) {
		return elapsed >= 0 && (BLINDNESS_TICKS - elapsed) >= BLINDNESS_FADE_TICKS;
	}
}
