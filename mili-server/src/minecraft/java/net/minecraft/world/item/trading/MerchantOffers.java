package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class MerchantOffers extends ArrayList<MerchantOffer> {
    public static final Codec<MerchantOffers> CODEC = MerchantOffer.CODEC
        .listOf()
        .optionalFieldOf("Recipes", List.of())
        .xmap(MerchantOffers::new, Function.identity())
        .codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffers> STREAM_CODEC = MerchantOffer.STREAM_CODEC
        .apply(ByteBufCodecs.collection(MerchantOffers::new));

    public MerchantOffers() {
    }

    private MerchantOffers(int size) {
        super(size);
    }

    private MerchantOffers(Collection<MerchantOffer> offers) {
        super(offers);
    }

    public @Nullable MerchantOffer getRecipeFor(ItemStack stackA, ItemStack stackB, int index) {
        if (index > 0 && index < this.size()) {
            MerchantOffer merchantOffer = this.get(index);
            return merchantOffer.satisfiedBy(stackA, stackB) ? merchantOffer : null;
        } else {
            for (int i = 0; i < this.size(); i++) {
                MerchantOffer merchantOffer1 = this.get(i);
                if (merchantOffer1.satisfiedBy(stackA, stackB)) {
                    return merchantOffer1;
                }
            }

            return null;
        }
    }

    public MerchantOffers copy() {
        MerchantOffers list = new MerchantOffers(this.size());

        for (MerchantOffer merchantOffer : this) {
            list.add(merchantOffer.copy());
        }

        return list;
    }
}
