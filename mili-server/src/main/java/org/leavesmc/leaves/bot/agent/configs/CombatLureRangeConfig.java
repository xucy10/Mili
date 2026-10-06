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

import com.mojang.brigadier.arguments.DoubleArgumentType;
import fun.bm.mili.config.modules.function.FakeplayerConfig;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;

/**
 * Mili start - bot combat AI
 *
 * How far (in blocks) a {@link CombatModeConfig.CombatMode#LURE} bot will walk towards a mob in
 * order to drag it back to the kill zone. Only consulted in LURE mode.
 *
 * <p>The bot gives up and returns to its anchor once the target escapes this radius, so a mob that
 * flees cannot drag the bot off the platform forever.
 */
public class CombatLureRangeConfig extends AbstractBotConfig<Double, CombatLureRangeConfig> {
    private double value;

    public CombatLureRangeConfig() {
        super("combat_lure_range", DoubleArgumentType.doubleArg(1.0D, 64.0D), CombatLureRangeConfig::new);
        this.value = FakeplayerConfig.botCombatLureRange;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public void setValue(Double value) throws IllegalArgumentException {
        this.value = value;
    }

    @Override
    public Double loadFromCommand(CommandContext context) {
        return context.getArgument(getName(), Double.class);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        super.save(nbt);
        nbt.putDouble(getName(), this.getValue());
        return nbt;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        this.setValue(nbt.getDouble(getName()).orElse(FakeplayerConfig.botCombatLureRange));
    }
}
// Mili end - bot combat AI