package net.minecraft.world.item.enchantment;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;

public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;
    public static final Codec<Enchantment> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
                Enchantment.EnchantmentDefinition.CODEC.forGetter(Enchantment::definition),
                RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                    .optionalFieldOf("exclusive_set", HolderSet.direct())
                    .forGetter(Enchantment::exclusiveSet),
                EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY).forGetter(Enchantment::effects)
            )
            .apply(instance, Enchantment::new)
    );
    public static final Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static Enchantment.Cost constantCost(int cost) {
        return new Enchantment.Cost(cost, 0);
    }

    public static Enchantment.Cost dynamicCost(int base, int perLevel) {
        return new Enchantment.Cost(base, perLevel);
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        HolderSet<Item> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.of(primaryItems), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> map = Maps.newEnumMap(EquipmentSlot.class);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            if (this.matchingSlot(equipmentSlot)) {
                ItemStack itemBySlot = entity.getItemBySlot(equipmentSlot);
                if (!itemBySlot.isEmpty()) {
                    map.put(equipmentSlot, itemBySlot);
                }
            }
        }

        return map;
    }

    public HolderSet<Item> getSupportedItems() {
        return this.definition.supportedItems();
    }

    public boolean matchingSlot(EquipmentSlot slot) {
        return this.definition.slots().stream().anyMatch(equipmentSlotGroup -> equipmentSlotGroup.test(slot));
    }

    public boolean isPrimaryItem(ItemStack stack) {
        return this.isSupportedItem(stack) && (this.definition.primaryItems.isEmpty() || stack.is(this.definition.primaryItems.get()));
    }

    public boolean isSupportedItem(ItemStack item) {
        return item.is(this.definition.supportedItems);
    }

    public int getWeight() {
        return this.definition.weight();
    }

    public int getAnvilCost() {
        return this.definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return this.definition.maxLevel();
    }

    public int getMinCost(int level) {
        return this.definition.minCost().calculate(level);
    }

    public int getMaxCost(int level) {
        return this.definition.maxCost().calculate(level);
    }

    @Override
    public String toString() {
        return "Enchantment " + this.description.getString();
    }

    public static boolean areCompatible(Holder<Enchantment> first, Holder<Enchantment> second) {
        return !first.equals(second) && !first.value().exclusiveSet.contains(second) && !second.value().exclusiveSet.contains(first);
    }

    public static Component getFullname(Holder<Enchantment> enchantment, int level) {
        MutableComponent mutableComponent = enchantment.value().description.copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            mutableComponent = ComponentUtils.mergeStyles(mutableComponent, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            mutableComponent = ComponentUtils.mergeStyles(mutableComponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
        }

        return mutableComponent;
    }

    public boolean canEnchant(ItemStack stack) {
        return this.definition.supportedItems().contains(stack.getItemHolder());
    }

    public <T> List<T> getEffects(DataComponentType<List<T>> component) {
        return this.effects.getOrDefault(component, List.of());
    }

    public boolean isImmuneToDamage(ServerLevel level, int enchantmentLevel, Entity entity, DamageSource damageSource) {
        LootContext lootContext = damageContext(level, enchantmentLevel, entity, damageSource);

        for (ConditionalEffect<DamageImmunity> conditionalEffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
            if (conditionalEffect.matches(lootContext)) {
                return true;
            }
        }

        return false;
    }

    public void modifyDamageProtection(
        ServerLevel level, int enchantmentLevel, ItemStack stack, Entity entity, DamageSource damageSource, MutableFloat damageProtection
    ) {
        LootContext lootContext = damageContext(level, enchantmentLevel, entity, damageSource);

        for (ConditionalEffect<EnchantmentValueEffect> conditionalEffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION)) {
            if (conditionalEffect.matches(lootContext)) {
                damageProtection.setValue(conditionalEffect.effect().process(enchantmentLevel, entity.getRandom(), damageProtection.floatValue()));
            }
        }
    }

    public void modifyDurabilityChange(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat durabilityChange) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.ITEM_DAMAGE, level, enchantmentLevel, tool, durabilityChange);
    }

    public void modifyAmmoCount(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat ammoCount) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.AMMO_USE, level, enchantmentLevel, tool, ammoCount);
    }

    public void modifyPiercingCount(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat piercingCount) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.PROJECTILE_PIERCING, level, enchantmentLevel, tool, piercingCount);
    }

    public void modifyBlockExperience(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat blockExperience) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.BLOCK_EXPERIENCE, level, enchantmentLevel, tool, blockExperience);
    }

    public void modifyMobExperience(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat mobExperience) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.MOB_EXPERIENCE, level, enchantmentLevel, tool, entity, mobExperience);
    }

    public void modifyDurabilityToRepairFromXp(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat durabilityToRepairFromXp) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.REPAIR_WITH_XP, level, enchantmentLevel, tool, durabilityToRepairFromXp);
    }

    public void modifyTridentReturnToOwnerAcceleration(
        ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat tridentReturnToOwnerAcceleration
    ) {
        this.modifyEntityFilteredValue(
            EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, level, enchantmentLevel, tool, entity, tridentReturnToOwnerAcceleration
        );
    }

    public void modifyTridentSpinAttackStrength(RandomSource random, int enchantmentLevel, MutableFloat value) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, random, enchantmentLevel, value);
    }

    public void modifyFishingTimeReduction(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat fishingTimeReduction) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, level, enchantmentLevel, tool, entity, fishingTimeReduction);
    }

    public void modifyFishingLuckBonus(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat fishingLuckBonus) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, level, enchantmentLevel, tool, entity, fishingLuckBonus);
    }

    public void modifyDamage(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat damage) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.DAMAGE, level, enchantmentLevel, tool, entity, damageSource, damage);
    }

    public void modifyFallBasedDamage(
        ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat fallBasedDamage
    ) {
        this.modifyDamageFilteredValue(
            EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, level, enchantmentLevel, tool, entity, damageSource, fallBasedDamage
        );
    }

    public void modifyKnockback(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat knockback) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.KNOCKBACK, level, enchantmentLevel, tool, entity, damageSource, knockback);
    }

    public void modifyArmorEffectivness(
        ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat armorEffectiveness
    ) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, level, enchantmentLevel, tool, entity, damageSource, armorEffectiveness);
    }

    public void doPostAttack(
        ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, EnchantmentTarget target, Entity entity, DamageSource damageSource
    ) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> targetedConditionalEffect : this.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (target == targetedConditionalEffect.enchanted()) {
                doPostAttack(targetedConditionalEffect, level, enchantmentLevel, item, entity, damageSource);
            }
        }
    }

    public static void doPostAttack(
        TargetedConditionalEffect<EnchantmentEntityEffect> effect,
        ServerLevel level,
        int enchantmentLevel,
        EnchantedItemInUse item,
        Entity entity,
        DamageSource damageSource
    ) {
        if (effect.matches(damageContext(level, enchantmentLevel, entity, damageSource))) {
            Entity entity1 = switch (effect.affected()) {
                case ATTACKER -> damageSource.getEntity();
                case DAMAGING_ENTITY -> damageSource.getDirectEntity();
                case VICTIM -> entity;
            };
            if (entity1 != null) {
                effect.effect().apply(level, enchantmentLevel, item, entity1, entity1.position());
            }
        }
    }

    public void doLunge(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            enchantmentEntityEffect -> enchantmentEntityEffect.apply(level, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void modifyProjectileCount(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat projectileCount) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_COUNT, level, enchantmentLevel, tool, entity, projectileCount);
    }

    public void modifyProjectileSpread(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat projectileSpread) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, level, enchantmentLevel, tool, entity, projectileSpread);
    }

    public void modifyCrossbowChargeTime(RandomSource random, int enchantmentLevel, MutableFloat value) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME, random, enchantmentLevel, value);
    }

    public void modifyUnfilteredValue(DataComponentType<EnchantmentValueEffect> component, RandomSource random, int enchantmentLevel, MutableFloat value) {
        EnchantmentValueEffect enchantmentValueEffect = this.effects.get(component);
        if (enchantmentValueEffect != null) {
            value.setValue(enchantmentValueEffect.process(enchantmentLevel, random, value.floatValue()));
        }
    }

    public void tick(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.TICK),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            enchantmentEntityEffect -> enchantmentEntityEffect.apply(level, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void onProjectileSpawned(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            enchantmentEntityEffect -> enchantmentEntityEffect.apply(level, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void onHitBlock(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 pos, BlockState state) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.HIT_BLOCK),
            blockHitContext(level, enchantmentLevel, entity, pos, state),
            enchantmentEntityEffect -> enchantmentEntityEffect.apply(level, enchantmentLevel, item, entity, pos)
        );
    }

    private void modifyItemFilteredCount(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(component),
            itemContext(level, enchantmentLevel, tool),
            enchantmentValueEffect -> value.setValue(enchantmentValueEffect.process(enchantmentLevel, level.getRandom(), value.floatValue()))
        );
    }

    private void modifyEntityFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        Entity entity,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(component),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            enchantmentValueEffect -> value.setValue(enchantmentValueEffect.process(enchantmentLevel, entity.getRandom(), value.floatValue()))
        );
    }

    private void modifyDamageFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        Entity entity,
        DamageSource damageSource,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(component),
            damageContext(level, enchantmentLevel, entity, damageSource),
            enchantmentValueEffect -> value.setValue(enchantmentValueEffect.process(enchantmentLevel, entity.getRandom(), value.floatValue()))
        );
    }

    public static LootContext damageContext(ServerLevel level, int enchantmentLevel, Entity entity, DamageSource damageSource) {
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity())
            .create(LootContextParamSets.ENCHANTED_DAMAGE);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext itemContext(ServerLevel level, int enchantmentLevel, ItemStack tool) {
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .create(LootContextParamSets.ENCHANTED_ITEM);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext locationContext(ServerLevel level, int enchantmentLevel, Entity entity, boolean enchantmentActive) {
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, enchantmentActive)
            .create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext entityContext(ServerLevel level, int enchantmentLevel, Entity entity, Vec3 origin) {
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, origin)
            .create(LootContextParamSets.ENCHANTED_ENTITY);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext blockHitContext(ServerLevel level, int enchantmentLevel, Entity entity, Vec3 origin, BlockState state) {
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, origin)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .create(LootContextParamSets.HIT_BLOCK);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static <T> void applyEffects(List<ConditionalEffect<T>> effects, LootContext context, Consumer<T> applier) {
        for (ConditionalEffect<T> conditionalEffect : effects) {
            if (conditionalEffect.matches(context)) {
                applier.accept(conditionalEffect.effect());
            }
        }
    }

    public void runLocationChangedEffects(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        EquipmentSlot equipmentSlot = item.inSlot();
        if (equipmentSlot != null) {
            Map<Enchantment, Set<EnchantmentLocationBasedEffect>> map = entity.activeLocationDependentEnchantments(equipmentSlot);
            if (!this.matchingSlot(equipmentSlot)) {
                Set<EnchantmentLocationBasedEffect> set = map.remove(this);
                if (set != null) {
                    set.forEach(effect -> effect.onDeactivated(item, entity, entity.position(), enchantmentLevel));
                }
            } else {
                Set<EnchantmentLocationBasedEffect> set = map.get(this);

                for (ConditionalEffect<EnchantmentLocationBasedEffect> conditionalEffect : this.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED)) {
                    EnchantmentLocationBasedEffect enchantmentLocationBasedEffect = conditionalEffect.effect();
                    boolean flag = set != null && set.contains(enchantmentLocationBasedEffect);
                    if (conditionalEffect.matches(locationContext(level, enchantmentLevel, entity, flag))) {
                        if (!flag) {
                            if (set == null) {
                                set = new ObjectArraySet<>();
                                map.put(this, set);
                            }

                            set.add(enchantmentLocationBasedEffect);
                        }

                        enchantmentLocationBasedEffect.onChangedBlock(level, enchantmentLevel, item, entity, entity.position(), !flag);
                    } else if (set != null && set.remove(enchantmentLocationBasedEffect)) {
                        enchantmentLocationBasedEffect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
                    }
                }

                if (set != null && set.isEmpty()) {
                    map.remove(this);
                }
            }
        }
    }

    public void stopLocationBasedEffects(int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        EquipmentSlot equipmentSlot = item.inSlot();
        if (equipmentSlot != null) {
            Set<EnchantmentLocationBasedEffect> set = entity.activeLocationDependentEnchantments(equipmentSlot).remove(this);
            if (set != null) {
                for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : set) {
                    enchantmentLocationBasedEffect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
                }
            }
        }
    }

    public static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition definition) {
        return new Enchantment.Builder(definition);
    }

    public static class Builder {
        private final Enchantment.EnchantmentDefinition definition;
        private HolderSet<Enchantment> exclusiveSet = HolderSet.direct();
        private final Map<DataComponentType<?>, List<?>> effectLists = new HashMap<>();
        private final DataComponentMap.Builder effectMapBuilder = DataComponentMap.builder();

        public Builder(Enchantment.EnchantmentDefinition definition) {
            this.definition = definition;
        }

        public Enchantment.Builder exclusiveWith(HolderSet<Enchantment> exclusiveSet) {
            this.exclusiveSet = exclusiveSet;
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> component, E effect, LootItemCondition.Builder requirements) {
            this.getEffectsList(component).add(new ConditionalEffect<>(effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> component, E effect) {
            this.getEffectsList(component).add(new ConditionalEffect<>(effect, Optional.empty()));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> component,
            EnchantmentTarget enchanted,
            EnchantmentTarget affected,
            E effect,
            LootItemCondition.Builder requirements
        ) {
            this.getEffectsList(component).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> component, EnchantmentTarget enchanted, EnchantmentTarget affected, E effect
        ) {
            this.getEffectsList(component).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.empty()));
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<List<EnchantmentAttributeEffect>> component, EnchantmentAttributeEffect effect) {
            this.getEffectsList(component).add(effect);
            return this;
        }

        public <E> Enchantment.Builder withSpecialEffect(DataComponentType<E> component, E value) {
            this.effectMapBuilder.set(component, value);
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<Unit> component) {
            this.effectMapBuilder.set(component, Unit.INSTANCE);
            return this;
        }

        private <E> List<E> getEffectsList(DataComponentType<List<E>> component) {
            return (List<E>)this.effectLists.computeIfAbsent(component, dataComponentType -> {
                ArrayList<E> list = new ArrayList<>();
                this.effectMapBuilder.set(component, list);
                return list;
            });
        }

        public Enchantment build(Identifier location) {
            return new Enchantment(
                Component.translatable(Util.makeDescriptionId("enchantment", location)), this.definition, this.exclusiveSet, this.effectMapBuilder.build()
            );
        }
    }

    public record Cost(int base, int perLevelAboveFirst) {
        public static final Codec<Enchantment.Cost> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
                    Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
                )
                .apply(instance, Enchantment.Cost::new)
        );

        public int calculate(int level) {
            return this.base + this.perLevelAboveFirst * (level - 1);
        }
    }

    public record EnchantmentDefinition(
        HolderSet<Item> supportedItems,
        Optional<HolderSet<Item>> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots
    ) {
        public static final MapCodec<Enchantment.EnchantmentDefinition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Enchantment.EnchantmentDefinition::supportedItems),
                    RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("primary_items").forGetter(Enchantment.EnchantmentDefinition::primaryItems),
                    ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Enchantment.EnchantmentDefinition::weight),
                    ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Enchantment.EnchantmentDefinition::maxLevel),
                    Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Enchantment.EnchantmentDefinition::minCost),
                    Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Enchantment.EnchantmentDefinition::maxCost),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Enchantment.EnchantmentDefinition::anvilCost),
                    EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Enchantment.EnchantmentDefinition::slots)
                )
                .apply(instance, Enchantment.EnchantmentDefinition::new)
        );
    }
}
