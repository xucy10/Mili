package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class ResourceKeyArgument<T> implements ArgumentType<ResourceKey<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_INVALID_FEATURE = new DynamicCommandExceptionType(
        featureType -> Component.translatableEscape("commands.place.feature.invalid", featureType)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_STRUCTURE = new DynamicCommandExceptionType(
        structureType -> Component.translatableEscape("commands.place.structure.invalid", structureType)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_TEMPLATE_POOL = new DynamicCommandExceptionType(
        templatePoolType -> Component.translatableEscape("commands.place.jigsaw.invalid", templatePoolType)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_RECIPE = new DynamicCommandExceptionType(
        recipe -> Component.translatableEscape("recipe.notFound", recipe)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_ADVANCEMENT = new DynamicCommandExceptionType(
        advancement -> Component.translatableEscape("advancement.advancementNotFound", advancement)
    );
    final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceKeyArgument(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    public static <T> ResourceKeyArgument<T> key(ResourceKey<? extends Registry<T>> registryKey) {
        return new ResourceKeyArgument<>(registryKey);
    }

    public static <T> ResourceKey<T> getRegistryKey(
        CommandContext<CommandSourceStack> context, String argument, ResourceKey<Registry<T>> registryKey, DynamicCommandExceptionType exception
    ) throws CommandSyntaxException {
        ResourceKey<?> resourceKey = context.getArgument(argument, ResourceKey.class);
        Optional<ResourceKey<T>> optional = resourceKey.cast(registryKey);
        return optional.orElseThrow(() -> exception.create(resourceKey.identifier()));
    }

    private static <T> Registry<T> getRegistry(CommandContext<CommandSourceStack> context, ResourceKey<? extends Registry<T>> registryKey) {
        return context.getSource().getServer().registryAccess().lookupOrThrow(registryKey);
    }

    private static <T> Holder.Reference<T> resolveKey(
        CommandContext<CommandSourceStack> context, String argument, ResourceKey<Registry<T>> registryKey, DynamicCommandExceptionType exception
    ) throws CommandSyntaxException {
        ResourceKey<T> registryKey1 = getRegistryKey(context, argument, registryKey, exception);
        return getRegistry(context, registryKey).get(registryKey1).orElseThrow(() -> exception.create(registryKey1.identifier()));
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return resolveKey(context, argument, Registries.CONFIGURED_FEATURE, ERROR_INVALID_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return resolveKey(context, argument, Registries.STRUCTURE, ERROR_INVALID_STRUCTURE);
    }

    public static Holder.Reference<StructureTemplatePool> getStructureTemplatePool(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return resolveKey(context, argument, Registries.TEMPLATE_POOL, ERROR_INVALID_TEMPLATE_POOL);
    }

    public static RecipeHolder<?> getRecipe(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        RecipeManager recipeManager = context.getSource().getServer().getRecipeManager();
        ResourceKey<Recipe<?>> registryKey = getRegistryKey(context, argument, Registries.RECIPE, ERROR_INVALID_RECIPE);
        return recipeManager.byKey(registryKey).orElseThrow(() -> ERROR_INVALID_RECIPE.create(registryKey.identifier()));
    }

    public static AdvancementHolder getAdvancement(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        ResourceKey<Advancement> registryKey = getRegistryKey(context, argument, Registries.ADVANCEMENT, ERROR_INVALID_ADVANCEMENT);
        AdvancementHolder advancementHolder = context.getSource().getServer().getAdvancements().get(registryKey.identifier());
        if (advancementHolder == null) {
            throw ERROR_INVALID_ADVANCEMENT.create(registryKey.identifier());
        } else {
            return advancementHolder;
        }
    }

    @Override
    public ResourceKey<T> parse(StringReader reader) throws CommandSyntaxException {
        Identifier identifier = Identifier.read(reader);
        return ResourceKey.create(this.registryKey, identifier);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceKeyArgument<T>, ResourceKeyArgument.Info<T>.Template> {
        @Override
        public void serializeToNetwork(ResourceKeyArgument.Info<T>.Template template, FriendlyByteBuf buffer) {
            buffer.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new ResourceKeyArgument.Info.Template(buffer.readRegistryKey());
        }

        @Override
        public void serializeToJson(ResourceKeyArgument.Info<T>.Template template, JsonObject json) {
            json.addProperty("registry", template.registryKey.identifier().toString());
        }

        @Override
        public ResourceKeyArgument.Info<T>.Template unpack(ResourceKeyArgument<T> argument) {
            return new ResourceKeyArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceKeyArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(final ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceKeyArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceKeyArgument<>(this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceKeyArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
