package net.minecraft.server.packs.resources;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;

public interface ResourceManager extends ResourceProvider {
    Set<String> getNamespaces();

    List<Resource> getResourceStack(Identifier location);

    Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter);

    Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter);

    Stream<PackResources> listPacks();

    public static enum Empty implements ResourceManager {
        INSTANCE;

        @Override
        public Set<String> getNamespaces() {
            return Set.of();
        }

        @Override
        public Optional<Resource> getResource(Identifier location) {
            return Optional.empty();
        }

        @Override
        public List<Resource> getResourceStack(Identifier location) {
            return List.of();
        }

        @Override
        public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) {
            return Map.of();
        }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) {
            return Map.of();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.of();
        }
    }
}
