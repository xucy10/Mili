package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BrushItem extends Item {
    public static final int ANIMATION_DURATION = 10;
    private static final int USE_DURATION = 200;

    public BrushItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && this.calculateHitResult(player).getType() == HitResult.Type.BLOCK) {
            player.startUsingItem(context.getHand());
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 200;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration >= 0 && livingEntity instanceof Player player) {
            HitResult hitResult = this.calculateHitResult(player);
            if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
                int i = this.getUseDuration(stack, livingEntity) - remainingUseDuration + 1;
                boolean flag = i % 10 == 5;
                if (flag) {
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockState blockState = level.getBlockState(blockPos);
                    HumanoidArm humanoidArm = livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND
                        ? player.getMainArm()
                        : player.getMainArm().getOpposite();
                    if (blockState.shouldSpawnTerrainParticles() && blockState.getRenderShape() != RenderShape.INVISIBLE) {
                        this.spawnDustParticles(level, blockHitResult, blockState, livingEntity.getViewVector(0.0F), humanoidArm);
                    }

                    SoundEvent brushSound;
                    if (blockState.getBlock() instanceof BrushableBlock brushableBlock) {
                        brushSound = brushableBlock.getBrushSound();
                    } else {
                        brushSound = SoundEvents.BRUSH_GENERIC;
                    }

                    level.playSound(player, blockPos, brushSound, SoundSource.BLOCKS);
                    if (level instanceof ServerLevel serverLevel && level.getBlockEntity(blockPos) instanceof BrushableBlockEntity brushableBlockEntity) {
                        boolean flag1 = brushableBlockEntity.brush(level.getGameTime(), serverLevel, player, blockHitResult.getDirection(), stack);
                        if (flag1) {
                            EquipmentSlot equipmentSlot = stack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND))
                                ? EquipmentSlot.OFFHAND
                                : EquipmentSlot.MAINHAND;
                            stack.hurtAndBreak(1, player, equipmentSlot);
                        }
                    }
                }
            } else {
                livingEntity.releaseUsingItem();
            }
        } else {
            livingEntity.releaseUsingItem();
        }
    }

    private HitResult calculateHitResult(Player player) {
        return ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange());
    }

    private void spawnDustParticles(Level level, BlockHitResult hitResult, BlockState state, Vec3 pos, HumanoidArm arm) {
        double d = 3.0;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        int randomInt = level.getRandom().nextInt(7, 12);
        BlockParticleOption blockParticleOption = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction direction = hitResult.getDirection();
        BrushItem.DustParticlesDelta dustParticlesDelta = BrushItem.DustParticlesDelta.fromDirection(pos, direction);
        Vec3 location = hitResult.getLocation();

        for (int i1 = 0; i1 < randomInt; i1++) {
            level.addParticle(
                blockParticleOption,
                location.x - (direction == Direction.WEST ? 1.0E-6F : 0.0F),
                location.y,
                location.z - (direction == Direction.NORTH ? 1.0E-6F : 0.0F),
                dustParticlesDelta.xd() * i * 3.0 * level.getRandom().nextDouble(),
                0.0,
                dustParticlesDelta.zd() * i * 3.0 * level.getRandom().nextDouble()
            );
        }
    }

    record DustParticlesDelta(double xd, double yd, double zd) {
        private static final double ALONG_SIDE_DELTA = 1.0;
        private static final double OUT_FROM_SIDE_DELTA = 0.1;

        public static BrushItem.DustParticlesDelta fromDirection(Vec3 pos, Direction direction) {
            double d = 0.0;

            return switch (direction) {
                case DOWN, UP -> new BrushItem.DustParticlesDelta(pos.z(), 0.0, -pos.x());
                case NORTH -> new BrushItem.DustParticlesDelta(1.0, 0.0, -0.1);
                case SOUTH -> new BrushItem.DustParticlesDelta(-1.0, 0.0, 0.1);
                case WEST -> new BrushItem.DustParticlesDelta(-0.1, 0.0, -1.0);
                case EAST -> new BrushItem.DustParticlesDelta(0.1, 0.0, 1.0);
            };
        }
    }
}
