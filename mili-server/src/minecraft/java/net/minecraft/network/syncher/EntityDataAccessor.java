package net.minecraft.network.syncher;

public record EntityDataAccessor<T>(int id, EntityDataSerializer<T> serializer) {
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            EntityDataAccessor<?> entityDataAccessor = (EntityDataAccessor<?>)other;
            return this.id == entityDataAccessor.id;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    @Override
    public String toString() {
        return "<entity data: " + this.id + ">";
    }
}
