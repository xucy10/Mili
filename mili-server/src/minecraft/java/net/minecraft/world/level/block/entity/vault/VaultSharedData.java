package net.minecraft.world.level.block.entity.vault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class VaultSharedData {
    static final String TAG_NAME = "shared_data";
    static Codec<VaultSharedData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ItemStack.lenientOptionalFieldOf("display_item").forGetter(vaultSharedData -> vaultSharedData.displayItem),
                UUIDUtil.CODEC_LINKED_SET.lenientOptionalFieldOf("connected_players", Set.of()).forGetter(vaultSharedData -> vaultSharedData.connectedPlayers),
                Codec.DOUBLE
                    .lenientOptionalFieldOf("connected_particles_range", VaultConfig.DEFAULT.deactivationRange())
                    .forGetter(vaultSharedData -> vaultSharedData.connectedParticlesRange)
            )
            .apply(instance, VaultSharedData::new)
    );
    private ItemStack displayItem = ItemStack.EMPTY;
    private Set<UUID> connectedPlayers = new ObjectLinkedOpenHashSet<>();
    private double connectedParticlesRange = VaultConfig.DEFAULT.deactivationRange();
    boolean isDirty;

    VaultSharedData(ItemStack displayItem, Set<UUID> connectedPlayers, double connectedParticlesRange) {
        this.displayItem = displayItem;
        this.connectedPlayers.addAll(connectedPlayers);
        this.connectedParticlesRange = connectedParticlesRange;
    }

    VaultSharedData() {
    }

    public ItemStack getDisplayItem() {
        return this.displayItem;
    }

    public boolean hasDisplayItem() {
        return !this.displayItem.isEmpty();
    }

    public void setDisplayItem(ItemStack displayItem) {
        if (!ItemStack.matches(this.displayItem, displayItem)) {
            this.displayItem = displayItem.copy();
            this.markDirty();
        }
    }

    boolean hasConnectedPlayers() {
        return !this.connectedPlayers.isEmpty();
    }

    public Set<UUID> getConnectedPlayers() {
        return this.connectedPlayers;
    }

    double connectedParticlesRange() {
        return this.connectedParticlesRange;
    }

    void updateConnectedPlayersWithinRange(ServerLevel level, BlockPos pos, VaultServerData serverData, VaultConfig config, double deactivationRange) {
        Set<UUID> set = config.playerDetector()
            .detect(level, config.entitySelector(), pos, deactivationRange, false)
            .stream()
            .filter(uuid -> !serverData.getRewardedPlayers().contains(uuid))
            .collect(Collectors.toSet());
        if (!this.connectedPlayers.equals(set)) {
            this.connectedPlayers = set;
            this.markDirty();
        }
    }

    private void markDirty() {
        this.isDirty = true;
    }

    void set(VaultSharedData other) {
        this.displayItem = other.displayItem;
        this.connectedPlayers = other.connectedPlayers;
        this.connectedParticlesRange = other.connectedParticlesRange;
    }
}
