package net.minecraft.nbt.visitors;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import org.jspecify.annotations.Nullable;

public class CollectToTag implements StreamTagVisitor {
    private final Deque<CollectToTag.ContainerBuilder> containerStack = new ArrayDeque<>();

    public CollectToTag() {
        this.containerStack.addLast(new CollectToTag.RootBuilder());
    }

    public @Nullable Tag getResult() {
        return this.containerStack.getFirst().build();
    }

    protected int depth() {
        return this.containerStack.size() - 1;
    }

    private void appendEntry(Tag tag) {
        this.containerStack.getLast().acceptValue(tag);
    }

    @Override
    public StreamTagVisitor.ValueResult visitEnd() {
        this.appendEntry(EndTag.INSTANCE);
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(String entry) {
        this.appendEntry(StringTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(byte entry) {
        this.appendEntry(ByteTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(short entry) {
        this.appendEntry(ShortTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(int entry) {
        this.appendEntry(IntTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(long entry) {
        this.appendEntry(LongTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(float entry) {
        this.appendEntry(FloatTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(double entry) {
        this.appendEntry(DoubleTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(byte[] entry) {
        this.appendEntry(new ByteArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(int[] entry) {
        this.appendEntry(new IntArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(long[] entry) {
        this.appendEntry(new LongArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visitList(TagType<?> type, int size) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.EntryResult visitElement(TagType<?> type, int size) {
        this.enterContainerIfNeeded(type);
        return StreamTagVisitor.EntryResult.ENTER;
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        return StreamTagVisitor.EntryResult.ENTER;
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        this.containerStack.getLast().acceptKey(id);
        this.enterContainerIfNeeded(type);
        return StreamTagVisitor.EntryResult.ENTER;
    }

    private void enterContainerIfNeeded(TagType<?> type) {
        if (type == ListTag.TYPE) {
            this.containerStack.addLast(new CollectToTag.ListBuilder());
        } else if (type == CompoundTag.TYPE) {
            this.containerStack.addLast(new CollectToTag.CompoundBuilder());
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        CollectToTag.ContainerBuilder containerBuilder = this.containerStack.removeLast();
        Tag tag = containerBuilder.build();
        if (tag != null) {
            this.containerStack.getLast().acceptValue(tag);
        }

        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        this.enterContainerIfNeeded(type);
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    static class CompoundBuilder implements CollectToTag.ContainerBuilder {
        private final CompoundTag compound = new CompoundTag();
        private String lastId = "";

        @Override
        public void acceptKey(String lastId) {
            this.lastId = lastId;
        }

        @Override
        public void acceptValue(Tag tag) {
            this.compound.put(this.lastId, tag);
        }

        @Override
        public Tag build() {
            return this.compound;
        }
    }

    interface ContainerBuilder {
        default void acceptKey(String lastId) {
        }

        void acceptValue(Tag tag);

        @Nullable Tag build();
    }

    static class ListBuilder implements CollectToTag.ContainerBuilder {
        private final ListTag list = new ListTag();

        @Override
        public void acceptValue(Tag tag) {
            this.list.addAndUnwrap(tag);
        }

        @Override
        public Tag build() {
            return this.list;
        }
    }

    static class RootBuilder implements CollectToTag.ContainerBuilder {
        private @Nullable Tag result;

        @Override
        public void acceptValue(Tag tag) {
            this.result = tag;
        }

        @Override
        public @Nullable Tag build() {
            return this.result;
        }
    }
}
