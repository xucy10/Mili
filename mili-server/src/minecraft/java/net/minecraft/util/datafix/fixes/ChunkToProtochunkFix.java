package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChunkToProtochunkFix extends DataFix {
    private static final int NUM_SECTIONS = 16;

    public ChunkToProtochunkFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ChunkToProtoChunkFix",
            this.getInputSchema().getType(References.CHUNK),
            this.getOutputSchema().getType(References.CHUNK),
            dynamic -> dynamic.update("Level", ChunkToProtochunkFix::fixChunkData)
        );
    }

    private static <T> Dynamic<T> fixChunkData(Dynamic<T> chunkData) {
        boolean _boolean = chunkData.get("TerrainPopulated").asBoolean(false);
        boolean flag = chunkData.get("LightPopulated").asNumber().result().isEmpty() || chunkData.get("LightPopulated").asBoolean(false);
        String string;
        if (_boolean) {
            if (flag) {
                string = "mobs_spawned";
            } else {
                string = "decorated";
            }
        } else {
            string = "carved";
        }

        return repackTicks(repackBiomes(chunkData)).set("Status", chunkData.createString(string)).set("hasLegacyStructureData", chunkData.createBoolean(true));
    }

    private static <T> Dynamic<T> repackBiomes(Dynamic<T> dynamic) {
        return dynamic.update("Biomes", dynamic1 -> DataFixUtils.orElse(dynamic1.asByteBufferOpt().result().map(byteBuffer -> {
            int[] ints = new int[256];

            for (int i = 0; i < ints.length; i++) {
                if (i < byteBuffer.capacity()) {
                    ints[i] = byteBuffer.get(i) & 255;
                }
            }

            return dynamic.createIntList(Arrays.stream(ints));
        }), dynamic1));
    }

    private static <T> Dynamic<T> repackTicks(Dynamic<T> dynamic) {
        return DataFixUtils.orElse(
            dynamic.get("TileTicks")
                .asStreamOpt()
                .result()
                .map(
                    stream -> {
                        List<ShortList> list = IntStream.range(0, 16).mapToObj(i -> new ShortArrayList()).collect(Collectors.toList());
                        stream.forEach(dynamic1 -> {
                            int _int = dynamic1.get("x").asInt(0);
                            int _int1 = dynamic1.get("y").asInt(0);
                            int _int2 = dynamic1.get("z").asInt(0);
                            short s = packOffsetCoordinates(_int, _int1, _int2);
                            list.get(_int1 >> 4).add(s);
                        });
                        return dynamic.remove("TileTicks")
                            .set(
                                "ToBeTicked",
                                dynamic.createList(
                                    list.stream().map(list1 -> dynamic.createList(list1.intStream().mapToObj(i -> dynamic.createShort((short)i))))
                                )
                            );
                    }
                ),
            dynamic
        );
    }

    private static short packOffsetCoordinates(int x, int y, int z) {
        return (short)(x & 15 | (y & 15) << 4 | (z & 15) << 8);
    }
}
