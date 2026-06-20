package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BlockPosFormatAndRenamesFix extends DataFix {
    private static final List<String> PATROLLING_MOBS = List.of(
        "minecraft:witch", "minecraft:ravager", "minecraft:pillager", "minecraft:illusioner", "minecraft:evoker", "minecraft:vindicator"
    );

    public BlockPosFormatAndRenamesFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private Typed<?> fixFields(Typed<?> data, Map<String, String> renames) {
        return data.update(DSL.remainderFinder(), dynamic -> {
            for (Entry<String, String> entry : renames.entrySet()) {
                dynamic = dynamic.renameAndFixField(entry.getKey(), entry.getValue(), ExtraDataFixUtils::fixBlockPos);
            }

            return dynamic;
        });
    }

    private <T> Dynamic<T> fixMapSavedData(Dynamic<T> data) {
        return data.update("frames", dynamic -> dynamic.createList(dynamic.asStream().map(dynamic1 -> {
            dynamic1 = dynamic1.renameAndFixField("Pos", "pos", ExtraDataFixUtils::fixBlockPos);
            dynamic1 = dynamic1.renameField("Rotation", "rotation");
            return dynamic1.renameField("EntityId", "entity_id");
        }))).update("banners", dynamic -> dynamic.createList(dynamic.asStream().map(dynamic1 -> {
            dynamic1 = dynamic1.renameField("Pos", "pos");
            dynamic1 = dynamic1.renameField("Color", "color");
            return dynamic1.renameField("Name", "name");
        })));
    }

    @Override
    public TypeRewriteRule makeRule() {
        List<TypeRewriteRule> list = new ArrayList<>();
        this.addEntityRules(list);
        this.addBlockEntityRules(list);
        list.add(
            this.writeFixAndRead(
                "BlockPos format for map frames",
                this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
                this.getOutputSchema().getType(References.SAVED_DATA_MAP_DATA),
                dynamic -> dynamic.update("data", this::fixMapSavedData)
            )
        );
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        list.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format for compass target",
                type,
                ItemStackTagFix.createFixer(
                    type,
                    "minecraft:compass"::equals,
                    typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("LodestonePos", ExtraDataFixUtils::fixBlockPos))
                )
            )
        );
        return TypeRewriteRule.seq(list);
    }

    private void addEntityRules(List<TypeRewriteRule> output) {
        output.add(this.createEntityFixer(References.ENTITY, "minecraft:bee", Map.of("HivePos", "hive_pos", "FlowerPos", "flower_pos")));
        output.add(this.createEntityFixer(References.ENTITY, "minecraft:end_crystal", Map.of("BeamTarget", "beam_target")));
        output.add(this.createEntityFixer(References.ENTITY, "minecraft:wandering_trader", Map.of("WanderTarget", "wander_target")));

        for (String string : PATROLLING_MOBS) {
            output.add(this.createEntityFixer(References.ENTITY, string, Map.of("PatrolTarget", "patrol_target")));
        }

        output.add(
            this.fixTypeEverywhereTyped(
                "BlockPos format in Leash for mobs",
                this.getInputSchema().getType(References.ENTITY),
                typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.renameAndFixField("Leash", "leash", ExtraDataFixUtils::fixBlockPos))
            )
        );
    }

    private void addBlockEntityRules(List<TypeRewriteRule> output) {
        output.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:beehive", Map.of("FlowerPos", "flower_pos")));
        output.add(this.createEntityFixer(References.BLOCK_ENTITY, "minecraft:end_gateway", Map.of("ExitPortal", "exit_portal")));
    }

    private TypeRewriteRule createEntityFixer(TypeReference reference, String entityId, Map<String, String> renames) {
        String string = "BlockPos format in " + renames.keySet() + " for " + entityId + " (" + reference.typeName() + ")";
        OpticFinder<?> opticFinder = DSL.namedChoice(entityId, this.getInputSchema().getChoiceType(reference, entityId));
        return this.fixTypeEverywhereTyped(
            string, this.getInputSchema().getType(reference), typed -> typed.updateTyped(opticFinder, typed1 -> this.fixFields(typed1, renames))
        );
    }
}
