package net.minecraft.server.packs.repository;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.packs.PackResources;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

public class PackRepository {
    private final Set<RepositorySource> sources;
    private Map<String, Pack> available = ImmutableMap.of();
    private List<Pack> selected = ImmutableList.of();
    private final net.minecraft.world.level.validation.DirectoryValidator validator; // Paper - add validator

    // Paper start - add validator
    public PackRepository(net.minecraft.world.level.validation.DirectoryValidator validator, RepositorySource... providers) {
        this.validator = validator;
        // Paper end - add validator
        this.sources = ImmutableSet.copyOf(providers);
    }

    public static String displayPackList(Collection<Pack> packs) {
        return packs.stream().map(pack -> pack.getId() + (pack.getCompatibility().isCompatible() ? "" : " (incompatible)")).collect(Collectors.joining(", "));
    }

    public void reload() {
        // Paper start - add pendingReload flag to determine required pack loading
        this.reload(false);
    }
    public void reload(final boolean pendingReload) {
        // Paper end - add pendingReload flag to determine required pack loading
        List<String> list = this.selected.stream().map(Pack::getId).collect(ImmutableList.toImmutableList());
        this.available = this.discoverAvailable();
        this.selected = this.rebuildSelected(list, pendingReload); // Paper - add pendingReload flag to determine required pack loading
    }

    private Map<String, Pack> discoverAvailable() {
        Map<String, Pack> map = Maps.newTreeMap();

        for (RepositorySource repositorySource : this.sources) {
            repositorySource.loadPacks(pack -> map.put(pack.getId(), pack));
        }

        // Paper start - custom plugin-loaded datapacks
        final io.papermc.paper.datapack.PaperDatapackRegistrar registrar = new io.papermc.paper.datapack.PaperDatapackRegistrar(this.validator, map);
        io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callStaticRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.DATAPACK_DISCOVERY,
            registrar,
            io.papermc.paper.plugin.bootstrap.BootstrapContext.class
        );
        return ImmutableMap.copyOf(registrar.discoveredPacks);
        // Paper end - custom plugin-loaded datapacks
    }

    public boolean isAbleToClearAnyPack() {
        List<Pack> list = this.rebuildSelected(List.of(), false); // Paper - add pendingReload flag to determine required pack loading
        return !this.selected.equals(list);
    }

    public void setSelected(Collection<String> ids, final boolean pendingReload) { // Paper - add pendingReload flag to determine required pack loading
        this.selected = this.rebuildSelected(ids, pendingReload); // Paper - add pendingReload flag to determine required pack loading
    }

    public boolean addPack(String id) {
        Pack pack = this.available.get(id);
        if (pack != null && !this.selected.contains(pack)) {
            List<Pack> list = Lists.newArrayList(this.selected);
            list.add(pack);
            this.selected = list;
            return true;
        } else {
            return false;
        }
    }

    public boolean removePack(String id) {
        Pack pack = this.available.get(id);
        if (pack != null && this.selected.contains(pack)) {
            List<Pack> list = Lists.newArrayList(this.selected);
            list.remove(pack);
            this.selected = list;
            return true;
        } else {
            return false;
        }
    }

    private List<Pack> rebuildSelected(Collection<String> ids, boolean pendingReload) { // Paper - add pendingReload flag to determine required pack loading
        List<Pack> list = this.getAvailablePacks(ids).collect(Util.toMutableList());

        for (Pack pack : this.available.values()) {
            if (pack.isRequired() && !list.contains(pack) && pendingReload) { // Paper - add pendingReload flag to determine required pack loading
                pack.getDefaultPosition().insert(list, pack, Pack::selectionConfig, false);
            }
        }

        return ImmutableList.copyOf(list);
    }

    private Stream<Pack> getAvailablePacks(Collection<String> ids) {
        return ids.stream().map(this.available::get).filter(Objects::nonNull);
    }

    public Collection<String> getAvailableIds() {
        return this.available.keySet();
    }

    public Collection<Pack> getAvailablePacks() {
        return this.available.values();
    }

    public Collection<String> getSelectedIds() {
        return this.selected.stream().map(Pack::getId).collect(ImmutableSet.toImmutableSet());
    }

    public FeatureFlagSet getRequestedFeatureFlags() {
        return this.getSelectedPacks().stream().map(Pack::getRequestedFeatures).reduce(FeatureFlagSet::join).orElse(FeatureFlagSet.of());
    }

    public Collection<Pack> getSelectedPacks() {
        return this.selected;
    }

    public @Nullable Pack getPack(String id) {
        return this.available.get(id);
    }

    public boolean isAvailable(String id) {
        return this.available.containsKey(id);
    }

    public List<PackResources> openAllSelected() {
        return this.selected.stream().map(Pack::open).collect(ImmutableList.toImmutableList());
    }
}
