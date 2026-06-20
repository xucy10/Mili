package net.minecraft.world.entity.animal.sheep;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;

public class SheepColorSpawnRules {
    private static final SheepColorSpawnRules.SheepColorSpawnConfiguration TEMPERATE_SPAWN_CONFIGURATION = new SheepColorSpawnRules.SheepColorSpawnConfiguration(
        weighted(
            builder()
                .add(single(DyeColor.BLACK), 5)
                .add(single(DyeColor.GRAY), 5)
                .add(single(DyeColor.LIGHT_GRAY), 5)
                .add(single(DyeColor.BROWN), 3)
                .add(commonColors(DyeColor.WHITE), 82)
                .build()
        )
    );
    private static final SheepColorSpawnRules.SheepColorSpawnConfiguration WARM_SPAWN_CONFIGURATION = new SheepColorSpawnRules.SheepColorSpawnConfiguration(
        weighted(
            builder()
                .add(single(DyeColor.GRAY), 5)
                .add(single(DyeColor.LIGHT_GRAY), 5)
                .add(single(DyeColor.WHITE), 5)
                .add(single(DyeColor.BLACK), 3)
                .add(commonColors(DyeColor.BROWN), 82)
                .build()
        )
    );
    private static final SheepColorSpawnRules.SheepColorSpawnConfiguration COLD_SPAWN_CONFIGURATION = new SheepColorSpawnRules.SheepColorSpawnConfiguration(
        weighted(
            builder()
                .add(single(DyeColor.LIGHT_GRAY), 5)
                .add(single(DyeColor.GRAY), 5)
                .add(single(DyeColor.WHITE), 5)
                .add(single(DyeColor.BROWN), 3)
                .add(commonColors(DyeColor.BLACK), 82)
                .build()
        )
    );

    private static SheepColorSpawnRules.SheepColorProvider commonColors(DyeColor mainColor) {
        return weighted(builder().add(single(mainColor), 499).add(single(DyeColor.PINK), 1).build());
    }

    public static DyeColor getSheepColor(Holder<Biome> biome, RandomSource random) {
        SheepColorSpawnRules.SheepColorSpawnConfiguration sheepColorConfiguration = getSheepColorConfiguration(biome);
        return sheepColorConfiguration.colors().get(random);
    }

    private static SheepColorSpawnRules.SheepColorSpawnConfiguration getSheepColorConfiguration(Holder<Biome> biome) {
        if (biome.is(BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS)) {
            return WARM_SPAWN_CONFIGURATION;
        } else {
            return biome.is(BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS) ? COLD_SPAWN_CONFIGURATION : TEMPERATE_SPAWN_CONFIGURATION;
        }
    }

    private static SheepColorSpawnRules.SheepColorProvider weighted(WeightedList<SheepColorSpawnRules.SheepColorProvider> colors) {
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("List must be non-empty");
        } else {
            return random -> colors.getRandomOrThrow(random).get(random);
        }
    }

    private static SheepColorSpawnRules.SheepColorProvider single(DyeColor color) {
        return random -> color;
    }

    private static WeightedList.Builder<SheepColorSpawnRules.SheepColorProvider> builder() {
        return WeightedList.builder();
    }

    @FunctionalInterface
    interface SheepColorProvider {
        DyeColor get(RandomSource random);
    }

    record SheepColorSpawnConfiguration(SheepColorSpawnRules.SheepColorProvider colors) {
    }
}
