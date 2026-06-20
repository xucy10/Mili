package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.mutable.MutableInt;

public class BellBlockEntity extends BlockEntity {
    private static final int DURATION = 50;
    private static final int GLOW_DURATION = 60;
    private static final int MIN_TICKS_BETWEEN_SEARCHES = 60;
    private static final int MAX_RESONATION_TICKS = 40;
    private static final int TICKS_BEFORE_RESONATION = 5;
    private static final int SEARCH_RADIUS = 48;
    private static final int HEAR_BELL_RADIUS = 32;
    private static final int HIGHLIGHT_RAIDERS_RADIUS = 48;
    private long lastRingTimestamp;
    public int ticks;
    public boolean shaking;
    public Direction clickDirection;
    private List<LivingEntity> nearbyEntities;
    public boolean resonating;
    public int resonationTicks;

    public BellBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BELL, pos, blockState);
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.updateEntities();
            this.resonationTicks = 0;
            this.clickDirection = Direction.from3DDataValue(type);
            this.ticks = 0;
            this.shaking = true;
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    private static void tick(Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity, BellBlockEntity.ResonationEndAction resonationEndAction) {
        if (blockEntity.shaking) {
            blockEntity.ticks++;
        }

        if (blockEntity.ticks >= 50) {
            blockEntity.shaking = false;
            // Paper start - Fix bell block entity memory leak
            if (!blockEntity.resonating) {
                blockEntity.nearbyEntities.clear();
            }
            // Paper end - Fix bell block entity memory leak
            blockEntity.ticks = 0;
        }

        if (blockEntity.ticks >= 5 && blockEntity.resonationTicks == 0 && areRaidersNearby(pos, blockEntity.nearbyEntities)) {
            blockEntity.resonating = true;
            level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (blockEntity.resonating) {
            if (blockEntity.resonationTicks < 40) {
                blockEntity.resonationTicks++;
            } else {
                resonationEndAction.run(level, pos, blockEntity.nearbyEntities);
                blockEntity.nearbyEntities.clear(); // Paper - Fix bell block entity memory leak
                blockEntity.resonating = false;
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity) {
        tick(level, pos, state, blockEntity, BellBlockEntity::showBellParticles);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity) {
        tick(level, pos, state, blockEntity, BellBlockEntity::makeRaidersGlow);
    }

    public void onHit(Direction direction) {
        BlockPos blockPos = this.getBlockPos();
        this.clickDirection = direction;
        if (this.shaking) {
            this.ticks = 0;
        } else {
            this.shaking = true;
        }

        this.level.blockEvent(blockPos, this.getBlockState().getBlock(), 1, direction.get3DDataValue());
    }

    private void updateEntities() {
        BlockPos blockPos = this.getBlockPos();
        if (this.level.getGameTime() > this.lastRingTimestamp + 60L || this.nearbyEntities == null) {
            this.lastRingTimestamp = this.level.getGameTime();
            AABB aabb = new AABB(blockPos).inflate(48.0);
            this.nearbyEntities = this.level.getEntitiesOfClass(LivingEntity.class, aabb);
        }

        if (!this.level.isClientSide()) {
            for (LivingEntity livingEntity : this.nearbyEntities) {
                if (livingEntity.isAlive() && !livingEntity.isRemoved() && blockPos.closerToCenterThan(livingEntity.position(), 32.0)) {
                    livingEntity.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, this.level.getGameTime());
                }
            }
        }

        this.nearbyEntities.removeIf(e -> !e.isAlive()); // Paper - Fix bell block entity memory leak
    }

    private static boolean areRaidersNearby(BlockPos pos, List<LivingEntity> raiders) {
        for (LivingEntity livingEntity : raiders) {
            if (livingEntity.isAlive()
                && !livingEntity.isRemoved()
                && pos.closerToCenterThan(livingEntity.position(), 32.0)
                && livingEntity.getType().is(EntityTypeTags.RAIDERS)) {
                return true;
            }
        }

        return false;
    }

    private static void makeRaidersGlow(Level level, BlockPos pos, List<LivingEntity> raiders) {
        // Paper start - call bell resonate event and bell reveal raider event
        final List<org.bukkit.entity.LivingEntity> inRangeRaiders = raiders.stream().filter(raider -> isRaiderWithinRange(pos, raider)).map(e -> (org.bukkit.entity.LivingEntity) e.getBukkitEntity()).toList();
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBellResonateEvent(level, pos, inRangeRaiders).forEach(e -> glow(e, pos));
        // Paper end - call bell resonate event and bell reveal raider event
    }

    private static void showBellParticles(Level level, BlockPos pos, List<LivingEntity> raiders) {
        MutableInt mutableInt = new MutableInt(16700985);
        int i = (int)raiders.stream().filter(raider -> pos.closerToCenterThan(raider.position(), 48.0)).count();
        raiders.stream()
            .filter(raider -> isRaiderWithinRange(pos, raider))
            .forEach(
                raider -> {
                    float f = 1.0F;
                    double squareRoot = Math.sqrt(
                        (raider.getX() - pos.getX()) * (raider.getX() - pos.getX()) + (raider.getZ() - pos.getZ()) * (raider.getZ() - pos.getZ())
                    );
                    double d = pos.getX() + 0.5F + 1.0 / squareRoot * (raider.getX() - pos.getX());
                    double d1 = pos.getZ() + 0.5F + 1.0 / squareRoot * (raider.getZ() - pos.getZ());
                    int i1 = Mth.clamp((i - 21) / -2, 3, 15);

                    for (int i2 = 0; i2 < i1; i2++) {
                        int i3 = mutableInt.addAndGet(5);
                        level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, i3), d, pos.getY() + 0.5F, d1, 0.0, 0.0, 0.0);
                    }
                }
            );
    }

    private static boolean isRaiderWithinRange(BlockPos pos, LivingEntity raider) {
        return raider.isAlive() && !raider.isRemoved() && pos.closerToCenterThan(raider.position(), 48.0) && raider.getType().is(EntityTypeTags.RAIDERS);
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Add BellRevealRaiderEvent
    private static void glow(LivingEntity entity) {
        // Paper start - Add BellRevealRaiderEvent
        glow(entity, null);
    }

    private static void glow(LivingEntity entity, @javax.annotation.Nullable BlockPos pos) {
        if (pos != null && !new io.papermc.paper.event.block.BellRevealRaiderEvent(org.bukkit.craftbukkit.block.CraftBlock.at(entity.level(), pos), (org.bukkit.entity.Raider) entity.getBukkitEntity()).callEvent())
            return;
        // Paper end - Add BellRevealRaiderEvent
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60));
    }

    @FunctionalInterface
    interface ResonationEndAction {
        void run(Level level, BlockPos pos, List<LivingEntity> raiders);
    }
}
