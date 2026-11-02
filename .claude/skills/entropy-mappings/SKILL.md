---
name: entropy-mappings
description: Mojang-mappings API reference for Entropy Mod (MC 26.1.2) — the Yarn-to-Mojang name table, the changed screen/HUD render pipeline, verified GUI and command signatures, and the javap recipe for checking a symbol against the shipped jar. Read this BEFORE writing any Minecraft API code, and whenever a symbol will not resolve, a method "does not override" its supposed parent, or a tutorial's names fail to compile — almost every Minecraft snippet online is written in Yarn names, which do not exist for this version.
---

# Entropy Mod — Mojang mappings and verified API signatures

*(This was Part 0 of `CLAUDE.md`. It moved here so it loads on demand rather
than in every session; the pointer table at the bottom of `CLAUDE.md` names it.)*

This project's Loom setup uses **Mojang's official mappings**, not Yarn.
This matters because almost every Minecraft-tutorial or Stack Overflow
snippet you'll find online is written in Yarn names. As of Minecraft
1.21.11, Yarn mappings stopped being published entirely -- this project
targets 26.1.2, which is newer, so Yarn names don't even exist for it.

Confirmed correct names (verified against docs.fabricmc.net for MC 26.1.2,
not guessed) vs. the Yarn names you'll see in older guides:

| Yarn (don't use)      | Mojang (use this)              |
|------------------------|--------------------------------|
| `CustomPayload`        | `CustomPacketPayload`          |
| `CustomPayload.Id<T>`  | `CustomPacketPayload.Type<T>`  |
| `getId()` override     | `type()` override              |
| `PacketCodec`          | `StreamCodec`                  |
| `RegistryByteBuf`      | `RegistryFriendlyByteBuf`      |
| `PacketCodec.tuple`    | `StreamCodec.composite`        |
| `PayloadTypeRegistry.playS2C()` | `PayloadTypeRegistry.clientboundPlay()` |
| `PayloadTypeRegistry.playC2S()` | `PayloadTypeRegistry.serverboundPlay()` |
| `Text` / `Text.literal`| `Component` / `Component.literal` |
| `PlayerManager`        | `PlayerList`                   |
| `npc.Villager`         | `npc.villager.Villager` *(moved package)* |
| `getPlayerManager()`   | `getPlayerList()`              |
| `World`                | `Level`                        |
| `PlayerEntity`         | `Player`                       |
| `ServerPlayerEntity`   | `ServerPlayer`                 |
| `ActionResult`         | `InteractionResult`            |
| `StatusEffectInstance` | `MobEffectInstance`            |
| `ButtonWidget`         | `Button`                       |
| `.dimensions(x,y,w,h)` (button) | `.bounds(x,y,w,h)`     |
| `addDrawableChild`     | `addRenderableWidget`          |
| `DrawContext`          | *(see render pipeline note below)* |
| `TextRenderer` / `this.textRenderer` | `this.font`      |
| `ClientCommandManager` (client cmds) | `ClientCommands`         |
| `CommandManager` (server cmds)       | `Commands`               |

**One correction to the Fabric docs' own wording:** the networking doc page
says the server is accessible "via `ServerPlayerEntity.server`" as if it's a
public field. In this version's mappings it's **private**. The verified
working accessor (confirmed against two separate examples in the same docs
page) is: `((ServerLevel) player.level()).getServer()` -- `player.level()`
appears directly in the docs' server-receiver example, and the
`(ServerLevel)` cast appears in the docs' Lightning Tater example; combining
them is what actually compiles. A `serverLevel()` method (guessed before
this) does not exist on `ServerPlayer` in this mapping -- don't reuse that.


`Identifier` is correct as-is in both -- Mojang appears to have renamed
their own `ResourceLocation` to `Identifier` at some point, so this one
name is shared between old-Yarn-style code and current Mojang mappings.

**Bigger change: the screen render pipeline itself is different in this MC
version**, not just renamed. Screens no longer override
`render(DrawContext, mouseX, mouseY, delta)`. Instead they override
`extractRenderState(GuiGraphicsExtractor graphics, mouseX, mouseY, delta)`
(must call `super.extractRenderState(...)` first).

**This is now VERIFIED, not guessed** (commit `422687d`). Every GUI symbol
was checked with `javap` against the deobfuscated jar rather than inferred
from docs. If you need to check something else, this is the command:

```bash
javap -p -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/\
minecraft-clientonly-deobf/26.1.2/minecraft-clientonly-deobf-26.1.2.jar \
  net.minecraft.client.gui.GuiGraphicsExtractor
```

Use `javap -p -c` for bytecode when argument *order* is what's in doubt.
Prefer this over the docs site -- it is the actual shipped API.

Facts that checking produced, three of which contradicted reasonable guesses:

- `GuiGraphicsExtractor` **is** in `net.minecraft.client.gui`. The old note
  called this the least-verified import in the project; it was right.
- **`mouseClicked` changed signature**: it is now
  `mouseClicked(MouseButtonEvent, boolean)`, *not*
  `(double mouseX, double mouseY, int button)`. Get coordinates from
  `event.x()` / `event.y()`. Any tutorial older than this version is wrong
  here, and the mismatch fails as "method does not override" rather than
  anything that points at the real cause.
- **`fill(x1, y1, x2, y2, argb)` is corner-to-corner, not `(x, y, w, h)`.**
  Its bytecode swaps each pair when out of order, so either winding works,
  but passing a width where a right edge belongs silently draws garbage.
- **`fill` colours need an explicit alpha byte.** `0x707070` has alpha 0 and
  draws *nothing*. Use `0xFF707070`. Note this differs from text colours,
  which `Font` promotes to opaque when the alpha bits are empty -- so
  `0xFFFFFF` works for text but is invisible as a fill. This asymmetry is a
  very easy way to lose an hour.
- Useful `GuiGraphicsExtractor` methods beyond `text`: `fill`, `fillGradient`,
  `outline`, `centeredText`, `textWithWordWrap`, `enableScissor` /
  `disableScissor`, `pose()` (a JOML `Matrix3x2fStack` with
  `pushMatrix`/`popMatrix`/`scale`/`translate` -- this is how you get
  smaller text, since Minecraft has exactly one font size).
- Custom widgets extend `AbstractWidget(x, y, w, h, Component)` and implement
  `extractWidgetRenderState(...)` + `updateWidgetNarration(...)`.
  `AbstractWidget.mouseClicked` hit-tests the full widget rect, plays the
  click sound, then calls `onClick` -- so a panel-sized widget gets
  whole-panel clicking for free. Prefer this over `Button` when the visual
  is custom, and over manual hit-testing in `Screen`.
- **`HudRenderCallback` does not exist in this Fabric API version.** Nearly
  every HUD tutorial online uses it. The current API is
  `HudElementRegistry.addLast(Identifier, HudElement)` (also `addFirst` /
  `attachElementBefore` / `attachElementAfter`, anchored on the ids in
  `VanillaHudElements`), and `HudElement` is a single method:
  `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` -- the same
  graphics type the screen pipeline uses.
- **The HUD keeps rendering while a `Screen` is open.** Confirmed in
  `GameRenderer`'s bytecode: the `Gui.extractRenderState` call is gated only
  on a boolean parameter, while the `Screen` call is separately gated on
  `minecraft.screen != null`. Any HUD element that shouldn't show behind an
  open GUI must check `Minecraft.getInstance().screen == null` itself.
- `StreamCodec.composite` supports up to 12 component pairs here, so the
  old "arity limit" worry in the networking notes no longer applies.
- **`source.hasPermission(2)` does not exist.** This is the idiom in every
  server-command tutorial and it is gone in this version. `CommandSourceStack`
  has no `hasPermission` method at all; permissions now go through
  `PermissionSet` / `PermissionCheck`. The verified working form, taken from
  vanilla `GameRuleCommand`'s own bytecode, is:

  ```java
  Commands.literal("mycommand")
      .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
  ```

  The levels are `Commands.LEVEL_ALL` / `LEVEL_MODERATORS` / `LEVEL_GAMEMASTERS`
  (the old level 2) / `LEVEL_ADMINS` / `LEVEL_OWNERS`. `Commands.hasPermission`
  is a static helper returning a `PermissionProviderCheck`, which implements
  `Predicate`, which is what `.requires` wants. Note the two `hasPermission`s
  are different things — the static one on `Commands` exists, the instance one
  on the source does not.
- **There is no `Entity.getServer()`** in this mapping — the convenience
  accessor most code reaches for simply isn't there. But `ServerPlayer.level()`
  is a **covariant override returning `ServerLevel`**, so from a `ServerPlayer`
  the accessor is just `player.level().getServer()`, no cast needed. (The
  `(ServerLevel)` cast recorded above is still required when you only have a
  `Player`.)
- **`ServerEntityWorldChangeEvents` → `ServerEntityLevelChangeEvents`**, same
  World→Level rename as everything else, and the field is
  `AFTER_PLAYER_CHANGE_LEVEL` (not `AFTER_PLAYER_CHANGE`). Its functional method
  is `afterChangeLevel(ServerPlayer, ServerLevel origin, ServerLevel destination)`.
- **`DimensionDataStorage` → `SavedDataStorage`**, reached via
  `ServerLevel.getDataStorage()`. `SavedData` no longer has `save(CompoundTag)`
  / a load function; it is **codec-based** now:
  `new SavedDataType<>(Identifier, Supplier<T>, Codec<T>, DataFixTypes)` plus
  `storage.computeIfAbsent(TYPE)`. Any tutorial showing `SavedData.load`/`save`
  overrides predates this.
- **`AttributeModifier` is keyed by `Identifier`, not `UUID`.** Every older guide
  builds a `UUID.fromString("...")` constant table; that constructor is gone. The
  record is `AttributeModifier(Identifier, double, Operation)`, and
  `AttributeInstance` looks modifiers up by `Identifier` throughout
  (`getModifier`, `hasModifier`, `removeModifier`). Deriving the id from
  something stable you already have beats a hardcoded UUID table.
- **`addTransientModifier` throws `IllegalArgumentException` on a duplicate id**
  — verified by running it. Use **`addOrUpdateTransientModifier`** (or
  `addOrReplacePermanentModifier`) for anything that might be applied more than
  once; its bytecode removes the existing id first, which is what makes
  re-application idempotent. The failure mode here is a crash on respawn rather
  than a silent double-buff, which is at least loud — but it is still a crash.
- Server commands register via
  `CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ...)`
  — **three** parameters, from `net.fabricmc.fabric.api.command.v2`. The
  client-side callback in `...client.command.v2` takes only two, so copying one
  shape into the other fails as a lambda-arity error that doesn't name the
  cause. `CommandSourceStack` is the server source type;
  `FabricClientCommandSource` is the client one.

If you hit a "cannot find symbol" error not covered by this table: check
`docs.fabricmc.net/develop/...` for the specific system (networking, GUI,
text, etc.) -- the docs site versions itself per MC release and the current
page reflects 26.1.2 specifically, which is what this project needs.
