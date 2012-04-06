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

import org.bennedum.transporter.Servers;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RemoteLocation {
    
    private RemoteServer server;
    private RemoteWorld world;
    private double x;
    private double y;
    private double z;
    
    public RemoteLocation(String serverName, String worldName, double x, double y, double z) {
        setServer(serverName);
        setWorld(worldName);
        setX(x);
        setY(y);
        setZ(z);
    }
    
    public RemoteLocation(RemoteServer server, RemoteWorld world, double x, double y, double z) {
        setServer(server);
        setWorld(world);
        setX(x);
        setY(y);
        setZ(z);
    }
    
    public RemoteServer getServer() {
        return server;
    }
    
    public void setServer(RemoteServer server) {
        if (server == null) throw new IllegalArgumentException("server is required");
        this.server = server;
    }
    
    public void setServer(String serverName) {
        server = Servers.getRemoteServer(serverName);
    }
    
    public RemoteWorld getWorld() {
        return world;
    }
    
    public void setWorld(RemoteWorld world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        this.world = world;
    }
    
    public void setWorld(String worldName) {
        world = server.getRemoteWorld(worldName);
    }
    
    public double getX() {
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
    }
    
    public double getZ() {
        return z;
    }
    
    public void setZ(double z) {
        this.z = z;
    }
    
}
