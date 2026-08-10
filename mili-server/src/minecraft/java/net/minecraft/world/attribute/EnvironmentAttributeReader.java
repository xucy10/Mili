package net.minecraft.world.attribute;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface EnvironmentAttributeReader {
    EnvironmentAttributeReader EMPTY = new EnvironmentAttributeReader() {
        @Override
        public <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute) {
            return attribute.defaultValue();
        }

        @Override
        public <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
            return attribute.defaultValue();
        }
    };

    <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute);

    default <Value> Value getValue(EnvironmentAttribute<Value> attribute, BlockPos pos) {
        return this.getValue(attribute, Vec3.atCenterOf(pos));
    }

    default <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos) {
        return this.getValue(attribute, pos, null);
    }

    <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator);
}
