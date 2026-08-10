package net.minecraft.data.advancements.packs;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.ChanneledLightningTrigger;
import net.minecraft.advancements.criterion.DamagePredicate;
import net.minecraft.advancements.criterion.DamageSourcePredicate;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.DistancePredicate;
import net.minecraft.advancements.criterion.DistanceTrigger;
import net.minecraft.advancements.criterion.EntityEquipmentPredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.FallAfterExplosionTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.criterion.KilledByArrowTrigger;
import net.minecraft.advancements.criterion.KilledTrigger;
import net.minecraft.advancements.criterion.LightningBoltPredicate;
import net.minecraft.advancements.criterion.LightningStrikeTrigger;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.advancements.criterion.LootTableTrigger;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.PlayerHurtEntityTrigger;
import net.minecraft.advancements.criterion.PlayerInteractTrigger;
import net.minecraft.advancements.criterion.PlayerPredicate;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.advancements.criterion.RecipeCraftedTrigger;
import net.minecraft.advancements.criterion.ShotCrossbowTrigger;
import net.minecraft.advancements.criterion.SlideDownBlockTrigger;
import net.minecraft.advancements.criterion.SpearMobsTrigger;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.advancements.criterion.SummonedEntityTrigger;
import net.minecraft.advancements.criterion.TagPredicate;
import net.minecraft.advancements.criterion.TargetBlockTrigger;
import net.minecraft.advancements.criterion.TradeTrigger;
import net.minecraft.advancements.criterion.UsedTotemTrigger;
import net.minecraft.advancements.criterion.UsingItemTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.JukeboxPlayablePredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class VanillaAdventureAdvancements implements AdvancementSubProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DISTANCE_FROM_BOTTOM_TO_TOP = 384;
    private static final int Y_COORDINATE_AT_TOP = 320;
    private static final int Y_COORDINATE_AT_BOTTOM = -64;
    private static final int BEDROCK_THICKNESS = 5;
    private static final Map<MobCategory, Set<EntityType<?>>> EXCEPTIONS_BY_EXPECTED_CATEGORIES = Map.of(
        MobCategory.MONSTER, Set.of(EntityType.GIANT, EntityType.ILLUSIONER, EntityType.WARDEN)
    );
    private static final List<EntityType<?>> MOBS_TO_KILL = Arrays.asList(
        EntityType.BLAZE,
        EntityType.BOGGED,
        EntityType.BREEZE,
        EntityType.CAMEL_HUSK,
        EntityType.CAVE_SPIDER,
        EntityType.CREAKING,
        EntityType.CREEPER,
        EntityType.DROWNED,
        EntityType.ELDER_GUARDIAN,
        EntityType.ENDER_DRAGON,
        EntityType.ENDERMAN,
        EntityType.ENDERMITE,
        EntityType.EVOKER,
        EntityType.GHAST,
        EntityType.GUARDIAN,
        EntityType.HOGLIN,
        EntityType.HUSK,
        EntityType.MAGMA_CUBE,
        EntityType.PARCHED,
        EntityType.PHANTOM,
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.PILLAGER,
        EntityType.RAVAGER,
        EntityType.SHULKER,
        EntityType.SILVERFISH,
        EntityType.SKELETON,
        EntityType.SLIME,
        EntityType.SPIDER,
        EntityType.STRAY,
        EntityType.VEX,
        EntityType.VINDICATOR,
        EntityType.WITCH,
        EntityType.WITHER_SKELETON,
        EntityType.WITHER,
        EntityType.ZOGLIN,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.ZOMBIE,
        EntityType.ZOMBIE_HORSE,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.ZOMBIE_NAUTILUS
    );

    private static Criterion<LightningStrikeTrigger.TriggerInstance> fireCountAndBystander(MinMaxBounds.Ints fireCount, Optional<EntityPredicate> bystander) {
        return LightningStrikeTrigger.TriggerInstance.lightningStrike(
            Optional.of(
                EntityPredicate.Builder.entity()
                    .distance(DistancePredicate.absolute(MinMaxBounds.Doubles.atMost(30.0)))
                    .subPredicate(LightningBoltPredicate.blockSetOnFire(fireCount))
                    .build()
            ),
            bystander
        );
    }

    private static Criterion<UsingItemTrigger.TriggerInstance> lookAtThroughItem(EntityPredicate.Builder builder, ItemPredicate.Builder item) {
        return UsingItemTrigger.TriggerInstance.lookingAt(
            EntityPredicate.Builder.entity().subPredicate(PlayerPredicate.Builder.player().setLookingAt(builder).build()), item
        );
    }

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> writer) {
        HolderLookup<EntityType<?>> holderLookup = registries.lookupOrThrow(Registries.ENTITY_TYPE);
        HolderLookup<Item> holderLookup1 = registries.lookupOrThrow(Registries.ITEM);
        HolderLookup<Block> holderLookup2 = registries.lookupOrThrow(Registries.BLOCK);
        AdvancementHolder advancementHolder = Advancement.Builder.advancement()
            .display(
                Items.MAP,
                Component.translatable("advancements.adventure.root.title"),
                Component.translatable("advancements.adventure.root.description"),
                Identifier.withDefaultNamespace("gui/advancements/backgrounds/adventure"),
                AdvancementType.TASK,
                false,
                false,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .addCriterion("killed_something", KilledTrigger.TriggerInstance.playerKilledEntity())
            .addCriterion("killed_by_something", KilledTrigger.TriggerInstance.entityKilledPlayer())
            .save(writer, "adventure/root");
        AdvancementHolder advancementHolder1 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Blocks.RED_BED,
                Component.translatable("advancements.adventure.sleep_in_bed.title"),
                Component.translatable("advancements.adventure.sleep_in_bed.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion("slept_in_bed", PlayerTrigger.TriggerInstance.sleptInBed())
            .save(writer, "adventure/sleep_in_bed");
        createAdventuringTime(registries, writer, advancementHolder1, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);
        AdvancementHolder advancementHolder2 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.EMERALD,
                Component.translatable("advancements.adventure.trade.title"),
                Component.translatable("advancements.adventure.trade.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion("traded", TradeTrigger.TriggerInstance.tradedWithVillager())
            .save(writer, "adventure/trade");
        Advancement.Builder.advancement()
            .parent(advancementHolder2)
            .display(
                Items.EMERALD,
                Component.translatable("advancements.adventure.trade_at_world_height.title"),
                Component.translatable("advancements.adventure.trade_at_world_height.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "trade_at_world_height",
                TradeTrigger.TriggerInstance.tradedWithVillager(
                    EntityPredicate.Builder.entity().located(LocationPredicate.Builder.atYLocation(MinMaxBounds.Doubles.atLeast(319.0)))
                )
            )
            .save(writer, "adventure/trade_at_world_height");
        AdvancementHolder advancementHolder3 = createMonsterHunterAdvancement(
            advancementHolder, writer, holderLookup, validateMobsToKill(MOBS_TO_KILL, holderLookup)
        );
        AdvancementHolder advancementHolder4 = Advancement.Builder.advancement()
            .parent(advancementHolder3)
            .display(
                Items.BOW,
                Component.translatable("advancements.adventure.shoot_arrow.title"),
                Component.translatable("advancements.adventure.shoot_arrow.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "shot_arrow",
                PlayerHurtEntityTrigger.TriggerInstance.playerHurtEntityWithDamage(
                    DamagePredicate.Builder.damageInstance()
                        .type(
                            DamageSourcePredicate.Builder.damageType()
                                .tag(TagPredicate.is(DamageTypeTags.IS_PROJECTILE))
                                .direct(EntityPredicate.Builder.entity().of(holderLookup, EntityTypeTags.ARROWS))
                        )
                )
            )
            .save(writer, "adventure/shoot_arrow");
        AdvancementHolder advancementHolder5 = Advancement.Builder.advancement()
            .parent(advancementHolder3)
            .display(
                Items.TRIDENT,
                Component.translatable("advancements.adventure.throw_trident.title"),
                Component.translatable("advancements.adventure.throw_trident.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "shot_trident",
                PlayerHurtEntityTrigger.TriggerInstance.playerHurtEntityWithDamage(
                    DamagePredicate.Builder.damageInstance()
                        .type(
                            DamageSourcePredicate.Builder.damageType()
                                .tag(TagPredicate.is(DamageTypeTags.IS_PROJECTILE))
                                .direct(EntityPredicate.Builder.entity().of(holderLookup, EntityType.TRIDENT))
                        )
                )
            )
            .save(writer, "adventure/throw_trident");
        Advancement.Builder.advancement()
            .parent(advancementHolder5)
            .display(
                Items.TRIDENT,
                Component.translatable("advancements.adventure.very_very_frightening.title"),
                Component.translatable("advancements.adventure.very_very_frightening.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "struck_villager",
                ChanneledLightningTrigger.TriggerInstance.channeledLightning(EntityPredicate.Builder.entity().of(holderLookup, EntityType.VILLAGER))
            )
            .save(writer, "adventure/very_very_frightening");
        Advancement.Builder.advancement()
            .parent(advancementHolder2)
            .display(
                Blocks.CARVED_PUMPKIN,
                Component.translatable("advancements.adventure.summon_iron_golem.title"),
                Component.translatable("advancements.adventure.summon_iron_golem.description"),
                null,
                AdvancementType.GOAL,
                true,
                true,
                false
            )
            .addCriterion(
                "summoned_golem",
                SummonedEntityTrigger.TriggerInstance.summonedEntity(EntityPredicate.Builder.entity().of(holderLookup, EntityType.IRON_GOLEM))
            )
            .save(writer, "adventure/summon_iron_golem");
        Advancement.Builder.advancement()
            .parent(advancementHolder4)
            .display(
                Items.ARROW,
                Component.translatable("advancements.adventure.sniper_duel.title"),
                Component.translatable("advancements.adventure.sniper_duel.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(50))
            .addCriterion(
                "killed_skeleton",
                KilledTrigger.TriggerInstance.playerKilledEntity(
                    EntityPredicate.Builder.entity()
                        .of(holderLookup, EntityType.SKELETON)
                        .distance(DistancePredicate.horizontal(MinMaxBounds.Doubles.atLeast(50.0))),
                    DamageSourcePredicate.Builder.damageType().tag(TagPredicate.is(DamageTypeTags.IS_PROJECTILE))
                )
            )
            .save(writer, "adventure/sniper_duel");
        Advancement.Builder.advancement()
            .parent(advancementHolder3)
            .display(
                Items.TOTEM_OF_UNDYING,
                Component.translatable("advancements.adventure.totem_of_undying.title"),
                Component.translatable("advancements.adventure.totem_of_undying.description"),
                null,
                AdvancementType.GOAL,
                true,
                true,
                false
            )
            .addCriterion("used_totem", UsedTotemTrigger.TriggerInstance.usedTotem(holderLookup1, Items.TOTEM_OF_UNDYING))
            .save(writer, "adventure/totem_of_undying");
        Advancement.Builder.advancement()
            .parent(advancementHolder3)
            .display(
                Items.IRON_SPEAR,
                Component.translatable("advancements.adventure.spear_many_mobs.title"),
                Component.translatable("advancements.adventure.spear_many_mobs.description"),
                null,
                AdvancementType.GOAL,
                true,
                true,
                false
            )
            .addCriterion("spear_many_mobs", SpearMobsTrigger.TriggerInstance.spearMobs(5))
            .save(writer, "adventure/spear_many_mobs");
        AdvancementHolder advancementHolder6 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.CROSSBOW,
                Component.translatable("advancements.adventure.ol_betsy.title"),
                Component.translatable("advancements.adventure.ol_betsy.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion("shot_crossbow", ShotCrossbowTrigger.TriggerInstance.shotCrossbow(holderLookup1, Items.CROSSBOW))
            .save(writer, "adventure/ol_betsy");
        Advancement.Builder.advancement()
            .parent(advancementHolder6)
            .display(
                Items.CROSSBOW,
                Component.translatable("advancements.adventure.whos_the_pillager_now.title"),
                Component.translatable("advancements.adventure.whos_the_pillager_now.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "kill_pillager",
                KilledByArrowTrigger.TriggerInstance.crossbowKilled(holderLookup1, EntityPredicate.Builder.entity().of(holderLookup, EntityType.PILLAGER))
            )
            .save(writer, "adventure/whos_the_pillager_now");
        Advancement.Builder.advancement()
            .parent(advancementHolder6)
            .display(
                Items.CROSSBOW,
                Component.translatable("advancements.adventure.two_birds_one_arrow.title"),
                Component.translatable("advancements.adventure.two_birds_one_arrow.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(65))
            .addCriterion(
                "two_birds",
                KilledByArrowTrigger.TriggerInstance.crossbowKilled(
                    holderLookup1,
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.PHANTOM),
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.PHANTOM)
                )
            )
            .save(writer, "adventure/two_birds_one_arrow");
        Advancement.Builder.advancement()
            .parent(advancementHolder6)
            .display(
                Items.CROSSBOW,
                Component.translatable("advancements.adventure.arbalistic.title"),
                Component.translatable("advancements.adventure.arbalistic.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                true
            )
            .rewards(AdvancementRewards.Builder.experience(85))
            .addCriterion("arbalistic", KilledByArrowTrigger.TriggerInstance.crossbowKilled(holderLookup1, MinMaxBounds.Ints.exactly(5)))
            .save(writer, "adventure/arbalistic");
        HolderLookup.RegistryLookup<BannerPattern> registryLookup = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        AdvancementHolder advancementHolder7 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Raid.getOminousBannerInstance(registryLookup),
                Component.translatable("advancements.adventure.voluntary_exile.title"),
                Component.translatable("advancements.adventure.voluntary_exile.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                true
            )
            .addCriterion(
                "voluntary_exile",
                KilledTrigger.TriggerInstance.playerKilledEntity(
                    EntityPredicate.Builder.entity()
                        .of(holderLookup, EntityTypeTags.RAIDERS)
                        .equipment(EntityEquipmentPredicate.captainPredicate(holderLookup1, registryLookup))
                )
            )
            .save(writer, "adventure/voluntary_exile");
        Advancement.Builder.advancement()
            .parent(advancementHolder7)
            .display(
                Raid.getOminousBannerInstance(registryLookup),
                Component.translatable("advancements.adventure.hero_of_the_village.title"),
                Component.translatable("advancements.adventure.hero_of_the_village.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                true
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .addCriterion("hero_of_the_village", PlayerTrigger.TriggerInstance.raidWon())
            .save(writer, "adventure/hero_of_the_village");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Blocks.HONEY_BLOCK.asItem(),
                Component.translatable("advancements.adventure.honey_block_slide.title"),
                Component.translatable("advancements.adventure.honey_block_slide.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion("honey_block_slide", SlideDownBlockTrigger.TriggerInstance.slidesDownBlock(Blocks.HONEY_BLOCK))
            .save(writer, "adventure/honey_block_slide");
        Advancement.Builder.advancement()
            .parent(advancementHolder4)
            .display(
                Blocks.TARGET.asItem(),
                Component.translatable("advancements.adventure.bullseye.title"),
                Component.translatable("advancements.adventure.bullseye.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(50))
            .addCriterion(
                "bullseye",
                TargetBlockTrigger.TriggerInstance.targetHit(
                    MinMaxBounds.Ints.exactly(15),
                    Optional.of(
                        EntityPredicate.wrap(EntityPredicate.Builder.entity().distance(DistancePredicate.horizontal(MinMaxBounds.Doubles.atLeast(30.0))))
                    )
                )
            )
            .save(writer, "adventure/bullseye");
        Advancement.Builder.advancement()
            .parent(advancementHolder1)
            .display(
                Items.LEATHER_BOOTS,
                Component.translatable("advancements.adventure.walk_on_powder_snow_with_leather_boots.title"),
                Component.translatable("advancements.adventure.walk_on_powder_snow_with_leather_boots.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "walk_on_powder_snow_with_leather_boots",
                PlayerTrigger.TriggerInstance.walkOnBlockWithEquipment(holderLookup2, holderLookup1, Blocks.POWDER_SNOW, Items.LEATHER_BOOTS)
            )
            .save(writer, "adventure/walk_on_powder_snow_with_leather_boots");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.LIGHTNING_ROD,
                Component.translatable("advancements.adventure.lightning_rod_with_villager_no_fire.title"),
                Component.translatable("advancements.adventure.lightning_rod_with_villager_no_fire.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "lightning_rod_with_villager_no_fire",
                fireCountAndBystander(MinMaxBounds.Ints.exactly(0), Optional.of(EntityPredicate.Builder.entity().of(holderLookup, EntityType.VILLAGER).build()))
            )
            .save(writer, "adventure/lightning_rod_with_villager_no_fire");
        AdvancementHolder advancementHolder8 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.SPYGLASS,
                Component.translatable("advancements.adventure.spyglass_at_parrot.title"),
                Component.translatable("advancements.adventure.spyglass_at_parrot.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "spyglass_at_parrot",
                lookAtThroughItem(
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.PARROT), ItemPredicate.Builder.item().of(holderLookup1, Items.SPYGLASS)
                )
            )
            .save(writer, "adventure/spyglass_at_parrot");
        AdvancementHolder advancementHolder9 = Advancement.Builder.advancement()
            .parent(advancementHolder8)
            .display(
                Items.SPYGLASS,
                Component.translatable("advancements.adventure.spyglass_at_ghast.title"),
                Component.translatable("advancements.adventure.spyglass_at_ghast.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "spyglass_at_ghast",
                lookAtThroughItem(
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.GHAST), ItemPredicate.Builder.item().of(holderLookup1, Items.SPYGLASS)
                )
            )
            .save(writer, "adventure/spyglass_at_ghast");
        Advancement.Builder.advancement()
            .parent(advancementHolder1)
            .display(
                Items.JUKEBOX,
                Component.translatable("advancements.adventure.play_jukebox_in_meadows.title"),
                Component.translatable("advancements.adventure.play_jukebox_in_meadows.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "play_jukebox_in_meadows",
                ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                    LocationPredicate.Builder.location()
                        .setBiomes(HolderSet.direct(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.MEADOW)))
                        .setBlock(BlockPredicate.Builder.block().of(holderLookup2, Blocks.JUKEBOX)),
                    ItemPredicate.Builder.item()
                        .withComponents(
                            DataComponentMatchers.Builder.components()
                                .partial(DataComponentPredicates.JUKEBOX_PLAYABLE, JukeboxPlayablePredicate.any())
                                .build()
                        )
                )
            )
            .save(writer, "adventure/play_jukebox_in_meadows");
        Advancement.Builder.advancement()
            .parent(advancementHolder9)
            .display(
                Items.SPYGLASS,
                Component.translatable("advancements.adventure.spyglass_at_dragon.title"),
                Component.translatable("advancements.adventure.spyglass_at_dragon.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "spyglass_at_dragon",
                lookAtThroughItem(
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.ENDER_DRAGON), ItemPredicate.Builder.item().of(holderLookup1, Items.SPYGLASS)
                )
            )
            .save(writer, "adventure/spyglass_at_dragon");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.WATER_BUCKET,
                Component.translatable("advancements.adventure.fall_from_world_height.title"),
                Component.translatable("advancements.adventure.fall_from_world_height.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "fall_from_world_height",
                DistanceTrigger.TriggerInstance.fallFromHeight(
                    EntityPredicate.Builder.entity().located(LocationPredicate.Builder.atYLocation(MinMaxBounds.Doubles.atMost(-59.0))),
                    DistancePredicate.vertical(MinMaxBounds.Doubles.atLeast(379.0)),
                    LocationPredicate.Builder.atYLocation(MinMaxBounds.Doubles.atLeast(319.0))
                )
            )
            .save(writer, "adventure/fall_from_world_height");
        Advancement.Builder.advancement()
            .parent(advancementHolder3)
            .display(
                Blocks.SCULK_CATALYST,
                Component.translatable("advancements.adventure.kill_mob_near_sculk_catalyst.title"),
                Component.translatable("advancements.adventure.kill_mob_near_sculk_catalyst.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .addCriterion("kill_mob_near_sculk_catalyst", KilledTrigger.TriggerInstance.playerKilledEntityNearSculkCatalyst())
            .save(writer, "adventure/kill_mob_near_sculk_catalyst");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Blocks.SCULK_SENSOR,
                Component.translatable("advancements.adventure.avoid_vibration.title"),
                Component.translatable("advancements.adventure.avoid_vibration.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion("avoid_vibration", PlayerTrigger.TriggerInstance.avoidVibration())
            .save(writer, "adventure/avoid_vibration");
        AdvancementHolder advancementHolder10 = respectingTheRemnantsCriterions(holderLookup1, Advancement.Builder.advancement())
            .parent(advancementHolder)
            .display(
                Items.BRUSH,
                Component.translatable("advancements.adventure.salvage_sherd.title"),
                Component.translatable("advancements.adventure.salvage_sherd.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(writer, "adventure/salvage_sherd");
        Advancement.Builder.advancement()
            .parent(advancementHolder10)
            .display(
                DecoratedPotBlockEntity.createDecoratedPotItem(
                    new PotDecorations(Optional.empty(), Optional.of(Items.HEART_POTTERY_SHERD), Optional.empty(), Optional.of(Items.EXPLORER_POTTERY_SHERD))
                ),
                Component.translatable("advancements.adventure.craft_decorated_pot_using_only_sherds.title"),
                Component.translatable("advancements.adventure.craft_decorated_pot_using_only_sherds.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "pot_crafted_using_only_sherds",
                RecipeCraftedTrigger.TriggerInstance.craftedItem(
                    ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("decorated_pot")),
                    List.of(
                        ItemPredicate.Builder.item().of(holderLookup1, ItemTags.DECORATED_POT_SHERDS),
                        ItemPredicate.Builder.item().of(holderLookup1, ItemTags.DECORATED_POT_SHERDS),
                        ItemPredicate.Builder.item().of(holderLookup1, ItemTags.DECORATED_POT_SHERDS),
                        ItemPredicate.Builder.item().of(holderLookup1, ItemTags.DECORATED_POT_SHERDS)
                    )
                )
            )
            .save(writer, "adventure/craft_decorated_pot_using_only_sherds");
        AdvancementHolder advancementHolder11 = craftingANewLook(Advancement.Builder.advancement())
            .parent(advancementHolder)
            .display(
                new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
                Component.translatable("advancements.adventure.trim_with_any_armor_pattern.title"),
                Component.translatable("advancements.adventure.trim_with_any_armor_pattern.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(writer, "adventure/trim_with_any_armor_pattern");
        smithingWithStyle(Advancement.Builder.advancement())
            .parent(advancementHolder11)
            .display(
                new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
                Component.translatable("advancements.adventure.trim_with_all_exclusive_armor_patterns.title"),
                Component.translatable("advancements.adventure.trim_with_all_exclusive_armor_patterns.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(150))
            .save(writer, "adventure/trim_with_all_exclusive_armor_patterns");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.CHISELED_BOOKSHELF,
                Component.translatable("advancements.adventure.read_power_from_chiseled_bookshelf.title"),
                Component.translatable("advancements.adventure.read_power_from_chiseled_bookshelf.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .addCriterion("chiseled_bookshelf", placedBlockReadByComparator(holderLookup2, Blocks.CHISELED_BOOKSHELF))
            .addCriterion("comparator", placedComparatorReadingBlock(holderLookup2, Blocks.CHISELED_BOOKSHELF))
            .save(writer, "adventure/read_power_of_chiseled_bookshelf");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.ARMADILLO_SCUTE,
                Component.translatable("advancements.adventure.brush_armadillo.title"),
                Component.translatable("advancements.adventure.brush_armadillo.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "brush_armadillo",
                PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(
                    ItemPredicate.Builder.item().of(holderLookup1, Items.BRUSH),
                    Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity().of(holderLookup, EntityType.ARMADILLO)))
                )
            )
            .save(writer, "adventure/brush_armadillo");
        AdvancementHolder advancementHolder12 = Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Blocks.CHISELED_TUFF,
                Component.translatable("advancements.adventure.minecraft_trials_edition.title"),
                Component.translatable("advancements.adventure.minecraft_trials_edition.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "minecraft_trials_edition",
                PlayerTrigger.TriggerInstance.located(
                    LocationPredicate.Builder.inStructure(registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.TRIAL_CHAMBERS))
                )
            )
            .save(writer, "adventure/minecraft_trials_edition");
        Advancement.Builder.advancement()
            .parent(advancementHolder12)
            .display(
                Items.COPPER_BULB,
                Component.translatable("advancements.adventure.lighten_up.title"),
                Component.translatable("advancements.adventure.lighten_up.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "lighten_up",
                ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                    LocationPredicate.Builder.location()
                        .setBlock(
                            BlockPredicate.Builder.block()
                                .of(
                                    holderLookup2,
                                    Blocks.OXIDIZED_COPPER_BULB,
                                    Blocks.WEATHERED_COPPER_BULB,
                                    Blocks.EXPOSED_COPPER_BULB,
                                    Blocks.WAXED_OXIDIZED_COPPER_BULB,
                                    Blocks.WAXED_WEATHERED_COPPER_BULB,
                                    Blocks.WAXED_EXPOSED_COPPER_BULB
                                )
                                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CopperBulbBlock.LIT, true))
                        ),
                    ItemPredicate.Builder.item().of(holderLookup1, VanillaHusbandryAdvancements.WAX_SCRAPING_TOOLS)
                )
            )
            .save(writer, "adventure/lighten_up");
        AdvancementHolder advancementHolder13 = Advancement.Builder.advancement()
            .parent(advancementHolder12)
            .display(
                Items.TRIAL_KEY,
                Component.translatable("advancements.adventure.under_lock_and_key.title"),
                Component.translatable("advancements.adventure.under_lock_and_key.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "under_lock_and_key",
                ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                    LocationPredicate.Builder.location()
                        .setBlock(
                            BlockPredicate.Builder.block()
                                .of(holderLookup2, Blocks.VAULT)
                                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(VaultBlock.OMINOUS, false))
                        ),
                    ItemPredicate.Builder.item().of(holderLookup1, Items.TRIAL_KEY)
                )
            )
            .save(writer, "adventure/under_lock_and_key");
        Advancement.Builder.advancement()
            .parent(advancementHolder13)
            .display(
                Items.OMINOUS_TRIAL_KEY,
                Component.translatable("advancements.adventure.revaulting.title"),
                Component.translatable("advancements.adventure.revaulting.description"),
                null,
                AdvancementType.GOAL,
                true,
                true,
                false
            )
            .addCriterion(
                "revaulting",
                ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                    LocationPredicate.Builder.location()
                        .setBlock(
                            BlockPredicate.Builder.block()
                                .of(holderLookup2, Blocks.VAULT)
                                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(VaultBlock.OMINOUS, true))
                        ),
                    ItemPredicate.Builder.item().of(holderLookup1, Items.OMINOUS_TRIAL_KEY)
                )
            )
            .save(writer, "adventure/revaulting");
        Advancement.Builder.advancement()
            .parent(advancementHolder12)
            .display(
                Items.WIND_CHARGE,
                Component.translatable("advancements.adventure.blowback.title"),
                Component.translatable("advancements.adventure.blowback.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(40))
            .addCriterion(
                "blowback",
                KilledTrigger.TriggerInstance.playerKilledEntity(
                    EntityPredicate.Builder.entity().of(holderLookup, EntityType.BREEZE),
                    DamageSourcePredicate.Builder.damageType()
                        .tag(TagPredicate.is(DamageTypeTags.IS_PROJECTILE))
                        .direct(EntityPredicate.Builder.entity().of(holderLookup, EntityType.BREEZE_WIND_CHARGE))
                )
            )
            .save(writer, "adventure/blowback");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.CRAFTER,
                Component.translatable("advancements.adventure.crafters_crafting_crafters.title"),
                Component.translatable("advancements.adventure.crafters_crafting_crafters.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "crafter_crafted_crafter",
                RecipeCraftedTrigger.TriggerInstance.crafterCraftedItem(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("crafter")))
            )
            .save(writer, "adventure/crafters_crafting_crafters");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Items.LODESTONE,
                Component.translatable("advancements.adventure.use_lodestone.title"),
                Component.translatable("advancements.adventure.use_lodestone.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "use_lodestone",
                ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                    LocationPredicate.Builder.location().setBlock(BlockPredicate.Builder.block().of(holderLookup2, Blocks.LODESTONE)),
                    ItemPredicate.Builder.item().of(holderLookup1, Items.COMPASS)
                )
            )
            .save(writer, "adventure/use_lodestone");
        Advancement.Builder.advancement()
            .parent(advancementHolder12)
            .display(
                Items.WIND_CHARGE,
                Component.translatable("advancements.adventure.who_needs_rockets.title"),
                Component.translatable("advancements.adventure.who_needs_rockets.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "who_needs_rockets",
                FallAfterExplosionTrigger.TriggerInstance.fallAfterExplosion(
                    DistancePredicate.vertical(MinMaxBounds.Doubles.atLeast(7.0)), EntityPredicate.Builder.entity().of(holderLookup, EntityType.WIND_CHARGE)
                )
            )
            .save(writer, "adventure/who_needs_rockets");
        Advancement.Builder.advancement()
            .parent(advancementHolder12)
            .display(
                Items.MACE,
                Component.translatable("advancements.adventure.overoverkill.title"),
                Component.translatable("advancements.adventure.overoverkill.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(50))
            .addCriterion(
                "overoverkill",
                PlayerHurtEntityTrigger.TriggerInstance.playerHurtEntityWithDamage(
                    DamagePredicate.Builder.damageInstance()
                        .dealtDamage(MinMaxBounds.Doubles.atLeast(100.0))
                        .type(
                            DamageSourcePredicate.Builder.damageType()
                                .tag(TagPredicate.is(DamageTypeTags.IS_MACE_SMASH))
                                .direct(
                                    EntityPredicate.Builder.entity()
                                        .of(holderLookup, EntityType.PLAYER)
                                        .equipment(
                                            EntityEquipmentPredicate.Builder.equipment().mainhand(ItemPredicate.Builder.item().of(holderLookup1, Items.MACE))
                                        )
                                )
                        )
                )
            )
            .save(writer, "adventure/overoverkill");
        Advancement.Builder.advancement()
            .parent(advancementHolder)
            .display(
                Blocks.CREAKING_HEART,
                Component.translatable("advancements.adventure.heart_transplanter.title"),
                Component.translatable("advancements.adventure.heart_transplanter.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .addCriterion(
                "place_creaking_heart_dormant",
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlockWithProperties(
                    Blocks.CREAKING_HEART, BlockStateProperties.CREAKING_HEART_STATE, CreakingHeartState.DORMANT
                )
            )
            .addCriterion(
                "place_creaking_heart_awake",
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlockWithProperties(
                    Blocks.CREAKING_HEART, BlockStateProperties.CREAKING_HEART_STATE, CreakingHeartState.AWAKE
                )
            )
            .addCriterion("place_pale_oak_log", placedBlockActivatesCreakingHeart(holderLookup2, BlockTags.PALE_OAK_LOGS))
            .save(writer, "adventure/heart_transplanter");
    }

    public static AdvancementHolder createMonsterHunterAdvancement(
        AdvancementHolder parent, Consumer<AdvancementHolder> output, HolderGetter<EntityType<?>> entityTypeRegistry, List<EntityType<?>> typesRequired
    ) {
        AdvancementHolder advancementHolder = addMobsToKill(Advancement.Builder.advancement(), entityTypeRegistry, typesRequired)
            .parent(parent)
            .display(
                Items.IRON_SWORD,
                Component.translatable("advancements.adventure.kill_a_mob.title"),
                Component.translatable("advancements.adventure.kill_a_mob.description"),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .save(output, "adventure/kill_a_mob");
        addMobsToKill(Advancement.Builder.advancement(), entityTypeRegistry, typesRequired)
            .parent(advancementHolder)
            .display(
                Items.DIAMOND_SWORD,
                Component.translatable("advancements.adventure.kill_all_mobs.title"),
                Component.translatable("advancements.adventure.kill_all_mobs.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(output, "adventure/kill_all_mobs");
        return advancementHolder;
    }

    private static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockReadByComparator(HolderGetter<Block> blockRegistry, Block block) {
        LootItemCondition.Builder[] builders = ComparatorBlock.FACING.getPossibleValues().stream().map(direction -> {
            StatePropertiesPredicate.Builder builder = StatePropertiesPredicate.Builder.properties().hasProperty(ComparatorBlock.FACING, direction);
            BlockPredicate.Builder builder1 = BlockPredicate.Builder.block().of(blockRegistry, Blocks.COMPARATOR).setProperties(builder);
            return LocationCheck.checkLocation(LocationPredicate.Builder.location().setBlock(builder1), new BlockPos(direction.getOpposite().getUnitVec3i()));
        }).toArray(LootItemCondition.Builder[]::new);
        return ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(
            LootItemBlockStatePropertyCondition.hasBlockStateProperties(block), AnyOfCondition.anyOf(builders)
        );
    }

    private static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedComparatorReadingBlock(HolderGetter<Block> blockRegistry, Block block) {
        LootItemCondition.Builder[] builders = ComparatorBlock.FACING
            .getPossibleValues()
            .stream()
            .map(
                direction -> {
                    StatePropertiesPredicate.Builder builder = StatePropertiesPredicate.Builder.properties().hasProperty(ComparatorBlock.FACING, direction);
                    LootItemBlockStatePropertyCondition.Builder builder1 = new LootItemBlockStatePropertyCondition.Builder(Blocks.COMPARATOR)
                        .setProperties(builder);
                    LootItemCondition.Builder builder2 = LocationCheck.checkLocation(
                        LocationPredicate.Builder.location().setBlock(BlockPredicate.Builder.block().of(blockRegistry, block)),
                        new BlockPos(direction.getUnitVec3i())
                    );
                    return AllOfCondition.allOf(builder1, builder2);
                }
            )
            .toArray(LootItemCondition.Builder[]::new);
        return ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(AnyOfCondition.anyOf(builders));
    }

    private static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockActivatesCreakingHeart(
        HolderGetter<Block> blockRegistry, TagKey<Block> blockTag
    ) {
        LootItemCondition.Builder[] builders = Stream.of(Direction.values())
            .map(
                direction -> {
                    StatePropertiesPredicate.Builder builder = StatePropertiesPredicate.Builder.properties()
                        .hasProperty(CreakingHeartBlock.AXIS, direction.getAxis());
                    BlockPredicate.Builder builder1 = BlockPredicate.Builder.block().of(blockRegistry, blockTag).setProperties(builder);
                    Vec3i unitVec3i = direction.getUnitVec3i();
                    LootItemCondition.Builder builder2 = LocationCheck.checkLocation(LocationPredicate.Builder.location().setBlock(builder1));
                    LootItemCondition.Builder builder3 = LocationCheck.checkLocation(
                        LocationPredicate.Builder.location()
                            .setBlock(BlockPredicate.Builder.block().of(blockRegistry, Blocks.CREAKING_HEART).setProperties(builder)),
                        new BlockPos(unitVec3i)
                    );
                    LootItemCondition.Builder builder4 = LocationCheck.checkLocation(
                        LocationPredicate.Builder.location().setBlock(builder1), new BlockPos(unitVec3i.multiply(2))
                    );
                    return AllOfCondition.allOf(builder2, builder3, builder4);
                }
            )
            .toArray(LootItemCondition.Builder[]::new);
        return ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(AnyOfCondition.anyOf(builders));
    }

    private static Advancement.Builder smithingWithStyle(Advancement.Builder builder) {
        builder.requirements(AdvancementRequirements.Strategy.AND);
        Set<Item> set = Set.of(
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE
        );
        VanillaRecipeProvider.smithingTrims()
            .filter(trimTemplate -> set.contains(trimTemplate.template()))
            .forEach(
                trimTemplate -> builder.addCriterion(
                    "armor_trimmed_" + trimTemplate.recipeId().identifier(), RecipeCraftedTrigger.TriggerInstance.craftedItem(trimTemplate.recipeId())
                )
            );
        return builder;
    }

    private static Advancement.Builder craftingANewLook(Advancement.Builder builder) {
        builder.requirements(AdvancementRequirements.Strategy.OR);
        VanillaRecipeProvider.smithingTrims()
            .map(VanillaRecipeProvider.TrimTemplate::recipeId)
            .forEach(
                resourceKey -> builder.addCriterion(
                    "armor_trimmed_" + resourceKey.identifier(), RecipeCraftedTrigger.TriggerInstance.craftedItem((ResourceKey<Recipe<?>>)resourceKey)
                )
            );
        return builder;
    }

    private static Advancement.Builder respectingTheRemnantsCriterions(HolderGetter<Item> itemRegistry, Advancement.Builder builder) {
        List<Pair<String, Criterion<LootTableTrigger.TriggerInstance>>> list = List.of(
            Pair.of("desert_pyramid", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY)),
            Pair.of("desert_well", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY)),
            Pair.of("ocean_ruin_cold", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY)),
            Pair.of("ocean_ruin_warm", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY)),
            Pair.of("trail_ruins_rare", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE)),
            Pair.of("trail_ruins_common", LootTableTrigger.TriggerInstance.lootTableUsed(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON))
        );
        list.forEach(pair -> builder.addCriterion(pair.getFirst(), pair.getSecond()));
        String string = "has_sherd";
        builder.addCriterion(
            "has_sherd", InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(itemRegistry, ItemTags.DECORATED_POT_SHERDS))
        );
        builder.requirements(new AdvancementRequirements(List.of(list.stream().map(Pair::getFirst).toList(), List.of("has_sherd"))));
        return builder;
    }

    protected static void createAdventuringTime(
        HolderLookup.Provider levelRegistry, Consumer<AdvancementHolder> writer, AdvancementHolder parent, MultiNoiseBiomeSourceParameterList.Preset preset
    ) {
        addBiomes(Advancement.Builder.advancement(), levelRegistry, preset.usedBiomes().toList())
            .parent(parent)
            .display(
                Items.DIAMOND_BOOTS,
                Component.translatable("advancements.adventure.adventuring_time.title"),
                Component.translatable("advancements.adventure.adventuring_time.description"),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(500))
            .save(writer, "adventure/adventuring_time");
    }

    private static Advancement.Builder addMobsToKill(
        Advancement.Builder builder, HolderGetter<EntityType<?>> entityTypeRegistry, List<EntityType<?>> mobsToKill
    ) {
        mobsToKill.forEach(
            entityType -> builder.addCriterion(
                BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>)entityType).toString(),
                KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(entityTypeRegistry, (EntityType<?>)entityType))
            )
        );
        return builder;
    }

    protected static Advancement.Builder addBiomes(Advancement.Builder builder, HolderLookup.Provider levelRegistry, List<ResourceKey<Biome>> biomes) {
        HolderGetter<Biome> holderGetter = levelRegistry.lookupOrThrow(Registries.BIOME);

        for (ResourceKey<Biome> resourceKey : biomes) {
            builder.addCriterion(
                resourceKey.identifier().toString(),
                PlayerTrigger.TriggerInstance.located(LocationPredicate.Builder.inBiome(holderGetter.getOrThrow(resourceKey)))
            );
        }

        return builder;
    }

    private static List<EntityType<?>> validateMobsToKill(List<EntityType<?>> mobsToKill, HolderLookup<EntityType<?>> entityTypeRegistry) {
        List<String> list = new ArrayList<>();
        Set<? extends EntityType<?>> set = Set.copyOf(mobsToKill);
        Set<MobCategory> set1 = set.stream().map(EntityType::getCategory).collect(Collectors.toSet());
        Set<MobCategory> set2 = Sets.symmetricDifference(EXCEPTIONS_BY_EXPECTED_CATEGORIES.keySet(), set1);
        if (!set2.isEmpty()) {
            list.add(
                "Found EntityType with MobCategory only in either expected exceptions or kill_all_mobs advancement: "
                    + set2.stream().map(Object::toString).sorted().collect(Collectors.joining(", "))
            );
        }

        Set<EntityType<?>> set3 = Sets.intersection(
            EXCEPTIONS_BY_EXPECTED_CATEGORIES.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()), set
        );
        if (!set3.isEmpty()) {
            list.add(
                "Found EntityType in both expected exceptions and kill_all_mobs advancement: "
                    + set3.stream().map(Object::toString).sorted().collect(Collectors.joining(", "))
            );
        }

        Map<MobCategory, Set<EntityType<?>>> map = entityTypeRegistry.listElements()
            .map(Holder.Reference::value)
            .filter(Predicate.not(set::contains))
            .collect(Collectors.groupingBy(EntityType::getCategory, Collectors.toSet()));
        EXCEPTIONS_BY_EXPECTED_CATEGORIES.forEach(
            (mobCategory, set4) -> {
                Set<EntityType<?>> set5 = Sets.difference(map.getOrDefault(mobCategory, Set.of()), (Set<?>)set4);
                if (!set5.isEmpty()) {
                    list.add(
                        String.format(
                            Locale.ROOT,
                            "Found (new?) EntityType with MobCategory %s which are in neither expected exceptions nor kill_all_mobs advancement: %s",
                            mobCategory,
                            set5.stream().map(Object::toString).sorted().collect(Collectors.joining(", "))
                        )
                    );
                }
            }
        );
        if (!list.isEmpty()) {
            list.forEach(LOGGER::error);
            throw new IllegalStateException("Found inconsistencies with kill_all_mobs advancement");
        } else {
            return mobsToKill;
        }
    }
}
