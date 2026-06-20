package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.jfr.parse.JfrStatsParser;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SummaryReporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Runnable onDeregistration;

    protected SummaryReporter(Runnable onDeregistration) {
        this.onDeregistration = onDeregistration;
    }

    public void recordingStopped(@Nullable Path outputPath) {
        if (outputPath != null) {
            this.onDeregistration.run();
            infoWithFallback(() -> "Dumped flight recorder profiling to " + outputPath);

            JfrStatsResult jfrStatsResult;
            try {
                jfrStatsResult = JfrStatsParser.parse(outputPath);
            } catch (Throwable var5) {
                warnWithFallback(() -> "Failed to parse JFR recording", var5);
                return;
            }

            try {
                infoWithFallback(jfrStatsResult::asJson);
                Path path = outputPath.resolveSibling("jfr-report-" + StringUtils.substringBefore(outputPath.getFileName().toString(), ".jfr") + ".json");
                Files.writeString(path, jfrStatsResult.asJson(), StandardOpenOption.CREATE);
                infoWithFallback(() -> "Dumped recording summary to " + path);
            } catch (Throwable var4) {
                warnWithFallback(() -> "Failed to output JFR report", var4);
            }
        }
    }

    private static void infoWithFallback(Supplier<String> message) {
        if (LogUtils.isLoggerActive()) {
            LOGGER.info(message.get());
        } else {
            Bootstrap.realStdoutPrintln(message.get());
        }
    }

    private static void warnWithFallback(Supplier<String> message, Throwable throwable) {
        if (LogUtils.isLoggerActive()) {
            LOGGER.warn(message.get(), throwable);
        } else {
            Bootstrap.realStdoutPrintln(message.get());
            throwable.printStackTrace(Bootstrap.STDOUT);
        }
    }
}
