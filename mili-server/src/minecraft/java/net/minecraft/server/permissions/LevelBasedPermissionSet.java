package net.minecraft.server.permissions;

public interface LevelBasedPermissionSet extends PermissionSet {
    @Deprecated
    LevelBasedPermissionSet ALL = create(PermissionLevel.ALL);
    LevelBasedPermissionSet MODERATOR = create(PermissionLevel.MODERATORS);
    LevelBasedPermissionSet GAMEMASTER = create(PermissionLevel.GAMEMASTERS);
    LevelBasedPermissionSet ADMIN = create(PermissionLevel.ADMINS);
    LevelBasedPermissionSet OWNER = create(PermissionLevel.OWNERS);

    PermissionLevel level();

    @Override
    default boolean hasPermission(Permission permission) {
        return permission instanceof Permission.HasCommandLevel hasCommandLevel
            ? this.level().isEqualOrHigherThan(hasCommandLevel.level())
            : permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS) && this.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    @Override
    default PermissionSet union(PermissionSet permissions) {
        if (permissions instanceof LevelBasedPermissionSet levelBasedPermissionSet) {
            return this.level().isEqualOrHigherThan(levelBasedPermissionSet.level()) ? levelBasedPermissionSet : this;
        } else {
            return PermissionSet.super.union(permissions);
        }
    }

    static LevelBasedPermissionSet forLevel(PermissionLevel level) {
        return switch (level) {
            case ALL -> ALL;
            case MODERATORS -> MODERATOR;
            case GAMEMASTERS -> GAMEMASTER;
            case ADMINS -> ADMIN;
            case OWNERS -> OWNER;
        };
    }

    private static LevelBasedPermissionSet create(final PermissionLevel level) {
        return new LevelBasedPermissionSet() {
            @Override
            public PermissionLevel level() {
                return level;
            }

            @Override
            public String toString() {
                return "permission level: " + level.name();
            }
        };
    }
}
