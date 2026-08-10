package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeMap {
    // Gale start - Lithium - replace AI attributes with optimized collections
    private final Map<Holder<Attribute>, AttributeInstance> attributes = new it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap<>(0);
    private final Set<AttributeInstance> attributesToSync = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(0);
    private final Set<AttributeInstance> attributesToUpdate = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(0);
    // Gale end - Lithium - replace AI attributes with optimized collections
    private final AttributeSupplier supplier;

    public AttributeMap(AttributeSupplier supplier) {
        this.supplier = supplier;
    }

    private void onAttributeModified(AttributeInstance instance) {
        this.attributesToUpdate.add(instance);
        if (instance.getAttribute().value().isClientSyncable()) {
            this.attributesToSync.add(instance);
        }
    }

    public Set<AttributeInstance> getAttributesToSync() {
        return this.attributesToSync;
    }

    public Set<AttributeInstance> getAttributesToUpdate() {
        return this.attributesToUpdate;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter(instance -> instance.getAttribute().value().isClientSyncable()).collect(Collectors.toList());
    }

    public @Nullable AttributeInstance getInstance(Holder<Attribute> attribute) {
        return this.attributes.computeIfAbsent(attribute, holder -> this.supplier.createInstance(this::onAttributeModified, (Holder<Attribute>)holder));
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id) != null : this.supplier.hasModifier(attribute, id);
    }

    public double getValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Holder<Attribute> attribute, Identifier id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id).amount() : this.supplier.getModifierValue(attribute, id);
    }

    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.forEach((attribute, modifier) -> {
            AttributeInstance instance = this.getInstance((Holder<Attribute>)attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.asMap().forEach((holder, collection) -> {
            AttributeInstance attributeInstance = this.attributes.get(holder);
            if (attributeInstance != null) {
                collection.forEach(attributeModifier -> attributeInstance.removeModifier(attributeModifier.id()));
            }
        });
    }

    public void assignAllValues(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.replaceFrom(attribute);
            }
        });
    }

    public void assignBaseValues(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.setBaseValue(attribute.getBaseValue());
            }
        });
    }

    public void assignPermanentModifiers(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.addPermanentModifiers(attribute.getPermanentModifiers());
            }
        });
    }

    public boolean resetBaseValue(Holder<Attribute> attribute) {
        if (!this.supplier.hasAttribute(attribute)) {
            return false;
        } else {
            AttributeInstance attributeInstance = this.attributes.get(attribute);
            if (attributeInstance != null) {
                attributeInstance.setBaseValue(this.supplier.getBaseValue(attribute));
            }

            return true;
        }
    }

    public List<AttributeInstance.Packed> pack() {
        List<AttributeInstance.Packed> list = new ArrayList<>(this.attributes.values().size());

        for (AttributeInstance attributeInstance : this.attributes.values()) {
            list.add(attributeInstance.pack());
        }

        return list;
    }

    public void apply(List<AttributeInstance.Packed> attributes) {
        for (AttributeInstance.Packed packed : attributes) {
            AttributeInstance instance = this.getInstance(packed.attribute());
            if (instance != null) {
                instance.apply(packed);
            }
        }
    }

    // Paper - start - living entity allow attribute registration
    public void registerAttribute(Holder<Attribute> attributeBase) {
        AttributeInstance attributeModifiable = new AttributeInstance(attributeBase, AttributeInstance::getAttribute);
        attributes.put(attributeBase, attributeModifiable);
    }
    // Paper - end - living entity allow attribute registration

}
