package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPlatformFeature extends Feature<NoneFeatureConfiguration> {
    public EndPlatformFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        createEndPlatform(context.level(), context.origin(), false);
        return true;
    }

    public static void createEndPlatform(ServerLevelAccessor level, BlockPos pos, boolean dropBlocks) {
        // CraftBukkit start
        createEndPlatform(level, pos, dropBlocks, null);
    }

    public static void createEndPlatform(ServerLevelAccessor level, BlockPos pos, boolean dropBlocks, @javax.annotation.Nullable net.minecraft.world.entity.Entity entity) {
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(level);
        // CraftBukkit end
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        // Luminol start - tripwire behavior modifier
        java.util.List<BlockPos> blockList1 = new java.util.ArrayList<>();
        java.util.List<BlockPos> blockList2 = new java.util.ArrayList<>();
        boolean flag21 = me.earthme.luminol.config.modules.function.TripwireBehaviorConfig.behaviorMode == me.earthme.luminol.enums.EnumTripwireBehavior.VANILLA21;
        for (int i = -2; i <= 2; i++) {
            for (int i1 = -2; i1 <= 2; i1++) {
                for (int i2 = -1; i2 < 3; i2++) {
                    BlockPos blockPos = mutableBlockPos.set(pos).move(i1, i2, i);
                    Block block = i2 == -1 ? Blocks.OBSIDIAN : Blocks.AIR;
                    if (!blockList.getBlockState(blockPos).is(block)) { // CraftBukkit
                        if (dropBlocks) {
                            boolean flag = false;
                            if (me.earthme.luminol.config.modules.function.TripwireBehaviorConfig.enabled) {
                                switch (me.earthme.luminol.config.modules.function.TripwireBehaviorConfig.behaviorMode) {
                                    case me.earthme.luminol.enums.EnumTripwireBehavior.VANILLA20: {
                                        flag = true;
                                    }
                                    case me.earthme.luminol.enums.EnumTripwireBehavior.MIXED: {
                                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(blockPos);
                                        if (state.is(Blocks.TRIPWIRE)) {
                                            if (state.getValue(net.minecraft.world.level.block.TripWireBlock.DISARMED)) {
                                                flag = true;
                                                blockList2.add(blockPos.immutable());
                                            }
                                            if (!flag) {
                                                flag = checkString(blockList2, blockPos);
                                            }
                                        }
                                    }
                                    default: {} // Luminol - 1.21 & default Logic - default empty
                                }
                            }
                            if (flag) blockList1.add(blockPos.immutable());
                            else blockList.destroyBlock(blockPos, true, null); // CraftBukkit
                        }

                        blockList.setBlock(blockPos, block.defaultBlockState(), Block.UPDATE_ALL); // CraftBukkit
                    }
                }
            }
        }

        // CraftBukkit start
        // SPIGOT-7746: Entity will only be null during world generation, which is async, so just generate without event
        if (false) { // Folia - region threading
            org.bukkit.World bworld = level.getLevel().getWorld();
            org.bukkit.event.world.PortalCreateEvent portalEvent = new org.bukkit.event.world.PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) blockList.getSnapshotBlocks(), bworld, entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);
            level.getLevel().getCraftServer().getPluginManager().callEvent(portalEvent);
            if (portalEvent.isCancelled()) return;
        }

        if (flag21 || !me.earthme.luminol.config.modules.function.TripwireBehaviorConfig.enabled) {
            if (dropBlocks) {
                blockList.placeBlocks(state -> level.destroyBlock(state.getPosition(), true, null));
            } else {
                blockList.placeBlocks();
            }
        } else {
            if (dropBlocks) {
                blockList.getSnapshotBlocks().forEach((state) -> {
                                        level.destroyBlock(state.getPosition(), !blockList1.contains(state.getPosition()), null);
                });
                // Luminol - prevent tripwire dupe in end platform generate
            }
            blockList.placeBlocks();
        }
        // CraftBukkit end
    }

    private static boolean checkString(java.util.List<BlockPos> blockList, BlockPos blockPos) {
        for (BlockPos pos : blockList) {
            if (pos.getY() != blockPos.getY()) continue;
            if (pos.getX() == blockPos.getX() || pos.getZ() == blockPos.getZ()) return true;
        }
        return false;
    }
    // Luminol end - tripwire behavior modifier
}
