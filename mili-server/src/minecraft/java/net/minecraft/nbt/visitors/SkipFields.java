package net.minecraft.nbt.visitors;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public class SkipFields extends CollectToTag {
    private final Deque<FieldTree> stack = new ArrayDeque<>();

    public SkipFields(FieldSelector... selectors) {
        FieldTree fieldTree = FieldTree.createRoot();

        for (FieldSelector fieldSelector : selectors) {
            fieldTree.addEntry(fieldSelector);
        }

        this.stack.push(fieldTree);
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        FieldTree fieldTree = this.stack.element();
        if (fieldTree.isSelected(type, id)) {
            return StreamTagVisitor.EntryResult.SKIP;
        } else {
            if (type == CompoundTag.TYPE) {
                FieldTree fieldTree1 = fieldTree.fieldsToRecurse().get(id);
                if (fieldTree1 != null) {
                    this.stack.push(fieldTree1);
                }
            }

            return super.visitEntry(type, id);
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        if (this.depth() == this.stack.element().depth()) {
            this.stack.pop();
        }

        return super.visitContainerEnd();
    }
}
