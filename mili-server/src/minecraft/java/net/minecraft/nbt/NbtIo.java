package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtIo {
    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
        StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    };

    public static CompoundTag readCompressed(Path path, NbtAccounter accounter) throws IOException {
        CompoundTag var4;
        try (
            InputStream inputStream = Files.newInputStream(path);
            InputStream inputStream1 = new FastBufferedInputStream(inputStream);
        ) {
            var4 = readCompressed(inputStream1, accounter);
        }

        return var4;
    }

    private static DataInputStream createDecompressorStream(InputStream zippedStream) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(zippedStream)));
    }

    private static DataOutputStream createCompressorStream(OutputStream outputStream) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(outputStream)));
    }

    public static CompoundTag readCompressed(InputStream zippedStream, NbtAccounter accounter) throws IOException {
        CompoundTag var3;
        try (DataInputStream dataInputStream = createDecompressorStream(zippedStream)) {
            var3 = read(dataInputStream, accounter);
        }

        return var3;
    }

    public static void parseCompressed(Path path, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        try (
            InputStream inputStream = Files.newInputStream(path);
            InputStream inputStream1 = new FastBufferedInputStream(inputStream);
        ) {
            parseCompressed(inputStream1, visitor, accounter);
        }
    }

    public static void parseCompressed(InputStream zippedStream, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        try (DataInputStream dataInputStream = createDecompressorStream(zippedStream)) {
            parse(dataInputStream, visitor, accounter);
        }
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        try (
            OutputStream outputStream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
            OutputStream outputStream1 = new BufferedOutputStream(outputStream);
        ) {
            writeCompressed(tag, outputStream1);
        }
    }

    public static void writeCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        try (DataOutputStream dataOutputStream = createCompressorStream(outputStream)) {
            write(tag, dataOutputStream);
        }
    }

    public static void write(CompoundTag tag, Path path) throws IOException {
        try (
            OutputStream outputStream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
            OutputStream outputStream1 = new BufferedOutputStream(outputStream);
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream1);
        ) {
            write(tag, dataOutputStream);
        }
    }

    public static @Nullable CompoundTag read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        } else {
            CompoundTag var3;
            try (
                InputStream inputStream = Files.newInputStream(path);
                DataInputStream dataInputStream = new DataInputStream(inputStream);
            ) {
                var3 = read(dataInputStream, NbtAccounter.unlimitedHeap());
            }

            return var3;
        }
    }

    public static CompoundTag read(DataInput input) throws IOException {
        return read(input, NbtAccounter.unlimitedHeap());
    }

    public static CompoundTag read(DataInput input, NbtAccounter accounter) throws IOException {
        // Spigot start
        if (input instanceof io.netty.buffer.ByteBufInputStream byteBufInputStream) {
            input = new DataInputStream(new org.spigotmc.LimitStream(byteBufInputStream, accounter));
        }
        // Spigot end
        Tag unnamedTag = readUnnamedTag(input, accounter);
        if (unnamedTag instanceof CompoundTag) {
            return (CompoundTag)unnamedTag;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag tag, DataOutput output) throws IOException {
        writeUnnamedTagWithFallback(tag, output);
    }

    public static void parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        TagType<?> type = TagTypes.getType(input.readByte());
        if (type == EndTag.TYPE) {
            if (visitor.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
                visitor.visitEnd();
            }
        } else {
            switch (visitor.visitRootEntry(type)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    StringTag.skipString(input);
                    type.skip(input, accounter);
                    break;
                case CONTINUE:
                    StringTag.skipString(input);
                    type.parse(input, visitor, accounter);
            }
        }
    }

    public static Tag readAnyTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte _byte = input.readByte();
        return (Tag)(_byte == 0 ? EndTag.INSTANCE : readTagSafe(input, accounter, _byte));
    }

    public static void writeAnyTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            tag.write(output);
        }
    }

    public static void writeUnnamedTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF("");
            tag.write(output);
        }
    }

    public static void writeUnnamedTagWithFallback(Tag tag, DataOutput output) throws IOException {
        writeUnnamedTag(tag, new NbtIo.StringFallbackDataOutput(output));
    }

    @VisibleForTesting
    public static Tag readUnnamedTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte _byte = input.readByte();
        if (_byte == 0) {
            return EndTag.INSTANCE;
        } else {
            StringTag.skipString(input);
            return readTagSafe(input, accounter, _byte);
        }
    }

    private static Tag readTagSafe(DataInput input, NbtAccounter accounter, byte type) {
        try {
            return TagTypes.getType(type).load(input, accounter);
        } catch (IOException var6) {
            CrashReport crashReport = CrashReport.forThrowable(var6, "Loading NBT data");
            CrashReportCategory crashReportCategory = crashReport.addCategory("NBT Tag");
            crashReportCategory.setDetail("Tag type", type);
            throw new ReportedNbtException(crashReport);
        }
    }

    public static class StringFallbackDataOutput extends DelegateDataOutput {
        public StringFallbackDataOutput(DataOutput parent) {
            super(parent);
        }

        @Override
        public void writeUTF(String value) throws IOException {
            try {
                super.writeUTF(value);
            } catch (UTFDataFormatException var3) {
                Util.logAndPauseIfInIde("Failed to write NBT String", var3);
                super.writeUTF("");
            }
        }
    }
}
