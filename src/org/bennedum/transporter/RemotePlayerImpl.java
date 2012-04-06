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

import org.bennedum.transporter.api.Callback;
import org.bennedum.transporter.api.RemoteLocation;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RemotePlayerImpl implements RemotePlayer {

    private Server server;
    private String name;
    private String displayName;
    private String worldName;
    
    public RemotePlayerImpl(Server server, String name, String displayName, String worldName) {
        this.server = server;
        if (name == null) throw new IllegalArgumentException("name is required");
        this.name = name;
        if (displayName == null) displayName = name;
        this.displayName = displayName;
        setWorld(worldName);
        this.worldName = worldName;
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public RemoteWorld getWorld() {
        return server.getRemoteWorld(worldName);
    }

    public void setWorld(String worldName) {
        if (worldName == null) throw new IllegalArgumentException("worldName is required");
        this.worldName = worldName;
    }
    
    public void setWorld(RemoteWorld world) {
        worldName = world.getName();
    }
    
    @Override
    public RemoteServer getServer() {
        return server;
    }

    @Override
    public void getLocation(Callback<RemoteLocation> cb) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void sendMessage(Callback<Void> cb, String msg) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void sendRawMessage(Callback<Void> cb, String msg) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
