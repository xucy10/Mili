package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class PotionItem extends Item {
    public PotionItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack itemStack = super.getDefaultInstance();
        itemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return itemStack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack itemInHand = context.getItemInHand();
        PotionContents potionContents = itemInHand.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        BlockState blockState = level.getBlockState(clickedPos);
        if (context.getClickedFace() != Direction.DOWN && blockState.is(BlockTags.CONVERTABLE_TO_MUD) && potionContents.is(Potions.WATER)) {
            // Paper start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, clickedPos, Blocks.MUD.defaultBlockState())) {
                if (itemInHand.getCount() > 1 || player.hasInfiniteMaterials()) {
                    player.containerMenu.sendAllDataToRemote();
                } else {
                    player.containerMenu.forceHeldSlot(context.getHand());
                }
                return InteractionResult.PASS;
            }
            // Paper end
            level.playSound(null, clickedPos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(itemInHand, player, new ItemStack(Items.GLASS_BOTTLE)));
            if (!level.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel)level;

                for (int i = 0; i < 5; i++) {
                    serverLevel.sendParticles(
                        ParticleTypes.SPLASH,
                        clickedPos.getX() + level.random.nextDouble(),
                        clickedPos.getY() + 1,
                        clickedPos.getZ() + level.random.nextDouble(),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        1.0
                    );
                }
            }

            level.playSound(null, clickedPos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, clickedPos);
            level.setBlockAndUpdate(clickedPos, Blocks.MUD.defaultBlockState());
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        return potionContents != null ? potionContents.getName(this.descriptionId + ".effect.") : super.getName(stack);
    }
}
