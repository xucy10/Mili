package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

public class SignText {
    private static final Codec<Component[]> LINES_CODEC = ComponentSerialization.CODEC
        .listOf()
        .comapFlatMap(
            lineComponents -> Util.fixedSize((List<Component>)lineComponents, 4)
                .map(lines -> new Component[]{lines.get(0), lines.get(1), lines.get(2), lines.get(3)}),
            lineComponents -> List.of(lineComponents[0], lineComponents[1], lineComponents[2], lineComponents[3])
        );
    public static final Codec<SignText> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                LINES_CODEC.fieldOf("messages").forGetter(signText -> signText.messages),
                LINES_CODEC.lenientOptionalFieldOf("filtered_messages").forGetter(SignText::filteredMessages),
                DyeColor.CODEC.fieldOf("color").orElse(DyeColor.BLACK).forGetter(signText -> signText.color),
                Codec.BOOL.fieldOf("has_glowing_text").orElse(false).forGetter(signText -> signText.hasGlowingText)
            )
            .apply(instance, SignText::load)
    );
    public static final int LINES = 4;
    private final Component[] messages;
    private final Component[] filteredMessages;
    private final DyeColor color;
    private final boolean hasGlowingText;
    private FormattedCharSequence @Nullable [] renderMessages;
    private boolean renderMessagedFiltered;

    public SignText() {
        this(emptyMessages(), emptyMessages(), DyeColor.BLACK, false);
    }

    public SignText(Component[] messages, Component[] filteredMessages, DyeColor color, boolean hasGlowingText) {
        this.messages = messages;
        this.filteredMessages = filteredMessages;
        this.color = color;
        this.hasGlowingText = hasGlowingText;
    }

    private static Component[] emptyMessages() {
        return new Component[]{CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY};
    }

    private static SignText load(Component[] messages, Optional<Component[]> filteredMessages, DyeColor color, boolean hasGlowingText) {
        return new SignText(messages, filteredMessages.orElse(Arrays.copyOf(messages, messages.length)), color, hasGlowingText);
    }

    public boolean hasGlowingText() {
        return this.hasGlowingText;
    }

    public SignText setHasGlowingText(boolean hasGlowingText) {
        return hasGlowingText == this.hasGlowingText ? this : new SignText(this.messages, this.filteredMessages, this.color, hasGlowingText);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public SignText setColor(DyeColor color) {
        return color == this.getColor() ? this : new SignText(this.messages, this.filteredMessages, color, this.hasGlowingText);
    }

    public Component getMessage(int index, boolean isFiltered) {
        return this.getMessages(isFiltered)[index];
    }

    public SignText setMessage(int index, Component text) {
        return this.setMessage(index, text, text);
    }

    public SignText setMessage(int index, Component text, Component filteredText) {
        Component[] components = Arrays.copyOf(this.messages, this.messages.length);
        Component[] components1 = Arrays.copyOf(this.filteredMessages, this.filteredMessages.length);
        components[index] = text;
        components1[index] = filteredText;
        return new SignText(components, components1, this.color, this.hasGlowingText);
    }

    public boolean hasMessage(Player player) {
        return Arrays.stream(this.getMessages(player.isTextFilteringEnabled())).anyMatch(message -> !message.getString().isEmpty());
    }

    public Component[] getMessages(boolean isFiltered) {
        return isFiltered ? this.filteredMessages : this.messages;
    }

    public FormattedCharSequence[] getRenderMessages(boolean renderMessagesFiltered, Function<Component, FormattedCharSequence> formatter) {
        if (this.renderMessages == null || this.renderMessagedFiltered != renderMessagesFiltered) {
            this.renderMessagedFiltered = renderMessagesFiltered;
            this.renderMessages = new FormattedCharSequence[4];

            for (int i = 0; i < 4; i++) {
                this.renderMessages[i] = formatter.apply(this.getMessage(i, renderMessagesFiltered));
            }
        }

        return this.renderMessages;
    }

    private Optional<Component[]> filteredMessages() {
        for (int i = 0; i < 4; i++) {
            if (!this.filteredMessages[i].equals(this.messages[i])) {
                return Optional.of(this.filteredMessages);
            }
        }

        return Optional.empty();
    }

    public boolean hasAnyClickCommands(Player player) {
        for (Component component : this.getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent != null && clickEvent.action() == ClickEvent.Action.RUN_COMMAND) {
                return true;
            }
        }

        return false;
    }
}
