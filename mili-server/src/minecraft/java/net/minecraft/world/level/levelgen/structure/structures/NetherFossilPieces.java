package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class NetherFossilPieces {
    private static final Identifier[] FOSSILS = new Identifier[]{
        Identifier.withDefaultNamespace("nether_fossils/fossil_1"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_2"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_3"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_4"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_5"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_6"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_7"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_8"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_9"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_10"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_11"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_12"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_13"),
        Identifier.withDefaultNamespace("nether_fossils/fossil_14")
    };

    public static void addPieces(StructureTemplateManager structureManager, StructurePieceAccessor pieces, RandomSource random, BlockPos pos) {
        Rotation random1 = Rotation.getRandom(random);
        pieces.addPiece(new NetherFossilPieces.NetherFossilPiece(structureManager, Util.getRandom(FOSSILS, random), pos, random1));
    }

    public static class NetherFossilPiece extends TemplateStructurePiece {
        public NetherFossilPiece(StructureTemplateManager structureManager, Identifier location, BlockPos pos, Rotation rotation) {
            super(StructurePieceType.NETHER_FOSSIL, 0, structureManager, location, location.toString(), makeSettings(rotation), pos);
        }

        public NetherFossilPiece(StructureTemplateManager structureManager, CompoundTag tag) {
            super(StructurePieceType.NETHER_FOSSIL, tag, structureManager, identifier -> makeSettings(tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow()));
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            BoundingBox boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
            box.encapsulate(boundingBox);
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
            this.placeDriedGhast(level, random, boundingBox, box);
        }

        private void placeDriedGhast(WorldGenLevel level, RandomSource random, BoundingBox templateBox, BoundingBox box) {
            RandomSource randomSource = RandomSource.create(level.getSeed()).forkPositional().at(templateBox.getCenter());
            if (randomSource.nextFloat() < 0.5F) {
                int i = templateBox.minX() + randomSource.nextInt(templateBox.getXSpan());
                int minY = templateBox.minY();
                int i1 = templateBox.minZ() + randomSource.nextInt(templateBox.getZSpan());
                BlockPos blockPos = new BlockPos(i, minY, i1);
                if (level.getBlockState(blockPos).isAir() && box.isInside(blockPos)) {
                    level.setBlock(blockPos, Blocks.DRIED_GHAST.defaultBlockState().rotate(Rotation.getRandom(randomSource)), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
