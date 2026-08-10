package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.IdentifierException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.FileUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure";
    private static final String STRUCTURE_GENERATED_DIRECTORY_NAME = "structures";
    private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
    private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
    public final Map<Identifier, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
    private final DataFixer fixerUpper;
    private ResourceManager resourceManager;
    private final Path generatedDir;
    private final List<StructureTemplateManager.Source> sources;
    private final HolderGetter<Block> blockLookup;
    private static final FileToIdConverter RESOURCE_LISTER = new FileToIdConverter("structure", ".nbt");

    public StructureTemplateManager(
        ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer fixerUpper, HolderGetter<Block> blockLookup
    ) {
        this.resourceManager = resourceManager;
        this.fixerUpper = fixerUpper;
        this.generatedDir = levelStorageAccess.getLevelPath(LevelResource.GENERATED_DIR).normalize();
        this.blockLookup = blockLookup;
        Builder<StructureTemplateManager.Source> builder = ImmutableList.builder();
        builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
        }

        builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
        this.sources = builder.build();
    }

    public StructureTemplate getOrCreate(Identifier id) {
        Optional<StructureTemplate> optional = this.get(id);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            StructureTemplate structureTemplate = new StructureTemplate();
            this.structureRepository.put(id, Optional.of(structureTemplate));
            return structureTemplate;
        }
    }

    public Optional<StructureTemplate> get(Identifier id) {
        return this.structureRepository.computeIfAbsent(id, this::tryLoad);
    }

    public Stream<Identifier> listTemplates() {
        return this.sources.stream().flatMap(source -> source.lister().get()).distinct();
    }

    private Optional<StructureTemplate> tryLoad(Identifier id) {
        for (StructureTemplateManager.Source source : this.sources) {
            try {
                Optional<StructureTemplate> optional = source.loader().apply(id);
                if (optional.isPresent()) {
                    return optional;
                }
            } catch (Exception var5) {
            }
        }

        return Optional.empty();
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.structureRepository.clear();
    }

    public Optional<StructureTemplate> loadFromResource(Identifier id) {
        Identifier identifier = RESOURCE_LISTER.idToFile(id);
        return this.load(() -> this.resourceManager.open(identifier), throwable -> LOGGER.error("Couldn't load structure {}", id, throwable));
    }

    private Stream<Identifier> listResources() {
        return RESOURCE_LISTER.listMatchingResources(this.resourceManager).keySet().stream().map(RESOURCE_LISTER::fileToId);
    }

    private Optional<StructureTemplate> loadFromTestStructures(Identifier id) {
        return this.loadFromSnbt(id, StructureUtils.testStructuresDir);
    }

    private Stream<Identifier> listTestStructures() {
        if (!Files.isDirectory(StructureUtils.testStructuresDir)) {
            return Stream.empty();
        } else {
            List<Identifier> list = new ArrayList<>();
            this.listFolderContents(StructureUtils.testStructuresDir, "minecraft", ".snbt", list::add);
            return list.stream();
        }
    }

    public Optional<StructureTemplate> loadFromGenerated(Identifier id) {
        if (!Files.isDirectory(this.generatedDir)) {
            return Optional.empty();
        } else {
            Path path = this.createAndValidatePathToGeneratedStructure(id, ".nbt");
            return this.load(() -> new FileInputStream(path.toFile()), throwable -> LOGGER.error("Couldn't load structure from {}", path, throwable));
        }
    }

    private Stream<Identifier> listGenerated() {
        if (!Files.isDirectory(this.generatedDir)) {
            return Stream.empty();
        } else {
            try {
                List<Identifier> list = new ArrayList<>();

                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.generatedDir, path2 -> Files.isDirectory(path2))) {
                    for (Path path : directoryStream) {
                        String string = path.getFileName().toString();
                        Path path1 = path.resolve("structures");
                        this.listFolderContents(path1, string, ".nbt", list::add);
                    }
                }

                return list.stream();
            } catch (IOException var9) {
                return Stream.empty();
            }
        }
    }

    private void listFolderContents(Path folder, String namespace, String extension, Consumer<Identifier> output) {
        int len = extension.length();
        Function<String, String> function = string -> string.substring(0, string.length() - len);

        try (Stream<Path> stream = Files.find(
                folder, Integer.MAX_VALUE, (path, basicFileAttributes) -> basicFileAttributes.isRegularFile() && path.toString().endsWith(extension)
            )) {
            stream.forEach(path -> {
                try {
                    output.accept(Identifier.fromNamespaceAndPath(namespace, function.apply(this.relativize(folder, path))));
                } catch (IdentifierException var7x) {
                    LOGGER.error("Invalid location while listing folder {} contents", folder, var7x);
                }
            });
        } catch (IOException var12) {
            LOGGER.error("Failed to list folder {} contents", folder, var12);
        }
    }

    private String relativize(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separator, "/");
    }

    private Optional<StructureTemplate> loadFromSnbt(Identifier id, Path path) {
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        } else {
            Path path1 = FileUtil.createPathToResource(path, id.getPath(), ".snbt");

            try {
                Optional var6;
                try (BufferedReader bufferedReader = Files.newBufferedReader(path1)) {
                    String string = IOUtils.toString(bufferedReader);
                    var6 = Optional.of(this.readStructure(NbtUtils.snbtToStructure(string)));
                }

                return var6;
            } catch (NoSuchFileException var9) {
                return Optional.empty();
            } catch (CommandSyntaxException | IOException var10) {
                LOGGER.error("Couldn't load structure from {}", path1, var10);
                return Optional.empty();
            }
        }
    }

    private Optional<StructureTemplate> load(StructureTemplateManager.InputStreamOpener inputStream, Consumer<Throwable> onError) {
        try {
            Optional var5;
            try (
                InputStream inputStream1 = inputStream.open();
                InputStream inputStream2 = new FastBufferedInputStream(inputStream1);
            ) {
                var5 = Optional.of(this.readStructure(inputStream2));
            }

            return var5;
        } catch (FileNotFoundException var11) {
            return Optional.empty();
        } catch (Throwable var12) {
            onError.accept(var12);
            return Optional.empty();
        }
    }

    public StructureTemplate readStructure(InputStream stream) throws IOException {
        CompoundTag compressed = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        return this.readStructure(compressed);
    }

    public StructureTemplate readStructure(CompoundTag tag) {
        StructureTemplate structureTemplate = new StructureTemplate();
        int dataVersion = NbtUtils.getDataVersion(tag, 500);
        structureTemplate.load(this.blockLookup, ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.STRUCTURE, tag, dataVersion, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion())); // Paper - rewrite data conversion system
        return structureTemplate;
    }

    public boolean save(Identifier id) {
        Optional<StructureTemplate> optional = this.structureRepository.get(id);
        if (optional.isEmpty()) {
            return false;
        } else {
            StructureTemplate structureTemplate = optional.get();
            Path path = this.createAndValidatePathToGeneratedStructure(id, SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT ? ".snbt" : ".nbt");
            Path parent = path.getParent();
            if (parent == null) {
                return false;
            } else {
                try {
                    Files.createDirectories(Files.exists(parent) ? parent.toRealPath() : parent);
                } catch (IOException var14) {
                    LOGGER.error("Failed to create parent directory: {}", parent);
                    return false;
                }

                CompoundTag compoundTag = structureTemplate.save(new CompoundTag());
                if (SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT) {
                    try {
                        NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, path, NbtUtils.structureToSnbt(compoundTag));
                    } catch (Throwable var13) {
                        return false;
                    }
                } else {
                    try (OutputStream outputStream = new FileOutputStream(path.toFile())) {
                        NbtIo.writeCompressed(compoundTag, outputStream);
                    } catch (Throwable var12) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    public Path createAndValidatePathToGeneratedStructure(Identifier location, String extension) {
        if (location.getPath().contains("//")) {
            throw new IdentifierException("Invalid resource path: " + location);
        } else {
            try {
                Path path = this.generatedDir.resolve(location.getNamespace());
                Path path1 = path.resolve("structures");
                Path path2 = FileUtil.createPathToResource(path1, location.getPath(), extension);
                if (path2.startsWith(this.generatedDir) && FileUtil.isPathNormalized(path2) && FileUtil.isPathPortable(path2)) {
                    return path2;
                } else {
                    throw new IdentifierException("Invalid resource path: " + path2);
                }
            } catch (InvalidPathException var6) {
                throw new IdentifierException("Invalid resource path: " + location, var6);
            }
        }
    }

    public void remove(Identifier id) {
        this.structureRepository.remove(id);
    }

    @FunctionalInterface
    interface InputStreamOpener {
        InputStream open() throws IOException;
    }

    record Source(Function<Identifier, Optional<StructureTemplate>> loader, Supplier<Stream<Identifier>> lister) {
    }
}
