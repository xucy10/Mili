package net.minecraft.network.syncher;

import java.util.List;

public interface SyncedDataHolder {
    void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor);

    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> newData);
}
