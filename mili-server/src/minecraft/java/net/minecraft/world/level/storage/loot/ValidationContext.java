package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextKeySet;

public class ValidationContext {
    private final ProblemReporter reporter;
    private final ContextKeySet contextKeySet;
    private final Optional<HolderGetter.Provider> resolver;
    private final Set<ResourceKey<?>> visitedElements;

    public ValidationContext(ProblemReporter reporter, ContextKeySet contextKeySet, HolderGetter.Provider resolver) {
        this(reporter, contextKeySet, Optional.of(resolver), Set.of());
    }

    public ValidationContext(ProblemReporter reporter, ContextKeySet contextKeySet) {
        this(reporter, contextKeySet, Optional.empty(), Set.of());
    }

    private ValidationContext(
        ProblemReporter reporter, ContextKeySet contextKeySet, Optional<HolderGetter.Provider> resolver, Set<ResourceKey<?>> visitedElements
    ) {
        this.reporter = reporter;
        this.contextKeySet = contextKeySet;
        this.resolver = resolver;
        this.visitedElements = visitedElements;
    }

    public ValidationContext forChild(ProblemReporter.PathElement child) {
        return new ValidationContext(this.reporter.forChild(child), this.contextKeySet, this.resolver, this.visitedElements);
    }

    public ValidationContext enterElement(ProblemReporter.PathElement element, ResourceKey<?> key) {
        Set<ResourceKey<?>> set = ImmutableSet.<ResourceKey<?>>builder().addAll(this.visitedElements).add(key).build();
        return new ValidationContext(this.reporter.forChild(element), this.contextKeySet, this.resolver, set);
    }

    public boolean hasVisitedElement(ResourceKey<?> key) {
        return this.visitedElements.contains(key);
    }

    public void reportProblem(ProblemReporter.Problem problem) {
        this.reporter.report(problem);
    }

    public void validateContextUsage(LootContextUser user) {
        Set<ContextKey<?>> referencedContextParams = user.getReferencedContextParams();
        Set<ContextKey<?>> set = Sets.difference(referencedContextParams, this.contextKeySet.allowed());
        if (!set.isEmpty()) {
            this.reporter.report(new ValidationContext.ParametersNotProvidedProblem(set));
        }
    }

    public HolderGetter.Provider resolver() {
        return this.resolver.orElseThrow(() -> new UnsupportedOperationException("References not allowed"));
    }

    public boolean allowsReferences() {
        return this.resolver.isPresent();
    }

    public ValidationContext setContextKeySet(ContextKeySet contextKeySet) {
        return new ValidationContext(this.reporter, contextKeySet, this.resolver, this.visitedElements);
    }

    public ProblemReporter reporter() {
        return this.reporter;
    }

    public record MissingReferenceProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Missing element " + this.referenced.identifier() + " of type " + this.referenced.registry();
        }
    }

    public record ParametersNotProvidedProblem(Set<ContextKey<?>> notProvided) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Parameters " + this.notProvided + " are not provided in this context";
        }
    }

    public record RecursiveReferenceProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return this.referenced.identifier() + " of type " + this.referenced.registry() + " is recursively called";
        }
    }

    public record ReferenceNotAllowedProblem(ResourceKey<?> referenced) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Reference to " + this.referenced.identifier() + " of type " + this.referenced.registry() + " was used, but references are not allowed";
        }
    }
}
