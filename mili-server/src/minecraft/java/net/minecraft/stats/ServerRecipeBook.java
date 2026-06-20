package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.Logger;

public class ServerRecipeBook extends RecipeBook {
    public static final String RECIPE_BOOK_TAG = "recipeBook";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerRecipeBook.DisplayResolver displayResolver;
    @VisibleForTesting
    public final Set<ResourceKey<Recipe<?>>> known = Sets.newIdentityHashSet();
    @VisibleForTesting
    protected final Set<ResourceKey<Recipe<?>>> highlight = Sets.newIdentityHashSet();

    public ServerRecipeBook(ServerRecipeBook.DisplayResolver displayResolver) {
        this.displayResolver = displayResolver;
    }

    public void add(ResourceKey<Recipe<?>> recipe) {
        this.known.add(recipe);
    }

    public boolean contains(ResourceKey<Recipe<?>> recipe) {
        return this.known.contains(recipe);
    }

    public void remove(ResourceKey<Recipe<?>> recipe) {
        this.known.remove(recipe);
        this.highlight.remove(recipe);
    }

    public void removeHighlight(ResourceKey<Recipe<?>> recipe) {
        this.highlight.remove(recipe);
    }

    private void addHighlight(ResourceKey<Recipe<?>> recipe) {
        this.highlight.add(recipe);
    }

    public int addRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>();

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
            if (!this.known.contains(resourceKey) && !recipeHolder.value().isSpecial()) {
                // Paper start - PlayerRecipeDiscoverEvent event
                final org.bukkit.event.player.PlayerRecipeDiscoverEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerRecipeListUpdateEvent(player, recipeHolder);
                if (event.isCancelled()) continue;
                // Paper end - PlayerRecipeDiscoverEvent event
                this.add(resourceKey);
                this.addHighlight(resourceKey);
                this.displayResolver
                    .displaysForRecipe(
                        resourceKey, entry -> list.add(new ClientboundRecipeBookAddPacket.Entry(entry, event.shouldShowNotification(), true)) // Paper - set notification from the event
                    );
                CriteriaTriggers.RECIPE_UNLOCKED.trigger(player, recipeHolder);
            }
        }

        if (!list.isEmpty() && player.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            player.connection.send(new ClientboundRecipeBookAddPacket(list, false));
        }

        return list.size();
    }

    public int removeRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<RecipeDisplayId> list = Lists.newArrayList();

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
            if (this.known.contains(resourceKey)) {
                this.remove(resourceKey);
                this.displayResolver.displaysForRecipe(resourceKey, entry -> list.add(entry.id()));
            }
        }

        if (!list.isEmpty() && player.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            player.connection.send(new ClientboundRecipeBookRemovePacket(list));
        }

        return list.size();
    }

    private void loadRecipes(List<ResourceKey<Recipe<?>>> recipes, Consumer<ResourceKey<Recipe<?>>> output, Predicate<ResourceKey<Recipe<?>>> isRecognized) {
        for (ResourceKey<Recipe<?>> resourceKey : recipes) {
            if (!isRecognized.test(resourceKey)) {
                LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourceKey);
            } else {
                output.accept(resourceKey);
            }
        }
    }

    public void sendInitialRecipeBook(ServerPlayer player) {
        player.connection.send(new ClientboundRecipeBookSettingsPacket(this.getBookSettings().copy()));
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>(this.known.size());

        for (ResourceKey<Recipe<?>> resourceKey : this.known) {
            this.displayResolver
                .displaysForRecipe(resourceKey, entry -> list.add(new ClientboundRecipeBookAddPacket.Entry(entry, false, this.highlight.contains(resourceKey))));
        }

        player.connection.send(new ClientboundRecipeBookAddPacket(list, true));
    }

    public void copyOverData(ServerRecipeBook other) {
        this.apply(other.pack());
    }

    public ServerRecipeBook.Packed pack() {
        return new ServerRecipeBook.Packed(this.bookSettings.copy(), List.copyOf(this.known), List.copyOf(this.highlight));
    }

    private void apply(ServerRecipeBook.Packed recipeBook) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(recipeBook.settings);
        this.known.addAll(recipeBook.known);
        this.highlight.addAll(recipeBook.highlight);
    }

    public void loadUntrusted(ServerRecipeBook.Packed recipeBook, Predicate<ResourceKey<Recipe<?>>> predicate) {
        this.bookSettings.replaceFrom(recipeBook.settings);
        this.loadRecipes(recipeBook.known, this.known::add, predicate);
        this.loadRecipes(recipeBook.highlight, this.highlight::add, predicate);
    }

    @FunctionalInterface
    public interface DisplayResolver {
        void displaysForRecipe(ResourceKey<Recipe<?>> recipe, Consumer<RecipeDisplayEntry> output);
    }

    public record Packed(RecipeBookSettings settings, List<ResourceKey<Recipe<?>>> known, List<ResourceKey<Recipe<?>>> highlight) {
        public static final Codec<ServerRecipeBook.Packed> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    RecipeBookSettings.MAP_CODEC.forGetter(ServerRecipeBook.Packed::settings),
                    Recipe.KEY_CODEC.listOf().fieldOf("recipes").forGetter(ServerRecipeBook.Packed::known),
                    Recipe.KEY_CODEC.listOf().fieldOf("toBeDisplayed").forGetter(ServerRecipeBook.Packed::highlight)
                )
                .apply(instance, ServerRecipeBook.Packed::new)
        );
    }
}
