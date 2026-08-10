package net.minecraft.world.entity.variant;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class VariantUtils {
    public static final String TAG_VARIANT = "variant";

    public static <T> Holder<T> getDefaultOrAny(RegistryAccess registryAccess, ResourceKey<T> key) {
        Registry<T> registry = registryAccess.lookupOrThrow(key.registryKey());
        return registry.get(key).or(registry::getAny).orElseThrow();
    }

    public static <T> Holder<T> getAny(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> registryKey) {
        return registryAccess.lookupOrThrow(registryKey).getAny().orElseThrow();
    }

    public static <T> void writeVariant(ValueOutput output, Holder<T> variant) {
        variant.unwrapKey().ifPresent(resourceKey -> output.store("variant", Identifier.CODEC, resourceKey.identifier()));
    }

    public static <T> Optional<Holder<T>> readVariant(ValueInput input, ResourceKey<? extends Registry<T>> registryKey) {
        return input.read("variant", Identifier.CODEC).map(identifier -> ResourceKey.create(registryKey, identifier)).flatMap(input.lookup()::get);
    }

    public static <T extends PriorityProvider<SpawnContext, ?>> Optional<Holder.Reference<T>> selectVariantToSpawn(
        SpawnContext context, ResourceKey<Registry<T>> registryKey
    ) {
        ServerLevelAccessor serverLevelAccessor = context.level();
        Stream<Holder.Reference<T>> stream = serverLevelAccessor.registryAccess().lookupOrThrow(registryKey).listElements();
        return PriorityProvider.pick(stream, Holder::value, serverLevelAccessor.getRandom(), context);
    }
}
