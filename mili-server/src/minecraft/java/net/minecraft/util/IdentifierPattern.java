package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

public class IdentifierPattern {
    public static final Codec<IdentifierPattern> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.PATTERN.optionalFieldOf("namespace").forGetter(identifierPattern -> identifierPattern.namespacePattern),
                ExtraCodecs.PATTERN.optionalFieldOf("path").forGetter(identifierPattern -> identifierPattern.pathPattern)
            )
            .apply(instance, IdentifierPattern::new)
    );
    private final Optional<Pattern> namespacePattern;
    private final Predicate<String> namespacePredicate;
    private final Optional<Pattern> pathPattern;
    private final Predicate<String> pathPredicate;
    private final Predicate<Identifier> locationPredicate;

    private IdentifierPattern(Optional<Pattern> namespacePattern, Optional<Pattern> pathPattern) {
        this.namespacePattern = namespacePattern;
        this.namespacePredicate = namespacePattern.map(Pattern::asPredicate).orElse(namespace -> true);
        this.pathPattern = pathPattern;
        this.pathPredicate = pathPattern.map(Pattern::asPredicate).orElse(path -> true);
        this.locationPredicate = location -> this.namespacePredicate.test(location.getNamespace()) && this.pathPredicate.test(location.getPath());
    }

    public Predicate<String> namespacePredicate() {
        return this.namespacePredicate;
    }

    public Predicate<String> pathPredicate() {
        return this.pathPredicate;
    }

    public Predicate<Identifier> locationPredicate() {
        return this.locationPredicate;
    }
}
