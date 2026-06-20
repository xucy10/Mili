package net.minecraft.server.permissions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;

public interface PermissionCheck {
    Codec<PermissionCheck> CODEC = BuiltInRegistries.PERMISSION_CHECK_TYPE.byNameCodec().dispatch(PermissionCheck::codec, mapCodec -> mapCodec);

    boolean check(PermissionSet permissions);

    MapCodec<? extends PermissionCheck> codec();

    public static class AlwaysPass implements PermissionCheck {
        public static final PermissionCheck.AlwaysPass INSTANCE = new PermissionCheck.AlwaysPass();
        public static final MapCodec<PermissionCheck.AlwaysPass> MAP_CODEC = MapCodec.unit(INSTANCE);

        private AlwaysPass() {
        }

        @Override
        public boolean check(PermissionSet permissions) {
            return true;
        }

        @Override
        public MapCodec<PermissionCheck.AlwaysPass> codec() {
            return MAP_CODEC;
        }
    }

    public record Require(Permission permission) implements PermissionCheck {
        public static final MapCodec<PermissionCheck.Require> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Permission.CODEC.fieldOf("permission").forGetter(PermissionCheck.Require::permission))
                .apply(instance, PermissionCheck.Require::new)
        );

        @Override
        public MapCodec<PermissionCheck.Require> codec() {
            return MAP_CODEC;
        }

        @Override
        public boolean check(PermissionSet permissions) {
            return permissions.hasPermission(this.permission);
        }
    }
}
