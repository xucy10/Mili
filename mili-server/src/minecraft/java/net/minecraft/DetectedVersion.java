package net.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.DataVersion;
import org.slf4j.Logger;

public class DetectedVersion {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final WorldVersion BUILT_IN = createBuiltIn(UUID.randomUUID().toString().replaceAll("-", ""), "Development Version");

    public static WorldVersion createBuiltIn(String id, String name) {
        return createBuiltIn(id, name, true);
    }

    public static WorldVersion createBuiltIn(String id, String name, boolean stable) {
        return new WorldVersion.Simple(
            id, name, new DataVersion(4671, "main"), SharedConstants.getProtocolVersion(), PackFormat.of(75, 0), PackFormat.of(94, 1), new Date(), stable
        );
    }

    private static WorldVersion createFromJson(JsonObject json) {
        JsonObject asJsonObject = GsonHelper.getAsJsonObject(json, "pack_version");
        return new WorldVersion.Simple(
            GsonHelper.getAsString(json, "id"),
            GsonHelper.getAsString(json, "name"),
            new DataVersion(GsonHelper.getAsInt(json, "world_version"), GsonHelper.getAsString(json, "series_id", "main")),
            GsonHelper.getAsInt(json, "protocol_version"),
            PackFormat.of(GsonHelper.getAsInt(asJsonObject, "resource_major"), GsonHelper.getAsInt(asJsonObject, "resource_minor")),
            PackFormat.of(GsonHelper.getAsInt(asJsonObject, "data_major"), GsonHelper.getAsInt(asJsonObject, "data_minor")),
            Date.from(ZonedDateTime.parse(GsonHelper.getAsString(json, "build_time")).toInstant()),
            GsonHelper.getAsBoolean(json, "stable")
        );
    }

    public static WorldVersion tryDetectVersion() {
        try {
            WorldVersion var2;
            try (InputStream resourceAsStream = DetectedVersion.class.getResourceAsStream("/version.json")) {
                if (resourceAsStream == null) {
                    LOGGER.warn("Missing version information!");
                    return BUILT_IN;
                }

                try (InputStreamReader inputStreamReader = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8)) {
                    var2 = createFromJson(GsonHelper.parse(inputStreamReader));
                }
            }

            return var2;
        } catch (JsonParseException | IOException var8) {
            throw new IllegalStateException("Game version information is corrupt", var8);
        }
    }
}
