package net.minecraft.world.effect;

import com.google.common.collect.ComparisonChain;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MobEffectInstance implements Comparable<MobEffectInstance> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final Codec<MobEffectInstance> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                MobEffect.CODEC.fieldOf("id").forGetter(MobEffectInstance::getEffect),
                MobEffectInstance.Details.MAP_CODEC.forGetter(MobEffectInstance::asDetails)
            )
            .apply(instance, MobEffectInstance::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance> STREAM_CODEC = StreamCodec.composite(
        MobEffect.STREAM_CODEC, MobEffectInstance::getEffect, MobEffectInstance.Details.STREAM_CODEC, MobEffectInstance::asDetails, MobEffectInstance::new
    );
    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    public @Nullable MobEffectInstance hiddenEffect;
    private final MobEffectInstance.BlendState blendState = new MobEffectInstance.BlendState();

    public MobEffectInstance(Holder<MobEffect> effect) {
        this(effect, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, null);
    }

    public MobEffectInstance(
        Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, @Nullable MobEffectInstance hiddenEffect
    ) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Mth.clamp(amplifier, 0, 255);
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
    }

    public MobEffectInstance(MobEffectInstance other) {
        this.effect = other.effect;
        this.setDetailsFrom(other);
    }

    private MobEffectInstance(Holder<MobEffect> effect, MobEffectInstance.Details details) {
        this(
            effect,
            details.duration(),
            details.amplifier(),
            details.ambient(),
            details.showParticles(),
            details.showIcon(),
            details.hiddenEffect().map(details1 -> new MobEffectInstance(effect, details1)).orElse(null)
        );
    }

    private MobEffectInstance.Details asDetails() {
        return new MobEffectInstance.Details(
            this.getAmplifier(),
            this.getDuration(),
            this.isAmbient(),
            this.isVisible(),
            this.showIcon(),
            Optional.ofNullable(this.hiddenEffect).map(MobEffectInstance::asDetails)
        );
    }

    public float getBlendFactor(LivingEntity entity, float delta) {
        return this.blendState.getFactor(entity, delta);
    }

    public ParticleOptions getParticleOptions() {
        return this.effect.value().createParticleOptions(this);
    }

    void setDetailsFrom(MobEffectInstance effectInstance) {
        this.duration = effectInstance.duration;
        this.amplifier = effectInstance.amplifier;
        this.ambient = effectInstance.ambient;
        this.visible = effectInstance.visible;
        this.showIcon = effectInstance.showIcon;
    }

    public boolean update(MobEffectInstance other) {
        if (!this.effect.equals(other.effect)) {
            LOGGER.warn("This method should only be called for matching effects!");
        }

        boolean flag = false;
        if (other.amplifier > this.amplifier) {
            if (other.isShorterDurationThan(this)) {
                MobEffectInstance mobEffectInstance = this.hiddenEffect;
                this.hiddenEffect = new MobEffectInstance(this);
                this.hiddenEffect.hiddenEffect = mobEffectInstance;
            }

            this.amplifier = other.amplifier;
            this.duration = other.duration;
            flag = true;
        } else if (this.isShorterDurationThan(other)) {
            if (other.amplifier == this.amplifier) {
                this.duration = other.duration;
                flag = true;
            } else if (this.hiddenEffect == null) {
                this.hiddenEffect = new MobEffectInstance(other);
            } else {
                this.hiddenEffect.update(other);
            }
        }

        if (!other.ambient && this.ambient || flag) {
            this.ambient = other.ambient;
            flag = true;
        }

        if (other.visible != this.visible) {
            this.visible = other.visible;
            flag = true;
        }

        if (other.showIcon != this.showIcon) {
            this.showIcon = other.showIcon;
            flag = true;
        }

        return flag;
    }

    public boolean isShorterDurationThan(MobEffectInstance other) {
        return !this.isInfiniteDuration() && (this.duration < other.duration || other.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() {
        return this.duration == -1;
    }

    public boolean endsWithin(int duration) {
        return !this.isInfiniteDuration() && this.duration <= duration;
    }

    public MobEffectInstance withScaledDuration(float scale) {
        MobEffectInstance mobEffectInstance = new MobEffectInstance(this);
        mobEffectInstance.duration = mobEffectInstance.mapDuration(duration -> Math.max(Mth.floor(duration * scale), 1));
        return mobEffectInstance;
    }

    public int mapDuration(Int2IntFunction mapper) {
        return !this.isInfiniteDuration() && this.duration != 0 ? mapper.applyAsInt(this.duration) : this.duration;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean showIcon() {
        return this.showIcon;
    }

    public boolean tickServer(ServerLevel level, LivingEntity entity, Runnable onEffectUpdated) {
        if (!this.hasRemainingDuration()) {
            return false;
        } else {
            int i = this.isInfiniteDuration() ? entity.tickCount : this.duration;
            if (this.effect.value().shouldApplyEffectTickThisTick(i, this.amplifier) && new io.papermc.paper.event.entity.EntityEffectTickEvent(entity.getBukkitLivingEntity(), org.bukkit.craftbukkit.potion.CraftPotionEffectType.minecraftHolderToBukkit(this.effect), this.amplifier).callEvent() && !this.effect.value().applyEffectTick(level, entity, this.amplifier)) { // Paper - Add EntityEffectTickEvent
                return false;
            } else {
                this.tickDownDuration();
                if (this.downgradeToHiddenEffect()) {
                    onEffectUpdated.run();
                }

                return this.hasRemainingDuration();
            }
        }
    }

    public void tickClient() {
        if (this.hasRemainingDuration()) {
            this.tickDownDuration();
            this.downgradeToHiddenEffect();
        }

        this.blendState.tick(this);
    }

    private boolean hasRemainingDuration() {
        return this.isInfiniteDuration() || this.duration > 0;
    }

    private void tickDownDuration() {
        if (this.hiddenEffect != null) {
            this.hiddenEffect.tickDownDuration();
        }

        this.duration = this.mapDuration(duration -> duration - 1);
    }

    private boolean downgradeToHiddenEffect() {
        if (this.duration == 0 && this.hiddenEffect != null) {
            this.setDetailsFrom(this.hiddenEffect);
            this.hiddenEffect = this.hiddenEffect.hiddenEffect;
            return true;
        } else {
            return false;
        }
    }

    public void onEffectStarted(LivingEntity entity) {
        this.effect.value().onEffectStarted(entity, this.amplifier);
    }

    public void onMobRemoved(ServerLevel level, LivingEntity entity, Entity.RemovalReason reason) {
        this.effect.value().onMobRemoved(level, entity, this.amplifier, reason);
    }

    public void onMobHurt(ServerLevel level, LivingEntity entity, DamageSource damageSource, float amount) {
        this.effect.value().onMobHurt(level, entity, this.amplifier, damageSource, amount);
    }

    public String getDescriptionId() {
        return this.effect.value().getDescriptionId();
    }

    @Override
    public String toString() {
        String string;
        if (this.amplifier > 0) {
            string = this.getDescriptionId() + " x " + (this.amplifier + 1) + ", Duration: " + this.describeDuration();
        } else {
            string = this.getDescriptionId() + ", Duration: " + this.describeDuration();
        }

        if (!this.visible) {
            string = string + ", Particles: false";
        }

        if (!this.showIcon) {
            string = string + ", Show Icon: false";
        }

        return string;
    }

    private String describeDuration() {
        return this.isInfiniteDuration() ? "infinite" : Integer.toString(this.duration);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof MobEffectInstance mobEffectInstance
                && this.duration == mobEffectInstance.duration
                && this.amplifier == mobEffectInstance.amplifier
                && this.ambient == mobEffectInstance.ambient
                && this.visible == mobEffectInstance.visible
                && this.showIcon == mobEffectInstance.showIcon
                && this.effect.equals(mobEffectInstance.effect);
    }

    @Override
    public int hashCode() {
        int hashCode = this.effect.hashCode();
        hashCode = 31 * hashCode + this.duration;
        hashCode = 31 * hashCode + this.amplifier;
        hashCode = 31 * hashCode + (this.ambient ? 1 : 0);
        hashCode = 31 * hashCode + (this.visible ? 1 : 0);
        return 31 * hashCode + (this.showIcon ? 1 : 0);
    }

    @Override
    public int compareTo(MobEffectInstance other) {
        int i = 32147;
        return (this.getDuration() <= 32147 || other.getDuration() <= 32147) && (!this.isAmbient() || !other.isAmbient())
            ? ComparisonChain.start()
                .compareFalseFirst(this.isAmbient(), other.isAmbient())
                .compareFalseFirst(this.isInfiniteDuration(), other.isInfiniteDuration())
                .compare(this.getDuration(), other.getDuration())
                .compare(this.getEffect().value().getColor(), other.getEffect().value().getColor())
                .result()
            : ComparisonChain.start()
                .compare(this.isAmbient(), other.isAmbient())
                .compare(this.getEffect().value().getColor(), other.getEffect().value().getColor())
                .result();
    }

    public void onEffectAdded(LivingEntity livingEntity) {
        this.effect.value().onEffectAdded(livingEntity, this.amplifier);
    }

    public boolean is(Holder<MobEffect> effect) {
        return this.effect.equals(effect);
    }

    public void copyBlendState(MobEffectInstance effectInstance) {
        this.blendState.copyFrom(effectInstance.blendState);
    }

    public void skipBlending() {
        this.blendState.setImmediate(this);
    }

    static class BlendState {
        private float factor;
        private float factorPreviousFrame;

        public void setImmediate(MobEffectInstance effectInstance) {
            this.factor = hasEffect(effectInstance) ? 1.0F : 0.0F;
            this.factorPreviousFrame = this.factor;
        }

        public void copyFrom(MobEffectInstance.BlendState blendState) {
            this.factor = blendState.factor;
            this.factorPreviousFrame = blendState.factorPreviousFrame;
        }

        public void tick(MobEffectInstance effect) {
            this.factorPreviousFrame = this.factor;
            boolean hasEffect = hasEffect(effect);
            float f = hasEffect ? 1.0F : 0.0F;
            if (this.factor != f) {
                MobEffect mobEffect = effect.getEffect().value();
                int i = hasEffect ? mobEffect.getBlendInDurationTicks() : mobEffect.getBlendOutDurationTicks();
                if (i == 0) {
                    this.factor = f;
                } else {
                    float f1 = 1.0F / i;
                    this.factor = this.factor + Mth.clamp(f - this.factor, -f1, f1);
                }
            }
        }

        private static boolean hasEffect(MobEffectInstance effect) {
            return !effect.endsWithin(effect.getEffect().value().getBlendOutAdvanceTicks());
        }

        public float getFactor(LivingEntity entity, float delta) {
            if (entity.isRemoved()) {
                this.factorPreviousFrame = this.factor;
            }

            return Mth.lerp(delta, this.factorPreviousFrame, this.factor);
        }
    }

    record Details(int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect) {
        public static final MapCodec<MobEffectInstance.Details> MAP_CODEC = MapCodec.recursive(
            "MobEffectInstance.Details",
            codec -> RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance.Details::amplifier),
                        Codec.INT.optionalFieldOf("duration", 0).forGetter(MobEffectInstance.Details::duration),
                        Codec.BOOL.optionalFieldOf("ambient", false).forGetter(MobEffectInstance.Details::ambient),
                        Codec.BOOL.optionalFieldOf("show_particles", true).forGetter(MobEffectInstance.Details::showParticles),
                        Codec.BOOL.optionalFieldOf("show_icon").forGetter(details -> Optional.of(details.showIcon())),
                        codec.optionalFieldOf("hidden_effect").forGetter(MobEffectInstance.Details::hiddenEffect)
                    )
                    .apply(instance, MobEffectInstance.Details::create)
            )
        );
        public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, MobEffectInstance.Details> STREAM_CODEC = StreamCodec.recursive( // Paper - Track codec depth
            codec -> StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                MobEffectInstance.Details::amplifier,
                ByteBufCodecs.VAR_INT,
                MobEffectInstance.Details::duration,
                ByteBufCodecs.BOOL,
                MobEffectInstance.Details::ambient,
                ByteBufCodecs.BOOL,
                MobEffectInstance.Details::showParticles,
                ByteBufCodecs.BOOL,
                MobEffectInstance.Details::showIcon,
                codec.apply(ByteBufCodecs::increaseDepth).apply(ByteBufCodecs::optional), // Paper - Track codec depth
                MobEffectInstance.Details::hiddenEffect,
                MobEffectInstance.Details::new
            )
        );

        private static MobEffectInstance.Details create(
            int amplifier, int duration, boolean ambient, boolean showParticles, Optional<Boolean> showIcon, Optional<MobEffectInstance.Details> hiddenEffect
        ) {
            return new MobEffectInstance.Details(amplifier, duration, ambient, showParticles, showIcon.orElse(showParticles), hiddenEffect);
        }
    }
}
