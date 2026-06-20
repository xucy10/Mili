package net.minecraft.data;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.WorldVersion;
import net.minecraft.server.Bootstrap;
import org.slf4j.Logger;

public class DataGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path rootOutputFolder;
    private final PackOutput vanillaPackOutput;
    final Set<String> allProviderIds = new HashSet<>();
    final Map<String, DataProvider> providersToRun = new LinkedHashMap<>();
    private final WorldVersion version;
    private final boolean alwaysGenerate;

    public DataGenerator(Path rootOutputFolder, WorldVersion version, boolean alwaysGenerate) {
        this.rootOutputFolder = rootOutputFolder;
        this.vanillaPackOutput = new PackOutput(this.rootOutputFolder);
        this.version = version;
        this.alwaysGenerate = alwaysGenerate;
    }

    public void run() throws IOException {
        HashCache hashCache = new HashCache(this.rootOutputFolder, this.allProviderIds, this.version);
        Stopwatch stopwatch = Stopwatch.createStarted();
        Stopwatch stopwatch1 = Stopwatch.createUnstarted();
        this.providersToRun.forEach((providerName, dataProvider) -> {
            if (!this.alwaysGenerate && !hashCache.shouldRunInThisVersion(providerName)) {
                LOGGER.debug("Generator {} already run for version {}", providerName, this.version.name());
            } else {
                LOGGER.info("Starting provider: {}", providerName);
                stopwatch1.start();
                hashCache.applyUpdate(hashCache.generateUpdate(providerName, dataProvider::run).join());
                stopwatch1.stop();
                LOGGER.info("{} finished after {} ms", providerName, stopwatch1.elapsed(TimeUnit.MILLISECONDS));
                stopwatch1.reset();
            }
        });
        LOGGER.info("All providers took: {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        hashCache.purgeStaleAndWrite();
    }

    public DataGenerator.PackGenerator getVanillaPack(boolean toRun) {
        return new DataGenerator.PackGenerator(toRun, "vanilla", this.vanillaPackOutput);
    }

    public DataGenerator.PackGenerator getBuiltinDatapack(boolean toRun, String providerPrefix) {
        Path path = this.vanillaPackOutput.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("minecraft").resolve("datapacks").resolve(providerPrefix);
        return new DataGenerator.PackGenerator(toRun, providerPrefix, new PackOutput(path));
    }

    static {
        Bootstrap.bootStrap();
    }

    public class PackGenerator {
        private final boolean toRun;
        private final String providerPrefix;
        private final PackOutput output;

        PackGenerator(final boolean toRun, final String providerPrefix, final PackOutput output) {
            this.toRun = toRun;
            this.providerPrefix = providerPrefix;
            this.output = output;
        }

        public <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory) {
            T dataProvider = factory.create(this.output);
            String string = this.providerPrefix + "/" + dataProvider.getName();
            if (!DataGenerator.this.allProviderIds.add(string)) {
                throw new IllegalStateException("Duplicate provider: " + string);
            } else {
                if (this.toRun) {
                    DataGenerator.this.providersToRun.put(string, dataProvider);
                }

                return dataProvider;
            }
        }
    }
}
