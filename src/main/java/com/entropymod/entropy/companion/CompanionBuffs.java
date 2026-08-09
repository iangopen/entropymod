package com.entropymod.entropy.companion;

import com.entropymod.EntropyMod;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Attribute buffs applied to spawned companions, and the one piece of arithmetic
 * they all get wrong if it is not written down.
 *
 * <h2>A MOB'S REAL SPEED GOES AS THE SQUARE OF ITS MOVEMENT_SPEED ATTRIBUTE</h2>
 *
 * <p>This is CLAUDE.md finding 1e, and it is the reason
 * {@link #speedModifierAmount(double)} exists rather than callers writing
 * {@code +0.25} for "25% faster". Verified in bytecode:
 *
 * <ul>
 *   <li>{@code PathNavigation} sets the mob's speed to
 *       {@code speedModifier * mob.getAttributeValue(MOVEMENT_SPEED)}.</li>
 *   <li><b>{@code Mob.setSpeed(f)} calls {@code super.setSpeed(f)} AND
 *       {@code setZza(f)}</b> -- so a mob's forward <em>input</em> is its speed
 *       value, where a player's is a normalised 1.0 -- and {@code moveRelative}
 *       multiplies the input by the speed.</li>
 * </ul>
 *
 * <p>So ground acceleration is {@code (speedModifier * attribute)^2}, and steady
 * state ground speed is linear in acceleration
 * ({@code v <- 0.546 v + a} converges to {@code a / 0.454}). The project's own two
 * published figures confirm the shape exactly: an iron golem at attribute 0.25
 * accelerates at {@code 0.0625} and reaches <b>2.70 b/s</b>, a villager at 0.5
 * accelerates at {@code 0.25} and reaches <b>10.79 b/s</b> -- four times the
 * acceleration, four times the speed.
 *
 * <p><b>Therefore real speed scales as the SQUARE of the attribute, and the
 * attribute multiplier wanted for a real speed multiplier {@code m} is
 * {@code sqrt(m)}.</b> Writing {@code m} directly overshoots badly and in a way
 * nothing in the build would catch:
 *
 * <table border="1">
 *   <caption>Naive vs derived, for the two buffs shipped here</caption>
 *   <tr><th>wanted</th><th>naive attribute x</th><th>real result</th>
 *       <th>derived attribute x</th><th>real result</th></tr>
 *   <tr><td>+25% (golems)</td><td>1.25</td><td><b>+56.3%</b></td>
 *       <td>1.118034</td><td>+25.0%</td></tr>
 *   <tr><td>+50% (audience)</td><td>1.50</td><td><b>+125.0%</b></td>
 *       <td>1.224745</td><td>+50.0%</td></tr>
 * </table>
 *
 * <h2>PERMANENT modifiers, not transient -- the opposite of the player rule</h2>
 *
 * <p>Every player effect in this project applies attributes <em>transiently</em>,
 * deliberately, so that {@code AcquiredEffects} stays the single source of truth and
 * nothing is written into the player's own NBT. <b>Companions are the exact
 * inverse.</b> Their {@code apply} is gated on {@code isFreshPick()} and never runs
 * again (see {@code CompanionRoster}), so there is nothing to re-derive a transient
 * modifier from -- it would silently vanish on the first chunk unload.
 *
 * <p>Permanent modifiers persist because {@code AttributeInstance.pack()} writes
 * {@code baseValue} and {@code permanentModifiers} and nothing else -- javap-verified
 * -- and that packed list is ordinary entity NBT, saved by vanilla with the chunk.
 * The same property the companions' invulnerability flag and the wolves' armour
 * already rely on.
 *
 * <p>A modifier rather than {@code setBaseValue} because the identifier makes the
 * buff attributable and idempotent: re-applying replaces rather than stacks, and the
 * mob's own vanilla base value stays visible underneath.
 */
public final class CompanionBuffs {

	private CompanionBuffs() {}

	/**
	 * The {@code ADD_MULTIPLIED_BASE} amount that produces a real speed multiplier
	 * of {@code realMultiplier}.
	 *
	 * <p>Minecraft-free on purpose -- same discipline as {@code CompanionMotion},
	 * {@code SpawnSchedule} and {@code TramplePath}. A speed buff that is silently
	 * 2.2x the size asked for is indistinguishable from one that is correct without
	 * a stopwatch, which is precisely the class of error the harness exists for.
	 *
	 * @param realMultiplier wanted multiple of real blocks/second, e.g. 1.25 for +25%
	 */
	public static double speedModifierAmount(double realMultiplier) {
		return Math.sqrt(realMultiplier) - 1.0;
	}

	/**
	 * The real speed multiplier an {@code ADD_MULTIPLIED_BASE} amount actually
	 * delivers. The inverse of {@link #speedModifierAmount(double)}, so the harness
	 * can check the round trip rather than restating the formula.
	 */
	public static double realSpeedMultiplier(double modifierAmount) {
		double attributeMultiplier = 1.0 + modifierAmount;
		return attributeMultiplier * attributeMultiplier;
	}

	/**
	 * Gives a companion a real movement-speed multiplier, deriving the attribute
	 * amount rather than taking it.
	 */
	public static void applySpeed(LivingEntity companion, String effectId, double realMultiplier) {
		modify(companion, Attributes.MOVEMENT_SPEED, effectId + "/speed",
				speedModifierAmount(realMultiplier));
	}

	/**
	 * Multiplies a companion's maximum health and fills the new bar.
	 *
	 * <p><b>The refill is mandatory, not decoration.</b> Raising {@code MAX_HEALTH}
	 * raises the ceiling only -- the same reason Phoenix Chambered Heart has to grant
	 * Regeneration alongside Health Boost, and the same reason Brittle Bones has to
	 * clamp downward. Without it a 500 HP golem would spawn at 100/500.
	 *
	 * @param realMultiplier the new maximum as a multiple of the old, e.g. 5.0 for +400%
	 */
	public static void applyMaxHealth(LivingEntity companion, String effectId, double realMultiplier) {
		modify(companion, Attributes.MAX_HEALTH, effectId + "/health", realMultiplier - 1.0);
		companion.setHealth(companion.getMaxHealth());
	}

	private static void modify(LivingEntity companion, Holder<Attribute> attribute,
							   String path, double amount) {
		AttributeInstance instance = companion.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(
				EntropyMod.id("companion/" + path),
				amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}
}
