package net.minecraft.advancements.criterion;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {
    private final ProblemReporter reporter;
    private final HolderGetter.Provider lootData;

    public CriterionValidator(ProblemReporter reporter, HolderGetter.Provider lootData) {
        this.reporter = reporter;
        this.lootData = lootData;
    }

    public void validateEntity(Optional<ContextAwarePredicate> entity, String name) {
        entity.ifPresent(predicate -> this.validateEntity(predicate, name));
    }

    public void validateEntities(List<ContextAwarePredicate> entities, String name) {
        this.validate(entities, LootContextParamSets.ADVANCEMENT_ENTITY, name);
    }

    public void validateEntity(ContextAwarePredicate entity, String name) {
        this.validate(entity, LootContextParamSets.ADVANCEMENT_ENTITY, name);
    }

    public void validate(ContextAwarePredicate entity, ContextKeySet contextKeySet, String name) {
        entity.validate(new ValidationContext(this.reporter.forChild(new ProblemReporter.FieldPathElement(name)), contextKeySet, this.lootData));
    }

    public void validate(List<ContextAwarePredicate> entities, ContextKeySet contextKeySet, String name) {
        for (int i = 0; i < entities.size(); i++) {
            ContextAwarePredicate contextAwarePredicate = entities.get(i);
            contextAwarePredicate.validate(
                new ValidationContext(this.reporter.forChild(new ProblemReporter.IndexedFieldPathElement(name, i)), contextKeySet, this.lootData)
            );
        }
    }
}
