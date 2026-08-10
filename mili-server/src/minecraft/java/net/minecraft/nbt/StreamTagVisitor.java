package net.minecraft.nbt;

public interface StreamTagVisitor {
    StreamTagVisitor.ValueResult visitEnd();

    StreamTagVisitor.ValueResult visit(String entry);

    StreamTagVisitor.ValueResult visit(byte entry);

    StreamTagVisitor.ValueResult visit(short entry);

    StreamTagVisitor.ValueResult visit(int entry);

    StreamTagVisitor.ValueResult visit(long entry);

    StreamTagVisitor.ValueResult visit(float entry);

    StreamTagVisitor.ValueResult visit(double entry);

    StreamTagVisitor.ValueResult visit(byte[] entry);

    StreamTagVisitor.ValueResult visit(int[] entry);

    StreamTagVisitor.ValueResult visit(long[] entry);

    StreamTagVisitor.ValueResult visitList(TagType<?> type, int size);

    StreamTagVisitor.EntryResult visitEntry(TagType<?> type);

    StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id);

    StreamTagVisitor.EntryResult visitElement(TagType<?> type, int size);

    StreamTagVisitor.ValueResult visitContainerEnd();

    StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type);

    public static enum EntryResult {
        ENTER,
        SKIP,
        BREAK,
        HALT;
    }

    public static enum ValueResult {
        CONTINUE,
        BREAK,
        HALT;
    }
}
