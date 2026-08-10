package net.minecraft.server.permissions;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public class PermissionSetUnion implements PermissionSet {
    private final ReferenceSet<PermissionSet> permissions = new ReferenceArraySet<>();

    PermissionSetUnion(PermissionSet first, PermissionSet second) {
        this.permissions.add(first);
        this.permissions.add(second);
        this.ensureNoUnionsWithinUnions();
    }

    private PermissionSetUnion(ReferenceSet<PermissionSet> oldPermissions, PermissionSet other) {
        this.permissions.addAll(oldPermissions);
        this.permissions.add(other);
        this.ensureNoUnionsWithinUnions();
    }

    private PermissionSetUnion(ReferenceSet<PermissionSet> oldPermissions, ReferenceSet<PermissionSet> other) {
        this.permissions.addAll(oldPermissions);
        this.permissions.addAll(other);
        this.ensureNoUnionsWithinUnions();
    }

    @Override
    public boolean hasPermission(Permission permission) {
        for (PermissionSet permissionSet : this.permissions) {
            if (permissionSet.hasPermission(permission)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public PermissionSet union(PermissionSet permissions) {
        return permissions instanceof PermissionSetUnion permissionSetUnion
            ? new PermissionSetUnion(this.permissions, permissionSetUnion.permissions)
            : new PermissionSetUnion(this.permissions, permissions);
    }

    @VisibleForTesting
    public ReferenceSet<PermissionSet> getPermissions() {
        return new ReferenceArraySet<>(this.permissions);
    }

    private void ensureNoUnionsWithinUnions() {
        for (PermissionSet permissionSet : this.permissions) {
            if (permissionSet instanceof PermissionSetUnion) {
                throw new IllegalArgumentException("Cannot have PermissionSetUnion within another PermissionSetUnion");
            }
        }
    }
}
