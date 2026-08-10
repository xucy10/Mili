package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {
    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = Util.make(new float[13824], array -> {
        for (int i = 0; i < 24; i++) {
            for (int i1 = 0; i1 < 24; i1++) {
                for (int i2 = 0; i2 < 24; i2++) {
                    array[i * 24 * 24 + i1 * 24 + i2] = (float)computeBeardContribution(i1 - 12, i2 - 12, i - 12);
                }
            }
        }
    });
    public static final Beardifier EMPTY = new Beardifier(List.of(), List.of(), null);
    private final List<Beardifier.Rigid> pieces;
    private final List<JigsawJunction> junctions;
    private final @Nullable BoundingBox affectedBox;

    public static Beardifier forStructuresInChunk(StructureManager structureManager, ChunkPos chunkPos) {
        List<StructureStart> list = structureManager.startsForStructure(chunkPos, structure -> structure.terrainAdaptation() != TerrainAdjustment.NONE);
        if (list.isEmpty()) {
            return EMPTY;
        } else {
            int minBlockX = chunkPos.getMinBlockX();
            int minBlockZ = chunkPos.getMinBlockZ();
            List<Beardifier.Rigid> list1 = new ArrayList<>();
            List<JigsawJunction> list2 = new ArrayList<>();
            BoundingBox boundingBox = null;

            for (StructureStart structureStart : list) {
                TerrainAdjustment terrainAdjustment = structureStart.getStructure().terrainAdaptation();

                for (StructurePiece structurePiece : structureStart.getPieces()) {
                    if (structurePiece.isCloseToChunk(chunkPos, 12)) {
                        if (structurePiece instanceof PoolElementStructurePiece poolElementStructurePiece) {
                            StructureTemplatePool.Projection projection = poolElementStructurePiece.getElement().getProjection();
                            if (projection == StructureTemplatePool.Projection.RIGID) {
                                list1.add(
                                    new Beardifier.Rigid(
                                        poolElementStructurePiece.getBoundingBox(), terrainAdjustment, poolElementStructurePiece.getGroundLevelDelta()
                                    )
                                );
                                boundingBox = includeBoundingBox(boundingBox, structurePiece.getBoundingBox());
                            }

                            for (JigsawJunction jigsawJunction : poolElementStructurePiece.getJunctions()) {
                                int sourceX = jigsawJunction.getSourceX();
                                int sourceZ = jigsawJunction.getSourceZ();
                                if (sourceX > minBlockX - 12 && sourceZ > minBlockZ - 12 && sourceX < minBlockX + 15 + 12 && sourceZ < minBlockZ + 15 + 12) {
                                    list2.add(jigsawJunction);
                                    BoundingBox boundingBox1 = new BoundingBox(new BlockPos(sourceX, jigsawJunction.getSourceGroundY(), sourceZ));
                                    boundingBox = includeBoundingBox(boundingBox, boundingBox1);
                                }
                            }
                        } else {
                            list1.add(new Beardifier.Rigid(structurePiece.getBoundingBox(), terrainAdjustment, 0));
                            boundingBox = includeBoundingBox(boundingBox, structurePiece.getBoundingBox());
                        }
                    }
                }
            }

            if (boundingBox == null) {
                return EMPTY;
            } else {
                BoundingBox boundingBox2 = boundingBox.inflatedBy(24);
                return new Beardifier(List.copyOf(list1), List.copyOf(list2), boundingBox2);
            }
        }
    }

    private static BoundingBox includeBoundingBox(@Nullable BoundingBox current, BoundingBox toInclude) {
        return current == null ? toInclude : BoundingBox.encapsulating(current, toInclude);
    }

    @VisibleForTesting
    public Beardifier(List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions, @Nullable BoundingBox affectedBox) {
        this.pieces = pieces;
        this.junctions = junctions;
        this.affectedBox = affectedBox;
    }

    @Override
    public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
        if (this.affectedBox == null) {
            Arrays.fill(array, 0.0);
        } else {
            DensityFunctions.BeardifierOrMarker.super.fillArray(array, contextProvider);
        }
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        if (this.affectedBox == null) {
            return 0.0;
        } else {
            int i = context.blockX();
            int i1 = context.blockY();
            int i2 = context.blockZ();
            if (!this.affectedBox.isInside(i, i1, i2)) {
                return 0.0;
            } else {
                double d = 0.0;

                for (Beardifier.Rigid rigid : this.pieces) {
                    BoundingBox boundingBox = rigid.box();
                    int groundLevelDelta = rigid.groundLevelDelta();
                    int max = Math.max(0, Math.max(boundingBox.minX() - i, i - boundingBox.maxX()));
                    int max1 = Math.max(0, Math.max(boundingBox.minZ() - i2, i2 - boundingBox.maxZ()));
                    int i3 = boundingBox.minY() + groundLevelDelta;
                    int i4 = i1 - i3;

                    int i5 = switch (rigid.terrainAdjustment()) {
                        case NONE -> 0;
                        case BURY, BEARD_THIN -> i4;
                        case BEARD_BOX -> Math.max(0, Math.max(i3 - i1, i1 - boundingBox.maxY()));
                        case ENCAPSULATE -> Math.max(0, Math.max(boundingBox.minY() - i1, i1 - boundingBox.maxY()));
                    };

                    d += switch (rigid.terrainAdjustment()) {
                        case NONE -> 0.0;
                        case BURY -> getBuryContribution(max, i5 / 2.0, max1);
                        case BEARD_THIN, BEARD_BOX -> getBeardContribution(max, i5, max1, i4) * 0.8;
                        case ENCAPSULATE -> getBuryContribution(max / 2.0, i5 / 2.0, max1 / 2.0) * 0.8;
                    };
                }

                for (JigsawJunction jigsawJunction : this.junctions) {
                    int i6 = i - jigsawJunction.getSourceX();
                    int groundLevelDelta = i1 - jigsawJunction.getSourceGroundY();
                    int max = i2 - jigsawJunction.getSourceZ();
                    d += getBeardContribution(i6, groundLevelDelta, max, groundLevelDelta) * 0.4;
                }

                return d;
            }
        }
    }

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    private static double getBuryContribution(double x, double y, double z) {
        double len = Mth.length(x, y, z);
        return Mth.clampedMap(len, 0.0, 6.0, 1.0, 0.0);
    }

    private static double getBeardContribution(int x, int y, int z, int height) {
        int i = x + 12;
        int i1 = y + 12;
        int i2 = z + 12;
        if (isInKernelRange(i) && isInKernelRange(i1) && isInKernelRange(i2)) {
            double d = height + 0.5;
            double d1 = Mth.lengthSquared((double)x, d, (double)z);
            double d2 = -d * Mth.fastInvSqrt(d1 / 2.0) / 2.0;
            return d2 * BEARD_KERNEL[i2 * 24 * 24 + i * 24 + i1];
        } else {
            return 0.0;
        }
    }

    private static boolean isInKernelRange(int value) {
        return value >= 0 && value < 24;
    }

    private static double computeBeardContribution(int x, int y, int z) {
        return computeBeardContribution(x, y + 0.5, z);
    }

    private static double computeBeardContribution(int x, double y, int z) {
        double d = Mth.lengthSquared((double)x, y, (double)z);
        return Math.pow(Math.E, -d / 16.0);
    }

    @VisibleForTesting
    public record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
    }
}
