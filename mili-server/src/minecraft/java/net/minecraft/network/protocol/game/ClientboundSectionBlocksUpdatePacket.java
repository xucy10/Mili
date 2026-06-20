package net.minecraft.network.protocol.game;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class ClientboundSectionBlocksUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSectionBlocksUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundSectionBlocksUpdatePacket::write, ClientboundSectionBlocksUpdatePacket::new
    );
    private static final int POS_IN_SECTION_BITS = 12;
    private final SectionPos sectionPos;
    private final short[] positions;
    private final BlockState[] states;

    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, ShortSet positions, LevelChunkSection section) {
        this.sectionPos = sectionPos;
        int size = positions.size();
        this.positions = new short[size];
        this.states = new BlockState[size];
        int i = 0;

        for (short s : positions) {
            this.positions[i] = s;
            this.states[i] = (section != null) ? section.getBlockState(SectionPos.sectionRelativeX(s), SectionPos.sectionRelativeY(s), SectionPos.sectionRelativeZ(s)) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(); // CraftBukkit - SPIGOT-6076, Mojang bug when empty chunk section notified
            i++;
        }
    }
    // CraftBukkit start - Add constructor
    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionposition, ShortSet shortset, BlockState[] states) {
        this.sectionPos = sectionposition;
        this.positions = shortset.toShortArray();
        this.states = states;
    }
    // CraftBukkit end
    // Paper start - Multi Block Change API
    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, it.unimi.dsi.fastutil.shorts.Short2ObjectMap<BlockState> blockChanges) {
        this.sectionPos = sectionPos;
        this.positions = blockChanges.keySet().toShortArray();
        this.states = blockChanges.values().toArray(new BlockState[0]);
    }
    // Paper end - Multi Block Change API


    private ClientboundSectionBlocksUpdatePacket(FriendlyByteBuf buffer) {
        this.sectionPos = SectionPos.STREAM_CODEC.decode(buffer);
        int varInt = buffer.readVarInt();
        this.positions = new short[varInt];
        this.states = new BlockState[varInt];

        for (int i = 0; i < varInt; i++) {
            long varLong = buffer.readVarLong();
            this.positions[i] = (short)(varLong & 4095L);
            this.states[i] = Block.BLOCK_STATE_REGISTRY.byId((int)(varLong >>> 12));
        }
    }

    private void write(FriendlyByteBuf buffer) {
        SectionPos.STREAM_CODEC.encode(buffer, this.sectionPos);
        buffer.writeVarInt(this.positions.length);

        for (int i = 0; i < this.positions.length; i++) {
            buffer.writeVarLong((long)Block.getId(this.states[i]) << 12 | this.positions[i]);
        }
    }

    @Override
    public PacketType<ClientboundSectionBlocksUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleChunkBlocksUpdate(this);
    }

    public void runUpdates(BiConsumer<BlockPos, BlockState> consumer) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < this.positions.length; i++) {
            short s = this.positions[i];
            mutableBlockPos.set(this.sectionPos.relativeToBlockX(s), this.sectionPos.relativeToBlockY(s), this.sectionPos.relativeToBlockZ(s));
            consumer.accept(mutableBlockPos, this.states[i]);
        }
    }
}
