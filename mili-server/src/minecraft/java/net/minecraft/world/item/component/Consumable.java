package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public record Consumable(
    float consumeSeconds, ItemUseAnimation animation, Holder<SoundEvent> sound, boolean hasConsumeParticles, List<ConsumeEffect> onConsumeEffects
) {
    public static final float DEFAULT_CONSUME_SECONDS = 1.6F;
    private static final int CONSUME_EFFECTS_INTERVAL = 4;
    private static final float CONSUME_EFFECTS_START_FRACTION = 0.21875F;
    public static final Codec<Consumable> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("consume_seconds", 1.6F).forGetter(Consumable::consumeSeconds),
                ItemUseAnimation.CODEC.optionalFieldOf("animation", ItemUseAnimation.EAT).forGetter(Consumable::animation),
                SoundEvent.CODEC.optionalFieldOf("sound", SoundEvents.GENERIC_EAT).forGetter(Consumable::sound),
                Codec.BOOL.optionalFieldOf("has_consume_particles", true).forGetter(Consumable::hasConsumeParticles),
                ConsumeEffect.CODEC.listOf().optionalFieldOf("on_consume_effects", List.of()).forGetter(Consumable::onConsumeEffects)
            )
            .apply(instance, Consumable::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Consumable> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        Consumable::consumeSeconds,
        ItemUseAnimation.STREAM_CODEC,
        Consumable::animation,
        SoundEvent.STREAM_CODEC,
        Consumable::sound,
        ByteBufCodecs.BOOL,
        Consumable::hasConsumeParticles,
        ConsumeEffect.STREAM_CODEC.apply(ByteBufCodecs.list()),
        Consumable::onConsumeEffects,
        Consumable::new
    );

    public InteractionResult startConsuming(LivingEntity entity, ItemStack stack, InteractionHand hand) {
        if (!this.canConsume(entity, stack)) {
            return InteractionResult.FAIL;
        } else {
            boolean flag = this.consumeTicks() > 0;
            if (flag) {
                entity.startUsingItem(hand);
                return InteractionResult.CONSUME;
            } else {
                ItemStack itemStack = this.onConsume(entity.level(), entity, stack);
                return InteractionResult.CONSUME.heldItemTransformedTo(itemStack);
            }
        }
    }

    public ItemStack onConsume(Level level, LivingEntity entity, ItemStack stack) {
        RandomSource random = entity.getRandom();
        this.emitParticlesAndSounds(random, entity, stack, 16);
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }

        stack.getAllOfType(ConsumableListener.class).forEach(consumableListener -> consumableListener.onConsume(level, entity, stack, this));
        if (!level.isClientSide()) {
            // CraftBukkit start
            org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause;
            if (stack.is(net.minecraft.world.item.Items.MILK_BUCKET)) {
                cause = org.bukkit.event.entity.EntityPotionEffectEvent.Cause.MILK;
            } else if (stack.is(net.minecraft.world.item.Items.POTION)) {
                cause = org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_DRINK;
            } else {
                cause = org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD;
            }

            this.onConsumeEffects.forEach(consumeEffect -> consumeEffect.apply(level, stack, entity, cause));
            // CraftBukkit end
        }

        entity.gameEvent(this.animation == ItemUseAnimation.DRINK ? GameEvent.DRINK : GameEvent.EAT);
        stack.consume(1, entity);
        return stack;
    }

    // CraftBukkit start
    public void cancelUsingItem(ServerPlayer player, ItemStack stack) {
        final java.util.List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packets = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(); // Paper - properly resend entities - collect packets for bundle
        stack.getAllOfType(ConsumableListener.class).forEach(listener -> {
            listener.cancelUsingItem(player, stack, packets); // Paper - properly resend entities - collect packets for bundle
        });
        player.level().getServer().getPlayerList().sendActiveEffects(player, packets::add); // Paper - properly resend entities - collect packets for bundle
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(packets));
    }
    // CraftBukkit end

    public boolean canConsume(LivingEntity entity, ItemStack stack) {
        FoodProperties foodProperties = stack.get(DataComponents.FOOD);
        return !(foodProperties != null && entity instanceof Player player) || player.canEat(foodProperties.canAlwaysEat());
    }

    public int consumeTicks() {
        return (int)(this.consumeSeconds * 20.0F);
    }

    public void emitParticlesAndSounds(RandomSource random, LivingEntity entity, ItemStack stack, int amount) {
        float f = random.nextBoolean() ? 0.5F : 1.0F;
        float f1 = random.triangle(1.0F, 0.2F);
        float f2 = 0.5F;
        float f3 = Mth.randomBetween(random, 0.9F, 1.0F);
        float f4 = this.animation == ItemUseAnimation.DRINK ? 0.5F : f;
        float f5 = this.animation == ItemUseAnimation.DRINK ? f3 : f1;
        if (this.hasConsumeParticles) {
            entity.spawnItemParticles(stack, amount);
        }

        SoundEvent soundEvent = entity instanceof Consumable.OverrideConsumeSound overrideConsumeSound
            ? overrideConsumeSound.getConsumeSound(stack)
            : this.sound.value();
        entity.playSound(soundEvent, f4, f5);
    }

    public boolean shouldEmitParticlesAndSounds(int remainingUseDuration) {
        int i = this.consumeTicks() - remainingUseDuration;
        int i1 = (int)(this.consumeTicks() * 0.21875F);
        boolean flag = i > i1;
        return flag && remainingUseDuration % 4 == 0;
    }

    public static Consumable.Builder builder() {
        return new Consumable.Builder();
    }

    public static class Builder {
        private float consumeSeconds = 1.6F;
        private ItemUseAnimation animation = ItemUseAnimation.EAT;
        private Holder<SoundEvent> sound = SoundEvents.GENERIC_EAT;
        private boolean hasConsumeParticles = true;
        private final List<ConsumeEffect> onConsumeEffects = new ArrayList<>();

        Builder() {
        }

        public Consumable.Builder consumeSeconds(float consumeSeconds) {
            this.consumeSeconds = consumeSeconds;
            return this;
        }

        public Consumable.Builder animation(ItemUseAnimation animation) {
            this.animation = animation;
            return this;
        }

        public Consumable.Builder sound(Holder<SoundEvent> sound) {
            this.sound = sound;
            return this;
        }

        public Consumable.Builder soundAfterConsume(Holder<SoundEvent> consumptionSound) {
            return this.onConsume(new PlaySoundConsumeEffect(consumptionSound));
        }

        public Consumable.Builder hasConsumeParticles(boolean hasConsumeParticles) {
            this.hasConsumeParticles = hasConsumeParticles;
            return this;
        }

        public Consumable.Builder onConsume(ConsumeEffect effect) {
            this.onConsumeEffects.add(effect);
            return this;
        }

        public Consumable build() {
            return new Consumable(this.consumeSeconds, this.animation, this.sound, this.hasConsumeParticles, this.onConsumeEffects);
        }
    }

    public interface OverrideConsumeSound {
        SoundEvent getConsumeSound(ItemStack stack);
    }
}
