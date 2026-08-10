package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.EncoderCache;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.LockCode;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.saveddata.maps.MapId;

public class DataComponents {
    static final EncoderCache ENCODER_CACHE = new EncoderCache(512);
    public static final DataComponentType<CustomData> CUSTOM_DATA = register("custom_data", builder -> builder.persistent(CustomData.CODEC));
    public static final DataComponentType<Integer> MAX_STACK_SIZE = register(
        "max_stack_size", builder -> builder.persistent(ExtraCodecs.intRange(1, 99)).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Integer> MAX_DAMAGE = register(
        "max_damage", builder -> builder.persistent(ExtraCodecs.POSITIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Integer> DAMAGE = register(
        "damage", builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).ignoreSwapAnimation().networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Unit> UNBREAKABLE = register(
        "unbreakable", builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC)
    );
    public static final DataComponentType<UseEffects> USE_EFFECTS = register(
        "use_effects", builder -> builder.persistent(UseEffects.CODEC).networkSynchronized(UseEffects.STREAM_CODEC)
    );
    public static final DataComponentType<Component> CUSTOM_NAME = register(
        "custom_name", builder -> builder.persistent(ComponentSerialization.CODEC).networkSynchronized(ComponentSerialization.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Float> MINIMUM_ATTACK_CHARGE = register(
        "minimum_attack_charge", builder -> builder.persistent(ExtraCodecs.floatRange(0.0F, 1.0F)).networkSynchronized(ByteBufCodecs.FLOAT)
    );
    public static final DataComponentType<EitherHolder<DamageType>> DAMAGE_TYPE = register(
        "damage_type",
        builder -> builder.persistent(EitherHolder.codec(Registries.DAMAGE_TYPE, DamageType.CODEC))
            .networkSynchronized(EitherHolder.streamCodec(Registries.DAMAGE_TYPE, DamageType.STREAM_CODEC))
    );
    public static final DataComponentType<Component> ITEM_NAME = register(
        "item_name", builder -> builder.persistent(ComponentSerialization.CODEC).networkSynchronized(ComponentSerialization.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Identifier> ITEM_MODEL = register(
        "item_model", builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemLore> LORE = register(
        "lore", builder -> builder.persistent(ItemLore.CODEC).networkSynchronized(ItemLore.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Rarity> RARITY = register(
        "rarity", builder -> builder.persistent(Rarity.CODEC).networkSynchronized(Rarity.STREAM_CODEC)
    );
    public static final DataComponentType<ItemEnchantments> ENCHANTMENTS = register(
        "enchantments", builder -> builder.persistent(ItemEnchantments.CODEC).networkSynchronized(ItemEnchantments.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<AdventureModePredicate> CAN_PLACE_ON = register(
        "can_place_on", builder -> builder.persistent(AdventureModePredicate.CODEC).networkSynchronized(AdventureModePredicate.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<AdventureModePredicate> CAN_BREAK = register(
        "can_break", builder -> builder.persistent(AdventureModePredicate.CODEC).networkSynchronized(AdventureModePredicate.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS = register(
        "attribute_modifiers",
        builder -> builder.persistent(ItemAttributeModifiers.CODEC).networkSynchronized(ItemAttributeModifiers.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA = register(
        "custom_model_data", builder -> builder.persistent(CustomModelData.CODEC).networkSynchronized(CustomModelData.STREAM_CODEC)
    );
    public static final DataComponentType<TooltipDisplay> TOOLTIP_DISPLAY = register(
        "tooltip_display", builder -> builder.persistent(TooltipDisplay.CODEC).networkSynchronized(TooltipDisplay.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Integer> REPAIR_COST = register(
        "repair_cost", builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Unit> CREATIVE_SLOT_LOCK = register("creative_slot_lock", builder -> builder.networkSynchronized(Unit.STREAM_CODEC));
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE = register(
        "enchantment_glint_override", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
    );
    public static final DataComponentType<Unit> INTANGIBLE_PROJECTILE = register("intangible_projectile", builder -> builder.persistent(Unit.CODEC));
    public static final DataComponentType<FoodProperties> FOOD = register(
        "food", builder -> builder.persistent(FoodProperties.DIRECT_CODEC).networkSynchronized(FoodProperties.DIRECT_STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Consumable> CONSUMABLE = register(
        "consumable", builder -> builder.persistent(Consumable.CODEC).networkSynchronized(Consumable.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<UseRemainder> USE_REMAINDER = register(
        "use_remainder", builder -> builder.persistent(UseRemainder.CODEC).networkSynchronized(UseRemainder.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<UseCooldown> USE_COOLDOWN = register(
        "use_cooldown", builder -> builder.persistent(UseCooldown.CODEC).networkSynchronized(UseCooldown.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DamageResistant> DAMAGE_RESISTANT = register(
        "damage_resistant", builder -> builder.persistent(DamageResistant.CODEC).networkSynchronized(DamageResistant.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Tool> TOOL = register(
        "tool", builder -> builder.persistent(Tool.CODEC).networkSynchronized(Tool.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Weapon> WEAPON = register(
        "weapon", builder -> builder.persistent(Weapon.CODEC).networkSynchronized(Weapon.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<AttackRange> ATTACK_RANGE = register(
        "attack_range", builder -> builder.persistent(AttackRange.CODEC).networkSynchronized(AttackRange.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Enchantable> ENCHANTABLE = register(
        "enchantable", builder -> builder.persistent(Enchantable.CODEC).networkSynchronized(Enchantable.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Equippable> EQUIPPABLE = register(
        "equippable", builder -> builder.persistent(Equippable.CODEC).networkSynchronized(Equippable.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Repairable> REPAIRABLE = register(
        "repairable", builder -> builder.persistent(Repairable.CODEC).networkSynchronized(Repairable.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Unit> GLIDER = register("glider", builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));
    public static final DataComponentType<Identifier> TOOLTIP_STYLE = register(
        "tooltip_style", builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DeathProtection> DEATH_PROTECTION = register(
        "death_protection", builder -> builder.persistent(DeathProtection.CODEC).networkSynchronized(DeathProtection.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<BlocksAttacks> BLOCKS_ATTACKS = register(
        "blocks_attacks", builder -> builder.persistent(BlocksAttacks.CODEC).networkSynchronized(BlocksAttacks.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<PiercingWeapon> PIERCING_WEAPON = register(
        "piercing_weapon", builder -> builder.persistent(PiercingWeapon.CODEC).networkSynchronized(PiercingWeapon.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<KineticWeapon> KINETIC_WEAPON = register(
        "kinetic_weapon", builder -> builder.persistent(KineticWeapon.CODEC).networkSynchronized(KineticWeapon.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<SwingAnimation> SWING_ANIMATION = register(
        "swing_animation", builder -> builder.persistent(SwingAnimation.CODEC).networkSynchronized(SwingAnimation.STREAM_CODEC)
    );
    public static final DataComponentType<ItemEnchantments> STORED_ENCHANTMENTS = register(
        "stored_enchantments", builder -> builder.persistent(ItemEnchantments.CODEC).networkSynchronized(ItemEnchantments.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DyedItemColor> DYED_COLOR = register(
        "dyed_color", builder -> builder.persistent(DyedItemColor.CODEC).networkSynchronized(DyedItemColor.STREAM_CODEC)
    );
    public static final DataComponentType<MapItemColor> MAP_COLOR = register(
        "map_color", builder -> builder.persistent(MapItemColor.CODEC).networkSynchronized(MapItemColor.STREAM_CODEC)
    );
    public static final DataComponentType<MapId> MAP_ID = register("map_id", builder -> builder.persistent(MapId.CODEC).networkSynchronized(MapId.STREAM_CODEC));
    public static final DataComponentType<MapDecorations> MAP_DECORATIONS = register(
        "map_decorations", builder -> builder.persistent(MapDecorations.CODEC).cacheEncoding()
    );
    public static final DataComponentType<MapPostProcessing> MAP_POST_PROCESSING = register(
        "map_post_processing", builder -> builder.networkSynchronized(MapPostProcessing.STREAM_CODEC)
    );
    public static final DataComponentType<ChargedProjectiles> CHARGED_PROJECTILES = register(
        "charged_projectiles", builder -> builder.persistent(ChargedProjectiles.CODEC).networkSynchronized(io.papermc.paper.util.sanitizer.OversizedItemComponentSanitizer.CHARGED_PROJECTILES).cacheEncoding() // Paper - sanitize charged projectiles
    );
    public static final DataComponentType<BundleContents> BUNDLE_CONTENTS = register(
        "bundle_contents", builder -> builder.persistent(BundleContents.CODEC).networkSynchronized(io.papermc.paper.util.sanitizer.OversizedItemComponentSanitizer.BUNDLE_CONTENTS).cacheEncoding() // Paper - sanitize bundle contents
    );
    public static final DataComponentType<PotionContents> POTION_CONTENTS = register(
        "potion_contents", builder -> builder.persistent(PotionContents.CODEC).networkSynchronized(PotionContents.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Float> POTION_DURATION_SCALE = register(
        "potion_duration_scale", builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).cacheEncoding()
    );
    public static final DataComponentType<SuspiciousStewEffects> SUSPICIOUS_STEW_EFFECTS = register(
        "suspicious_stew_effects",
        builder -> builder.persistent(SuspiciousStewEffects.CODEC).networkSynchronized(SuspiciousStewEffects.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<WritableBookContent> WRITABLE_BOOK_CONTENT = register(
        "writable_book_content", builder -> builder.persistent(WritableBookContent.CODEC).networkSynchronized(WritableBookContent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<WrittenBookContent> WRITTEN_BOOK_CONTENT = register(
        "written_book_content", builder -> builder.persistent(WrittenBookContent.CODEC).networkSynchronized(WrittenBookContent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ArmorTrim> TRIM = register(
        "trim", builder -> builder.persistent(ArmorTrim.CODEC).networkSynchronized(ArmorTrim.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DebugStickState> DEBUG_STICK_STATE = register(
        "debug_stick_state", builder -> builder.persistent(DebugStickState.CODEC).cacheEncoding()
    );
    public static final DataComponentType<TypedEntityData<EntityType<?>>> ENTITY_DATA = register(
        "entity_data",
        builder -> builder.persistent(TypedEntityData.codec(EntityType.CODEC)).networkSynchronized(TypedEntityData.streamCodec(EntityType.STREAM_CODEC))
    );
    public static final DataComponentType<CustomData> BUCKET_ENTITY_DATA = register(
        "bucket_entity_data", builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC)
    );
    public static final DataComponentType<TypedEntityData<BlockEntityType<?>>> BLOCK_ENTITY_DATA = register(
        "block_entity_data",
        builder -> builder.persistent(TypedEntityData.codec(BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec()))
            .networkSynchronized(TypedEntityData.streamCodec(ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE)))
    );
    public static final DataComponentType<InstrumentComponent> INSTRUMENT = register(
        "instrument", builder -> builder.persistent(InstrumentComponent.CODEC).networkSynchronized(InstrumentComponent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ProvidesTrimMaterial> PROVIDES_TRIM_MATERIAL = register(
        "provides_trim_material",
        builder -> builder.persistent(ProvidesTrimMaterial.CODEC).networkSynchronized(ProvidesTrimMaterial.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<OminousBottleAmplifier> OMINOUS_BOTTLE_AMPLIFIER = register(
        "ominous_bottle_amplifier", builder -> builder.persistent(OminousBottleAmplifier.CODEC).networkSynchronized(OminousBottleAmplifier.STREAM_CODEC)
    );
    public static final DataComponentType<JukeboxPlayable> JUKEBOX_PLAYABLE = register(
        "jukebox_playable", builder -> builder.persistent(JukeboxPlayable.CODEC).networkSynchronized(JukeboxPlayable.STREAM_CODEC)
    );
    public static final DataComponentType<TagKey<BannerPattern>> PROVIDES_BANNER_PATTERNS = register(
        "provides_banner_patterns",
        builder -> builder.persistent(TagKey.hashedCodec(Registries.BANNER_PATTERN))
            .networkSynchronized(TagKey.streamCodec(Registries.BANNER_PATTERN))
            .cacheEncoding()
    );
    public static final DataComponentType<List<ResourceKey<Recipe<?>>>> RECIPES = register(
        "recipes", builder -> builder.persistent(Recipe.KEY_CODEC.listOf()).cacheEncoding()
    );
    public static final DataComponentType<LodestoneTracker> LODESTONE_TRACKER = register(
        "lodestone_tracker", builder -> builder.persistent(LodestoneTracker.CODEC).networkSynchronized(LodestoneTracker.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<FireworkExplosion> FIREWORK_EXPLOSION = register(
        "firework_explosion", builder -> builder.persistent(FireworkExplosion.CODEC).networkSynchronized(FireworkExplosion.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Fireworks> FIREWORKS = register(
        "fireworks", builder -> builder.persistent(Fireworks.CODEC).networkSynchronized(Fireworks.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ResolvableProfile> PROFILE = register(
        "profile", builder -> builder.persistent(ResolvableProfile.CODEC).networkSynchronized(ResolvableProfile.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Identifier> NOTE_BLOCK_SOUND = register(
        "note_block_sound", builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC)
    );
    public static final DataComponentType<BannerPatternLayers> BANNER_PATTERNS = register(
        "banner_patterns", builder -> builder.persistent(BannerPatternLayers.CODEC).networkSynchronized(BannerPatternLayers.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DyeColor> BASE_COLOR = register(
        "base_color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<PotDecorations> POT_DECORATIONS = register(
        "pot_decorations", builder -> builder.persistent(PotDecorations.CODEC).networkSynchronized(PotDecorations.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemContainerContents> CONTAINER = register(
        "container", builder -> builder.persistent(ItemContainerContents.CODEC).networkSynchronized(io.papermc.paper.util.sanitizer.OversizedItemComponentSanitizer.CONTAINER).cacheEncoding() // Paper - sanitize container contents
    );
    public static final DataComponentType<BlockItemStateProperties> BLOCK_STATE = register(
        "block_state", builder -> builder.persistent(BlockItemStateProperties.CODEC).networkSynchronized(BlockItemStateProperties.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Bees> BEES = register(
        "bees", builder -> builder.persistent(Bees.CODEC).networkSynchronized(Bees.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<LockCode> LOCK = register("lock", builder -> builder.persistent(LockCode.CODEC));
    public static final DataComponentType<SeededContainerLoot> CONTAINER_LOOT = register(
        "container_loot", builder -> builder.persistent(SeededContainerLoot.CODEC)
    );
    public static final DataComponentType<Holder<SoundEvent>> BREAK_SOUND = register(
        "break_sound", builder -> builder.persistent(SoundEvent.CODEC).networkSynchronized(SoundEvent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Holder<VillagerType>> VILLAGER_VARIANT = register(
        "villager/variant", builder -> builder.persistent(VillagerType.CODEC).networkSynchronized(VillagerType.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<WolfVariant>> WOLF_VARIANT = register(
        "wolf/variant", builder -> builder.persistent(WolfVariant.CODEC).networkSynchronized(WolfVariant.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<WolfSoundVariant>> WOLF_SOUND_VARIANT = register(
        "wolf/sound_variant", builder -> builder.persistent(WolfSoundVariant.CODEC).networkSynchronized(WolfSoundVariant.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> WOLF_COLLAR = register(
        "wolf/collar", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<Fox.Variant> FOX_VARIANT = register(
        "fox/variant", builder -> builder.persistent(Fox.Variant.CODEC).networkSynchronized(Fox.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Salmon.Variant> SALMON_SIZE = register(
        "salmon/size", builder -> builder.persistent(Salmon.Variant.CODEC).networkSynchronized(Salmon.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Parrot.Variant> PARROT_VARIANT = register(
        "parrot/variant", builder -> builder.persistent(Parrot.Variant.CODEC).networkSynchronized(Parrot.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<TropicalFish.Pattern> TROPICAL_FISH_PATTERN = register(
        "tropical_fish/pattern", builder -> builder.persistent(TropicalFish.Pattern.CODEC).networkSynchronized(TropicalFish.Pattern.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> TROPICAL_FISH_BASE_COLOR = register(
        "tropical_fish/base_color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> TROPICAL_FISH_PATTERN_COLOR = register(
        "tropical_fish/pattern_color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<MushroomCow.Variant> MOOSHROOM_VARIANT = register(
        "mooshroom/variant", builder -> builder.persistent(MushroomCow.Variant.CODEC).networkSynchronized(MushroomCow.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Rabbit.Variant> RABBIT_VARIANT = register(
        "rabbit/variant", builder -> builder.persistent(Rabbit.Variant.CODEC).networkSynchronized(Rabbit.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<PigVariant>> PIG_VARIANT = register(
        "pig/variant", builder -> builder.persistent(PigVariant.CODEC).networkSynchronized(PigVariant.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<CowVariant>> COW_VARIANT = register(
        "cow/variant", builder -> builder.persistent(CowVariant.CODEC).networkSynchronized(CowVariant.STREAM_CODEC)
    );
    public static final DataComponentType<EitherHolder<ChickenVariant>> CHICKEN_VARIANT = register(
        "chicken/variant",
        builder -> builder.persistent(EitherHolder.codec(Registries.CHICKEN_VARIANT, ChickenVariant.CODEC))
            .networkSynchronized(EitherHolder.streamCodec(Registries.CHICKEN_VARIANT, ChickenVariant.STREAM_CODEC))
    );
    public static final DataComponentType<EitherHolder<ZombieNautilusVariant>> ZOMBIE_NAUTILUS_VARIANT = register(
        "zombie_nautilus/variant",
        builder -> builder.persistent(EitherHolder.codec(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.CODEC))
            .networkSynchronized(EitherHolder.streamCodec(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.STREAM_CODEC))
    );
    public static final DataComponentType<Holder<FrogVariant>> FROG_VARIANT = register(
        "frog/variant", builder -> builder.persistent(FrogVariant.CODEC).networkSynchronized(FrogVariant.STREAM_CODEC)
    );
    public static final DataComponentType<Variant> HORSE_VARIANT = register(
        "horse/variant", builder -> builder.persistent(Variant.CODEC).networkSynchronized(Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<PaintingVariant>> PAINTING_VARIANT = register(
        "painting/variant", builder -> builder.persistent(PaintingVariant.CODEC).networkSynchronized(PaintingVariant.STREAM_CODEC)
    );
    public static final DataComponentType<Llama.Variant> LLAMA_VARIANT = register(
        "llama/variant", builder -> builder.persistent(Llama.Variant.CODEC).networkSynchronized(Llama.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Axolotl.Variant> AXOLOTL_VARIANT = register(
        "axolotl/variant", builder -> builder.persistent(Axolotl.Variant.CODEC).networkSynchronized(Axolotl.Variant.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<CatVariant>> CAT_VARIANT = register(
        "cat/variant", builder -> builder.persistent(CatVariant.CODEC).networkSynchronized(CatVariant.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> CAT_COLLAR = register(
        "cat/collar", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> SHEEP_COLOR = register(
        "sheep/color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<DyeColor> SHULKER_COLOR = register(
        "shulker/color", builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentMap COMMON_ITEM_COMPONENTS = DataComponentMap.builder()
        .set(MAX_STACK_SIZE, 64)
        .set(LORE, ItemLore.EMPTY)
        .set(ENCHANTMENTS, ItemEnchantments.EMPTY)
        .set(REPAIR_COST, 0)
        .set(USE_EFFECTS, UseEffects.DEFAULT)
        .set(ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        .set(RARITY, Rarity.COMMON)
        .set(BREAK_SOUND, SoundEvents.ITEM_BREAK)
        .set(TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
        .set(SWING_ANIMATION, SwingAnimation.DEFAULT)
        .build();

    public static DataComponentType<?> bootstrap(Registry<DataComponentType<?>> registry) {
        return CUSTOM_DATA;
    }

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, name, builder.apply(DataComponentType.builder()).build());
    }
}
