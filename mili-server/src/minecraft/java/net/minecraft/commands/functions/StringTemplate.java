package net.minecraft.commands.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;

public record StringTemplate(List<String> segments, List<String> variables) {
    public static StringTemplate fromString(String input) {
        Builder<String> builder = ImmutableList.builder();
        Builder<String> builder1 = ImmutableList.builder();
        int len = input.length();
        int i = 0;
        int index = input.indexOf(36);

        while (index != -1) {
            if (index != len - 1 && input.charAt(index + 1) == '(') {
                builder.add(input.substring(i, index));
                int index1 = input.indexOf(41, index + 1);
                if (index1 == -1) {
                    throw new IllegalArgumentException("Unterminated macro variable");
                }

                String sub = input.substring(index + 2, index1);
                if (!isValidVariableName(sub)) {
                    throw new IllegalArgumentException("Invalid macro variable name '" + sub + "'");
                }

                builder1.add(sub);
                i = index1 + 1;
                index = input.indexOf(36, i);
            } else {
                index = input.indexOf(36, index + 1);
            }
        }

        if (i == 0) {
            throw new IllegalArgumentException("No variables in macro");
        } else {
            if (i != len) {
                builder.add(input.substring(i));
            }

            return new StringTemplate(builder.build(), builder1.build());
        }
    }

    public static boolean isValidVariableName(String variableName) {
        for (int i = 0; i < variableName.length(); i++) {
            char c = variableName.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }

        return true;
    }

    public String substitute(List<String> arguments) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < this.variables.size(); i++) {
            stringBuilder.append(this.segments.get(i)).append(arguments.get(i));
            CommandFunction.checkCommandLineLength(stringBuilder);
        }

        if (this.segments.size() > this.variables.size()) {
            stringBuilder.append(this.segments.getLast());
        }

        CommandFunction.checkCommandLineLength(stringBuilder);
        return stringBuilder.toString();
    }
}
