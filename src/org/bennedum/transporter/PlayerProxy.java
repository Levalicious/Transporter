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

import org.bennedum.transporter.net.Message;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerProxy {
    
    private Player player = null;
    
    // these fields are only used when player is null
    private String name = null;
    private String displayName = null;
    private String serverName = null;
    private String worldName = null;
    
    public PlayerProxy(Player player) {
        this.player = player;
    }
    
    public PlayerProxy(String name, String displayName, String serverName, String worldName) {
        player = Global.plugin.getServer().getPlayer(name);
        if (player == null) {
            this.name = name;
            this.displayName = displayName;
            this.serverName = serverName;
            this.worldName = worldName;
        }
    }
    
    public PlayerProxy(Server server, Message m) throws TransporterException {
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
    
    public boolean isLocal() {
        return player != null;
    }
    
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
        }
    }

}
