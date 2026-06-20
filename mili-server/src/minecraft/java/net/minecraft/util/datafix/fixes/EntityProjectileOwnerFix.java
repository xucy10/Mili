package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Arrays;
import java.util.function.Function;

public class EntityProjectileOwnerFix extends DataFix {
    public EntityProjectileOwnerFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        return this.fixTypeEverywhereTyped("EntityProjectileOwner", inputSchema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> typed) {
        typed = this.updateEntity(typed, "minecraft:egg", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:ender_pearl", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:experience_bottle", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:snowball", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:potion", this::updateOwnerThrowable);
        typed = this.updateEntity(typed, "minecraft:llama_spit", this::updateOwnerLlamaSpit);
        typed = this.updateEntity(typed, "minecraft:arrow", this::updateOwnerArrow);
        typed = this.updateEntity(typed, "minecraft:spectral_arrow", this::updateOwnerArrow);
        return this.updateEntity(typed, "minecraft:trident", this::updateOwnerArrow);
    }

    private Dynamic<?> updateOwnerArrow(Dynamic<?> arrowTag) {
        long _long = arrowTag.get("OwnerUUIDMost").asLong(0L);
        long _long1 = arrowTag.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(arrowTag, _long, _long1).remove("OwnerUUIDMost").remove("OwnerUUIDLeast");
    }

    private Dynamic<?> updateOwnerLlamaSpit(Dynamic<?> llamaSpitTag) {
        OptionalDynamic<?> optionalDynamic = llamaSpitTag.get("Owner");
        long _long = optionalDynamic.get("OwnerUUIDMost").asLong(0L);
        long _long1 = optionalDynamic.get("OwnerUUIDLeast").asLong(0L);
        return this.setUUID(llamaSpitTag, _long, _long1).remove("Owner");
    }

    private Dynamic<?> updateOwnerThrowable(Dynamic<?> throwableTag) {
        String string = "owner";
        OptionalDynamic<?> optionalDynamic = throwableTag.get("owner");
        long _long = optionalDynamic.get("M").asLong(0L);
        long _long1 = optionalDynamic.get("L").asLong(0L);
        return this.setUUID(throwableTag, _long, _long1).remove("owner");
    }

    private Dynamic<?> setUUID(Dynamic<?> dynamic, long uuidMost, long uuidLeast) {
        String string = "OwnerUUID";
        return uuidMost != 0L && uuidLeast != 0L
            ? dynamic.set("OwnerUUID", dynamic.createIntList(Arrays.stream(createUUIDArray(uuidMost, uuidLeast))))
            : dynamic;
    }

    private static int[] createUUIDArray(long uuidMost, long uuidLeast) {
        return new int[]{(int)(uuidMost >> 32), (int)uuidMost, (int)(uuidLeast >> 32), (int)uuidLeast};
    }

    private Typed<?> updateEntity(Typed<?> typed, String choiceName, Function<Dynamic<?>, Dynamic<?>> updater) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.ENTITY, choiceName);
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(References.ENTITY, choiceName);
        return typed.updateTyped(DSL.namedChoice(choiceName, choiceType), choiceType1, typed1 -> typed1.update(DSL.remainderFinder(), updater));
    }
}
