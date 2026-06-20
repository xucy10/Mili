package net.minecraft.world.item.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public record Equippable(
    EquipmentSlot slot,
    Holder<SoundEvent> equipSound,
    Optional<ResourceKey<EquipmentAsset>> assetId,
    Optional<Identifier> cameraOverlay,
    Optional<HolderSet<EntityType<?>>> allowedEntities,
    boolean dispensable,
    boolean swappable,
    boolean damageOnHurt,
    boolean equipOnInteract,
    boolean canBeSheared,
    Holder<SoundEvent> shearingSound
) {
    public static final Codec<Equippable> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(Equippable::slot),
                SoundEvent.CODEC.optionalFieldOf("equip_sound", SoundEvents.ARMOR_EQUIP_GENERIC).forGetter(Equippable::equipSound),
                ResourceKey.codec(EquipmentAssets.ROOT_ID).optionalFieldOf("asset_id").forGetter(Equippable::assetId),
                Identifier.CODEC.optionalFieldOf("camera_overlay").forGetter(Equippable::cameraOverlay),
                RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).optionalFieldOf("allowed_entities").forGetter(Equippable::allowedEntities),
                Codec.BOOL.optionalFieldOf("dispensable", true).forGetter(Equippable::dispensable),
                Codec.BOOL.optionalFieldOf("swappable", true).forGetter(Equippable::swappable),
                Codec.BOOL.optionalFieldOf("damage_on_hurt", true).forGetter(Equippable::damageOnHurt),
                Codec.BOOL.optionalFieldOf("equip_on_interact", false).forGetter(Equippable::equipOnInteract),
                Codec.BOOL.optionalFieldOf("can_be_sheared", false).forGetter(Equippable::canBeSheared),
                SoundEvent.CODEC
                    .optionalFieldOf("shearing_sound", BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SHEARS_SNIP))
                    .forGetter(Equippable::shearingSound)
            )
            .apply(instance, Equippable::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Equippable> STREAM_CODEC = StreamCodec.composite(
        EquipmentSlot.STREAM_CODEC,
        Equippable::slot,
        SoundEvent.STREAM_CODEC,
        Equippable::equipSound,
        ResourceKey.streamCodec(EquipmentAssets.ROOT_ID).apply(ByteBufCodecs::optional),
        Equippable::assetId,
        Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional),
        Equippable::cameraOverlay,
        ByteBufCodecs.holderSet(Registries.ENTITY_TYPE).apply(ByteBufCodecs::optional),
        Equippable::allowedEntities,
        ByteBufCodecs.BOOL,
        Equippable::dispensable,
        ByteBufCodecs.BOOL,
        Equippable::swappable,
        ByteBufCodecs.BOOL,
        Equippable::damageOnHurt,
        ByteBufCodecs.BOOL,
        Equippable::equipOnInteract,
        ByteBufCodecs.BOOL,
        Equippable::canBeSheared,
        SoundEvent.STREAM_CODEC,
        Equippable::shearingSound,
        Equippable::new
    );

    public static Equippable llamaSwag(DyeColor color) {
        return builder(EquipmentSlot.BODY)
            .setEquipSound(SoundEvents.LLAMA_SWAG)
            .setAsset(EquipmentAssets.CARPETS.get(color))
            .setAllowedEntities(EntityType.LLAMA, EntityType.TRADER_LLAMA)
            .setCanBeSheared(true)
            .setShearingSound(SoundEvents.LLAMA_CARPET_UNEQUIP)
            .build();
    }

    public static Equippable saddle() {
        HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
        return builder(EquipmentSlot.SADDLE)
            .setEquipSound(SoundEvents.HORSE_SADDLE)
            .setAsset(EquipmentAssets.SADDLE)
            .setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_EQUIP_SADDLE))
            .setEquipOnInteract(true)
            .setCanBeSheared(true)
            .setShearingSound(SoundEvents.SADDLE_UNEQUIP)
            .build();
    }

    public static Equippable harness(DyeColor color) {
        HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
        return builder(EquipmentSlot.BODY)
            .setEquipSound(SoundEvents.HARNESS_EQUIP)
            .setAsset(EquipmentAssets.HARNESSES.get(color))
            .setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_EQUIP_HARNESS))
            .setEquipOnInteract(true)
            .setCanBeSheared(true)
            .setShearingSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.HARNESS_UNEQUIP))
            .build();
    }

    public static Equippable.Builder builder(EquipmentSlot slot) {
        return new Equippable.Builder(slot);
    }

    public InteractionResult swapWithEquipmentSlot(ItemStack stack, Player player) {
        if (player.canUseSlot(this.slot) && this.canBeEquippedBy(player.getType())) {
            ItemStack itemBySlot = player.getItemBySlot(this.slot);
            if ((!EnchantmentHelper.has(itemBySlot, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative())
                && !ItemStack.isSameItemSameComponents(stack, itemBySlot)) {
                if (!player.level().isClientSide()) {
                    player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }

                if (stack.getCount() <= 1) {
                    ItemStack itemStack = itemBySlot.isEmpty() ? stack : itemBySlot.copyAndClear();
                    ItemStack itemStack1 = player.isCreative() ? stack.copy() : stack.copyAndClear();
                    player.setItemSlot(this.slot, itemStack1);
                    return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
                } else {
                    ItemStack itemStack = itemBySlot.copyAndClear();
                    ItemStack itemStack1 = stack.consumeAndReturn(1, player);
                    player.setItemSlot(this.slot, itemStack1);
                    if (!player.getInventory().add(itemStack)) {
                        player.drop(itemStack, false);
                    }

                    return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
                }
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    public InteractionResult equipOnTarget(Player player, LivingEntity entity, ItemStack stack) {
        if (entity.isEquippableInSlot(stack, this.slot) && !entity.hasItemInSlot(this.slot) && entity.isAlive()) {
            if (!player.level().isClientSide()) {
                entity.setItemSlot(this.slot, stack.split(1));
                if (entity instanceof Mob mob) {
                    mob.setGuaranteedDrop(this.slot);
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    public boolean canBeEquippedBy(EntityType<?> entityType) {
        return this.allowedEntities.isEmpty() || this.allowedEntities.get().contains(entityType.builtInRegistryHolder());
    }

    public static class Builder {
        private final EquipmentSlot slot;
        private Holder<SoundEvent> equipSound = SoundEvents.ARMOR_EQUIP_GENERIC;
        private Optional<ResourceKey<EquipmentAsset>> assetId = Optional.empty();
        private Optional<Identifier> cameraOverlay = Optional.empty();
        private Optional<HolderSet<EntityType<?>>> allowedEntities = Optional.empty();
        private boolean dispensable = true;
        private boolean swappable = true;
        private boolean damageOnHurt = true;
        private boolean equipOnInteract;
        private boolean canBeSheared;
        private Holder<SoundEvent> shearingSound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SHEARS_SNIP);

        Builder(EquipmentSlot slot) {
            this.slot = slot;
        }

        public Equippable.Builder setEquipSound(Holder<SoundEvent> equipSound) {
            this.equipSound = equipSound;
            return this;
        }

        public Equippable.Builder setAsset(ResourceKey<EquipmentAsset> asset) {
            this.assetId = Optional.of(asset);
            return this;
        }

        public Equippable.Builder setCameraOverlay(Identifier cameraOverlay) {
            this.cameraOverlay = Optional.of(cameraOverlay);
            return this;
        }

        public Equippable.Builder setAllowedEntities(EntityType<?>... allowedEntities) {
            return this.setAllowedEntities(HolderSet.direct(EntityType::builtInRegistryHolder, allowedEntities));
        }

        public Equippable.Builder setAllowedEntities(HolderSet<EntityType<?>> allowedEntities) {
            this.allowedEntities = Optional.of(allowedEntities);
            return this;
        }

        public Equippable.Builder setDispensable(boolean dispensable) {
            this.dispensable = dispensable;
            return this;
        }

        public Equippable.Builder setSwappable(boolean swappable) {
            this.swappable = swappable;
            return this;
        }

        public Equippable.Builder setDamageOnHurt(boolean damageOnHurt) {
            this.damageOnHurt = damageOnHurt;
            return this;
        }

        public Equippable.Builder setEquipOnInteract(boolean equipOnInteract) {
            this.equipOnInteract = equipOnInteract;
            return this;
        }

        public Equippable.Builder setCanBeSheared(boolean canBeSheared) {
            this.canBeSheared = canBeSheared;
            return this;
        }

        public Equippable.Builder setShearingSound(Holder<SoundEvent> shearingSound) {
            this.shearingSound = shearingSound;
            return this;
        }

        public Equippable build() {
            return new Equippable(
                this.slot,
                this.equipSound,
                this.assetId,
                this.cameraOverlay,
                this.allowedEntities,
                this.dispensable,
                this.swappable,
                this.damageOnHurt,
                this.equipOnInteract,
                this.canBeSheared,
                this.shearingSound
            );
        }
    }
}
