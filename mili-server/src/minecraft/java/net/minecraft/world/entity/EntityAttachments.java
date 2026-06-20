package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntityAttachments {
    private final Map<EntityAttachment, List<Vec3>> attachments;

    EntityAttachments(Map<EntityAttachment, List<Vec3>> attachments) {
        this.attachments = attachments;
    }

    public static EntityAttachments createDefault(float width, float height) {
        return builder().build(width, height);
    }

    public static EntityAttachments.Builder builder() {
        return new EntityAttachments.Builder();
    }

    public EntityAttachments scale(float xScale, float yScale, float zScale) {
        return new EntityAttachments(Util.makeEnumMap(EntityAttachment.class, entityAttachment -> {
            List<Vec3> list = new ArrayList<>();

            for (Vec3 vec3 : this.attachments.get(entityAttachment)) {
                list.add(vec3.multiply(xScale, yScale, zScale));
            }

            return list;
        }));
    }

    public @Nullable Vec3 getNullable(EntityAttachment attachment, int index, float yRot) {
        List<Vec3> list = this.attachments.get(attachment);
        return index >= 0 && index < list.size() ? transformPoint(list.get(index), yRot) : null;
    }

    public Vec3 get(EntityAttachment attachment, int index, float yRot) {
        Vec3 nullable = this.getNullable(attachment, index, yRot);
        if (nullable == null) {
            throw new IllegalStateException("Had no attachment point of type: " + attachment + " for index: " + index);
        } else {
            return nullable;
        }
    }

    public Vec3 getAverage(EntityAttachment attachment) {
        List<Vec3> list = this.attachments.get(attachment);
        if (list != null && !list.isEmpty()) {
            Vec3 vec3 = Vec3.ZERO;

            for (Vec3 vec31 : list) {
                vec3 = vec3.add(vec31);
            }

            return vec3.scale(1.0F / list.size());
        } else {
            throw new IllegalStateException("No attachment points of type: PASSENGER");
        }
    }

    public Vec3 getClamped(EntityAttachment attachment, int index, float yRot) {
        List<Vec3> list = this.attachments.get(attachment);
        if (list.isEmpty()) {
            throw new IllegalStateException("Had no attachment points of type: " + attachment);
        } else {
            Vec3 vec3 = list.get(Mth.clamp(index, 0, list.size() - 1));
            return transformPoint(vec3, yRot);
        }
    }

    private static Vec3 transformPoint(Vec3 point, float yRot) {
        return point.yRot(-yRot * (float) (Math.PI / 180.0));
    }

    public static class Builder {
        private final Map<EntityAttachment, List<Vec3>> attachments = new EnumMap<>(EntityAttachment.class);

        Builder() {
        }

        public EntityAttachments.Builder attach(EntityAttachment attachment, float x, float y, float z) {
            return this.attach(attachment, new Vec3(x, y, z));
        }

        public EntityAttachments.Builder attach(EntityAttachment attachment, Vec3 pos) {
            this.attachments.computeIfAbsent(attachment, entityAttachment -> new ArrayList<>(1)).add(pos);
            return this;
        }

        public EntityAttachments build(float width, float height) {
            Map<EntityAttachment, List<Vec3>> map = Util.makeEnumMap(EntityAttachment.class, entityAttachment -> {
                List<Vec3> list = this.attachments.get(entityAttachment);
                return list == null ? entityAttachment.createFallbackPoints(width, height) : List.copyOf(list);
            });
            return new EntityAttachments(map);
        }
    }
}
