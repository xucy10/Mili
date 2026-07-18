package fun.bm.mili.utils.concurrent;

public record TableEntry<X, Y, Z>(X x, Y y, Z z) {
    public X getX() {
        return x;
    }

    public Y getY() {
        return y;
    }

    public Z getZ() {
        return z;
    }
}