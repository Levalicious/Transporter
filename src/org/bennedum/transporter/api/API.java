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
import org.bennedum.transporter.Config;
import org.bennedum.transporter.GateImpl;
import org.bennedum.transporter.Gates;
import org.bennedum.transporter.LocalGateImpl;
import org.bennedum.transporter.ReservationImpl;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.Servers;
import org.bennedum.transporter.Worlds;
import org.bukkit.entity.Player;

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
        return new HashSet<LocalGate>(Gates.getLocalGates());
    }
    
    public Set<RemoteGate> getRemoteGates() {
        return new HashSet<RemoteGate>(Gates.getRemoteGates());
    }
    
    public Set<RemoteServer> getRemoteServers() {
        return new HashSet<RemoteServer>(Servers.getAll());
    }

    public Set<LocalWorld> getLocalWorlds() {
        return new HashSet<LocalWorld>(Worlds.getAll());
    }
    
    public void saveAll() {
        saveConfig();
        saveGates();
    }
    
    public void saveConfig() {
        Config.save(null);
    }
    
    public void saveGates() {
        Gates.save(null);
    }
    
    public void teleportPlayer(Player player, LocalGate fromGate) throws ReservationException {
        ReservationImpl res = new ReservationImpl(player, (LocalGateImpl)fromGate);
        res.depart();
    }
    
    public void teleportPlayer(Player player, Gate toGate) throws ReservationException {
        ReservationImpl res = new ReservationImpl(player, (GateImpl)toGate);
        res.depart();
    }
    
    public void teleportPlayer(Player player, RemoteServer server) throws ReservationException {
        ReservationImpl res = new ReservationImpl(player, (Server)server);
        res.depart();
    }

    public void teleportPlayer(Player player, RemoteWorld world) throws ReservationException {
        ReservationImpl res = new ReservationImpl(player, (Server)world.getRemoteServer(), world.getName());
        res.depart();
    }

    public void teleportPlayer(Player player, RemoteLocation location) throws ReservationException {
        ReservationImpl res = new ReservationImpl(player, (Server)location.getRemoteServer(), location.getRemoteWorld().getName(), location.getX(), location.getY(), location.getZ());
        res.depart();
    }
    
}
