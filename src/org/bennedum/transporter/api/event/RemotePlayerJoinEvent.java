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
package org.bennedum.transporter.api.event;

import org.bennedum.transporter.api.RemotePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RemotePlayerJoinEvent extends Event {
    
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return handlers;
    }    
    
    private RemotePlayer player;
    
    public RemotePlayerJoinEvent(RemotePlayer player) {
        this.player = player;
    }
 
    public RemotePlayer getRemotePlayer() {
        return player;
    }
 
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
 
}
