package net.minecraft.server.level;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PlayerSpawnFinder {
    public static final EntityDimensions PLAYER_DIMENSIONS = EntityType.PLAYER.getDimensions(); // Folia - region threading - public
    private static final int ABSOLUTE_MAX_ATTEMPTS = 1024;
    private final ServerLevel level;
    private final BlockPos spawnSuggestion;
    private final int radius;
    private final int candidateCount;
    private final int coprime;
    private final int offset;
    private int nextCandidateIndex;
    private final CompletableFuture<Vec3> finishedFuture = new CompletableFuture<>();

    private PlayerSpawnFinder(ServerLevel level, BlockPos spawnSuggestion, int radius) {
        this.level = level;
        this.spawnSuggestion = spawnSuggestion;
        this.radius = radius;
        long l = radius * 2L + 1L;
        this.candidateCount = (int)Math.min(1024L, l * l);
        this.coprime = getCoprime(this.candidateCount);
        this.offset = RandomSource.create().nextInt(this.candidateCount);
    }

    public static CompletableFuture<Vec3> findSpawn(ServerLevel level, BlockPos pos) {
        if (level.dimensionType().hasSkyLight() && level.serverLevelData.getGameType() != GameType.ADVENTURE) { // CraftBukkit
            int max = Math.max(0, level.getGameRules().get(GameRules.RESPAWN_RADIUS));
            int floor = Mth.floor(level.getWorldBorder().getDistanceToBorder(pos.getX(), pos.getZ()));
            if (floor < max) {
                max = floor;
            }

            if (floor <= 1) {
                max = 1;
            }

            PlayerSpawnFinder playerSpawnFinder = new PlayerSpawnFinder(level, pos, max);
            playerSpawnFinder.scheduleNext();
            return playerSpawnFinder.finishedFuture;
        } else {
            return CompletableFuture.completedFuture(fixupSpawnHeight(level, pos));
        }
    }

    private void scheduleNext() {
        int i = this.nextCandidateIndex++;
        if (i < this.candidateCount) {
            int i1 = (this.offset + this.coprime * i) % this.candidateCount;
            int i2 = i1 % (this.radius * 2 + 1);
            int i3 = i1 / (this.radius * 2 + 1);
            int i4 = this.spawnSuggestion.getX() + i2 - this.radius;
            int i5 = this.spawnSuggestion.getZ() + i3 - this.radius;
            this.scheduleCandidate(
                i4,
                i5,
                i,
                () -> {
                    BlockPos overworldRespawnPos = getOverworldRespawnPos(this.level, i4, i5);
                    return overworldRespawnPos != null && noCollisionNoLiquid(this.level, overworldRespawnPos)
                        ? Optional.of(Vec3.atBottomCenterOf(overworldRespawnPos))
                        : Optional.empty();
                }
            );
        } else {
            this.scheduleCandidate(
                this.spawnSuggestion.getX(), this.spawnSuggestion.getZ(), i, () -> Optional.of(fixupSpawnHeight(this.level, this.spawnSuggestion))
            );
        }
    }

    private static Vec3 fixupSpawnHeight(CollisionGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        while (!noCollisionNoLiquid(level, mutableBlockPos) && mutableBlockPos.getY() < level.getMaxY()) {
            mutableBlockPos.move(Direction.UP);
        }

        mutableBlockPos.move(Direction.DOWN);

        while (noCollisionNoLiquid(level, mutableBlockPos) && mutableBlockPos.getY() > level.getMinY()) {
            mutableBlockPos.move(Direction.DOWN);
        }

        mutableBlockPos.move(Direction.UP);
        return Vec3.atBottomCenterOf(mutableBlockPos);
    }

    private static boolean noCollisionNoLiquid(CollisionGetter level, BlockPos pos) {
        return level.noCollision(null, PLAYER_DIMENSIONS.makeBoundingBox(pos.getBottomCenter()), true);
    }

    private static int getCoprime(int candidateCount) {
        return candidateCount <= 16 ? candidateCount - 1 : 17;
    }

    private void scheduleCandidate(int x, int z, int index, Supplier<Optional<Vec3>> calculator) {
        if (!this.finishedFuture.isDone()) {
            int sectionPosX = SectionPos.blockToSectionCoord(x);
            int sectionPosZ = SectionPos.blockToSectionCoord(z);

            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
                    sectionPosX, sectionPosZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false,
                    ca.spottedleaf.concurrentutil.util.Priority.HIGHER, (final net.minecraft.world.level.chunk.ChunkAccess object) -> {
                    Throwable throwable = null;
                    if (throwable == null) {
                        try {
                            Optional<Vec3> optional = calculator.get();
                            if (optional.isPresent()) {
                                this.finishedFuture.complete(optional.get());
                            } else {
                                this.scheduleNext();
                            }
                        } catch (Throwable var9) {
                            throwable = var9;
                        }
                    }

                    if (throwable != null) {
                        CrashReport crashReport = CrashReport.forThrowable(throwable, "Searching for spawn");
                        CrashReportCategory crashReportCategory = crashReport.addCategory("Spawn Lookup");
                        crashReportCategory.setDetail("Origin", this.spawnSuggestion::toString);
                        crashReportCategory.setDetail("Radius", () -> Integer.toString(this.radius));
                        crashReportCategory.setDetail("Candidate", () -> "[" + x + "," + z + "]");
                        crashReportCategory.setDetail("Progress", () -> index + " out of " + this.candidateCount);
                        this.finishedFuture.completeExceptionally(new ReportedException(crashReport));
                    }
                }); // Paper - rewrite chunk system // Folia - region threading
        }
    }

    protected static @Nullable BlockPos getOverworldRespawnPos(ServerLevel level, int x, int z) {
        boolean hasCeiling = level.dimensionType().hasCeiling();
        net.minecraft.world.level.chunk.ChunkAccess chunk = level.moonrise$getChunkTaskScheduler().syncLoadNonFull(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z), net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN); // Folia - region threading
        int i = hasCeiling ? level.getChunkSource().getGenerator().getSpawnHeight(level) : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        if (i < level.getMinY()) {
            return null;
        } else {
            int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            if (height <= i && height > chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15)) {
                return null;
            } else {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i1 = i + 1; i1 >= level.getMinY(); i1--) {
                    mutableBlockPos.set(x, i1, z);
                    BlockState blockState = chunk.getBlockState(mutableBlockPos); // Folia - region threading
                    if (!blockState.getFluidState().isEmpty()) {
                        break;
                    }

                    if (Block.isFaceFull(blockState.getCollisionShape(chunk, mutableBlockPos), Direction.UP)) { // Folia - region threading
                        return mutableBlockPos.above().immutable();
                    }
                }

                return null;
            }
        }
    }

    public static @Nullable BlockPos getSpawnPosInChunk(ServerLevel level, ChunkPos pos) {
        if (SharedConstants.debugVoidTerrain(pos)) {
            return null;
        } else {
            for (int blockX = pos.getMinBlockX(); blockX <= pos.getMaxBlockX(); blockX++) {
                for (int blockZ = pos.getMinBlockZ(); blockZ <= pos.getMaxBlockZ(); blockZ++) {
                    BlockPos overworldRespawnPos = getOverworldRespawnPos(level, blockX, blockZ);
                    if (overworldRespawnPos != null) {
                        return overworldRespawnPos;
                    }
                }
            }

            return null;
        }
    }
}
