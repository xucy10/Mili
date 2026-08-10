package net.minecraft.resources;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.IdentifierException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

public final class Identifier implements Comparable<Identifier> {
    public static final Codec<Identifier> CODEC = Codec.STRING.<Identifier>comapFlatMap(Identifier::read, Identifier::toString).stable();
    public static final StreamCodec<ByteBuf, Identifier> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Identifier::parse, Identifier::toString);
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    public static final String PAPER_NAMESPACE = "paper"; // Paper
    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        assert isValidNamespace(namespace);

        assert isValidPath(path);

        // Paper start - Validate Identifier
        // Check for the max network string length (capped at Short.MAX_VALUE) as well as the max bytes of a StringTag (length written as an unsigned short)
        final String resourceLocation = namespace + ":" + path;
        if (resourceLocation.length() > Short.MAX_VALUE || io.netty.buffer.ByteBufUtil.utf8MaxBytes(resourceLocation) > 2 * Short.MAX_VALUE + 1) {
            throw new IdentifierException("Resource location too long: " + resourceLocation);
        }
        // Paper end - Validate Identifier
        this.namespace = namespace;
        this.path = path;
    }

    private static Identifier createUntrusted(String namespace, String path) {
        return new Identifier(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return createUntrusted(namespace, path);
    }

    public static Identifier parse(String location) {
        return bySeparator(location, ':');
    }

    public static Identifier withDefaultNamespace(String location) {
        return new Identifier("minecraft", assertValidPath("minecraft", location));
    }

    public static @Nullable Identifier tryParse(String location) {
        return tryBySeparator(location, ':');
    }

    public static @Nullable Identifier tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new Identifier(namespace, path) : null;
    }

    public static Identifier bySeparator(String location, char separator) {
        int index = location.indexOf(separator);
        if (index >= 0) {
            String sub = location.substring(index + 1);
            if (index != 0) {
                String sub1 = location.substring(0, index);
                return createUntrusted(sub1, sub);
            } else {
                return withDefaultNamespace(sub);
            }
        } else {
            return withDefaultNamespace(location);
        }
    }

    public static @Nullable Identifier tryBySeparator(String location, char separator) {
        int index = location.indexOf(separator);
        if (index >= 0) {
            String sub = location.substring(index + 1);
            if (!isValidPath(sub)) {
                return null;
            } else if (index != 0) {
                String sub1 = location.substring(0, index);
                return isValidNamespace(sub1) ? new Identifier(sub1, sub) : null;
            } else {
                return new Identifier("minecraft", sub);
            }
        } else {
            return isValidPath(location) ? new Identifier("minecraft", location) : null;
        }
    }

    public static DataResult<Identifier> read(String location) {
        try {
            return DataResult.success(parse(location));
        } catch (IdentifierException var2) {
            return DataResult.error(() -> "Not a valid resource location: " + location + " " + var2.getMessage());
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public Identifier withPath(String path) {
        return new Identifier(this.namespace, assertValidPath(this.namespace, path));
    }

    public Identifier withPath(UnaryOperator<String> pathOperator) {
        return this.withPath(pathOperator.apply(this.path));
    }

    public Identifier withPrefix(String pathPrefix) {
        return this.withPath(pathPrefix + this.path);
    }

    public Identifier withSuffix(String pathSuffix) {
        return this.withPath(this.path + pathSuffix);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Identifier identifier && this.namespace.equals(identifier.namespace) && this.path.equals(identifier.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    @Override
    public int compareTo(Identifier other) {
        int i = this.path.compareTo(other.path);
        if (i == 0) {
            i = this.namespace.compareTo(other.namespace);
        }

        return i;
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toShortString() {
        return this.namespace.equals("minecraft") ? this.path : this.toString();
    }

    public String toLanguageKey(String type) {
        return type + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String type, String key) {
        return type + "." + this.toLanguageKey() + "." + key;
    }

    private static String readGreedy(StringReader reader) {
        int cursor = reader.getCursor();

        while (reader.canRead() && isAllowedInIdentifier(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(cursor, reader.getCursor());
    }

    public static Identifier read(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String greedy = readGreedy(reader);

        try {
            return parse(greedy);
        } catch (IdentifierException var4) {
            reader.setCursor(cursor);
            throw ERROR_INVALID.createWithContext(reader);
        }
    }

    public static Identifier readNonEmpty(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String greedy = readGreedy(reader);
        if (greedy.isEmpty()) {
            throw ERROR_INVALID.createWithContext(reader);
        } else {
            try {
                return parse(greedy);
            } catch (IdentifierException var4) {
                reader.setCursor(cursor);
                throw ERROR_INVALID.createWithContext(reader);
            }
        }
    }

    public static boolean isAllowedInIdentifier(char character) {
        return character >= '0' && character <= '9'
            || character >= 'a' && character <= 'z'
            || character == '_'
            || character == ':'
            || character == '/'
            || character == '.'
            || character == '-';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new IdentifierException("Non [a-z0-9_.-] character in namespace of location: " + org.apache.commons.lang3.StringUtils.normalizeSpace(namespace) + ":" + org.apache.commons.lang3.StringUtils.normalizeSpace(path)); // Paper - Sanitize Identifier error logging
        } else {
            return namespace;
        }
    }

    public static boolean validPathChar(char pathChar) {
        return pathChar == '_'
            || pathChar == '-'
            || pathChar >= 'a' && pathChar <= 'z'
            || pathChar >= '0' && pathChar <= '9'
            || pathChar == '/'
            || pathChar == '.';
    }

    private static boolean validNamespaceChar(char namespaceChar) {
        return namespaceChar == '_'
            || namespaceChar == '-'
            || namespaceChar >= 'a' && namespaceChar <= 'z'
            || namespaceChar >= '0' && namespaceChar <= '9'
            || namespaceChar == '.';
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new IdentifierException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + org.apache.commons.lang3.StringUtils.normalizeSpace(path)); // Paper - Sanitize Identifier error logging
        } else {
            return path;
        }
    }
}
