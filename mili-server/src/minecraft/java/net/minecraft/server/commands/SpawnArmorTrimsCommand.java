package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

public class SpawnArmorTrimsCommand {
    private static final List<ResourceKey<TrimPattern>> VANILLA_TRIM_PATTERNS = List.of(
        TrimPatterns.SENTRY,
        TrimPatterns.DUNE,
        TrimPatterns.COAST,
        TrimPatterns.WILD,
        TrimPatterns.WARD,
        TrimPatterns.EYE,
        TrimPatterns.VEX,
        TrimPatterns.TIDE,
        TrimPatterns.SNOUT,
        TrimPatterns.RIB,
        TrimPatterns.SPIRE,
        TrimPatterns.WAYFINDER,
        TrimPatterns.SHAPER,
        TrimPatterns.SILENCE,
        TrimPatterns.RAISER,
        TrimPatterns.HOST,
        TrimPatterns.FLOW,
        TrimPatterns.BOLT
    );
    private static final List<ResourceKey<TrimMaterial>> VANILLA_TRIM_MATERIALS = List.of(
        TrimMaterials.QUARTZ,
        TrimMaterials.IRON,
        TrimMaterials.NETHERITE,
        TrimMaterials.REDSTONE,
        TrimMaterials.COPPER,
        TrimMaterials.GOLD,
        TrimMaterials.EMERALD,
        TrimMaterials.DIAMOND,
        TrimMaterials.LAPIS,
        TrimMaterials.AMETHYST,
        TrimMaterials.RESIN
    );
    private static final ToIntFunction<ResourceKey<TrimPattern>> TRIM_PATTERN_ORDER = Util.createIndexLookup(VANILLA_TRIM_PATTERNS);
    private static final ToIntFunction<ResourceKey<TrimMaterial>> TRIM_MATERIAL_ORDER = Util.createIndexLookup(VANILLA_TRIM_MATERIALS);
    private static final DynamicCommandExceptionType ERROR_INVALID_PATTERN = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("Invalid pattern", object)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawn_armor_trims")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("*_lag_my_game")
                        .executes(commandContext -> spawnAllArmorTrims(commandContext.getSource(), commandContext.getSource().getPlayerOrException()))
                )
                .then(
                    Commands.argument("pattern", ResourceKeyArgument.key(Registries.TRIM_PATTERN))
                        .executes(
                            context -> spawnArmorTrim(
                                context.getSource(),
                                context.getSource().getPlayerOrException(),
                                ResourceKeyArgument.getRegistryKey(context, "pattern", Registries.TRIM_PATTERN, ERROR_INVALID_PATTERN)
                            )
                        )
                )
        );
    }

    private static int spawnAllArmorTrims(CommandSourceStack source, Player player) {
        return spawnArmorTrims(source, player, source.getServer().registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).listElements());
    }

    private static int spawnArmorTrim(CommandSourceStack source, Player player, ResourceKey<TrimPattern> pattern) {
        return spawnArmorTrims(source, player, Stream.of(source.getServer().registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(pattern).orElseThrow()));
    }

    private static int spawnArmorTrims(CommandSourceStack source, Player player, Stream<Holder.Reference<TrimPattern>> patterns) {
        ServerLevel level = source.getLevel();
        List<Holder.Reference<TrimPattern>> list = patterns.sorted(Comparator.comparing(reference3 -> TRIM_PATTERN_ORDER.applyAsInt(reference3.key())))
            .toList();
        List<Holder.Reference<TrimMaterial>> list1 = level.registryAccess()
            .lookupOrThrow(Registries.TRIM_MATERIAL)
            .listElements()
            .sorted(Comparator.comparing(reference3 -> TRIM_MATERIAL_ORDER.applyAsInt(reference3.key())))
            .toList();
        List<Holder.Reference<Item>> list2 = findEquippableItemsWithAssets(level.registryAccess().lookupOrThrow(Registries.ITEM));
        BlockPos blockPos = player.blockPosition().relative(player.getDirection(), 5);
        double d = 3.0;

        for (int i = 0; i < list1.size(); i++) {
            Holder.Reference<TrimMaterial> reference = list1.get(i);

            for (int i1 = 0; i1 < list.size(); i1++) {
                Holder.Reference<TrimPattern> reference1 = list.get(i1);
                ArmorTrim armorTrim = new ArmorTrim(reference, reference1);

                for (int i2 = 0; i2 < list2.size(); i2++) {
                    Holder.Reference<Item> reference2 = list2.get(i2);
                    double d1 = blockPos.getX() + 0.5 - i2 * 3.0;
                    double d2 = blockPos.getY() + 0.5 + i * 3.0;
                    double d3 = blockPos.getZ() + 0.5 + i1 * 10;
                    ArmorStand armorStand = new ArmorStand(level, d1, d2, d3);
                    armorStand.setYRot(180.0F);
                    armorStand.setNoGravity(true);
                    ItemStack itemStack = new ItemStack(reference2);
                    Equippable equippable = Objects.requireNonNull(itemStack.get(DataComponents.EQUIPPABLE));
                    itemStack.set(DataComponents.TRIM, armorTrim);
                    armorStand.setItemSlot(equippable.slot(), itemStack);
                    if (i2 == 0) {
                        armorStand.setCustomName(
                            armorTrim.pattern()
                                .value()
                                .copyWithStyle(armorTrim.material())
                                .copy()
                                .append(" & ")
                                .append(armorTrim.material().value().description())
                        );
                        armorStand.setCustomNameVisible(true);
                    } else {
                        armorStand.setInvisible(true);
                    }

                    level.addFreshEntity(armorStand);
                }
            }
        }

        source.sendSuccess(() -> Component.literal("Armorstands with trimmed armor spawned around you"), true);
        return 1;
    }

    private static List<Holder.Reference<Item>> findEquippableItemsWithAssets(HolderLookup<Item> itemRegistry) {
        List<Holder.Reference<Item>> list = new ArrayList<>();
        itemRegistry.listElements().forEach(reference -> {
            Equippable equippable = reference.value().components().get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && equippable.assetId().isPresent()) {
                list.add((Holder.Reference<Item>)reference);
            }
        });
        return list;
    }
}
