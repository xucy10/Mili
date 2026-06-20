package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RecipeManager extends SimplePreparableReloadListener<RecipeMap> implements RecipeAccess {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<RecipePropertySet>, RecipeManager.IngredientExtractor> RECIPE_PROPERTY_SETS = Map.of(
        RecipePropertySet.SMITHING_ADDITION,
        recipe -> recipe instanceof SmithingRecipe smithingRecipe ? smithingRecipe.additionIngredient() : Optional.empty(),
        RecipePropertySet.SMITHING_BASE,
        recipe -> recipe instanceof SmithingRecipe smithingRecipe ? Optional.of(smithingRecipe.baseIngredient()) : Optional.empty(),
        RecipePropertySet.SMITHING_TEMPLATE,
        recipe -> recipe instanceof SmithingRecipe smithingRecipe ? smithingRecipe.templateIngredient() : Optional.empty(),
        RecipePropertySet.FURNACE_INPUT,
        forSingleInput(RecipeType.SMELTING),
        RecipePropertySet.BLAST_FURNACE_INPUT,
        forSingleInput(RecipeType.BLASTING),
        RecipePropertySet.SMOKER_INPUT,
        forSingleInput(RecipeType.SMOKING),
        RecipePropertySet.CAMPFIRE_INPUT,
        forSingleInput(RecipeType.CAMPFIRE_COOKING)
    );
    private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.registry(Registries.RECIPE);
    private final HolderLookup.Provider registries;
    public RecipeMap recipes = RecipeMap.EMPTY;
    private Map<ResourceKey<RecipePropertySet>, RecipePropertySet> propertySets = Map.of();
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes = SelectableRecipe.SingleInputSet.empty();
    private List<RecipeManager.ServerDisplayInfo> allDisplays = List.of();
    private Map<ResourceKey<Recipe<?>>, List<RecipeManager.ServerDisplayInfo>> recipeToDisplay = Map.of();

    public RecipeManager(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    @Override
    protected RecipeMap prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        SortedMap<Identifier, Recipe<?>> map = new TreeMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(
            resourceManager, RECIPE_LISTER, this.registries.createSerializationContext(JsonOps.INSTANCE), Recipe.CODEC, map
        );
        List<RecipeHolder<?>> list = new ArrayList<>(map.size());
        map.forEach((location, recipe) -> {
            ResourceKey<Recipe<?>> resourceKey = ResourceKey.create(Registries.RECIPE, location);
            RecipeHolder<?> recipeHolder = new RecipeHolder<>(resourceKey, recipe);
            list.add(recipeHolder);
        });
        return RecipeMap.create(list);
    }

    @Override
    protected void apply(RecipeMap object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.recipes = object;
        LOGGER.info("Loaded {} recipes", object.values().size());
    }

    // CraftBukkit start
    public void addRecipe(RecipeHolder<?> holder) {
        org.spigotmc.AsyncCatcher.catchOp("Recipe Add"); // Spigot
        this.recipes.addRecipe(holder);
        this.finalizeRecipeLoading();
    }

    private FeatureFlagSet featureflagset;

    public void finalizeRecipeLoading() {
        if (this.featureflagset != null) {
            this.finalizeRecipeLoading(this.featureflagset);

            net.minecraft.server.MinecraftServer.getServer().getPlayerList().reloadResources();
        }
    }

    public void finalizeRecipeLoading(FeatureFlagSet enabledFeatures) {
        this.featureflagset = enabledFeatures;
        // CraftBukkit end
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = new ArrayList<>();
        List<RecipeManager.IngredientCollector> list1 = RECIPE_PROPERTY_SETS.entrySet()
            .stream()
            .map(entry -> new RecipeManager.IngredientCollector(entry.getKey(), entry.getValue()))
            .toList();
        this.recipes
            .values()
            .forEach(
                recipe -> {
                    Recipe<?> recipe1 = recipe.value();
                    if (!recipe1.isSpecial() && recipe1.placementInfo().isImpossibleToPlace()) {
                        LOGGER.warn("Recipe {} can't be placed due to empty ingredients and will be ignored", recipe.id().identifier());
                    } else {
                        list1.forEach(collector -> collector.accept(recipe1));
                        if (recipe1 instanceof StonecutterRecipe stonecutterRecipe
                            && isIngredientEnabled(enabledFeatures, stonecutterRecipe.input())
                            && stonecutterRecipe.resultDisplay().isEnabled(enabledFeatures)) {
                            list.add(
                                new SelectableRecipe.SingleInputEntry<>(
                                    stonecutterRecipe.input(),
                                    new SelectableRecipe<>(stonecutterRecipe.resultDisplay(), Optional.of((RecipeHolder<StonecutterRecipe>)recipe))
                                )
                            );
                        }
                    }
                }
            );
        this.propertySets = list1.stream()
            .collect(Collectors.toUnmodifiableMap(collector -> collector.key, collector -> collector.asPropertySet(enabledFeatures)));
        this.stonecutterRecipes = new SelectableRecipe.SingleInputSet<>(list);
        this.allDisplays = unpackRecipeInfo(this.recipes.values(), enabledFeatures);
        this.recipeToDisplay = this.allDisplays
            .stream()
            .collect(Collectors.groupingBy(displayInfo -> displayInfo.parent.id(), IdentityHashMap::new, Collectors.toList()));
    }

    static List<Ingredient> filterDisabled(FeatureFlagSet enabledFeatures, List<Ingredient> ingredients) {
        ingredients.removeIf(ingredient -> !isIngredientEnabled(enabledFeatures, ingredient));
        return ingredients;
    }

    private static boolean isIngredientEnabled(FeatureFlagSet enabledFeatures, Ingredient ingredient) {
        return ingredient.items().allMatch(item -> item.value().isEnabled(enabledFeatures));
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> recipeType, I input, Level level, @Nullable ResourceKey<Recipe<?>> recipe
    ) {
        RecipeHolder<T> recipeHolder = recipe != null ? this.byKeyTyped(recipeType, recipe) : null;
        return this.getRecipeFor(recipeType, input, level, recipeHolder);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> recipeType, I input, Level level, @Nullable RecipeHolder<T> lastRecipe
    ) {
        return lastRecipe != null && lastRecipe.value().matches(input, level) ? Optional.of(lastRecipe) : this.getRecipeFor(recipeType, input, level);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> recipeType, I input, Level level) {
        // CraftBukkit start
        List<RecipeHolder<T>> list = this.recipes.getRecipesFor(recipeType, input, level).toList();
        return (list.isEmpty()) ? Optional.empty() : Optional.of(list.getLast()); // CraftBukkit - SPIGOT-4638: last recipe gets priority
        // CraftBukkit end
    }

    public Optional<RecipeHolder<?>> byKey(ResourceKey<Recipe<?>> key) {
        return Optional.ofNullable(this.recipes.byKey(key));
    }

    private <T extends Recipe<?>> @Nullable RecipeHolder<T> byKeyTyped(RecipeType<T> type, ResourceKey<Recipe<?>> key) {
        RecipeHolder<?> recipeHolder = this.recipes.byKey(key);
        return (RecipeHolder<T>)(recipeHolder != null && recipeHolder.value().getType().equals(type) ? recipeHolder : null);
    }

    public Map<ResourceKey<RecipePropertySet>, RecipePropertySet> getSynchronizedItemProperties() {
        return this.propertySets;
    }

    public SelectableRecipe.SingleInputSet<StonecutterRecipe> getSynchronizedStonecutterRecipes() {
        return this.stonecutterRecipes;
    }

    @Override
    public RecipePropertySet propertySet(ResourceKey<RecipePropertySet> propertySet) {
        return this.propertySets.getOrDefault(propertySet, RecipePropertySet.EMPTY);
    }

    @Override
    public SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes() {
        return this.stonecutterRecipes;
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return this.recipes.values();
    }

    public RecipeManager.@Nullable ServerDisplayInfo getRecipeFromDisplay(RecipeDisplayId display) {
        int index = display.index();
        return index >= 0 && index < this.allDisplays.size() ? this.allDisplays.get(index) : null;
    }

    public void listDisplaysForRecipe(ResourceKey<Recipe<?>> recipe, Consumer<RecipeDisplayEntry> output) {
        List<RecipeManager.ServerDisplayInfo> list = this.recipeToDisplay.get(recipe);
        if (list != null) {
            list.forEach(displayInfo -> output.accept(displayInfo.display));
        }
    }

    @VisibleForTesting
    protected static RecipeHolder<?> fromJson(ResourceKey<Recipe<?>> recipe, JsonObject json, HolderLookup.Provider registries) {
        Recipe<?> recipe1 = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
        return new RecipeHolder<>(recipe, recipe1);
    }

    // CraftBukkit start
    public boolean removeRecipe(ResourceKey<Recipe<?>> mcKey) {
        boolean removed = this.recipes.removeRecipe((ResourceKey<Recipe<RecipeInput>>) (ResourceKey) mcKey); // Paper - generic fix
        if (removed) {
            this.finalizeRecipeLoading();
        }

        return removed;
    }

    public void clearRecipes() {
        this.recipes = RecipeMap.create(java.util.Collections.emptyList());
        this.finalizeRecipeLoading();
    }
    // CraftBukkit end

    public static <I extends RecipeInput, T extends Recipe<I>> RecipeManager.CachedCheck<I, T> createCheck(final RecipeType<T> recipeType) {
        return new RecipeManager.CachedCheck<I, T>() {
            private @Nullable ResourceKey<Recipe<?>> lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(I input, ServerLevel level) {
                RecipeManager recipeManager = level.recipeAccess();
                Optional<RecipeHolder<T>> recipeFor = recipeManager.getRecipeFor(recipeType, input, level, this.lastRecipe);
                if (recipeFor.isPresent()) {
                    RecipeHolder<T> recipeHolder = recipeFor.get();
                    this.lastRecipe = recipeHolder.id();
                    return Optional.of(recipeHolder);
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    private static List<RecipeManager.ServerDisplayInfo> unpackRecipeInfo(Iterable<RecipeHolder<?>> recipes, FeatureFlagSet enabledFeatures) {
        List<RecipeManager.ServerDisplayInfo> list = new ArrayList<>();
        Object2IntMap<String> map = new Object2IntOpenHashMap<>();

        for (RecipeHolder<?> recipeHolder : recipes) {
            Recipe<?> recipe = recipeHolder.value();
            OptionalInt optionalInt;
            if (recipe.group().isEmpty()) {
                optionalInt = OptionalInt.empty();
            } else {
                optionalInt = OptionalInt.of(map.computeIfAbsent(recipe.group(), object -> map.size()));
            }

            Optional<List<Ingredient>> optional;
            if (recipe.isSpecial()) {
                optional = Optional.empty();
            } else {
                optional = Optional.of(recipe.placementInfo().ingredients());
            }

            for (RecipeDisplay recipeDisplay : recipe.display()) {
                if (recipeDisplay.isEnabled(enabledFeatures)) {
                    int size = list.size();
                    RecipeDisplayId recipeDisplayId = new RecipeDisplayId(size);
                    RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(
                        recipeDisplayId, recipeDisplay, optionalInt, recipe.recipeBookCategory(), optional
                    );
                    list.add(new RecipeManager.ServerDisplayInfo(recipeDisplayEntry, recipeHolder));
                }
            }
        }

        return list;
    }

    private static RecipeManager.IngredientExtractor forSingleInput(RecipeType<? extends SingleItemRecipe> recipeType) {
        return recipe -> recipe.getType() == recipeType && recipe instanceof SingleItemRecipe singleItemRecipe
            ? Optional.of(singleItemRecipe.input())
            : Optional.empty();
    }

    public interface CachedCheck<I extends RecipeInput, T extends Recipe<I>> {
        Optional<RecipeHolder<T>> getRecipeFor(I input, ServerLevel level);
    }

    public static class IngredientCollector implements Consumer<Recipe<?>> {
        final ResourceKey<RecipePropertySet> key;
        private final RecipeManager.IngredientExtractor extractor;
        private final List<Ingredient> ingredients = new ArrayList<>();

        protected IngredientCollector(ResourceKey<RecipePropertySet> key, RecipeManager.IngredientExtractor extractor) {
            this.key = key;
            this.extractor = extractor;
        }

        @Override
        public void accept(Recipe<?> recipe) {
            this.extractor.apply(recipe).ifPresent(this.ingredients::add);
        }

        public RecipePropertySet asPropertySet(FeatureFlagSet enabledFeatures) {
            return RecipePropertySet.create(RecipeManager.filterDisabled(enabledFeatures, this.ingredients));
        }
    }

    @FunctionalInterface
    public interface IngredientExtractor {
        Optional<Ingredient> apply(Recipe<?> recipe);
    }

    public record ServerDisplayInfo(RecipeDisplayEntry display, RecipeHolder<?> parent) {
    }
}
