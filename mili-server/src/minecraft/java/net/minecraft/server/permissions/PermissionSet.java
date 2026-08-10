package net.minecraft.server.permissions;

public interface PermissionSet {
    PermissionSet NO_PERMISSIONS = permission -> false;
    PermissionSet ALL_PERMISSIONS = permission -> true;

    boolean hasPermission(Permission permission);

    default PermissionSet union(PermissionSet permissions) {
        return (PermissionSet)(permissions instanceof PermissionSetUnion ? permissions.union(this) : new PermissionSetUnion(this, permissions));
    }
}
