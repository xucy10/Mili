package net.minecraft.world.level.portal;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

public class PortalForcer {
    public static final int TICKET_RADIUS = 3;
    private static final int NETHER_PORTAL_RADIUS = 16;
    private static final int OVERWORLD_PORTAL_RADIUS = 128;
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_BOX = 3;
    private static final int FRAME_HEIGHT_START = -1;
    private static final int FRAME_HEIGHT_END = 4;
    private static final int FRAME_WIDTH_START = -1;
    private static final int FRAME_WIDTH_END = 3;
    private static final int FRAME_BOX_START = -1;
    private static final int FRAME_BOX_END = 2;
    private static final int NOTHING_FOUND = -1;
    private final ServerLevel level;

    public PortalForcer(ServerLevel level) {
        this.level = level;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public Optional<BlockPos> findClosestPortalPosition(BlockPos exitPos, boolean isNether, WorldBorder worldBorder) {
        // CraftBukkit start
        return this.findClosestPortalPosition(exitPos, worldBorder, isNether ? 16 : 128); // Search Radius
    }

    public Optional<BlockPos> findClosestPortalPosition(BlockPos exitPos, WorldBorder worldBorder, int i) {
        PoiManager poiManager = this.level.getPoiManager();
        // int i = isNether ? 16 : 128;
        // CraftBukkit end
        // Paper start - optimise portals
        java.util.List<PoiRecord> records = new java.util.ArrayList<>();
        io.papermc.paper.util.PoiAccess.findClosestPoiDataRecords(
            poiManager,
            type -> type.is(PoiTypes.NETHER_PORTAL),
            (BlockPos pos) -> {
                net.minecraft.world.level.chunk.ChunkAccess lowest = this.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.EMPTY);
                if (!lowest.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)
                    && (lowest.getBelowZeroRetrogen() == null || !lowest.getBelowZeroRetrogen().targetStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN))) {
                    // why would we generate the chunk?
                    return false;
                }
                if (!worldBorder.isWithinBounds(pos) || (this.level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER && this.level.paperConfig().environment.netherCeilingVoidDamageHeight.test(v -> pos.getY() >= v))) { // Paper - Configurable nether ceiling damage
                    return false;
                }
                return lowest.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS);
            },
            exitPos, i, Double.MAX_VALUE, PoiManager.Occupancy.ANY, true, records
        );

        // this gets us most of the way there, but we bias towards lower y values.
        BlockPos lowestPos = null;
        for (PoiRecord record : records) {
            if (lowestPos == null) {
                lowestPos = record.getPos();
            } else if (lowestPos.getY() > record.getPos().getY()) {
                lowestPos = record.getPos();
            }
        }
        // now we're done
        return Optional.ofNullable(lowestPos);
        // Paper end - optimise portals
    }

    public Optional<BlockUtil.FoundRectangle> createPortal(BlockPos pos, Direction.Axis axis) {
        // CraftBukkit start
        return this.createPortal(pos, axis, null, 16);
    }

    public Optional<BlockUtil.FoundRectangle> createPortal(BlockPos pos, Direction.Axis axis, @javax.annotation.Nullable net.minecraft.world.entity.Entity entity, int createRadius) {
        // CraftBukkit end
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        double d = -1.0;
        BlockPos blockPos = null;
        double d1 = -1.0;
        BlockPos blockPos1 = null;
        WorldBorder worldBorder = this.level.getWorldBorder();
        int min = Math.min(this.level.getMaxY(), this.level.getMinY() + this.level.getLogicalHeight() - 1);
        // Paper start - Configurable nether ceiling damage; make sure the max height doesn't exceed the void damage height
        if (this.level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER && this.level.paperConfig().environment.netherCeilingVoidDamageHeight.enabled()) {
            min = Math.min(min, this.level.paperConfig().environment.netherCeilingVoidDamageHeight.intValue() - 1);
        }
        // Paper end - Configurable nether ceiling damage
        int i = 1;
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (BlockPos.MutableBlockPos mutableBlockPos1 : BlockPos.spiralAround(pos, createRadius, Direction.EAST, Direction.SOUTH)) { // CraftBukkit
            int min1 = Math.min(min, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, mutableBlockPos1.getX(), mutableBlockPos1.getZ()));
            if (worldBorder.isWithinBounds(mutableBlockPos1) && worldBorder.isWithinBounds(mutableBlockPos1.move(direction, 1))) {
                mutableBlockPos1.move(direction.getOpposite(), 1);

                for (int i1 = min1; i1 >= this.level.getMinY(); i1--) {
                    mutableBlockPos1.setY(i1);
                    if (this.canPortalReplaceBlock(mutableBlockPos1)) {
                        int i2 = i1;

                        while (i1 > this.level.getMinY() && this.canPortalReplaceBlock(mutableBlockPos1.move(Direction.DOWN))) {
                            i1--;
                        }

                        if (i1 + 4 <= min) {
                            int i3 = i2 - i1;
                            if (i3 <= 0 || i3 >= 3) {
                                mutableBlockPos1.setY(i1);
                                if (this.canHostFrame(mutableBlockPos1, mutableBlockPos, direction, 0)) {
                                    double d2 = pos.distSqr(mutableBlockPos1);
                                    if (this.canHostFrame(mutableBlockPos1, mutableBlockPos, direction, -1)
                                        && this.canHostFrame(mutableBlockPos1, mutableBlockPos, direction, 1)
                                        && (d == -1.0 || d > d2)) {
                                        d = d2;
                                        blockPos = mutableBlockPos1.immutable();
                                    }

                                    if (d == -1.0 && (d1 == -1.0 || d1 > d2)) {
                                        d1 = d2;
                                        blockPos1 = mutableBlockPos1.immutable();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (d == -1.0 && d1 != -1.0) {
            blockPos = blockPos1;
            d = d1;
        }

        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(this.level); // CraftBukkit - Use BlockStateListPopulator
        if (d == -1.0) {
            int max = Math.max(this.level.getMinY() - -1, 70);
            int i4 = min - 9;
            if (i4 < max) {
                return Optional.empty();
            }

            blockPos = new BlockPos(pos.getX() - direction.getStepX() * 1, Mth.clamp(pos.getY(), max, i4), pos.getZ() - direction.getStepZ() * 1).immutable();
            blockPos = worldBorder.clampToBounds(blockPos);
            Direction clockWise = direction.getClockWise();

            for (int i1x = -1; i1x < 2; i1x++) {
                for (int i2 = 0; i2 < 2; i2++) {
                    for (int i3 = -1; i3 < 3; i3++) {
                        BlockState blockState = i3 < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        mutableBlockPos.setWithOffset(
                            blockPos, i2 * direction.getStepX() + i1x * clockWise.getStepX(), i3, i2 * direction.getStepZ() + i1x * clockWise.getStepZ()
                        );
                        blockList.setBlock(mutableBlockPos, blockState, Block.UPDATE_ALL); // CraftBukkit
                    }
                }
            }
        }

        for (int max = -1; max < 3; max++) {
            for (int i4 = -1; i4 < 4; i4++) {
                if (max == -1 || max == 2 || i4 == -1 || i4 == 3) {
                    mutableBlockPos.setWithOffset(blockPos, max * direction.getStepX(), i4, max * direction.getStepZ());
                    blockList.setBlock(mutableBlockPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL); // CraftBukkit
                }
            }
        }

        BlockState blockState1 = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);

        for (int i4x = 0; i4x < 2; i4x++) {
            for (int min1 = 0; min1 < 3; min1++) {
                mutableBlockPos.setWithOffset(blockPos, i4x * direction.getStepX(), min1, i4x * direction.getStepZ());
                blockList.setBlock(mutableBlockPos, blockState1, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); // CraftBukkit
            }
        }

        // CraftBukkit start
        org.bukkit.World bworld = this.level.getWorld();
        org.bukkit.event.world.PortalCreateEvent event = new org.bukkit.event.world.PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) blockList.getSnapshotBlocks(), bworld, (entity == null) ? null : entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.NETHER_PAIR);

        this.level.getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Optional.empty();
        }
        blockList.placeBlocks();
        // CraftBukkit end
        return Optional.of(new BlockUtil.FoundRectangle(blockPos.immutable(), 2, 3));
    }

    private boolean canPortalReplaceBlock(BlockPos.MutableBlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        return blockState.canBeReplaced() && blockState.getFluidState().isEmpty();
    }

    private boolean canHostFrame(BlockPos originalPos, BlockPos.MutableBlockPos offsetPos, Direction direction, int offsetScale) {
        Direction clockWise = direction.getClockWise();

        for (int i = -1; i < 3; i++) {
            for (int i1 = -1; i1 < 4; i1++) {
                offsetPos.setWithOffset(
                    originalPos,
                    direction.getStepX() * i + clockWise.getStepX() * offsetScale,
                    i1,
                    direction.getStepZ() * i + clockWise.getStepZ() * offsetScale
                );
                // Paper start - Protect Bedrock and End Portal/Frames from being destroyed
                if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits) {
                    if (!this.level.getBlockState(offsetPos).isDestroyable()) {
                        return false;
                    }
                }
                // Paper end - Protect Bedrock and End Portal/Frames from being destroyed
                if (i1 < 0 && !this.level.getBlockState(offsetPos).isSolid()) {
                    return false;
                }

                if (i1 >= 0 && !this.canPortalReplaceBlock(offsetPos)) {
                    return false;
                }
            }
        }

        return true;
    }
}
