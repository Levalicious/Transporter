/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bennedum.transporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class SuperPermsPrincipal implements Permissible {

    private String name;
    private String worldName;
    private Map<String, PermissionAttachmentInfo> permissions = new HashMap<String, PermissionAttachmentInfo>();
    
    public SuperPermsPrincipal(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
    }
    
    @Override
    public boolean isPermissionSet(String name) {
        if (name == null)
            throw new IllegalArgumentException("Permission name cannot be null");
        return permissions.containsKey(name.toLowerCase());        
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        if (perm == null)
            throw new IllegalArgumentException("Permission cannot be null");
        return isPermissionSet(perm.getName());        
    }

    @Override
    public boolean hasPermission(String inName) {
        if (inName == null)
            throw new IllegalArgumentException("Permission name cannot be null");
        String lName = inName.toLowerCase();
        if (isPermissionSet(lName))
            return permissions.get(lName).getValue();
        else {
            Permission perm = Global.plugin.getServer().getPluginManager().getPermission(lName);
            if (perm != null)
                return perm.getDefault().getValue(isOp());
            else
                return Permission.DEFAULT_PERMISSION.getValue(isOp());
        }
    }

    @Override
    public boolean hasPermission(Permission perm) {
        if (perm == null)
            throw new IllegalArgumentException("Permission cannot be null");
        String lName = perm.getName().toLowerCase();
        if (isPermissionSet(lName))
            return permissions.get(lName).getValue();
        return perm.getDefault().getValue(isOp());        
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String string, boolean bln) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String string, boolean bln, int i) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int i) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void removeAttachment(PermissionAttachment pa) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void recalculatePermissions() {
        permissions.clear();
        Set<Permission> defaults = Global.plugin.getServer().getPluginManager().getDefaultPermissions(isOp());
        for (Permission perm : defaults) {
            String lName = perm.getName().toLowerCase();
            permissions.put(lName, new PermissionAttachmentInfo(this, lName, null, true));
            calculateChildPermissions(perm.getChildren(), false, null);
        }
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return new HashSet<PermissionAttachmentInfo>(permissions.values());
    }

    @Override
    public boolean isOp() {
        return Permissions.isOp(name);
    }

    @Override
    public void setOp(boolean bln) {
        throw new UnsupportedOperationException("Not supported.");
    }
    
    private void calculateChildPermissions(Map<String, Boolean> children, boolean invert, PermissionAttachment attachment) {
        Set<String> keys = children.keySet();
        for (String kName : keys) {
            Permission perm = Global.plugin.getServer().getPluginManager().getPermission(kName);
            boolean value = children.get(kName) ^ invert;
            String lName = kName.toLowerCase();
            permissions.put(lName, new PermissionAttachmentInfo(this, lName, attachment, value));
            if (perm != null)
                calculateChildPermissions(perm.getChildren(), !value, attachment);
        }
    }    
    
}