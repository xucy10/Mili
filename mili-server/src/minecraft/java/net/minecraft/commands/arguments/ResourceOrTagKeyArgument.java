package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class ResourceOrTagKeyArgument<T> implements ArgumentType<ResourceOrTagKeyArgument.Result<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons");
    final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceOrTagKeyArgument(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    public static <T> ResourceOrTagKeyArgument<T> resourceOrTagKey(ResourceKey<? extends Registry<T>> registryKey) {
        return new ResourceOrTagKeyArgument<>(registryKey);
    }

    public static <T> ResourceOrTagKeyArgument.Result<T> getResourceOrTagKey(
        CommandContext<CommandSourceStack> context,
        String argument,
        ResourceKey<Registry<T>> registryKey,
        DynamicCommandExceptionType dynamicCommandExceptionType
    ) throws CommandSyntaxException {
        ResourceOrTagKeyArgument.Result<?> result = context.getArgument(argument, ResourceOrTagKeyArgument.Result.class);
        Optional<ResourceOrTagKeyArgument.Result<T>> optional = result.cast(registryKey);
        return optional.orElseThrow(() -> dynamicCommandExceptionType.create(result));
    }

    @Override
    public ResourceOrTagKeyArgument.Result<T> parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int cursor = reader.getCursor();

            try {
                reader.skip();
                Identifier identifier = Identifier.read(reader);
                return new ResourceOrTagKeyArgument.TagResult<>(TagKey.create(this.registryKey, identifier));
            } catch (CommandSyntaxException var4) {
                reader.setCursor(cursor);
                throw var4;
            }
        } else {
            Identifier identifier1 = Identifier.read(reader);
            return new ResourceOrTagKeyArgument.ResourceResult<>(ResourceKey.create(this.registryKey, identifier1));
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.listSuggestions(context, builder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ALL);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ResourceOrTagKeyArgument.Info<T>.Template> {
        @Override
        public void serializeToNetwork(ResourceOrTagKeyArgument.Info<T>.Template template, FriendlyByteBuf buffer) {
            buffer.writeResourceKey(template.registryKey);
        }

        @Override
        public ResourceOrTagKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new ResourceOrTagKeyArgument.Info.Template(buffer.readRegistryKey());
        }

        @Override
        public void serializeToJson(ResourceOrTagKeyArgument.Info<T>.Template template, JsonObject json) {
            json.addProperty("registry", template.registryKey.identifier().toString());
        }

        @Override
        public ResourceOrTagKeyArgument.Info<T>.Template unpack(ResourceOrTagKeyArgument<T> argument) {
            return new ResourceOrTagKeyArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceOrTagKeyArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(final ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            @Override
            public ResourceOrTagKeyArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceOrTagKeyArgument<>(this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceOrTagKeyArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }

    record ResourceResult<T>(ResourceKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {
        @Override
        public Either<ResourceKey<T>, TagKey<T>> unwrap() {
            return Either.left(this.key);
        }

        @Override
        public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.key.cast(registryKey).map(ResourceOrTagKeyArgument.ResourceResult::new);
        }

        @Override
        public boolean test(Holder<T> holder) {
            return holder.is(this.key);
        }

        @Override
        public String asPrintable() {
            return this.key.identifier().toString();
        }
    }

    public interface Result<T> extends Predicate<Holder<T>> {
        Either<ResourceKey<T>, TagKey<T>> unwrap();

        <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey);

        String asPrintable();
    }

    record TagResult<T>(TagKey<T> key) implements ResourceOrTagKeyArgument.Result<T> {
        @Override
        public Either<ResourceKey<T>, TagKey<T>> unwrap() {
            return Either.right(this.key);
        }

        @Override
        public <E> Optional<ResourceOrTagKeyArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.key.cast(registryKey).map(ResourceOrTagKeyArgument.TagResult::new);
        }

        @Override
        public boolean test(Holder<T> holder) {
            return holder.is(this.key);
        }

        @Override
        public String asPrintable() {
            return "#" + this.key.location();
        }
    }
}
