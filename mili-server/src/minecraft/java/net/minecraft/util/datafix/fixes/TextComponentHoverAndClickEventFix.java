package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import org.jspecify.annotations.Nullable;

public class TextComponentHoverAndClickEventFix extends DataFix {
    public TextComponentHoverAndClickEventFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<? extends Pair<String, ?>> type = (Type<? extends Pair<String, ?>>)this.getInputSchema()
            .getType(References.TEXT_COMPONENT)
            .findFieldType("hoverEvent");
        return this.createFixer(this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT), this.getOutputSchema().getType(References.TEXT_COMPONENT), type);
    }

    private <C1, C2, H extends Pair<String, ?>> TypeRewriteRule createFixer(Type<C1> inputComponentType, Type<C2> outputComponentType, Type<H> hoverEventType) {
        Type<Pair<String, Either<Either<String, List<C1>>, Pair<Either<List<C1>, Unit>, Pair<Either<C1, Unit>, Pair<Either<H, Unit>, Dynamic<?>>>>>>> type = DSL.named(
            References.TEXT_COMPONENT.typeName(),
            DSL.or(
                DSL.or(DSL.string(), DSL.list(inputComponentType)),
                DSL.and(
                    DSL.optional(DSL.field("extra", DSL.list(inputComponentType))),
                    DSL.optional(DSL.field("separator", inputComponentType)),
                    DSL.optional(DSL.field("hoverEvent", hoverEventType)),
                    DSL.remainderType()
                )
            )
        );
        if (!type.equals(this.getInputSchema().getType(References.TEXT_COMPONENT))) {
            throw new IllegalStateException(
                "Text component type did not match, expected " + type + " but got " + this.getInputSchema().getType(References.TEXT_COMPONENT)
            );
        } else {
            Type<?> type1 = ExtraDataFixUtils.patchSubType(type, type, outputComponentType);
            return this.fixTypeEverywhere(
                "TextComponentHoverAndClickEventFix",
                type,
                outputComponentType,
                dynamicOps -> pair -> {
                    boolean flag = pair.getSecond().map(either -> false, pair1 -> {
                        Pair<Either<H, Unit>, Dynamic<?>> pair2 = pair1.getSecond().getSecond();
                        boolean isPresent = pair2.getFirst().left().isPresent();
                        boolean isPresent1 = pair2.getSecond().get("clickEvent").result().isPresent();
                        return isPresent || isPresent1;
                    });
                    return (C2)(!flag
                        ? pair
                        : Util.writeAndReadTypedOrThrow(
                                ExtraDataFixUtils.cast(type1, pair, dynamicOps), outputComponentType, TextComponentHoverAndClickEventFix::fixTextComponent
                            )
                            .getValue());
                }
            );
        }
    }

    private static Dynamic<?> fixTextComponent(Dynamic<?> data) {
        return data.renameAndFixField("hoverEvent", "hover_event", TextComponentHoverAndClickEventFix::fixHoverEvent)
            .renameAndFixField("clickEvent", "click_event", TextComponentHoverAndClickEventFix::fixClickEvent);
    }

    private static Dynamic<?> copyFields(Dynamic<?> newData, Dynamic<?> oldData, String... fields) {
        for (String string : fields) {
            newData = Dynamic.copyField(oldData, string, newData, string);
        }

        return newData;
    }

    private static Dynamic<?> fixHoverEvent(Dynamic<?> data) {
        String string = data.get("action").asString("");

        return switch (string) {
            case "show_text" -> data.renameField("contents", "value");
            case "show_item" -> {
                Dynamic<?> dynamic = data.get("contents").orElseEmptyMap();
                Optional<String> optional = dynamic.asString().result();
                yield optional.isPresent() ? data.renameField("contents", "id") : copyFields(data.remove("contents"), dynamic, "id", "count", "components");
            }
            case "show_entity" -> {
                Dynamic<?> dynamic = data.get("contents").orElseEmptyMap();
                yield copyFields(data.remove("contents"), dynamic, "id", "type", "name").renameField("id", "uuid").renameField("type", "id");
            }
            default -> data;
        };
    }

    private static <T> @Nullable Dynamic<T> fixClickEvent(Dynamic<T> data) {
        String string = data.get("action").asString("");
        String string1 = data.get("value").asString("");

        return switch (string) {
            case "open_url" -> !validateUri(string1) ? null : data.renameField("value", "url");
            case "open_file" -> data.renameField("value", "path");
            case "run_command", "suggest_command" -> !validateChat(string1) ? null : data.renameField("value", "command");
            case "change_page" -> {
                Integer integer = data.get("value").result().map(TextComponentHoverAndClickEventFix::parseOldPage).orElse(null);
                if (integer == null) {
                    yield null;
                } else {
                    int max = Math.max(integer, 1);
                    yield data.remove("value").set("page", data.createInt(max));
                }
            }
            default -> data;
        };
    }

    private static @Nullable Integer parseOldPage(Dynamic<?> data) {
        Optional<Number> optional = data.asNumber().result();
        if (optional.isPresent()) {
            return optional.get().intValue();
        } else {
            try {
                return Integer.parseInt(data.asString(""));
            } catch (Exception var3) {
                return null;
            }
        }
    }

    private static boolean validateUri(String uri) {
        try {
            URI uri1 = new URI(uri);
            String scheme = uri1.getScheme();
            if (scheme == null) {
                return false;
            } else {
                String string = scheme.toLowerCase(Locale.ROOT);
                return "http".equals(string) || "https".equals(string);
            }
        } catch (URISyntaxException var4) {
            return false;
        }
    }

    private static boolean validateChat(String chat) {
        for (int i = 0; i < chat.length(); i++) {
            char c = chat.charAt(i);
            if (c == 167 || c < ' ' || c == 127) {
                return false;
            }
        }

        return true;
    }
}
