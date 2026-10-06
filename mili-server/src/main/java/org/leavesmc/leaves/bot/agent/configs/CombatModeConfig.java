/*
 * This file is part of Leaves (https://github.com/LeavesMC/Leaves)
 *
 * Leaves is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Leaves is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Leaves. If not, see <https://www.gnu.org/licenses/>.
 */

package org.leavesmc.leaves.bot.agent.configs;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fun.bm.mili.config.modules.function.FakeplayerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.arguments.EnumArgumentType;

import java.util.Locale;

/**
 * Mili start - bot combat AI
 *
 * Selects how this bot behaves when a hostile mob is nearby.
 *
 * <ul>
 *   <li>{@link CombatMode#NONE} — no AI at all (upstream Leaves behaviour).</li>
 *   <li>{@link CombatMode#GUARD} — "tower top" form: the bot holds its ground and only rotates to
 *       face + attack whatever comes into {@link CombatRangeConfig#attackRange}. It never walks, so
 *       it does not walk off a farm platform. Use this when the spawner is right next to the bot.</li>
 *   <li>{@link CombatMode#LURE} — "tower bottom" form: the bot seeks mobs that are further away
 *       (out to {@link CombatRangeConfig#lureRange}) and walks towards them to pull them into the
 *       kill zone, then fights. This is what a farm where the spawner is above the bot needs.</li>
 * </ul>
 */
public class CombatModeConfig extends AbstractBotConfig<CombatModeConfig.CombatMode, CombatModeConfig> {

    public enum CombatMode {
        /** No combat AI. */
        NONE,
        /** Hold position, rotate and attack in melee range. */
        GUARD,
        /** Walk towards distant mobs to lure them, then attack. */
        LURE
    }

    private CombatMode value;

    public CombatModeConfig() {
        super("combat_mode", EnumArgumentType.fromEnum(CombatMode.class), CombatModeConfig::new);
        this.value = FakeplayerConfig.botCombatMode;
    }

    @Override
    public void applySuggestions(CommandContext context, @NotNull SuggestionsBuilder builder) {
        builder.suggest("none", Component.literal("No combat AI (default)"));
        builder.suggest("guard", Component.literal("Hold position, attack nearby (farm top)"));
        builder.suggest("lure", Component.literal("Walk to pull mobs in (farm bottom)"));
    }

    @Override
    public CombatMode getValue() {
        return value;
    }

    @Override
    public void setValue(CombatMode value) throws IllegalArgumentException {
        this.value = value;
    }

    @Override
    public CombatMode loadFromCommand(CommandContext context) {
        return context.getArgument(getName(), CombatMode.class);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        super.save(nbt);
        nbt.putString(getName(), this.getValue().toString().toLowerCase(Locale.ROOT));
        return nbt;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        String raw = nbt.getStringOr(getName(), FakeplayerConfig.botCombatMode.name());
        CombatMode parsed;
        try {
            parsed = CombatMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = CombatMode.NONE;
        }
        this.setValue(parsed);
    }
}
// Mili end - bot combat AI