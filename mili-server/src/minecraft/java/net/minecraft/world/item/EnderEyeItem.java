package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EnderEyeItem extends Item {
    public EnderEyeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (!blockState.is(Blocks.END_PORTAL_FRAME) || blockState.getValue(EndPortalFrameBlock.HAS_EYE)) {
            return InteractionResult.PASS;
        } else if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            BlockState blockState1 = blockState.setValue(EndPortalFrameBlock.HAS_EYE, true);
            // Paper start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(context.getPlayer(), clickedPos, blockState1)) {
                return InteractionResult.PASS;
            }
            // Paper end
            Block.pushEntitiesUp(blockState, blockState1, level, clickedPos);
            level.setBlock(clickedPos, blockState1, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(clickedPos, Blocks.END_PORTAL_FRAME);
            context.getItemInHand().shrink(1);
            level.levelEvent(LevelEvent.END_PORTAL_FRAME_FILL, clickedPos, 0);
            BlockPattern.BlockPatternMatch blockPatternMatch = EndPortalFrameBlock.getOrCreatePortalShape().find(level, clickedPos);
            if (blockPatternMatch != null) {
                BlockPos blockPos = blockPatternMatch.getFrontTopLeft().offset(-3, 0, -3);

                for (int i = 0; i < 3; i++) {
                    for (int i1 = 0; i1 < 3; i1++) {
                        BlockPos blockPos1 = blockPos.offset(i, 0, i1);
                        level.destroyBlock(blockPos1, true, null);
                        level.setBlock(blockPos1, Blocks.END_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }

                // CraftBukkit start - Use relative location for far away sounds
                // level.globalLevelEvent(LevelEvent.SOUND_END_PORTAL_SPAWN, blockPos.offset(1, 0, 1), 0);
                int viewDistance = level.getCraftServer().getViewDistance() * 16;
                BlockPos soundPos = blockPos.offset(1, 0, 1);
                final net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level; // Paper - respect global sound events gamerule - ensured by isClientSide check above
                for (ServerPlayer player : serverLevel.getPlayersForGlobalSoundGamerule()) { // Paper - respect global sound events gamerule
                    double deltaX = soundPos.getX() - player.getX();
                    double deltaZ = soundPos.getZ() - player.getZ();
                    double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                    final double soundRadiusSquared = serverLevel.getGlobalSoundRangeSquared(config -> config.endPortalSoundRadius); // Paper - respect global sound events gamerule
                    if (!serverLevel.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.GLOBAL_SOUND_EVENTS) && distanceSquared > soundRadiusSquared) continue; // Spigot // Paper - respect global sound events gamerule
                    if (distanceSquared > viewDistance * viewDistance) {
                        double deltaLength = Math.sqrt(distanceSquared);
                        double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                        double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_END_PORTAL_SPAWN, new BlockPos((int) relativeX, (int) soundPos.getY(), (int) relativeZ), 0, true));
                    } else {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_END_PORTAL_SPAWN, soundPos, 0, true));
                    }
                }
                // CraftBukkit end
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 0;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult playerPovHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (playerPovHitResult.getType() == HitResult.Type.BLOCK && level.getBlockState(playerPovHitResult.getBlockPos()).is(Blocks.END_PORTAL_FRAME)) {
            return InteractionResult.PASS;
        } else {
            player.startUsingItem(hand);
            if (level instanceof ServerLevel serverLevel) {
                BlockPos blockPos = serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, player.blockPosition(), 100, false);
                if (blockPos == null) {
                    return InteractionResult.CONSUME;
                }

                EyeOfEnder eyeOfEnder = new EyeOfEnder(level, player.getX(), player.getY(0.5), player.getZ());
                eyeOfEnder.setItem(itemInHand);
                eyeOfEnder.signalTo(Vec3.atLowerCornerOf(blockPos));
                level.gameEvent(GameEvent.PROJECTILE_SHOOT, eyeOfEnder.position(), GameEvent.Context.of(player));
                // CraftBukkit start
                if (!level.addFreshEntity(eyeOfEnder)) {
                    return InteractionResult.FAIL;
                }
                // CraftBukkit end
                if (player instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.USED_ENDER_EYE.trigger(serverPlayer, blockPos);
                }

                float f = Mth.lerp(level.random.nextFloat(), 0.33F, 0.5F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 1.0F, f);
                itemInHand.consume(1, player);
                player.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResult.SUCCESS_SERVER;
        }
    }
}
