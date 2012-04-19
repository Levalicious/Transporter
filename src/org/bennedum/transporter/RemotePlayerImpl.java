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
import org.bennedum.transporter.api.RemoteException;
import org.bennedum.transporter.api.RemoteLocation;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;
import org.bennedum.transporter.net.Message;

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
    public RemoteWorld getRemoteWorld() {
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
    public RemoteServer getRemoteServer() {
        return server;
    }

    @Override
    public void getRemoteWorld(final Callback<RemoteWorld> cb) {
        Message args = new Message();
        args.put("player", name);
        server.sendAPIRequest(new Callback<Message>() {
            @Override
            public void onSuccess(Message m) {
                cb.onSuccess(server.getRemoteWorld(m.getString("result")));
            }
            @Override
            public void onFailure(RemoteException re) {
                cb.onFailure(re);
            }
        }, "player", "getWorld", args);
    }
    
    @Override
    public void getRemoteLocation(final Callback<RemoteLocation> cb) {
        Message args = new Message();
        args.put("player", name);
        server.sendAPIRequest(new Callback<Message>() {
            @Override
            public void onSuccess(Message m) {
                Message locMsg = m.getMessage("result");
                RemoteLocation loc = new RemoteLocation(server, server.getRemoteWorld(locMsg.getString("world")), locMsg.getDouble("x"), locMsg.getDouble("y"), locMsg.getDouble("z"));
                cb.onSuccess(loc);
            }
            @Override
            public void onFailure(RemoteException re) {
                cb.onFailure(re);
            }
        }, "player", "getLocation", args);
    }

    @Override
    public void sendMessage(final Callback<Void> cb, String msg) {
        Message args = new Message();
        args.put("player", name);
        args.put("message", msg);
        server.sendAPIRequest(new Callback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(null);
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "player", "sendMessage", args);
    }

    @Override
    public void sendRawMessage(final Callback<Void> cb, String msg) {
        Message args = new Message();
        args.put("player", name);
        args.put("message", msg);
        server.sendAPIRequest(new Callback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(null);
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "player", "sendRawMessage", args);
    }
    
}
