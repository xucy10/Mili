package net.minecraft.world.level.levelgen.structure.pieces;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.jspecify.annotations.Nullable;

public class StructurePiecesBuilder implements StructurePieceAccessor {
    private final List<StructurePiece> pieces = Lists.newArrayList();

    @Override
    public void addPiece(StructurePiece piece) {
        this.pieces.add(piece);
    }

    @Override
    public @Nullable StructurePiece findCollisionPiece(BoundingBox box) {
        return StructurePiece.findCollisionPiece(this.pieces, box);
    }

    @Deprecated
    public void offsetPiecesVertically(int offset) {
        for (StructurePiece structurePiece : this.pieces) {
            structurePiece.move(0, offset, 0);
        }
    }

    @Deprecated
    public int moveBelowSeaLevel(int seaLevel, int minY, RandomSource random, int amount) {
        int i = seaLevel - amount;
        BoundingBox boundingBox = this.getBoundingBox();
        int i1 = boundingBox.getYSpan() + minY + 1;
        if (i1 < i) {
            i1 += random.nextInt(i - i1);
        }

        int i2 = i1 - boundingBox.maxY();
        this.offsetPiecesVertically(i2);
        return i2;
    }

    @Deprecated
    public void moveInsideHeights(RandomSource random, int minY, int maxY) {
        BoundingBox boundingBox = this.getBoundingBox();
        int i = maxY - minY + 1 - boundingBox.getYSpan();
        int i1;
        if (i > 1) {
            i1 = minY + random.nextInt(i);
        } else {
            i1 = minY;
        }

        int i2 = i1 - boundingBox.minY();
        this.offsetPiecesVertically(i2);
    }

    public PiecesContainer build() {
        return new PiecesContainer(this.pieces);
    }

    public void clear() {
        this.pieces.clear();
    }

    public boolean isEmpty() {
        return this.pieces.isEmpty();
    }

    public BoundingBox getBoundingBox() {
        return StructurePiece.createBoundingBox(this.pieces.stream());
    }
}
