package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public abstract sealed class ResolvableProfile implements TooltipProvider permits ResolvableProfile.Static, ResolvableProfile.Dynamic {
    private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.mapEither(ExtraCodecs.STORED_GAME_PROFILE, ResolvableProfile.Partial.MAP_CODEC).forGetter(ResolvableProfile::unpack),
                PlayerSkin.Patch.MAP_CODEC.forGetter(ResolvableProfile::skinPatch)
            )
            .apply(instance, ResolvableProfile::create)
    );
    public static final Codec<ResolvableProfile> CODEC = Codec.withAlternative(FULL_CODEC, ExtraCodecs.PLAYER_NAME, ResolvableProfile::createUnresolved);
    public static final StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.either(ByteBufCodecs.GAME_PROFILE, ResolvableProfile.Partial.STREAM_CODEC),
        ResolvableProfile::unpack,
        PlayerSkin.Patch.STREAM_CODEC,
        ResolvableProfile::skinPatch,
        ResolvableProfile::create
    );
    protected final GameProfile partialProfile;
    protected final PlayerSkin.Patch skinPatch;

    private static ResolvableProfile create(Either<GameProfile, ResolvableProfile.Partial> contents, PlayerSkin.Patch skinPatch) {
        return contents.map(
            gameProfile -> new ResolvableProfile.Static(Either.left(gameProfile), skinPatch),
            partial -> (ResolvableProfile)(partial.properties.isEmpty() && partial.id.isPresent() != partial.name.isPresent() // Paper - diff on change - heuristic for dynamic vs static resolvable profile - used in CraftPlayerProfile#buildResolvable
                ? partial.name
                    .<ResolvableProfile>map(string -> new ResolvableProfile.Dynamic(Either.left(string), skinPatch))
                    .orElseGet(() -> new ResolvableProfile.Dynamic(Either.right(partial.id.get()), skinPatch))
                : new ResolvableProfile.Static(Either.right(partial), skinPatch))
        );
    }

    public static ResolvableProfile createResolved(GameProfile profile) {
        return new ResolvableProfile.Static(Either.left(profile), PlayerSkin.Patch.EMPTY);
    }

    public static ResolvableProfile createUnresolved(String name) {
        return new ResolvableProfile.Dynamic(Either.left(name), PlayerSkin.Patch.EMPTY);
    }

    public static ResolvableProfile createUnresolved(UUID id) {
        return new ResolvableProfile.Dynamic(Either.right(id), PlayerSkin.Patch.EMPTY);
    }

    public abstract Either<GameProfile, ResolvableProfile.Partial> unpack();

    protected ResolvableProfile(GameProfile partialProfile, PlayerSkin.Patch skinPatch) {
        this.partialProfile = partialProfile;
        this.skinPatch = skinPatch;
    }

    public abstract CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver);

    public GameProfile partialProfile() {
        return this.partialProfile;
    }

    public PlayerSkin.Patch skinPatch() {
        return this.skinPatch;
    }

    static GameProfile createPartialProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        String string = name.orElse("");
        UUID uuid = id.orElseGet(() -> name.map(UUIDUtil::createOfflinePlayerUUID).orElse(Util.NIL_UUID));
        return new GameProfile(uuid, string, properties);
    }

    public abstract Optional<String> name();

    public static final class Dynamic extends ResolvableProfile {
        private static final Component DYNAMIC_TOOLTIP = Component.translatable("component.profile.dynamic").withStyle(ChatFormatting.GRAY);
        private final Either<String, UUID> nameOrId;

        public Dynamic(Either<String, UUID> nameOrId, PlayerSkin.Patch skinPatch) {
            super(ResolvableProfile.createPartialProfile(nameOrId.left(), nameOrId.right(), PropertyMap.EMPTY), skinPatch);
            this.nameOrId = nameOrId;
        }

        @Override
        public Optional<String> name() {
            return this.nameOrId.left();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || other instanceof ResolvableProfile.Dynamic dynamic && this.nameOrId.equals(dynamic.nameOrId) && this.skinPatch.equals(dynamic.skinPatch);
        }

        @Override
        public int hashCode() {
            int i = 31 + this.nameOrId.hashCode();
            return 31 * i + this.skinPatch.hashCode();
        }

        @Override
        public Either<GameProfile, ResolvableProfile.Partial> unpack() {
            return Either.right(new ResolvableProfile.Partial(this.nameOrId.left(), this.nameOrId.right(), PropertyMap.EMPTY));
        }

        @Override
        public CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver) {
            return CompletableFuture.supplyAsync(() -> resolver.fetchByNameOrId(this.nameOrId).orElse(this.partialProfile), Util.nonCriticalIoPool());
        }

        @Override
        public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
            tooltipAdder.accept(DYNAMIC_TOOLTIP);
        }
    }

    public record Partial(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        public static final ResolvableProfile.Partial EMPTY = new ResolvableProfile.Partial(Optional.empty(), Optional.empty(), PropertyMap.EMPTY);
        public static final MapCodec<ResolvableProfile.Partial> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(ResolvableProfile.Partial::name),
                    UUIDUtil.CODEC.optionalFieldOf("id").forGetter(ResolvableProfile.Partial::id),
                    UUIDUtil.STRING_CODEC.lenientOptionalFieldOf("Id").forGetter($ -> Optional.empty()), // Paper
                    ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", PropertyMap.EMPTY).forGetter(ResolvableProfile.Partial::properties)
                )
                .apply(instance, (name, uuid, uuid2, propertyMap) -> new ResolvableProfile.Partial(name, uuid2.or(() -> uuid), propertyMap)) // Paper
        );
        public static final StreamCodec<ByteBuf, ResolvableProfile.Partial> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.PLAYER_NAME.apply(ByteBufCodecs::optional),
            ResolvableProfile.Partial::name,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional),
            ResolvableProfile.Partial::id,
            ByteBufCodecs.GAME_PROFILE_PROPERTIES,
            ResolvableProfile.Partial::properties,
            ResolvableProfile.Partial::new
        );

        private GameProfile createProfile() {
            return ResolvableProfile.createPartialProfile(this.name, this.id, this.properties);
        }
    }

    public static final class Static extends ResolvableProfile {
        public static final ResolvableProfile.Static EMPTY = new ResolvableProfile.Static(Either.right(ResolvableProfile.Partial.EMPTY), PlayerSkin.Patch.EMPTY);
        private final Either<GameProfile, ResolvableProfile.Partial> contents;

        public Static(Either<GameProfile, ResolvableProfile.Partial> contents, PlayerSkin.Patch skinPatch) {
            super(contents.map(gameProfile -> (GameProfile)gameProfile, ResolvableProfile.Partial::createProfile), skinPatch);
            this.contents = contents;
        }

        @Override
        public CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver) {
            return CompletableFuture.completedFuture(this.partialProfile);
        }

        @Override
        public Either<GameProfile, ResolvableProfile.Partial> unpack() {
            return this.contents;
        }

        @Override
        public Optional<String> name() {
            return this.contents.map(gameProfile -> Optional.of(gameProfile.name()), partial -> partial.name);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || other instanceof ResolvableProfile.Static _static && this.contents.equals(_static.contents) && this.skinPatch.equals(_static.skinPatch);
        }

        @Override
        public int hashCode() {
            int i = 31 + this.contents.hashCode();
            return 31 * i + this.skinPatch.hashCode();
        }

        @Override
        public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        }
    }
}
