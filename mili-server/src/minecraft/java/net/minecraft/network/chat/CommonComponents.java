package net.minecraft.network.chat;

import java.util.Arrays;
import java.util.Collection;

public class CommonComponents {
    public static final Component EMPTY = Component.empty();
    public static final Component OPTION_ON = Component.translatable("options.on");
    public static final Component OPTION_OFF = Component.translatable("options.off");
    public static final Component GUI_DONE = Component.translatable("gui.done");
    public static final Component GUI_CANCEL = Component.translatable("gui.cancel");
    public static final Component GUI_YES = Component.translatable("gui.yes");
    public static final Component GUI_NO = Component.translatable("gui.no");
    public static final Component GUI_OK = Component.translatable("gui.ok");
    public static final Component GUI_PROCEED = Component.translatable("gui.proceed");
    public static final Component GUI_CONTINUE = Component.translatable("gui.continue");
    public static final Component GUI_BACK = Component.translatable("gui.back");
    public static final Component GUI_TO_TITLE = Component.translatable("gui.toTitle");
    public static final Component GUI_ACKNOWLEDGE = Component.translatable("gui.acknowledge");
    public static final Component GUI_OPEN_IN_BROWSER = Component.translatable("chat.link.open");
    public static final Component GUI_COPY_TO_CLIPBOARD = Component.translatable("chat.copy");
    public static final Component GUI_COPY_LINK_TO_CLIPBOARD = Component.translatable("gui.copy_link_to_clipboard");
    public static final Component GUI_DISCONNECT = Component.translatable("menu.disconnect");
    public static final Component GUI_RETURN_TO_MENU = Component.translatable("menu.returnToMenu");
    public static final Component TRANSFER_CONNECT_FAILED = Component.translatable("connect.failed.transfer");
    public static final Component CONNECT_FAILED = Component.translatable("connect.failed");
    public static final Component NEW_LINE = Component.literal("\n");
    public static final Component NARRATION_SEPARATOR = Component.literal(". ");
    public static final Component ELLIPSIS = Component.literal("...");
    public static final Component SPACE = space();

    public static MutableComponent space() {
        return Component.literal(" ");
    }

    public static MutableComponent days(long days) {
        return Component.translatable("gui.days", days);
    }

    public static MutableComponent hours(long hours) {
        return Component.translatable("gui.hours", hours);
    }

    public static MutableComponent minutes(long minutes) {
        return Component.translatable("gui.minutes", minutes);
    }

    public static Component optionStatus(boolean isEnabled) {
        return isEnabled ? OPTION_ON : OPTION_OFF;
    }

    public static Component disconnectButtonLabel(boolean isSinglePlayer) {
        return isSinglePlayer ? GUI_RETURN_TO_MENU : GUI_DISCONNECT;
    }

    public static MutableComponent optionStatus(Component message, boolean composed) {
        return Component.translatable(composed ? "options.on.composed" : "options.off.composed", message);
    }

    public static MutableComponent optionNameValue(Component caption, Component valueMessage) {
        return Component.translatable("options.generic_value", caption, valueMessage);
    }

    public static MutableComponent joinForNarration(Component... components) {
        MutableComponent mutableComponent = Component.empty();

        for (int i = 0; i < components.length; i++) {
            mutableComponent.append(components[i]);
            if (i != components.length - 1) {
                mutableComponent.append(NARRATION_SEPARATOR);
            }
        }

        return mutableComponent;
    }

    public static Component joinLines(Component... lines) {
        return joinLines(Arrays.asList(lines));
    }

    public static Component joinLines(Collection<? extends Component> lines) {
        return ComponentUtils.formatList(lines, NEW_LINE);
    }
}
