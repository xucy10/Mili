package net.minecraft.world.level.gameevent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class GameEventDispatcher {
    private final ServerLevel level;

    public GameEventDispatcher(ServerLevel level) {
        this.level = level;
    }

    public void post(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
        int notificationRadius = gameEvent.value().notificationRadius();
        BlockPos blockPos = BlockPos.containing(pos);
        // CraftBukkit start
        org.bukkit.event.world.GenericGameEvent apiEvent = new org.bukkit.event.world.GenericGameEvent(
            org.bukkit.craftbukkit.CraftGameEvent.minecraftHolderToBukkit(gameEvent), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(blockPos, this.level),
            (context.sourceEntity() == null) ? null : context.sourceEntity().getBukkitEntity(), notificationRadius, !org.bukkit.Bukkit.isPrimaryThread());
        if (!apiEvent.callEvent()) {
            return;
        }
        notificationRadius = apiEvent.getRadius();
        // CraftBukkit end
        int sectionPosCoord = SectionPos.blockToSectionCoord(blockPos.getX() - notificationRadius);
        int sectionPosCoord1 = SectionPos.blockToSectionCoord(blockPos.getY() - notificationRadius);
        int sectionPosCoord2 = SectionPos.blockToSectionCoord(blockPos.getZ() - notificationRadius);
        int sectionPosCoord3 = SectionPos.blockToSectionCoord(blockPos.getX() + notificationRadius);
        int sectionPosCoord4 = SectionPos.blockToSectionCoord(blockPos.getY() + notificationRadius);
        int sectionPosCoord5 = SectionPos.blockToSectionCoord(blockPos.getZ() + notificationRadius);
        List<GameEvent.ListenerInfo> list = new ArrayList<>();
        GameEventListenerRegistry.ListenerVisitor listenerVisitor = (listener, pos1) -> {
            if (listener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                list.add(new GameEvent.ListenerInfo(gameEvent, pos, context, listener, pos1));
            } else {
                listener.handleGameEvent(this.level, gameEvent, context, pos);
            }
        };
        boolean flag = false;

        for (int i = sectionPosCoord; i <= sectionPosCoord3; i++) {
            for (int i1 = sectionPosCoord2; i1 <= sectionPosCoord5; i1++) {
                ChunkAccess chunkNow = this.level.getChunkIfLoadedImmediately(i, i1); // Paper - Use getChunkIfLoadedImmediately
                if (chunkNow != null) {
                    for (int i2 = sectionPosCoord1; i2 <= sectionPosCoord4; i2++) {
                        flag |= chunkNow.getListenerRegistry(i2).visitInRangeListeners(gameEvent, pos, context, listenerVisitor);
                    }
                }
            }
        }

        if (!list.isEmpty()) {
            this.handleGameEventMessagesInQueue(list);
        }

        if (flag) {
            this.level
                .debugSynchronizers()
                .broadcastEventToTracking(BlockPos.containing(pos), DebugSubscriptions.GAME_EVENTS, new DebugGameEventInfo(gameEvent, pos));
        }
    }

    private void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> listenerInfos) {
        Collections.sort(listenerInfos);

        for (GameEvent.ListenerInfo listenerInfo : listenerInfos) {
            GameEventListener gameEventListener = listenerInfo.recipient();
            gameEventListener.handleGameEvent(this.level, listenerInfo.gameEvent(), listenerInfo.context(), listenerInfo.source());
        }
    }
}
