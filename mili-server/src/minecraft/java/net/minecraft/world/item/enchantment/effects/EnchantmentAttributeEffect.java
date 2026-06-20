package net.minecraft.world.item.enchantment.effects;

import com.google.common.collect.HashMultimap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record EnchantmentAttributeEffect(Identifier id, Holder<Attribute> attribute, LevelBasedValue amount, AttributeModifier.Operation operation)
    implements EnchantmentLocationBasedEffect {
    public static final MapCodec<EnchantmentAttributeEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(EnchantmentAttributeEffect::id),
                Attribute.CODEC.fieldOf("attribute").forGetter(EnchantmentAttributeEffect::attribute),
                LevelBasedValue.CODEC.fieldOf("amount").forGetter(EnchantmentAttributeEffect::amount),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(EnchantmentAttributeEffect::operation)
            )
            .apply(instance, EnchantmentAttributeEffect::new)
    );

    private Identifier idForSlot(StringRepresentable slot) {
        return this.id.withSuffix("/" + slot.getSerializedName());
    }

    public AttributeModifier getModifier(int enchantmentLevel, StringRepresentable slot) {
        return new AttributeModifier(this.idForSlot(slot), this.amount().calculate(enchantmentLevel), this.operation());
    }

    @Override
    public void onChangedBlock(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 pos, boolean applyTransientEffects) {
        if (applyTransientEffects && entity instanceof LivingEntity livingEntity) {
            livingEntity.getAttributes().addTransientAttributeModifiers(this.makeAttributeMap(enchantmentLevel, item.inSlot()));
        }
    }

    @Override
    public void onDeactivated(EnchantedItemInUse item, Entity entity, Vec3 pos, int enchantmentLevel) {
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.getAttributes().removeAttributeModifiers(this.makeAttributeMap(enchantmentLevel, item.inSlot()));
        }
    }

    private HashMultimap<Holder<Attribute>, AttributeModifier> makeAttributeMap(int enchantmentLevel, EquipmentSlot slot) {
        HashMultimap<Holder<Attribute>, AttributeModifier> hashMultimap = HashMultimap.create();
        hashMultimap.put(this.attribute, this.getModifier(enchantmentLevel, slot));
        return hashMultimap;
    }

    @Override
    public MapCodec<EnchantmentAttributeEffect> codec() {
        return CODEC;
    }
}
