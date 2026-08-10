package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

public class MobBucketItem extends BucketItem {
    private final EntityType<? extends Mob> type;
    private final SoundEvent emptySound;

    public MobBucketItem(EntityType<? extends Mob> type, Fluid content, SoundEvent emptySound, Item.Properties properties) {
        super(content, properties);
        this.type = type;
        this.emptySound = emptySound;
    }

    @Override
    public void checkExtraContent(@Nullable LivingEntity entity, Level level, ItemStack stack, BlockPos pos) {
        if (level instanceof ServerLevel) {
            this.spawn((ServerLevel)level, stack, pos);
            level.gameEvent(entity, GameEvent.ENTITY_PLACE, pos);
        }
    }

    @Override
    protected void playEmptySound(@Nullable LivingEntity entity, LevelAccessor level, BlockPos pos) {
        level.playSound(entity, pos, this.emptySound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void spawn(ServerLevel level, ItemStack bucketedMobStack, BlockPos pos) {
        Mob mob = this.type.create(level, EntityType.createDefaultStackConfig(level, bucketedMobStack, null), pos, EntitySpawnReason.BUCKET, true, false);
        if (mob instanceof Bucketable bucketable) {
            CustomData customData = bucketedMobStack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            bucketable.loadFromBucketTag(customData.copyTag());
            bucketable.setFromBucket(true);
        }

        if (mob != null) {
            level.addFreshEntityWithPassengers(mob, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUCKET); // Paper - Add SpawnReason
            mob.playAmbientSound();
        }
    }
}
