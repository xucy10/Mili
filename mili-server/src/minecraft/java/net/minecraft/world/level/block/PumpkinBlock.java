package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;

public class PumpkinBlock extends Block {
    public static final MapCodec<PumpkinBlock> CODEC = simpleCodec(PumpkinBlock::new);

    @Override
    public MapCodec<PumpkinBlock> codec() {
        return CODEC;
    }

    protected PumpkinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.is(Items.SHEARS)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        } else if (level instanceof ServerLevel serverLevel) {
            Direction direction = hitResult.getDirection();
            Direction direction1 = direction.getAxis() == Direction.Axis.Y ? player.getDirection().getOpposite() : direction;
            java.util.List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>(); // Paper
            dropFromBlockInteractLootTable(
                serverLevel,
                BuiltInLootTables.CARVE_PUMPKIN,
                state,
                level.getBlockEntity(pos),
                stack,
                player,
                (serverLevel1, itemStack) -> {
                    drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack)); // Paper - move spawn logic after event call
                }
            );
            // Paper start - Add PlayerShearBlockEvent
            io.papermc.paper.event.block.PlayerShearBlockEvent event = new io.papermc.paper.event.block.PlayerShearBlockEvent(
                (org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), drops);
            if (!event.callEvent()) {
                return InteractionResult.PASS;
            }
            for (org.bukkit.inventory.ItemStack itemStack : event.getDrops()) {
                // moved from above
                ItemEntity itemEntity = new ItemEntity(
                    level, pos.getX() + 0.5 + direction1.getStepX() * 0.65, pos.getY() + 0.1, pos.getZ() + 0.5 + direction1.getStepZ() * 0.65, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack)
                );
                itemEntity.setDeltaMovement(
                    0.05 * direction1.getStepX() + level.random.nextDouble() * 0.02, 0.05, 0.05 * direction1.getStepZ() + level.random.nextDouble() * 0.02
                );
                level.addFreshEntity(itemEntity);
            }
            // Paper end - Add PlayerShearBlockEvent
            level.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, direction1), Block.UPDATE_ALL_IMMEDIATE);
            stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            level.gameEvent(player, GameEvent.SHEAR, pos);
            player.awardStat(Stats.ITEM_USED.get(Items.SHEARS));
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.SUCCESS;
        }
    }
}
