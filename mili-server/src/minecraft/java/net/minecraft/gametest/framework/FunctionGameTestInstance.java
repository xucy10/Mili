package net.minecraft.gametest.framework;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;

public class FunctionGameTestInstance extends GameTestInstance {
    public static final MapCodec<FunctionGameTestInstance> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.TEST_FUNCTION).fieldOf("function").forGetter(FunctionGameTestInstance::function),
                TestData.CODEC.forGetter(GameTestInstance::info)
            )
            .apply(instance, FunctionGameTestInstance::new)
    );
    private final ResourceKey<Consumer<GameTestHelper>> function;

    public FunctionGameTestInstance(ResourceKey<Consumer<GameTestHelper>> function, TestData<Holder<TestEnvironmentDefinition>> info) {
        super(info);
        this.function = function;
    }

    @Override
    public void run(GameTestHelper helper) {
        helper.getLevel()
            .registryAccess()
            .get(this.function)
            .map(Holder.Reference::value)
            .orElseThrow(() -> new IllegalStateException("Trying to access missing test function: " + this.function.identifier()))
            .accept(helper);
    }

    private ResourceKey<Consumer<GameTestHelper>> function() {
        return this.function;
    }

    @Override
    public MapCodec<FunctionGameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.translatable("test_instance.type.function");
    }

    @Override
    public Component describe() {
        return this.describeType()
            .append(this.descriptionRow("test_instance.description.function", this.function.identifier().toString()))
            .append(this.describeInfo());
    }
}
