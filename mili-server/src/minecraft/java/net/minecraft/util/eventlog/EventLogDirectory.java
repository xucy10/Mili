package net.minecraft.util.eventlog;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EventLogDirectory {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMPRESS_BUFFER_SIZE = 4096;
    private static final String COMPRESSED_EXTENSION = ".gz";
    private final Path root;
    private final String extension;

    private EventLogDirectory(Path root, String extension) {
        this.root = root;
        this.extension = extension;
    }

    public static EventLogDirectory open(Path root, String extension) throws IOException {
        Files.createDirectories(root);
        return new EventLogDirectory(root, extension);
    }

    public EventLogDirectory.FileList listFiles() throws IOException {
        EventLogDirectory.FileList var2;
        try (Stream<Path> stream = Files.list(this.root)) {
            var2 = new EventLogDirectory.FileList(stream.filter(path -> Files.isRegularFile(path)).map(this::parseFile).filter(Objects::nonNull).toList());
        }

        return var2;
    }

    private EventLogDirectory.@Nullable File parseFile(Path path) {
        String string = path.getFileName().toString();
        int index = string.indexOf(46);
        if (index == -1) {
            return null;
        } else {
            EventLogDirectory.FileId fileId = EventLogDirectory.FileId.parse(string.substring(0, index));
            if (fileId != null) {
                String sub = string.substring(index);
                if (sub.equals(this.extension)) {
                    return new EventLogDirectory.RawFile(path, fileId);
                }

                if (sub.equals(this.extension + ".gz")) {
                    return new EventLogDirectory.CompressedFile(path, fileId);
                }
            }

            return null;
        }
    }

    static void tryCompress(Path path, Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            throw new IOException("Compressed target file already exists: " + outputPath);
        } else {
            try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                FileLock fileLock = fileChannel.tryLock();
                if (fileLock == null) {
                    throw new IOException("Raw log file is already locked, cannot compress: " + path);
                }

                writeCompressed(fileChannel, outputPath);
                fileChannel.truncate(0L);
            }

            Files.delete(path);
        }
    }

    private static void writeCompressed(ReadableByteChannel channel, Path outputPath) throws IOException {
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(outputPath))) {
            byte[] bytes = new byte[4096];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

            while (channel.read(byteBuffer) >= 0) {
                byteBuffer.flip();
                outputStream.write(bytes, 0, byteBuffer.limit());
                byteBuffer.clear();
            }
        }
    }

    public EventLogDirectory.RawFile createNewFile(LocalDate date) throws IOException {
        int i = 1;
        Set<EventLogDirectory.FileId> set = this.listFiles().ids();

        EventLogDirectory.FileId fileId;
        do {
            fileId = new EventLogDirectory.FileId(date, i++);
        } while (set.contains(fileId));

        EventLogDirectory.RawFile rawFile = new EventLogDirectory.RawFile(this.root.resolve(fileId.toFileName(this.extension)), fileId);
        Files.createFile(rawFile.path());
        return rawFile;
    }

    public record CompressedFile(@Override Path path, @Override EventLogDirectory.FileId id) implements EventLogDirectory.File {
        @Override
        public @Nullable Reader openReader() throws IOException {
            return !Files.exists(this.path)
                ? null
                : new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(this.path)), StandardCharsets.UTF_8));
        }

        @Override
        public EventLogDirectory.CompressedFile compress() {
            return this;
        }
    }

    public interface File {
        Path path();

        EventLogDirectory.FileId id();

        @Nullable Reader openReader() throws IOException;

        EventLogDirectory.CompressedFile compress() throws IOException;
    }

    public record FileId(LocalDate date, int index) {
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

        public static EventLogDirectory.@Nullable FileId parse(String fileName) {
            int index = fileName.indexOf("-");
            if (index == -1) {
                return null;
            } else {
                String sub = fileName.substring(0, index);
                String sub1 = fileName.substring(index + 1);

                try {
                    return new EventLogDirectory.FileId(LocalDate.parse(sub, DATE_FORMATTER), Integer.parseInt(sub1));
                } catch (DateTimeParseException | NumberFormatException var5) {
                    return null;
                }
            }
        }

        @Override
        public String toString() {
            return DATE_FORMATTER.format(this.date) + "-" + this.index;
        }

        public String toFileName(String extension) {
            return this + extension;
        }
    }

    public static class FileList implements Iterable<EventLogDirectory.File> {
        private final List<EventLogDirectory.File> files;

        FileList(List<EventLogDirectory.File> files) {
            this.files = new ArrayList<>(files);
        }

        public EventLogDirectory.FileList prune(LocalDate date, int daysToKeep) {
            this.files.removeIf(file -> {
                EventLogDirectory.FileId fileId = file.id();
                LocalDate localDate = fileId.date().plusDays(daysToKeep);
                if (!date.isBefore(localDate)) {
                    try {
                        Files.delete(file.path());
                        return true;
                    } catch (IOException var6) {
                        EventLogDirectory.LOGGER.warn("Failed to delete expired event log file: {}", file.path(), var6);
                    }
                }

                return false;
            });
            return this;
        }

        public EventLogDirectory.FileList compressAll() {
            ListIterator<EventLogDirectory.File> listIterator = this.files.listIterator();

            while (listIterator.hasNext()) {
                EventLogDirectory.File file = listIterator.next();

                try {
                    listIterator.set(file.compress());
                } catch (IOException var4) {
                    EventLogDirectory.LOGGER.warn("Failed to compress event log file: {}", file.path(), var4);
                }
            }

            return this;
        }

        @Override
        public Iterator<EventLogDirectory.File> iterator() {
            return this.files.iterator();
        }

        public Stream<EventLogDirectory.File> stream() {
            return this.files.stream();
        }

        public Set<EventLogDirectory.FileId> ids() {
            return this.files.stream().map(EventLogDirectory.File::id).collect(Collectors.toSet());
        }
    }

    public record RawFile(@Override Path path, @Override EventLogDirectory.FileId id) implements EventLogDirectory.File {
        public FileChannel openChannel() throws IOException {
            return FileChannel.open(this.path, StandardOpenOption.WRITE, StandardOpenOption.READ);
        }

        @Override
        public @Nullable Reader openReader() throws IOException {
            return Files.exists(this.path) ? Files.newBufferedReader(this.path) : null;
        }

        @Override
        public EventLogDirectory.CompressedFile compress() throws IOException {
            Path path = this.path.resolveSibling(this.path.getFileName().toString() + ".gz");
            EventLogDirectory.tryCompress(this.path, path);
            return new EventLogDirectory.CompressedFile(path, this.id);
        }
    }
}
