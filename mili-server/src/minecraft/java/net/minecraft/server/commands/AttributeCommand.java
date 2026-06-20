package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.stream.Stream;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType(
        entityName -> Component.translatableEscape("commands.attribute.failed.entity", entityName)
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE = new Dynamic2CommandExceptionType(
        (entityName, attributeName) -> Component.translatableEscape("commands.attribute.failed.no_attribute", entityName, attributeName)
    );
    private static final Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER = new Dynamic3CommandExceptionType(
        (entityName, attributeName, attributeUuid) -> Component.translatableEscape(
            "commands.attribute.failed.no_modifier", attributeName, entityName, attributeUuid
        )
    );
    private static final Dynamic3CommandExceptionType ERROR_MODIFIER_ALREADY_PRESENT = new Dynamic3CommandExceptionType(
        (entityName, attributeName, attributeUuid) -> Component.translatableEscape(
            "commands.attribute.failed.modifier_already_present", attributeUuid, attributeName, entityName
        )
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("attribute")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.argument("attribute", ResourceArgument.resource(context, Registries.ATTRIBUTE))
                                .then(
                                    Commands.literal("get")
                                        .executes(
                                            context1 -> getAttributeValue(
                                                context1.getSource(),
                                                EntityArgument.getEntity(context1, "target"),
                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                1.0
                                            )
                                        )
                                        .then(
                                            Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                .executes(
                                                    context1 -> getAttributeValue(
                                                        context1.getSource(),
                                                        EntityArgument.getEntity(context1, "target"),
                                                        ResourceArgument.getAttribute(context1, "attribute"),
                                                        DoubleArgumentType.getDouble(context1, "scale")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("base")
                                        .then(
                                            Commands.literal("set")
                                                .then(
                                                    Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(
                                                            context1 -> setAttributeBase(
                                                                context1.getSource(),
                                                                EntityArgument.getEntity(context1, "target"),
                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                DoubleArgumentType.getDouble(context1, "value")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("get")
                                                .executes(
                                                    context1 -> getAttributeBase(
                                                        context1.getSource(),
                                                        EntityArgument.getEntity(context1, "target"),
                                                        ResourceArgument.getAttribute(context1, "attribute"),
                                                        1.0
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                        .executes(
                                                            context1 -> getAttributeBase(
                                                                context1.getSource(),
                                                                EntityArgument.getEntity(context1, "target"),
                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                DoubleArgumentType.getDouble(context1, "scale")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("reset")
                                                .executes(
                                                    context1 -> resetAttributeBase(
                                                        context1.getSource(),
                                                        EntityArgument.getEntity(context1, "target"),
                                                        ResourceArgument.getAttribute(context1, "attribute")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("modifier")
                                        .then(
                                            Commands.literal("add")
                                                .then(
                                                    Commands.argument("id", IdentifierArgument.id())
                                                        .then(
                                                            Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                .then(
                                                                    Commands.literal("add_value")
                                                                        .executes(
                                                                            context1 -> addModifier(
                                                                                context1.getSource(),
                                                                                EntityArgument.getEntity(context1, "target"),
                                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                                IdentifierArgument.getId(context1, "id"),
                                                                                DoubleArgumentType.getDouble(context1, "value"),
                                                                                AttributeModifier.Operation.ADD_VALUE
                                                                            )
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.literal("add_multiplied_base")
                                                                        .executes(
                                                                            context1 -> addModifier(
                                                                                context1.getSource(),
                                                                                EntityArgument.getEntity(context1, "target"),
                                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                                IdentifierArgument.getId(context1, "id"),
                                                                                DoubleArgumentType.getDouble(context1, "value"),
                                                                                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                                                                            )
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.literal("add_multiplied_total")
                                                                        .executes(
                                                                            context1 -> addModifier(
                                                                                context1.getSource(),
                                                                                EntityArgument.getEntity(context1, "target"),
                                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                                IdentifierArgument.getId(context1, "id"),
                                                                                DoubleArgumentType.getDouble(context1, "value"),
                                                                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("remove")
                                                .then(
                                                    Commands.argument("id", IdentifierArgument.id())
                                                        .suggests(
                                                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
                                                                getAttributeModifiers(
                                                                    EntityArgument.getEntity(commandContext, "target"),
                                                                    ResourceArgument.getAttribute(commandContext, "attribute")
                                                                ),
                                                                suggestionsBuilder
                                                            )
                                                        )
                                                        .executes(
                                                            context1 -> removeModifier(
                                                                context1.getSource(),
                                                                EntityArgument.getEntity(context1, "target"),
                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                IdentifierArgument.getId(context1, "id")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("value")
                                                .then(
                                                    Commands.literal("get")
                                                        .then(
                                                            Commands.argument("id", IdentifierArgument.id())
                                                                .suggests(
                                                                    (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
                                                                        getAttributeModifiers(
                                                                            EntityArgument.getEntity(commandContext, "target"),
                                                                            ResourceArgument.getAttribute(commandContext, "attribute")
                                                                        ),
                                                                        suggestionsBuilder
                                                                    )
                                                                )
                                                                .executes(
                                                                    context1 -> getAttributeModifier(
                                                                        context1.getSource(),
                                                                        EntityArgument.getEntity(context1, "target"),
                                                                        ResourceArgument.getAttribute(context1, "attribute"),
                                                                        IdentifierArgument.getId(context1, "id"),
                                                                        1.0
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                                        .executes(
                                                                            context1 -> getAttributeModifier(
                                                                                context1.getSource(),
                                                                                EntityArgument.getEntity(context1, "target"),
                                                                                ResourceArgument.getAttribute(context1, "attribute"),
                                                                                IdentifierArgument.getId(context1, "id"),
                                                                                DoubleArgumentType.getDouble(context1, "scale")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static AttributeInstance getAttributeInstance(Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance instance = getLivingEntity(entity).getAttributes().getInstance(attribute);
        if (instance == null) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(entity.getName(), getAttributeDescription(attribute));
        } else {
            return instance;
        }
    }

    private static LivingEntity getLivingEntity(Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity)) {
            throw ERROR_NOT_LIVING_ENTITY.create(target.getName());
        } else {
            return (LivingEntity)target;
        }
    }

    private static LivingEntity getEntityWithAttribute(Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        LivingEntity livingEntity = getLivingEntity(entity);
        if (!livingEntity.getAttributes().hasAttribute(attribute)) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(entity.getName(), getAttributeDescription(attribute));
        } else {
            return livingEntity;
        }
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int getAttributeValue(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        LivingEntity entityWithAttribute = getEntityWithAttribute(nmsEntity, attribute); // Folia - region threading
        double attributeValue = entityWithAttribute.getAttributeValue(attribute);
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.value.get.success", getAttributeDescription(attribute), nmsEntity.getName(), attributeValue), false // Folia - region threading
        );
        return; // Folia - region threading
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static int getAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        LivingEntity entityWithAttribute = getEntityWithAttribute(nmsEntity, attribute); // Folia - region threading
        double attributeBaseValue = entityWithAttribute.getAttributeBaseValue(attribute);
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.base_value.get.success", getAttributeDescription(attribute), nmsEntity.getName(), attributeBaseValue), // Folia - region threading
            false
        );
        return; // Folia - region threading
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static int getAttributeModifier(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, Identifier id, double scale) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        LivingEntity entityWithAttribute = getEntityWithAttribute(nmsEntity, attribute); // Folia - region threading
        AttributeMap attributes = entityWithAttribute.getAttributes();
        if (!attributes.hasModifier(attribute, id)) {
            throw ERROR_NO_SUCH_MODIFIER.create(nmsEntity.getName(), getAttributeDescription(attribute), id); // Folia - region threading
        } else {
            double modifierValue = attributes.getModifierValue(attribute, id);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.attribute.modifier.value.get.success",
                    Component.translationArg(id),
                    getAttributeDescription(attribute),
                    nmsEntity.getName(), // Folia - region threading
                    modifierValue
                ),
                false
            );
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static Stream<Identifier> getAttributeModifiers(Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance attributeInstance = getAttributeInstance(entity, attribute);
        return attributeInstance.getModifiers().stream().map(AttributeModifier::id);
    }

    private static int setAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double value) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        getAttributeInstance(nmsEntity, attribute).setBaseValue(value); // Folia - region threading
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.base_value.set.success", getAttributeDescription(attribute), nmsEntity.getName(), value), false // Folia - region threading
        );
        return; // Folia - region threading
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static int resetAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        LivingEntity livingEntity = getLivingEntity(entity);
        if (!livingEntity.getAttributes().resetBaseValue(attribute)) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(entity.getName(), getAttributeDescription(attribute));
        } else {
            double attributeBaseValue = livingEntity.getAttributeBaseValue(attribute);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.attribute.base_value.reset.success", getAttributeDescription(attribute), entity.getName(), attributeBaseValue
                ),
                false
            );
            return 1;
        }
    }

    private static int addModifier(
        CommandSourceStack source, Entity entity, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation
    ) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        AttributeInstance attributeInstance = getAttributeInstance(nmsEntity, attribute); // Folia - region threading
        AttributeModifier attributeModifier = new AttributeModifier(id, amount, operation);
        if (attributeInstance.hasModifier(id)) {
            throw ERROR_MODIFIER_ALREADY_PRESENT.create(nmsEntity.getName(), getAttributeDescription(attribute), id); // Folia - region threading
        } else {
            attributeInstance.addPermanentModifier(attributeModifier);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.attribute.modifier.add.success", Component.translationArg(id), getAttributeDescription(attribute), nmsEntity.getName() // Folia - region threading
                ),
                false
            );
            return; // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static int removeModifier(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, Identifier id) throws CommandSyntaxException {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            try {
                // Folia end - region threading
        AttributeInstance attributeInstance = getAttributeInstance(nmsEntity, attribute); // Folia - region threading
        if (attributeInstance.removeModifier(id)) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.attribute.modifier.remove.success", Component.translationArg(id), getAttributeDescription(attribute), nmsEntity.getName() // Folia - region threading
                ),
                false
            );
            return; // Folia - region threading
        } else {
            throw ERROR_NO_SUCH_MODIFIER.create(nmsEntity.getName(), getAttributeDescription(attribute), id); // Folia - region threading
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        }, null, 1L);
        return 0;
        // Folia end - region threading
    }

    private static Component getAttributeDescription(Holder<Attribute> attribute) {
        return Component.translatable(attribute.value().getDescriptionId());
    }
}
