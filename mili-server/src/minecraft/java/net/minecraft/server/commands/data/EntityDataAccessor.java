package net.minecraft.server.commands.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

public class EntityDataAccessor implements DataAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(Component.translatable("commands.data.entity.invalid"));
    public static final Function<String, DataCommands.DataProvider> PROVIDER = argumentName -> new DataCommands.DataProvider() {
        @Override
        public DataAccessor access(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            return new EntityDataAccessor(EntityArgument.getEntity(context, argumentName));
        }

        @Override
        public ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> builder, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> action
        ) {
            return builder.then(Commands.literal("entity").then(action.apply(Commands.argument(argumentName, EntityArgument.entity()))));
        }
    };
    private final Entity entity;

    public EntityDataAccessor(Entity entity) {
        this.entity = entity;
    }

    @Override
    public void setData(CompoundTag other) throws CommandSyntaxException {
        if (this.entity instanceof Player) {
            throw ERROR_NO_PLAYERS.create();
        } else {
            UUID uuid = this.entity.getUUID();

            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.entity.problemPath(), LOGGER)) {
                this.entity.load(TagValueInput.create(scopedCollector, this.entity.registryAccess(), other));
                this.entity.setUUID(uuid);
                if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) itemEntity.levelCallback.onMove(); // Leaves - Lithium Sleeping Block Entity
            }
        }
    }

    @Override
    public CompoundTag getData() {
        return NbtPredicate.getEntityTagToCompare(this.entity);
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.data.entity.modified", this.entity.getDisplayName());
    }

    @Override
    public Component getPrintSuccess(Tag tag) {
        return Component.translatable("commands.data.entity.query", this.entity.getDisplayName(), NbtUtils.toPrettyComponent(tag));
    }

    @Override
    public Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int value) {
        return Component.translatable(
            "commands.data.entity.get", path.asString(), this.entity.getDisplayName(), String.format(Locale.ROOT, "%.2f", scale), value
        );
    }
}
