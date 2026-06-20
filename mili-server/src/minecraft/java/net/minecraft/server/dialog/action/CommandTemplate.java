package net.minecraft.server.dialog.action;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;

public record CommandTemplate(ParsedTemplate template) implements Action {
    public static final MapCodec<CommandTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ParsedTemplate.CODEC.fieldOf("template").forGetter(CommandTemplate::template)).apply(instance, CommandTemplate::new)
    );

    @Override
    public MapCodec<CommandTemplate> codec() {
        return MAP_CODEC;
    }

    @Override
    public Optional<ClickEvent> createAction(Map<String, Action.ValueGetter> valueGetters) {
        String string = this.template.instantiate(Action.ValueGetter.getAsTemplateSubstitutions(valueGetters));
        return Optional.of(new ClickEvent.RunCommand(string));
    }
}
