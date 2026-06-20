package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public class ParticleArgument implements ArgumentType<ParticleOptions> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "particle{foo:bar}");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType(
        particle -> Component.translatableEscape("particle.notFound", particle)
    );
    public static final DynamicCommandExceptionType ERROR_INVALID_OPTIONS = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("particle.invalidOptions", object)
    );
    private final HolderLookup.Provider registries;
    private static final TagParser<?> VALUE_PARSER = TagParser.create(NbtOps.INSTANCE);

    public ParticleArgument(CommandBuildContext buildContext) {
        this.registries = buildContext;
    }

    public static ParticleArgument particle(CommandBuildContext buildContext) {
        return new ParticleArgument(buildContext);
    }

    public static ParticleOptions getParticle(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ParticleOptions.class);
    }

    @Override
    public ParticleOptions parse(StringReader reader) throws CommandSyntaxException {
        return readParticle(reader, this.registries);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static ParticleOptions readParticle(StringReader reader, HolderLookup.Provider registries) throws CommandSyntaxException {
        ParticleType<?> particleType = readParticleType(reader, registries.lookupOrThrow(Registries.PARTICLE_TYPE));
        return readParticle(VALUE_PARSER, reader, (ParticleType<ParticleOptions>)particleType, registries);
    }

    private static ParticleType<?> readParticleType(StringReader reader, HolderLookup<ParticleType<?>> particleTypeLookup) throws CommandSyntaxException {
        Identifier identifier = Identifier.read(reader);
        ResourceKey<ParticleType<?>> resourceKey = ResourceKey.create(Registries.PARTICLE_TYPE, identifier);
        return particleTypeLookup.get(resourceKey).orElseThrow(() -> ERROR_UNKNOWN_PARTICLE.createWithContext(reader, identifier)).value();
    }

    private static <T extends ParticleOptions, O> T readParticle(
        TagParser<O> parser, StringReader reader, ParticleType<T> particleType, HolderLookup.Provider registries
    ) throws CommandSyntaxException {
        RegistryOps<O> registryOps = registries.createSerializationContext(parser.getOps());
        O object;
        if (reader.canRead() && reader.peek() == '{') {
            object = parser.parseAsArgument(reader);
        } else {
            object = registryOps.emptyMap();
        }

        return particleType.codec().codec().parse(registryOps, object).getOrThrow(ERROR_INVALID_OPTIONS::create);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        HolderLookup.RegistryLookup<ParticleType<?>> registryLookup = this.registries.lookupOrThrow(Registries.PARTICLE_TYPE);
        return SharedSuggestionProvider.suggestResource(registryLookup.listElementIds().map(ResourceKey::identifier), builder);
    }
}
