package net.minecraft.data.tags;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public interface TagAppender<E, T> {
    TagAppender<E, T> add(E element);

    default TagAppender<E, T> add(E... elements) {
        return this.addAll(Arrays.stream(elements));
    }

    default TagAppender<E, T> addAll(Collection<E> elements) {
        elements.forEach(this::add);
        return this;
    }

    default TagAppender<E, T> addAll(Stream<E> elements) {
        elements.forEach(this::add);
        return this;
    }

    TagAppender<E, T> addOptional(E element);

    TagAppender<E, T> addTag(TagKey<T> tag);

    TagAppender<E, T> addOptionalTag(TagKey<T> tag);

    static <T> TagAppender<ResourceKey<T>, T> forBuilder(final TagBuilder builder) {
        return new TagAppender<ResourceKey<T>, T>() {
            @Override
            public TagAppender<ResourceKey<T>, T> add(ResourceKey<T> element) {
                builder.addElement(element.identifier());
                return this;
            }

            @Override
            public TagAppender<ResourceKey<T>, T> addOptional(ResourceKey<T> element) {
                builder.addOptionalElement(element.identifier());
                return this;
            }

            @Override
            public TagAppender<ResourceKey<T>, T> addTag(TagKey<T> tag) {
                builder.addTag(tag.location());
                return this;
            }

            @Override
            public TagAppender<ResourceKey<T>, T> addOptionalTag(TagKey<T> tag) {
                builder.addOptionalTag(tag.location());
                return this;
            }
        };
    }

    default <U> TagAppender<U, T> map(final Function<U, E> mapper) {
        final TagAppender<E, T> tagAppender = this;
        return new TagAppender<U, T>() {
            @Override
            public TagAppender<U, T> add(U element) {
                tagAppender.add(mapper.apply(element));
                return this;
            }

            @Override
            public TagAppender<U, T> addOptional(U element) {
                tagAppender.add(mapper.apply(element));
                return this;
            }

            @Override
            public TagAppender<U, T> addTag(TagKey<T> tag) {
                tagAppender.addTag(tag);
                return this;
            }

            @Override
            public TagAppender<U, T> addOptionalTag(TagKey<T> tag) {
                tagAppender.addOptionalTag(tag);
                return this;
            }
        };
    }
}
