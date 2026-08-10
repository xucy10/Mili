package net.minecraft;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;
import oshi.hardware.VirtualMemory;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;

public class SystemReport {
    public static final long BYTES_PER_MEBIBYTE = 1048576L;
    private static final long ONE_GIGA = 1000000000L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OPERATING_SYSTEM = System.getProperty("os.name")
        + " ("
        + System.getProperty("os.arch")
        + ") version "
        + System.getProperty("os.version");
    private static final String JAVA_VERSION = System.getProperty("java.version") + ", " + System.getProperty("java.vendor");
    private static final String JAVA_VM_VERSION = System.getProperty("java.vm.name")
        + " ("
        + System.getProperty("java.vm.info")
        + "), "
        + System.getProperty("java.vm.vendor");
    private final Map<String, String> entries = Maps.newLinkedHashMap();

    public SystemReport() {
        this.setDetail("Minecraft Version", SharedConstants.getCurrentVersion().name());
        this.setDetail("Minecraft Version ID", SharedConstants.getCurrentVersion().id());
        this.setDetail("Operating System", OPERATING_SYSTEM);
        this.setDetail("Java Version", JAVA_VERSION);
        this.setDetail("Java VM Version", JAVA_VM_VERSION);
        this.setDetail("Memory", () -> {
            Runtime runtime = Runtime.getRuntime();
            long l = runtime.maxMemory();
            long l1 = runtime.totalMemory();
            long l2 = runtime.freeMemory();
            long l3 = l / 1048576L;
            long l4 = l1 / 1048576L;
            long l5 = l2 / 1048576L;
            return l2 + " bytes (" + l5 + " MiB) / " + l1 + " bytes (" + l4 + " MiB) up to " + l + " bytes (" + l3 + " MiB)";
        });
        this.setDetail("CPUs", () -> String.valueOf(Runtime.getRuntime().availableProcessors()));
        this.ignoreErrors("hardware", () -> this.putHardware(new SystemInfo()));
        this.setDetail("JVM Flags", () -> printJvmFlags(string -> string.startsWith("-X")));
        this.setDetail("Debug Flags", () -> printJvmFlags(string -> string.startsWith("-DMC_DEBUG_")));
    }

    private static String printJvmFlags(Predicate<String> typePredicate) {
        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> list = inputArguments.stream().filter(typePredicate).toList();
        return String.format(Locale.ROOT, "%d total; %s", list.size(), String.join(" ", list));
    }

    public void setDetail(String identifier, String value) {
        this.entries.put(identifier, value);
    }

    public void setDetail(String identifier, Supplier<String> valueSupplier) {
        try {
            this.setDetail(identifier, valueSupplier.get());
        } catch (Exception var4) {
            LOGGER.warn("Failed to get system info for {}", identifier, var4);
            this.setDetail(identifier, "ERR");
        }
    }

    private void putHardware(SystemInfo info) {
        HardwareAbstractionLayer hardware = info.getHardware();
        this.ignoreErrors("processor", () -> this.putProcessor(hardware.getProcessor()));
        this.ignoreErrors("graphics", () -> this.putGraphics(hardware.getGraphicsCards()));
        this.ignoreErrors("memory", () -> this.putMemory(hardware.getMemory()));
        this.ignoreErrors("storage", this::putStorage);
    }

    private void ignoreErrors(String groupIdentifier, Runnable executor) {
        try {
            executor.run();
        } catch (Throwable var4) {
            LOGGER.warn("Failed retrieving info for group {}", groupIdentifier, var4);
        }
    }

    public static float sizeInMiB(long bytes) {
        return (float)bytes / 1048576.0F;
    }

    private void putPhysicalMemory(List<PhysicalMemory> memorySlots) {
        int i = 0;

        for (PhysicalMemory physicalMemory : memorySlots) {
            String string = String.format(Locale.ROOT, "Memory slot #%d ", i++);
            this.setDetail(string + "capacity (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(physicalMemory.getCapacity())));
            this.setDetail(string + "clockSpeed (GHz)", () -> String.format(Locale.ROOT, "%.2f", (float)physicalMemory.getClockSpeed() / 1.0E9F));
            this.setDetail(string + "type", physicalMemory::getMemoryType);
        }
    }

    private void putVirtualMemory(VirtualMemory memory) {
        this.setDetail("Virtual memory max (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getVirtualMax())));
        this.setDetail("Virtual memory used (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getVirtualInUse())));
        this.setDetail("Swap memory total (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getSwapTotal())));
        this.setDetail("Swap memory used (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(memory.getSwapUsed())));
    }

    private void putMemory(GlobalMemory memory) {
        this.ignoreErrors("physical memory", () -> this.putPhysicalMemory(memory.getPhysicalMemory()));
        this.ignoreErrors("virtual memory", () -> this.putVirtualMemory(memory.getVirtualMemory()));
    }

    private void putGraphics(List<GraphicsCard> gpus) {
        int i = 0;

        for (GraphicsCard graphicsCard : gpus) {
            String string = String.format(Locale.ROOT, "Graphics card #%d ", i++);
            this.setDetail(string + "name", graphicsCard::getName);
            this.setDetail(string + "vendor", graphicsCard::getVendor);
            this.setDetail(string + "VRAM (MiB)", () -> String.format(Locale.ROOT, "%.2f", sizeInMiB(graphicsCard.getVRam())));
            this.setDetail(string + "deviceId", graphicsCard::getDeviceId);
            this.setDetail(string + "versionInfo", graphicsCard::getVersionInfo);
        }
    }

    private void putProcessor(CentralProcessor cpu) {
        ProcessorIdentifier processorIdentifier = cpu.getProcessorIdentifier();
        this.setDetail("Processor Vendor", processorIdentifier::getVendor);
        this.setDetail("Processor Name", processorIdentifier::getName);
        this.setDetail("Identifier", processorIdentifier::getIdentifier);
        this.setDetail("Microarchitecture", processorIdentifier::getMicroarchitecture);
        this.setDetail("Frequency (GHz)", () -> String.format(Locale.ROOT, "%.2f", (float)processorIdentifier.getVendorFreq() / 1.0E9F));
        this.setDetail("Number of physical packages", () -> String.valueOf(cpu.getPhysicalPackageCount()));
        this.setDetail("Number of physical CPUs", () -> String.valueOf(cpu.getPhysicalProcessorCount()));
        this.setDetail("Number of logical CPUs", () -> String.valueOf(cpu.getLogicalProcessorCount()));
    }

    private void putStorage() {
        this.putSpaceForProperty("jna.tmpdir");
        this.putSpaceForProperty("org.lwjgl.system.SharedLibraryExtractPath");
        this.putSpaceForProperty("io.netty.native.workdir");
        this.putSpaceForProperty("java.io.tmpdir");
        this.putSpaceForPath("workdir", () -> "");
    }

    private void putSpaceForProperty(String property) {
        this.putSpaceForPath(property, () -> System.getProperty(property));
    }

    private void putSpaceForPath(String property, Supplier<@Nullable String> valueSupplier) {
        String string = "Space in storage for " + property + " (MiB)";

        try {
            String string1 = valueSupplier.get();
            if (string1 == null) {
                this.setDetail(string, "<path not set>");
                return;
            }

            FileStore fileStore = Files.getFileStore(Path.of(string1));
            this.setDetail(
                string, String.format(Locale.ROOT, "available: %.2f, total: %.2f", sizeInMiB(fileStore.getUsableSpace()), sizeInMiB(fileStore.getTotalSpace()))
            );
        } catch (InvalidPathException var6) {
            LOGGER.warn("{} is not a path", property, var6);
            this.setDetail(string, "<invalid path>");
        } catch (Exception var7) {
            LOGGER.warn("Failed retrieving storage space for {}", property, var7);
            this.setDetail(string, "ERR");
        }
    }

    public void appendToCrashReportString(StringBuilder reportAppender) {
        reportAppender.append("-- ").append("System Details").append(" --\n");
        reportAppender.append("Details:");
        this.entries.forEach((string, string1) -> {
            reportAppender.append("\n\t");
            reportAppender.append(string);
            reportAppender.append(": ");
            reportAppender.append(string1);
        });
    }

    public String toLineSeparatedString() {
        return this.entries.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining(System.lineSeparator()));
    }
}
