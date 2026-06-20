package net.minecraft.gizmos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class SimpleGizmoCollector implements GizmoCollector {
    private final List<SimpleGizmoCollector.GizmoInstance> gizmos = new ArrayList<>();
    private final List<SimpleGizmoCollector.GizmoInstance> temporaryGizmos = new ArrayList<>();

    @Override
    public GizmoProperties add(Gizmo gizmo) {
        SimpleGizmoCollector.GizmoInstance gizmoInstance = new SimpleGizmoCollector.GizmoInstance(gizmo);
        this.gizmos.add(gizmoInstance);
        return gizmoInstance;
    }

    public List<SimpleGizmoCollector.GizmoInstance> drainGizmos() {
        ArrayList<SimpleGizmoCollector.GizmoInstance> list = new ArrayList<>(this.gizmos);
        list.addAll(this.temporaryGizmos);
        long millis = Util.getMillis();
        this.gizmos.removeIf(gizmoInstance -> gizmoInstance.getExpireTimeMillis() < millis);
        this.temporaryGizmos.clear();
        return list;
    }

    public List<SimpleGizmoCollector.GizmoInstance> getGizmos() {
        return this.gizmos;
    }

    public void addTemporaryGizmos(Collection<SimpleGizmoCollector.GizmoInstance> gizmos) {
        this.temporaryGizmos.addAll(gizmos);
    }

    public static class GizmoInstance implements GizmoProperties {
        private final Gizmo gizmo;
        private boolean isAlwaysOnTop;
        private long startTimeMillis;
        private long expireTimeMillis;
        private boolean shouldFadeOut;

        GizmoInstance(Gizmo gizmo) {
            this.gizmo = gizmo;
        }

        @Override
        public GizmoProperties setAlwaysOnTop() {
            this.isAlwaysOnTop = true;
            return this;
        }

        @Override
        public GizmoProperties persistForMillis(int time) {
            this.startTimeMillis = Util.getMillis();
            this.expireTimeMillis = this.startTimeMillis + time;
            return this;
        }

        @Override
        public GizmoProperties fadeOut() {
            this.shouldFadeOut = true;
            return this;
        }

        public float getAlphaMultiplier(long timeMillis) {
            if (this.shouldFadeOut) {
                long l = this.expireTimeMillis - this.startTimeMillis;
                long l1 = timeMillis - this.startTimeMillis;
                return 1.0F - Mth.clamp((float)l1 / (float)l, 0.0F, 1.0F);
            } else {
                return 1.0F;
            }
        }

        public boolean isAlwaysOnTop() {
            return this.isAlwaysOnTop;
        }

        public long getExpireTimeMillis() {
            return this.expireTimeMillis;
        }

        public Gizmo gizmo() {
            return this.gizmo;
        }
    }
}
