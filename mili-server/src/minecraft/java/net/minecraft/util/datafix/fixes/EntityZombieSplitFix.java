package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.function.Supplier;
import net.minecraft.util.Util;

public class EntityZombieSplitFix extends EntityRenameFix {
    private final Supplier<Type<?>> zombieVillagerType = Suppliers.memoize(() -> this.getOutputSchema().getChoiceType(References.ENTITY, "ZombieVillager"));

    public EntityZombieSplitFix(Schema outputSchema) {
        super("EntityZombieSplitFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String entityName, Typed<?> typed) {
        if (!entityName.equals("Zombie")) {
            return Pair.of(entityName, typed);
        } else {
            Dynamic<?> dynamic = typed.getOptional(DSL.remainderFinder()).orElseThrow();
            int _int = dynamic.get("ZombieType").asInt(0);
            String string;
            Typed<?> typed1;
            switch (_int) {
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    string = "ZombieVillager";
                    typed1 = this.changeSchemaToZombieVillager(typed, _int - 1);
                    break;
                case 6:
                    string = "Husk";
                    typed1 = typed;
                    break;
                default:
                    string = "Zombie";
                    typed1 = typed;
            }

            return Pair.of(string, typed1.update(DSL.remainderFinder(), dynamic1 -> dynamic1.remove("ZombieType")));
        }
    }

    private Typed<?> changeSchemaToZombieVillager(Typed<?> typed, int profession) {
        return Util.writeAndReadTypedOrThrow(typed, this.zombieVillagerType.get(), dynamic -> dynamic.set("Profession", dynamic.createInt(profession)));
    }
}
