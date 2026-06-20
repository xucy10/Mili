package net.minecraft.util.datafix.schemas;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.types.templates.Hook.HookFunction;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;

public class V1451_6 extends NamespacedSchema {
    public static final String SPECIAL_OBJECTIVE_MARKER = "_special";
    protected static final HookFunction UNPACK_OBJECTIVE_ID = new HookFunction() {
        @Override
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic<>(ops, value);
            return DataFixUtils.orElse(
                    dynamic.get("CriteriaName")
                        .asString()
                        .result()
                        .map(string -> {
                            int index = string.indexOf(58);
                            if (index < 0) {
                                return Pair.of("_special", string);
                            } else {
                                try {
                                    Identifier identifier = Identifier.bySeparator(string.substring(0, index), '.');
                                    Identifier identifier1 = Identifier.bySeparator(string.substring(index + 1), '.');
                                    return Pair.of(identifier.toString(), identifier1.toString());
                                } catch (Exception var4) {
                                    return Pair.of("_special", string);
                                }
                            }
                        })
                        .map(
                            pair -> dynamic.set(
                                "CriteriaType",
                                dynamic.createMap(
                                    ImmutableMap.of(
                                        dynamic.createString("type"),
                                        dynamic.createString(pair.getFirst()),
                                        dynamic.createString("id"),
                                        dynamic.createString(pair.getSecond())
                                    )
                                )
                            )
                        ),
                    dynamic
                )
                .getValue();
        }
    };
    protected static final HookFunction REPACK_OBJECTIVE_ID = new HookFunction() {
        @Override
        public <T> T apply(DynamicOps<T> ops, T value) {
            Dynamic<T> dynamic = new Dynamic<>(ops, value);
            Optional<Dynamic<T>> optional = dynamic.get("CriteriaType")
                .get()
                .result()
                .flatMap(
                    dynamic1 -> {
                        Optional<String> optional1 = dynamic1.get("type").asString().result();
                        Optional<String> optional2 = dynamic1.get("id").asString().result();
                        if (optional1.isPresent() && optional2.isPresent()) {
                            String string = optional1.get();
                            return string.equals("_special")
                                ? Optional.of(dynamic.createString(optional2.get()))
                                : Optional.of(
                                    dynamic1.createString(V1451_6.packNamespacedWithDot(string) + ":" + V1451_6.packNamespacedWithDot(optional2.get()))
                                );
                        } else {
                            return Optional.empty();
                        }
                    }
                );
            return DataFixUtils.orElse(optional.map(dynamic1 -> dynamic.set("CriteriaName", (Dynamic<?>)dynamic1).remove("CriteriaType")), dynamic).getValue();
        }
    };

    public V1451_6(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    @Override
    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        Supplier<TypeTemplate> supplier = () -> DSL.compoundList(References.ITEM_NAME.in(schema), DSL.constType(DSL.intType()));
        schema.registerType(
            false,
            References.STATS,
            () -> DSL.optionalFields(
                "stats",
                DSL.optionalFields(
                    Pair.of("minecraft:mined", DSL.compoundList(References.BLOCK_NAME.in(schema), DSL.constType(DSL.intType()))),
                    Pair.of("minecraft:crafted", supplier.get()),
                    Pair.of("minecraft:used", supplier.get()),
                    Pair.of("minecraft:broken", supplier.get()),
                    Pair.of("minecraft:picked_up", supplier.get()),
                    Pair.of("minecraft:dropped", supplier.get()),
                    Pair.of("minecraft:killed", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))),
                    Pair.of("minecraft:killed_by", DSL.compoundList(References.ENTITY_NAME.in(schema), DSL.constType(DSL.intType()))),
                    Pair.of("minecraft:custom", DSL.compoundList(DSL.constType(namespacedString()), DSL.constType(DSL.intType())))
                )
            )
        );
        Map<String, Supplier<TypeTemplate>> map = createCriterionTypes(schema);
        schema.registerType(
            false,
            References.OBJECTIVE,
            () -> DSL.hook(
                DSL.optionalFields("CriteriaType", DSL.taggedChoiceLazy("type", DSL.string(), map), "DisplayName", References.TEXT_COMPONENT.in(schema)),
                UNPACK_OBJECTIVE_ID,
                REPACK_OBJECTIVE_ID
            )
        );
    }

    protected static Map<String, Supplier<TypeTemplate>> createCriterionTypes(Schema schema) {
        Supplier<TypeTemplate> supplier = () -> DSL.optionalFields("id", References.ITEM_NAME.in(schema));
        Supplier<TypeTemplate> supplier1 = () -> DSL.optionalFields("id", References.BLOCK_NAME.in(schema));
        Supplier<TypeTemplate> supplier2 = () -> DSL.optionalFields("id", References.ENTITY_NAME.in(schema));
        Map<String, Supplier<TypeTemplate>> map = Maps.newHashMap();
        map.put("minecraft:mined", supplier1);
        map.put("minecraft:crafted", supplier);
        map.put("minecraft:used", supplier);
        map.put("minecraft:broken", supplier);
        map.put("minecraft:picked_up", supplier);
        map.put("minecraft:dropped", supplier);
        map.put("minecraft:killed", supplier2);
        map.put("minecraft:killed_by", supplier2);
        map.put("minecraft:custom", () -> DSL.optionalFields("id", DSL.constType(namespacedString())));
        map.put("_special", () -> DSL.optionalFields("id", DSL.constType(DSL.string())));
        return map;
    }

    public static String packNamespacedWithDot(String namespace) {
        Identifier identifier = Identifier.tryParse(namespace);
        return identifier != null ? identifier.getNamespace() + "." + identifier.getPath() : namespace;
    }
}
