package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jspecify.annotations.Nullable;

public record EntityEquipmentPredicate(
    Optional<ItemPredicate> head,
    Optional<ItemPredicate> chest,
    Optional<ItemPredicate> legs,
    Optional<ItemPredicate> feet,
    Optional<ItemPredicate> body,
    Optional<ItemPredicate> mainhand,
    Optional<ItemPredicate> offhand
) {
    public static final Codec<EntityEquipmentPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ItemPredicate.CODEC.optionalFieldOf("head").forGetter(EntityEquipmentPredicate::head),
                ItemPredicate.CODEC.optionalFieldOf("chest").forGetter(EntityEquipmentPredicate::chest),
                ItemPredicate.CODEC.optionalFieldOf("legs").forGetter(EntityEquipmentPredicate::legs),
                ItemPredicate.CODEC.optionalFieldOf("feet").forGetter(EntityEquipmentPredicate::feet),
                ItemPredicate.CODEC.optionalFieldOf("body").forGetter(EntityEquipmentPredicate::body),
                ItemPredicate.CODEC.optionalFieldOf("mainhand").forGetter(EntityEquipmentPredicate::mainhand),
                ItemPredicate.CODEC.optionalFieldOf("offhand").forGetter(EntityEquipmentPredicate::offhand)
            )
            .apply(instance, EntityEquipmentPredicate::new)
    );

    public static EntityEquipmentPredicate captainPredicate(HolderGetter<Item> itemRegistry, HolderGetter<BannerPattern> patternRegistry) {
        return EntityEquipmentPredicate.Builder.equipment()
            .head(
                ItemPredicate.Builder.item()
                    .of(itemRegistry, Items.WHITE_BANNER)
                    .withComponents(
                        DataComponentMatchers.Builder.components()
                            .exact(
                                DataComponentExactPredicate.someOf(
                                    Raid.getOminousBannerInstance(patternRegistry).getComponents(), DataComponents.BANNER_PATTERNS, DataComponents.ITEM_NAME
                                )
                            )
                            .build()
                    )
            )
            .build();
    }

    public boolean matches(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity
            && (!this.head.isPresent() || this.head.get().test(livingEntity.getItemBySlot(EquipmentSlot.HEAD)))
            && (!this.chest.isPresent() || this.chest.get().test(livingEntity.getItemBySlot(EquipmentSlot.CHEST)))
            && (!this.legs.isPresent() || this.legs.get().test(livingEntity.getItemBySlot(EquipmentSlot.LEGS)))
            && (!this.feet.isPresent() || this.feet.get().test(livingEntity.getItemBySlot(EquipmentSlot.FEET)))
            && (!this.body.isPresent() || this.body.get().test(livingEntity.getItemBySlot(EquipmentSlot.BODY)))
            && (!this.mainhand.isPresent() || this.mainhand.get().test(livingEntity.getItemBySlot(EquipmentSlot.MAINHAND)))
            && (!this.offhand.isPresent() || this.offhand.get().test(livingEntity.getItemBySlot(EquipmentSlot.OFFHAND)));
    }

    public static class Builder {
        private Optional<ItemPredicate> head = Optional.empty();
        private Optional<ItemPredicate> chest = Optional.empty();
        private Optional<ItemPredicate> legs = Optional.empty();
        private Optional<ItemPredicate> feet = Optional.empty();
        private Optional<ItemPredicate> body = Optional.empty();
        private Optional<ItemPredicate> mainhand = Optional.empty();
        private Optional<ItemPredicate> offhand = Optional.empty();

        public static EntityEquipmentPredicate.Builder equipment() {
            return new EntityEquipmentPredicate.Builder();
        }

        public EntityEquipmentPredicate.Builder head(ItemPredicate.Builder head) {
            this.head = Optional.of(head.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder chest(ItemPredicate.Builder chest) {
            this.chest = Optional.of(chest.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder legs(ItemPredicate.Builder legs) {
            this.legs = Optional.of(legs.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder feet(ItemPredicate.Builder feet) {
            this.feet = Optional.of(feet.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder body(ItemPredicate.Builder body) {
            this.body = Optional.of(body.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder mainhand(ItemPredicate.Builder mainhand) {
            this.mainhand = Optional.of(mainhand.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder offhand(ItemPredicate.Builder offhand) {
            this.offhand = Optional.of(offhand.build());
            return this;
        }

        public EntityEquipmentPredicate build() {
            return new EntityEquipmentPredicate(this.head, this.chest, this.legs, this.feet, this.body, this.mainhand, this.offhand);
        }
    }
}
