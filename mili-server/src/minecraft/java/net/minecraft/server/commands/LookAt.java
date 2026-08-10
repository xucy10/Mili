package net.minecraft.server.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface LookAt {
    void perform(CommandSourceStack source, Entity entity);

    public record LookAtEntity(Entity entity, EntityAnchorArgument.Anchor anchor) implements LookAt {
        @Override
        public void perform(CommandSourceStack source, Entity entity) {
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.lookAt(source.getAnchor(), this.entity, this.anchor);
            } else {
                entity.lookAt(source.getAnchor(), this.anchor.apply(this.entity));
            }
        }
    }

    public record LookAtPosition(Vec3 position) implements LookAt {
        @Override
        public void perform(CommandSourceStack source, Entity entity) {
            entity.lookAt(source.getAnchor(), this.position);
        }
    }
}
