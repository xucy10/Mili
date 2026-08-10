package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FilePackResources extends AbstractPackResources {
    static final Logger LOGGER = LogUtils.getLogger();
    private final FilePackResources.SharedZipFileAccess zipFileAccess;
    private final String prefix;

    FilePackResources(PackLocationInfo location, FilePackResources.SharedZipFileAccess zipFileAccess, String prefix) {
        super(location);
        this.zipFileAccess = zipFileAccess;
        this.prefix = prefix;
    }

    private static String getPathFromLocation(PackType packType, Identifier location) {
        return String.format(Locale.ROOT, "%s/%s/%s", packType.getDirectory(), location.getNamespace(), location.getPath());
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return this.getResource(String.join("/", elements));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, Identifier location) {
        return this.getResource(getPathFromLocation(packType, location));
    }

    private String addPrefix(String resourcePath) {
        return this.prefix.isEmpty() ? resourcePath : this.prefix + "/" + resourcePath;
    }

    private @Nullable IoSupplier<InputStream> getResource(String resourcePath) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return null;
        } else {
            ZipEntry entry = zipFile.getEntry(this.addPrefix(resourcePath));
            return entry == null ? null : IoSupplier.create(zipFile, entry);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return Set.of();
        } else {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            Set<String> set = Sets.newHashSet();
            String string = this.addPrefix(type.getDirectory() + "/");

            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String name = zipEntry.getName();
                String string1 = extractNamespace(string, name);
                if (!string1.isEmpty()) {
                    if (Identifier.isValidNamespace(string1)) {
                        set.add(string1);
                    } else {
                        LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", string1, this.zipFileAccess.file);
                    }
                }
            }

            return set;
        }
    }

    @VisibleForTesting
    public static String extractNamespace(String directory, String name) {
        if (!name.startsWith(directory)) {
            return "";
        } else {
            int len = directory.length();
            int index = name.indexOf(47, len);
            return index == -1 ? name.substring(len) : name.substring(len, index);
        }
    }

    @Override
    public void close() {
        this.zipFileAccess.close();
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile != null) {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            String string = this.addPrefix(packType.getDirectory() + "/" + namespace + "/");
            String string1 = string + path + "/";

            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                if (!zipEntry.isDirectory()) {
                    String name = zipEntry.getName();
                    if (name.startsWith(string1)) {
                        String sub = name.substring(string.length());
                        Identifier identifier = Identifier.tryBuild(namespace, sub);
                        if (identifier != null) {
                            resourceOutput.accept(identifier, IoSupplier.create(zipFile, zipEntry));
                        } else {
                            LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, sub);
                        }
                    }
                }
            }
        }
    }

    public static class FileResourcesSupplier implements Pack.ResourcesSupplier {
        private final File content;

        public FileResourcesSupplier(Path content) {
            this(content.toFile());
        }

        public FileResourcesSupplier(File content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            FilePackResources.SharedZipFileAccess sharedZipFileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            return new FilePackResources(location, sharedZipFileAccess, "");
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            FilePackResources.SharedZipFileAccess sharedZipFileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            PackResources packResources = new FilePackResources(location, sharedZipFileAccess, "");
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packResources;
            } else {
                List<PackResources> list1 = new ArrayList<>(list.size());

                for (String string : list) {
                    list1.add(new FilePackResources(location, sharedZipFileAccess, string));
                }

                return new CompositePackResources(packResources, list1);
            }
        }
    }

    static class SharedZipFileAccess implements AutoCloseable {
        final File file;
        private @Nullable ZipFile zipFile;
        private boolean failedToLoad;

        SharedZipFileAccess(File file) {
            this.file = file;
        }

        @Nullable ZipFile getOrCreateZipFile() {
            if (this.failedToLoad) {
                return null;
            } else {
                if (this.zipFile == null) {
                    try {
                        this.zipFile = new ZipFile(this.file);
                    } catch (IOException var2) {
                        FilePackResources.LOGGER.error("Failed to open pack {}", this.file, var2);
                        this.failedToLoad = true;
                        return null;
                    }
                }

                return this.zipFile;
            }
        }

        @Override
        public void close() {
            if (this.zipFile != null) {
                IOUtils.closeQuietly(this.zipFile);
                this.zipFile = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }
}
