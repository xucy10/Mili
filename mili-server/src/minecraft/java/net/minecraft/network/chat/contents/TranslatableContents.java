package net.minecraft.network.chat.contents;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class TranslatableContents implements ComponentContents {
    public static final Object[] NO_ARGS = new Object[0];
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.JAVA.validate(TranslatableContents::filterAllowedArguments);
    private static final Codec<Object> ARG_CODEC = Codec.either(PRIMITIVE_ARG_CODEC, ComponentSerialization.CODEC)
        .xmap(
            arg -> arg.map(arg1 -> arg1, text -> Objects.requireNonNullElse(text.tryCollapseToString(), text)),
            arg -> arg instanceof Component component ? Either.right(component) : Either.left(arg)
        );
    public static final MapCodec<TranslatableContents> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.STRING.fieldOf("translate").forGetter(contents -> contents.key),
                Codec.STRING.lenientOptionalFieldOf("fallback").forGetter(contents -> Optional.ofNullable(contents.fallback)),
                ARG_CODEC.listOf().optionalFieldOf("with").forGetter(contents -> adjustArgs(contents.args))
            )
            .apply(instance, TranslatableContents::create)
    );
    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private final String key;
    private final @Nullable String fallback;
    private final Object[] args;
    private @Nullable Language decomposedWith;
    private List<FormattedText> decomposedParts = ImmutableList.of();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    public static DataResult<Object> filterAllowedArguments(@Nullable Object input) {
        return !isAllowedPrimitiveArgument(input) ? DataResult.error(() -> "This value needs to be parsed as component") : DataResult.success(input);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object input) {
        return input instanceof Number || input instanceof Boolean || input instanceof String;
    }

    private static Optional<List<Object>> adjustArgs(Object[] args) {
        return args.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(args));
    }

    private static Object[] adjustArgs(Optional<List<Object>> args) {
        return args.<Object[]>map(arg -> arg.isEmpty() ? NO_ARGS : arg.toArray()).orElse(NO_ARGS);
    }

    private static TranslatableContents create(String key, Optional<String> fallback, Optional<List<Object>> args) {
        return new TranslatableContents(key, fallback.orElse(null), adjustArgs(args));
    }

    public TranslatableContents(String key, @Nullable String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    @Override
    public MapCodec<TranslatableContents> codec() {
        return MAP_CODEC;
    }

    private void decompose() {
        Language instance = Language.getInstance();
        if (instance != this.decomposedWith) {
            this.decomposedWith = instance;
            String string = this.fallback != null ? instance.getOrDefault(this.key, this.fallback) : instance.getOrDefault(this.key);

            try {
                Builder<FormattedText> builder = ImmutableList.builder();
                this.decomposeTemplate(string, builder::add);
                this.decomposedParts = builder.build();
            } catch (TranslatableFormatException var4) {
                this.decomposedParts = ImmutableList.of(FormattedText.of(string));
            }
        }
    }

    private void decomposeTemplate(String formatTemplate, Consumer<FormattedText> consumer) {
        Matcher matcher = FORMAT_PATTERN.matcher(formatTemplate);

        try {
            int i = 0;
            int i1 = 0;

            while (matcher.find(i1)) {
                int i2 = matcher.start();
                int i3 = matcher.end();
                if (i2 > i1) {
                    String sub = formatTemplate.substring(i1, i2);
                    if (sub.indexOf(37) != -1) {
                        throw new IllegalArgumentException();
                    }

                    consumer.accept(FormattedText.of(sub));
                }

                String sub = matcher.group(2);
                String sub1 = formatTemplate.substring(i2, i3);
                if ("%".equals(sub) && "%%".equals(sub1)) {
                    consumer.accept(TEXT_PERCENT);
                } else {
                    if (!"s".equals(sub)) {
                        throw new TranslatableFormatException(this, "Unsupported format: '" + sub1 + "'");
                    }

                    String string = matcher.group(1);
                    int i4 = string != null ? Integer.parseInt(string) - 1 : i++;
                    consumer.accept(this.getArgument(i4));
                }

                i1 = i3;
            }

            if (i1 < formatTemplate.length()) {
                String sub2 = formatTemplate.substring(i1);
                if (sub2.indexOf(37) != -1) {
                    throw new IllegalArgumentException();
                }

                consumer.accept(FormattedText.of(sub2));
            }
        } catch (IllegalArgumentException var12) {
            throw new TranslatableFormatException(this, var12);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < this.args.length) {
            Object object = this.args[index];
            if (object instanceof Component component) {
                return component;
            } else {
                return object == null ? TEXT_NULL : FormattedText.of(object.toString());
            }
        } else {
            throw new TranslatableFormatException(this, index);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        this.decompose();

        for (FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(styledContentConsumer, style);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        // Paper start - Count visited parts
        try {
            return this.visit(new TranslatableContentConsumer<>(contentConsumer));
        } catch (IllegalArgumentException ignored) {
            return contentConsumer.accept("...");
        }
    }

    private <T> Optional<T> visit(TranslatableContentConsumer<T> contentConsumer) {
        // Paper end - Count visited parts
        this.decompose();

        for (FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(contentConsumer);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    // Paper start - Count visited parts
    private static final class TranslatableContentConsumer<T> implements FormattedText.ContentConsumer<T> {
        private static final IllegalArgumentException NESTED_TOO_LONG = new IllegalArgumentException("Too long");

        private final FormattedText.ContentConsumer<T> visitor;
        private int visited;

        private TranslatableContentConsumer(final FormattedText.ContentConsumer<T> visitor) {
            this.visitor = visitor;
        }

        @Override
        public Optional<T> accept(final String asString) {
            if (this.visited++ > 32) {
                throw NESTED_TOO_LONG;
            }
            return this.visitor.accept(asString);
        }
    }
    // Paper end - Count visited parts

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        Object[] objects = new Object[this.args.length];

        for (int i = 0; i < objects.length; i++) {
            Object object = this.args[i];
            if (object instanceof Component component) {
                objects[i] = ComponentUtils.updateForEntity(source, component, entity, recursionDepth);
            } else {
                objects[i] = object;
            }
        }

        return MutableComponent.create(new TranslatableContents(this.key, this.fallback, objects));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof TranslatableContents translatableContents
                && Objects.equals(this.key, translatableContents.key)
                && Objects.equals(this.fallback, translatableContents.fallback)
                && Arrays.equals(this.args, translatableContents.args);
    }

    @Override
    public int hashCode() {
        int hashCode = Objects.hashCode(this.key);
        hashCode = 31 * hashCode + Objects.hashCode(this.fallback);
        return 31 * hashCode + Arrays.hashCode(this.args);
    }

    @Override
    public String toString() {
        return "translation{key='"
            + this.key
            + "'"
            + (this.fallback != null ? ", fallback='" + this.fallback + "'" : "")
            + ", args="
            + Arrays.toString(this.args)
            + "}";
    }

    public String getKey() {
        return this.key;
    }

    public @Nullable String getFallback() {
        return this.fallback;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
