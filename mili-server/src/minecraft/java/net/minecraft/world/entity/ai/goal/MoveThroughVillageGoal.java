package net.minecraft.world.entity.ai.goal;

import com.google.common.collect.Lists;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveThroughVillageGoal extends Goal {
    protected final PathfinderMob mob;
    private final double speedModifier;
    private @Nullable Path path;
    private BlockPos poiPos;
    private final boolean onlyAtNight;
    private final List<BlockPos> visited = Lists.newArrayList();
    private final int distanceToPoi;
    private final BooleanSupplier canDealWithDoors;

    public MoveThroughVillageGoal(PathfinderMob mob, double speedModifier, boolean onlyAtNight, int distanceToPoi, BooleanSupplier canDealWithDoors) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.onlyAtNight = onlyAtNight;
        this.distanceToPoi = distanceToPoi;
        this.canDealWithDoors = canDealWithDoors;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        if (!GoalUtils.hasGroundPathNavigation(mob)) {
            throw new IllegalArgumentException("Unsupported mob for MoveThroughVillageGoal");
        }
    }

    @Override
    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation(this.mob)) {
            return false;
        } else {
            this.updateVisited();
            if (this.onlyAtNight && this.mob.level().isBrightOutside()) {
                return false;
            } else {
                ServerLevel serverLevel = (ServerLevel)this.mob.level();
                BlockPos blockPos = this.mob.blockPosition();
                if (!serverLevel.isCloseToVillage(blockPos, 6)) {
                    return false;
                } else {
                    Vec3 pos = LandRandomPos.getPos(
                        this.mob,
                        15,
                        7,
                        blockPos2 -> {
                            if (!serverLevel.isVillage(blockPos2)) {
                                return Double.NEGATIVE_INFINITY;
                            } else {
                                Optional<BlockPos> optional1 = serverLevel.getPoiManager()
                                    .find(holder -> holder.is(PoiTypeTags.VILLAGE), this::hasNotVisited, blockPos2, 10, PoiManager.Occupancy.IS_OCCUPIED);
                                return optional1.<Double>map(blockPos3 -> -blockPos3.distSqr(blockPos)).orElse(Double.NEGATIVE_INFINITY);
                            }
                        }
                    );
                    if (pos == null) {
                        return false;
                    } else {
                        Optional<BlockPos> optional = serverLevel.getPoiManager()
                            .find(holder -> holder.is(PoiTypeTags.VILLAGE), this::hasNotVisited, BlockPos.containing(pos), 10, PoiManager.Occupancy.IS_OCCUPIED);
                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            this.poiPos = optional.get().immutable();
                            PathNavigation navigation = this.mob.getNavigation();
                            navigation.setCanOpenDoors(this.canDealWithDoors.getAsBoolean());
                            this.path = navigation.createPath(this.poiPos, 0);
                            navigation.setCanOpenDoors(true);
                            if (this.path == null) {
                                Vec3 posTowards = DefaultRandomPos.getPosTowards(this.mob, 10, 7, Vec3.atBottomCenterOf(this.poiPos), (float) (Math.PI / 2));
                                if (posTowards == null) {
                                    return false;
                                }

                                navigation.setCanOpenDoors(this.canDealWithDoors.getAsBoolean());
                                this.path = this.mob.getNavigation().createPath(posTowards.x, posTowards.y, posTowards.z, 0);
                                navigation.setCanOpenDoors(true);
                                if (this.path == null) {
                                    return false;
                                }
                            }

                            for (int i = 0; i < this.path.getNodeCount(); i++) {
                                Node node = this.path.getNode(i);
                                BlockPos blockPos1 = new BlockPos(node.x, node.y + 1, node.z);
                                if (DoorBlock.isWoodenDoor(this.mob.level(), blockPos1)) {
                                    this.path = this.mob.getNavigation().createPath(node.x, node.y, node.z, 0);
                                    break;
                                }
                            }

                            return this.path != null;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && !this.poiPos.closerToCenterThan(this.mob.position(), this.mob.getBbWidth() + this.distanceToPoi);
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.path, this.speedModifier);
    }

    @Override
    public void stop() {
        if (this.mob.getNavigation().isDone() || this.poiPos.closerToCenterThan(this.mob.position(), this.distanceToPoi)) {
            this.visited.add(this.poiPos);
        }
    }

    private boolean hasNotVisited(BlockPos pos) {
        for (BlockPos blockPos : this.visited) {
            if (Objects.equals(pos, blockPos)) {
                return false;
            }
        }

        return true;
    }

    private void updateVisited() {
        if (this.visited.size() > 15) {
            this.visited.remove(0);
        }
    }
}
