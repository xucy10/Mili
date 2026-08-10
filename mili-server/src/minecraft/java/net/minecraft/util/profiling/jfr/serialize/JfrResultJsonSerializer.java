package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.FpsStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.StructureGenStat;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {
    private static final String BYTES_PER_SECOND = "bytesPerSecond";
    private static final String COUNT = "count";
    private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
    private static final String TOTAL_BYTES = "totalBytes";
    private static final String COUNT_PER_SECOND = "countPerSecond";
    final Gson gson = new GsonBuilder().setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();

    private static void serializePacketId(PacketIdentification packetIdentification, JsonObject json) {
        json.addProperty("protocolId", packetIdentification.protocolId());
        json.addProperty("packetId", packetIdentification.packetId());
    }

    private static void serializeChunkId(ChunkIdentification chunkIdentification, JsonObject json) {
        json.addProperty("level", chunkIdentification.level());
        json.addProperty("dimension", chunkIdentification.dimension());
        json.addProperty("x", chunkIdentification.x());
        json.addProperty("z", chunkIdentification.z());
    }

    public String format(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("startedEpoch", result.recordingStarted().toEpochMilli());
        jsonObject.addProperty("endedEpoch", result.recordingEnded().toEpochMilli());
        jsonObject.addProperty("durationMs", result.recordingDuration().toMillis());
        Duration duration = result.worldCreationDuration();
        if (duration != null) {
            jsonObject.addProperty("worldGenDurationMs", duration.toMillis());
        }

        jsonObject.add("heap", this.heap(result.heapSummary()));
        jsonObject.add("cpuPercent", this.cpu(result.cpuLoadStats()));
        jsonObject.add("network", this.network(result));
        jsonObject.add("fileIO", this.fileIO(result));
        jsonObject.add("fps", this.fps(result.fps()));
        jsonObject.add("serverTick", this.serverTicks(result.serverTickTimes()));
        jsonObject.add("threadAllocation", this.threadAllocations(result.threadAllocationSummary()));
        jsonObject.add("chunkGen", this.chunkGen(result.chunkGenSummary()));
        jsonObject.add("structureGen", this.structureGen(result.structureGenStats()));
        return this.gson.toJson((JsonElement)jsonObject);
    }

    private JsonElement heap(GcHeapStat.Summary summary) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("allocationRateBytesPerSecond", summary.allocationRateBytesPerSecond());
        jsonObject.addProperty("gcCount", summary.totalGCs());
        jsonObject.addProperty("gcOverHeadPercent", summary.gcOverHead());
        jsonObject.addProperty("gcTotalDurationMs", summary.gcTotalDuration().toMillis());
        return jsonObject;
    }

    private JsonElement structureGen(List<StructureGenStat> stats) {
        JsonObject jsonObject = new JsonObject();
        Optional<TimedStatSummary<StructureGenStat>> optional = TimedStatSummary.summary(stats);
        if (optional.isEmpty()) {
            return jsonObject;
        } else {
            TimedStatSummary<StructureGenStat> timedStatSummary = optional.get();
            JsonArray jsonArray = new JsonArray();
            jsonObject.add("structure", jsonArray);
            stats.stream()
                .collect(Collectors.groupingBy(StructureGenStat::structureName))
                .forEach(
                    (string, list) -> {
                        Optional<TimedStatSummary<StructureGenStat>> optional1 = TimedStatSummary.summary((List<StructureGenStat>)list);
                        if (!optional1.isEmpty()) {
                            TimedStatSummary<StructureGenStat> timedStatSummary1 = optional1.get();
                            JsonObject jsonObject1 = new JsonObject();
                            jsonArray.add(jsonObject1);
                            jsonObject1.addProperty("name", string);
                            jsonObject1.addProperty("count", timedStatSummary1.count());
                            jsonObject1.addProperty("durationNanosTotal", timedStatSummary1.totalDuration().toNanos());
                            jsonObject1.addProperty("durationNanosAvg", timedStatSummary1.totalDuration().toNanos() / timedStatSummary1.count());
                            JsonObject jsonObject2 = Util.make(new JsonObject(), jsonObject3 -> jsonObject1.add("durationNanosPercentiles", jsonObject3));
                            timedStatSummary1.percentilesNanos().forEach((integer, _double) -> jsonObject2.addProperty("p" + integer, _double));
                            Function<StructureGenStat, JsonElement> function = structureGenStat -> {
                                JsonObject jsonObject3 = new JsonObject();
                                jsonObject3.addProperty("durationNanos", structureGenStat.duration().toNanos());
                                jsonObject3.addProperty("chunkPosX", structureGenStat.chunkPos().x);
                                jsonObject3.addProperty("chunkPosZ", structureGenStat.chunkPos().z);
                                jsonObject3.addProperty("structureName", structureGenStat.structureName());
                                jsonObject3.addProperty("level", structureGenStat.level());
                                jsonObject3.addProperty("success", structureGenStat.success());
                                return jsonObject3;
                            };
                            jsonObject.add("fastest", function.apply(timedStatSummary.fastest()));
                            jsonObject.add("slowest", function.apply(timedStatSummary.slowest()));
                            jsonObject.add(
                                "secondSlowest",
                                (JsonElement)(timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE)
                            );
                        }
                    }
                );
            return jsonObject;
        }
    }

    private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> summary) {
        JsonObject jsonObject = new JsonObject();
        if (summary.isEmpty()) {
            return jsonObject;
        } else {
            jsonObject.addProperty("durationNanosTotal", summary.stream().mapToDouble(pair1 -> pair1.getSecond().totalDuration().toNanos()).sum());
            JsonArray jsonArray = Util.make(new JsonArray(), jsonArray1 -> jsonObject.add("status", jsonArray1));

            for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : summary) {
                TimedStatSummary<ChunkGenStat> timedStatSummary = pair.getSecond();
                JsonObject jsonObject1 = Util.make(new JsonObject(), jsonArray::add);
                jsonObject1.addProperty("state", pair.getFirst().toString());
                jsonObject1.addProperty("count", timedStatSummary.count());
                jsonObject1.addProperty("durationNanosTotal", timedStatSummary.totalDuration().toNanos());
                jsonObject1.addProperty("durationNanosAvg", timedStatSummary.totalDuration().toNanos() / timedStatSummary.count());
                JsonObject jsonObject2 = Util.make(new JsonObject(), jsonObject3 -> jsonObject1.add("durationNanosPercentiles", jsonObject3));
                timedStatSummary.percentilesNanos().forEach((integer, _double) -> jsonObject2.addProperty("p" + integer, _double));
                Function<ChunkGenStat, JsonElement> function = chunkGenStat -> {
                    JsonObject jsonObject3 = new JsonObject();
                    jsonObject3.addProperty("durationNanos", chunkGenStat.duration().toNanos());
                    jsonObject3.addProperty("level", chunkGenStat.level());
                    jsonObject3.addProperty("chunkPosX", chunkGenStat.chunkPos().x);
                    jsonObject3.addProperty("chunkPosZ", chunkGenStat.chunkPos().z);
                    jsonObject3.addProperty("worldPosX", chunkGenStat.worldPos().x());
                    jsonObject3.addProperty("worldPosZ", chunkGenStat.worldPos().z());
                    return jsonObject3;
                };
                jsonObject1.add("fastest", function.apply(timedStatSummary.fastest()));
                jsonObject1.add("slowest", function.apply(timedStatSummary.slowest()));
                jsonObject1.add(
                    "secondSlowest",
                    (JsonElement)(timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE)
                );
            }

            return jsonObject;
        }
    }

    private JsonElement threadAllocations(ThreadAllocationStat.Summary summary) {
        JsonArray jsonArray = new JsonArray();
        summary.allocationsPerSecondByThread().forEach((string, _double) -> jsonArray.add(Util.make(new JsonObject(), jsonObject -> {
            jsonObject.addProperty("thread", string);
            jsonObject.addProperty("bytesPerSecond", _double);
        })));
        return jsonArray;
    }

    private JsonElement serverTicks(List<TickTimeStat> stats) {
        if (stats.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonObject = new JsonObject();
            double[] doubles = stats.stream().mapToDouble(tickTimeStat -> tickTimeStat.currentAverage().toNanos() / 1000000.0).toArray();
            DoubleSummaryStatistics doubleSummaryStatistics = DoubleStream.of(doubles).summaryStatistics();
            jsonObject.addProperty("minMs", doubleSummaryStatistics.getMin());
            jsonObject.addProperty("averageMs", doubleSummaryStatistics.getAverage());
            jsonObject.addProperty("maxMs", doubleSummaryStatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(doubles);
            map.forEach((integer, _double) -> jsonObject.addProperty("p" + integer, _double));
            return jsonObject;
        }
    }

    private JsonElement fps(List<FpsStat> stats) {
        if (stats.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonObject = new JsonObject();
            int[] ints = stats.stream().mapToInt(FpsStat::fps).toArray();
            IntSummaryStatistics intSummaryStatistics = IntStream.of(ints).summaryStatistics();
            jsonObject.addProperty("minFPS", intSummaryStatistics.getMin());
            jsonObject.addProperty("averageFPS", intSummaryStatistics.getAverage());
            jsonObject.addProperty("maxFPS", intSummaryStatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(ints);
            map.forEach((integer, _double) -> jsonObject.addProperty("p" + integer, _double));
            return jsonObject;
        }
    }

    private JsonElement fileIO(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("write", this.fileIoSummary(result.fileWrites()));
        jsonObject.add("read", this.fileIoSummary(result.fileReads()));
        jsonObject.add("chunksRead", this.ioSummary(result.readChunks(), JfrResultJsonSerializer::serializeChunkId));
        jsonObject.add("chunksWritten", this.ioSummary(result.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
        return jsonObject;
    }

    private JsonElement fileIoSummary(FileIOStat.Summary summary) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", summary.totalBytes());
        jsonObject.addProperty("count", summary.counts());
        jsonObject.addProperty("bytesPerSecond", summary.bytesPerSecond());
        jsonObject.addProperty("countPerSecond", summary.countsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        summary.topTenContributorsByTotalBytes().forEach(pair -> {
            JsonObject jsonObject1 = new JsonObject();
            jsonArray.add(jsonObject1);
            jsonObject1.addProperty("path", pair.getFirst());
            jsonObject1.addProperty("totalBytes", pair.getSecond());
        });
        return jsonObject;
    }

    private JsonElement network(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("sent", this.ioSummary(result.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        jsonObject.add("received", this.ioSummary(result.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        return jsonObject;
    }

    private <T> JsonElement ioSummary(IoSummary<T> ioSummary, BiConsumer<T, JsonObject> serializer) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", ioSummary.getTotalSize());
        jsonObject.addProperty("count", ioSummary.getTotalCount());
        jsonObject.addProperty("bytesPerSecond", ioSummary.getSizePerSecond());
        jsonObject.addProperty("countPerSecond", ioSummary.getCountsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        ioSummary.largestSizeContributors().forEach(pair -> {
            JsonObject jsonObject1 = new JsonObject();
            jsonArray.add(jsonObject1);
            T first = pair.getFirst();
            IoSummary.CountAndSize countAndSize = pair.getSecond();
            serializer.accept(first, jsonObject1);
            jsonObject1.addProperty("totalBytes", countAndSize.totalSize());
            jsonObject1.addProperty("count", countAndSize.totalCount());
            jsonObject1.addProperty("averageSize", countAndSize.averageSize());
        });
        return jsonObject;
    }

    private JsonElement cpu(List<CpuLoadStat> stats) {
        JsonObject jsonObject = new JsonObject();
        BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> biFunction = (list, toDoubleFunction) -> {
            JsonObject jsonObject1 = new JsonObject();
            DoubleSummaryStatistics doubleSummaryStatistics = list.stream().mapToDouble(toDoubleFunction).summaryStatistics();
            jsonObject1.addProperty("min", doubleSummaryStatistics.getMin());
            jsonObject1.addProperty("average", doubleSummaryStatistics.getAverage());
            jsonObject1.addProperty("max", doubleSummaryStatistics.getMax());
            return jsonObject1;
        };
        jsonObject.add("jvm", biFunction.apply(stats, CpuLoadStat::jvm));
        jsonObject.add("userJvm", biFunction.apply(stats, CpuLoadStat::userJvm));
        jsonObject.add("system", biFunction.apply(stats, CpuLoadStat::system));
        return jsonObject;
    }
}
