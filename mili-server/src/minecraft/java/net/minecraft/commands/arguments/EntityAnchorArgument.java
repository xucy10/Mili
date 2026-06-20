package net.minecraft.commands.arguments;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntityAnchorArgument implements ArgumentType<EntityAnchorArgument.Anchor> {
    private static final Collection<String> EXAMPLES = Arrays.asList("eyes", "feet");
    private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(
        anchor -> Component.translatableEscape("argument.anchor.invalid", anchor)
    );

    public static EntityAnchorArgument.Anchor getAnchor(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, EntityAnchorArgument.Anchor.class);
    }

    public static EntityAnchorArgument anchor() {
        return new EntityAnchorArgument();
    }

    @Override
    public EntityAnchorArgument.Anchor parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String unquotedString = reader.readUnquotedString();
        EntityAnchorArgument.Anchor byName = EntityAnchorArgument.Anchor.getByName(unquotedString);
        if (byName == null) {
            reader.setCursor(cursor);
            throw ERROR_INVALID.createWithContext(reader, unquotedString);
        } else {
            return byName;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(EntityAnchorArgument.Anchor.BY_NAME.keySet(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static enum Anchor {
        FEET("feet", (pos, entity) -> pos),
        EYES("eyes", (pos, entity) -> new Vec3(pos.x, pos.y + entity.getEyeHeight(), pos.z));

        static final Map<String, EntityAnchorArgument.Anchor> BY_NAME = Util.make(Maps.newHashMap(), map -> {
            for (EntityAnchorArgument.Anchor anchor : values()) {
                map.put(anchor.name, anchor);
            }
        });
        private final String name;
        private final BiFunction<Vec3, Entity, Vec3> transform;

        private Anchor(final String name, final BiFunction<Vec3, Entity, Vec3> transform) {
            this.name = name;
            this.transform = transform;
        }

        public static EntityAnchorArgument.@Nullable Anchor getByName(String name) {
            return BY_NAME.get(name);
        }

        public Vec3 apply(Entity entity) {
            return this.transform.apply(entity.position(), entity);
        }

        public Vec3 apply(CommandSourceStack source) {
            Entity entity = source.getEntity();
            return entity == null ? source.getPosition() : this.transform.apply(source.getPosition(), entity);
        }
    }
}
