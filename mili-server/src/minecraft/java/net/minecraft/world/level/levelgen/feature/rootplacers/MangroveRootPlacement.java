package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record MangroveRootPlacement(
    HolderSet<Block> canGrowThrough,
    HolderSet<Block> muddyRootsIn,
    BlockStateProvider muddyRootsProvider,
    int maxRootWidth,
    int maxRootLength,
    float randomSkewChance
) {
    public static final Codec<MangroveRootPlacement> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("can_grow_through").forGetter(placement -> placement.canGrowThrough),
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("muddy_roots_in").forGetter(placement -> placement.muddyRootsIn),
                BlockStateProvider.CODEC.fieldOf("muddy_roots_provider").forGetter(placement -> placement.muddyRootsProvider),
                Codec.intRange(1, 12).fieldOf("max_root_width").forGetter(placement -> placement.maxRootWidth),
                Codec.intRange(1, 64).fieldOf("max_root_length").forGetter(placement -> placement.maxRootLength),
                Codec.floatRange(0.0F, 1.0F).fieldOf("random_skew_chance").forGetter(placement -> placement.randomSkewChance)
            )
            .apply(instance, MangroveRootPlacement::new)
    );
}
