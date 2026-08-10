package net.minecraft.world.item;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class DebugStickItem extends Item {
    public DebugStickItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            this.handleInteraction(player, state, level, pos, false, stack);
        }

        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (!level.isClientSide() && player != null) {
            BlockPos clickedPos = context.getClickedPos();
            if (!this.handleInteraction(player, level.getBlockState(clickedPos), level, clickedPos, true, context.getItemInHand())) {
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.SUCCESS;
    }

    public boolean handleInteraction(Player player, BlockState stateClicked, LevelAccessor level, BlockPos pos, boolean shouldCycleState, ItemStack debugStack) {
        if (!player.canUseGameMasterBlocks() && !(player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.debugstick")) && !player.getBukkitEntity().hasPermission("minecraft.debugstick.always")) { // Spigot
            return false;
        } else {
            Holder<Block> blockHolder = stateClicked.getBlockHolder();
            StateDefinition<Block, BlockState> stateDefinition = blockHolder.value().getStateDefinition();
            Collection<Property<?>> properties = stateDefinition.getProperties();
            if (properties.isEmpty()) {
                message(player, Component.translatable(this.descriptionId + ".empty", blockHolder.getRegisteredName()));
                return false;
            } else {
                DebugStickState debugStickState = debugStack.get(DataComponents.DEBUG_STICK_STATE);
                if (debugStickState == null) {
                    return false;
                } else {
                    Property<?> property = debugStickState.properties().get(blockHolder);
                    if (shouldCycleState) {
                        if (property == null) {
                            property = properties.iterator().next();
                        }

                        BlockState blockState = cycleState(stateClicked, property, player.isSecondaryUseActive());
                        level.setBlock(pos, blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        message(player, Component.translatable(this.descriptionId + ".update", property.getName(), getNameHelper(blockState, property)));
                    } else {
                        property = getRelative(properties, property, player.isSecondaryUseActive());
                        debugStack.set(DataComponents.DEBUG_STICK_STATE, debugStickState.withProperty(blockHolder, property));
                        message(player, Component.translatable(this.descriptionId + ".select", property.getName(), getNameHelper(stateClicked, property)));
                    }

                    return true;
                }
            }
        }
    }

    private static <T extends Comparable<T>> BlockState cycleState(BlockState state, Property<T> property, boolean backwards) {
        return state.setValue(property, getRelative(property.getPossibleValues(), state.getValue(property), backwards));
    }

    private static <T> T getRelative(Iterable<T> allowedValues, @Nullable T currentValue, boolean backwards) {
        return backwards ? Util.findPreviousInIterable(allowedValues, currentValue) : Util.findNextInIterable(allowedValues, currentValue);
    }

    private static void message(Player player, Component message) {
        ((ServerPlayer)player).sendSystemMessage(message, true);
    }

    private static <T extends Comparable<T>> String getNameHelper(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
