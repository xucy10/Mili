package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jspecify.annotations.Nullable;

public record BlockPredicate(
    Optional<HolderSet<Block>> blocks, Optional<StatePropertiesPredicate> properties, Optional<NbtPredicate> nbt, DataComponentMatchers components
) {
    public static final Codec<BlockPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("blocks").forGetter(BlockPredicate::blocks),
                StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(BlockPredicate::properties),
                NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(BlockPredicate::nbt),
                DataComponentMatchers.CODEC.forGetter(BlockPredicate::components)
            )
            .apply(instance, BlockPredicate::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPredicate> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(ByteBufCodecs.holderSet(Registries.BLOCK)),
        BlockPredicate::blocks,
        ByteBufCodecs.optional(StatePropertiesPredicate.STREAM_CODEC),
        BlockPredicate::properties,
        ByteBufCodecs.optional(NbtPredicate.STREAM_CODEC),
        BlockPredicate::nbt,
        DataComponentMatchers.STREAM_CODEC,
        BlockPredicate::components,
        BlockPredicate::new
    );

    public boolean matches(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        } else if (!this.matchesState(level.getBlockState(pos))) {
            return false;
        } else {
            if (this.nbt.isPresent() || !this.components.isEmpty()) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (this.nbt.isPresent() && !matchesBlockEntity(level, blockEntity, this.nbt.get())) {
                    return false;
                }

                if (!this.components.isEmpty() && !matchesComponents(blockEntity, this.components)) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean matches(BlockInWorld block) {
        return this.matchesState(block.getState()) && (!this.nbt.isPresent() || matchesBlockEntity(block.getLevel(), block.getEntity(), this.nbt.get()));
    }

    private boolean matchesState(BlockState state) {
        return (!this.blocks.isPresent() || state.is(this.blocks.get())) && (!this.properties.isPresent() || this.properties.get().matches(state));
    }

    private static boolean matchesBlockEntity(LevelReader level, @Nullable BlockEntity blockEntity, NbtPredicate nbtPredicate) {
        return blockEntity != null && nbtPredicate.matches(blockEntity.saveWithFullMetadata(level.registryAccess()));
    }

    private static boolean matchesComponents(@Nullable BlockEntity entity, DataComponentMatchers components) {
        return entity != null && components.test((DataComponentGetter)entity.collectComponents());
    }

    public boolean requiresNbt() {
        return this.nbt.isPresent();
    }

    public static class Builder {
        private Optional<HolderSet<Block>> blocks = Optional.empty();
        private Optional<StatePropertiesPredicate> properties = Optional.empty();
        private Optional<NbtPredicate> nbt = Optional.empty();
        private DataComponentMatchers components = DataComponentMatchers.ANY;

        private Builder() {
        }

        public static BlockPredicate.Builder block() {
            return new BlockPredicate.Builder();
        }

        public BlockPredicate.Builder of(HolderGetter<Block> blockRegistry, Block... blocks) {
            return this.of(blockRegistry, Arrays.asList(blocks));
        }

        public BlockPredicate.Builder of(HolderGetter<Block> blockRegistry, Collection<Block> blocks) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, blocks));
            return this;
        }

        public BlockPredicate.Builder of(HolderGetter<Block> blockRegistry, TagKey<Block> blockTag) {
            this.blocks = Optional.of(blockRegistry.getOrThrow(blockTag));
            return this;
        }

        public BlockPredicate.Builder hasNbt(CompoundTag nbt) {
            this.nbt = Optional.of(new NbtPredicate(nbt));
            return this;
        }

        public BlockPredicate.Builder setProperties(StatePropertiesPredicate.Builder properties) {
            this.properties = properties.build();
            return this;
        }

        public BlockPredicate.Builder components(DataComponentMatchers components) {
            this.components = components;
            return this;
        }

        public BlockPredicate build() {
            return new BlockPredicate(this.blocks, this.properties, this.nbt, this.components);
        }
    }
}
