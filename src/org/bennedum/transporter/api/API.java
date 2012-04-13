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

import java.util.HashSet;
import java.util.Set;
import org.bennedum.transporter.Endpoints;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.Servers;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class API {
    
    public Set<RemotePlayer> getRemotePlayers() {
        Set<RemotePlayer> players = new HashSet<RemotePlayer>();
        for (Server server : Servers.getAll())
            players.addAll(server.getRemotePlayers());
        return players;
    }
    
    public Set<LocalGate> getLocalGates() {
        return new HashSet<LocalGate>(Endpoints.getLocalGates());
    }
    
    public Set<RemoteGate> getRemoteGates() {
        return new HashSet<RemoteGate>(Endpoints.getRemoteGates());
    }
    
    public Set<LocalVolume> getLocalVolumes() {
        return new HashSet<LocalVolume>(Endpoints.getLocalVolumes());
    }
    
    public Set<RemoteVolume> getRemoteVolumes() {
        return new HashSet<RemoteVolume>(Endpoints.getRemoteVolumes());
    }
    
    public Set<RemoteServer> getRemoteServers() {
        return new HashSet<RemoteServer>(Servers.getAll());
    }
    
    // TODO: add teleport methods
    
}
