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
        setRemoteServer(serverName);
        setRemoteWorld(worldName);
        setX(x);
        setY(y);
        setZ(z);
    }
    
    public RemoteLocation(RemoteServer server, RemoteWorld world, double x, double y, double z) {
        setRemoteServer(server);
        setRemoteWorld(world);
        setX(x);
        setY(y);
        setZ(z);
    }
    
    public RemoteServer getRemoteServer() {
        return server;
    }
    
    private void setRemoteServer(RemoteServer server) {
        if (server == null) throw new IllegalArgumentException("server is required");
        this.server = server;
    }
    
    private void setRemoteServer(String serverName) {
        server = Servers.getRemoteServer(serverName);
    }
    
    public RemoteWorld getRemoteWorld() {
        return world;
    }
    
    private void setRemoteWorld(RemoteWorld world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        this.world = world;
    }
    
    private void setRemoteWorld(String worldName) {
        world = server.getRemoteWorld(worldName);
    }
    
    public double getX() {
        return x;
    }
    
    private void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    private void setY(double y) {
        this.y = y;
    }
    
    public double getZ() {
        return z;
    }
    
    private void setZ(double z) {
        this.z = z;
    }
    
}
