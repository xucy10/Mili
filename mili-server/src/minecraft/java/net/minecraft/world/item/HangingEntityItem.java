package net.minecraft.world.item;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class HangingEntityItem extends Item {
    private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos blockPos = clickedPos.relative(clickedFace);
        Player player = context.getPlayer();
        ItemStack itemInHand = context.getItemInHand();
        if (player != null && !this.mayPlace(player, clickedFace, itemInHand, blockPos)) {
            return InteractionResult.FAIL;
        } else {
            Level level = context.getLevel();
            HangingEntity hangingEntity;
            if (this.type == EntityType.PAINTING) {
                Optional<Painting> optional = Painting.create(level, blockPos, clickedFace);
                if (optional.isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                hangingEntity = optional.get();
            } else if (this.type == EntityType.ITEM_FRAME) {
                hangingEntity = new ItemFrame(level, blockPos, clickedFace);
            } else {
                if (this.type != EntityType.GLOW_ITEM_FRAME) {
                    return InteractionResult.SUCCESS;
                }

                hangingEntity = new GlowItemFrame(level, blockPos, clickedFace);
            }

            EntityType.<HangingEntity>createDefaultStackConfig(level, itemInHand, player).accept(hangingEntity);
            if (hangingEntity.survives()) {
                if (!level.isClientSide()) {
                    // CraftBukkit start - fire HangingPlaceEvent
                    org.bukkit.entity.Player bukkitPlayer = player == null ? null : (org.bukkit.entity.Player) player.getBukkitEntity();
                    org.bukkit.block.Block blockClicked = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos);
                    org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(clickedFace);
                    org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand());

                    org.bukkit.event.hanging.HangingPlaceEvent event = new org.bukkit.event.hanging.HangingPlaceEvent((org.bukkit.entity.Hanging) hangingEntity.getBukkitEntity(), bukkitPlayer, blockClicked, blockFace, hand, org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemInHand));
                    level.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        if (player != null) player.containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    hangingEntity.playPlacementSound();
                    level.gameEvent(player, GameEvent.ENTITY_PLACE, hangingEntity.position());
                    level.addFreshEntity(hangingEntity);
                }

                itemInHand.shrink(1);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(Player player, Direction direction, ItemStack hangingEntityStack, BlockPos pos) {
        return !direction.getAxis().isVertical() && player.mayUseItemAt(pos, direction, hangingEntityStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        if (this.type == EntityType.PAINTING && tooltipDisplay.shows(DataComponents.PAINTING_VARIANT)) {
            Holder<PaintingVariant> holder = stack.get(DataComponents.PAINTING_VARIANT);
            if (holder != null) {
                holder.value().title().ifPresent(tooltipAdder);
                holder.value().author().ifPresent(tooltipAdder);
                tooltipAdder.accept(Component.translatable("painting.dimensions", holder.value().width(), holder.value().height()));
            } else if (flag.isCreative()) {
                tooltipAdder.accept(TOOLTIP_RANDOM_VARIANT);
            }
        }
    }
}
