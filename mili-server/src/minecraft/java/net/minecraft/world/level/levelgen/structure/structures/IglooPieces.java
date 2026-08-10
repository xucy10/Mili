package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class IglooPieces {
    public static final int GENERATION_HEIGHT = 90;
    static final Identifier STRUCTURE_LOCATION_IGLOO = Identifier.withDefaultNamespace("igloo/top");
    private static final Identifier STRUCTURE_LOCATION_LADDER = Identifier.withDefaultNamespace("igloo/middle");
    private static final Identifier STRUCTURE_LOCATION_LABORATORY = Identifier.withDefaultNamespace("igloo/bottom");
    static final Map<Identifier, BlockPos> PIVOTS = ImmutableMap.of(
        STRUCTURE_LOCATION_IGLOO, new BlockPos(3, 5, 5), STRUCTURE_LOCATION_LADDER, new BlockPos(1, 3, 1), STRUCTURE_LOCATION_LABORATORY, new BlockPos(3, 6, 7)
    );
    static final Map<Identifier, BlockPos> OFFSETS = ImmutableMap.of(
        STRUCTURE_LOCATION_IGLOO, BlockPos.ZERO, STRUCTURE_LOCATION_LADDER, new BlockPos(2, -3, 4), STRUCTURE_LOCATION_LABORATORY, new BlockPos(0, -3, -2)
    );

    public static void addPieces(
        StructureTemplateManager structureTemplateManager, BlockPos startPos, Rotation rotation, StructurePieceAccessor pieces, RandomSource random
    ) {
        if (random.nextDouble() < 0.5) {
            int i = random.nextInt(8) + 4;
            pieces.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_LABORATORY, startPos, rotation, i * 3));

            for (int i1 = 0; i1 < i - 1; i1++) {
                pieces.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_LADDER, startPos, rotation, i1 * 3));
            }
        }

        pieces.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_IGLOO, startPos, rotation, 0));
    }

    public static class IglooPiece extends TemplateStructurePiece {
        public IglooPiece(StructureTemplateManager structureTemplateManager, Identifier location, BlockPos startPos, Rotation rotation, int down) {
            super(
                StructurePieceType.IGLOO,
                0,
                structureTemplateManager,
                location,
                location.toString(),
                makeSettings(rotation, location),
                makePosition(location, startPos, down)
            );
        }

        public IglooPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(
                StructurePieceType.IGLOO,
                tag,
                structureTemplateManager,
                identifier -> makeSettings(tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow(), identifier)
            );
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, Identifier location) {
            return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(IglooPieces.PIVOTS.get(location))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        }

        private static BlockPos makePosition(Identifier location, BlockPos pos, int down) {
            return pos.offset(IglooPieces.OFFSETS.get(location)).below(down);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            if ("chest".equals(name)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                // CraftBukkit start - ensure block transformation
                // BlockEntity blockEntity = level.getBlockEntity(pos.below());
                // if (blockEntity instanceof ChestBlockEntity) {
                //     ((ChestBlockEntity)blockEntity).setLootTable(BuiltInLootTables.IGLOO_CHEST, random.nextLong());
                // }
                this.setCraftLootTable(level, pos.below(), random, BuiltInLootTables.IGLOO_CHEST);
                // CraftBukkit end
            }
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
            Identifier identifier = Identifier.parse(this.templateName);
            StructurePlaceSettings structurePlaceSettings = makeSettings(this.placeSettings.getRotation(), identifier);
            BlockPos blockPos = IglooPieces.OFFSETS.get(identifier);
            BlockPos blockPos1 = this.templatePosition
                .offset(StructureTemplate.calculateRelativePosition(structurePlaceSettings, new BlockPos(3 - blockPos.getX(), 0, -blockPos.getZ())));
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockPos1.getX(), blockPos1.getZ());
            BlockPos blockPos2 = this.templatePosition;
            this.templatePosition = this.templatePosition.offset(0, height - 90 - 1, 0);
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
            if (identifier.equals(IglooPieces.STRUCTURE_LOCATION_IGLOO)) {
                BlockPos blockPos3 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(structurePlaceSettings, new BlockPos(3, 0, 5)));
                BlockState blockState = level.getBlockState(blockPos3.below());
                if (!blockState.isAir() && !blockState.is(Blocks.LADDER)) {
                    level.setBlock(blockPos3, Blocks.SNOW_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                }
            }

            this.templatePosition = blockPos2;
        }
    }
}
