package net.minecraft.server.dialog.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.functions.StringTemplate;

public class ParsedTemplate {
    public static final Codec<ParsedTemplate> CODEC = Codec.STRING.comapFlatMap(ParsedTemplate::parse, parsedTemplate -> parsedTemplate.raw);
    public static final Codec<String> VARIABLE_CODEC = Codec.STRING
        .validate(
            string -> StringTemplate.isValidVariableName(string) ? DataResult.success(string) : DataResult.error(() -> string + " is not a valid input name")
        );
    private final String raw;
    private final StringTemplate parsed;

    public ParsedTemplate(String raw, StringTemplate parsed) { // Luminol - private -> public
        this.raw = raw;
        this.parsed = parsed;
    }

    private static DataResult<ParsedTemplate> parse(String input) {
        StringTemplate stringTemplate;
        try {
            stringTemplate = StringTemplate.fromString(input);
        } catch (Exception var3) {
            return DataResult.error(() -> "Failed to parse template " + input + ": " + var3.getMessage());
        }

        return DataResult.success(new ParsedTemplate(input, stringTemplate));
    }

    public String instantiate(Map<String, String> variables) {
        List<String> list = this.parsed.variables().stream().map(string -> variables.getOrDefault(string, "")).toList();
        return this.parsed.substitute(list);
    }
}
