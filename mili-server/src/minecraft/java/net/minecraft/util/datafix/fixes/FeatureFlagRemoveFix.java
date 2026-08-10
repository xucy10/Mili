package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureFlagRemoveFix extends DataFix {
    private final String name;
    private final Set<String> flagsToRemove;

    public FeatureFlagRemoveFix(Schema outputSchema, String name, Set<String> flagsToRemove) {
        super(outputSchema, false);
        this.name = name;
        this.flagsToRemove = flagsToRemove;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.name, this.getInputSchema().getType(References.LIGHTWEIGHT_LEVEL), typed -> typed.update(DSL.remainderFinder(), this::fixTag)
        );
    }

    private <T> Dynamic<T> fixTag(Dynamic<T> tag) {
        List<Dynamic<T>> list = tag.get("removed_features").asStream().collect(Collectors.toCollection(ArrayList::new));
        Dynamic<T> dynamic = tag.update(
            "enabled_features", dynamic1 -> DataFixUtils.orElse(dynamic1.asStreamOpt().result().map(stream -> stream.filter(dynamic2 -> {
                Optional<String> optional = dynamic2.asString().result();
                if (optional.isEmpty()) {
                    return true;
                } else {
                    boolean flag = this.flagsToRemove.contains(optional.get());
                    if (flag) {
                        list.add(tag.createString(optional.get()));
                    }

                    return !flag;
                }
            })).map(tag::createList), dynamic1)
        );
        if (!list.isEmpty()) {
            dynamic = dynamic.set("removed_features", tag.createList(list.stream()));
        }

        return dynamic;
    }
}
