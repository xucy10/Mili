package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

public class JigsawJunction {
    private final int sourceX;
    private final int sourceGroundY;
    private final int sourceZ;
    private final int deltaY;
    private final StructureTemplatePool.Projection destProjection;

    public JigsawJunction(int sourceX, int sourceGroundY, int sourceZ, int deltaY, StructureTemplatePool.Projection destProjection) {
        this.sourceX = sourceX;
        this.sourceGroundY = sourceGroundY;
        this.sourceZ = sourceZ;
        this.deltaY = deltaY;
        this.destProjection = destProjection;
    }

    public int getSourceX() {
        return this.sourceX;
    }

    public int getSourceGroundY() {
        return this.sourceGroundY;
    }

    public int getSourceZ() {
        return this.sourceZ;
    }

    public int getDeltaY() {
        return this.deltaY;
    }

    public StructureTemplatePool.Projection getDestProjection() {
        return this.destProjection;
    }

    public <T> Dynamic<T> serialize(DynamicOps<T> ops) {
        Builder<T, T> builder = ImmutableMap.builder();
        builder.put(ops.createString("source_x"), ops.createInt(this.sourceX))
            .put(ops.createString("source_ground_y"), ops.createInt(this.sourceGroundY))
            .put(ops.createString("source_z"), ops.createInt(this.sourceZ))
            .put(ops.createString("delta_y"), ops.createInt(this.deltaY))
            .put(ops.createString("dest_proj"), ops.createString(this.destProjection.getName()));
        return new Dynamic<>(ops, ops.createMap(builder.build()));
    }

    public static <T> JigsawJunction deserialize(Dynamic<T> dynamic) {
        return new JigsawJunction(
            dynamic.get("source_x").asInt(0),
            dynamic.get("source_ground_y").asInt(0),
            dynamic.get("source_z").asInt(0),
            dynamic.get("delta_y").asInt(0),
            StructureTemplatePool.Projection.byName(dynamic.get("dest_proj").asString(""))
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            JigsawJunction jigsawJunction = (JigsawJunction)other;
            return this.sourceX == jigsawJunction.sourceX
                && this.sourceZ == jigsawJunction.sourceZ
                && this.deltaY == jigsawJunction.deltaY
                && this.destProjection == jigsawJunction.destProjection;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.sourceX;
        i = 31 * i + this.sourceGroundY;
        i = 31 * i + this.sourceZ;
        i = 31 * i + this.deltaY;
        return 31 * i + this.destProjection.hashCode();
    }

    @Override
    public String toString() {
        return "JigsawJunction{sourceX="
            + this.sourceX
            + ", sourceGroundY="
            + this.sourceGroundY
            + ", sourceZ="
            + this.sourceZ
            + ", deltaY="
            + this.deltaY
            + ", destProjection="
            + this.destProjection
            + "}";
    }
}
