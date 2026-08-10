package net.minecraft.world.entity;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public interface OwnableEntity {
    @Nullable EntityReference<LivingEntity> getOwnerReference();

    Level level();

    default @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.getOwnerReference(), this.level());
    }

    default @Nullable LivingEntity getRootOwner() {
        Set<Object> set = new ObjectArraySet<>();
        LivingEntity owner = this.getOwner();
        set.add(this);

        while (owner instanceof OwnableEntity) {
            OwnableEntity ownableEntity = (OwnableEntity)owner;
            LivingEntity owner1 = ownableEntity.getOwner();
            if (set.contains(owner1)) {
                return null;
            }

            set.add(owner);
            owner = ownableEntity.getOwner();
        }

        return owner;
    }
}
