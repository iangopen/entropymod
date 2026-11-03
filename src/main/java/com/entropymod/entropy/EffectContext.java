package com.entropymod.entropy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an {@link EffectBehavior} needs to do its job, in one object.
 *
 * <p>This exists instead of passing a raw {@code MinecraftServer} so that later
 * additions can be added here <em>once</em> rather than editing the signature of
 * every effect class. Treat this as the extension point: adding a field here is
 * cheap, changing {@code apply(EffectContext)} is not.
 *
 * <p>Unlike the first version, this now names a <b>specific target player</b>
 * rather than "the" player. Re-application after a respawn happens for one
 * player at a time, so a behavior must be told which one — guessing
 * "first in the list" would silently buff the wrong player the moment a second
 * one exists.
 */
public final class EffectContext {

	/** Why {@link EffectBehavior#apply} is being called. */
	public enum Reason {
		/** The player just chose this effect. Happens once per effect, ever. */
		PICKED,
		/**
		 * Re-establishing an effect the player already owns, after a respawn, a
		 * rejoin, or a dimension change that rebuilt their attribute map. Happens an
		 * unbounded number of times — behaviors must be idempotent under this.
		 */
		REAPPLIED
	}

	private final MinecraftServer server;
	private final ServerPlayer target;
	private final EffectDefinition effect;
	private final Reason reason;
	private final double severityMultiplier;

	/** Ordinary severity -- what every effect gets unless All In has marked this one. */
	public EffectContext(MinecraftServer server, ServerPlayer target, EffectDefinition effect, Reason reason) {
		this(server, target, effect, reason, 1.0);
	}

	public EffectContext(MinecraftServer server, ServerPlayer target, EffectDefinition effect, Reason reason,
						 double severityMultiplier) {
		this.server = server;
		this.target = target;
		this.effect = effect;
		this.reason = reason;
		this.severityMultiplier = severityMultiplier;
	}

	public MinecraftServer server() {
		return server;
	}

	/** The player this apply/remove is for. Never null. */
	public ServerPlayer target() {
		return target;
	}

	/** The definition being applied/removed -- id, category, description. */
	public EffectDefinition effect() {
		return effect;
	}

	public Reason reason() {
		return reason;
	}

	/**
	 * How much stronger than normal this effect should land -- {@code 1.0} for
	 * everything except the one curse All In has doubled.
	 *
	 * <p><b>Computed by {@code EntropyManager} and passed in, rather than looked up
	 * here.</b> That is what keeps this class a plain carrier with no reach into run
	 * state, and it is what lets every existing harness call site keep using the
	 * four-argument constructor unchanged -- CLAUDE.md's "adding a field here is
	 * cheap" extension point working as advertised.
	 *
	 * <p>Read by {@link AttributeEffectBehavior#apply}, which multiplies every
	 * {@code Change} amount by it. Idempotency is unaffected: the multiplier is
	 * derived from persisted run state, so re-application after a respawn produces
	 * the same amount and {@code addOrUpdateTransientModifier} replaces rather than
	 * stacks, exactly as at 1.0.
	 */
	public double severityMultiplier() {
		return severityMultiplier;
	}

	/** True on a fresh pick, false when re-establishing an effect after respawn/rejoin. */
	public boolean isFreshPick() {
		return reason == Reason.PICKED;
	}

	/** Message just the target player. */
	public void tell(String message) {
		target.sendSystemMessage(Component.literal(message));
	}

	/** Message everyone. Used for run-wide announcements, not per-effect chatter. */
	public void broadcast(String message) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}
}
