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

import org.bennedum.transporter.api.LocalGate;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class LocalGateCreateEvent extends Event {
    
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return handlers;
    }    
    
    private LocalGate gate;
    
    public LocalGateCreateEvent(LocalGate gate) {
        this.gate = gate;
    }
 
    public LocalGate getLocalGate() {
        return gate;
    }
 
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
 
}
