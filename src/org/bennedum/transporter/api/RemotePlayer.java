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

import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public interface RemotePlayer {
    
    public String getName();
    
    public String getDisplayName();
    
    public RemoteWorld getRemoteWorld();
    
    public RemoteServer getRemoteServer();

    public void getRemoteWorld(Callback<RemoteWorld> cb);
    
    public void getRemoteLocation(Callback<RemoteLocation> cb);
    
    public void sendMessage(Callback<Void> cb, String msg);
    
    public void sendRawMessage(Callback<Void> cb, String msg);
    
    public void sendPM(Player fromPlayer, String message);
    
}
