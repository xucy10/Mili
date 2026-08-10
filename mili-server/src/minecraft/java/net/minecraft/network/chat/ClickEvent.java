package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

public interface ClickEvent {
    Codec<ClickEvent> CODEC = ClickEvent.Action.CODEC.dispatch("action", ClickEvent::action, action -> action.codec);

    ClickEvent.Action action();

    public static enum Action implements StringRepresentable {
        OPEN_URL("open_url", true, ClickEvent.OpenUrl.CODEC),
        OPEN_FILE("open_file", false, ClickEvent.OpenFile.CODEC),
        RUN_COMMAND("run_command", true, ClickEvent.RunCommand.CODEC),
        SUGGEST_COMMAND("suggest_command", true, ClickEvent.SuggestCommand.CODEC),
        SHOW_DIALOG("show_dialog", true, ClickEvent.ShowDialog.CODEC),
        CHANGE_PAGE("change_page", true, ClickEvent.ChangePage.CODEC),
        COPY_TO_CLIPBOARD("copy_to_clipboard", true, ClickEvent.CopyToClipboard.CODEC),
        CUSTOM("custom", true, ClickEvent.Custom.CODEC);

        public static final Codec<ClickEvent.Action> UNSAFE_CODEC = StringRepresentable.fromEnum(ClickEvent.Action::values);
        public static final Codec<ClickEvent.Action> CODEC = UNSAFE_CODEC.validate(ClickEvent.Action::filterForSerialization);
        private final boolean allowFromServer;
        private final String name;
        final MapCodec<? extends ClickEvent> codec;

        private Action(final String name, final boolean allowFromServer, final MapCodec<? extends ClickEvent> codec) {
            this.name = name;
            this.allowFromServer = allowFromServer;
            this.codec = codec;
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public MapCodec<? extends ClickEvent> valueCodec() {
            return this.codec;
        }

        public static DataResult<ClickEvent.Action> filterForSerialization(ClickEvent.Action action) {
            return !action.isAllowedFromServer()
                ? DataResult.error(() -> "Click event type not allowed: " + action)
                : DataResult.success(action, Lifecycle.stable());
        }
    }

    public record ChangePage(int page) implements ClickEvent {
        public static final MapCodec<ClickEvent.ChangePage> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ExtraCodecs.POSITIVE_INT.fieldOf("page").forGetter(ClickEvent.ChangePage::page))
                .apply(instance, ClickEvent.ChangePage::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.CHANGE_PAGE;
        }
    }

    public record CopyToClipboard(String value) implements ClickEvent {
        public static final MapCodec<ClickEvent.CopyToClipboard> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Codec.STRING.fieldOf("value").forGetter(ClickEvent.CopyToClipboard::value))
                .apply(instance, ClickEvent.CopyToClipboard::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.COPY_TO_CLIPBOARD;
        }
    }

    public record Custom(Identifier id, Optional<Tag> payload) implements ClickEvent {
        public static final MapCodec<ClickEvent.Custom> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("id").forGetter(ClickEvent.Custom::id),
                    ExtraCodecs.NBT.optionalFieldOf("payload").forGetter(ClickEvent.Custom::payload)
                )
                .apply(instance, ClickEvent.Custom::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.CUSTOM;
        }
    }

    public record OpenFile(String path) implements ClickEvent {
        public static final MapCodec<ClickEvent.OpenFile> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Codec.STRING.fieldOf("path").forGetter(ClickEvent.OpenFile::path)).apply(instance, ClickEvent.OpenFile::new)
        );

        public OpenFile(File file) {
            this(file.toString());
        }

        public OpenFile(Path file) {
            this(file.toFile());
        }

        public File file() {
            return new File(this.path);
        }

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.OPEN_FILE;
        }
    }

    public record OpenUrl(URI uri) implements ClickEvent {
        public static final MapCodec<ClickEvent.OpenUrl> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ExtraCodecs.UNTRUSTED_URI.fieldOf("url").forGetter(ClickEvent.OpenUrl::uri)).apply(instance, ClickEvent.OpenUrl::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.OPEN_URL;
        }
    }

    public record RunCommand(String command) implements ClickEvent {
        public static final MapCodec<ClickEvent.RunCommand> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ExtraCodecs.CHAT_STRING.fieldOf("command").forGetter(ClickEvent.RunCommand::command))
                .apply(instance, ClickEvent.RunCommand::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.RUN_COMMAND;
        }
    }

    public record ShowDialog(Holder<Dialog> dialog) implements ClickEvent {
        public static final MapCodec<ClickEvent.ShowDialog> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Dialog.CODEC.fieldOf("dialog").forGetter(ClickEvent.ShowDialog::dialog)).apply(instance, ClickEvent.ShowDialog::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.SHOW_DIALOG;
        }
    }

    public record SuggestCommand(String command) implements ClickEvent {
        public static final MapCodec<ClickEvent.SuggestCommand> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ExtraCodecs.CHAT_STRING.fieldOf("command").forGetter(ClickEvent.SuggestCommand::command))
                .apply(instance, ClickEvent.SuggestCommand::new)
        );

        @Override
        public ClickEvent.Action action() {
            return ClickEvent.Action.SUGGEST_COMMAND;
        }
    }
}
