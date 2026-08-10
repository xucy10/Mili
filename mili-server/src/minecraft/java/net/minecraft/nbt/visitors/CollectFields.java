package net.minecraft.nbt.visitors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public class CollectFields extends CollectToTag {
    private int fieldsToGetCount;
    private final Set<TagType<?>> wantedTypes;
    private final Deque<FieldTree> stack = new ArrayDeque<>();

    public CollectFields(FieldSelector... selectors) {
        this.fieldsToGetCount = selectors.length;
        Builder<TagType<?>> builder = ImmutableSet.builder();
        FieldTree fieldTree = FieldTree.createRoot();

        for (FieldSelector fieldSelector : selectors) {
            fieldTree.addEntry(fieldSelector);
            builder.add(fieldSelector.type());
        }

        this.stack.push(fieldTree);
        builder.add(CompoundTag.TYPE);
        this.wantedTypes = builder.build();
    }

    @Override
    public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        return type != CompoundTag.TYPE ? StreamTagVisitor.ValueResult.HALT : super.visitRootEntry(type);
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        FieldTree fieldTree = this.stack.element();
        if (this.depth() > fieldTree.depth()) {
            return super.visitEntry(type);
        } else if (this.fieldsToGetCount <= 0) {
            return StreamTagVisitor.EntryResult.BREAK;
        } else {
            return !this.wantedTypes.contains(type) ? StreamTagVisitor.EntryResult.SKIP : super.visitEntry(type);
        }
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        FieldTree fieldTree = this.stack.element();
        if (this.depth() > fieldTree.depth()) {
            return super.visitEntry(type, id);
        } else if (fieldTree.selectedFields().remove(id, type)) {
            this.fieldsToGetCount--;
            return super.visitEntry(type, id);
        } else {
            if (type == CompoundTag.TYPE) {
                FieldTree fieldTree1 = fieldTree.fieldsToRecurse().get(id);
                if (fieldTree1 != null) {
                    this.stack.push(fieldTree1);
                    return super.visitEntry(type, id);
                }
            }

            return StreamTagVisitor.EntryResult.SKIP;
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        if (this.depth() == this.stack.element().depth()) {
            this.stack.pop();
        }

        return super.visitContainerEnd();
    }

    public int getMissingFieldCount() {
        return this.fieldsToGetCount;
    }
}
