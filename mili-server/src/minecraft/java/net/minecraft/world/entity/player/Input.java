package net.minecraft.world.entity.player;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record Input(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint) {
    private static final byte FLAG_FORWARD = 1;
    private static final byte FLAG_BACKWARD = 2;
    private static final byte FLAG_LEFT = 4;
    private static final byte FLAG_RIGHT = 8;
    private static final byte FLAG_JUMP = 16;
    private static final byte FLAG_SHIFT = 32;
    private static final byte FLAG_SPRINT = 64;
    public static final StreamCodec<FriendlyByteBuf, Input> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, Input>() {
        @Override
        public void encode(FriendlyByteBuf buffer, Input value) {
            byte b = 0;
            b = (byte)(b | (value.forward() ? 1 : 0));
            b = (byte)(b | (value.backward() ? 2 : 0));
            b = (byte)(b | (value.left() ? 4 : 0));
            b = (byte)(b | (value.right() ? 8 : 0));
            b = (byte)(b | (value.jump() ? 16 : 0));
            b = (byte)(b | (value.shift() ? 32 : 0));
            b = (byte)(b | (value.sprint() ? 64 : 0));
            buffer.writeByte(b);
        }

        @Override
        public Input decode(FriendlyByteBuf buffer) {
            byte _byte = buffer.readByte();
            boolean flag = (_byte & 1) != 0;
            boolean flag1 = (_byte & 2) != 0;
            boolean flag2 = (_byte & 4) != 0;
            boolean flag3 = (_byte & 8) != 0;
            boolean flag4 = (_byte & 16) != 0;
            boolean flag5 = (_byte & 32) != 0;
            boolean flag6 = (_byte & 64) != 0;
            return new Input(flag, flag1, flag2, flag3, flag4, flag5, flag6);
        }
    };
    public static Input EMPTY = new Input(false, false, false, false, false, false, false);
}
