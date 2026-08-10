package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Sets;
import com.mojang.datafixers.kinds.OptionalBox.Mu;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class InteractWithDoor {
    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 3.0;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0;

    public static BehaviorControl<LivingEntity> create() {
        MutableObject<Node> mutableObject = new MutableObject<>();
        MutableInt mutableInt = new MutableInt(0);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.PATH),
                    instance.registered(MemoryModuleType.DOORS_TO_CLOSE),
                    instance.registered(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                )
                .apply(
                    instance,
                    (navigationPath, doorsToClose, nearestLivingEntities) -> (level, entity, gameTime) -> {
                        Path path = instance.get(navigationPath);
                        Optional<Set<GlobalPos>> optional = instance.tryGet(doorsToClose);
                        if (!path.notStarted() && !path.isDone()) {
                            if (Objects.equals(mutableObject.get(), path.getNextNode())) {
                                mutableInt.setValue(20);
                            } else if (mutableInt.decrementAndGet() > 0) {
                                return false;
                            }

                            mutableObject.setValue(path.getNextNode());
                            Node previousNode = path.getPreviousNode();
                            Node nextNode = path.getNextNode();
                            BlockPos blockPos = previousNode.asBlockPos();
                            BlockState blockState = level.getBlockState(blockPos);
                            if (blockState.is(BlockTags.MOB_INTERACTABLE_DOORS, state -> state.getBlock() instanceof DoorBlock)) {
                                DoorBlock doorBlock = (DoorBlock)blockState.getBlock();
                                if (!doorBlock.isOpen(blockState)) {
                                    // CraftBukkit start - entities opening doors
                                    org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(entity.level(), blockPos));
                                    if (!event.callEvent()) {
                                        return false;
                                    }
                                    // CraftBukkit end - entities opening doors
                                    doorBlock.setOpen(entity, level, blockState, blockPos, true);
                                }

                                optional = rememberDoorToClose(doorsToClose, optional, level, blockPos);
                            }

                            BlockPos blockPos1 = nextNode.asBlockPos();
                            BlockState blockState1 = level.getBlockState(blockPos1);
                            if (blockState1.is(BlockTags.MOB_INTERACTABLE_DOORS, state -> state.getBlock() instanceof DoorBlock)) {
                                DoorBlock doorBlock1 = (DoorBlock)blockState1.getBlock();
                                if (!doorBlock1.isOpen(blockState1)) {
                                    // CraftBukkit start - entities opening doors
                                    org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(entity.level(), blockPos1));
                                    if (!event.callEvent()) {
                                        return false;
                                    }
                                    // CraftBukkit end - entities opening doors
                                    doorBlock1.setOpen(entity, level, blockState1, blockPos1, true);
                                    optional = rememberDoorToClose(doorsToClose, optional, level, blockPos1);
                                }
                            }

                            optional.ifPresent(
                                doorPositions -> closeDoorsThatIHaveOpenedOrPassedThrough(
                                    level, entity, previousNode, nextNode, (Set<GlobalPos>)doorPositions, instance.tryGet(nearestLivingEntities)
                                )
                            );
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }

    public static void closeDoorsThatIHaveOpenedOrPassedThrough(
        ServerLevel level,
        LivingEntity entity,
        @Nullable Node previous,
        @Nullable Node next,
        Set<GlobalPos> doorPositions,
        Optional<List<LivingEntity>> nearestLivingEntities
    ) {
        Iterator<GlobalPos> iterator = doorPositions.iterator();

        while (iterator.hasNext()) {
            GlobalPos globalPos = iterator.next();
            BlockPos blockPos = globalPos.pos();
            if ((previous == null || !previous.asBlockPos().equals(blockPos)) && (next == null || !next.asBlockPos().equals(blockPos))) {
                if (isDoorTooFarAway(level, entity, globalPos)) {
                    iterator.remove();
                } else {
                    BlockState blockState = level.getBlockState(blockPos);
                    if (!blockState.is(BlockTags.MOB_INTERACTABLE_DOORS, state -> state.getBlock() instanceof DoorBlock)) {
                        iterator.remove();
                    } else {
                        DoorBlock doorBlock = (DoorBlock)blockState.getBlock();
                        if (!doorBlock.isOpen(blockState)) {
                            iterator.remove();
                        } else if (areOtherMobsComingThroughDoor(entity, blockPos, nearestLivingEntities)) {
                            iterator.remove();
                        } else {
                            doorBlock.setOpen(entity, level, blockState, blockPos, false);
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    private static boolean areOtherMobsComingThroughDoor(LivingEntity entity, BlockPos pos, Optional<List<LivingEntity>> nearestLivingEntities) {
        return !nearestLivingEntities.isEmpty()
            && nearestLivingEntities.get()
                .stream()
                .filter(nearEntity -> nearEntity.getType() == entity.getType())
                .filter(nearEntity -> pos.closerToCenterThan(nearEntity.position(), 2.0))
                .anyMatch(nearEntity -> isMobComingThroughDoor(nearEntity.getBrain(), pos));
    }

    private static boolean isMobComingThroughDoor(Brain<?> brain, BlockPos pos) {
        if (!brain.hasMemoryValue(MemoryModuleType.PATH)) {
            return false;
        } else {
            Path path = brain.getMemory(MemoryModuleType.PATH).get();
            if (path.isDone()) {
                return false;
            } else {
                Node previousNode = path.getPreviousNode();
                if (previousNode == null) {
                    return false;
                } else {
                    Node nextNode = path.getNextNode();
                    return pos.equals(previousNode.asBlockPos()) || pos.equals(nextNode.asBlockPos());
                }
            }
        }
    }

    private static boolean isDoorTooFarAway(ServerLevel level, LivingEntity entity, GlobalPos pos) {
        return pos.dimension() != level.dimension() || !pos.pos().closerToCenterThan(entity.position(), 3.0);
    }

    private static Optional<Set<GlobalPos>> rememberDoorToClose(
        MemoryAccessor<Mu, Set<GlobalPos>> doorsToClose, Optional<Set<GlobalPos>> doorPositions, ServerLevel level, BlockPos pos
    ) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        return Optional.of(doorPositions.<Set<GlobalPos>>map(set -> {
            set.add(globalPos);
            return set;
        }).orElseGet(() -> {
            Set<GlobalPos> set = Sets.newHashSet(globalPos);
            doorsToClose.set(set);
            return set;
        }));
    }
}
