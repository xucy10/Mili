package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeProbe {
    private final Map<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbes = new Reference2ObjectOpenHashMap<>();
    private final Function<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbeFactory = environmentAttribute -> new EnvironmentAttributeProbe.ValueProbe<>(
        environmentAttribute
    );
    @Nullable Level level;
    @Nullable Vec3 position;
    final SpatialAttributeInterpolator biomeInterpolator = new SpatialAttributeInterpolator();

    public void reset() {
        this.level = null;
        this.position = null;
        this.biomeInterpolator.clear();
        this.valueProbes.clear();
    }

    public void tick(Level level, Vec3 pos) {
        this.level = level;
        this.position = pos;
        this.valueProbes.values().removeIf(EnvironmentAttributeProbe.ValueProbe::tick);
        this.biomeInterpolator.clear();
        GaussianSampler.sample(
            pos.scale(0.25),
            level.getBiomeManager()::getNoiseBiomeAtQuart,
            (weight, value) -> this.biomeInterpolator.accumulate(weight, value.value().getAttributes())
        );
    }

    public <Value> Value getValue(EnvironmentAttribute<Value> attribute, float partialTick) {
        EnvironmentAttributeProbe.ValueProbe<Value> valueProbe = (EnvironmentAttributeProbe.ValueProbe<Value>)this.valueProbes
            .computeIfAbsent(attribute, this.valueProbeFactory);
        return valueProbe.get(attribute, partialTick);
    }

    class ValueProbe<Value> {
        private Value lastValue;
        private @Nullable Value newValue;

        public ValueProbe(final EnvironmentAttribute<Value> attribute) {
            Value valueFromLevel = this.getValueFromLevel(attribute);
            this.lastValue = valueFromLevel;
            this.newValue = valueFromLevel;
        }

        private Value getValueFromLevel(EnvironmentAttribute<Value> attribute) {
            return EnvironmentAttributeProbe.this.level != null && EnvironmentAttributeProbe.this.position != null
                ? EnvironmentAttributeProbe.this.level
                    .environmentAttributes()
                    .getValue(attribute, EnvironmentAttributeProbe.this.position, EnvironmentAttributeProbe.this.biomeInterpolator)
                : attribute.defaultValue();
        }

        public boolean tick() {
            if (this.newValue == null) {
                return true;
            } else {
                this.lastValue = this.newValue;
                this.newValue = null;
                return false;
            }
        }

        public Value get(EnvironmentAttribute<Value> attribute, float partialTick) {
            if (this.newValue == null) {
                this.newValue = this.getValueFromLevel(attribute);
            }

            return attribute.type().partialTickLerp().apply(partialTick, this.lastValue, this.newValue);
        }
    }
}
