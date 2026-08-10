package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class LongArgumentInfo implements ArgumentTypeInfo<LongArgumentType, LongArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(LongArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean flag = template.min != Long.MIN_VALUE;
        boolean flag1 = template.max != Long.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            buffer.writeLong(template.min);
        }

        if (flag1) {
            buffer.writeLong(template.max);
        }
    }

    @Override
    public LongArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte _byte = buffer.readByte();
        long l = ArgumentUtils.numberHasMin(_byte) ? buffer.readLong() : Long.MIN_VALUE;
        long l1 = ArgumentUtils.numberHasMax(_byte) ? buffer.readLong() : Long.MAX_VALUE;
        return new LongArgumentInfo.Template(l, l1);
    }

    @Override
    public void serializeToJson(LongArgumentInfo.Template template, JsonObject json) {
        if (template.min != Long.MIN_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Long.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    @Override
    public LongArgumentInfo.Template unpack(LongArgumentType argument) {
        return new LongArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<LongArgumentType> {
        final long min;
        final long max;

        Template(final long min, final long max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public LongArgumentType instantiate(CommandBuildContext context) {
            return LongArgumentType.longArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<LongArgumentType, ?> type() {
            return LongArgumentInfo.this;
        }
    }
}
