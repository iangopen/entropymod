package com.entropymod.entropy.behavior;

import com.entropymod.entropy.StatusEffectBehavior;
import net.minecraft.world.effect.MobEffects;

/**
 * Salamander Blood (GOOD / SURVIVAL, entropy 61-100) -- Permanent Fire Resistance -- fire and lava cannot hurt you.
 *
 * <p>LivingEntity.hurtServer refuses any #is_fire source outright while the entity has Fire
 * Resistance. That check sits below ServerPlayer.hurtServer, where Flamboyant amplifies fire
 * damage, so the amplified hit is still refused: this is a complete cure for Flamboyant and
 * for Sunburn's damage, and that is deliberate -- a late-run blessing that answers an earlier
 * curse.
 */
public final class SalamanderBloodBehavior extends StatusEffectBehavior {

	public static final String ID = "salamander_blood";

	/** Zero-based, as vanilla stores it: 0 displays as level I. */
	public static final int AMPLIFIER = 0;

	public SalamanderBloodBehavior() {
		super(MobEffects.FIRE_RESISTANCE, AMPLIFIER);
	}
}
