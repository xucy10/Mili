package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class FireChargeItem extends Item implements ProjectileItem {
    public FireChargeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        boolean flag = false;
        if (!CampfireBlock.canLight(blockState) && !CandleBlock.canLight(blockState) && !CandleCakeBlock.canLight(blockState)) {
            clickedPos = clickedPos.relative(context.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(level, clickedPos, context.getHorizontalDirection())) {
                // CraftBukkit start - fire BlockIgniteEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, clickedPos, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, context.getPlayer()).isCancelled()) {
                    if (!context.getPlayer().getAbilities().instabuild) {
                        context.getItemInHand().shrink(1);
                    }
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                this.playSound(level, clickedPos);
                level.setBlockAndUpdate(clickedPos, BaseFireBlock.getState(level, clickedPos));
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, clickedPos);
                flag = true;
            }
        } else {
            // CraftBukkit start - fire BlockIgniteEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, clickedPos, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, context.getPlayer()).isCancelled()) {
                if (!context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.playSound(level, clickedPos);
            level.setBlockAndUpdate(clickedPos, blockState.setValue(BlockStateProperties.LIT, true));
            level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, clickedPos);
            flag = true;
        }

        if (flag) {
            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.FAIL;
        }
    }

    private void playSound(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        RandomSource random = level.getRandom();
        double d = random.triangle((double)direction.getStepX(), 0.11485000000000001);
        double d1 = random.triangle((double)direction.getStepY(), 0.11485000000000001);
        double d2 = random.triangle((double)direction.getStepZ(), 0.11485000000000001);
        Vec3 vec3 = new Vec3(d, d1, d2);
        SmallFireball smallFireball = new SmallFireball(level, pos.x(), pos.y(), pos.z(), vec3.normalize());
        smallFireball.setItem(stack);
        return smallFireball;
    }

    @Override
    public void shoot(Projectile projectile, double x, double y, double z, float velocity, float inaccuracy) {
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction((source, direction) -> DispenserBlock.getDispensePosition(source, 1.0, Vec3.ZERO))
            .uncertainty(6.6666665F)
            .power(1.0F)
            .overrideDispenseEvent(LevelEvent.SOUND_BLAZE_FIREBALL)
            .build();
    }
}
