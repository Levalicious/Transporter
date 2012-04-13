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

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerListenerImpl implements Listener {

    // Logic map for player interaction
    private static final Map<String,String> ACTIONS = new HashMap<String,String>();
    
    // Keys are strings composed of zeros and ones. Each position corresponds to
    // a boolean test:
    // 0: Is the gate currently open?
    // 1: Does the player have trp.gate.open permission?
    // 2: Does the player have trp.gate.close permission?
    // 3: Does the player have trp.gate.changeLink permission?
    // 4: Is the gate on its last link?
    // 5: Is the gate block a trigger?
    // 6: Is the gate block a switch?
    
    // Values are a comma separated list of actions to perform:
    // NOTPERMITTED: issue a 'not permitted' message to the player
    // OPEN: open the gate
    // CLOSE: close the gate
    // CHANGELINK: change the gate's link
    
    static {
        // gate is closed
        ACTIONS.put("0010011", "NOTPERMITTED");
        ACTIONS.put("0010101", "NOTPERMITTED");
        ACTIONS.put("0010110", "NOTPERMITTED");
        ACTIONS.put("0010111", "NOTPERMITTED");
        ACTIONS.put("0011001", "CHANGELINK");
        ACTIONS.put("0011010", "NOTPERMITTED");
        ACTIONS.put("0011011", "CHANGELINK");
        ACTIONS.put("0011101", "CHANGELINK");
        ACTIONS.put("0011110", "NOTPERMITTED");
        ACTIONS.put("0011111", "NOTPERMITTED");
        ACTIONS.put("0100001", "NOTPERMITTED");
        ACTIONS.put("0100010", "OPEN");
        ACTIONS.put("0100011", "OPEN");
        ACTIONS.put("0100101", "NOTPERMITTED");
        ACTIONS.put("0100110", "OPEN");
        ACTIONS.put("0100111", "OPEN");
        ACTIONS.put("0101001", "CHANGELINK");
        ACTIONS.put("0101010", "OPEN");
        ACTIONS.put("0101011", "CHANGELINK");
        ACTIONS.put("0101101", "OPEN");
        ACTIONS.put("0101110", "OPEN");
        ACTIONS.put("0101111", "OPEN");
        ACTIONS.put("0110001", "NOTPERMITTED");
        ACTIONS.put("0110010", "OPEN");
        ACTIONS.put("0110011", "OPEN");
        ACTIONS.put("0110101", "NOTPERMITTED");
        ACTIONS.put("0110110", "OPEN");
        ACTIONS.put("0110111", "OPEN");
        ACTIONS.put("0111001", "CHANGELINK");
        ACTIONS.put("0111010", "OPEN");
        ACTIONS.put("0111011", "OPEN");
        ACTIONS.put("0111101", "CHANGELINK");
        ACTIONS.put("0111110", "OPEN");
        ACTIONS.put("0111111", "OPEN");

        // gate is open
        ACTIONS.put("1010011", "CLOSE");
        ACTIONS.put("1010101", "NOTPERMITTED");
        ACTIONS.put("1010110", "CLOSE");
        ACTIONS.put("1010111", "CLOSE");
        ACTIONS.put("1011001", "CHANGELINK");
        ACTIONS.put("1011010", "CLOSE");
        ACTIONS.put("1011011", "CHANGELINK");
        ACTIONS.put("1011101", "CHANGELINK");
        ACTIONS.put("1011110", "CLOSE");
        ACTIONS.put("1011111", "CLOSE,CHANGELINK");
        ACTIONS.put("1100001", "NOTPERMITTED");
        ACTIONS.put("1100010", "NOTPERMITTED");
        ACTIONS.put("1100011", "NOTPERMITTED");
        ACTIONS.put("1100101", "NOTPERMITTED");
        ACTIONS.put("1100110", "NOTPERMITTED");
        ACTIONS.put("1100111", "NOTPERMITTED");
        ACTIONS.put("1101001", "CHANGELINK");
        ACTIONS.put("1101010", "NOTPERMITTED");
        ACTIONS.put("1101011", "CHANGELINK");
        ACTIONS.put("1101101", "CHANGELINK");
        ACTIONS.put("1101110", "NOTPERMITTED");
        ACTIONS.put("1101111", "CHANGELINK");
        ACTIONS.put("1110001", "NOTPERMITTED");
        ACTIONS.put("1110010", "CLOSE");
        ACTIONS.put("1110011", "CLOSE");
        ACTIONS.put("1110101", "NOTPERMITTED");
        ACTIONS.put("1110110", "CLOSE");
        ACTIONS.put("1110111", "CLOSE");
        ACTIONS.put("1111001", "CHANGELINK");
        ACTIONS.put("1111010", "CLOSE");
        ACTIONS.put("1111011", "CHANGELINK");
        ACTIONS.put("1111101", "CHANGELINK");
        ACTIONS.put("1111110", "CLOSE");
        ACTIONS.put("1111111", "CLOSE,CHANGELINK");
 
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location location = block.getLocation();
        Context ctx = new Context(event.getPlayer());

        LocalGateImpl triggerGate = Gates.findGateForTrigger(location);
        LocalGateImpl switchGate = Gates.findGateForSwitch(location);
        if ((triggerGate == null) && (switchGate == null)) return;
        if ((triggerGate != null) && (switchGate != null) && (triggerGate != switchGate)) switchGate = null;
        
        LocalGateImpl testGate = (triggerGate == null) ? switchGate : triggerGate;
        Player player = event.getPlayer();
        Gates.setSelectedGate(player, testGate);
        
        String key =
                (testGate.isOpen() ? "1" : "0") +
                (Permissions.has(player, "trp.gate.open." + testGate.getFullName()) ? "1" : "0") +
                (Permissions.has(player, "trp.gate.close." + testGate.getFullName()) ? "1" : "0") +
                (Permissions.has(player, "trp.gate.changeLink." + testGate.getFullName()) ? "1" : "0") +
                (testGate.isLastLink() ? "1" : "0") +
                ((triggerGate != null) ? "1" : "0") +
                ((switchGate != null) ? "1" : "0");
        String value = ACTIONS.get(key);
        if (value == null) {
            Utils.severe("Action key '%s' doesn't map to any actions!", key);
            return;
        }
        String[] actions = value.split(",");
        
        for (String action : actions) {
            
            if (action.equals("NOTPERMIITED")) {
                ctx.send("not permitted");
                return;
            }
            
            if (action.equals("OPEN")) {
                if (testGate.hasValidDestination())
                    try {
                        testGate.open();
                        ctx.send("opened gate '%s'", testGate.getName());
                        Utils.debug("player '%s' open gate '%s'", player.getName(), testGate.getName());
                    } catch (GateException ee) {
                        ctx.warnLog(ee.getMessage());
                    }
            }
            
            if (action.equals("CLOSE")) {
                testGate.close();
                ctx.send("closed gate '%s'", testGate.getName());
                Utils.debug("player '%s' closed gate '%s'", player.getName(), testGate.getName());
            }
            
            if (action.equals("CHANGELINK")) {
                try {
                    testGate.nextLink();
                    Utils.debug("player '%s' changed link for gate '%s'", player.getName(), testGate.getName());
                } catch (TransporterException te) {
                    ctx.warnLog(te.getMessage());
                }
            }
        }
                
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LocalGateImpl fromGate = Gates.findGateForPortal(event.getTo());
        if (fromGate == null) {
            Reservation.removeGateLock(player);
            return;
        }
        if (Reservation.isGateLocked(player)) {
            return;
        }

        Context ctx = new Context(player);
        try {
            Reservation r = new Reservation(player, fromGate);
            r.depart();
            Location newLoc = r.getToLocation();
            if (newLoc != null) {
                event.setFrom(newLoc);
                event.setTo(newLoc);
                // cancelling the event is bad in RB 953!
                //event.setCancelled(true);
            }
        } catch (ReservationException re) {
            ctx.warnLog(re.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location location = event.getTo();
        if ((location == null) ||
            (location.getWorld() == null)) return;
        for (Server server : Servers.getAll())
            server.sendPlayerChangedWorld(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        for (Server server : Servers.getAll())
            server.sendPlayerJoined(player, r == null);
        if (r == null) {
            Reservation.addGateLock(player);
            return;
        }
        try {
            r.arrive();
            event.setJoinMessage(null);
        } catch (ReservationException e) {
            Context ctx = new Context(player);
            ctx.warnLog("there was a problem processing your arrival: ", e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        for (Server server : Servers.getAll())
            server.sendPlayerQuit(player, r == null);
        if (r != null)
            event.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        for (Server server : Servers.getAll())
            server.sendPlayerKicked(player, r == null);
        if (r != null)
            event.setLeaveMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(PlayerChatEvent event) {
        Chat.send(event.getPlayer(), event.getMessage());
    }

}
