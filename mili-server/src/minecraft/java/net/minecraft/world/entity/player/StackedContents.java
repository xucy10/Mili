package net.minecraft.world.entity.player;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class StackedContents<T> {
    public final it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<T> amounts = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<>(); // Paper - Improve exact choice recipe ingredients (don't use "reference" map)

    boolean hasAtLeast(T item, int amount) {
        return this.amounts.getInt(item) >= amount;
    }

    void take(T item, int amount) {
        int i = this.amounts.addTo(item, -amount);
        if (i < amount) {
            throw new IllegalStateException("Took " + amount + " items, but only had " + i);
        }
    }

    void put(T item, int amount) {
        this.amounts.addTo(item, amount);
    }

    public boolean tryPick(List<? extends StackedContents.IngredientInfo<T>> ingredients, int amount, StackedContents.@Nullable Output<T> output) {
        return new StackedContents.RecipePicker(ingredients).tryPick(amount, output);
    }

    public int tryPickAll(List<? extends StackedContents.IngredientInfo<T>> ingredients, int amount, StackedContents.@Nullable Output<T> output) {
        return new StackedContents.RecipePicker(ingredients).tryPickAll(amount, output);
    }

    public void clear() {
        this.amounts.clear();
    }

    public void account(T item, int amount) {
        this.put(item, amount);
    }

    List<T> getUniqueAvailableIngredientItems(Iterable<? extends StackedContents.IngredientInfo<T>> ingredients) {
        List<T> list = new ArrayList<>();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<T> entry : it.unimi.dsi.fastutil.objects.Object2IntMaps.fastIterable(this.amounts)) { // Paper - Improve exact choice recipe ingredients (don't use "reference" map)
            if (entry.getIntValue() > 0 && anyIngredientMatches(ingredients, entry.getKey())) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    private static <T> boolean anyIngredientMatches(Iterable<? extends StackedContents.IngredientInfo<T>> ingredients, T item) {
        for (StackedContents.IngredientInfo<T> ingredientInfo : ingredients) {
            if (ingredientInfo.acceptsItem(item)) {
                return true;
            }
        }

        return false;
    }

    @VisibleForTesting
    public int getResultUpperBound(List<? extends StackedContents.IngredientInfo<T>> ingredients) {
        int i = Integer.MAX_VALUE;
        ObjectIterable<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<T>> objectIterable = it.unimi.dsi.fastutil.objects.Object2IntMaps.fastIterable(this.amounts); // Paper - Improve exact choice recipe ingredients (don't use "reference" map)

        label31:
        for (StackedContents.IngredientInfo<T> ingredientInfo : ingredients) {
            int i1 = 0;

            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<T> entry : objectIterable) { // Paper - Improve exact choice recipe ingredients (don't use "reference" map)
                int intValue = entry.getIntValue();
                if (intValue > i1) {
                    if (ingredientInfo.acceptsItem(entry.getKey())) {
                        i1 = intValue;
                    }

                    if (i1 >= i) {
                        continue label31;
                    }
                }
            }

            i = i1;
            if (i1 == 0) {
                break;
            }
        }

        return i;
    }

    @FunctionalInterface
    public interface IngredientInfo<T> {
        boolean acceptsItem(T item);
    }

    @FunctionalInterface
    public interface Output<T> {
        void accept(T item);
    }

    class RecipePicker {
        private final List<? extends StackedContents.IngredientInfo<T>> ingredients;
        private final int ingredientCount;
        private final List<T> items;
        private final int itemCount;
        private final BitSet data;
        private final IntList path = new IntArrayList();

        public RecipePicker(final List<? extends StackedContents.IngredientInfo<T>> ingredients) {
            this.ingredients = ingredients;
            this.ingredientCount = ingredients.size();
            this.items = StackedContents.this.getUniqueAvailableIngredientItems(ingredients);
            this.itemCount = this.items.size();
            this.data = new BitSet(
                this.visitedIngredientCount() + this.visitedItemCount() + this.satisfiedCount() + this.connectionCount() + this.residualCount()
            );
            this.setInitialConnections();
        }

        private void setInitialConnections() {
            for (int i = 0; i < this.ingredientCount; i++) {
                StackedContents.IngredientInfo<T> ingredientInfo = (StackedContents.IngredientInfo<T>)this.ingredients.get(i);

                for (int i1 = 0; i1 < this.itemCount; i1++) {
                    if (ingredientInfo.acceptsItem(this.items.get(i1))) {
                        this.setConnection(i1, i);
                    }
                }
            }
        }

        public boolean tryPick(int amount, StackedContents.@Nullable Output<T> output) {
            if (amount <= 0) {
                return true;
            } else {
                int i = 0;

                while (true) {
                    IntList list = this.tryAssigningNewItem(amount);
                    if (list == null) {
                        boolean flag = i == this.ingredientCount;
                        boolean flag1 = flag && output != null;
                        this.clearAllVisited();
                        this.clearSatisfied();

                        for (int i1 = 0; i1 < this.ingredientCount; i1++) {
                            for (int i2 = 0; i2 < this.itemCount; i2++) {
                                if (this.isAssigned(i2, i1)) {
                                    this.unassign(i2, i1);
                                    StackedContents.this.put(this.items.get(i2), amount);
                                    if (flag1) {
                                        output.accept(this.items.get(i2));
                                    }
                                    break;
                                }
                            }
                        }

                        assert this.data.get(this.residualOffset(), this.residualOffset() + this.residualCount()).isEmpty();

                        return flag;
                    }

                    int _int = list.getInt(0);
                    StackedContents.this.take(this.items.get(_int), amount);
                    int i1 = list.size() - 1;
                    this.setSatisfied(list.getInt(i1));
                    i++;

                    for (int i2x = 0; i2x < list.size() - 1; i2x++) {
                        if (isPathIndexItem(i2x)) {
                            int _int1 = list.getInt(i2x);
                            int _int2 = list.getInt(i2x + 1);
                            this.assign(_int1, _int2);
                        } else {
                            int _int1 = list.getInt(i2x + 1);
                            int _int2 = list.getInt(i2x);
                            this.unassign(_int1, _int2);
                        }
                    }
                }
            }
        }

        private static boolean isPathIndexItem(int index) {
            return (index & 1) == 0;
        }

        private @Nullable IntList tryAssigningNewItem(int amount) {
            this.clearAllVisited();

            for (int i = 0; i < this.itemCount; i++) {
                if (StackedContents.this.hasAtLeast(this.items.get(i), amount)) {
                    IntList list = this.findNewItemAssignmentPath(i);
                    if (list != null) {
                        return list;
                    }
                }
            }

            return null;
        }

        private @Nullable IntList findNewItemAssignmentPath(int amount) {
            this.path.clear();
            this.visitItem(amount);
            this.path.add(amount);

            while (!this.path.isEmpty()) {
                int size = this.path.size();
                if (isPathIndexItem(size - 1)) {
                    int _int = this.path.getInt(size - 1);

                    for (int i = 0; i < this.ingredientCount; i++) {
                        if (!this.hasVisitedIngredient(i) && this.hasConnection(_int, i) && !this.isAssigned(_int, i)) {
                            this.visitIngredient(i);
                            this.path.add(i);
                            break;
                        }
                    }
                } else {
                    int _int = this.path.getInt(size - 1);
                    if (!this.isSatisfied(_int)) {
                        return this.path;
                    }

                    for (int ix = 0; ix < this.itemCount; ix++) {
                        if (!this.hasVisitedItem(ix) && this.isAssigned(ix, _int)) {
                            assert this.hasConnection(ix, _int);

                            this.visitItem(ix);
                            this.path.add(ix);
                            break;
                        }
                    }
                }

                int _int = this.path.size();
                if (_int == size) {
                    this.path.removeInt(_int - 1);
                }
            }

            return null;
        }

        private int visitedIngredientOffset() {
            return 0;
        }

        private int visitedIngredientCount() {
            return this.ingredientCount;
        }

        private int visitedItemOffset() {
            return this.visitedIngredientOffset() + this.visitedIngredientCount();
        }

        private int visitedItemCount() {
            return this.itemCount;
        }

        private int satisfiedOffset() {
            return this.visitedItemOffset() + this.visitedItemCount();
        }

        private int satisfiedCount() {
            return this.ingredientCount;
        }

        private int connectionOffset() {
            return this.satisfiedOffset() + this.satisfiedCount();
        }

        private int connectionCount() {
            return this.ingredientCount * this.itemCount;
        }

        private int residualOffset() {
            return this.connectionOffset() + this.connectionCount();
        }

        private int residualCount() {
            return this.ingredientCount * this.itemCount;
        }

        private boolean isSatisfied(int stackingIndex) {
            return this.data.get(this.getSatisfiedIndex(stackingIndex));
        }

        private void setSatisfied(int stackingIndex) {
            this.data.set(this.getSatisfiedIndex(stackingIndex));
        }

        private int getSatisfiedIndex(int stackingIndex) {
            assert stackingIndex >= 0 && stackingIndex < this.ingredientCount;

            return this.satisfiedOffset() + stackingIndex;
        }

        private void clearSatisfied() {
            this.clearRange(this.satisfiedOffset(), this.satisfiedCount());
        }

        private void setConnection(int itemIndex, int ingredientIndex) {
            this.data.set(this.getConnectionIndex(itemIndex, ingredientIndex));
        }

        private boolean hasConnection(int itemIndex, int ingredientIndex) {
            return this.data.get(this.getConnectionIndex(itemIndex, ingredientIndex));
        }

        private int getConnectionIndex(int itemIndex, int ingredientIndex) {
            assert itemIndex >= 0 && itemIndex < this.itemCount;

            assert ingredientIndex >= 0 && ingredientIndex < this.ingredientCount;

            return this.connectionOffset() + itemIndex * this.ingredientCount + ingredientIndex;
        }

        private boolean isAssigned(int itemIndex, int ingredientIndex) {
            return this.data.get(this.getResidualIndex(itemIndex, ingredientIndex));
        }

        private void assign(int itemIndex, int ingredientIndex) {
            int residualIndex = this.getResidualIndex(itemIndex, ingredientIndex);

            assert !this.data.get(residualIndex);

            this.data.set(residualIndex);
        }

        private void unassign(int itemIndex, int ingredientIndex) {
            int residualIndex = this.getResidualIndex(itemIndex, ingredientIndex);

            assert this.data.get(residualIndex);

            this.data.clear(residualIndex);
        }

        private int getResidualIndex(int itemIndex, int ingredientIndex) {
            assert itemIndex >= 0 && itemIndex < this.itemCount;

            assert ingredientIndex >= 0 && ingredientIndex < this.ingredientCount;

            return this.residualOffset() + itemIndex * this.ingredientCount + ingredientIndex;
        }

        private void visitIngredient(int ingredientIndex) {
            this.data.set(this.getVisitedIngredientIndex(ingredientIndex));
        }

        private boolean hasVisitedIngredient(int ingredientIndex) {
            return this.data.get(this.getVisitedIngredientIndex(ingredientIndex));
        }

        private int getVisitedIngredientIndex(int ingredientIndex) {
            assert ingredientIndex >= 0 && ingredientIndex < this.ingredientCount;

            return this.visitedIngredientOffset() + ingredientIndex;
        }

        private void visitItem(int itemIndex) {
            this.data.set(this.getVisitiedItemIndex(itemIndex));
        }

        private boolean hasVisitedItem(int itemIndex) {
            return this.data.get(this.getVisitiedItemIndex(itemIndex));
        }

        private int getVisitiedItemIndex(int itemIndex) {
            assert itemIndex >= 0 && itemIndex < this.itemCount;

            return this.visitedItemOffset() + itemIndex;
        }

        private void clearAllVisited() {
            this.clearRange(this.visitedIngredientOffset(), this.visitedIngredientCount());
            this.clearRange(this.visitedItemOffset(), this.visitedItemCount());
        }

        private void clearRange(int offset, int count) {
            this.data.clear(offset, offset + count);
        }

        public int tryPickAll(int amount, StackedContents.@Nullable Output<T> output) {
            int i = 0;
            int i1 = Math.min(amount, StackedContents.this.getResultUpperBound(this.ingredients)) + 1;

            while (true) {
                int i2 = (i + i1) / 2;
                if (this.tryPick(i2, null)) {
                    if (i1 - i <= 1) {
                        if (i2 > 0) {
                            this.tryPick(i2, output);
                        }

                        return i2;
                    }

                    i = i2;
                } else {
                    i1 = i2;
                }
            }
        }
    }
}
