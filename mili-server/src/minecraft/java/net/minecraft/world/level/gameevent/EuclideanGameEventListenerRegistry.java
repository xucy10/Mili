package net.minecraft.world.level.gameevent;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugGameEventListenerInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EuclideanGameEventListenerRegistry implements GameEventListenerRegistry {
    private final List<GameEventListener> listeners = Lists.newArrayList();
    private final Set<GameEventListener> listenersToRemove = Sets.newHashSet();
    private final List<GameEventListener> listenersToAdd = Lists.newArrayList();
    private boolean processing;
    private final ServerLevel level;
    private final int sectionY;
    private final EuclideanGameEventListenerRegistry.OnEmptyAction onEmptyAction;

    public EuclideanGameEventListenerRegistry(ServerLevel level, int sectionY, EuclideanGameEventListenerRegistry.OnEmptyAction onEmptyAction) {
        this.level = level;
        this.sectionY = sectionY;
        this.onEmptyAction = onEmptyAction;
    }

    @Override
    public boolean isEmpty() {
        return this.listeners.isEmpty();
    }

    @Override
    public void register(GameEventListener listener) {
        if (this.processing) {
            this.listenersToAdd.add(listener);
        } else {
            this.listeners.add(listener);
        }

        sendDebugInfo(this.level, listener);
    }

    private static void sendDebugInfo(ServerLevel level, GameEventListener listener) {
        if (level.debugSynchronizers().hasAnySubscriberFor(DebugSubscriptions.GAME_EVENT_LISTENERS)) {
            DebugGameEventListenerInfo debugGameEventListenerInfo = new DebugGameEventListenerInfo(listener.getListenerRadius());
            PositionSource listenerSource = listener.getListenerSource();
            if (listenerSource instanceof BlockPositionSource blockPositionSource) {
                level.debugSynchronizers().sendBlockValue(blockPositionSource.pos(), DebugSubscriptions.GAME_EVENT_LISTENERS, debugGameEventListenerInfo);
            } else if (listenerSource instanceof EntityPositionSource entityPositionSource) {
                Entity entity = level.getEntity(entityPositionSource.getUuid());
                if (entity != null) {
                    level.debugSynchronizers().sendEntityValue(entity, DebugSubscriptions.GAME_EVENT_LISTENERS, debugGameEventListenerInfo);
                }
            }
        }
    }

    @Override
    public void unregister(GameEventListener listener) {
        if (this.processing) {
            this.listenersToRemove.add(listener);
        } else {
            this.listeners.remove(listener);
        }

        if (this.listeners.isEmpty()) {
            this.onEmptyAction.apply(this.sectionY);
        }
    }

    @Override
    public boolean visitInRangeListeners(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context, GameEventListenerRegistry.ListenerVisitor visitor) {
        this.processing = true;
        boolean flag = false;

        try {
            Iterator<GameEventListener> iterator = this.listeners.iterator();

            while (iterator.hasNext()) {
                GameEventListener gameEventListener = iterator.next();
                if (this.listenersToRemove.remove(gameEventListener)) {
                    iterator.remove();
                } else {
                    Optional<Vec3> postableListenerPosition = getPostableListenerPosition(this.level, pos, gameEventListener);
                    if (postableListenerPosition.isPresent()) {
                        visitor.visit(gameEventListener, postableListenerPosition.get());
                        flag = true;
                    }
                }
            }
        } finally {
            this.processing = false;
        }

        if (!this.listenersToAdd.isEmpty()) {
            this.listeners.addAll(this.listenersToAdd);
            this.listenersToAdd.clear();
        }

        if (!this.listenersToRemove.isEmpty()) {
            this.listeners.removeAll(this.listenersToRemove);
            this.listenersToRemove.clear();
        }

        return flag;
    }

    private static Optional<Vec3> getPostableListenerPosition(ServerLevel level, Vec3 pos, GameEventListener listener) {
        Optional<Vec3> position = listener.getListenerSource().getPosition(level);
        if (position.isEmpty()) {
            return Optional.empty();
        } else {
            double d = BlockPos.containing(position.get()).distSqr(BlockPos.containing(pos));
            int i = listener.getListenerRadius() * listener.getListenerRadius();
            return d > i ? Optional.empty() : position;
        }
    }

    @FunctionalInterface
    public interface OnEmptyAction {
        void apply(int sectionY);
    }
}
