package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class OceanRuinPieces {
    static final StructureProcessor WARM_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
        Blocks.SAND, Blocks.SUSPICIOUS_SAND, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY
    );
    static final StructureProcessor COLD_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
        Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY
    );
    private static final Identifier[] WARM_RUINS = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/warm_1"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_2"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_3"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_4"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_5"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_6"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_7"),
        Identifier.withDefaultNamespace("underwater_ruin/warm_8")
    };
    private static final Identifier[] RUINS_BRICK = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/brick_1"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_2"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_3"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_4"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_5"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_6"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_7"),
        Identifier.withDefaultNamespace("underwater_ruin/brick_8")
    };
    private static final Identifier[] RUINS_CRACKED = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/cracked_1"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_2"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_3"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_4"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_5"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_6"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_7"),
        Identifier.withDefaultNamespace("underwater_ruin/cracked_8")
    };
    private static final Identifier[] RUINS_MOSSY = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/mossy_1"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_2"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_3"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_4"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_5"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_6"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_7"),
        Identifier.withDefaultNamespace("underwater_ruin/mossy_8")
    };
    private static final Identifier[] BIG_RUINS_BRICK = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/big_brick_1"),
        Identifier.withDefaultNamespace("underwater_ruin/big_brick_2"),
        Identifier.withDefaultNamespace("underwater_ruin/big_brick_3"),
        Identifier.withDefaultNamespace("underwater_ruin/big_brick_8")
    };
    private static final Identifier[] BIG_RUINS_MOSSY = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/big_mossy_1"),
        Identifier.withDefaultNamespace("underwater_ruin/big_mossy_2"),
        Identifier.withDefaultNamespace("underwater_ruin/big_mossy_3"),
        Identifier.withDefaultNamespace("underwater_ruin/big_mossy_8")
    };
    private static final Identifier[] BIG_RUINS_CRACKED = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/big_cracked_1"),
        Identifier.withDefaultNamespace("underwater_ruin/big_cracked_2"),
        Identifier.withDefaultNamespace("underwater_ruin/big_cracked_3"),
        Identifier.withDefaultNamespace("underwater_ruin/big_cracked_8")
    };
    private static final Identifier[] BIG_WARM_RUINS = new Identifier[]{
        Identifier.withDefaultNamespace("underwater_ruin/big_warm_4"),
        Identifier.withDefaultNamespace("underwater_ruin/big_warm_5"),
        Identifier.withDefaultNamespace("underwater_ruin/big_warm_6"),
        Identifier.withDefaultNamespace("underwater_ruin/big_warm_7")
    };

    private static StructureProcessor archyRuleProcessor(Block block, Block suspiciousBlock, ResourceKey<LootTable> lootTable) {
        return new CappedProcessor(
            new RuleProcessor(
                List.of(
                    new ProcessorRule(
                        new BlockMatchTest(block),
                        AlwaysTrueTest.INSTANCE,
                        PosAlwaysTrueTest.INSTANCE,
                        suspiciousBlock.defaultBlockState(),
                        new AppendLoot(lootTable)
                    )
                )
            ),
            ConstantInt.of(5)
        );
    }

    private static Identifier getSmallWarmRuin(RandomSource random) {
        return Util.getRandom(WARM_RUINS, random);
    }

    private static Identifier getBigWarmRuin(RandomSource random) {
        return Util.getRandom(BIG_WARM_RUINS, random);
    }

    public static void addPieces(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        StructurePieceAccessor structurePieceAccessor,
        RandomSource random,
        OceanRuinStructure structure
    ) {
        boolean flag = random.nextFloat() <= structure.largeProbability;
        float f = flag ? 0.9F : 0.8F;
        addPiece(structureTemplateManager, pos, rotation, structurePieceAccessor, random, structure, flag, f);
        if (flag && random.nextFloat() <= structure.clusterProbability) {
            addClusterRuins(structureTemplateManager, random, rotation, pos, structure, structurePieceAccessor);
        }
    }

    private static void addClusterRuins(
        StructureTemplateManager structureTemplateManager,
        RandomSource random,
        Rotation rotation,
        BlockPos pos,
        OceanRuinStructure structure,
        StructurePieceAccessor structurePieceAccessor
    ) {
        BlockPos blockPos = new BlockPos(pos.getX(), 90, pos.getZ());
        BlockPos blockPos1 = StructureTemplate.transform(new BlockPos(15, 0, 15), Mirror.NONE, rotation, BlockPos.ZERO).offset(blockPos);
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos1);
        BlockPos blockPos2 = new BlockPos(Math.min(blockPos.getX(), blockPos1.getX()), blockPos.getY(), Math.min(blockPos.getZ(), blockPos1.getZ()));
        List<BlockPos> list = allPositions(random, blockPos2);
        int randomInt = Mth.nextInt(random, 4, 8);

        for (int i = 0; i < randomInt; i++) {
            if (!list.isEmpty()) {
                int randomInt1 = random.nextInt(list.size());
                BlockPos blockPos3 = list.remove(randomInt1);
                Rotation random1 = Rotation.getRandom(random);
                BlockPos blockPos4 = StructureTemplate.transform(new BlockPos(5, 0, 6), Mirror.NONE, random1, BlockPos.ZERO).offset(blockPos3);
                BoundingBox boundingBox1 = BoundingBox.fromCorners(blockPos3, blockPos4);
                if (!boundingBox1.intersects(boundingBox)) {
                    addPiece(structureTemplateManager, blockPos3, random1, structurePieceAccessor, random, structure, false, 0.8F);
                }
            }
        }
    }

    private static List<BlockPos> allPositions(RandomSource random, BlockPos pos) {
        List<BlockPos> list = Lists.newArrayList();
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, -16 + Mth.nextInt(random, 4, 8)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 6)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 3, 8)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 8)));
        return list;
    }

    private static void addPiece(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        StructurePieceAccessor structurePieceAccessor,
        RandomSource random,
        OceanRuinStructure structure,
        boolean isLarge,
        float integrity
    ) {
        switch (structure.biomeTemp) {
            case WARM:
            default:
                Identifier identifier = isLarge ? getBigWarmRuin(random) : getSmallWarmRuin(random);
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, identifier, pos, rotation, integrity, structure.biomeTemp, isLarge)
                );
                break;
            case COLD:
                Identifier[] identifiers = isLarge ? BIG_RUINS_BRICK : RUINS_BRICK;
                Identifier[] identifiers1 = isLarge ? BIG_RUINS_CRACKED : RUINS_CRACKED;
                Identifier[] identifiers2 = isLarge ? BIG_RUINS_MOSSY : RUINS_MOSSY;
                int randomInt = random.nextInt(identifiers.length);
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, identifiers[randomInt], pos, rotation, integrity, structure.biomeTemp, isLarge)
                );
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, identifiers1[randomInt], pos, rotation, 0.7F, structure.biomeTemp, isLarge)
                );
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, identifiers2[randomInt], pos, rotation, 0.5F, structure.biomeTemp, isLarge)
                );
        }
    }

    public static class OceanRuinPiece extends TemplateStructurePiece {
        private final OceanRuinStructure.Type biomeType;
        private final float integrity;
        private final boolean isLarge;

        public OceanRuinPiece(
            StructureTemplateManager structureTemplateManager,
            Identifier location,
            BlockPos pos,
            Rotation rotation,
            float integrity,
            OceanRuinStructure.Type biomeType,
            boolean isLarge
        ) {
            super(StructurePieceType.OCEAN_RUIN, 0, structureTemplateManager, location, location.toString(), makeSettings(rotation, integrity, biomeType), pos);
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private OceanRuinPiece(
            StructureTemplateManager structureTemplateManager,
            CompoundTag genDepth,
            Rotation rotation,
            float integrity,
            OceanRuinStructure.Type biomeType,
            boolean isLarge
        ) {
            super(StructurePieceType.OCEAN_RUIN, genDepth, structureTemplateManager, identifier -> makeSettings(rotation, integrity, biomeType));
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, float integrity, OceanRuinStructure.Type structureType) {
            StructureProcessor structureProcessor = structureType == OceanRuinStructure.Type.COLD
                ? OceanRuinPieces.COLD_SUSPICIOUS_BLOCK_PROCESSOR
                : OceanRuinPieces.WARM_SUSPICIOUS_BLOCK_PROCESSOR;
            return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .addProcessor(new BlockRotProcessor(integrity))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .addProcessor(structureProcessor);
        }

        public static OceanRuinPieces.OceanRuinPiece create(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            Rotation rotation = tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow();
            float floatOr = tag.getFloatOr("Integrity", 0.0F);
            OceanRuinStructure.Type type = tag.read("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC).orElseThrow();
            boolean booleanOr = tag.getBooleanOr("IsLarge", false);
            return new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, tag, rotation, floatOr, type, booleanOr);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
            tag.putFloat("Integrity", this.integrity);
            tag.store("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC, this.biomeType);
            tag.putBoolean("IsLarge", this.isLarge);
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            if ("chest".equals(name)) {
                // CraftBukkit start - transform block to ensure loot table is accessible
                // level.setBlock(
                //     pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, level.getFluidState(pos).is(FluidTags.WATER)), Block.UPDATE_CLIENTS
                // );
                // BlockEntity blockEntity = level.getBlockEntity(pos);
                // if (blockEntity instanceof ChestBlockEntity) {
                //     ((ChestBlockEntity)blockEntity)
                //         .setLootTable(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL, random.nextLong());
                // }
                org.bukkit.craftbukkit.block.CraftChest craftChest = (org.bukkit.craftbukkit.block.CraftChest) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, level.getFluidState(pos).is(FluidTags.WATER)), null);
                craftChest.setSeed(random.nextLong());
                craftChest.setLootTable(org.bukkit.craftbukkit.CraftLootTable.minecraftToBukkit(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL));
                this.placeCraftBlockEntity(level, pos, craftChest, Block.UPDATE_CLIENTS);
                // CraftBukkit end
            } else if ("drowned".equals(name)) {
                Drowned drowned = EntityType.DROWNED.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (drowned != null) {
                    drowned.setPersistenceRequired();
                    drowned.snapTo(pos, 0.0F, 0.0F);
                    drowned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(drowned);
                    if (pos.getY() > level.getSeaLevel()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
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
            int height = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.templatePosition.getX(), this.templatePosition.getZ());
            this.templatePosition = new BlockPos(this.templatePosition.getX(), height, this.templatePosition.getZ());
            BlockPos blockPos = StructureTemplate.transform(
                    new BlockPos(this.template.getSize().getX() - 1, 0, this.template.getSize().getZ() - 1),
                    Mirror.NONE,
                    this.placeSettings.getRotation(),
                    BlockPos.ZERO
                )
                .offset(this.templatePosition);
            this.templatePosition = new BlockPos(
                this.templatePosition.getX(), this.getHeight(this.templatePosition, level, blockPos), this.templatePosition.getZ()
            );
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
        }

        private int getHeight(BlockPos templatePos, BlockGetter level, BlockPos pos) {
            int y = templatePos.getY();
            int i = 512;
            int i1 = y - 1;
            int i2 = 0;

            for (BlockPos blockPos : BlockPos.betweenClosed(templatePos, pos)) {
                int x = blockPos.getX();
                int z = blockPos.getZ();
                int i3 = templatePos.getY() - 1;
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, i3, z);
                BlockState blockState = level.getBlockState(mutableBlockPos);

                for (FluidState fluidState = level.getFluidState(mutableBlockPos);
                    (blockState.isAir() || fluidState.is(FluidTags.WATER) || blockState.is(BlockTags.ICE)) && i3 > level.getMinY() + 1;
                    fluidState = level.getFluidState(mutableBlockPos)
                ) {
                    mutableBlockPos.set(x, --i3, z);
                    blockState = level.getBlockState(mutableBlockPos);
                }

                i = Math.min(i, i3);
                if (i3 < i1 - 2) {
                    i2++;
                }
            }

            int abs = Math.abs(templatePos.getX() - pos.getX());
            if (i1 - i > 2 && i2 > abs - 2) {
                y = i + 1;
            }

            return y;
        }
    }
}
