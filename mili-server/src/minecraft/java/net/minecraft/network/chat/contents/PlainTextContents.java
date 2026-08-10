package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public interface PlainTextContents extends ComponentContents {
    MapCodec<PlainTextContents> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.STRING.fieldOf("text").forGetter(PlainTextContents::text)).apply(instance, PlainTextContents::create)
    );
    PlainTextContents EMPTY = new PlainTextContents() {
        @Override
        public String toString() {
            return "empty";
        }

        @Override
        public String text() {
            return "";
        }
    };

    static PlainTextContents create(String text) {
        return (PlainTextContents)(text.isEmpty() ? EMPTY : new PlainTextContents.LiteralContents(text));
    }

    String text();

    @Override
    default MapCodec<PlainTextContents> codec() {
        return MAP_CODEC;
    }

    public record LiteralContents(@Override String text) implements PlainTextContents {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
            return contentConsumer.accept(this.text);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
            return styledContentConsumer.accept(style, this.text);
        }

        @Override
        public String toString() {
            return "literal{" + this.text + "}";
        }
    }
}
