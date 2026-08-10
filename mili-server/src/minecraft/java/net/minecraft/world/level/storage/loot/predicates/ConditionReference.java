package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import org.slf4j.Logger;

public record ConditionReference(ResourceKey<LootItemCondition> name) implements LootItemCondition {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<ConditionReference> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ResourceKey.codec(Registries.PREDICATE).fieldOf("name").forGetter(ConditionReference::name))
            .apply(instance, ConditionReference::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.REFERENCE;
    }

    @Override
    public void validate(ValidationContext context) {
        if (!context.allowsReferences()) {
            context.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(this.name));
        } else if (context.hasVisitedElement(this.name)) {
            context.reportProblem(new ValidationContext.RecursiveReferenceProblem(this.name));
        } else {
            LootItemCondition.super.validate(context);
            context.resolver()
                .get(this.name)
                .ifPresentOrElse(
                    reference -> reference.value().validate(context.enterElement(new ProblemReporter.ElementReferencePathElement(this.name), this.name)),
                    () -> context.reportProblem(new ValidationContext.MissingReferenceProblem(this.name))
                );
        }
    }

    @Override
    public boolean test(LootContext context) {
        LootItemCondition lootItemCondition = context.getResolver().get(this.name).map(Holder.Reference::value).orElse(null);
        if (lootItemCondition == null) {
            LOGGER.warn("Tried using unknown condition table called {}", this.name.identifier());
            return false;
        } else {
            LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(lootItemCondition);
            if (context.pushVisitedElement(visitedEntry)) {
                boolean var4;
                try {
                    var4 = lootItemCondition.test(context);
                } finally {
                    context.popVisitedElement(visitedEntry);
                }

                return var4;
            } else {
                LOGGER.warn("Detected infinite loop in loot tables");
                return false;
            }
        }
    }

    public static LootItemCondition.Builder conditionReference(ResourceKey<LootItemCondition> name) {
        return () -> new ConditionReference(name);
    }
}
