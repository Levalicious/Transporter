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
package org.bennedum.transporter.api;

import java.net.InetSocketAddress;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public interface RemoteServer {
    
    public String getName();
    
    public boolean isConnected();
    
    public Set<RemotePlayer> getRemotePlayers();
    
    public Set<RemoteWorld> getRemoteWorlds();
    
    public Set<RemoteGate> getRemoteGates();
 
    public RemoteWorld getRemoteWorld(String worldName);
    
    public RemoteGate getRemoteGate(String name);
    
    public void broadcast(Callback<Integer> cb, String message, String permission);
    
    public void broadcastMessage(Callback<Integer> cb, String message);
    
    public void dispatchCommand(Callback<Boolean> cb, CommandSender sender, String commandLine);
    
    public void getDefaultGameMode(Callback<GameMode> cb);

    public void getName(Callback<String> cb);
    
    public void getServerId(Callback<String> cb);
    
    public void getVersion(Callback<String> cb);
    
    /* Options */
    
    public String getKey();
    public void setKey(String key);
    public boolean isEnabled();
    public void setEnabled(boolean en);
    public String getPublicAddress();
    public void setPublicAddress(String address);
    public String getPrivateAddress();
    public void setPrivateAddress(String address);
    public boolean getSendChat();
    public void setSendChat(boolean b);
    public boolean getReceiveChat();
    public void setReceiveChat(boolean b);
    public boolean getAnnouncePlayers();
    public void setAnnouncePlayers(boolean b);

    /* End Options */
        
}
