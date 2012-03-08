/*
 * Copyright 2011 frdfsnlght <frdfsnlght@gmail.com>.
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

import java.util.Set;
import org.bennedum.transporter.net.Message;
import org.bukkit.entity.Player;
import org.bukkit.permissions.*;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerProxy implements Permissible {
    
    private Player player = null;
    
    // these fields are only used when player is null
    private String name;
    private String displayName;
    private String serverName;
    private String worldName;
    private PermissibleBase perm = null;
    
    public PlayerProxy(Player player) {
        setPlayer(player);
    }
    
    public PlayerProxy(String name, String displayName, String serverName, String worldName) {
        Player p = Global.plugin.getServer().getPlayer(name);
        if (p == null) {
            this.name = name;
            this.displayName = displayName;
            this.serverName = serverName;
            this.worldName = worldName;
        } else
            setPlayer(p);
    }
    
    public PlayerProxy(Server server, Message m) throws TransporterException {
        setPlayer(null);
        name = m.getString("name");
        if (name == null)
            throw new TransporterException("missing name");
        displayName = m.getString("displayName");
        if (displayName == null)
            throw new TransporterException("missing displayName");
        worldName = m.getString("world");
        if (worldName == null)
            throw new TransporterException("missing world");
        serverName = server.getName();
    }
    
    public void setPlayer(Player player) {
        this.player = player;
        if (player == null)
            perm = new PermissibleBase(this);
        else
            perm = null;
    }
    
    public void destroy() {
        if (perm == null) return;

        // This is a hack to emulate what the Bukkit class BasePermissible does since the clearPermissions method is private.
        for (PermissionAttachmentInfo info : perm.getEffectivePermissions())
            Global.plugin.getServer().getPluginManager().unsubscribeFromPermission(info.getPermission(), this);

        /*
        Set<Permission> defaults = Global.plugin.getServer().getPluginManager().getDefaultPermissions(isOp());
        for (Permission dPerm : defaults) {
            String permName = dPerm.getName().toLowerCase();
            Global.plugin.getServer().getPluginManager().unsubscribeFromPermission(permName, this);
            unsubscribeChildren(dPerm.getChildren(), false);
        } 
        * 
        */
        Global.plugin.getServer().getPluginManager().unsubscribeFromDefaultPerms(false, this);
        Global.plugin.getServer().getPluginManager().unsubscribeFromDefaultPerms(true, this);
    }
    
    /*
    private void unsubscribeChildren(Map<String, Boolean> children, boolean invert) {
        Set<String> keys = children.keySet();
        for (String cName : keys) {
            Permission cPerm = Global.plugin.getServer().getPluginManager().getPermission(cName);
            boolean value = children.get(cName) ^ invert;
            Global.plugin.getServer().getPluginManager().subscribeToPermission(cName, this);
            if (cPerm != null)
                unsubscribeChildren(cPerm.getChildren(), !value);
        }
    }
    * 
    */

    public Message encode() {
        Message m = new Message();
        m.put("name", getName());
        m.put("displayName", getDisplayName());
        m.put("world", getWorldName());
        return m;
    }
    
    public String getName() {
        if (player == null) return name;
        return player.getName();
    }
    
    public String getDisplayName() {
        if (player == null) return displayName;
        return player.getDisplayName();
    }
    
    public String getServerName() {
        if (player == null) return serverName;
        return null;
    }
    
    public String getWorldName() {
        if (player == null) return worldName;
        if (player.getWorld() == null) return null;
        return player.getWorld().getName();
    }
    
    public void setWorldName(String worldName) {
        if (player == null) {
            this.worldName = worldName;
            perm.recalculatePermissions();
        }
    }

    // Permissible interface
    
    @Override
    public boolean isPermissionSet(String string) {
        if (player == null) return perm.isPermissionSet(string);
        else return player.isPermissionSet(string);
    }

    @Override
    public boolean isPermissionSet(Permission prmsn) {
        if (player == null) return perm.isPermissionSet(prmsn);
        else return player.isPermissionSet(prmsn);
    }

    @Override
    public boolean hasPermission(String string) {
        if (player == null) return perm.hasPermission(string);
        else return player.hasPermission(string);
    }

    @Override
    public boolean hasPermission(Permission prmsn) {
        if (player == null) return perm.hasPermission(prmsn);
        else return player.hasPermission(prmsn);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String string, boolean bln) {
        if (player == null) return perm.addAttachment(plugin, string, bln);
        else return player.addAttachment(plugin, string, bln);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        if (player == null) return perm.addAttachment(plugin);
        else return player.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String string, boolean bln, int i) {
        if (player == null) return perm.addAttachment(plugin, string, bln, i);
        else return player.addAttachment(plugin, string, bln, i);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int i) {
        if (player == null) return perm.addAttachment(plugin, i);
        else return player.addAttachment(plugin, i);
    }

    @Override
    public void removeAttachment(PermissionAttachment pa) {
        if (player == null) perm.removeAttachment(pa);
        else player.removeAttachment(pa);
    }

    @Override
    public void recalculatePermissions() {
        if (player == null) perm.recalculatePermissions();
        else player.recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        if (player == null) return perm.getEffectivePermissions();
        else return player.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return Permissions.isOp(name);
    }

    @Override
    public void setOp(boolean bln) {
        // Do nothing
    }

    // End Permissible interface
    
}
