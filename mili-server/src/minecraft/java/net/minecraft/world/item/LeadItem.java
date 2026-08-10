package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class LeadItem extends Item {
    public LeadItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (blockState.is(BlockTags.FENCES)) {
            Player player = context.getPlayer();
            if (!level.isClientSide() && player != null) {
                return bindPlayerMobs(player, level, clickedPos, context.getHand()); // CraftBukkit - Pass hand
            }
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult bindPlayerMobs(Player player, Level level, BlockPos pos, net.minecraft.world.InteractionHand interactionHand) { // CraftBukkit - Add InteractionHand
        LeashFenceKnotEntity leashFenceKnotEntity = null;
        List<Leashable> list = Leashable.leashableInArea(level, Vec3.atCenterOf(pos), leashable1 -> leashable1.getLeashHolder() == player);
        boolean flag = false;

        for (Leashable leashable : list) {
            if (leashFenceKnotEntity == null) {
                // CraftBukkit start - fire HangingPlaceEvent
                org.apache.commons.lang3.mutable.MutableBoolean created = new org.apache.commons.lang3.mutable.MutableBoolean(false);
                leashFenceKnotEntity = LeashFenceKnotEntity.getOrCreateKnot(level, pos, created);
                if (created.booleanValue()) {
                    org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(interactionHand);
                    org.bukkit.event.hanging.HangingPlaceEvent event = new org.bukkit.event.hanging.HangingPlaceEvent((org.bukkit.entity.Hanging) leashFenceKnotEntity.getBukkitEntity(), player != null ? (org.bukkit.entity.Player) player.getBukkitEntity() : null, org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), org.bukkit.block.BlockFace.SELF, hand);
                    level.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        leashFenceKnotEntity.discard(null);
                        return InteractionResult.PASS;
                    }
                }
                // CraftBukkit end
                leashFenceKnotEntity.playPlacementSound();
            }

            if (leashable.canHaveALeashAttachedTo(leashFenceKnotEntity) && org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerLeashEntityEvent(leashable, leashFenceKnotEntity, player, interactionHand)) { // Paper - leash event
                leashable.setLeashedTo(leashFenceKnotEntity, true);
                flag = true;
            }
        }

        if (flag) {
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
            return InteractionResult.SUCCESS_SERVER;
        } else {
            // CraftBukkit start - remove leash if we do not leash any entity because of the cancelled event
            if (leashFenceKnotEntity != null) {
                leashFenceKnotEntity.discard(null);
            }
            // CraftBukkit end
            return InteractionResult.PASS;
        }
    }

    // CraftBukkit start
    public static InteractionResult bindPlayerMobs(Player player, Level world, BlockPos pos) {
        return LeadItem.bindPlayerMobs(player, world, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    // CraftBukkit end
}
