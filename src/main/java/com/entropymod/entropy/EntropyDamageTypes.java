package com.entropymod.entropy;

import com.entropymod.EntropyMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * The mod's own damage types.
 *
 * <h2>This is DATA, not code -- there is nothing to register in Java</h2>
 *
 * <p>Unlike {@code EntropyAttributes}, which registers into {@code BuiltInRegistries}
 * during {@code onInitialize} and has to dance around Fabric's delayed registry
 * freeze, damage types live in a <b>dynamic, datapack-loaded registry</b>. The whole
 * definition is {@code data/entropymod/damage_type/tremor.json} and its two tag
 * memberships are {@code data/minecraft/tags/damage_type/*.json}; this class holds
 * only the key needed to look the loaded value back up.
 *
 * <p>That also means <b>the holder cannot be resolved before a world is loaded</b>,
 * so this exposes a per-call lookup against a live {@code ServerLevel} rather than a
 * cached static. Same discipline as the {@code PICKUP_RANGE} note in CLAUDE.md, for
 * a different reason: there the value is unbound until the freeze, here the registry
 * itself does not exist until a level does.
 *
 * <h2>The tag memberships are the entire mechanism</h2>
 *
 * <p>{@code entropymod:tremor} is added to <b>{@code #minecraft:bypasses_armor}</b>
 * and <b>{@code #minecraft:bypasses_enchantments}</b> by shipping files at those
 * vanilla paths. Tag loading merges every namespace's contribution additively unless
 * a file sets {@code "replace": true}, so this adds one entry to each vanilla tag
 * rather than overwriting nineteen and one. The mod's {@code potion.potency.*} lang
 * entries already rely on the same additive-merge property on the assets side.
 *
 * <p><b>Why a new type rather than reusing {@code minecraft:sonic_boom}</b>, which is
 * the only vanilla type in both tags: the death message. Sonic boom renders as "was
 * obliterated by a sonically-charged shriek", which for a tremor is simply a lie. A
 * new type costs three small JSON files and one lang key and gets
 * {@code death.attack.tremor} instead.
 *
 * <p>{@code "scaling": "never"} because difficulty-scaling a fixed half-heart tremor
 * would make the number in {@code EarthquakeBehavior} untrue on Hard.
 * {@code "exhaustion": 0.0} because the tremor should not also make the player hungry.
 */
public final class EntropyDamageTypes {

	/** {@code entropymod:tremor} -- Earthquake's unmitigated ground shock. */
	public static final ResourceKey<DamageType> TREMOR =
			ResourceKey.create(Registries.DAMAGE_TYPE, EntropyMod.id("tremor"));

	private EntropyDamageTypes() {}

	/**
	 * A tremor damage source with <b>no attacker and no direct entity</b>.
	 *
	 * <p>Deliberate on both counts. There is nothing to blame the shaking on, so the
	 * death message stays impersonal; and {@code LivingEntity.hurtServer} only applies
	 * knockback when the source has an entity, so an attacker-less source means the
	 * tremor shakes the player without shoving them, which is what
	 * {@code #minecraft:no_knockback} would otherwise have had to buy.
	 */
	public static DamageSource tremor(ServerLevel level) {
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(TREMOR);
		return new DamageSource(type);
	}
}
