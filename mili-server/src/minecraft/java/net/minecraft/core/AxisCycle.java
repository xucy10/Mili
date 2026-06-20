package net.minecraft.core;

public enum AxisCycle {
    NONE {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) {
            return axis.choose(x, y, z);
        }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) {
            return axis.choose(x, y, z);
        }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) {
            return axis;
        }

        @Override
        public AxisCycle inverse() {
            return this;
        }
    },
    FORWARD {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) {
            return axis.choose(z, x, y);
        }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) {
            return axis.choose(z, x, y);
        }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) {
            return AXIS_VALUES[Math.floorMod(axis.ordinal() + 1, 3)];
        }

        @Override
        public AxisCycle inverse() {
            return BACKWARD;
        }
    },
    BACKWARD {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) {
            return axis.choose(y, z, x);
        }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) {
            return axis.choose(y, z, x);
        }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) {
            return AXIS_VALUES[Math.floorMod(axis.ordinal() - 1, 3)];
        }

        @Override
        public AxisCycle inverse() {
            return FORWARD;
        }
    };

    public static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    public static final AxisCycle[] VALUES = values();

    public abstract int cycle(int x, int y, int z, Direction.Axis axis);

    public abstract double cycle(double x, double y, double z, Direction.Axis axis);

    public abstract Direction.Axis cycle(Direction.Axis axis);

    public abstract AxisCycle inverse();

    public static AxisCycle between(Direction.Axis to, Direction.Axis from) {
        return VALUES[Math.floorMod(from.ordinal() - to.ordinal(), 3)];
    }
}
