package net.minecraft;

import org.apache.commons.lang3.StringEscapeUtils;

public class IdentifierException extends RuntimeException {
    public IdentifierException(String message) {
        super(StringEscapeUtils.escapeJava(message));
    }

    public IdentifierException(String message, Throwable cause) {
        super(StringEscapeUtils.escapeJava(message), cause);
    }
}
