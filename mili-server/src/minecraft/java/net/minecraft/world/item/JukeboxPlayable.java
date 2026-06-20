package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public record JukeboxPlayable(EitherHolder<JukeboxSong> song) implements TooltipProvider {
    public static final Codec<JukeboxPlayable> CODEC = EitherHolder.codec(Registries.JUKEBOX_SONG, JukeboxSong.CODEC)
        .xmap(JukeboxPlayable::new, JukeboxPlayable::song);
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxPlayable> STREAM_CODEC = StreamCodec.composite(
        EitherHolder.streamCodec(Registries.JUKEBOX_SONG, JukeboxSong.STREAM_CODEC), JukeboxPlayable::song, JukeboxPlayable::new
    );

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        HolderLookup.Provider provider = context.registries();
        if (provider != null) {
            this.song.unwrap(provider).ifPresent(holder -> {
                Component component = ComponentUtils.mergeStyles(holder.value().description(), Style.EMPTY.withColor(ChatFormatting.GRAY));
                tooltipAdder.accept(component);
            });
        }
    }

    public static InteractionResult tryInsertIntoJukebox(Level level, BlockPos pos, ItemStack stack, Player player) {
        JukeboxPlayable jukeboxPlayable = stack.get(DataComponents.JUKEBOX_PLAYABLE);
        if (jukeboxPlayable == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.is(Blocks.JUKEBOX) && !blockState.getValue(JukeboxBlock.HAS_RECORD)) {
                if (!level.isClientSide()) {
                    ItemStack itemStack = stack.consumeAndReturn(1, player);
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBlockEntity) {
                        jukeboxBlockEntity.setTheItem(itemStack);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockState));
                    }

                    player.awardStat(Stats.PLAY_RECORD);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        }
    }
}
