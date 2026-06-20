package net.minecraft.world.inventory;

public abstract class DataSlot {
    private int prevValue;

    public static DataSlot forContainer(final ContainerData data, final int index) {
        return new DataSlot() {
            @Override
            public int get() {
                return data.get(index);
            }

            @Override
            public void set(int value) {
                data.set(index, value);
            }
        };
    }

    public static DataSlot shared(final int[] data, final int index) {
        return new DataSlot() {
            @Override
            public int get() {
                return data[index];
            }

            @Override
            public void set(int value) {
                data[index] = value;
            }
        };
    }

    public static DataSlot standalone() {
        return new DataSlot() {
            private int value;

            @Override
            public int get() {
                return this.value;
            }

            @Override
            public void set(int value) {
                this.value = value;
            }
        };
    }

    public abstract int get();

    public abstract void set(int value);

    public boolean checkAndClearUpdateFlag() {
        int i = this.get();
        boolean flag = i != this.prevValue;
        this.prevValue = i;
        return flag;
    }
}
