package net.minecraft.advancements.criterion;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class EntitySubPredicates {
    public static final MapCodec<LightningBoltPredicate> LIGHTNING = register("lightning", LightningBoltPredicate.CODEC);
    public static final MapCodec<FishingHookPredicate> FISHING_HOOK = register("fishing_hook", FishingHookPredicate.CODEC);
    public static final MapCodec<PlayerPredicate> PLAYER = register("player", PlayerPredicate.CODEC);
    public static final MapCodec<SlimePredicate> SLIME = register("slime", SlimePredicate.CODEC);
    public static final MapCodec<RaiderPredicate> RAIDER = register("raider", RaiderPredicate.CODEC);
    public static final MapCodec<SheepPredicate> SHEEP = register("sheep", SheepPredicate.CODEC);

    private static <T extends EntitySubPredicate> MapCodec<T> register(String name, MapCodec<T> codec) {
        return Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, name, codec);
    }

    public static MapCodec<? extends EntitySubPredicate> bootstrap(Registry<MapCodec<? extends EntitySubPredicate>> registry) {
        return LIGHTNING;
    }
}
