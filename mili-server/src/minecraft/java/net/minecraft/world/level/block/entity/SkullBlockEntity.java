package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SkullBlockEntity extends BlockEntity {
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    public @Nullable ResolvableProfile owner;
    public @Nullable Identifier noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;
    public @Nullable Component customName;

    public SkullBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SKULL, pos, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("profile", ResolvableProfile.CODEC, this.owner);
        output.storeNullable("note_block_sound", Identifier.CODEC, this.noteBlockSound);
        output.storeNullable("custom_name", ComponentSerialization.CODEC, this.customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("profile", ResolvableProfile.CODEC).orElse(null);
        this.noteBlockSound = input.read("note_block_sound", Identifier.CODEC).orElse(null);
        this.customName = parseCustomNameSafe(input, "custom_name");
    }

    public static void animation(Level level, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
        if (state.hasProperty(SkullBlock.POWERED) && state.getValue(SkullBlock.POWERED)) {
            blockEntity.isAnimating = true;
            blockEntity.animationTickCount++;
        } else {
            blockEntity.isAnimating = false;
        }
    }

    public float getAnimation(float partialTick) {
        return this.isAnimating ? this.animationTickCount + partialTick : this.animationTickCount;
    }

    public @Nullable ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    public @Nullable Identifier getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        this.owner = componentGetter.get(DataComponents.PROFILE);
        this.noteBlockSound = componentGetter.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = componentGetter.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, this.owner);
        components.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
        components.set(DataComponents.CUSTOM_NAME, this.customName);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("profile");
        output.discard("note_block_sound");
        output.discard("custom_name");
    }
}
