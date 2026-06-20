package net.minecraft.world.level.levelgen.feature;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;

public class MonsterRoomFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityType<?>[] MOBS = new EntityType[]{EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SPIDER};
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    public MonsterRoomFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Predicate<BlockState> predicate = Feature.isReplaceable(BlockTags.FEATURES_CANNOT_REPLACE);
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        int i = 3;
        int i1 = randomSource.nextInt(2) + 2;
        int i2 = -i1 - 1;
        int i3 = i1 + 1;
        int i4 = -1;
        int i5 = 4;
        int i6 = randomSource.nextInt(2) + 2;
        int i7 = -i6 - 1;
        int i8 = i6 + 1;
        int i9 = 0;

        for (int i10 = i2; i10 <= i3; i10++) {
            for (int i11 = -1; i11 <= 4; i11++) {
                for (int i12 = i7; i12 <= i8; i12++) {
                    BlockPos blockPos1 = blockPos.offset(i10, i11, i12);
                    boolean isSolid = worldGenLevel.getBlockState(blockPos1).isSolid();
                    if (i11 == -1 && !isSolid) {
                        return false;
                    }

                    if (i11 == 4 && !isSolid) {
                        return false;
                    }

                    if ((i10 == i2 || i10 == i3 || i12 == i7 || i12 == i8)
                        && i11 == 0
                        && worldGenLevel.isEmptyBlock(blockPos1)
                        && worldGenLevel.isEmptyBlock(blockPos1.above())) {
                        i9++;
                    }
                }
            }
        }

        if (i9 >= 1 && i9 <= 5) {
            for (int i10 = i2; i10 <= i3; i10++) {
                for (int i11 = 3; i11 >= -1; i11--) {
                    for (int i12 = i7; i12 <= i8; i12++) {
                        BlockPos blockPos1x = blockPos.offset(i10, i11, i12);
                        BlockState blockState = worldGenLevel.getBlockState(blockPos1x);
                        if (i10 == i2 || i11 == -1 || i12 == i7 || i10 == i3 || i11 == 4 || i12 == i8) {
                            if (blockPos1x.getY() >= worldGenLevel.getMinY() && !worldGenLevel.getBlockState(blockPos1x.below()).isSolid()) {
                                worldGenLevel.setBlock(blockPos1x, AIR, Block.UPDATE_CLIENTS);
                            } else if (blockState.isSolid() && !blockState.is(Blocks.CHEST)) {
                                if (i11 == -1 && randomSource.nextInt(4) != 0) {
                                    this.safeSetBlock(worldGenLevel, blockPos1x, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), predicate);
                                } else {
                                    this.safeSetBlock(worldGenLevel, blockPos1x, Blocks.COBBLESTONE.defaultBlockState(), predicate);
                                }
                            }
                        } else if (!blockState.is(Blocks.CHEST) && !blockState.is(Blocks.SPAWNER)) {
                            this.safeSetBlock(worldGenLevel, blockPos1x, AIR, predicate);
                        }
                    }
                }
            }

            for (int i10 = 0; i10 < 2; i10++) {
                for (int i11 = 0; i11 < 3; i11++) {
                    int i12x = blockPos.getX() + randomSource.nextInt(i1 * 2 + 1) - i1;
                    int y = blockPos.getY();
                    int i13 = blockPos.getZ() + randomSource.nextInt(i6 * 2 + 1) - i6;
                    BlockPos blockPos2 = new BlockPos(i12x, y, i13);
                    if (worldGenLevel.isEmptyBlock(blockPos2)) {
                        int i14 = 0;

                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            if (worldGenLevel.getBlockState(blockPos2.relative(direction)).isSolid()) {
                                i14++;
                            }
                        }

                        if (i14 == 1) {
                            this.safeSetBlock(
                                worldGenLevel, blockPos2, StructurePiece.reorient(worldGenLevel, blockPos2, Blocks.CHEST.defaultBlockState()), predicate
                            );
                            RandomizableContainer.setBlockEntityLootTable(worldGenLevel, randomSource, blockPos2, BuiltInLootTables.SIMPLE_DUNGEON);
                            break;
                        }
                    }
                }
            }

            this.safeSetBlock(worldGenLevel, blockPos, Blocks.SPAWNER.defaultBlockState(), predicate);
            if (worldGenLevel.getBlockEntity(blockPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                spawnerBlockEntity.setEntityId(this.randomEntityId(randomSource), randomSource);
            } else {
                LOGGER.error("Failed to fetch mob spawner entity at ({}, {}, {})", blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }

            return true;
        } else {
            return false;
        }
    }

    private EntityType<?> randomEntityId(RandomSource random) {
        return Util.getRandom(MOBS, random);
    }
}
