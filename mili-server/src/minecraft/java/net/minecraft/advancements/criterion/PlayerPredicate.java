package net.minecraft.advancements.criterion;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap.Entry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record PlayerPredicate(
    MinMaxBounds.Ints level,
    GameTypePredicate gameType,
    List<PlayerPredicate.StatMatcher<?>> stats,
    Object2BooleanMap<ResourceKey<Recipe<?>>> recipes,
    Map<Identifier, PlayerPredicate.AdvancementPredicate> advancements,
    Optional<EntityPredicate> lookingAt,
    Optional<InputPredicate> input
) implements EntitySubPredicate {
    public static final int LOOKING_AT_RANGE = 100;
    public static final MapCodec<PlayerPredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                MinMaxBounds.Ints.CODEC.optionalFieldOf("level", MinMaxBounds.Ints.ANY).forGetter(PlayerPredicate::level),
                GameTypePredicate.CODEC.optionalFieldOf("gamemode", GameTypePredicate.ANY).forGetter(PlayerPredicate::gameType),
                PlayerPredicate.StatMatcher.CODEC.listOf().optionalFieldOf("stats", List.of()).forGetter(PlayerPredicate::stats),
                ExtraCodecs.object2BooleanMap(Recipe.KEY_CODEC).optionalFieldOf("recipes", Object2BooleanMaps.emptyMap()).forGetter(PlayerPredicate::recipes),
                Codec.unboundedMap(Identifier.CODEC, PlayerPredicate.AdvancementPredicate.CODEC)
                    .optionalFieldOf("advancements", Map.of())
                    .forGetter(PlayerPredicate::advancements),
                EntityPredicate.CODEC.optionalFieldOf("looking_at").forGetter(PlayerPredicate::lookingAt),
                InputPredicate.CODEC.optionalFieldOf("input").forGetter(PlayerPredicate::input)
            )
            .apply(instance, PlayerPredicate::new)
    );

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return false;
        } else if (!this.level.matches(serverPlayer.experienceLevel)) {
            return false;
        } else if (!this.gameType.matches(serverPlayer.gameMode())) {
            return false;
        } else {
            StatsCounter stats = serverPlayer.getStats();

            for (PlayerPredicate.StatMatcher<?> statMatcher : this.stats) {
                if (!statMatcher.matches(stats)) {
                    return false;
                }
            }

            ServerRecipeBook recipeBook = serverPlayer.getRecipeBook();

            for (Entry<ResourceKey<Recipe<?>>> entry : this.recipes.object2BooleanEntrySet()) {
                if (recipeBook.contains(entry.getKey()) != entry.getBooleanValue()) {
                    return false;
                }
            }

            if (!this.advancements.isEmpty()) {
                PlayerAdvancements advancements = serverPlayer.getAdvancements();
                ServerAdvancementManager advancements1 = serverPlayer.level().getServer().getAdvancements();

                for (java.util.Map.Entry<Identifier, PlayerPredicate.AdvancementPredicate> entry1 : this.advancements.entrySet()) {
                    AdvancementHolder advancementHolder = advancements1.get(entry1.getKey());
                    if (advancementHolder == null || !entry1.getValue().test(advancements.getOrStartProgress(advancementHolder))) {
                        return false;
                    }
                }
            }

            if (this.lookingAt.isPresent()) {
                Vec3 eyePosition = serverPlayer.getEyePosition();
                Vec3 viewVector = serverPlayer.getViewVector(1.0F);
                Vec3 vec3 = eyePosition.add(viewVector.x * 100.0, viewVector.y * 100.0, viewVector.z * 100.0);
                EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                    serverPlayer.level(), serverPlayer, eyePosition, vec3, new AABB(eyePosition, vec3).inflate(1.0), entity2 -> !entity2.isSpectator(), 0.0F
                );
                if (entityHitResult == null || entityHitResult.getType() != HitResult.Type.ENTITY) {
                    return false;
                }

                Entity entity1 = entityHitResult.getEntity();
                if (!this.lookingAt.get().matches(serverPlayer, entity1) || !serverPlayer.hasLineOfSight(entity1)) {
                    return false;
                }
            }

            return !this.input.isPresent() || this.input.get().matches(serverPlayer.getLastClientInput());
        }
    }

    @Override
    public MapCodec<PlayerPredicate> codec() {
        return EntitySubPredicates.PLAYER;
    }

    record AdvancementCriterionsPredicate(Object2BooleanMap<String> criterions) implements PlayerPredicate.AdvancementPredicate {
        public static final Codec<PlayerPredicate.AdvancementCriterionsPredicate> CODEC = ExtraCodecs.object2BooleanMap(Codec.STRING)
            .xmap(PlayerPredicate.AdvancementCriterionsPredicate::new, PlayerPredicate.AdvancementCriterionsPredicate::criterions);

        @Override
        public boolean test(AdvancementProgress progress) {
            for (Entry<String> entry : this.criterions.object2BooleanEntrySet()) {
                CriterionProgress criterion = progress.getCriterion(entry.getKey());
                if (criterion == null || criterion.isDone() != entry.getBooleanValue()) {
                    return false;
                }
            }

            return true;
        }
    }

    record AdvancementDonePredicate(boolean state) implements PlayerPredicate.AdvancementPredicate {
        public static final Codec<PlayerPredicate.AdvancementDonePredicate> CODEC = Codec.BOOL
            .xmap(PlayerPredicate.AdvancementDonePredicate::new, PlayerPredicate.AdvancementDonePredicate::state);

        @Override
        public boolean test(AdvancementProgress progress) {
            return progress.isDone() == this.state;
        }
    }

    interface AdvancementPredicate extends Predicate<AdvancementProgress> {
        Codec<PlayerPredicate.AdvancementPredicate> CODEC = Codec.either(
                PlayerPredicate.AdvancementDonePredicate.CODEC, PlayerPredicate.AdvancementCriterionsPredicate.CODEC
            )
            .xmap(Either::unwrap, advancementPredicate -> {
                if (advancementPredicate instanceof PlayerPredicate.AdvancementDonePredicate advancementDonePredicate) {
                    return Either.left(advancementDonePredicate);
                } else if (advancementPredicate instanceof PlayerPredicate.AdvancementCriterionsPredicate advancementCriterionsPredicate) {
                    return Either.right(advancementCriterionsPredicate);
                } else {
                    throw new UnsupportedOperationException();
                }
            });
    }

    public static class Builder {
        private MinMaxBounds.Ints level = MinMaxBounds.Ints.ANY;
        private GameTypePredicate gameType = GameTypePredicate.ANY;
        private final ImmutableList.Builder<PlayerPredicate.StatMatcher<?>> stats = ImmutableList.builder();
        private final Object2BooleanMap<ResourceKey<Recipe<?>>> recipes = new Object2BooleanOpenHashMap<>();
        private final Map<Identifier, PlayerPredicate.AdvancementPredicate> advancements = Maps.newHashMap();
        private Optional<EntityPredicate> lookingAt = Optional.empty();
        private Optional<InputPredicate> input = Optional.empty();

        public static PlayerPredicate.Builder player() {
            return new PlayerPredicate.Builder();
        }

        public PlayerPredicate.Builder setLevel(MinMaxBounds.Ints level) {
            this.level = level;
            return this;
        }

        public <T> PlayerPredicate.Builder addStat(StatType<T> type, Holder.Reference<T> value, MinMaxBounds.Ints range) {
            this.stats.add(new PlayerPredicate.StatMatcher<>(type, value, range));
            return this;
        }

        public PlayerPredicate.Builder addRecipe(ResourceKey<Recipe<?>> recipe, boolean unlocked) {
            this.recipes.put(recipe, unlocked);
            return this;
        }

        public PlayerPredicate.Builder setGameType(GameTypePredicate gameType) {
            this.gameType = gameType;
            return this;
        }

        public PlayerPredicate.Builder setLookingAt(EntityPredicate.Builder lookingAt) {
            this.lookingAt = Optional.of(lookingAt.build());
            return this;
        }

        public PlayerPredicate.Builder checkAdvancementDone(Identifier advancement, boolean done) {
            this.advancements.put(advancement, new PlayerPredicate.AdvancementDonePredicate(done));
            return this;
        }

        public PlayerPredicate.Builder checkAdvancementCriterions(Identifier advancement, Map<String, Boolean> criterions) {
            this.advancements.put(advancement, new PlayerPredicate.AdvancementCriterionsPredicate(new Object2BooleanOpenHashMap<>(criterions)));
            return this;
        }

        public PlayerPredicate.Builder hasInput(InputPredicate input) {
            this.input = Optional.of(input);
            return this;
        }

        public PlayerPredicate build() {
            return new PlayerPredicate(this.level, this.gameType, this.stats.build(), this.recipes, this.advancements, this.lookingAt, this.input);
        }
    }

    record StatMatcher<T>(StatType<T> type, Holder<T> value, MinMaxBounds.Ints range, Supplier<Stat<T>> stat) {
        public static final Codec<PlayerPredicate.StatMatcher<?>> CODEC = BuiltInRegistries.STAT_TYPE
            .byNameCodec()
            .dispatch(PlayerPredicate.StatMatcher::type, PlayerPredicate.StatMatcher::createTypedCodec);

        public StatMatcher(StatType<T> type, Holder<T> value, MinMaxBounds.Ints range) {
            this(type, value, range, Suppliers.memoize(() -> type.get(value.value())));
        }

        private static <T> MapCodec<PlayerPredicate.StatMatcher<T>> createTypedCodec(StatType<T> statType) {
            return RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        statType.getRegistry()
                            .holderByNameCodec()
                            .fieldOf("stat")
                            .forGetter(PlayerPredicate.StatMatcher::value),
                        MinMaxBounds.Ints.CODEC
                            .optionalFieldOf("value", MinMaxBounds.Ints.ANY)
                            .forGetter(PlayerPredicate.StatMatcher::range)
                    )
                    .apply(instance, (holder, ints) -> new PlayerPredicate.StatMatcher<>(statType, holder, ints))
            );
        }

        public boolean matches(StatsCounter statsCounter) {
            return this.range.matches(statsCounter.getValue(this.stat.get()));
        }
    }
}
