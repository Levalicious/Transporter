/*
 * Copyright 2011 frdfsnlght <frdfsnlght@gmail.com>.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Chat {

    public static void send(Player player, String message) {
        Map<Server,Set<RemoteGateImpl>> servers = new HashMap<Server,Set<RemoteGateImpl>>();

        // add all servers that relay all chat
        for (Server server : Servers.getAll())
            if (server.getSendChat())
                servers.put(server, null);

        Location loc = player.getLocation();
        RemoteGateImpl destGate;
        Server destServer;
        for (LocalGateImpl gate : Endpoints.getLocalGates()) {
            if (gate.isOpen() && gate.getSendChat() && gate.isInChatSendProximity(loc)) {
                try {
                    Object ep = gate.getDestinationEndpoint();
                    if (! (ep instanceof RemoteGateImpl)) continue;
                    destGate = (RemoteGateImpl)ep;
                    destServer = (Server)destGate.getRemoteServer();
                    if (servers.containsKey(destServer)) {
                        if (servers.get(destServer) == null) continue;
                    } else
                        servers.put(destServer, new HashSet<RemoteGateImpl>());
                    servers.get(destServer).add(destGate);
                } catch (EndpointException e) {}
            }
        }
        for (Server server : servers.keySet()) {
            server.sendChat(player, message, servers.get(server));
        }
    }

    public static void receive(RemotePlayerImpl player, String message, List<String> toGates) {
        Player[] players = Global.plugin.getServer().getOnlinePlayers();
//        Set<Player,Location> players = new HashMap<String,Location>();
//        for (Player lp : Global.plugin.getServer().getOnlinePlayers())
//            players.put(lp.getName(), lp.getLocation());

        final Set<Player> playersToReceive = new HashSet<Player>();
        if ((toGates == null) && ((Server)player.getRemoteServer()).getReceiveChat())
            Collections.addAll(playersToReceive, players);
        else if ((toGates != null) && (! toGates.isEmpty())) {
            for (String gateName : toGates) {
                EndpointImpl ep = Endpoints.get(gateName);
                if ((ep == null) || (! (ep instanceof LocalGateImpl))) continue;
                LocalGateImpl gate = (LocalGateImpl)ep;
                if (! gate.getReceiveChat()) continue;
                for (Player p : players) {
                    if (gate.isInChatReceiveProximity(p.getLocation()))
                        playersToReceive.add(p);
                }
            }
        }

        if (playersToReceive.isEmpty()) return;

        String format = Config.getServerChatFormat();
        format = format.replace("%player%", player.getDisplayName());
        format = format.replace("%server%", player.getRemoteServer().getName());
        format = format.replace("%world%", player.getRemoteWorld().getName());
        format = format.replace("%message%", message);
        for (Player p : playersToReceive)
            p.sendMessage(format);
    }

}
