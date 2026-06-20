package net.minecraft.world.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DamageSource {
    private final Holder<DamageType> type;
    private final @Nullable Entity causingEntity;
    private final @Nullable Entity directEntity;
    private final @Nullable Vec3 damageSourcePosition;
    // CraftBukkit start
    private org.bukkit.event.entity.EntityDamageEvent.@Nullable DamageCause knownCause; // When the damage event cause is known by the context of the call rather than the damage source data
    @Nullable
    private Entity eventEntityDamager = null; // Relevant entity set when the game doesn't normally set a causingEntity/directEntity
    private org.bukkit.block.@Nullable Block eventBlockDamager; // Relevant block set. damageSourcePosition is only used for bad respawn point explosion or custom damage
    private org.bukkit.block.@Nullable BlockState fromBlockSnapshot; // Captured block snapshot when the eventBlockDamager is not relevant (e.g. for bad respawn point explosions the block is already removed)
    private boolean critical; // Supports arrows and sweeping damage

    public DamageSource knownCause(final org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        final DamageSource damageSource = this.copy();
        damageSource.knownCause = cause;
        return damageSource;
    }

    public org.bukkit.event.entity.EntityDamageEvent.@Nullable DamageCause knownCause() {
        return this.knownCause;
    }

    @Nullable
    public Entity eventEntityDamager() {
        return this.eventEntityDamager;
    }

    public DamageSource eventEntityDamager(final Entity entity) {
        if (this.directEntity != null) {
            throw new IllegalStateException("Cannot set an event damager when a direct entity is already set (report a bug to Paper)");
        }
        final DamageSource damageSource = this.copy();
        damageSource.eventEntityDamager = entity;
        return damageSource;
    }

    public org.bukkit.block.@Nullable Block eventBlockDamager() {
        return this.eventBlockDamager;
    }

    public DamageSource eventBlockDamager(final net.minecraft.world.level.LevelAccessor level, final net.minecraft.core.@Nullable BlockPos pos) {
        if (pos == null) {
            return this;
        }

        final DamageSource damageSource = this.copy();
        damageSource.eventBlockDamager = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        return damageSource;
    }

    public org.bukkit.block.@Nullable BlockState causingBlockSnapshot() {
        return this.fromBlockSnapshot;
    }

    public DamageSource causingBlockSnapshot(final org.bukkit.block.BlockState blockState) {
        if (this.eventBlockDamager != null) {
            throw new IllegalStateException("Cannot set a block snapshot when an event block damager is already set (report a bug to Paper)");
        }
        final DamageSource damageSource = this.copy();
        damageSource.fromBlockSnapshot = blockState;
        return damageSource;
    }

    public boolean isCritical() {
        return this.critical;
    }

    public DamageSource critical() {
        final DamageSource damageSource = this.copy();
        damageSource.critical = true;
        return damageSource;
    }

    // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
    private DamageSource copy() {
        final DamageSource damageSource = new DamageSource(this.type, this.directEntity, this.causingEntity, this.damageSourcePosition);
        damageSource.knownCause = this.knownCause;
        damageSource.eventEntityDamager = this.eventEntityDamager;
        damageSource.eventBlockDamager = this.eventBlockDamager;
        damageSource.fromBlockSnapshot = this.fromBlockSnapshot;
        damageSource.critical = this.critical;
        return damageSource;
    }
    // CraftBukkit end

    @Override
    public String toString() {
        return "DamageSource (" + this.type().msgId() + ")";
    }

    public float getFoodExhaustion() {
        return this.type().exhaustion();
    }

    public boolean isDirect() {
        return this.causingEntity == this.directEntity;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, @Nullable Vec3 damageSourcePosition) {
        this.type = type;
        this.causingEntity = causingEntity;
        this.directEntity = directEntity;
        this.damageSourcePosition = damageSourcePosition;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        this(type, directEntity, causingEntity, null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 damageSourcePosition) {
        this(type, null, null, damageSourcePosition);
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity entity) {
        this(type, entity, entity);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, null, null, null);
    }

    public @Nullable Entity getDirectEntity() {
        return this.directEntity;
    }

    public @Nullable Entity getEntity() {
        return this.causingEntity;
    }

    public @Nullable ItemStack getWeaponItem() {
        return this.directEntity != null ? this.directEntity.getWeaponItem() : null;
    }

    public Component getLocalizedDeathMessage(LivingEntity livingEntity) {
        String string = "death.attack." + this.type().msgId();
        if (this.causingEntity == null && this.directEntity == null) {
            LivingEntity killCredit = livingEntity.getKillCredit();
            String string1 = string + ".player";
            return killCredit != null && ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(killCredit)
                ? Component.translatable(string1, livingEntity.getDisplayName(), killCredit.getDisplayName())
                : Component.translatable(string, livingEntity.getDisplayName());
        } else {
            Component component = this.causingEntity == null ? this.directEntity.getDisplayName() : this.causingEntity.getDisplayName();
            ItemStack itemStack = this.causingEntity instanceof LivingEntity livingEntity1 && ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(livingEntity1) ? livingEntity1.getMainHandItem() : ItemStack.EMPTY; // Folia - region threading
            return !itemStack.isEmpty() && itemStack.has(DataComponents.CUSTOM_NAME)
                ? Component.translatable(string + ".item", livingEntity.getDisplayName(), component, itemStack.getDisplayName())
                : Component.translatable(string, livingEntity.getDisplayName(), component);
        }
    }

    public String getMsgId() {
        return this.type().msgId();
    }

    public boolean scalesWithDifficulty() {
        return switch (this.type().scaling()) {
            case NEVER -> false;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> this.causingEntity instanceof LivingEntity && !(this.causingEntity instanceof Player);
            case ALWAYS -> true;
        };
    }

    public boolean isCreativePlayer() {
        return this.getEntity() instanceof Player player && player.getAbilities().instabuild;
    }

    public @Nullable Vec3 getSourcePosition() {
        if (this.damageSourcePosition != null) {
            return this.damageSourcePosition;
        } else {
            return this.directEntity != null ? this.directEntity.position() : null;
        }
    }

    public @Nullable Vec3 sourcePositionRaw() {
        return this.damageSourcePosition;
    }

    public boolean is(TagKey<DamageType> damageTypeKey) {
        return this.type.is(damageTypeKey);
    }

    public boolean is(ResourceKey<DamageType> damageTypeKey) {
        return this.type.is(damageTypeKey);
    }

    public DamageType type() {
        return this.type.value();
    }

    public Holder<DamageType> typeHolder() {
        return this.type;
    }
}
