package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableInt;

public class FossilFeature extends Feature<FossilFeatureConfiguration> {
    public FossilFeature(Codec<FossilFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FossilFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        Rotation random = Rotation.getRandom(randomSource);
        FossilFeatureConfiguration fossilFeatureConfiguration = context.config();
        int randomInt = randomSource.nextInt(fossilFeatureConfiguration.fossilStructures.size());
        StructureTemplateManager structureManager = worldGenLevel.getLevel().getServer().getStructureManager();
        StructureTemplate structureTemplate = structureManager.getOrCreate(fossilFeatureConfiguration.fossilStructures.get(randomInt));
        StructureTemplate structureTemplate1 = structureManager.getOrCreate(fossilFeatureConfiguration.overlayStructures.get(randomInt));
        ChunkPos chunkPos = new ChunkPos(blockPos);
        BoundingBox boundingBox = new BoundingBox(
            chunkPos.getMinBlockX() - 16,
            worldGenLevel.getMinY(),
            chunkPos.getMinBlockZ() - 16,
            chunkPos.getMaxBlockX() + 16,
            worldGenLevel.getMaxY(),
            chunkPos.getMaxBlockZ() + 16
        );
        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings().setRotation(random).setBoundingBox(boundingBox).setRandom(randomSource);
        Vec3i size = structureTemplate.getSize(random);
        BlockPos blockPos1 = blockPos.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        int y = blockPos.getY();

        for (int i = 0; i < size.getX(); i++) {
            for (int i1 = 0; i1 < size.getZ(); i1++) {
                y = Math.min(y, worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, blockPos1.getX() + i, blockPos1.getZ() + i1));
            }
        }

        int i = Math.max(y - 15 - randomSource.nextInt(10), worldGenLevel.getMinY() + 10);
        BlockPos zeroPositionWithTransform = structureTemplate.getZeroPositionWithTransform(blockPos1.atY(i), Mirror.NONE, random);
        if (countEmptyCorners(worldGenLevel, structureTemplate.getBoundingBox(structurePlaceSettings, zeroPositionWithTransform))
            > fossilFeatureConfiguration.maxEmptyCornersAllowed) {
            return false;
        } else {
            structurePlaceSettings.clearProcessors();
            fossilFeatureConfiguration.fossilProcessors.value().list().forEach(structurePlaceSettings::addProcessor);
            structureTemplate.placeInWorld(
                worldGenLevel, zeroPositionWithTransform, zeroPositionWithTransform, structurePlaceSettings, randomSource, Block.UPDATE_NONE
            );
            structurePlaceSettings.clearProcessors();
            fossilFeatureConfiguration.overlayProcessors.value().list().forEach(structurePlaceSettings::addProcessor);
            structureTemplate1.placeInWorld(
                worldGenLevel, zeroPositionWithTransform, zeroPositionWithTransform, structurePlaceSettings, randomSource, Block.UPDATE_NONE
            );
            return true;
        }
    }

    private static int countEmptyCorners(WorldGenLevel level, BoundingBox boundingBox) {
        MutableInt mutableInt = new MutableInt(0);
        boundingBox.forAllCorners(blockPos -> {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.isAir() || blockState.is(Blocks.LAVA) || blockState.is(Blocks.WATER)) {
                mutableInt.add(1);
            }
        });
        return mutableInt.intValue();
    }
}
