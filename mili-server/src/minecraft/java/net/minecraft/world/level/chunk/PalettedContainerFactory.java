package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record PalettedContainerFactory(
    Strategy<BlockState> blockStatesStrategy,
    BlockState defaultBlockState,
    Codec<PalettedContainer<BlockState>> blockStatesContainerCodec,
    Strategy<Holder<Biome>> biomeStrategy,
    Holder<Biome> defaultBiome,
    Codec<PalettedContainerRO<Holder<Biome>>> biomeContainerCodec
    , Codec<PalettedContainer<Holder<Biome>>> biomeContainerRWCodec // Paper
) {
    public static PalettedContainerFactory create(RegistryAccess registryAccess) {
        Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState blockState = Blocks.AIR.defaultBlockState();
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Strategy<Holder<Biome>> strategy1 = Strategy.createForBiomes(registry.asHolderIdMap());
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        return new PalettedContainerFactory(
            strategy,
            blockState,
            PalettedContainer.codecRW(BlockState.CODEC, strategy, blockState, null), // Paper - Anti-Xray
            strategy1,
            orThrow,
            PalettedContainer.codecRO(registry.holderByNameCodec(), strategy1, orThrow)
            , PalettedContainer.codecRW(registry.holderByNameCodec(), strategy1, orThrow, null) // Paper // Anti-Xray
        );
    }

    public PalettedContainer<BlockState> createForBlockStates() {
        // Paper start - Anti-Xray
        return new PalettedContainer<>(this.defaultBlockState, this.blockStatesStrategy, null);
    }

    public PalettedContainer<BlockState> createForBlockStates(@javax.annotation.Nullable net.minecraft.world.level.Level level, net.minecraft.world.level.ChunkPos chunkPos, int chunkSectionY) {
        net.minecraft.world.level.block.state.BlockState[] states = null;
        if (level != null
            // This check is needed because of a circular reference in ChunkPacketBlockControllerAntiXray when creating an empty chunk section
            && level.chunkPacketBlockController != null) {
            states = level.chunkPacketBlockController.getPresetBlockStates(level, chunkPos, chunkSectionY);
        }
        return new PalettedContainer<>(this.defaultBlockState, this.blockStatesStrategy, states);
    }

    public PalettedContainer<Holder<Biome>> createForBiomes() {
        return new PalettedContainer<>(this.defaultBiome, this.biomeStrategy, null);
        // Paper end - Anti-Xray
    }
}
