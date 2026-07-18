package fun.bm.mili.chunk;

final class ChunkKey {

    private ChunkKey() {}

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int unpackX(long key) {
        return (int) (key >> 32);
    }

    static int unpackZ(long key) {
        return (int) key;
    }
}