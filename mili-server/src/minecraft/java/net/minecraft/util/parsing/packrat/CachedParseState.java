package net.minecraft.util.parsing.packrat;

import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public abstract class CachedParseState<S> implements ParseState<S> {
    private CachedParseState.@Nullable PositionCache[] positionCache = new CachedParseState.PositionCache[256];
    private final ErrorCollector<S> errorCollector;
    private final Scope scope = new Scope();
    private CachedParseState.@Nullable SimpleControl[] controlCache = new CachedParseState.SimpleControl[16];
    private int nextControlToReturn;
    private final CachedParseState<S>.Silent silent = new CachedParseState.Silent();

    protected CachedParseState(ErrorCollector<S> errorCollector) {
        this.errorCollector = errorCollector;
    }

    @Override
    public Scope scope() {
        return this.scope;
    }

    @Override
    public ErrorCollector<S> errorCollector() {
        return this.errorCollector;
    }

    @Override
    public <T> @Nullable T parse(NamedRule<S, T> rule) {
        int i = this.mark();
        CachedParseState.PositionCache cacheForPosition = this.getCacheForPosition(i);
        int i1 = cacheForPosition.findKeyIndex(rule.name());
        if (i1 != -1) {
            CachedParseState.CacheEntry<T> value = cacheForPosition.getValue(i1);
            if (value != null) {
                if (value == CachedParseState.CacheEntry.NEGATIVE) {
                    return null;
                }

                this.restore(value.markAfterParse);
                return value.value;
            }
        } else {
            i1 = cacheForPosition.allocateNewEntry(rule.name());
        }

        T object = rule.value().parse(this);
        CachedParseState.CacheEntry<T> cacheEntry;
        if (object == null) {
            cacheEntry = (CachedParseState.CacheEntry<T>)CachedParseState.CacheEntry.NEGATIVE;
        } else {
            int i2 = this.mark();
            cacheEntry = new CachedParseState.CacheEntry<>(object, i2);
        }

        cacheForPosition.setValue(i1, cacheEntry);
        return object;
    }

    private CachedParseState.PositionCache getCacheForPosition(int position) {
        int i = this.positionCache.length;
        if (position >= i) {
            int i1 = Util.growByHalf(i, position + 1);
            CachedParseState.PositionCache[] positionCaches = new CachedParseState.PositionCache[i1];
            System.arraycopy(this.positionCache, 0, positionCaches, 0, i);
            this.positionCache = positionCaches;
        }

        CachedParseState.PositionCache positionCache = this.positionCache[position];
        if (positionCache == null) {
            positionCache = new CachedParseState.PositionCache();
            this.positionCache[position] = positionCache;
        }

        return positionCache;
    }

    @Override
    public Control acquireControl() {
        int i = this.controlCache.length;
        if (this.nextControlToReturn >= i) {
            int i1 = Util.growByHalf(i, this.nextControlToReturn + 1);
            CachedParseState.SimpleControl[] simpleControls = new CachedParseState.SimpleControl[i1];
            System.arraycopy(this.controlCache, 0, simpleControls, 0, i);
            this.controlCache = simpleControls;
        }

        int i1 = this.nextControlToReturn++;
        CachedParseState.SimpleControl simpleControl = this.controlCache[i1];
        if (simpleControl == null) {
            simpleControl = new CachedParseState.SimpleControl();
            this.controlCache[i1] = simpleControl;
        } else {
            simpleControl.reset();
        }

        return simpleControl;
    }

    @Override
    public void releaseControl() {
        this.nextControlToReturn--;
    }

    @Override
    public ParseState<S> silent() {
        return this.silent;
    }

    record CacheEntry<T>(@Nullable T value, int markAfterParse) {
        public static final CachedParseState.CacheEntry<?> NEGATIVE = new CachedParseState.CacheEntry(null, -1);

        public static <T> CachedParseState.CacheEntry<T> negativeEntry() {
            return (CachedParseState.CacheEntry<T>)NEGATIVE;
        }
    }

    static class PositionCache {
        public static final int ENTRY_STRIDE = 2;
        private static final int NOT_FOUND = -1;
        private Object[] atomCache = new Object[16];
        private int nextKey;

        public int findKeyIndex(Atom<?> atom) {
            for (int i = 0; i < this.nextKey; i += 2) {
                if (this.atomCache[i] == atom) {
                    return i;
                }
            }

            return -1;
        }

        public int allocateNewEntry(Atom<?> entry) {
            int i = this.nextKey;
            this.nextKey += 2;
            int i1 = i + 1;
            int i2 = this.atomCache.length;
            if (i1 >= i2) {
                int i3 = Util.growByHalf(i2, i1 + 1);
                Object[] objects = new Object[i3];
                System.arraycopy(this.atomCache, 0, objects, 0, i2);
                this.atomCache = objects;
            }

            this.atomCache[i] = entry;
            return i;
        }

        public <T> CachedParseState.@Nullable CacheEntry<T> getValue(int index) {
            return (CachedParseState.CacheEntry<T>)this.atomCache[index + 1];
        }

        public void setValue(int index, CachedParseState.CacheEntry<?> value) {
            this.atomCache[index + 1] = value;
        }
    }

    class Silent implements ParseState<S> {
        private final ErrorCollector<S> silentCollector = new ErrorCollector.Nop<>();

        @Override
        public ErrorCollector<S> errorCollector() {
            return this.silentCollector;
        }

        @Override
        public Scope scope() {
            return CachedParseState.this.scope();
        }

        @Override
        public <T> @Nullable T parse(NamedRule<S, T> rule) {
            return CachedParseState.this.parse(rule);
        }

        @Override
        public S input() {
            return CachedParseState.this.input();
        }

        @Override
        public int mark() {
            return CachedParseState.this.mark();
        }

        @Override
        public void restore(int cursor) {
            CachedParseState.this.restore(cursor);
        }

        @Override
        public Control acquireControl() {
            return CachedParseState.this.acquireControl();
        }

        @Override
        public void releaseControl() {
            CachedParseState.this.releaseControl();
        }

        @Override
        public ParseState<S> silent() {
            return this;
        }
    }

    static class SimpleControl implements Control {
        private boolean hasCut;

        @Override
        public void cut() {
            this.hasCut = true;
        }

        @Override
        public boolean hasCut() {
            return this.hasCut;
        }

        public void reset() {
            this.hasCut = false;
        }
    }
}
