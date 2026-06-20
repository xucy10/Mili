package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class EnchantmentHelper {
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return itemEnchantments.getLevel(enchantment);
    }

    public static ItemEnchantments updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> updater) {
    // Paper start - allowing updating enchantments on items without component
        return updateEnchantments(stack, updater, false);
    }

    public static ItemEnchantments updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> updater, final boolean createComponentIfMissing) {
    // Paper end - allowing updating enchantments on items without component
        DataComponentType<ItemEnchantments> componentType = getComponentType(stack);
        ItemEnchantments itemEnchantments = createComponentIfMissing ? stack.getOrDefault(componentType, ItemEnchantments.EMPTY) : stack.get(componentType); // Paper - allowing updating enchantments on items without component
        if (itemEnchantments == null) {
            return ItemEnchantments.EMPTY;
        } else {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(itemEnchantments);
            updater.accept(mutable);
            ItemEnchantments itemEnchantments1 = mutable.toImmutable();
            stack.set(componentType, itemEnchantments1);
            return itemEnchantments1;
        }
    }

    public static boolean canStoreEnchantments(ItemStack stack) {
        return stack.has(getComponentType(stack));
    }

    public static void setEnchantments(ItemStack stack, ItemEnchantments enchantments) {
        stack.set(getComponentType(stack), enchantments);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack stack) {
        return stack.getOrDefault(getComponentType(stack), ItemEnchantments.EMPTY);
    }

    private static DataComponentType<ItemEnchantments> getComponentType(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
            || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel level, ItemStack stack, int damage) {
        MutableFloat mutableFloat = new MutableFloat(damage);
        runIterationOnItem(stack, (enchantment, enchantmentLevel) -> enchantment.value().modifyDurabilityChange(level, enchantmentLevel, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processAmmoUse(ServerLevel level, ItemStack weapon, ItemStack ammo, int count) {
        MutableFloat mutableFloat = new MutableFloat(count);
        runIterationOnItem(weapon, (enchantment, level1) -> enchantment.value().modifyAmmoCount(level, level1, ammo, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processBlockExperience(ServerLevel level, ItemStack stack, int experience) {
        MutableFloat mutableFloat = new MutableFloat(experience);
        runIterationOnItem(stack, (enchantment, level1) -> enchantment.value().modifyBlockExperience(level, level1, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processMobExperience(ServerLevel level, @Nullable Entity killer, Entity mob, int experience) {
        if (killer instanceof LivingEntity livingEntity) {
            MutableFloat mutableFloat = new MutableFloat(experience);
            runIterationOnEquipment(
                livingEntity, (enchantment, level1, item) -> enchantment.value().modifyMobExperience(level, level1, item.itemStack(), mob, mutableFloat)
            );
            return mutableFloat.intValue();
        } else {
            return experience;
        }
    }

    public static ItemStack createBook(EnchantmentInstance enchantment) {
        ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        itemStack.enchant(enchantment.enchantment(), enchantment.level());
        return itemStack;
    }

    private static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor visitor) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            visitor.accept(entry.getKey(), entry.getIntValue());
        }
    }

    private static void runIterationOnItem(ItemStack stack, EquipmentSlot slot, LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
        if (!stack.isEmpty()) {
            ItemEnchantments itemEnchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (itemEnchantments != null && !itemEnchantments.isEmpty()) {
                EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, entity);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().matchingSlot(slot)) {
                        visitor.accept(holder, entry.getIntValue(), enchantedItemInUse);
                    }
                }
            }
        }
    }

    private static void runIterationOnEquipment(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            runIterationOnItem(entity.getItemBySlot(equipmentSlot), equipmentSlot, entity, visitor);
        }
    }

    public static boolean isImmuneToDamage(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableBoolean mutableBoolean = new MutableBoolean();
        runIterationOnEquipment(
            entity,
            (enchantment, level1, item) -> mutableBoolean.setValue(
                mutableBoolean.isTrue() || enchantment.value().isImmuneToDamage(level, level1, entity, damageSource)
            )
        );
        return mutableBoolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnEquipment(
            entity,
            (enchantment, level1, item) -> enchantment.value().modifyDamageProtection(level, level1, item.itemStack(), entity, damageSource, mutableFloat)
        );
        return mutableFloat.floatValue();
    }

    public static float modifyDamage(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float damage) {
        MutableFloat mutableFloat = new MutableFloat(damage);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyDamage(level, level1, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float fallBasedDamage) {
        MutableFloat mutableFloat = new MutableFloat(fallBasedDamage);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyFallBasedDamage(level, level1, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float armorEffectiveness) {
        MutableFloat mutableFloat = new MutableFloat(armorEffectiveness);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyArmorEffectivness(level, level1, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float knockback) {
        MutableFloat mutableFloat = new MutableFloat(knockback);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyKnockback(level, level1, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel level, Entity entity, DamageSource damageSource) {
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, livingEntity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, null);
        }
    }

    public static void doLungeEffects(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            runIterationOnItem(
                entity.getWeaponItem(),
                EquipmentSlot.MAINHAND,
                livingEntity,
                (enchantment, level1, item) -> enchantment.value().doLunge(level, level1, item, entity)
            );
        }
    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel level, Entity entity, DamageSource damageSource, @Nullable ItemStack itemSource) {
        doPostAttackEffectsWithItemSourceOnBreak(level, entity, damageSource, itemSource, null);
    }

    public static void doPostAttackEffectsWithItemSourceOnBreak(
        ServerLevel level, Entity entity, DamageSource damageSource, @Nullable ItemStack itemSource, @Nullable Consumer<Item> onBreak
    ) {
        if (entity instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(
                livingEntity,
                (enchantment, level1, item) -> enchantment.value().doPostAttack(level, level1, item, EnchantmentTarget.VICTIM, entity, damageSource)
            );
        }

        if (itemSource != null) {
            if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
                runIterationOnItem(
                    itemSource,
                    EquipmentSlot.MAINHAND,
                    livingEntity,
                    (enchantment, level1, item) -> enchantment.value().doPostAttack(level, level1, item, EnchantmentTarget.ATTACKER, entity, damageSource)
                );
            } else if (onBreak != null) {
                EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(itemSource, null, null, onBreak);
                runIterationOnItem(
                    itemSource,
                    (enchantment, level1) -> enchantment.value()
                        .doPostAttack(level, level1, enchantedItemInUse, EnchantmentTarget.ATTACKER, entity, damageSource)
                );
            }
        }
    }

    public static void runLocationChangedEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(entity, (enchantment, level1, item) -> enchantment.value().runLocationChangedEffects(level, level1, item, entity));
    }

    public static void runLocationChangedEffects(ServerLevel level, ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, entity, (enchantment, level1, item) -> enchantment.value().runLocationChangedEffects(level, level1, item, entity));
    }

    public static void stopLocationBasedEffects(LivingEntity entity) {
        runIterationOnEquipment(entity, (enchantment, level, item) -> enchantment.value().stopLocationBasedEffects(level, item, entity));
    }

    public static void stopLocationBasedEffects(ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, entity, (enchantment, level, item) -> enchantment.value().stopLocationBasedEffects(level, item, entity));
    }

    public static void tickEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(entity, (enchantment, level1, item) -> enchantment.value().tick(level, level1, item, entity));
    }

    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        Iterable<ItemStack> iterable = enchantment.value().getSlotItems(entity).values();
        int i = 0;

        for (ItemStack itemStack : iterable) {
            int itemEnchantmentLevel = getItemEnchantmentLevel(enchantment, itemStack);
            if (itemEnchantmentLevel > i) {
                i = itemEnchantmentLevel;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel level, ItemStack tool, Entity entity, int projectileCount) {
        MutableFloat mutableFloat = new MutableFloat(projectileCount);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyProjectileCount(level, level1, tool, entity, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel level, ItemStack tool, Entity entity, float projectileSpread) {
        MutableFloat mutableFloat = new MutableFloat(projectileSpread);
        runIterationOnItem(tool, (enchantment, level1) -> enchantment.value().modifyProjectileSpread(level, level1, tool, entity, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel level, ItemStack firedFromWeapon, ItemStack pickupItemStack) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(firedFromWeapon, (enchantment, level1) -> enchantment.value().modifyPiercingCount(level, level1, pickupItemStack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel level, ItemStack firedFromWeapon, Projectile projectile, Consumer<Item> onBreak) {
        LivingEntity livingEntity1 = projectile.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null;
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(firedFromWeapon, null, livingEntity1, onBreak);
        runIterationOnItem(firedFromWeapon, (enchantment, level1) -> enchantment.value().onProjectileSpawned(level, level1, enchantedItemInUse, projectile));
    }

    public static void onHitBlock(
        ServerLevel level,
        ItemStack stack,
        @Nullable LivingEntity owner,
        Entity entity,
        @Nullable EquipmentSlot slot,
        Vec3 pos,
        BlockState state,
        Consumer<Item> onBreak
    ) {
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, owner, onBreak);
        runIterationOnItem(stack, (enchantment, level1) -> enchantment.value().onHitBlock(level, level1, enchantedItemInUse, entity, pos, state));
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel level, ItemStack stack, int durabilityToRepairFromXp) {
        MutableFloat mutableFloat = new MutableFloat(durabilityToRepairFromXp);
        runIterationOnItem(stack, (enchantment, level1) -> enchantment.value().modifyDurabilityToRepairFromXp(level, level1, stack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel level, LivingEntity entity, DamageSource damageSource, float equipmentDropChance) {
        MutableFloat mutableFloat = new MutableFloat(equipmentDropChance);
        RandomSource random = entity.getRandom();
        runIterationOnEquipment(
            entity,
            (enchantment, level1, item) -> {
                LootContext lootContext = Enchantment.damageContext(level, level1, entity, damageSource);
                enchantment.value()
                    .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                    .forEach(
                        targetedConditionalEffect -> {
                            if (targetedConditionalEffect.enchanted() == EnchantmentTarget.VICTIM
                                && targetedConditionalEffect.affected() == EnchantmentTarget.VICTIM
                                && targetedConditionalEffect.matches(lootContext)) {
                                mutableFloat.setValue(targetedConditionalEffect.effect().process(level1, random, mutableFloat.floatValue()));
                            }
                        }
                    );
            }
        );
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(
                livingEntity,
                (enchantment, level1, item) -> {
                    LootContext lootContext = Enchantment.damageContext(level, level1, entity, damageSource);
                    enchantment.value()
                        .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                        .forEach(
                            targetedConditionalEffect -> {
                                if (targetedConditionalEffect.enchanted() == EnchantmentTarget.ATTACKER
                                    && targetedConditionalEffect.affected() == EnchantmentTarget.VICTIM
                                    && targetedConditionalEffect.matches(lootContext)) {
                                    mutableFloat.setValue(targetedConditionalEffect.effect().process(level1, random, mutableFloat.floatValue()));
                                }
                            }
                        );
                }
            );
        }

        return mutableFloat.floatValue();
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlotGroup slotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(
            stack, (enchantment, level) -> enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(enchantmentAttributeEffect -> {
                if (((Enchantment)enchantment.value()).definition().slots().contains(slotGroup)) {
                    action.accept(enchantmentAttributeEffect.attribute(), enchantmentAttributeEffect.getModifier(level, slotGroup));
                }
            })
        );
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(
            stack, (enchantment, level) -> enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(enchantmentAttributeEffect -> {
                if (((Enchantment)enchantment.value()).matchingSlot(slot)) {
                    action.accept(enchantmentAttributeEffect.attribute(), enchantmentAttributeEffect.getModifier(level, slot));
                }
            })
        );
    }

    public static int getFishingLuckBonus(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level1) -> enchantment.value().modifyFishingLuckBonus(level, level1, stack, entity, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level1) -> enchantment.value().modifyFishingTimeReduction(level, level1, stack, entity, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(
            stack, (enchantment, level1) -> enchantment.value().modifyTridentReturnToOwnerAcceleration(level, level1, stack, entity, mutableFloat)
        );
        return Math.max(0, mutableFloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack stack, LivingEntity entity, float crossbowChargingTime) {
        MutableFloat mutableFloat = new MutableFloat(crossbowChargingTime);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyCrossbowChargeTime(entity.getRandom(), level, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack stack, LivingEntity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyTridentSpinAttackStrength(entity.getRandom(), level, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static boolean hasTag(ItemStack stack, TagKey<Enchantment> tag) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> component) {
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        runIterationOnItem(stack, (enchantment, level) -> {
            if (enchantment.value().effects().has(component)) {
                mutableBoolean.setTrue();
            }
        });
        return mutableBoolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack stack, DataComponentType<List<T>> component) {
        Pair<List<T>, Integer> highestLevel = getHighestLevel(stack, component);
        if (highestLevel != null) {
            List<T> list = highestLevel.getFirst();
            int second = highestLevel.getSecond();
            return Optional.of(list.get(Math.min(second, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    public static <T> Pair<T, Integer> getHighestLevel(ItemStack stack, DataComponentType<T> component) {
        MutableObject<Pair<T, Integer>> mutableObject = new MutableObject<>();
        runIterationOnItem(stack, (enchantment, level) -> {
            if (mutableObject.get() == null || mutableObject.get().getSecond() < level) {
                T object = enchantment.value().effects().get(component);
                if (object != null) {
                    mutableObject.setValue(Pair.of(object, level));
                }
            }
        });
        return mutableObject.get();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> component, LivingEntity entity, Predicate<ItemStack> filter) {
        List<EnchantedItemInUse> list = new ArrayList<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = entity.getItemBySlot(equipmentSlot);
            if (filter.test(itemBySlot)) {
                ItemEnchantments itemEnchantments = itemBySlot.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().effects().has(component) && holder.value().matchingSlot(equipmentSlot)) {
                        list.add(new EnchantedItemInUse(itemBySlot, equipmentSlot, entity));
                    }
                }
            }
        }

        return Util.getRandomSafe(list, entity.getRandom());
    }

    public static int getEnchantmentCost(RandomSource random, int enchantNum, int power, ItemStack stack) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable == null) {
            return 0;
        } else {
            if (power > 15) {
                power = 15;
            }

            int i = random.nextInt(8) + 1 + (power >> 1) + random.nextInt(power + 1);
            if (enchantNum == 0) {
                return Math.max(i / 3, 1);
            } else {
                return enchantNum == 1 ? i * 2 / 3 + 1 : Math.max(i, power * 2);
            }
        }
    }

    public static ItemStack enchantItem(
        RandomSource random, ItemStack stack, int level, RegistryAccess registryAccess, Optional<? extends HolderSet<Enchantment>> possibleEnchantments
    ) {
        return enchantItem(
            random,
            stack,
            level,
            possibleEnchantments.map(HolderSet::stream)
                .orElseGet(() -> registryAccess.lookupOrThrow(Registries.ENCHANTMENT).listElements().map(reference -> (Holder<Enchantment>)reference))
        );
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = selectEnchantment(random, stack, level, possibleEnchantments);
        if (stack.is(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentInstance : list) {
            stack.enchant(enchantmentInstance.enchantment(), enchantmentInstance.level());
        }

        return stack;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable == null) {
            return list;
        } else {
            level += 1 + random.nextInt(enchantable.value() / 4 + 1) + random.nextInt(enchantable.value() / 4 + 1);
            float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
            level = Mth.clamp(Math.round(level + level * f), 1, Integer.MAX_VALUE);
            List<EnchantmentInstance> availableEnchantmentResults = getAvailableEnchantmentResults(level, stack, possibleEnchantments);
            if (!availableEnchantmentResults.isEmpty()) {
                WeightedRandom.getRandomItem(random, availableEnchantmentResults, EnchantmentInstance::weight).ifPresent(list::add);

                while (random.nextInt(50) <= level) {
                    if (!list.isEmpty()) {
                        filterCompatibleEnchantments(availableEnchantmentResults, list.getLast());
                    }

                    if (availableEnchantmentResults.isEmpty()) {
                        break;
                    }

                    WeightedRandom.getRandomItem(random, availableEnchantmentResults, EnchantmentInstance::weight).ifPresent(list::add);
                    level /= 2;
                }
            }

            return list;
        }
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> dataList, EnchantmentInstance data) {
        dataList.removeIf(enchantmentInstance -> !Enchantment.areCompatible(data.enchantment(), enchantmentInstance.enchantment()));
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> currentEnchantments, Holder<Enchantment> newEnchantment) {
        for (Holder<Enchantment> holder : currentEnchantments) {
            if (!Enchantment.areCompatible(holder, newEnchantment)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int level, ItemStack stack, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean isBook = stack.is(Items.BOOK);
        possibleEnchantments.filter(holder -> holder.value().isPrimaryItem(stack) || isBook).forEach(holder -> {
            Enchantment enchantment = holder.value();

            for (int level1 = enchantment.getMaxLevel(); level1 >= enchantment.getMinLevel(); level1--) {
                if (level >= enchantment.getMinCost(level1) && level <= enchantment.getMaxCost(level1)) {
                    list.add(new EnchantmentInstance((Holder<Enchantment>)holder, level1));
                    break;
                }
            }
        });
        return list;
    }

    public static void enchantItemFromProvider(
        ItemStack stack, RegistryAccess registries, ResourceKey<EnchantmentProvider> key, DifficultyInstance difficulty, RandomSource random
    ) {
        EnchantmentProvider enchantmentProvider = registries.lookupOrThrow(Registries.ENCHANTMENT_PROVIDER).getValue(key);
        if (enchantmentProvider != null) {
            updateEnchantments(stack, mutable -> enchantmentProvider.enchant(stack, mutable, random, difficulty));
        }
    }

    @FunctionalInterface
    interface EnchantmentInSlotVisitor {
        void accept(Holder<Enchantment> enchantment, int level, EnchantedItemInUse item);
    }

    @FunctionalInterface
    interface EnchantmentVisitor {
        void accept(Holder<Enchantment> enchantment, int level);
    }
}
