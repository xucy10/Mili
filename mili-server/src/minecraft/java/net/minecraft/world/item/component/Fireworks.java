package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record Fireworks(int flightDuration, List<FireworkExplosion> explosions) implements TooltipProvider {
    public static final int MAX_EXPLOSIONS = 256;
    public static final Codec<Fireworks> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration", 0).forGetter(Fireworks::flightDuration),
                FireworkExplosion.CODEC.sizeLimitedListOf(256).optionalFieldOf("explosions", List.of()).forGetter(Fireworks::explosions)
            )
            .apply(instance, Fireworks::new)
    );
    public static final StreamCodec<ByteBuf, Fireworks> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, Fireworks::flightDuration, FireworkExplosion.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Fireworks::explosions, Fireworks::new
    );

    public Fireworks(int flightDuration, List<FireworkExplosion> explosions) {
        if (explosions.size() > 256) {
            throw new IllegalArgumentException("Got " + explosions.size() + " explosions, but maximum is 256");
        } else {
            this.flightDuration = flightDuration;
            this.explosions = explosions;
        }
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        if (this.flightDuration > 0) {
            tooltipAdder.accept(
                Component.translatable("item.minecraft.firework_rocket.flight")
                    .append(CommonComponents.SPACE)
                    .append(String.valueOf(this.flightDuration))
                    .withStyle(ChatFormatting.GRAY)
            );
        }

        FireworkExplosion fireworkExplosion = null;
        int i = 0;

        for (FireworkExplosion fireworkExplosion1 : this.explosions) {
            if (fireworkExplosion == null) {
                fireworkExplosion = fireworkExplosion1;
                i = 1;
            } else if (fireworkExplosion.equals(fireworkExplosion1)) {
                i++;
            } else {
                addExplosionTooltip(tooltipAdder, fireworkExplosion, i);
                fireworkExplosion = fireworkExplosion1;
                i = 1;
            }
        }

        if (fireworkExplosion != null) {
            addExplosionTooltip(tooltipAdder, fireworkExplosion, i);
        }
    }

    private static void addExplosionTooltip(Consumer<Component> tooltipAdder, FireworkExplosion explosion, int count) {
        Component name = explosion.shape().getName();
        if (count == 1) {
            tooltipAdder.accept(Component.translatable("item.minecraft.firework_rocket.single_star", name).withStyle(ChatFormatting.GRAY));
        } else {
            tooltipAdder.accept(Component.translatable("item.minecraft.firework_rocket.multiple_stars", count, name).withStyle(ChatFormatting.GRAY));
        }

        explosion.addAdditionalTooltip(component -> tooltipAdder.accept(Component.literal("  ").append(component)));
    }
}
