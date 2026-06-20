package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public abstract class DataComponentRemainderFix extends DataFix {
    private final String name;
    private final String componentId;
    private final String newComponentId;

    public DataComponentRemainderFix(Schema outputSchema, String name, String componentId) {
        this(outputSchema, name, componentId, componentId);
    }

    public DataComponentRemainderFix(Schema outputSchema, String name, String componentId, String newComponentId) {
        super(outputSchema, false);
        this.name = name;
        this.componentId = componentId;
        this.newComponentId = newComponentId;
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.DATA_COMPONENTS);
        return this.fixTypeEverywhereTyped(this.name, type, typed -> typed.update(DSL.remainderFinder(), dynamic -> {
            Optional<? extends Dynamic<?>> optional = dynamic.get(this.componentId).result();
            if (optional.isEmpty()) {
                return dynamic;
            } else {
                Dynamic<?> dynamic1 = this.fixComponent((Dynamic<?>)optional.get());
                return dynamic.remove(this.componentId).setFieldIfPresent(this.newComponentId, Optional.ofNullable(dynamic1));
            }
        }));
    }

    protected abstract <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> component);
}
