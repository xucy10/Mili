package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import net.minecraft.util.Util;

public class EntityHorseSplitFix extends EntityRenameFix {
    public EntityHorseSplitFix(Schema outputSchema, boolean changesType) {
        super("EntityHorseSplitFix", outputSchema, changesType);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String entityName, Typed<?> typed) {
        if (Objects.equals("EntityHorse", entityName)) {
            Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
            int _int = dynamic.get("Type").asInt(0);

            String string = switch (_int) {
                case 1 -> "Donkey";
                case 2 -> "Mule";
                case 3 -> "ZombieHorse";
                case 4 -> "SkeletonHorse";
                default -> "Horse";
            };
            Type<?> type = this.getOutputSchema().findChoiceType(References.ENTITY).types().get(string);
            return Pair.of(string, Util.writeAndReadTypedOrThrow(typed, type, dynamic1 -> dynamic1.remove("Type")));
        } else {
            return Pair.of(entityName, typed);
        }
    }
}
