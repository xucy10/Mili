package net.minecraft.world.effect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.jspecify.annotations.Nullable;

public class MobEffect implements FeatureElement {
    public static final Codec<Holder<MobEffect>> CODEC = BuiltInRegistries.MOB_EFFECT.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<MobEffect>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT);
    private static final int AMBIENT_ALPHA = Mth.floor(38.25F);
    public final Map<Holder<Attribute>, MobEffect.AttributeTemplate> attributeModifiers = new Object2ObjectOpenHashMap<>();
    private final MobEffectCategory category;
    private final int color;
    private final Function<MobEffectInstance, ParticleOptions> particleFactory;
    private @Nullable String descriptionId;
    private int blendInDurationTicks;
    private int blendOutDurationTicks;
    private int blendOutAdvanceTicks;
    private Optional<SoundEvent> soundOnAdded = Optional.empty();
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    protected MobEffect(MobEffectCategory category, int color) {
        this.category = category;
        this.color = color;
        this.particleFactory = effect -> {
            int i = effect.isAmbient() ? AMBIENT_ALPHA : 255;
            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.color(i, color));
        };
    }

    protected MobEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        this.category = category;
        this.color = color;
        this.particleFactory = effect -> particle;
    }

    public int getBlendInDurationTicks() {
        return this.blendInDurationTicks;
    }

    public int getBlendOutDurationTicks() {
        return this.blendOutDurationTicks;
    }

    public int getBlendOutAdvanceTicks() {
        return this.blendOutAdvanceTicks;
    }

    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        return true;
    }

    public void applyInstantenousEffect(
        ServerLevel level, @Nullable Entity source, @Nullable Entity indirectSource, LivingEntity entity, int amplifier, double health
    ) {
        if (!new io.papermc.paper.event.entity.EntityEffectTickEvent(entity.getBukkitLivingEntity(), org.bukkit.craftbukkit.potion.CraftPotionEffectType.minecraftToBukkit(this), amplifier).callEvent()) { return; } // Paper - Add EntityEffectTickEvent
        this.applyEffectTick(level, entity, amplifier);
    }

    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    public void onEffectStarted(LivingEntity entity, int amplifier) {
    }

    public void onEffectAdded(LivingEntity entity, int amplifier) {
        this.soundOnAdded
            .ifPresent(sound -> entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(), 1.0F, 1.0F));
    }

    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
    }

    public void onMobHurt(ServerLevel level, LivingEntity entity, int amplifier, DamageSource damageSource, float amount) {
    }

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return Component.translatable(this.getDescriptionId());
    }

    public MobEffectCategory getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, amount, operation));
        return this;
    }

    public MobEffect setBlendDuration(int blendDuration) {
        return this.setBlendDuration(blendDuration, blendDuration, blendDuration);
    }

    public MobEffect setBlendDuration(int blendInDurationTicks, int blendOutDurationTicks, int blendOutAdvanceTicks) {
        this.blendInDurationTicks = blendInDurationTicks;
        this.blendOutDurationTicks = blendOutDurationTicks;
        this.blendOutAdvanceTicks = blendOutAdvanceTicks;
        return this;
    }

    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> output) {
        this.attributeModifiers.forEach((holder, attributeTemplate) -> output.accept((Holder<Attribute>)holder, attributeTemplate.create(amplifier)));
    }

    public void removeAttributeModifiers(AttributeMap attributeMap) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance instance = attributeMap.getInstance(entry.getKey());
            if (instance != null) {
                instance.removeModifier(entry.getValue().id());
            }
        }
    }

    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance instance = attributeMap.getInstance(entry.getKey());
            if (instance != null) {
                instance.removeModifier(entry.getValue().id());
                instance.addPermanentModifier(entry.getValue().create(amplifier));
            }
        }
    }

    public boolean isBeneficial() {
        return this.category == MobEffectCategory.BENEFICIAL;
    }

    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return this.particleFactory.apply(effect);
    }

    public MobEffect withSoundOnAdded(SoundEvent sound) {
        this.soundOnAdded = Optional.of(sound);
        return this;
    }

    public MobEffect requiredFeatures(FeatureFlag... requiredFeatures) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public record AttributeTemplate(Identifier id, double amount, AttributeModifier.Operation operation) {
        public AttributeModifier create(int level) {
            return new AttributeModifier(this.id, this.amount * (level + 1), this.operation);
        }
    }
}
