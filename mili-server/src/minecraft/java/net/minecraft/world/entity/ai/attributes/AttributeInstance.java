package net.minecraft.world.entity.ai.attributes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeInstance {
    private final Holder<Attribute> attribute;
    private final Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation = Maps.newEnumMap(AttributeModifier.Operation.class);
    private final Map<Identifier, AttributeModifier> modifierById = new Object2ObjectArrayMap<>();
    private final Map<Identifier, AttributeModifier> permanentModifiers = new Object2ObjectArrayMap<>();
    private double baseValue;
    private boolean dirty = true;
    private double cachedValue;
    private final Consumer<AttributeInstance> onDirty;

    public AttributeInstance(Holder<Attribute> attribute, Consumer<AttributeInstance> onDirty) {
        this.attribute = attribute;
        this.onDirty = onDirty;
        this.baseValue = attribute.value().getDefaultValue();
    }

    public Holder<Attribute> getAttribute() {
        return this.attribute;
    }

    public double getBaseValue() {
        return this.baseValue;
    }

    public void setBaseValue(double baseValue) {
        if (baseValue != this.baseValue) {
            this.baseValue = baseValue;
            this.setDirty();
        }
    }

    @VisibleForTesting
    Map<Identifier, AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.computeIfAbsent(operation, operation1 -> new Object2ObjectOpenHashMap<>());
    }

    public Set<AttributeModifier> getModifiers() {
        return ImmutableSet.copyOf(this.modifierById.values());
    }

    public Set<AttributeModifier> getPermanentModifiers() {
        return ImmutableSet.copyOf(this.permanentModifiers.values());
    }

    public @Nullable AttributeModifier getModifier(Identifier id) {
        return this.modifierById.get(id);
    }

    public boolean hasModifier(Identifier id) {
        return this.modifierById.get(id) != null;
    }

    private void addModifier(AttributeModifier modifier) {
        AttributeModifier attributeModifier = this.modifierById.putIfAbsent(modifier.id(), modifier);
        if (attributeModifier != null) {
            throw new IllegalArgumentException("Modifier is already applied on this attribute!");
        } else {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addOrUpdateTransientModifier(AttributeModifier modifier) {
        AttributeModifier attributeModifier = this.modifierById.put(modifier.id(), modifier);
        if (modifier != attributeModifier) {
            this.getModifiers(modifier.operation()).put(modifier.id(), modifier);
            this.setDirty();
        }
    }

    public void addTransientModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
    }

    public void addOrReplacePermanentModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    public void addPermanentModifier(AttributeModifier modifier) {
        this.addModifier(modifier);
        this.permanentModifiers.put(modifier.id(), modifier);
    }

    public void addPermanentModifiers(Collection<AttributeModifier> modifiers) {
        for (AttributeModifier attributeModifier : modifiers) {
            this.addPermanentModifier(attributeModifier);
        }
    }

    protected void setDirty() {
        this.dirty = true;
        this.onDirty.accept(this);
    }

    public void removeModifier(AttributeModifier modifier) {
        this.removeModifier(modifier.id());
    }

    public boolean removeModifier(Identifier id) {
        AttributeModifier attributeModifier = this.modifierById.remove(id);
        if (attributeModifier == null) {
            return false;
        } else {
            this.getModifiers(attributeModifier.operation()).remove(id);
            this.permanentModifiers.remove(id);
            this.setDirty();
            return true;
        }
    }

    public void removeModifiers() {
        for (AttributeModifier attributeModifier : this.getModifiers()) {
            this.removeModifier(attributeModifier);
        }
    }

    public double getValue() {
        if (this.dirty) {
            this.cachedValue = this.calculateValue();
            this.dirty = false;
        }

        return this.cachedValue;
    }

    private double calculateValue() {
        double baseValue = this.getBaseValue();

        for (AttributeModifier attributeModifier : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_VALUE)) {
            baseValue += attributeModifier.amount(); // Paper - destroy speed API - diff on change
        }

        double d = baseValue;

        for (AttributeModifier attributeModifier1 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
            d += baseValue * attributeModifier1.amount(); // Paper - destroy speed API - diff on change
        }

        for (AttributeModifier attributeModifier1 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
            d *= 1.0 + attributeModifier1.amount(); // Paper - destroy speed API - diff on change
        }

        return this.attribute.value().sanitizeValue(d); // Paper - destroy speed API - diff on change
    }

    private Collection<AttributeModifier> getModifiersOrEmpty(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.getOrDefault(operation, Map.of()).values();
    }

    public void replaceFrom(AttributeInstance instance) {
        this.baseValue = instance.baseValue;
        this.modifierById.clear();
        this.modifierById.putAll(instance.modifierById);
        this.permanentModifiers.clear();
        this.permanentModifiers.putAll(instance.permanentModifiers);
        this.modifiersByOperation.clear();
        instance.modifiersByOperation
            .forEach((operation, map) -> this.getModifiers(operation).putAll((Map<? extends Identifier, ? extends AttributeModifier>)map));
        this.setDirty();
    }

    public AttributeInstance.Packed pack() {
        return new AttributeInstance.Packed(this.attribute, this.baseValue, List.copyOf(this.permanentModifiers.values()));
    }

    public void apply(AttributeInstance.Packed instance) {
        this.baseValue = instance.baseValue;

        for (AttributeModifier attributeModifier : instance.modifiers) {
            this.modifierById.put(attributeModifier.id(), attributeModifier);
            this.getModifiers(attributeModifier.operation()).put(attributeModifier.id(), attributeModifier);
            this.permanentModifiers.put(attributeModifier.id(), attributeModifier);
        }

        this.setDirty();
    }

    public record Packed(Holder<Attribute> attribute, double baseValue, List<AttributeModifier> modifiers) {
        public static final Codec<AttributeInstance.Packed> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("id").forGetter(AttributeInstance.Packed::attribute),
                    Codec.DOUBLE.fieldOf("base").orElse(0.0).forGetter(AttributeInstance.Packed::baseValue),
                    AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(AttributeInstance.Packed::modifiers)
                )
                .apply(instance, AttributeInstance.Packed::new)
        );
        public static final Codec<List<AttributeInstance.Packed>> LIST_CODEC = CODEC.listOf();
    }
}
