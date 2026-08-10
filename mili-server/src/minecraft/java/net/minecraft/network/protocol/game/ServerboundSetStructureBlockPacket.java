package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;

public class ServerboundSetStructureBlockPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetStructureBlockPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetStructureBlockPacket::write, ServerboundSetStructureBlockPacket::new
    );
    private static final int FLAG_IGNORE_ENTITIES = 1;
    private static final int FLAG_SHOW_AIR = 2;
    private static final int FLAG_SHOW_BOUNDING_BOX = 4;
    private static final int FLAG_STRICT = 8;
    private final BlockPos pos;
    private final StructureBlockEntity.UpdateType updateType;
    private final StructureMode mode;
    private final String name;
    private final BlockPos offset;
    private final Vec3i size;
    private final Mirror mirror;
    private final Rotation rotation;
    private final String data;
    private final boolean ignoreEntities;
    private final boolean strict;
    private final boolean showAir;
    private final boolean showBoundingBox;
    private final float integrity;
    private final long seed;

    public ServerboundSetStructureBlockPacket(
        BlockPos pos,
        StructureBlockEntity.UpdateType updateType,
        StructureMode mode,
        String name,
        BlockPos offset,
        Vec3i size,
        Mirror mirror,
        Rotation rotation,
        String data,
        boolean ignoreEntities,
        boolean strict,
        boolean showAir,
        boolean showBoundingBox,
        float integrity,
        long seed
    ) {
        this.pos = pos;
        this.updateType = updateType;
        this.mode = mode;
        this.name = name;
        this.offset = offset;
        this.size = size;
        this.mirror = mirror;
        this.rotation = rotation;
        this.data = data;
        this.ignoreEntities = ignoreEntities;
        this.strict = strict;
        this.showAir = showAir;
        this.showBoundingBox = showBoundingBox;
        this.integrity = integrity;
        this.seed = seed;
    }

    private ServerboundSetStructureBlockPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.updateType = buffer.readEnum(StructureBlockEntity.UpdateType.class);
        this.mode = buffer.readEnum(StructureMode.class);
        this.name = buffer.readUtf();
        int i = 48;
        this.offset = new BlockPos(Mth.clamp(buffer.readByte(), -48, 48), Mth.clamp(buffer.readByte(), -48, 48), Mth.clamp(buffer.readByte(), -48, 48));
        int i1 = 48;
        this.size = new Vec3i(Mth.clamp(buffer.readByte(), 0, 48), Mth.clamp(buffer.readByte(), 0, 48), Mth.clamp(buffer.readByte(), 0, 48));
        this.mirror = buffer.readEnum(Mirror.class);
        this.rotation = buffer.readEnum(Rotation.class);
        this.data = buffer.readUtf(128);
        this.integrity = Mth.clamp(buffer.readFloat(), 0.0F, 1.0F);
        this.seed = buffer.readVarLong();
        int _byte = buffer.readByte();
        this.ignoreEntities = (_byte & 1) != 0;
        this.strict = (_byte & 8) != 0;
        this.showAir = (_byte & 2) != 0;
        this.showBoundingBox = (_byte & 4) != 0;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeEnum(this.updateType);
        buffer.writeEnum(this.mode);
        buffer.writeUtf(this.name);
        buffer.writeByte(this.offset.getX());
        buffer.writeByte(this.offset.getY());
        buffer.writeByte(this.offset.getZ());
        buffer.writeByte(this.size.getX());
        buffer.writeByte(this.size.getY());
        buffer.writeByte(this.size.getZ());
        buffer.writeEnum(this.mirror);
        buffer.writeEnum(this.rotation);
        buffer.writeUtf(this.data);
        buffer.writeFloat(this.integrity);
        buffer.writeVarLong(this.seed);
        int i = 0;
        if (this.ignoreEntities) {
            i |= 1;
        }

        if (this.showAir) {
            i |= 2;
        }

        if (this.showBoundingBox) {
            i |= 4;
        }

        if (this.strict) {
            i |= 8;
        }

        buffer.writeByte(i);
    }

    @Override
    public PacketType<ServerboundSetStructureBlockPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_STRUCTURE_BLOCK;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSetStructureBlock(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public StructureBlockEntity.UpdateType getUpdateType() {
        return this.updateType;
    }

    public StructureMode getMode() {
        return this.mode;
    }

    public String getName() {
        return this.name;
    }

    public BlockPos getOffset() {
        return this.offset;
    }

    public Vec3i getSize() {
        return this.size;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public String getData() {
        return this.data;
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public boolean isStrict() {
        return this.strict;
    }

    public boolean isShowAir() {
        return this.showAir;
    }

    public boolean isShowBoundingBox() {
        return this.showBoundingBox;
    }

    public float getIntegrity() {
        return this.integrity;
    }

    public long getSeed() {
        return this.seed;
    }
}
