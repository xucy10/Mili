package net.minecraft.data.recipes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.EnterBlockTrigger;
import net.minecraft.advancements.criterion.ImpossibleTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import org.jspecify.annotations.Nullable;

public abstract class RecipeProvider {
    protected final HolderLookup.Provider registries;
    private final HolderGetter<Item> items;
    protected final RecipeOutput output;
    private static final Map<BlockFamily.Variant, RecipeProvider.FamilyRecipeProvider> SHAPE_BUILDERS = ImmutableMap.<BlockFamily.Variant, RecipeProvider.FamilyRecipeProvider>builder()
        .put(BlockFamily.Variant.BUTTON, (recipeProvider, ingredient, result) -> recipeProvider.buttonBuilder(ingredient, Ingredient.of(result)))
        .put(
            BlockFamily.Variant.CHISELED,
            (recipeProvider, ingredient, result) -> recipeProvider.chiseledBuilder(RecipeCategory.BUILDING_BLOCKS, ingredient, Ingredient.of(result))
        )
        .put(
            BlockFamily.Variant.CUT,
            (recipeProvider, ingredient, result) -> recipeProvider.cutBuilder(RecipeCategory.BUILDING_BLOCKS, ingredient, Ingredient.of(result))
        )
        .put(BlockFamily.Variant.DOOR, (recipeProvider, ingredient, result) -> recipeProvider.doorBuilder(ingredient, Ingredient.of(result)))
        .put(BlockFamily.Variant.CUSTOM_FENCE, (recipeProvider, ingredient, result) -> recipeProvider.fenceBuilder(ingredient, Ingredient.of(result)))
        .put(BlockFamily.Variant.FENCE, (recipeProvider, ingredient, result) -> recipeProvider.fenceBuilder(ingredient, Ingredient.of(result)))
        .put(BlockFamily.Variant.CUSTOM_FENCE_GATE, (recipeProvider, ingredient, result) -> recipeProvider.fenceGateBuilder(ingredient, Ingredient.of(result)))
        .put(BlockFamily.Variant.FENCE_GATE, (recipeProvider, ingredient, result) -> recipeProvider.fenceGateBuilder(ingredient, Ingredient.of(result)))
        .put(BlockFamily.Variant.SIGN, (recipeProvider, ingredient, result) -> recipeProvider.signBuilder(ingredient, Ingredient.of(result)))
        .put(
            BlockFamily.Variant.SLAB,
            (recipeProvider, ingredient, result) -> recipeProvider.slabBuilder(RecipeCategory.BUILDING_BLOCKS, ingredient, Ingredient.of(result))
        )
        .put(BlockFamily.Variant.STAIRS, (recipeProvider, ingredient, result) -> recipeProvider.stairBuilder(ingredient, Ingredient.of(result)))
        .put(
            BlockFamily.Variant.PRESSURE_PLATE,
            (recipeProvider, ingredient, result) -> recipeProvider.pressurePlateBuilder(RecipeCategory.REDSTONE, ingredient, Ingredient.of(result))
        )
        .put(
            BlockFamily.Variant.POLISHED,
            (recipeProvider, ingredient, result) -> recipeProvider.polishedBuilder(RecipeCategory.BUILDING_BLOCKS, ingredient, Ingredient.of(result))
        )
        .put(BlockFamily.Variant.TRAPDOOR, (recipeProvider, ingredient, result) -> recipeProvider.trapdoorBuilder(ingredient, Ingredient.of(result)))
        .put(
            BlockFamily.Variant.WALL,
            (recipeProvider, ingredient, result) -> recipeProvider.wallBuilder(RecipeCategory.DECORATIONS, ingredient, Ingredient.of(result))
        )
        .build();

    protected RecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        this.registries = registries;
        this.items = registries.lookupOrThrow(Registries.ITEM);
        this.output = output;
    }

    protected abstract void buildRecipes();

    protected void generateForEnabledBlockFamilies(FeatureFlagSet enabledFeatures) {
        BlockFamilies.getAllFamilies().filter(BlockFamily::shouldGenerateRecipe).forEach(blockFamily -> this.generateRecipes(blockFamily, enabledFeatures));
    }

    protected void oneToOneConversionRecipe(ItemLike result, ItemLike ingredient, @Nullable String group) {
        this.oneToOneConversionRecipe(result, ingredient, group, 1);
    }

    protected void oneToOneConversionRecipe(ItemLike result, ItemLike ingredient, @Nullable String group, int resultCount) {
        this.shapeless(RecipeCategory.MISC, result, resultCount)
            .requires(ingredient)
            .group(group)
            .unlockedBy(getHasName(ingredient), this.has(ingredient))
            .save(this.output, getConversionRecipeName(result, ingredient));
    }

    protected void oreSmelting(List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group) {
        this.oreCooking(RecipeSerializer.SMELTING_RECIPE, SmeltingRecipe::new, ingredients, category, result, experience, cookingTime, group, "_from_smelting");
    }

    protected void oreBlasting(List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group) {
        this.oreCooking(RecipeSerializer.BLASTING_RECIPE, BlastingRecipe::new, ingredients, category, result, experience, cookingTime, group, "_from_blasting");
    }

    private <T extends AbstractCookingRecipe> void oreCooking(
        RecipeSerializer<T> serializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        List<ItemLike> ingredients,
        RecipeCategory category,
        ItemLike result,
        float experience,
        int cookingTime,
        String group,
        String suffix
    ) {
        for (ItemLike itemLike : ingredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemLike), category, result, experience, cookingTime, serializer, recipeFactory)
                .group(group)
                .unlockedBy(getHasName(itemLike), this.has(itemLike))
                .save(this.output, getItemName(result) + suffix + "_" + getItemName(itemLike));
        }
    }

    protected void netheriteSmithing(Item ingredientItem, RecipeCategory category, Item resultItem) {
        SmithingTransformRecipeBuilder.smithing(
                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(ingredientItem),
                this.tag(ItemTags.NETHERITE_TOOL_MATERIALS),
                category,
                resultItem
            )
            .unlocks("has_netherite_ingot", this.has(ItemTags.NETHERITE_TOOL_MATERIALS))
            .save(this.output, getItemName(resultItem) + "_smithing");
    }

    protected void trimSmithing(Item template, ResourceKey<TrimPattern> pattern, ResourceKey<Recipe<?>> recipe) {
        Holder.Reference<TrimPattern> orThrow = this.registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(pattern);
        SmithingTrimRecipeBuilder.smithingTrim(
                Ingredient.of(template), this.tag(ItemTags.TRIMMABLE_ARMOR), this.tag(ItemTags.TRIM_MATERIALS), orThrow, RecipeCategory.MISC
            )
            .unlocks("has_smithing_trim_template", this.has(template))
            .save(this.output, recipe);
    }

    protected void twoByTwoPacker(RecipeCategory category, ItemLike packed, ItemLike unpacked) {
        this.shaped(category, packed, 1)
            .define('#', unpacked)
            .pattern("##")
            .pattern("##")
            .unlockedBy(getHasName(unpacked), this.has(unpacked))
            .save(this.output);
    }

    protected void threeByThreePacker(RecipeCategory category, ItemLike packed, ItemLike unpacked, String criterionName) {
        this.shapeless(category, packed).requires(unpacked, 9).unlockedBy(criterionName, this.has(unpacked)).save(this.output);
    }

    protected void threeByThreePacker(RecipeCategory category, ItemLike packed, ItemLike unpacked) {
        this.threeByThreePacker(category, packed, unpacked, getHasName(unpacked));
    }

    protected void planksFromLog(ItemLike planks, TagKey<Item> logs, int resultCount) {
        this.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, resultCount)
            .requires(logs)
            .group("planks")
            .unlockedBy("has_log", this.has(logs))
            .save(this.output);
    }

    protected void planksFromLogs(ItemLike planks, TagKey<Item> logs, int result) {
        this.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, result).requires(logs).group("planks").unlockedBy("has_logs", this.has(logs)).save(this.output);
    }

    protected void woodFromLogs(ItemLike wood, ItemLike log) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, wood, 3)
            .define('#', log)
            .pattern("##")
            .pattern("##")
            .group("bark")
            .unlockedBy("has_log", this.has(log))
            .save(this.output);
    }

    protected void woodenBoat(ItemLike boat, ItemLike material) {
        this.shaped(RecipeCategory.TRANSPORTATION, boat)
            .define('#', material)
            .pattern("# #")
            .pattern("###")
            .group("boat")
            .unlockedBy("in_water", insideOf(Blocks.WATER))
            .save(this.output);
    }

    protected void chestBoat(ItemLike boat, ItemLike material) {
        this.shapeless(RecipeCategory.TRANSPORTATION, boat)
            .requires(Blocks.CHEST)
            .requires(material)
            .group("chest_boat")
            .unlockedBy("has_boat", this.has(ItemTags.BOATS))
            .save(this.output);
    }

    private RecipeBuilder buttonBuilder(ItemLike button, Ingredient material) {
        return this.shapeless(RecipeCategory.REDSTONE, button).requires(material);
    }

    protected RecipeBuilder doorBuilder(ItemLike door, Ingredient material) {
        return this.shaped(RecipeCategory.REDSTONE, door, 3).define('#', material).pattern("##").pattern("##").pattern("##");
    }

    private RecipeBuilder fenceBuilder(ItemLike fence, Ingredient material) {
        int i = fence == Blocks.NETHER_BRICK_FENCE ? 6 : 3;
        Item item = fence == Blocks.NETHER_BRICK_FENCE ? Items.NETHER_BRICK : Items.STICK;
        return this.shaped(RecipeCategory.DECORATIONS, fence, i).define('W', material).define('#', item).pattern("W#W").pattern("W#W");
    }

    private RecipeBuilder fenceGateBuilder(ItemLike fenceGate, Ingredient material) {
        return this.shaped(RecipeCategory.REDSTONE, fenceGate).define('#', Items.STICK).define('W', material).pattern("#W#").pattern("#W#");
    }

    protected void pressurePlate(ItemLike pressurePlate, ItemLike material) {
        this.pressurePlateBuilder(RecipeCategory.REDSTONE, pressurePlate, Ingredient.of(material))
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    private RecipeBuilder pressurePlateBuilder(RecipeCategory category, ItemLike pressurePlate, Ingredient material) {
        return this.shaped(category, pressurePlate).define('#', material).pattern("##");
    }

    protected void slab(RecipeCategory category, ItemLike slab, ItemLike material) {
        this.slabBuilder(category, slab, Ingredient.of(material)).unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    protected void shelf(ItemLike shelf, ItemLike material) {
        this.shaped(RecipeCategory.DECORATIONS, shelf, 6)
            .define('#', material)
            .pattern("###")
            .pattern("   ")
            .pattern("###")
            .group("shelf")
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    protected RecipeBuilder slabBuilder(RecipeCategory category, ItemLike slab, Ingredient material) {
        return this.shaped(category, slab, 6).define('#', material).pattern("###");
    }

    protected RecipeBuilder stairBuilder(ItemLike stairs, Ingredient material) {
        return this.shaped(RecipeCategory.BUILDING_BLOCKS, stairs, 4).define('#', material).pattern("#  ").pattern("## ").pattern("###");
    }

    protected RecipeBuilder trapdoorBuilder(ItemLike trapdoor, Ingredient material) {
        return this.shaped(RecipeCategory.REDSTONE, trapdoor, 2).define('#', material).pattern("###").pattern("###");
    }

    private RecipeBuilder signBuilder(ItemLike sign, Ingredient material) {
        return this.shaped(RecipeCategory.DECORATIONS, sign, 3)
            .group("sign")
            .define('#', material)
            .define('X', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" X ");
    }

    protected void hangingSign(ItemLike sign, ItemLike material) {
        this.shaped(RecipeCategory.DECORATIONS, sign, 6)
            .group("hanging_sign")
            .define('#', material)
            .define('X', Items.IRON_CHAIN)
            .pattern("X X")
            .pattern("###")
            .pattern("###")
            .unlockedBy("has_stripped_logs", this.has(material))
            .save(this.output);
    }

    protected void colorItemWithDye(List<Item> dyeItems, List<Item> dyeableItems, String group, RecipeCategory category) {
        this.colorWithDye(dyeItems, dyeableItems, null, group, category);
    }

    protected void colorWithDye(List<Item> dyes, List<Item> dyeableItems, @Nullable Item dye, String group, RecipeCategory category) {
        for (int i = 0; i < dyes.size(); i++) {
            Item item = dyes.get(i);
            Item item1 = dyeableItems.get(i);
            Stream<Item> stream = dyeableItems.stream().filter(item2 -> !item2.equals(item1));
            if (dye != null) {
                stream = Stream.concat(stream, Stream.of(dye));
            }

            this.shapeless(category, item1)
                .requires(item)
                .requires(Ingredient.of(stream))
                .group(group)
                .unlockedBy("has_needed_dye", this.has(item))
                .save(this.output, "dye_" + getItemName(item1));
        }
    }

    protected void carpet(ItemLike carpet, ItemLike material) {
        this.shaped(RecipeCategory.DECORATIONS, carpet, 3)
            .define('#', material)
            .pattern("##")
            .group("carpet")
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    protected void bedFromPlanksAndWool(ItemLike bed, ItemLike wool) {
        this.shaped(RecipeCategory.DECORATIONS, bed)
            .define('#', wool)
            .define('X', ItemTags.PLANKS)
            .pattern("###")
            .pattern("XXX")
            .group("bed")
            .unlockedBy(getHasName(wool), this.has(wool))
            .save(this.output);
    }

    protected void banner(ItemLike banner, ItemLike material) {
        this.shaped(RecipeCategory.DECORATIONS, banner)
            .define('#', material)
            .define('|', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" | ")
            .group("banner")
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    protected void stainedGlassFromGlassAndDye(ItemLike stainedGlass, ItemLike dye) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, stainedGlass, 8)
            .define('#', Blocks.GLASS)
            .define('X', dye)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_glass")
            .unlockedBy("has_glass", this.has(Blocks.GLASS))
            .save(this.output);
    }

    protected void dryGhast(ItemLike dryGhast) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, dryGhast, 1)
            .define('#', Items.GHAST_TEAR)
            .define('X', Items.SOUL_SAND)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("dry_ghast")
            .unlockedBy(getHasName(Items.GHAST_TEAR), this.has(Items.GHAST_TEAR))
            .save(this.output);
    }

    protected void harness(ItemLike harness, ItemLike wool) {
        this.shaped(RecipeCategory.COMBAT, harness)
            .define('#', wool)
            .define('G', Items.GLASS)
            .define('L', Items.LEATHER)
            .pattern("LLL")
            .pattern("G#G")
            .group("harness")
            .unlockedBy("has_dried_ghast", this.has(Blocks.DRIED_GHAST))
            .save(this.output);
    }

    protected void stainedGlassPaneFromStainedGlass(ItemLike stainedGlassPane, ItemLike stainedGlass) {
        this.shaped(RecipeCategory.DECORATIONS, stainedGlassPane, 16)
            .define('#', stainedGlass)
            .pattern("###")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass", this.has(stainedGlass))
            .save(this.output);
    }

    protected void stainedGlassPaneFromGlassPaneAndDye(ItemLike stainedGlassPane, ItemLike dye) {
        this.shaped(RecipeCategory.DECORATIONS, stainedGlassPane, 8)
            .define('#', Blocks.GLASS_PANE)
            .define('$', dye)
            .pattern("###")
            .pattern("#$#")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass_pane", this.has(Blocks.GLASS_PANE))
            .unlockedBy(getHasName(dye), this.has(dye))
            .save(this.output, getConversionRecipeName(stainedGlassPane, Blocks.GLASS_PANE));
    }

    protected void coloredTerracottaFromTerracottaAndDye(ItemLike terracotta, ItemLike dye) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, terracotta, 8)
            .define('#', Blocks.TERRACOTTA)
            .define('X', dye)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_terracotta")
            .unlockedBy("has_terracotta", this.has(Blocks.TERRACOTTA))
            .save(this.output);
    }

    protected void concretePowder(ItemLike concretePowder, ItemLike dye) {
        this.shapeless(RecipeCategory.BUILDING_BLOCKS, concretePowder, 8)
            .requires(dye)
            .requires(Blocks.SAND, 4)
            .requires(Blocks.GRAVEL, 4)
            .group("concrete_powder")
            .unlockedBy("has_sand", this.has(Blocks.SAND))
            .unlockedBy("has_gravel", this.has(Blocks.GRAVEL))
            .save(this.output);
    }

    protected void candle(ItemLike candle, ItemLike dye) {
        this.shapeless(RecipeCategory.DECORATIONS, candle)
            .requires(Blocks.CANDLE)
            .requires(dye)
            .group("dyed_candle")
            .unlockedBy(getHasName(dye), this.has(dye))
            .save(this.output);
    }

    protected void wall(RecipeCategory category, ItemLike wall, ItemLike material) {
        this.wallBuilder(category, wall, Ingredient.of(material)).unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    private RecipeBuilder wallBuilder(RecipeCategory category, ItemLike wall, Ingredient material) {
        return this.shaped(category, wall, 6).define('#', material).pattern("###").pattern("###");
    }

    protected void polished(RecipeCategory category, ItemLike result, ItemLike material) {
        this.polishedBuilder(category, result, Ingredient.of(material)).unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    private RecipeBuilder polishedBuilder(RecipeCategory category, ItemLike result, Ingredient material) {
        return this.shaped(category, result, 4).define('S', material).pattern("SS").pattern("SS");
    }

    protected void cut(RecipeCategory category, ItemLike cutResult, ItemLike material) {
        this.cutBuilder(category, cutResult, Ingredient.of(material)).unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    private ShapedRecipeBuilder cutBuilder(RecipeCategory category, ItemLike cutResult, Ingredient material) {
        return this.shaped(category, cutResult, 4).define('#', material).pattern("##").pattern("##");
    }

    protected void chiseled(RecipeCategory category, ItemLike chiseledResult, ItemLike material) {
        this.chiseledBuilder(category, chiseledResult, Ingredient.of(material)).unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    protected void mosaicBuilder(RecipeCategory category, ItemLike result, ItemLike material) {
        this.shaped(category, result).define('#', material).pattern("#").pattern("#").unlockedBy(getHasName(material), this.has(material)).save(this.output);
    }

    protected ShapedRecipeBuilder chiseledBuilder(RecipeCategory category, ItemLike chiseledResult, Ingredient material) {
        return this.shaped(category, chiseledResult).define('#', material).pattern("#").pattern("#");
    }

    protected void stonecutterResultFromBase(RecipeCategory category, ItemLike result, ItemLike material) {
        this.stonecutterResultFromBase(category, result, material, 1);
    }

    protected void stonecutterResultFromBase(RecipeCategory category, ItemLike result, ItemLike material, int resultCount) {
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(material), category, result, resultCount)
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output, getConversionRecipeName(result, material) + "_stonecutting");
    }

    private void smeltingResultFromBase(ItemLike result, ItemLike ingredient) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ingredient), RecipeCategory.BUILDING_BLOCKS, result, 0.1F, 200)
            .unlockedBy(getHasName(ingredient), this.has(ingredient))
            .save(this.output);
    }

    protected void nineBlockStorageRecipes(RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed) {
        this.nineBlockStorageRecipes(unpackedCategory, unpacked, packedCategory, packed, getSimpleRecipeName(packed), null, getSimpleRecipeName(unpacked), null);
    }

    protected void nineBlockStorageRecipesWithCustomPacking(
        RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed, String packedName, String packedGroup
    ) {
        this.nineBlockStorageRecipes(unpackedCategory, unpacked, packedCategory, packed, packedName, packedGroup, getSimpleRecipeName(unpacked), null);
    }

    protected void nineBlockStorageRecipesRecipesWithCustomUnpacking(
        RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed, String unpackedName, String unpackedGroup
    ) {
        this.nineBlockStorageRecipes(unpackedCategory, unpacked, packedCategory, packed, getSimpleRecipeName(packed), null, unpackedName, unpackedGroup);
    }

    private void nineBlockStorageRecipes(
        RecipeCategory unpackedCategory,
        ItemLike unpacked,
        RecipeCategory packedCategory,
        ItemLike packed,
        String packedName,
        @Nullable String packedGroup,
        String unpackedName,
        @Nullable String unpackedGroup
    ) {
        this.shapeless(unpackedCategory, unpacked, 9)
            .requires(packed)
            .group(unpackedGroup)
            .unlockedBy(getHasName(packed), this.has(packed))
            .save(this.output, ResourceKey.create(Registries.RECIPE, Identifier.parse(unpackedName)));
        this.shaped(packedCategory, packed)
            .define('#', unpacked)
            .pattern("###")
            .pattern("###")
            .pattern("###")
            .group(packedGroup)
            .unlockedBy(getHasName(unpacked), this.has(unpacked))
            .save(this.output, ResourceKey.create(Registries.RECIPE, Identifier.parse(packedName)));
    }

    protected void copySmithingTemplate(ItemLike template, ItemLike baseItem) {
        this.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', baseItem)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), this.has(template))
            .save(this.output);
    }

    protected void copySmithingTemplate(ItemLike template, Ingredient baseItem) {
        this.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', baseItem)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), this.has(template))
            .save(this.output);
    }

    protected <T extends AbstractCookingRecipe> void cookRecipes(
        String cookingMethod, RecipeSerializer<T> cookingSerializer, AbstractCookingRecipe.Factory<T> recipeFactory, int cookingTime
    ) {
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.BEEF, Items.COOKED_BEEF, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.CHICKEN, Items.COOKED_CHICKEN, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.COD, Items.COOKED_COD, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.KELP, Items.DRIED_KELP, 0.1F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.SALMON, Items.COOKED_SALMON, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.MUTTON, Items.COOKED_MUTTON, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.PORKCHOP, Items.COOKED_PORKCHOP, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.POTATO, Items.BAKED_POTATO, 0.35F);
        this.simpleCookingRecipe(cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.RABBIT, Items.COOKED_RABBIT, 0.35F);
    }

    private <T extends AbstractCookingRecipe> void simpleCookingRecipe(
        String cookingMethod,
        RecipeSerializer<T> cookingSerializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        int cookingTime,
        ItemLike material,
        ItemLike result,
        float experience
    ) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(material), RecipeCategory.FOOD, result, experience, cookingTime, cookingSerializer, recipeFactory)
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output, getItemName(result) + "_from_" + cookingMethod);
    }

    protected void waxRecipes(FeatureFlagSet requiredFeatures) {
        HoneycombItem.WAXABLES
            .get()
            .forEach(
                (block, block1) -> {
                    if (block1.requiredFeatures().isSubsetOf(requiredFeatures)) {
                        Pair<RecipeCategory, String> pair = HoneycombItem.WAXED_RECIPES
                            .getOrDefault(block1, Pair.of(RecipeCategory.BUILDING_BLOCKS, getItemName(block1)));
                        RecipeCategory recipeCategory = pair.getFirst();
                        String string = pair.getSecond();
                        this.shapeless(recipeCategory, block1)
                            .requires(block)
                            .requires(Items.HONEYCOMB)
                            .group(string)
                            .unlockedBy(getHasName(block), this.has(block))
                            .save(this.output, getConversionRecipeName(block1, Items.HONEYCOMB));
                    }
                }
            );
    }

    protected void grate(Block grateBlock, Block material) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, grateBlock, 4)
            .define('M', material)
            .pattern(" M ")
            .pattern("M M")
            .pattern(" M ")
            .group(getItemName(grateBlock))
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    protected void copperBulb(Block bulbBlock, Block material) {
        this.shaped(RecipeCategory.REDSTONE, bulbBlock, 4)
            .define('C', material)
            .define('R', Items.REDSTONE)
            .define('B', Items.BLAZE_ROD)
            .pattern(" C ")
            .pattern("CBC")
            .pattern(" R ")
            .unlockedBy(getHasName(material), this.has(material))
            .group(getItemName(bulbBlock))
            .save(this.output);
    }

    protected void waxedChiseled(Block result, Block material) {
        this.shaped(RecipeCategory.BUILDING_BLOCKS, result)
            .define('M', material)
            .pattern(" M ")
            .pattern(" M ")
            .group(getItemName(result))
            .unlockedBy(getHasName(material), this.has(material))
            .save(this.output);
    }

    protected void suspiciousStew(Item flowerItem, SuspiciousEffectHolder effect) {
        ItemStack itemStack = new ItemStack(
            Items.SUSPICIOUS_STEW.builtInRegistryHolder(),
            1,
            DataComponentPatch.builder().set(DataComponents.SUSPICIOUS_STEW_EFFECTS, effect.getSuspiciousEffects()).build()
        );
        this.shapeless(RecipeCategory.FOOD, itemStack)
            .requires(Items.BOWL)
            .requires(Items.BROWN_MUSHROOM)
            .requires(Items.RED_MUSHROOM)
            .requires(flowerItem)
            .group("suspicious_stew")
            .unlockedBy(getHasName(flowerItem), this.has(flowerItem))
            .save(this.output, getItemName(itemStack.getItem()) + "_from_" + getItemName(flowerItem));
    }

    protected void generateRecipes(BlockFamily blockFamily, FeatureFlagSet requiredFeatures) {
        blockFamily.getVariants()
            .forEach(
                (variant, block) -> {
                    if (block.requiredFeatures().isSubsetOf(requiredFeatures)) {
                        RecipeProvider.FamilyRecipeProvider familyRecipeProvider = SHAPE_BUILDERS.get(variant);
                        ItemLike baseBlock = this.getBaseBlock(blockFamily, variant);
                        if (familyRecipeProvider != null) {
                            RecipeBuilder recipeBuilder = familyRecipeProvider.create(this, block, baseBlock);
                            blockFamily.getRecipeGroupPrefix()
                                .ifPresent(string -> recipeBuilder.group(string + (variant == BlockFamily.Variant.CUT ? "" : "_" + variant.getRecipeGroup())));
                            recipeBuilder.unlockedBy(blockFamily.getRecipeUnlockedBy().orElseGet(() -> getHasName(baseBlock)), this.has(baseBlock));
                            recipeBuilder.save(this.output);
                        }

                        if (variant == BlockFamily.Variant.CRACKED) {
                            this.smeltingResultFromBase(block, baseBlock);
                        }
                    }
                }
            );
    }

    private Block getBaseBlock(BlockFamily family, BlockFamily.Variant variant) {
        if (variant == BlockFamily.Variant.CHISELED) {
            if (!family.getVariants().containsKey(BlockFamily.Variant.SLAB)) {
                throw new IllegalStateException("Slab is not defined for the family.");
            } else {
                return family.get(BlockFamily.Variant.SLAB);
            }
        } else {
            return family.getBaseBlock();
        }
    }

    private static Criterion<EnterBlockTrigger.TriggerInstance> insideOf(Block block) {
        return CriteriaTriggers.ENTER_BLOCK
            .createCriterion(new EnterBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
    }

    private Criterion<InventoryChangeTrigger.TriggerInstance> has(MinMaxBounds.Ints count, ItemLike item) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, item).withCount(count));
    }

    protected Criterion<InventoryChangeTrigger.TriggerInstance> has(ItemLike item) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, item));
    }

    protected Criterion<InventoryChangeTrigger.TriggerInstance> has(TagKey<Item> tag) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, tag));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate.Builder... items) {
        return inventoryTrigger(Arrays.stream(items).map(ItemPredicate.Builder::build).toArray(ItemPredicate[]::new));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate... predicates) {
        return CriteriaTriggers.INVENTORY_CHANGED
            .createCriterion(
                new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(predicates))
            );
    }

    protected static String getHasName(ItemLike item) {
        return "has_" + getItemName(item);
    }

    protected static String getItemName(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
    }

    protected static String getSimpleRecipeName(ItemLike item) {
        return getItemName(item);
    }

    protected static String getConversionRecipeName(ItemLike result, ItemLike ingredient) {
        return getItemName(result) + "_from_" + getItemName(ingredient);
    }

    protected static String getSmeltingRecipeName(ItemLike item) {
        return getItemName(item) + "_from_smelting";
    }

    protected static String getBlastingRecipeName(ItemLike item) {
        return getItemName(item) + "_from_blasting";
    }

    protected Ingredient tag(TagKey<Item> tag) {
        return Ingredient.of(this.items.getOrThrow(tag));
    }

    protected ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result) {
        return ShapedRecipeBuilder.shaped(this.items, category, result);
    }

    protected ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result, int count) {
        return ShapedRecipeBuilder.shaped(this.items, category, result, count);
    }

    protected ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemStack result) {
        return ShapelessRecipeBuilder.shapeless(this.items, category, result);
    }

    protected ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike result) {
        return ShapelessRecipeBuilder.shapeless(this.items, category, result);
    }

    protected ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike result, int count) {
        return ShapelessRecipeBuilder.shapeless(this.items, category, result, count);
    }

    @FunctionalInterface
    interface FamilyRecipeProvider {
        RecipeBuilder create(RecipeProvider recipeProvider, ItemLike ingredient, ItemLike result);
    }

    protected abstract static class Runner implements DataProvider {
        private final PackOutput packOutput;
        private final CompletableFuture<HolderLookup.Provider> registries;

        protected Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            this.packOutput = packOutput;
            this.registries = registries;
        }

        @Override
        public final CompletableFuture<?> run(CachedOutput output) {
            return this.registries
                .thenCompose(
                    provider -> {
                        final PackOutput.PathProvider pathProvider = this.packOutput.createRegistryElementsPathProvider(Registries.RECIPE);
                        final PackOutput.PathProvider pathProvider1 = this.packOutput.createRegistryElementsPathProvider(Registries.ADVANCEMENT);
                        final Set<ResourceKey<Recipe<?>>> set = Sets.newHashSet();
                        final List<CompletableFuture<?>> list = new ArrayList<>();
                        RecipeOutput recipeOutput = new RecipeOutput() {
                            @Override
                            public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
                                if (!set.add(key)) {
                                    throw new IllegalStateException("Duplicate recipe " + key.identifier());
                                } else {
                                    this.saveRecipe(key, recipe);
                                    if (advancement != null) {
                                        this.saveAdvancement(advancement);
                                    }
                                }
                            }

                            @Override
                            public Advancement.Builder advancement() {
                                return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
                            }

                            @Override
                            public void includeRootAdvancement() {
                                AdvancementHolder advancementHolder = Advancement.Builder.recipeAdvancement()
                                    .addCriterion("impossible", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                                    .build(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
                                this.saveAdvancement(advancementHolder);
                            }

                            private void saveRecipe(ResourceKey<Recipe<?>> id, Recipe<?> recipe) {
                                list.add(DataProvider.saveStable(output, provider, Recipe.CODEC, recipe, pathProvider.json(id.identifier())));
                            }

                            private void saveAdvancement(AdvancementHolder advancement) {
                                list.add(
                                    DataProvider.saveStable(output, provider, Advancement.CODEC, advancement.value(), pathProvider1.json(advancement.id()))
                                );
                            }
                        };
                        this.createRecipeProvider(provider, recipeOutput).buildRecipes();
                        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
                    }
                );
        }

        protected abstract RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output);
    }
}
