package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap.Entry;
import java.util.Objects;

public class SpatialAttributeInterpolator {
    private final Reference2DoubleArrayMap<EnvironmentAttributeMap> weightsBySource = new Reference2DoubleArrayMap<>();

    public void clear() {
        this.weightsBySource.clear();
    }

    public SpatialAttributeInterpolator accumulate(double weight, EnvironmentAttributeMap attributes) {
        this.weightsBySource.mergeDouble(attributes, weight, Double::sum);
        return this;
    }

    public <Value> Value applyAttributeLayer(EnvironmentAttribute<Value> attribute, Value value) {
        if (this.weightsBySource.isEmpty()) {
            return value;
        } else if (this.weightsBySource.size() == 1) {
            EnvironmentAttributeMap environmentAttributeMap = this.weightsBySource.keySet().iterator().next();
            return environmentAttributeMap.applyModifier(attribute, value);
        } else {
            LerpFunction<Value> lerpFunction = attribute.type().spatialLerp();
            Value object = null;
            double d = 0.0;

            for (Entry<EnvironmentAttributeMap> entry : Reference2DoubleMaps.fastIterable(this.weightsBySource)) {
                EnvironmentAttributeMap environmentAttributeMap1 = entry.getKey();
                double doubleValue = entry.getDoubleValue();
                Value object1 = environmentAttributeMap1.applyModifier(attribute, value);
                d += doubleValue;
                if (object == null) {
                    object = object1;
                } else {
                    float f = (float)(doubleValue / d);
                    object = lerpFunction.apply(f, object, object1);
                }
            }

            return Objects.requireNonNull(object);
        }
    }
}
