package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetAttributesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetAttributesFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    SetAttributesFunction.Modifier.CODEC.listOf().fieldOf("modifiers").forGetter(function -> function.modifiers),
                    Codec.BOOL.optionalFieldOf("replace", true).forGetter(function -> function.replace)
                )
            )
            .apply(instance, SetAttributesFunction::new)
    );
    private final List<SetAttributesFunction.Modifier> modifiers;
    private final boolean replace;

    SetAttributesFunction(List<LootItemCondition> predicates, List<SetAttributesFunction.Modifier> modifiers, boolean replace) {
        super(predicates);
        this.modifiers = List.copyOf(modifiers);
        this.replace = replace;
    }

    @Override
    public LootItemFunctionType<SetAttributesFunction> getType() {
        return LootItemFunctions.SET_ATTRIBUTES;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.modifiers.stream().flatMap(modifier -> modifier.amount.getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (this.replace) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, this.updateModifiers(context, ItemAttributeModifiers.EMPTY));
        } else {
            stack.update(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY,
                itemAttributeModifiers -> this.updateModifiers(context, itemAttributeModifiers)
            );
        }

        return stack;
    }

    private ItemAttributeModifiers updateModifiers(LootContext context, ItemAttributeModifiers modifiers) {
        RandomSource random = context.getRandom();

        for (SetAttributesFunction.Modifier modifier : this.modifiers) {
            EquipmentSlotGroup equipmentSlotGroup = Util.getRandom(modifier.slots, random);
            modifiers = modifiers.withModifierAdded(
                modifier.attribute, new AttributeModifier(modifier.id, modifier.amount.getFloat(context), modifier.operation), equipmentSlotGroup
            );
        }

        return modifiers;
    }

    public static SetAttributesFunction.ModifierBuilder modifier(
        Identifier id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount
    ) {
        return new SetAttributesFunction.ModifierBuilder(id, attribute, operation, amount);
    }

    public static SetAttributesFunction.Builder setAttributes() {
        return new SetAttributesFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetAttributesFunction.Builder> {
        private final boolean replace;
        private final List<SetAttributesFunction.Modifier> modifiers = Lists.newArrayList();

        public Builder(boolean replace) {
            this.replace = replace;
        }

        public Builder() {
            this(false);
        }

        @Override
        protected SetAttributesFunction.Builder getThis() {
            return this;
        }

        public SetAttributesFunction.Builder withModifier(SetAttributesFunction.ModifierBuilder modifierBuilder) {
            this.modifiers.add(modifierBuilder.build());
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetAttributesFunction(this.getConditions(), this.modifiers, this.replace);
        }
    }

    record Modifier(Identifier id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount, List<EquipmentSlotGroup> slots) {
        private static final Codec<List<EquipmentSlotGroup>> SLOTS_CODEC = ExtraCodecs.nonEmptyList(ExtraCodecs.compactListCodec(EquipmentSlotGroup.CODEC));
        public static final Codec<SetAttributesFunction.Modifier> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("id").forGetter(SetAttributesFunction.Modifier::id),
                    Attribute.CODEC.fieldOf("attribute").forGetter(SetAttributesFunction.Modifier::attribute),
                    AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(SetAttributesFunction.Modifier::operation),
                    NumberProviders.CODEC.fieldOf("amount").forGetter(SetAttributesFunction.Modifier::amount),
                    SLOTS_CODEC.fieldOf("slot").forGetter(SetAttributesFunction.Modifier::slots)
                )
                .apply(instance, SetAttributesFunction.Modifier::new)
        );
    }

    public static class ModifierBuilder {
        private final Identifier id;
        private final Holder<Attribute> attribute;
        private final AttributeModifier.Operation operation;
        private final NumberProvider amount;
        private final Set<EquipmentSlotGroup> slots = EnumSet.noneOf(EquipmentSlotGroup.class);

        public ModifierBuilder(Identifier id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount) {
            this.id = id;
            this.attribute = attribute;
            this.operation = operation;
            this.amount = amount;
        }

        public SetAttributesFunction.ModifierBuilder forSlot(EquipmentSlotGroup slot) {
            this.slots.add(slot);
            return this;
        }

        public SetAttributesFunction.Modifier build() {
            return new SetAttributesFunction.Modifier(this.id, this.attribute, this.operation, this.amount, List.copyOf(this.slots));
        }
    }
}
