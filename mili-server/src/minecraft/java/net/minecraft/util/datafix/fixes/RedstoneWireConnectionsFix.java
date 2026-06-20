package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class RedstoneWireConnectionsFix extends DataFix {
    public RedstoneWireConnectionsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        return this.fixTypeEverywhereTyped(
            "RedstoneConnectionsFix",
            inputSchema.getType(References.BLOCK_STATE),
            typed -> typed.update(DSL.remainderFinder(), this::updateRedstoneConnections)
        );
    }

    private <T> Dynamic<T> updateRedstoneConnections(Dynamic<T> dynamic) {
        boolean isPresent = dynamic.get("Name").asString().result().filter("minecraft:redstone_wire"::equals).isPresent();
        return !isPresent
            ? dynamic
            : dynamic.update(
                "Properties",
                dynamic1 -> {
                    String string = dynamic1.get("east").asString("none");
                    String string1 = dynamic1.get("west").asString("none");
                    String string2 = dynamic1.get("north").asString("none");
                    String string3 = dynamic1.get("south").asString("none");
                    boolean flag = isConnected(string) || isConnected(string1);
                    boolean flag1 = isConnected(string2) || isConnected(string3);
                    String string4 = !isConnected(string) && !flag1 ? "side" : string;
                    String string5 = !isConnected(string1) && !flag1 ? "side" : string1;
                    String string6 = !isConnected(string2) && !flag ? "side" : string2;
                    String string7 = !isConnected(string3) && !flag ? "side" : string3;
                    return dynamic1.update("east", dynamic2 -> dynamic2.createString(string4))
                        .update("west", dynamic2 -> dynamic2.createString(string5))
                        .update("north", dynamic2 -> dynamic2.createString(string6))
                        .update("south", dynamic2 -> dynamic2.createString(string7));
                }
            );
    }

    private static boolean isConnected(String state) {
        return !"none".equals(state);
    }
}
