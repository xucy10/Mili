package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class PlayerDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;
        this.playerDir = levelStorageAccess.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, player.registryAccess());
            player.saveWithoutId(tagValueOutput);
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");
            CompoundTag compoundTag = tagValueOutput.buildResult();
            NbtIo.writeCompressed(compoundTag, path1);
            Path path2 = path.resolve(player.getStringUUID() + ".dat");
            Path path3 = path.resolve(player.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(path2, path1, path3);
        } catch (Exception var11) {
            LOGGER.warn("Failed to save player data for {}", player.getPlainTextName(), var11); // Paper - Print exception
        }
    }

    private void backup(NameAndId nameAndId, String suffix) {
        Path path = this.playerDir.toPath();
        String string = nameAndId.id().toString();
        Path path1 = path.resolve(string + suffix);
        Path path2 = path.resolve(string + "_corrupted_" + ZonedDateTime.now().format(FileNameDateFormatter.FORMATTER) + suffix);
        if (Files.isRegularFile(path1)) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception var8) {
                LOGGER.warn("Failed to copy the player.dat file for {}", nameAndId.name(), var8);
            }
        }
    }

    private Optional<CompoundTag> load(NameAndId nameAndId, String suffix) {
        File file = new File(this.playerDir, nameAndId.id() + suffix);
        // Spigot start
        boolean usingWrongFile = false;
        if (org.bukkit.Bukkit.getOnlineMode() && !file.exists()) { // Paper - Check online mode first
            file = new File(this.playerDir, net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(nameAndId.name()) + suffix);
            if (file.exists()) {
                usingWrongFile = true;
                LOGGER.warn("Using offline mode UUID file for player {} as it is the only copy we can find.", nameAndId.name());
            }
        }
        // Spigot end
        if (file.exists() && file.isFile()) {
            try {
                // Spigot start
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
                if (usingWrongFile) {
                    file.renameTo(new File(file.getPath() + ".offline-read"));
                }
                return optional;
                // Spigot end
            } catch (Exception var5) {
                LOGGER.warn("Failed to load player data for {}", nameAndId.name());
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(NameAndId nameAndId) {
        Optional<CompoundTag> optional = this.load(nameAndId, ".dat");
        if (optional.isEmpty()) {
            this.backup(nameAndId, ".dat");
        }

        return optional.or(() -> this.load(nameAndId, ".dat_old")).map(compoundTag -> {
            int dataVersion = NbtUtils.getDataVersion(compoundTag);
            return ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, compoundTag, dataVersion, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion());
        });
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
