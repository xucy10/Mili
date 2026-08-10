package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record BlocksAttacks(
    float blockDelaySeconds,
    float disableCooldownScale,
    List<BlocksAttacks.DamageReduction> damageReductions,
    BlocksAttacks.ItemDamageFunction itemDamage,
    Optional<TagKey<DamageType>> bypassedBy,
    Optional<Holder<SoundEvent>> blockSound,
    Optional<Holder<SoundEvent>> disableSound
) {
    public static final Codec<BlocksAttacks> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("block_delay_seconds", 0.0F).forGetter(BlocksAttacks::blockDelaySeconds),
                ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("disable_cooldown_scale", 1.0F).forGetter(BlocksAttacks::disableCooldownScale),
                BlocksAttacks.DamageReduction.CODEC
                    .listOf()
                    .optionalFieldOf("damage_reductions", List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)))
                    .forGetter(BlocksAttacks::damageReductions),
                BlocksAttacks.ItemDamageFunction.CODEC
                    .optionalFieldOf("item_damage", BlocksAttacks.ItemDamageFunction.DEFAULT)
                    .forGetter(BlocksAttacks::itemDamage),
                TagKey.hashedCodec(Registries.DAMAGE_TYPE).optionalFieldOf("bypassed_by").forGetter(BlocksAttacks::bypassedBy),
                SoundEvent.CODEC.optionalFieldOf("block_sound").forGetter(BlocksAttacks::blockSound),
                SoundEvent.CODEC.optionalFieldOf("disabled_sound").forGetter(BlocksAttacks::disableSound)
            )
            .apply(instance, BlocksAttacks::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlocksAttacks> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        BlocksAttacks::blockDelaySeconds,
        ByteBufCodecs.FLOAT,
        BlocksAttacks::disableCooldownScale,
        BlocksAttacks.DamageReduction.STREAM_CODEC.apply(ByteBufCodecs.list()),
        BlocksAttacks::damageReductions,
        BlocksAttacks.ItemDamageFunction.STREAM_CODEC,
        BlocksAttacks::itemDamage,
        TagKey.streamCodec(Registries.DAMAGE_TYPE).apply(ByteBufCodecs::optional),
        BlocksAttacks::bypassedBy,
        SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional),
        BlocksAttacks::blockSound,
        SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional),
        BlocksAttacks::disableSound,
        BlocksAttacks::new
    );

    public void onBlocked(ServerLevel level, LivingEntity entity) {
        this.blockSound
            .ifPresent(
                sound -> level.playSound(
                    null,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    (Holder<SoundEvent>)sound,
                    entity.getSoundSource(),
                    1.0F,
                    0.8F + level.random.nextFloat() * 0.4F
                )
            );
    }

    public void disable(ServerLevel level, LivingEntity entity, float duration, ItemStack stack, LivingEntity attacker) { // Paper
        int i = this.disableBlockingForTicks(duration);
        if (i > 0) {
            if (entity instanceof Player player) {
                // Paper start
                final io.papermc.paper.event.player.PlayerShieldDisableEvent shieldDisableEvent = new io.papermc.paper.event.player.PlayerShieldDisableEvent((org.bukkit.entity.Player) player.getBukkitEntity(), attacker.getBukkitEntity(), i);
                if (!shieldDisableEvent.callEvent()) return;
                player.getCooldowns().addCooldown(stack, shieldDisableEvent.getCooldown());
                // Paper end
            }

            entity.stopUsingItem();
            this.disableSound
                .ifPresent(
                    sound -> level.playSound(
                        null,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        (Holder<SoundEvent>)sound,
                        entity.getSoundSource(),
                        0.8F,
                        0.8F + level.random.nextFloat() * 0.4F
                    )
                );
        }
    }

    public void hurtBlockingItem(Level level, ItemStack stack, LivingEntity entity, InteractionHand hand, float damage) {
        if (entity instanceof Player player) {
            if (!level.isClientSide()) {
                player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }

            int i = this.itemDamage.apply(damage);
            if (i > 0) {
                stack.hurtAndBreak(i, entity, hand.asEquipmentSlot());
            }
        }
    }

    private int disableBlockingForTicks(float duration) {
        float f = duration * this.disableCooldownScale;
        return f > 0.0F ? Math.round(f * 20.0F) : 0;
    }

    public int blockDelayTicks() {
        return Math.round(this.blockDelaySeconds * 20.0F);
    }

    public float resolveBlockedDamage(DamageSource damageSource, float damageAmount, double horizontalAngle) {
        float f = 0.0F;

        for (BlocksAttacks.DamageReduction damageReduction : this.damageReductions) {
            f += damageReduction.resolve(damageSource, damageAmount, horizontalAngle);
        }

        return Mth.clamp(f, 0.0F, damageAmount);
    }

    public record DamageReduction(float horizontalBlockingAngle, Optional<HolderSet<DamageType>> type, float base, float factor) {
        public static final Codec<BlocksAttacks.DamageReduction> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ExtraCodecs.POSITIVE_FLOAT
                        .optionalFieldOf("horizontal_blocking_angle", 90.0F)
                        .forGetter(BlocksAttacks.DamageReduction::horizontalBlockingAngle),
                    RegistryCodecs.homogeneousList(Registries.DAMAGE_TYPE).optionalFieldOf("type").forGetter(BlocksAttacks.DamageReduction::type),
                    Codec.FLOAT.fieldOf("base").forGetter(BlocksAttacks.DamageReduction::base),
                    Codec.FLOAT.fieldOf("factor").forGetter(BlocksAttacks.DamageReduction::factor)
                )
                .apply(instance, BlocksAttacks.DamageReduction::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, BlocksAttacks.DamageReduction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            BlocksAttacks.DamageReduction::horizontalBlockingAngle,
            ByteBufCodecs.holderSet(Registries.DAMAGE_TYPE).apply(ByteBufCodecs::optional),
            BlocksAttacks.DamageReduction::type,
            ByteBufCodecs.FLOAT,
            BlocksAttacks.DamageReduction::base,
            ByteBufCodecs.FLOAT,
            BlocksAttacks.DamageReduction::factor,
            BlocksAttacks.DamageReduction::new
        );

        public float resolve(DamageSource damageSource, float damageAmount, double horizontalAngle) {
            if (horizontalAngle > (float) (Math.PI / 180.0) * this.horizontalBlockingAngle) {
                return 0.0F;
            } else {
                return this.type.isPresent() && !this.type.get().contains(damageSource.typeHolder())
                    ? 0.0F
                    : Mth.clamp(this.base + this.factor * damageAmount, 0.0F, damageAmount);
            }
        }
    }

    public record ItemDamageFunction(float threshold, float base, float factor) {
        public static final Codec<BlocksAttacks.ItemDamageFunction> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("threshold").forGetter(BlocksAttacks.ItemDamageFunction::threshold),
                    Codec.FLOAT.fieldOf("base").forGetter(BlocksAttacks.ItemDamageFunction::base),
                    Codec.FLOAT.fieldOf("factor").forGetter(BlocksAttacks.ItemDamageFunction::factor)
                )
                .apply(instance, BlocksAttacks.ItemDamageFunction::new)
        );
        public static final StreamCodec<ByteBuf, BlocksAttacks.ItemDamageFunction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            BlocksAttacks.ItemDamageFunction::threshold,
            ByteBufCodecs.FLOAT,
            BlocksAttacks.ItemDamageFunction::base,
            ByteBufCodecs.FLOAT,
            BlocksAttacks.ItemDamageFunction::factor,
            BlocksAttacks.ItemDamageFunction::new
        );
        public static final BlocksAttacks.ItemDamageFunction DEFAULT = new BlocksAttacks.ItemDamageFunction(1.0F, 0.0F, 1.0F);

        public int apply(float damageAmount) {
            return damageAmount < this.threshold ? 0 : Mth.floor(this.base + this.factor * damageAmount);
        }
    }
}
