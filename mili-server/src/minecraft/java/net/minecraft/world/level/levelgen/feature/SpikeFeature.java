package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.AABB;

public class SpikeFeature extends Feature<SpikeConfiguration> {
    public static final int NUMBER_OF_SPIKES = 10;
    private static final int SPIKE_DISTANCE = 42;
    private static final LoadingCache<Long, List<SpikeFeature.EndSpike>> SPIKE_CACHE = CacheBuilder.newBuilder()
        .expireAfterWrite(5L, TimeUnit.MINUTES)
        .build(new SpikeFeature.SpikeCacheLoader());

    public SpikeFeature(Codec<SpikeConfiguration> codec) {
        super(codec);
    }

    public static List<SpikeFeature.EndSpike> getSpikesForLevel(WorldGenLevel level) {
        RandomSource randomSource = RandomSource.create(level.getSeed());
        long l = randomSource.nextLong() & 65535L;
        return SPIKE_CACHE.getUnchecked(l);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpikeConfiguration> context) {
        SpikeConfiguration spikeConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        List<SpikeFeature.EndSpike> spikes = spikeConfiguration.getSpikes();
        if (spikes.isEmpty()) {
            spikes = getSpikesForLevel(worldGenLevel);
        }

        for (SpikeFeature.EndSpike endSpike : spikes) {
            if (endSpike.isCenterWithinChunk(blockPos)) {
                this.placeSpike(worldGenLevel, randomSource, spikeConfiguration, endSpike);
            }
        }

        return true;
    }

    private void placeSpike(ServerLevelAccessor level, RandomSource random, SpikeConfiguration config, SpikeFeature.EndSpike spike) {
        int radius = spike.getRadius();

        for (BlockPos blockPos : BlockPos.betweenClosed(
            new BlockPos(spike.getCenterX() - radius, level.getMinY(), spike.getCenterZ() - radius),
            new BlockPos(spike.getCenterX() + radius, spike.getHeight() + 10, spike.getCenterZ() + radius)
        )) {
            if (blockPos.distToLowCornerSqr(spike.getCenterX(), blockPos.getY(), spike.getCenterZ()) <= radius * radius + 1
                && blockPos.getY() < spike.getHeight()) {
                this.setBlock(level, blockPos, Blocks.OBSIDIAN.defaultBlockState());
            } else if (blockPos.getY() > 65) {
                this.setBlock(level, blockPos, Blocks.AIR.defaultBlockState());
            }
        }

        if (spike.isGuarded()) {
            int i = -2;
            int i1 = 2;
            int i2 = 3;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i3 = -2; i3 <= 2; i3++) {
                for (int i4 = -2; i4 <= 2; i4++) {
                    for (int i5 = 0; i5 <= 3; i5++) {
                        boolean flag = Mth.abs(i3) == 2;
                        boolean flag1 = Mth.abs(i4) == 2;
                        boolean flag2 = i5 == 3;
                        if (flag || flag1 || flag2) {
                            boolean flag3 = i3 == -2 || i3 == 2 || flag2;
                            boolean flag4 = i4 == -2 || i4 == 2 || flag2;
                            BlockState blockState = Blocks.IRON_BARS
                                .defaultBlockState()
                                .setValue(IronBarsBlock.NORTH, flag3 && i4 != -2)
                                .setValue(IronBarsBlock.SOUTH, flag3 && i4 != 2)
                                .setValue(IronBarsBlock.WEST, flag4 && i3 != -2)
                                .setValue(IronBarsBlock.EAST, flag4 && i3 != 2);
                            this.setBlock(level, mutableBlockPos.set(spike.getCenterX() + i3, spike.getHeight() + i5, spike.getCenterZ() + i4), blockState);
                        }
                    }
                }
            }
        }

        EndCrystal endCrystal = EntityType.END_CRYSTAL.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (endCrystal != null) {
            endCrystal.setBeamTarget(config.getCrystalBeamTarget());
            endCrystal.setInvulnerable(config.isCrystalInvulnerable());
            endCrystal.snapTo(spike.getCenterX() + 0.5, spike.getHeight() + 1, spike.getCenterZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            endCrystal.generatedByDragonFight = true; // Paper - Fix invulnerable end crystals
            level.addFreshEntity(endCrystal);
            BlockPos blockPosx = endCrystal.blockPosition();
            this.setBlock(level, blockPosx.below(), Blocks.BEDROCK.defaultBlockState());
            this.setBlock(level, blockPosx, FireBlock.getState(level, blockPosx));
        }
    }

    public static class EndSpike {
        public static final Codec<SpikeFeature.EndSpike> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("centerX").orElse(0).forGetter(endSpike -> endSpike.centerX),
                    Codec.INT.fieldOf("centerZ").orElse(0).forGetter(endSpike -> endSpike.centerZ),
                    Codec.INT.fieldOf("radius").orElse(0).forGetter(endSpike -> endSpike.radius),
                    Codec.INT.fieldOf("height").orElse(0).forGetter(endSpike -> endSpike.height),
                    Codec.BOOL.fieldOf("guarded").orElse(false).forGetter(endSpike -> endSpike.guarded)
                )
                .apply(instance, SpikeFeature.EndSpike::new)
        );
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final int height;
        private final boolean guarded;
        private final AABB topBoundingBox;

        public EndSpike(int centerX, int centerZ, int radius, int height, boolean guarded) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.height = height;
            this.guarded = guarded;
            this.topBoundingBox = new AABB(centerX - radius, DimensionType.MIN_Y, centerZ - radius, centerX + radius, DimensionType.MAX_Y, centerZ + radius);
        }

        public boolean isCenterWithinChunk(BlockPos pos) {
            return SectionPos.blockToSectionCoord(pos.getX()) == SectionPos.blockToSectionCoord(this.centerX)
                && SectionPos.blockToSectionCoord(pos.getZ()) == SectionPos.blockToSectionCoord(this.centerZ);
        }

        public int getCenterX() {
            return this.centerX;
        }

        public int getCenterZ() {
            return this.centerZ;
        }

        public int getRadius() {
            return this.radius;
        }

        public int getHeight() {
            return this.height;
        }

        public boolean isGuarded() {
            return this.guarded;
        }

        public AABB getTopBoundingBox() {
            return this.topBoundingBox;
        }
    }

    static class SpikeCacheLoader extends CacheLoader<Long, List<SpikeFeature.EndSpike>> {
        @Override
        public List<SpikeFeature.EndSpike> load(Long _long) {
            IntArrayList list = Util.toShuffledList(IntStream.range(0, 10), RandomSource.create(_long));
            List<SpikeFeature.EndSpike> list1 = Lists.newArrayList();

            for (int i = 0; i < 10; i++) {
                int floor = Mth.floor(42.0 * Math.cos(2.0 * (-Math.PI + (Math.PI / 10) * i)));
                int floor1 = Mth.floor(42.0 * Math.sin(2.0 * (-Math.PI + (Math.PI / 10) * i)));
                int i1 = list.get(i);
                int i2 = 2 + i1 / 3;
                int i3 = 76 + i1 * 3;
                boolean flag = i1 == 1 || i1 == 2;
                list1.add(new SpikeFeature.EndSpike(floor, floor1, i2, i3, flag));
            }

            return list1;
        }
    }
}
