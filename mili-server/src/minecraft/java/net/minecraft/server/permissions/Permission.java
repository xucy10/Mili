package net.minecraft.server.permissions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public interface Permission {
    Codec<Permission> FULL_CODEC = BuiltInRegistries.PERMISSION_TYPE.byNameCodec().dispatch(Permission::codec, mapCodec -> mapCodec);
    Codec<Permission> CODEC = Codec.either(FULL_CODEC, Identifier.CODEC)
        .xmap(
            either -> either.map(permission -> (Permission)permission, Permission.Atom::create),
            permission -> permission instanceof Permission.Atom atom ? Either.right(atom.id()) : Either.left(permission)
        );

    MapCodec<? extends Permission> codec();

    public record Atom(Identifier id) implements Permission {
        public static final MapCodec<Permission.Atom> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Identifier.CODEC.fieldOf("id").forGetter(Permission.Atom::id)).apply(instance, Permission.Atom::new)
        );

        @Override
        public MapCodec<Permission.Atom> codec() {
            return MAP_CODEC;
        }

        public static Permission.Atom create(String name) {
            return create(Identifier.withDefaultNamespace(name));
        }

        public static Permission.Atom create(Identifier id) {
            return new Permission.Atom(id);
        }
    }

    public record HasCommandLevel(PermissionLevel level) implements Permission {
        public static final MapCodec<Permission.HasCommandLevel> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(PermissionLevel.CODEC.fieldOf("level").forGetter(Permission.HasCommandLevel::level))
                .apply(instance, Permission.HasCommandLevel::new)
        );

        @Override
        public MapCodec<Permission.HasCommandLevel> codec() {
            return MAP_CODEC;
        }
    }
}
