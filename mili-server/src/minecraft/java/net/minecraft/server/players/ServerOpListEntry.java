package net.minecraft.server.players;

import com.google.gson.JsonObject;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

public class ServerOpListEntry extends StoredUserEntry<NameAndId> {
    private final LevelBasedPermissionSet permissions;
    private final boolean bypassesPlayerLimit;

    public ServerOpListEntry(NameAndId user, LevelBasedPermissionSet permissions, boolean bypassesPlayerLimit) {
        super(user);
        this.permissions = permissions;
        this.bypassesPlayerLimit = bypassesPlayerLimit;
    }

    public ServerOpListEntry(JsonObject user) {
        super(NameAndId.fromJson(user));
        PermissionLevel permissionLevel = user.has("level") ? PermissionLevel.byId(user.get("level").getAsInt()) : PermissionLevel.ALL;
        this.permissions = LevelBasedPermissionSet.forLevel(permissionLevel);
        this.bypassesPlayerLimit = user.has("bypassesPlayerLimit") && user.get("bypassesPlayerLimit").getAsBoolean();
    }

    public LevelBasedPermissionSet permissions() {
        return this.permissions;
    }

    public boolean getBypassesPlayerLimit() {
        return this.bypassesPlayerLimit;
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            this.getUser().appendTo(data);
            data.addProperty("level", this.permissions.level().id());
            data.addProperty("bypassesPlayerLimit", this.bypassesPlayerLimit);
        }
    }
}
