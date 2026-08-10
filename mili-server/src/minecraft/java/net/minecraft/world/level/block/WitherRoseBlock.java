package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WitherRoseBlock extends FlowerBlock {
    public static final MapCodec<WitherRoseBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects), propertiesCodec()).apply(instance, WitherRoseBlock::new)
    );

    @Override
    public MapCodec<WitherRoseBlock> codec() {
        return CODEC;
    }

    public WitherRoseBlock(Holder<MobEffect> effect, float seconds, BlockBehaviour.Properties properties) {
        this(makeEffectList(effect, seconds), properties);
    }

    public WitherRoseBlock(SuspiciousStewEffects suspiciousStewEffects, BlockBehaviour.Properties properties) {
        super(suspiciousStewEffects, properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.NETHERRACK) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        VoxelShape shape = this.getShape(state, level, pos, CollisionContext.empty());
        Vec3 center = shape.bounds().getCenter();
        double d = pos.getX() + center.x;
        double d1 = pos.getZ() + center.z;

        for (int i = 0; i < 3; i++) {
            if (random.nextBoolean()) {
                level.addParticle(
                    ParticleTypes.SMOKE, d + random.nextDouble() / 5.0, pos.getY() + (0.5 - random.nextDouble()), d1 + random.nextDouble() / 5.0, 0.0, 0.0, 0.0
                );
            }
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (level instanceof ServerLevel serverLevel
            && level.getDifficulty() != Difficulty.PEACEFUL
            && entity instanceof LivingEntity livingEntity
            && !livingEntity.isInvulnerableTo(serverLevel, level.damageSources().wither())) {
            livingEntity.addEffect(this.getBeeInteractionEffect(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WITHER_ROSE); // CraftBukkit
        }
    }

    @Override
    public MobEffectInstance getBeeInteractionEffect() {
        return new MobEffectInstance(MobEffects.WITHER, 40);
    }
}
