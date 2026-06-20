package net.minecraft.world.level.block.entity.vault;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class VaultServerData {
    static final String TAG_NAME = "server_data";
    static Codec<VaultServerData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                UUIDUtil.CODEC_LINKED_SET.lenientOptionalFieldOf("rewarded_players", Set.of()).forGetter(vaultServerData -> vaultServerData.rewardedPlayers),
                Codec.LONG.lenientOptionalFieldOf("state_updating_resumes_at", 0L).forGetter(vaultServerData -> vaultServerData.stateUpdatingResumesAt),
                ItemStack.CODEC.listOf().lenientOptionalFieldOf("items_to_eject", List.of()).forGetter(vaultServerData -> vaultServerData.itemsToEject),
                Codec.INT.lenientOptionalFieldOf("total_ejections_needed", 0).forGetter(vaultServerData -> vaultServerData.totalEjectionsNeeded)
            )
            .apply(instance, VaultServerData::new)
    );
    private static final int MAX_REWARD_PLAYERS = 128;
    private final Set<UUID> rewardedPlayers = new ObjectLinkedOpenHashSet<>();
    private long stateUpdatingResumesAt;
    private final List<ItemStack> itemsToEject = new ObjectArrayList<>();
    private long lastInsertFailTimestamp;
    private int totalEjectionsNeeded;
    boolean isDirty;

    VaultServerData(Set<UUID> rewardedPlayers, long stateUpdatingResumesAt, List<ItemStack> itemsToEject, int totalEjectionsNeeded) {
        this.rewardedPlayers.addAll(rewardedPlayers);
        this.stateUpdatingResumesAt = stateUpdatingResumesAt;
        this.itemsToEject.addAll(itemsToEject);
        this.totalEjectionsNeeded = totalEjectionsNeeded;
    }

    VaultServerData() {
    }

    void setLastInsertFailTimestamp(long lastInsertFailTimestamp) {
        this.lastInsertFailTimestamp = lastInsertFailTimestamp;
    }

    long getLastInsertFailTimestamp() {
        return this.lastInsertFailTimestamp;
    }

    public Set<UUID> getRewardedPlayers() {
        return this.rewardedPlayers;
    }

    boolean hasRewardedPlayer(Player player) {
        return this.rewardedPlayers.contains(player.getUUID());
    }

    @VisibleForTesting
    public void addToRewardedPlayers(Player player) {
    // Paper start - Vault API
        addToRewardedPlayers(player.getUUID());
    }
    public boolean addToRewardedPlayers(final java.util.UUID player) {
        final boolean removed = this.rewardedPlayers.add(player);
    // Paper end - Vault API
        if (this.rewardedPlayers.size() > 128) {
            Iterator<UUID> iterator = this.rewardedPlayers.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        this.markChanged();
        return removed; // Paper - Vault API
    }

    public long stateUpdatingResumesAt() {
        return this.stateUpdatingResumesAt;
    }

    public void pauseStateUpdatingUntil(long time) {
        this.stateUpdatingResumesAt = time;
        this.markChanged();
    }

    List<ItemStack> getItemsToEject() {
        return this.itemsToEject;
    }

    void markEjectionFinished() {
        this.totalEjectionsNeeded = 0;
        this.markChanged();
    }

    void setItemsToEject(List<ItemStack> itemsToEject) {
        this.itemsToEject.clear();
        this.itemsToEject.addAll(itemsToEject);
        this.totalEjectionsNeeded = this.itemsToEject.size();
        this.markChanged();
    }

    ItemStack getNextItemToEject() {
        return this.itemsToEject.isEmpty() ? ItemStack.EMPTY : Objects.requireNonNullElse(this.itemsToEject.get(this.itemsToEject.size() - 1), ItemStack.EMPTY);
    }

    ItemStack popNextItemToEject() {
        if (this.itemsToEject.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.markChanged();
            return Objects.requireNonNullElse(this.itemsToEject.remove(this.itemsToEject.size() - 1), ItemStack.EMPTY);
        }
    }

    void set(VaultServerData other) {
        this.stateUpdatingResumesAt = other.stateUpdatingResumesAt();
        this.itemsToEject.clear();
        this.itemsToEject.addAll(other.itemsToEject);
        this.rewardedPlayers.clear();
        this.rewardedPlayers.addAll(other.rewardedPlayers);
    }

    private void markChanged() {
        this.isDirty = true;
    }

    public float ejectionProgress() {
        return this.totalEjectionsNeeded == 1 ? 1.0F : 1.0F - Mth.inverseLerp((float)this.getItemsToEject().size(), 1.0F, (float)this.totalEjectionsNeeded);
    }

    // Paper start - Vault API
    public boolean removeFromRewardedPlayers(final UUID uuid) {
        if (this.rewardedPlayers.remove(uuid)) {
            this.markChanged();
            return true;
        }

        return false;
    }
    // Paper end - Vault API
}
