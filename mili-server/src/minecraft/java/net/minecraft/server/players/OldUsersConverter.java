package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class OldUsersConverter {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final File OLD_IPBANLIST = new File("banned-ips.txt");
    public static final File OLD_USERBANLIST = new File("banned-players.txt");
    public static final File OLD_OPLIST = new File("ops.txt");
    public static final File OLD_WHITELIST = new File("white-list.txt");

    static List<String> readOldListFormat(File inFile, Map<String, String[]> read) throws IOException {
        List<String> lines = Files.readLines(inFile, StandardCharsets.UTF_8);

        for (String string : lines) {
            string = string.trim();
            if (!string.startsWith("#") && !string.isEmpty()) {
                String[] parts = string.split("\\|");
                read.put(parts[0].toLowerCase(Locale.ROOT), parts);
            }
        }

        return lines;
    }

    private static void lookupPlayers(MinecraftServer server, Collection<String> names, ProfileLookupCallback callback) {
        String[] strings = names.stream().filter(name -> !StringUtil.isNullOrEmpty(name)).toArray(String[]::new);
        if (server.usesAuthentication() ||
            (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode())) { // Spigot: bungee = online mode, for now.  // Paper - Add setting for proxy online mode status
            server.services().profileRepository().findProfilesByNames(strings, callback);
        } else {
            for (String string : strings) {
                callback.onProfileLookupSucceeded(string, UUIDUtil.createOfflinePlayerUUID(string));
            }
        }
    }

    public static boolean convertUserBanlist(final MinecraftServer server) {
        final UserBanList userBanList = new UserBanList(PlayerList.USERBANLIST_FILE, new EmptyNotificationService());
        if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
            if (userBanList.getFile().exists()) {
                try {
                    userBanList.load();
                } catch (IOException var6) {
                    LOGGER.warn("Could not load existing file {}", userBanList.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                final Map<String, String[]> map = Maps.newHashMap();
                readOldListFormat(OLD_USERBANLIST, map);
                ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameAndId = new NameAndId(profileId, profileName);
                        server.services().nameToIdCache().add(nameAndId);
                        String[] strings = map.get(nameAndId.name().toLowerCase(Locale.ROOT));
                        if (strings == null) {
                            OldUsersConverter.LOGGER.warn("Could not convert user banlist entry for {}", nameAndId.name());
                            throw new OldUsersConverter.ConversionError("Profile not in the conversionlist");
                        } else {
                            Date date = strings.length > 1 ? OldUsersConverter.parseDate(strings[1], null) : null;
                            String string = strings.length > 2 ? strings[2] : null;
                            Date date1 = strings.length > 3 ? OldUsersConverter.parseDate(strings[3], null) : null;
                            String string1 = strings.length > 4 ? strings[4] : null;
                            userBanList.add(new UserBanListEntry(nameAndId, date, string, date1, string1));
                        }
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user banlist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, map.keySet(), profileLookupCallback);
                userBanList.save();
                renameOldFile(OLD_USERBANLIST);
                return true;
            } catch (IOException var4) {
                LOGGER.warn("Could not read old user banlist to convert it!", (Throwable)var4);
                return false;
            } catch (OldUsersConverter.ConversionError var5) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)var5);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertIpBanlist(MinecraftServer server) {
        IpBanList ipBanList = new IpBanList(PlayerList.IPBANLIST_FILE, new EmptyNotificationService());
        if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
            if (ipBanList.getFile().exists()) {
                try {
                    ipBanList.load();
                } catch (IOException var11) {
                    LOGGER.warn("Could not load existing file {}", ipBanList.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                Map<String, String[]> map = Maps.newHashMap();
                readOldListFormat(OLD_IPBANLIST, map);

                for (String string : map.keySet()) {
                    String[] strings = map.get(string);
                    Date date = strings.length > 1 ? parseDate(strings[1], null) : null;
                    String string1 = strings.length > 2 ? strings[2] : null;
                    Date date1 = strings.length > 3 ? parseDate(strings[3], null) : null;
                    String string2 = strings.length > 4 ? strings[4] : null;
                    ipBanList.add(new IpBanListEntry(string, date, string1, date1, string2));
                }

                ipBanList.save();
                renameOldFile(OLD_IPBANLIST);
                return true;
            } catch (IOException var10) {
                LOGGER.warn("Could not parse old ip banlist to convert it!", (Throwable)var10);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertOpsList(final MinecraftServer server) {
        final ServerOpList serverOpList = new ServerOpList(PlayerList.OPLIST_FILE, new EmptyNotificationService());
        if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
            if (serverOpList.getFile().exists()) {
                try {
                    serverOpList.load();
                } catch (IOException var6) {
                    LOGGER.warn("Could not load existing file {}", serverOpList.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                List<String> lines = Files.readLines(OLD_OPLIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameAndId = new NameAndId(profileId, profileName);
                        server.services().nameToIdCache().add(nameAndId);
                        serverOpList.add(new ServerOpListEntry(nameAndId, server.operatorUserPermissions(), false));
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup oplist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, lines, profileLookupCallback);
                serverOpList.save();
                renameOldFile(OLD_OPLIST);
                return true;
            } catch (IOException var4) {
                LOGGER.warn("Could not read old oplist to convert it!", (Throwable)var4);
                return false;
            } catch (OldUsersConverter.ConversionError var5) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)var5);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertWhiteList(final MinecraftServer server) {
        final UserWhiteList userWhiteList = new UserWhiteList(PlayerList.WHITELIST_FILE, new EmptyNotificationService());
        if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
            if (userWhiteList.getFile().exists()) {
                try {
                    userWhiteList.load();
                } catch (IOException var6) {
                    LOGGER.warn("Could not load existing file {}", userWhiteList.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                List<String> lines = Files.readLines(OLD_WHITELIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameAndId = new NameAndId(profileId, profileName);
                        server.services().nameToIdCache().add(nameAndId);
                        userWhiteList.add(new UserWhiteListEntry(nameAndId));
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };
                lookupPlayers(server, lines, profileLookupCallback);
                userWhiteList.save();
                renameOldFile(OLD_WHITELIST);
                return true;
            } catch (IOException var4) {
                LOGGER.warn("Could not read old whitelist to convert it!", (Throwable)var4);
                return false;
            } catch (OldUsersConverter.ConversionError var5) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)var5);
                return false;
            }
        } else {
            return true;
        }
    }

    public static @Nullable UUID convertMobOwnerIfNecessary(final MinecraftServer server, String username) {
        if (!StringUtil.isNullOrEmpty(username) && username.length() <= 16) {
            Optional<UUID> optional = server.services().nameToIdCache().get(username).map(NameAndId::id);
            if (optional.isPresent()) {
                return optional.get();
            } else if (!server.isSingleplayer() && server.usesAuthentication()) {
                final List<NameAndId> list = new ArrayList<>();
                ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameAndId = new NameAndId(profileId, profileName);
                        server.services().nameToIdCache().add(nameAndId);
                        list.add(nameAndId);
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                    }
                };
                lookupPlayers(server, Lists.newArrayList(username), profileLookupCallback);
                return !list.isEmpty() ? list.getFirst().id() : null;
            } else {
                return UUIDUtil.createOfflinePlayerUUID(username);
            }
        } else {
            try {
                return UUID.fromString(username);
            } catch (IllegalArgumentException var5) {
                return null;
            }
        }
    }

    public static boolean convertPlayers(final DedicatedServer server) {
        final File worldPlayersDirectory = getWorldPlayersDirectory(server);
        final File file = new File(worldPlayersDirectory.getParentFile(), "playerdata");
        final File file1 = new File(worldPlayersDirectory.getParentFile(), "unknownplayers");
        if (worldPlayersDirectory.exists() && worldPlayersDirectory.isDirectory()) {
            File[] files = worldPlayersDirectory.listFiles();
            List<String> list = Lists.newArrayList();

            for (File file2 : files) {
                String name = file2.getName();
                if (name.toLowerCase(Locale.ROOT).endsWith(".dat")) {
                    String sub = name.substring(0, name.length() - ".dat".length());
                    if (!sub.isEmpty()) {
                        list.add(sub);
                    }
                }
            }

            try {
                final String[] strings = list.toArray(new String[list.size()]);
                ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameAndId = new NameAndId(profileId, profileName);
                        server.services().nameToIdCache().add(nameAndId);
                        this.movePlayerFile(file, this.getFileNameForProfile(profileName), profileId.toString());
                    }

                    @Override
                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user uuid for {}", profileName, exception);
                        if (exception instanceof ProfileNotFoundException) {
                            String fileNameForProfile = this.getFileNameForProfile(profileName);
                            this.movePlayerFile(file1, fileNameForProfile, fileNameForProfile);
                        } else {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }

                    private void movePlayerFile(File file3, String oldFileName, String newFileName) {
                        File file4 = new File(worldPlayersDirectory, oldFileName + ".dat");
                        File file5 = new File(file3, newFileName + ".dat");
                        // CraftBukkit start - Use old file name to seed lastKnownName
                        net.minecraft.nbt.CompoundTag root = null;

                        try {
                            root = net.minecraft.nbt.NbtIo.readCompressed(new java.io.FileInputStream(file4), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                        } catch (Exception exception) {
                            // Paper start
                            exception.printStackTrace();
                            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception);
                            // Paper end
                        }

                        if (root != null) {
                            if (!root.contains("bukkit")) {
                                root.put("bukkit", new net.minecraft.nbt.CompoundTag());
                            }
                            net.minecraft.nbt.CompoundTag data = root.getCompoundOrEmpty("bukkit");
                            data.putString("lastKnownName", oldFileName);

                            try {
                                net.minecraft.nbt.NbtIo.writeCompressed(root, new java.io.FileOutputStream(file1));
                            } catch (Exception exception) {
                                // Paper start
                                exception.printStackTrace();
                                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception);
                                // Paper end
                            }
                       }
                        // CraftBukkit end
                        OldUsersConverter.ensureDirectoryExists(file3);
                        if (!file4.renameTo(file5)) {
                            throw new OldUsersConverter.ConversionError("Could not convert file for " + oldFileName);
                        }
                    }

                    private String getFileNameForProfile(String profileName) {
                        String string = null;

                        for (String string1 : strings) {
                            if (string1 != null && string1.equalsIgnoreCase(profileName)) {
                                string = string1;
                                break;
                            }
                        }

                        if (string == null) {
                            throw new OldUsersConverter.ConversionError("Could not find the filename for " + profileName + " anymore");
                        } else {
                            return string;
                        }
                    }
                };
                lookupPlayers(server, Lists.newArrayList(strings), profileLookupCallback);
                return true;
            } catch (OldUsersConverter.ConversionError var12) {
                LOGGER.error("Conversion failed, please try again later", (Throwable)var12);
                return false;
            }
        } else {
            return true;
        }
    }

    static void ensureDirectoryExists(File dir) {
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new OldUsersConverter.ConversionError("Can't create directory " + dir.getName() + " in world save directory.");
            }
        } else if (!dir.mkdirs()) {
            throw new OldUsersConverter.ConversionError("Can't create directory " + dir.getName() + " in world save directory.");
        }
    }

    public static boolean serverReadyAfterUserconversion(MinecraftServer server) {
        boolean flag = areOldUserlistsRemoved();
        return flag && areOldPlayersConverted(server);
    }

    private static boolean areOldUserlistsRemoved() {
        boolean flag = false;
        if (OLD_USERBANLIST.exists() && OLD_USERBANLIST.isFile()) {
            flag = true;
        }

        boolean flag1 = false;
        if (OLD_IPBANLIST.exists() && OLD_IPBANLIST.isFile()) {
            flag1 = true;
        }

        boolean flag2 = false;
        if (OLD_OPLIST.exists() && OLD_OPLIST.isFile()) {
            flag2 = true;
        }

        boolean flag3 = false;
        if (OLD_WHITELIST.exists() && OLD_WHITELIST.isFile()) {
            flag3 = true;
        }

        if (!flag && !flag1 && !flag2 && !flag3) {
            return true;
        } else {
            LOGGER.warn("**** FAILED TO START THE SERVER AFTER ACCOUNT CONVERSION!");
            LOGGER.warn("** please remove the following files and restart the server:");
            if (flag) {
                LOGGER.warn("* {}", OLD_USERBANLIST.getName());
            }

            if (flag1) {
                LOGGER.warn("* {}", OLD_IPBANLIST.getName());
            }

            if (flag2) {
                LOGGER.warn("* {}", OLD_OPLIST.getName());
            }

            if (flag3) {
                LOGGER.warn("* {}", OLD_WHITELIST.getName());
            }

            return false;
        }
    }

    private static boolean areOldPlayersConverted(MinecraftServer server) {
        File worldPlayersDirectory = getWorldPlayersDirectory(server);
        if (!worldPlayersDirectory.exists()
            || !worldPlayersDirectory.isDirectory()
            || worldPlayersDirectory.list().length <= 0 && worldPlayersDirectory.delete()) {
            return true;
        } else {
            LOGGER.warn("**** DETECTED OLD PLAYER DIRECTORY IN THE WORLD SAVE");
            LOGGER.warn("**** THIS USUALLY HAPPENS WHEN THE AUTOMATIC CONVERSION FAILED IN SOME WAY");
            LOGGER.warn("** please restart the server and if the problem persists, remove the directory '{}'", worldPlayersDirectory.getPath());
            return false;
        }
    }

    private static File getWorldPlayersDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_OLD_DATA_DIR).toFile();
    }

    private static void renameOldFile(File convertedFile) {
        File file = new File(convertedFile.getName() + ".converted");
        convertedFile.renameTo(file);
    }

    static Date parseDate(String input, Date defaultValue) {
        Date date;
        try {
            date = BanListEntry.DATE_FORMAT.get().parse(input); // Folia - region threading - SDF is not thread-safe
        } catch (ParseException var4) {
            date = defaultValue;
        }

        return date;
    }

    static class ConversionError extends RuntimeException {
        ConversionError(String message, Throwable cause) {
            super(message, cause);
        }

        ConversionError(String message) {
            super(message);
        }
    }
}
