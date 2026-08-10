package net.minecraft.commands;

@FunctionalInterface
public interface CommandResultCallback {
    CommandResultCallback EMPTY = new CommandResultCallback() {
        @Override
        public void onResult(boolean success, int result) {
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    };

    void onResult(boolean success, int result);

    default void onSuccess(int result) {
        this.onResult(true, result);
    }

    default void onFailure() {
        this.onResult(false, 0);
    }

    static CommandResultCallback chain(CommandResultCallback first, CommandResultCallback second) {
        if (first == EMPTY) {
            return second;
        } else {
            return second == EMPTY ? first : (success, result) -> {
                first.onResult(success, result);
                second.onResult(success, result);
            };
        }
    }
}
