package net.minecraft.nbt.visitors;

import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public interface SkipAll extends StreamTagVisitor {
    SkipAll INSTANCE = new SkipAll() {};

    @Override
    default StreamTagVisitor.ValueResult visitEnd() {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(String entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(byte entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(short entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(int entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(long entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(float entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(double entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(byte[] entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(int[] entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(long[] entry) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visitList(TagType<?> type, int size) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.EntryResult visitElement(TagType<?> type, int size) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.ValueResult visitContainerEnd() {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }
}
