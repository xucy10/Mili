package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class EndCrystalItem extends Item {
    public EndCrystalItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (!blockState.is(Blocks.OBSIDIAN) && !blockState.is(Blocks.BEDROCK)) {
            return InteractionResult.FAIL;
        } else {
            BlockPos blockPos = clickedPos.above(); final BlockPos aboveBlockPos = blockPos; // Paper - OBFHELPER
            if (!level.isEmptyBlock(blockPos)) {
                return InteractionResult.FAIL;
            } else {
                double d = blockPos.getX();
                double d1 = blockPos.getY();
                double d2 = blockPos.getZ();
                List<Entity> entities = level.getEntities(null, new AABB(d, d1, d2, d + 1.0, d1 + 2.0, d2 + 1.0));
                if (!entities.isEmpty()) {
                    return InteractionResult.FAIL;
                } else {
                    if (level instanceof ServerLevel) {
                        EndCrystal endCrystal = new EndCrystal(level, d + 0.5, d1, d2 + 0.5);
                        endCrystal.setShowBottom(false);
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(context, endCrystal).isCancelled()) {
                            if (context.getPlayer() != null) context.getPlayer().containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                            return InteractionResult.FAIL;
                        }
                        // CraftBukkit end
                        level.addFreshEntity(endCrystal);
                        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, blockPos);
                        EndDragonFight dragonFight = ((ServerLevel)level).getDragonFight();
                        if (dragonFight != null) {
                            dragonFight.tryRespawn(aboveBlockPos); // Paper - Perf: Do crystal-portal proximity check before entity lookup
                        }
                    }

                    context.getItemInHand().shrink(1);
                    return InteractionResult.SUCCESS;
                }
            }
        }
    }
}
