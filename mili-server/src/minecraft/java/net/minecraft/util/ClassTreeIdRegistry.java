package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class ClassTreeIdRegistry {
    public static final int NO_ID_VALUE = -1;
    private final Object2IntMap<Class<?>> classToLastIdCache = Util.make(new Object2IntOpenHashMap<>(), map -> map.defaultReturnValue(-1));

    public int getLastIdFor(Class<?> clazz) {
        int _int = this.classToLastIdCache.getInt(clazz);
        if (_int != -1) {
            return _int;
        } else {
            Class<?> clazz1 = clazz;

            while ((clazz1 = clazz1.getSuperclass()) != Object.class) {
                int _int1 = this.classToLastIdCache.getInt(clazz1);
                if (_int1 != -1) {
                    return _int1;
                }
            }

            return -1;
        }
    }

    public int getCount(Class<?> clazz) {
        return this.getLastIdFor(clazz) + 1;
    }

    public int define(Class<?> clazz) {
        int lastIdFor = this.getLastIdFor(clazz);
        int i = lastIdFor == -1 ? 0 : lastIdFor + 1;
        this.classToLastIdCache.put(clazz, i);
        return i;
    }
}
