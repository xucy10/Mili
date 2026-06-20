package net.minecraft.util.profiling.metrics.storage;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class MetricsPersister {
    public static final Path PROFILING_RESULTS_DIR = Paths.get("debug/profiling");
    public static final String METRICS_DIR_NAME = "metrics";
    public static final String DEVIATIONS_DIR_NAME = "deviations";
    public static final String PROFILING_RESULT_FILENAME = "profiling.txt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String rootFolderName;

    public MetricsPersister(String rootFolderName) {
        this.rootFolderName = rootFolderName;
    }

    public Path saveReports(Set<MetricSampler> samplers, Map<MetricSampler, List<RecordedDeviation>> deviations, ProfileResults results) {
        try {
            Files.createDirectories(PROFILING_RESULTS_DIR);
        } catch (IOException var8) {
            throw new UncheckedIOException(var8);
        }

        try {
            Path path = Files.createTempDirectory("minecraft-profiling");
            path.toFile().deleteOnExit();
            Files.createDirectories(PROFILING_RESULTS_DIR);
            Path path1 = path.resolve(this.rootFolderName);
            Path path2 = path1.resolve("metrics");
            this.saveMetrics(samplers, path2);
            if (!deviations.isEmpty()) {
                this.saveDeviations(deviations, path1.resolve("deviations"));
            }

            this.saveProfilingTaskExecutionResult(results, path1);
            return path;
        } catch (IOException var7) {
            throw new UncheckedIOException(var7);
        }
    }

    private void saveMetrics(Set<MetricSampler> samplers, Path path) {
        if (samplers.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one sampler to persist");
        } else {
            Map<MetricCategory, List<MetricSampler>> map = samplers.stream().collect(Collectors.groupingBy(MetricSampler::getCategory));
            map.forEach((metricCategory, list) -> this.saveCategory(metricCategory, (List<MetricSampler>)list, path));
        }
    }

    private void saveCategory(MetricCategory category, List<MetricSampler> samplers, Path path) {
        Path path1 = path.resolve(Util.sanitizeName(category.getDescription(), Identifier::validPathChar) + ".csv");
        Writer writer = null;

        try {
            Files.createDirectories(path1.getParent());
            writer = Files.newBufferedWriter(path1, StandardCharsets.UTF_8);
            CsvOutput.Builder builder = CsvOutput.builder();
            builder.addColumn("@tick");

            for (MetricSampler metricSampler : samplers) {
                builder.addColumn(metricSampler.getName());
            }

            CsvOutput csvOutput = builder.build(writer);
            List<MetricSampler.SamplerResult> list = samplers.stream().map(MetricSampler::result).collect(Collectors.toList());
            int min = list.stream().mapToInt(MetricSampler.SamplerResult::getFirstTick).summaryStatistics().getMin();
            int max = list.stream().mapToInt(MetricSampler.SamplerResult::getLastTick).summaryStatistics().getMax();

            for (int i = min; i <= max; i++) {
                int i1 = i;
                Stream<String> stream = list.stream().map(samplerResult -> String.valueOf(samplerResult.valueAtTick(i1)));
                Object[] objects = Stream.concat(Stream.of(String.valueOf(i)), stream).toArray(String[]::new);
                csvOutput.writeRow(objects);
            }

            LOGGER.info("Flushed metrics to {}", path1);
        } catch (Exception var18) {
            LOGGER.error("Could not save profiler results to {}", path1, var18);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private void saveDeviations(Map<MetricSampler, List<RecordedDeviation>> deviations, Path path) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS", Locale.UK).withZone(ZoneId.systemDefault());
        deviations.forEach(
            (metricSampler, list) -> list.forEach(
                recordedDeviation -> {
                    String string = dateTimeFormatter.format(recordedDeviation.timestamp);
                    Path path1 = path.resolve(Util.sanitizeName(metricSampler.getName(), Identifier::validPathChar))
                        .resolve(String.format(Locale.ROOT, "%d@%s.txt", recordedDeviation.tick, string));
                    recordedDeviation.profilerResultAtTick.saveResults(path1);
                }
            )
        );
    }

    private void saveProfilingTaskExecutionResult(ProfileResults results, Path outputPath) {
        results.saveResults(outputPath.resolve("profiling.txt"));
    }
}
