package net.minecraft.world.level.block.state.predicate;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class BlockStatePredicate implements Predicate<BlockState> {
    public static final Predicate<BlockState> ANY = state -> true;
    private final StateDefinition<Block, BlockState> definition;
    private final Map<Property<?>, Predicate<Object>> properties = Maps.newHashMap();

    private BlockStatePredicate(StateDefinition<Block, BlockState> definition) {
        this.definition = definition;
    }

    public static BlockStatePredicate forBlock(Block block) {
        return new BlockStatePredicate(block.getStateDefinition());
    }

    @Override
    public boolean test(@Nullable BlockState state) {
        if (state != null && state.getBlock().equals(this.definition.getOwner())) {
            if (this.properties.isEmpty()) {
                return true;
            } else {
                for (Entry<Property<?>, Predicate<Object>> entry : this.properties.entrySet()) {
                    if (!this.applies(state, entry.getKey(), entry.getValue())) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    protected <T extends Comparable<T>> boolean applies(BlockState state, Property<T> property, Predicate<Object> valuePredicate) {
        T value = state.getValue(property);
        return valuePredicate.test(value);
    }

    public <V extends Comparable<V>> BlockStatePredicate where(Property<V> property, Predicate<Object> valuePredicate) {
        if (!this.definition.getProperties().contains(property)) {
            throw new IllegalArgumentException(this.definition + " cannot support property " + property);
        } else {
            this.properties.put(property, valuePredicate);
            return this;
        }
    }
}
