package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import java.util.List;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class AttributeIdPrefixFix extends AttributesRenameFix {
    private static final List<String> PREFIXES = List.of("generic.", "horse.", "player.", "zombie.");

    public AttributeIdPrefixFix(Schema outputSchema) {
        super(outputSchema, "AttributeIdPrefixFix", AttributeIdPrefixFix::replaceId);
    }

    private static String replaceId(String id) {
        String string = NamespacedSchema.ensureNamespaced(id);

        for (String string1 : PREFIXES) {
            String string2 = NamespacedSchema.ensureNamespaced(string1);
            if (string.startsWith(string2)) {
                return "minecraft:" + string.substring(string2.length());
            }
        }

        return id;
    }
}
