package fun.bm.mili.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalForcer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Powered by NetherPortalFix(https://github.com/TwelveIterationMods/NetherPortalFix)
// Ported from Leaves
public class ReturnPortalManager {

    private static final int MAX_PORTAL_DISTANCE_SQ = 16;
    private static final String RETURN_PORTAL_LIST = "ReturnPortalList";
    private static final String RETURN_PORTAL_UID = "UID";
    private static final String FROM_DIM = "FromDim";
    private static final String FROM_POS = "FromPos";
    private static final String TO_POS = "ToPos";

    @SuppressWarnings("deprecation")
    public static BlockPos findPortalAt(Player player, ResourceKey<Level> dim, BlockPos pos) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ServerLevel fromWorld = server.getLevel(dim);
            if (fromWorld != null) {
                PortalForcer portalForcer = fromWorld.getPortalForcer();
                return portalForcer.findClosestPortalPosition(pos, false, fromWorld.getWorldBorder()).orElse(null);
            }
        }

        return null;
    }

    public static ListTag getPlayerPortalList(Player player) {
        CompoundTag data = player.getLeavesData();
        ListTag list = data.getListOrEmpty(RETURN_PORTAL_LIST);
        data.put(RETURN_PORTAL_LIST, list);
        return list;
    }

    @Nullable
    public static ReturnPortal findReturnPortal(ServerPlayer player, ResourceKey<Level> fromDim, BlockPos fromPos) {
        ListTag portalList = getPlayerPortalList(player);
        for (Tag entry : portalList) {
            // Mili start - fix: use instanceof check instead of direct cast; use isPresent checks instead of orElseThrow; use equals() instead of == for ResourceKey
            if (!(entry instanceof CompoundTag portal)) continue;
            var fromDimOpt = portal.getString(FROM_DIM);
            if (fromDimOpt.isEmpty()) continue;
            ResourceKey<Level> entryFromDim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(fromDimOpt.get()));
            if (entryFromDim.equals(fromDim)) {
                var fromPosOpt = portal.getLong(FROM_POS);
                if (fromPosOpt.isEmpty()) continue;
                BlockPos portalTrigger = BlockPos.of(fromPosOpt.get());
                if (portalTrigger.distSqr(fromPos) <= MAX_PORTAL_DISTANCE_SQ) {
                    final UUID uid;
                    if (portal.contains(RETURN_PORTAL_UID)) {
                        var uidOpt = portal.read(RETURN_PORTAL_UID, UUIDUtil.CODEC);
                        if (uidOpt.isEmpty()) continue;
                        uid = uidOpt.get();
                    } else {
                        uid = UUID.randomUUID();
                    }
                    var toPosOpt = portal.getLong(TO_POS);
                    if (toPosOpt.isEmpty()) continue;
                    final BlockPos pos = BlockPos.of(toPosOpt.get());
                    return new ReturnPortal(uid, pos);
                }
            }
            // Mili end
        }

        return null;
    }

    public static void storeReturnPortal(ServerPlayer player, ResourceKey<Level> fromDim, BlockPos fromPos, BlockPos toPos) {
        ListTag portalList = getPlayerPortalList(player);
        ReturnPortal returnPortal = findReturnPortal(player, fromDim, fromPos);
        if (returnPortal != null) {
            removeReturnPortal(player, returnPortal);
        }

        CompoundTag portalCompound = new CompoundTag();
        portalCompound.store(RETURN_PORTAL_UID, UUIDUtil.CODEC, UUID.randomUUID());
        portalCompound.putString(FROM_DIM, String.valueOf(fromDim.identifier()));
        portalCompound.putLong(FROM_POS, fromPos.asLong());
        portalCompound.putLong(TO_POS, toPos.asLong());
        portalList.add(portalCompound);
    }

    public static void removeReturnPortal(ServerPlayer player, ReturnPortal portal) {
        ListTag portalList = getPlayerPortalList(player);
        for (int i = 0; i < portalList.size(); i++) {
            // Mili start - fix: use isPresent check instead of orElseThrow to prevent NoSuchElementException
            // if the UUID tag is malformed or corrupted
            if (!(portalList.get(i) instanceof CompoundTag entry)) continue;
            if (entry.contains(RETURN_PORTAL_UID)) {
                var uidOpt = entry.read(RETURN_PORTAL_UID, UUIDUtil.CODEC);
                if (uidOpt.isPresent() && uidOpt.get().equals(portal.uid)) {
                    portalList.remove(i);
                    break;
                }
            }
            // Mili end
        }
    }

    public record ReturnPortal(UUID uid, BlockPos pos) {
    }
}
