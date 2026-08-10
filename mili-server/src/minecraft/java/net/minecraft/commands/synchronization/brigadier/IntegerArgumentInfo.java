package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class IntegerArgumentInfo implements ArgumentTypeInfo<IntegerArgumentType, IntegerArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(IntegerArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean flag = template.min != Integer.MIN_VALUE;
        boolean flag1 = template.max != Integer.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            buffer.writeInt(template.min);
        }

        if (flag1) {
            buffer.writeInt(template.max);
        }
    }

    @Override
    public IntegerArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte _byte = buffer.readByte();
        int i = ArgumentUtils.numberHasMin(_byte) ? buffer.readInt() : Integer.MIN_VALUE;
        int i1 = ArgumentUtils.numberHasMax(_byte) ? buffer.readInt() : Integer.MAX_VALUE;
        return new IntegerArgumentInfo.Template(i, i1);
    }

    @Override
    public void serializeToJson(IntegerArgumentInfo.Template template, JsonObject json) {
        if (template.min != Integer.MIN_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Integer.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    @Override
    public IntegerArgumentInfo.Template unpack(IntegerArgumentType argument) {
        return new IntegerArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<IntegerArgumentType> {
        final int min;
        final int max;

        Template(final int min, final int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public IntegerArgumentType instantiate(CommandBuildContext context) {
            return IntegerArgumentType.integer(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<IntegerArgumentType, ?> type() {
            return IntegerArgumentInfo.this;
        }
    }
}
