package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

public record WrittenBookContent(Filterable<String> title, String author, int generation, @Override List<Filterable<Component>> pages, boolean resolved)
    implements BookContent<Component, WrittenBookContent>,
    TooltipProvider {
    public static final WrittenBookContent EMPTY = new WrittenBookContent(Filterable.passThrough(""), "", 0, List.of(), true);
    public static final int PAGE_LENGTH = 32767;
    public static final int TITLE_LENGTH = 16;
    public static final int TITLE_MAX_LENGTH = 32;
    public static final int MAX_GENERATION = 3;
    public static final int MAX_CRAFTABLE_GENERATION = 2;
    public static final Codec<Component> CONTENT_CODEC = ComponentSerialization.flatRestrictedCodec(32767);
    public static final Codec<List<Filterable<Component>>> PAGES_CODEC = pagesCodec(CONTENT_CODEC);
    public static final Codec<WrittenBookContent> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Filterable.codec(Codec.string(0, 32)).fieldOf("title").forGetter(WrittenBookContent::title),
                Codec.STRING.fieldOf("author").forGetter(WrittenBookContent::author),
                ExtraCodecs.intRange(0, 3).optionalFieldOf("generation", 0).forGetter(WrittenBookContent::generation),
                PAGES_CODEC.optionalFieldOf("pages", List.of()).forGetter(WrittenBookContent::pages),
                Codec.BOOL.optionalFieldOf("resolved", false).forGetter(WrittenBookContent::resolved)
            )
            .apply(instance, WrittenBookContent::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, WrittenBookContent> STREAM_CODEC = StreamCodec.composite(
        Filterable.streamCodec(ByteBufCodecs.stringUtf8(32)),
        WrittenBookContent::title,
        ByteBufCodecs.STRING_UTF8,
        WrittenBookContent::author,
        ByteBufCodecs.VAR_INT,
        WrittenBookContent::generation,
        Filterable.streamCodec(ComponentSerialization.STREAM_CODEC).apply(ByteBufCodecs.list()),
        WrittenBookContent::pages,
        ByteBufCodecs.BOOL,
        WrittenBookContent::resolved,
        WrittenBookContent::new
    );

    public WrittenBookContent(Filterable<String> title, String author, int generation, List<Filterable<Component>> pages, boolean resolved) {
        if (generation >= 0 && generation <= 3) {
            this.title = title;
            this.author = author;
            this.generation = generation;
            this.pages = pages;
            this.resolved = resolved;
        } else {
            throw new IllegalArgumentException("Generation was " + generation + ", but must be between 0 and 3");
        }
    }

    private static Codec<Filterable<Component>> pageCodec(Codec<Component> codec) {
        return Filterable.codec(codec);
    }

    public static Codec<List<Filterable<Component>>> pagesCodec(Codec<Component> codec) {
        return pageCodec(codec).listOf();
    }

    public @Nullable WrittenBookContent tryCraftCopy() {
        return this.generation >= 2 ? null : new WrittenBookContent(this.title, this.author, this.generation + 1, this.pages, this.resolved);
    }

    public static boolean resolveForItem(ItemStack stack, CommandSourceStack source, @Nullable Player player) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.resolveSelectorsInBooks && writtenBookContent != null && !writtenBookContent.resolved()) { // Paper - Disable component selector resolving in books by default
            WrittenBookContent writtenBookContent1 = writtenBookContent.resolve(source, player);
            if (writtenBookContent1 != null) {
                stack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent1);
                return true;
            }

            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent.markResolved());
        }

        return false;
    }

    public @Nullable WrittenBookContent resolve(CommandSourceStack source, @Nullable Player player) {
        if (this.resolved) {
            return null;
        } else {
            Builder<Filterable<Component>> builder = ImmutableList.builderWithExpectedSize(this.pages.size());

            for (Filterable<Component> filterable : this.pages) {
                Optional<Filterable<Component>> optional = resolvePage(source, player, filterable);
                if (optional.isEmpty()) {
                    return null;
                }

                builder.add(optional.get());
            }

            return new WrittenBookContent(this.title, this.author, this.generation, builder.build(), true);
        }
    }

    public WrittenBookContent markResolved() {
        return new WrittenBookContent(this.title, this.author, this.generation, this.pages, true);
    }

    private static Optional<Filterable<Component>> resolvePage(CommandSourceStack source, @Nullable Player player, Filterable<Component> pages) {
        return pages.resolve(component -> {
            try {
                Component component1 = ComponentUtils.updateForEntity(source, component, player, 0);
                return isPageTooLarge(component1, source.registryAccess()) ? Optional.empty() : Optional.of(component1);
            } catch (Exception var4) {
                return Optional.of(component);
            }
        });
    }

    private static boolean isPageTooLarge(Component page, HolderLookup.Provider registryAccess) {
        DataResult<JsonElement> dataResult = ComponentSerialization.CODEC.encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), page);
        return dataResult.isSuccess() && GsonHelper.encodesLongerThan(dataResult.getOrThrow(), 32767);
    }

    public List<Component> getPages(boolean filtered) {
        return Lists.transform(this.pages, filterable -> filterable.get(filtered));
    }

    @Override
    public WrittenBookContent withReplacedPages(List<Filterable<Component>> newPages) {
        return new WrittenBookContent(this.title, this.author, this.generation, newPages, false);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        if (!StringUtil.isBlank(this.author)) {
            tooltipAdder.accept(Component.translatable("book.byAuthor", this.author).withStyle(ChatFormatting.GRAY));
        }

        tooltipAdder.accept(Component.translatable("book.generation." + this.generation).withStyle(ChatFormatting.GRAY));
    }
}
