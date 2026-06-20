package net.minecraft.advancements;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.CriterionValidator;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public record Advancement(
    Optional<Identifier> parent,
    Optional<DisplayInfo> display,
    AdvancementRewards rewards,
    Map<String, Criterion<?>> criteria,
    AdvancementRequirements requirements,
    boolean sendsTelemetryEvent,
    Optional<Component> name
) {
    private static final Codec<Map<String, Criterion<?>>> CRITERIA_CODEC = Codec.unboundedMap(Codec.STRING, Criterion.CODEC)
        .validate(criteria -> criteria.isEmpty() ? DataResult.error(() -> "Advancement criteria cannot be empty") : DataResult.success(criteria));
    public static final Codec<Advancement> CODEC = RecordCodecBuilder.<Advancement>create(
            instance -> instance.group(
                    Identifier.CODEC.optionalFieldOf("parent").forGetter(Advancement::parent),
                    DisplayInfo.CODEC.optionalFieldOf("display").forGetter(Advancement::display),
                    AdvancementRewards.CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(Advancement::rewards),
                    CRITERIA_CODEC.fieldOf("criteria").forGetter(Advancement::criteria),
                    AdvancementRequirements.CODEC.optionalFieldOf("requirements").forGetter(advancement -> Optional.of(advancement.requirements())),
                    Codec.BOOL.optionalFieldOf("sends_telemetry_event", false).forGetter(Advancement::sendsTelemetryEvent)
                )
                .apply(instance, (parent, displayInfo, rewards, criteria, requirements, sendsTelemetryEvent) -> {
                    AdvancementRequirements advancementRequirements = requirements.orElseGet(() -> AdvancementRequirements.allOf(criteria.keySet()));
                    return new Advancement(parent, displayInfo, rewards, criteria, advancementRequirements, sendsTelemetryEvent);
                })
        )
        .validate(Advancement::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, Advancement> STREAM_CODEC = StreamCodec.ofMember(Advancement::write, Advancement::read);

    public Advancement(
        Optional<Identifier> parent,
        Optional<DisplayInfo> display,
        AdvancementRewards rewards,
        Map<String, Criterion<?>> criteria,
        AdvancementRequirements requirements,
        boolean sendsTelemetryEvent
    ) {
        this(parent, display, rewards, Map.copyOf(criteria), requirements, sendsTelemetryEvent, display.map(Advancement::decorateName));
    }

    private static DataResult<Advancement> validate(Advancement advancement) {
        return advancement.requirements().validate(advancement.criteria().keySet()).map(requirements -> advancement);
    }

    public static Component decorateName(DisplayInfo display) {
        Component title = display.getTitle();
        ChatFormatting chatColor = display.getType().getChatColor();
        Component component = ComponentUtils.mergeStyles(title.copy(), Style.EMPTY.withColor(chatColor)).append("\n").append(display.getDescription());
        Component component1 = title.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(component)));
        return ComponentUtils.wrapInSquareBrackets(component1).withStyle(chatColor);
    }

    public static Component name(AdvancementHolder advancement) {
        return advancement.value().name().orElseGet(() -> Component.literal(advancement.id().toString()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeOptional(this.parent, FriendlyByteBuf::writeIdentifier);
        DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buffer, this.display);
        this.requirements.write(buffer);
        buffer.writeBoolean(this.sendsTelemetryEvent);
    }

    private static Advancement read(RegistryFriendlyByteBuf buffer) {
        return new Advancement(
            buffer.readOptional(FriendlyByteBuf::readIdentifier),
            (Optional<DisplayInfo>)DisplayInfo.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buffer),
            AdvancementRewards.EMPTY,
            Map.of(),
            new AdvancementRequirements(buffer),
            buffer.readBoolean()
        );
    }

    public boolean isRoot() {
        return this.parent.isEmpty();
    }

    public void validate(ProblemReporter reporter, HolderGetter.Provider lootData) {
        this.criteria.forEach((string, criterion) -> {
            CriterionValidator criterionValidator = new CriterionValidator(reporter.forChild(new ProblemReporter.RootFieldPathElement(string)), lootData);
            criterion.triggerInstance().validate(criterionValidator);
        });
    }

    public static class Builder {
        private Optional<Identifier> parent = Optional.empty();
        private Optional<DisplayInfo> display = Optional.empty();
        private AdvancementRewards rewards = AdvancementRewards.EMPTY;
        private final ImmutableMap.Builder<String, Criterion<?>> criteria = ImmutableMap.builder();
        private Optional<AdvancementRequirements> requirements = Optional.empty();
        private AdvancementRequirements.Strategy requirementsStrategy = AdvancementRequirements.Strategy.AND;
        private boolean sendsTelemetryEvent;

        public static Advancement.Builder advancement() {
            return new Advancement.Builder().sendsTelemetryEvent();
        }

        public static Advancement.Builder recipeAdvancement() {
            return new Advancement.Builder();
        }

        public Advancement.Builder parent(AdvancementHolder parent) {
            this.parent = Optional.of(parent.id());
            return this;
        }

        @Deprecated(forRemoval = true)
        public Advancement.Builder parent(Identifier parentId) {
            this.parent = Optional.of(parentId);
            return this;
        }

        public Advancement.Builder display(
            ItemStack icon,
            Component title,
            Component description,
            @Nullable Identifier background,
            AdvancementType type,
            boolean showToast,
            boolean announceChat,
            boolean hidden
        ) {
            return this.display(
                new DisplayInfo(
                    icon, title, description, Optional.ofNullable(background).map(ClientAsset.ResourceTexture::new), type, showToast, announceChat, hidden
                )
            );
        }

        public Advancement.Builder display(
            ItemLike icon,
            Component title,
            Component description,
            @Nullable Identifier background,
            AdvancementType type,
            boolean showToast,
            boolean announceChat,
            boolean hidden
        ) {
            return this.display(
                new DisplayInfo(
                    new ItemStack(icon.asItem()),
                    title,
                    description,
                    Optional.ofNullable(background).map(ClientAsset.ResourceTexture::new),
                    type,
                    showToast,
                    announceChat,
                    hidden
                )
            );
        }

        public Advancement.Builder display(DisplayInfo display) {
            this.display = Optional.of(display);
            return this;
        }

        public Advancement.Builder rewards(AdvancementRewards.Builder rewardsBuilder) {
            return this.rewards(rewardsBuilder.build());
        }

        public Advancement.Builder rewards(AdvancementRewards rewards) {
            this.rewards = rewards;
            return this;
        }

        public Advancement.Builder addCriterion(String key, Criterion<?> criterion) {
            this.criteria.put(key, criterion);
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements.Strategy requirementsStrategy) {
            this.requirementsStrategy = requirementsStrategy;
            return this;
        }

        public Advancement.Builder requirements(AdvancementRequirements requirements) {
            this.requirements = Optional.of(requirements);
            return this;
        }

        public Advancement.Builder sendsTelemetryEvent() {
            this.sendsTelemetryEvent = true;
            return this;
        }

        public AdvancementHolder build(Identifier id) {
            Map<String, Criterion<?>> map = this.criteria.buildOrThrow();
            AdvancementRequirements advancementRequirements = this.requirements.orElseGet(() -> this.requirementsStrategy.create(map.keySet()));
            return new AdvancementHolder(id, new Advancement(this.parent, this.display, this.rewards, map, advancementRequirements, this.sendsTelemetryEvent));
        }

        public AdvancementHolder save(Consumer<AdvancementHolder> output, String id) {
            AdvancementHolder advancementHolder = this.build(Identifier.parse(id));
            output.accept(advancementHolder);
            return advancementHolder;
        }
    }
}
