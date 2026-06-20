package net.minecraft.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record HashedPatchMap(Map<DataComponentType<?>, Integer> addedComponents, Set<DataComponentType<?>> removedComponents) {
    public static final StreamCodec<RegistryFriendlyByteBuf, HashedPatchMap> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE), ByteBufCodecs.INT, 256),
        HashedPatchMap::addedComponents,
        ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE), 256),
        HashedPatchMap::removedComponents,
        HashedPatchMap::new
    );

    public static HashedPatchMap create(DataComponentPatch patch, HashedPatchMap.HashGenerator hashGenerator) {
        DataComponentPatch.SplitResult splitResult = patch.split();
        Map<DataComponentType<?>, Integer> map = new IdentityHashMap<>(splitResult.added().size());
        splitResult.added().forEach(typedDataComponent -> map.put(typedDataComponent.type(), hashGenerator.apply((TypedDataComponent<?>)typedDataComponent)));
        return new HashedPatchMap(map, splitResult.removed());
    }

    public boolean matches(DataComponentPatch patch, HashedPatchMap.HashGenerator hashGenerator) {
        DataComponentPatch.SplitResult splitResult = patch.split();
        if (!splitResult.removed().equals(this.removedComponents)) {
            return false;
        } else if (this.addedComponents.size() != splitResult.added().size()) {
            return false;
        } else {
            for (TypedDataComponent<?> typedDataComponent : splitResult.added()) {
                Integer integer = this.addedComponents.get(typedDataComponent.type());
                if (integer == null) {
                    return false;
                }

                Integer integer1 = hashGenerator.apply(typedDataComponent);
                if (!integer1.equals(integer)) {
                    return false;
                }
            }

            return true;
        }
    }

    @FunctionalInterface
    public interface HashGenerator extends Function<TypedDataComponent<?>, Integer> {
    }
}
