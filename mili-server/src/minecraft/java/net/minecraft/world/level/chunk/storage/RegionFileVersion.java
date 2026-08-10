package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.minecraft.util.FastBufferedInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RegionFileVersion {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Int2ObjectMap<RegionFileVersion> VERSIONS = new Int2ObjectOpenHashMap<>(); // Paper - private -> public
    private static final Object2ObjectMap<String, RegionFileVersion> VERSIONS_BY_NAME = new Object2ObjectOpenHashMap<>();
    public static final RegionFileVersion VERSION_GZIP = register(
        new RegionFileVersion(
            1,
            null,
            inputWrapper -> new FastBufferedInputStream(new GZIPInputStream(inputWrapper)),
            outputWrapper -> new BufferedOutputStream(new GZIPOutputStream(outputWrapper))
        )
    );
    public static final RegionFileVersion VERSION_DEFLATE = register(
        new RegionFileVersion(
            2,
            "deflate",
            inputWrapper -> new FastBufferedInputStream(new InflaterInputStream(inputWrapper)),
            outputWrapper -> new BufferedOutputStream(new DeflaterOutputStream(outputWrapper))
        )
    );
    public static final RegionFileVersion VERSION_NONE = register(new RegionFileVersion(3, "none", FastBufferedInputStream::new, BufferedOutputStream::new));
    public static final RegionFileVersion VERSION_LZ4 = register(
        new RegionFileVersion(
            4,
            "lz4",
            stream -> new FastBufferedInputStream(new LZ4BlockInputStream(stream)),
            stream -> new BufferedOutputStream(new LZ4BlockOutputStream(stream))
        )
    );
    public static final RegionFileVersion VERSION_CUSTOM = register(new RegionFileVersion(127, null, inputWrapper -> {
        throw new UnsupportedOperationException();
    }, outputWrapper -> {
        throw new UnsupportedOperationException();
    }));
    public static final RegionFileVersion DEFAULT = VERSION_DEFLATE;
    private static volatile RegionFileVersion selected = DEFAULT;
    private final int id;
    private final @Nullable String optionName;
    private final RegionFileVersion.StreamWrapper<InputStream> inputWrapper;
    private final RegionFileVersion.StreamWrapper<OutputStream> outputWrapper;

    // Paper start - Configurable region compression format
    public static RegionFileVersion getCompressionFormat() {
        return switch (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.compressionFormat) {
            case GZIP -> VERSION_GZIP;
            case ZLIB -> VERSION_DEFLATE;
            case LZ4 -> VERSION_LZ4;
            case NONE -> VERSION_NONE;
        };
    }
    // Paper end - Configurable region compression format
    private RegionFileVersion(
        int id,
        @Nullable String optionName,
        RegionFileVersion.StreamWrapper<InputStream> inputWrapper,
        RegionFileVersion.StreamWrapper<OutputStream> outputWrapper
    ) {
        this.id = id;
        this.optionName = optionName;
        this.inputWrapper = inputWrapper;
        this.outputWrapper = outputWrapper;
    }

    private static RegionFileVersion register(RegionFileVersion fileVersion) {
        VERSIONS.put(fileVersion.id, fileVersion);
        if (fileVersion.optionName != null) {
            VERSIONS_BY_NAME.put(fileVersion.optionName, fileVersion);
        }

        return fileVersion;
    }

    public static @Nullable RegionFileVersion fromId(int id) {
        return VERSIONS.get(id);
    }

    public static void configure(String optionValue) {
        RegionFileVersion regionFileVersion = VERSIONS_BY_NAME.get(optionValue);
        if (regionFileVersion != null) {
            selected = regionFileVersion;
        } else {
            LOGGER.error(
                "Invalid `region-file-compression` value `{}` in server.properties. Please use one of: {}",
                optionValue,
                String.join(", ", VERSIONS_BY_NAME.keySet())
            );
        }
    }

    public static RegionFileVersion getSelected() {
        return selected;
    }

    public static boolean isValidVersion(int id) {
        return VERSIONS.containsKey(id);
    }

    public int getId() {
        return this.id;
    }

    public OutputStream wrap(OutputStream outputStream) throws IOException {
        return this.outputWrapper.wrap(outputStream);
    }

    public InputStream wrap(InputStream inputStream) throws IOException {
        return this.inputWrapper.wrap(inputStream);
    }

    @FunctionalInterface
    interface StreamWrapper<O> {
        O wrap(O stream) throws IOException;
    }
}
