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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location location = block.getLocation();
        Context ctx = new Context(event.getPlayer());

        LocalGate triggerGate = Gates.findGateForTrigger(location);
        LocalGate switchGate = null;

        if (triggerGate != null) {
            Global.setSelectedGate(event.getPlayer(), triggerGate);

            if (triggerGate.isClosed() &&
                Permissions.has(ctx.getPlayer(), "trp.gate.open." + triggerGate.getFullName())) {
                if (triggerGate.hasValidDestination()) {
                    try {
                        triggerGate.open();
                        ctx.sendLog("opened gate '%s'", triggerGate.getName());
                        return;
                    } catch (GateException ge) {
                        ctx.warnLog(ge.getMessage());
                    }
                }
            } else {

                switchGate = Gates.findGateForSwitch(location);
                if (switchGate == triggerGate) {
                    // the trigger is the same block as the switch, so do something special
                    if (triggerGate.isLastLink() &&
                        Permissions.has(ctx.getPlayer(), "trp.gate.close." + triggerGate.getFullName())) {
                        triggerGate.close();
                        ctx.sendLog("closed gate '%s'", triggerGate.getName());
                        if (Permissions.has(ctx.getPlayer(), "trp.gate.changeLink." + triggerGate.getFullName())) {
                            try {
                                triggerGate.nextLink();
                            } catch (GateException ge) {
                                ctx.warnLog(ge.getMessage());
                            }
                        }
                        return;
                    }

                } else if (Permissions.has(ctx.getPlayer(), "trp.gate.close." + triggerGate.getFullName())) {
                    triggerGate.close();
                    ctx.sendLog("closed gate '%s'", triggerGate.getName());
                    return;
                }
            }
        }

        if (switchGate == null)
            switchGate = Gates.findGateForSwitch(location);

        if (switchGate != null) {
            Global.setSelectedGate(event.getPlayer(), switchGate);
            try {
                Permissions.require(ctx.getPlayer(), "trp.gate.changeLink." + switchGate.getFullName());
                switchGate.nextLink();
            } catch (TransporterException te) {
                ctx.warnLog(te.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LocalGate fromGate = Gates.findGateForPortal(event.getTo());
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
        Players.onTeleport(player, location);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        Players.onJoin(player, r);
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
        Players.onQuit(player, r);
        if (r != null)
            event.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        Players.onKick(player, r);
        if (r != null)
            event.setLeaveMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(PlayerChatEvent event) {
        Chat.send(event.getPlayer(), event.getMessage());
    }

}
