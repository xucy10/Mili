package net.minecraft.network.chat.contents;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public record SelectorContents(SelectorPattern selector, Optional<Component> separator) implements ComponentContents {
    public static final MapCodec<SelectorContents> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                SelectorPattern.CODEC.fieldOf("selector").forGetter(SelectorContents::selector),
                ComponentSerialization.CODEC.optionalFieldOf("separator").forGetter(SelectorContents::separator)
            )
            .apply(instance, SelectorContents::new)
    );

    @Override
    public MapCodec<SelectorContents> codec() {
        return MAP_CODEC;
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack source, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (source == null) {
            return Component.empty();
        } else {
            Optional<? extends Component> optional = ComponentUtils.updateSeparatorForEntity(source, this.separator, entity, recursionDepth); // Paper - validate separator
            return ComponentUtils.formatList(this.selector.resolved().findEntities(source), optional, Entity::getDisplayName);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        return styledContentConsumer.accept(style, this.selector.pattern());
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        return contentConsumer.accept(this.selector.pattern());
    }

    @Override
    public String toString() {
        return "pattern{" + this.selector + "}";
    }
}
