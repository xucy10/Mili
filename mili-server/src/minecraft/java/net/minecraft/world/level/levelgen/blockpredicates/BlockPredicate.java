package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public interface BlockPredicate extends BiPredicate<WorldGenLevel, BlockPos> {
    Codec<BlockPredicate> CODEC = BuiltInRegistries.BLOCK_PREDICATE_TYPE.byNameCodec().dispatch(BlockPredicate::type, BlockPredicateType::codec);
    BlockPredicate ONLY_IN_AIR_PREDICATE = matchesBlocks(Blocks.AIR);
    BlockPredicate ONLY_IN_AIR_OR_WATER_PREDICATE = matchesBlocks(Blocks.AIR, Blocks.WATER);

    BlockPredicateType<?> type();

    static BlockPredicate allOf(List<BlockPredicate> predicates) {
        return new AllOfPredicate(predicates);
    }

    static BlockPredicate allOf(BlockPredicate... predicates) {
        return allOf(List.of(predicates));
    }

    static BlockPredicate allOf(BlockPredicate predicate1, BlockPredicate predicate2) {
        return allOf(List.of(predicate1, predicate2));
    }

    static BlockPredicate anyOf(List<BlockPredicate> predicates) {
        return new AnyOfPredicate(predicates);
    }

    static BlockPredicate anyOf(BlockPredicate... predicates) {
        return anyOf(List.of(predicates));
    }

    static BlockPredicate anyOf(BlockPredicate predicate1, BlockPredicate predicate2) {
        return anyOf(List.of(predicate1, predicate2));
    }

    static BlockPredicate matchesBlocks(Vec3i offset, List<Block> blocks) {
        return new MatchingBlocksPredicate(offset, HolderSet.direct(Block::builtInRegistryHolder, blocks));
    }

    static BlockPredicate matchesBlocks(List<Block> blocks) {
        return matchesBlocks(Vec3i.ZERO, blocks);
    }

    static BlockPredicate matchesBlocks(Vec3i offset, Block... blocks) {
        return matchesBlocks(offset, List.of(blocks));
    }

    static BlockPredicate matchesBlocks(Block... blocks) {
        return matchesBlocks(Vec3i.ZERO, blocks);
    }

    static BlockPredicate matchesTag(Vec3i offset, TagKey<Block> tag) {
        return new MatchingBlockTagPredicate(offset, tag);
    }

    static BlockPredicate matchesTag(TagKey<Block> tag) {
        return matchesTag(Vec3i.ZERO, tag);
    }

    static BlockPredicate matchesFluids(Vec3i offset, List<Fluid> fluids) {
        return new MatchingFluidsPredicate(offset, HolderSet.direct(Fluid::builtInRegistryHolder, fluids));
    }

    static BlockPredicate matchesFluids(Vec3i offset, Fluid... fluids) {
        return matchesFluids(offset, List.of(fluids));
    }

    static BlockPredicate matchesFluids(Fluid... fluids) {
        return matchesFluids(Vec3i.ZERO, fluids);
    }

    static BlockPredicate not(BlockPredicate predicate) {
        return new NotPredicate(predicate);
    }

    static BlockPredicate replaceable(Vec3i offset) {
        return new ReplaceablePredicate(offset);
    }

    static BlockPredicate replaceable() {
        return replaceable(Vec3i.ZERO);
    }

    static BlockPredicate wouldSurvive(BlockState state, Vec3i offset) {
        return new WouldSurvivePredicate(offset, state);
    }

    static BlockPredicate hasSturdyFace(Vec3i offset, Direction direction) {
        return new HasSturdyFacePredicate(offset, direction);
    }

    static BlockPredicate hasSturdyFace(Direction direction) {
        return hasSturdyFace(Vec3i.ZERO, direction);
    }

    static BlockPredicate solid(Vec3i offset) {
        return new SolidPredicate(offset);
    }

    static BlockPredicate solid() {
        return solid(Vec3i.ZERO);
    }

    static BlockPredicate noFluid() {
        return noFluid(Vec3i.ZERO);
    }

    static BlockPredicate noFluid(Vec3i offset) {
        return matchesFluids(offset, Fluids.EMPTY);
    }

    static BlockPredicate insideWorld(Vec3i offset) {
        return new InsideWorldBoundsPredicate(offset);
    }

    static BlockPredicate alwaysTrue() {
        return TrueBlockPredicate.INSTANCE;
    }

    static BlockPredicate unobstructed(Vec3i offset) {
        return new UnobstructedPredicate(offset);
    }

    static BlockPredicate unobstructed() {
        return unobstructed(Vec3i.ZERO);
    }
}
