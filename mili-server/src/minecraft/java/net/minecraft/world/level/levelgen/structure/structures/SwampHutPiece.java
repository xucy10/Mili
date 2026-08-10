package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class SwampHutPiece extends ScatteredFeaturePiece {
    private boolean spawnedWitch;
    private boolean spawnedCat;

    public SwampHutPiece(RandomSource random, int x, int z) {
        super(StructurePieceType.SWAMPLAND_HUT, x, 64, z, 7, 7, 9, getRandomHorizontalDirection(random));
    }

    public SwampHutPiece(CompoundTag tag) {
        super(StructurePieceType.SWAMPLAND_HUT, tag);
        this.spawnedWitch = tag.getBooleanOr("Witch", false);
        this.spawnedCat = tag.getBooleanOr("Cat", false);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("Witch", this.spawnedWitch);
        tag.putBoolean("Cat", this.spawnedCat);
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        if (this.updateAverageGroundHeight(level, box, 0)) {
            this.generateBox(level, box, 1, 1, 1, 5, 1, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 4, 2, 5, 4, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 1, 0, 4, 1, 0, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 2, 2, 3, 3, 2, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 3, 1, 3, 6, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 2, 3, 5, 3, 6, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 2, 7, 4, 3, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 0, 2, 1, 3, 2, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(level, box, 5, 0, 2, 5, 3, 2, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(level, box, 1, 0, 7, 1, 3, 7, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(level, box, 5, 0, 7, 5, 3, 7, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.placeBlock(level, Blocks.OAK_FENCE.defaultBlockState(), 2, 3, 2, box);
            this.placeBlock(level, Blocks.OAK_FENCE.defaultBlockState(), 3, 3, 7, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 1, 3, 4, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 3, 4, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 3, 5, box);
            this.placeBlock(level, Blocks.POTTED_RED_MUSHROOM.defaultBlockState(), 1, 3, 5, box);
            this.placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 3, 2, 6, box);
            this.placeBlock(level, Blocks.CAULDRON.defaultBlockState(), 4, 2, 6, box);
            this.placeBlock(level, Blocks.OAK_FENCE.defaultBlockState(), 1, 2, 1, box);
            this.placeBlock(level, Blocks.OAK_FENCE.defaultBlockState(), 5, 2, 1, box);
            BlockState blockState = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState blockState1 = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockState2 = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            BlockState blockState3 = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            this.generateBox(level, box, 0, 4, 1, 6, 4, 1, blockState, blockState, false);
            this.generateBox(level, box, 0, 4, 2, 0, 4, 7, blockState1, blockState1, false);
            this.generateBox(level, box, 6, 4, 2, 6, 4, 7, blockState2, blockState2, false);
            this.generateBox(level, box, 0, 4, 8, 6, 4, 8, blockState3, blockState3, false);
            this.placeBlock(level, blockState.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 0, 4, 1, box);
            this.placeBlock(level, blockState.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 6, 4, 1, box);
            this.placeBlock(level, blockState3.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 0, 4, 8, box);
            this.placeBlock(level, blockState3.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 6, 4, 8, box);

            for (int i = 2; i <= 7; i += 5) {
                for (int i1 = 1; i1 <= 5; i1 += 4) {
                    this.fillColumnDown(level, Blocks.OAK_LOG.defaultBlockState(), i1, -1, i, box);
                }
            }

            if (!this.spawnedWitch) {
                BlockPos worldPos = this.getWorldPos(2, 2, 5);
                if (box.isInside(worldPos)) {
                    this.spawnedWitch = true;
                    Witch witch = EntityType.WITCH.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                    if (witch != null) {
                        witch.setPersistenceRequired();
                        witch.snapTo(worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5, 0.0F, 0.0F);
                        witch.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPos), EntitySpawnReason.STRUCTURE, null);
                        level.addFreshEntityWithPassengers(witch, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit - add SpawnReason
                    }
                }
            }

            this.spawnCat(level, box);
        }
    }

    private void spawnCat(ServerLevelAccessor level, BoundingBox box) {
        if (!this.spawnedCat) {
            BlockPos worldPos = this.getWorldPos(2, 2, 5);
            if (box.isInside(worldPos)) {
                this.spawnedCat = true;
                Cat cat = EntityType.CAT.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (cat != null) {
                    cat.setPersistenceRequired();
                    cat.snapTo(worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5, 0.0F, 0.0F);
                    cat.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPos), EntitySpawnReason.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(cat, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit - add SpawnReason
                }
            }
        }
    }
}
