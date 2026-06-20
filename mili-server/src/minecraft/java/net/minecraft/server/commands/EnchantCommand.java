package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class EnchantCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType(
        entity -> Component.translatableEscape("commands.enchant.failed.entity", entity)
    );
    private static final DynamicCommandExceptionType ERROR_NO_ITEM = new DynamicCommandExceptionType(
        entity -> Component.translatableEscape("commands.enchant.failed.itemless", entity)
    );
    private static final DynamicCommandExceptionType ERROR_INCOMPATIBLE = new DynamicCommandExceptionType(
        item -> Component.translatableEscape("commands.enchant.failed.incompatible", item)
    );
    private static final Dynamic2CommandExceptionType ERROR_LEVEL_TOO_HIGH = new Dynamic2CommandExceptionType(
        (requestedLevel, maxLevel) -> Component.translatableEscape("commands.enchant.failed.level", requestedLevel, maxLevel)
    );
    private static final SimpleCommandExceptionType ERROR_NOTHING_HAPPENED = new SimpleCommandExceptionType(Component.translatable("commands.enchant.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("enchant")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .then(
                            Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                .executes(
                                    commandContext -> enchant(
                                        commandContext.getSource(),
                                        EntityArgument.getEntities(commandContext, "targets"),
                                        ResourceArgument.getEnchantment(commandContext, "enchantment"),
                                        1
                                    )
                                )
                                .then(
                                    Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(
                                            context1 -> enchant(
                                                context1.getSource(),
                                                EntityArgument.getEntities(context1, "targets"),
                                                ResourceArgument.getEnchantment(context1, "enchantment"),
                                                IntegerArgumentType.getInteger(context1, "level")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int enchant(CommandSourceStack source, Collection<? extends Entity> targets, Holder<Enchantment> enchantment, int level) throws CommandSyntaxException {
        Enchantment enchantment1 = enchantment.value();
        if (level > enchantment1.getMaxLevel()) {
            throw ERROR_LEVEL_TOO_HIGH.create(level, enchantment1.getMaxLevel());
        } else {
            final java.util.concurrent.atomic.AtomicInteger changed = new java.util.concurrent.atomic.AtomicInteger(0); // Folia - region threading
            final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(targets.size()); // Folia - region threading
            final java.util.concurrent.atomic.AtomicReference<Component> possibleSingleDisplayName = new java.util.concurrent.atomic.AtomicReference<>(); // Folia - region threading

            for (Entity entity : targets) {
                if (entity instanceof LivingEntity livingEntity) {
                    // Folia start - region threading
                    entity.getBukkitEntity().taskScheduler.schedule((LivingEntity nmsEntity) -> {
                        try {
                            ItemStack mainHandItem = livingEntity.getMainHandItem();
                            if (!mainHandItem.isEmpty()) {
                                if (enchantment1.canEnchant(mainHandItem)
                                    && EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(mainHandItem).keySet(), enchantment)) {
                                    mainHandItem.enchant(enchantment, level);
                                    possibleSingleDisplayName.set(livingEntity.getDisplayName());
                                    changed.incrementAndGet();
                                } else if (targets.size() == 1) {
                                    throw ERROR_INCOMPATIBLE.create(mainHandItem.getHoverName().getString());
                                }
                            } else if (targets.size() == 1) {
                                throw ERROR_NO_ITEM.create(livingEntity.getName().getString());
                            }
                        } catch (final CommandSyntaxException exception) {
                            sendMessage(source, exception);
                            return; // don't send feedback twice
                        }
                        sendFeedback(source, enchantment, level, possibleSingleDisplayName, count, changed);
                    }, ignored -> sendFeedback(source, enchantment, level, possibleSingleDisplayName, count, changed), 1L);
                } else if (targets.size() == 1) {
                    throw ERROR_NOT_LIVING_ENTITY.create(entity.getName().getString());
                } else {
                    sendFeedback(source, enchantment, level, possibleSingleDisplayName, count, changed);
                    // Folia end - region threading
                }
            }
            return targets.size(); // Folia - region threading
        }
    }

    // Folia start - region threading
    private static void sendFeedback(final CommandSourceStack source, final Holder<Enchantment> enchantment, final int level, final java.util.concurrent.atomic.AtomicReference<Component> possibleSingleDisplayName, final java.util.concurrent.atomic.AtomicInteger count, final java.util.concurrent.atomic.AtomicInteger changed) {
        if (count.decrementAndGet() == 0) {
            final int i = changed.get();
            if (i == 0) {
                sendMessage(source, ERROR_NOTHING_HAPPENED.create());
            } else {
                if (i == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                            "commands.enchant.success.single", Enchantment.getFullname(enchantment, level), possibleSingleDisplayName.get()
                        ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable("commands.enchant.success.multiple", Enchantment.getFullname(enchantment, level), i), true
                    );
                }
            }
        }
    }
    // Folia end - region threading
}
