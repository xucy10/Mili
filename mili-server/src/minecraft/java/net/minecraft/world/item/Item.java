package net.minecraft.world.item;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Item implements FeatureElement, ItemLike {
    public static final Codec<Holder<Item>> CODEC = BuiltInRegistries.ITEM
        .holderByNameCodec()
        .validate(
            holder -> holder.is(Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> "Item must not be minecraft:air") : DataResult.success(holder)
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Map<Block, Item> BY_BLOCK = Maps.newHashMap();
    public static final Identifier BASE_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
    public static final Identifier BASE_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");
    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
    public static final int MAX_BAR_WIDTH = 13;
    protected static final int APPROXIMATELY_INFINITE_USE_DURATION = 72000;
    private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
    private final DataComponentMap components;
    private final @Nullable Item craftingRemainingItem;
    protected final String descriptionId;
    private final FeatureFlagSet requiredFeatures;

    public static int getId(Item item) {
        return item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
    }

    public static Item byId(int id) {
        return BuiltInRegistries.ITEM.byId(id);
    }

    @Deprecated
    public static Item byBlock(Block block) {
        return BY_BLOCK.getOrDefault(block, Items.AIR);
    }

    public Item(Item.Properties properties) {
        this.descriptionId = properties.effectiveDescriptionId();
        this.components = properties.buildAndValidateComponents(Component.translatable(this.descriptionId), properties.effectiveModel());
        this.craftingRemainingItem = properties.craftingRemainingItem;
        this.requiredFeatures = properties.requiredFeatures;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            String simpleName = this.getClass().getSimpleName();
            if (!simpleName.endsWith("Item")) {
                LOGGER.error("Item classes should end with Item and {} doesn't.", simpleName);
            }
        }
    }

    @Deprecated
    public Holder.Reference<Item> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public DataComponentMap components() {
        return this.components;
    }

    public int getDefaultMaxStackSize() {
        return this.components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
    }

    public void onDestroyed(ItemEntity itemEntity) {
    }

    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity entity) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool == null || tool.canDestroyBlocksInCreative() || !(entity instanceof Player player && player.getAbilities().instabuild);
    }

    @Override
    public Item asItem() {
        return this;
    }

    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null ? tool.getMiningSpeed(state) : 1.0F;
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        Consumable consumable = itemInHand.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            return consumable.startConsuming(player, itemInHand, hand);
        } else {
            Equippable equippable = itemInHand.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.swappable()) {
                return equippable.swapWithEquipmentSlot(itemInHand, player);
            } else if (itemInHand.has(DataComponents.BLOCKS_ATTACKS)) {
                player.startUsingItem(hand);
                return InteractionResult.CONSUME;
            } else {
                KineticWeapon kineticWeapon = itemInHand.get(DataComponents.KINETIC_WEAPON);
                if (kineticWeapon != null) {
                    player.startUsingItem(hand);
                    kineticWeapon.makeSound(player);
                    return InteractionResult.CONSUME;
                } else {
                    return InteractionResult.PASS;
                }
            }
        }
    }

    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        return consumable != null ? consumable.onConsume(level, livingEntity, stack) : stack;
    }

    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F - stack.getDamageValue() * 13.0F / stack.getMaxDamage()), 0, 13);
    }

    public int getBarColor(ItemStack stack) {
        int maxDamage = stack.getMaxDamage();
        float max = Math.max(0.0F, ((float)maxDamage - stack.getDamageValue()) / maxDamage);
        return Mth.hsvToRgb(max / 3.0F, 1.0F, 1.0F);
    }

    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        return false;
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return false;
    }

    public float getAttackDamageBonus(Entity target, float damageAmount, DamageSource damageSource) {
        return 0.0F;
    }

    @Deprecated
    public @Nullable DamageSource getItemDamageSource(LivingEntity entity) {
        return null;
    }

    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        } else {
            if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F && tool.damagePerBlock() > 0) {
                stack.hurtAndBreak(tool.damagePerBlock(), miningEntity, EquipmentSlot.MAINHAND);
            }

            return true;
        }
    }

    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(state);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        return InteractionResult.PASS;
    }

    @Override
    public String toString() {
        return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
    }

    public final ItemStack getCraftingRemainder() {
        return this.craftingRemainingItem == null ? ItemStack.EMPTY : new ItemStack(this.craftingRemainingItem);
    }

    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
    }

    public void onCraftedBy(ItemStack stack, Player player) {
        this.onCraftedPostProcess(stack, player.level());
    }

    public void onCraftedPostProcess(ItemStack stack, Level level) {
    }

    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            return consumable.animation();
        } else if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
            return ItemUseAnimation.BLOCK;
        } else {
            return stack.has(DataComponents.KINETIC_WEAPON) ? ItemUseAnimation.SPEAR : ItemUseAnimation.NONE;
        }
    }

    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            return consumable.consumeTicks();
        } else {
            return !stack.has(DataComponents.BLOCKS_ATTACKS) && !stack.has(DataComponents.KINETIC_WEAPON) ? 0 : 72000;
        }
    }

    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return false;
    }

    @Deprecated
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.empty();
    }

    @VisibleForTesting
    public final String getDescriptionId() {
        return this.descriptionId;
    }

    public final Component getName() {
        return this.components.getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
    }

    public Component getName(ItemStack stack) {
        return stack.getComponents().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
    }

    public boolean isFoil(ItemStack stack) {
        return stack.isEnchanted();
    }

    protected static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluidMode) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 vec3 = eyePosition.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        return level.clip(new ClipContext(eyePosition, vec3, ClipContext.Block.OUTLINE, fluidMode, player));
    }

    public boolean useOnRelease(ItemStack stack) {
        return false;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public boolean canFitInsideContainerItems() {
        return true;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        return false;
    }

    public static class Properties {
        private static final DependantName<Item, String> BLOCK_DESCRIPTION_ID = key -> Util.makeDescriptionId("block", key.identifier());
        private static final DependantName<Item, String> ITEM_DESCRIPTION_ID = key -> Util.makeDescriptionId("item", key.identifier());
        private final DataComponentMap.Builder components = DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS);
        @Nullable Item craftingRemainingItem;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        private @Nullable ResourceKey<Item> id;
        private DependantName<Item, String> descriptionId = ITEM_DESCRIPTION_ID;
        private final DependantName<Item, Identifier> model = ResourceKey::identifier;

        public Item.Properties food(FoodProperties food) {
            return this.food(food, Consumables.DEFAULT_FOOD);
        }

        public Item.Properties food(FoodProperties food, Consumable consumable) {
            return this.component(DataComponents.FOOD, food).component(DataComponents.CONSUMABLE, consumable);
        }

        public Item.Properties usingConvertsTo(Item usingConvertsTo) {
            return this.component(DataComponents.USE_REMAINDER, new UseRemainder(new ItemStack(usingConvertsTo)));
        }

        public Item.Properties useCooldown(float useCooldown) {
            return this.component(DataComponents.USE_COOLDOWN, new UseCooldown(useCooldown));
        }

        public Item.Properties stacksTo(int maxStackSize) {
            return this.component(DataComponents.MAX_STACK_SIZE, maxStackSize);
        }

        public Item.Properties durability(int maxDamage) {
            this.component(DataComponents.MAX_DAMAGE, maxDamage);
            this.component(DataComponents.MAX_STACK_SIZE, 1);
            this.component(DataComponents.DAMAGE, 0);
            return this;
        }

        public Item.Properties craftRemainder(Item craftingRemainingItem) {
            this.craftingRemainingItem = craftingRemainingItem;
            return this;
        }

        public Item.Properties rarity(Rarity rarity) {
            return this.component(DataComponents.RARITY, rarity);
        }

        public Item.Properties fireResistant() {
            return this.component(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
        }

        public Item.Properties jukeboxPlayable(ResourceKey<JukeboxSong> song) {
            return this.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(song)));
        }

        public Item.Properties enchantable(int enchantmentValue) {
            return this.component(DataComponents.ENCHANTABLE, new Enchantable(enchantmentValue));
        }

        public Item.Properties repairable(Item repairItem) {
            return this.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(repairItem.builtInRegistryHolder())));
        }

        public Item.Properties repairable(TagKey<Item> repairItems) {
            HolderGetter<Item> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ITEM);
            return this.component(DataComponents.REPAIRABLE, new Repairable(holderGetter.getOrThrow(repairItems)));
        }

        public Item.Properties equippable(EquipmentSlot slot) {
            return this.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).build());
        }

        public Item.Properties equippableUnswappable(EquipmentSlot slot) {
            return this.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setSwappable(false).build());
        }

        public Item.Properties tool(ToolMaterial material, TagKey<Block> mineableBlocks, float attackDamage, float attackSpeed, float disableBlockingForSeconds) {
            return material.applyToolProperties(this, mineableBlocks, attackDamage, attackSpeed, disableBlockingForSeconds);
        }

        public Item.Properties pickaxe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return this.tool(material, BlockTags.MINEABLE_WITH_PICKAXE, attackDamage, attackSpeed, 0.0F);
        }

        public Item.Properties axe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return this.tool(material, BlockTags.MINEABLE_WITH_AXE, attackDamage, attackSpeed, 5.0F);
        }

        public Item.Properties hoe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return this.tool(material, BlockTags.MINEABLE_WITH_HOE, attackDamage, attackSpeed, 0.0F);
        }

        public Item.Properties shovel(ToolMaterial material, float attackDamage, float attackSpeed) {
            return this.tool(material, BlockTags.MINEABLE_WITH_SHOVEL, attackDamage, attackSpeed, 0.0F);
        }

        public Item.Properties sword(ToolMaterial material, float attackDamage, float attackSpeed) {
            return material.applySwordProperties(this, attackDamage, attackSpeed);
        }

        public Item.Properties spear(
            ToolMaterial material,
            float swingDurationSeconds,
            float damageMultiplier,
            float delaySeconds,
            float dismountMaxDurationSeconds,
            float dismountMinSpeed,
            float knockbackMaxDurationSeconds,
            float knockbackMinSpeed,
            float damageMaxDurationSeconds,
            float damageMinRelativeSpeed
        ) {
            return this.durability(material.durability())
                .repairable(material.repairItems())
                .enchantable(material.enchantmentValue())
                .component(DataComponents.DAMAGE_TYPE, new EitherHolder<>(DamageTypes.SPEAR))
                .component(
                    DataComponents.KINETIC_WEAPON,
                    new KineticWeapon(
                        10,
                        (int)(delaySeconds * 20.0F),
                        KineticWeapon.Condition.ofAttackerSpeed((int)(dismountMaxDurationSeconds * 20.0F), dismountMinSpeed),
                        KineticWeapon.Condition.ofAttackerSpeed((int)(knockbackMaxDurationSeconds * 20.0F), knockbackMinSpeed),
                        KineticWeapon.Condition.ofRelativeSpeed((int)(damageMaxDurationSeconds * 20.0F), damageMinRelativeSpeed),
                        0.38F,
                        damageMultiplier,
                        Optional.of(material == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_USE : SoundEvents.SPEAR_USE),
                        Optional.of(material == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
                    )
                )
                .component(
                    DataComponents.PIERCING_WEAPON,
                    new PiercingWeapon(
                        true,
                        false,
                        Optional.of(material == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_ATTACK : SoundEvents.SPEAR_ATTACK),
                        Optional.of(material == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
                    )
                )
                .component(DataComponents.ATTACK_RANGE, new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F))
                .component(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0F)
                .component(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.STAB, (int)(swingDurationSeconds * 20.0F)))
                .attributes(
                    ItemAttributeModifiers.builder()
                        .add(
                            Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 0.0F + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND
                        )
                        .add(
                            Attributes.ATTACK_SPEED,
                            new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 1.0F / swingDurationSeconds - 4.0, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND
                        )
                        .build()
                )
                .component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F))
                .component(DataComponents.WEAPON, new Weapon(1));
        }

        public Item.Properties spawnEgg(EntityType<?> type) {
            return this.component(DataComponents.ENTITY_DATA, TypedEntityData.of(type, new CompoundTag()));
        }

        public Item.Properties humanoidArmor(ArmorMaterial material, ArmorType type) {
            return this.durability(type.getDurability(material.durability()))
                .attributes(material.createAttributes(type))
                .enchantable(material.enchantmentValue())
                .component(
                    DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot()).setEquipSound(material.equipSound()).setAsset(material.assetId()).build()
                )
                .repairable(material.repairIngredient());
        }

        public Item.Properties wolfArmor(ArmorMaterial material) {
            return this.durability(ArmorType.BODY.getDurability(material.durability()))
                .attributes(material.createAttributes(ArmorType.BODY))
                .repairable(material.repairIngredient())
                .component(
                    DataComponents.EQUIPPABLE,
                    Equippable.builder(EquipmentSlot.BODY)
                        .setEquipSound(material.equipSound())
                        .setAsset(material.assetId())
                        .setAllowedEntities(HolderSet.direct(EntityType.WOLF.builtInRegistryHolder()))
                        .setCanBeSheared(true)
                        .setShearingSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ARMOR_UNEQUIP_WOLF))
                        .build()
                )
                .component(DataComponents.BREAK_SOUND, SoundEvents.WOLF_ARMOR_BREAK)
                .stacksTo(1);
        }

        public Item.Properties horseArmor(ArmorMaterial material) {
            HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
            return this.attributes(material.createAttributes(ArmorType.BODY))
                .component(
                    DataComponents.EQUIPPABLE,
                    Equippable.builder(EquipmentSlot.BODY)
                        .setEquipSound(SoundEvents.HORSE_ARMOR)
                        .setAsset(material.assetId())
                        .setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_WEAR_HORSE_ARMOR))
                        .setDamageOnHurt(false)
                        .setCanBeSheared(true)
                        .setShearingSound(SoundEvents.HORSE_ARMOR_UNEQUIP)
                        .build()
                )
                .stacksTo(1);
        }

        public Item.Properties nautilusArmor(ArmorMaterial material) {
            HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
            return this.attributes(material.createAttributes(ArmorType.BODY))
                .component(
                    DataComponents.EQUIPPABLE,
                    Equippable.builder(EquipmentSlot.BODY)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_NAUTILUS)
                        .setAsset(material.assetId())
                        .setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_WEAR_NAUTILUS_ARMOR))
                        .setDamageOnHurt(false)
                        .setEquipOnInteract(true)
                        .setCanBeSheared(true)
                        .setShearingSound(SoundEvents.ARMOR_UNEQUIP_NAUTILUS)
                        .build()
                )
                .stacksTo(1);
        }

        public Item.Properties trimMaterial(ResourceKey<TrimMaterial> trimMaterial) {
            return this.component(DataComponents.PROVIDES_TRIM_MATERIAL, new ProvidesTrimMaterial(trimMaterial));
        }

        public Item.Properties requiredFeatures(FeatureFlag... requiredFeatures) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
            return this;
        }

        public Item.Properties setId(ResourceKey<Item> id) {
            this.id = id;
            return this;
        }

        public Item.Properties overrideDescription(String description) {
            this.descriptionId = DependantName.fixed(description);
            return this;
        }

        public Item.Properties useBlockDescriptionPrefix() {
            this.descriptionId = BLOCK_DESCRIPTION_ID;
            return this;
        }

        public Item.Properties useItemDescriptionPrefix() {
            this.descriptionId = ITEM_DESCRIPTION_ID;
            return this;
        }

        protected String effectiveDescriptionId() {
            return this.descriptionId.get(Objects.requireNonNull(this.id, "Item id not set"));
        }

        public Identifier effectiveModel() {
            return this.model.get(Objects.requireNonNull(this.id, "Item id not set"));
        }

        public <T> Item.Properties component(DataComponentType<T> component, T value) {
            this.components.set(component, value);
            return this;
        }

        public Item.Properties attributes(ItemAttributeModifiers attributes) {
            return this.component(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        }

        DataComponentMap buildAndValidateComponents(Component itemName, Identifier itemModel) {
            DataComponentMap dataComponentMap = this.components.set(DataComponents.ITEM_NAME, itemName).set(DataComponents.ITEM_MODEL, itemModel).build();
            if (dataComponentMap.has(DataComponents.DAMAGE) && dataComponentMap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
                throw new IllegalStateException("Item cannot have both durability and be stackable");
            } else {
                return dataComponentMap;
            }
        }
    }

    public interface TooltipContext {
        Item.TooltipContext EMPTY = new Item.TooltipContext() {
            @Override
            public HolderLookup.@Nullable Provider registries() {
                return null;
            }

            @Override
            public float tickRate() {
                return 20.0F;
            }

            @Override
            public @Nullable MapItemSavedData mapData(MapId mapId) {
                return null;
            }

            @Override
            public boolean isPeaceful() {
                return false;
            }
        };

        HolderLookup.@Nullable Provider registries();

        float tickRate();

        @Nullable MapItemSavedData mapData(MapId mapId);

        boolean isPeaceful();

        static Item.TooltipContext of(final @Nullable Level level) {
            return level == null ? EMPTY : new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return level.registryAccess();
                }

                @Override
                public float tickRate() {
                    return level.tickRateManager().tickrate();
                }

                @Override
                public MapItemSavedData mapData(MapId mapId) {
                    return level.getMapData(mapId);
                }

                @Override
                public boolean isPeaceful() {
                    return level.getDifficulty() == Difficulty.PEACEFUL;
                }
            };
        }

        static Item.TooltipContext of(final HolderLookup.Provider registries) {
            return new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return registries;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Override
                public @Nullable MapItemSavedData mapData(MapId mapId) {
                    return null;
                }

                @Override
                public boolean isPeaceful() {
                    return false;
                }
            };
        }
    }
}
