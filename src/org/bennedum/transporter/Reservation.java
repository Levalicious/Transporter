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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bennedum.transporter.net.Message;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Reservation {

    private static final Map<Integer,Long> gateLocks = new HashMap<Integer,Long>();

    private static long nextId = 1;
    private static final Map<Long,Reservation> reservations = new HashMap<Long,Reservation>();

    public static Reservation get(long id) {
        return reservations.get(id);
    }

    public static Reservation get(String playerName) {
        for (Reservation r : reservations.values())
            if (playerName.equals(r.playerName)) return r;
        return null;
    }

    public static Reservation get(Player player) {
        return get(player.getName());
    }

    private static boolean put(Reservation r) {
        if (reservations.put(r.localId, r) == null) {
            Utils.debug("put reservation %s", r.localId);
            return true;
        }
        return false;
    }

    private static boolean remove(Reservation r) {
        if (reservations.remove(r.localId) != null) {
            Utils.debug("removed reservation %s", r.localId);
            return true;
        }
        return false;
    }

    public static void removeGateLock(Entity entity) {
        if (entity == null) return;
        Long expiry = gateLocks.get(entity.getEntityId());
        if (expiry == null) return;
        if (expiry <= System.currentTimeMillis()) {
            gateLocks.remove(entity.getEntityId());
            Utils.debug("removed gate lock for entity %d", entity.getEntityId());
        }
    }

    public static boolean isGateLocked(Entity entity) {
        if (entity == null) return false;
        return gateLocks.containsKey(entity.getEntityId());
    }

    public static void addGateLock(Entity entity) {
        if (entity == null) return;
        gateLocks.put(entity.getEntityId(), System.currentTimeMillis() + Config.getGateLockExpiration());
        Utils.debug("added gate lock for entity %d", entity.getEntityId());
    }

    private long localId = nextId++;
    private long remoteId = 0;

    private EntityType entityType = null;
    private Entity entity = null;
    private int localEntityId = 0;
    private int remoteEntityId = 0;

    private Player player = null;
    private String playerName = null;
    private String playerPin = null;
    private String clientAddress = null;

    private ItemStack[] inventory = null;
    private int health = 0;
    private int remainingAir = 0;
    private int fireTicks = 0;
    private int foodLevel = 0;
    private float exhaustion = 0;
    private float saturation = 0;
    private String gameMode = null;
    private int heldItemSlot = 0;
    private ItemStack[] armor = null;
    private float xp = 0;
    
    private Location fromLocation = null;
    private Vector fromVelocity = null;
    private BlockFace fromDirection = null;
    private EndpointImpl fromEp = null;
    private String fromWorldName = null;
    private String fromEpName = null;
    private LocalEndpointImpl fromEpLocal = null; // local endpoint
    private World fromWorld = null;         // local endpoint
    private Server fromServer = null;       // remote endpoint

    private Location toLocation = null;
    private Vector toVelocity = null;
    private BlockFace toDirection = null;
    private EndpointImpl toEp = null;
    private String toWorldName = null;
    private String toEpName = null;
    private LocalEndpointImpl toEpLocal = null;   // local endpoint
    private World toWorld = null;           // local endpoint
    private Server toServer = null;         // remote endpoint

    private boolean createdEntity = false;

    // player stepping into gate
    public Reservation(Player player, LocalEndpointImpl fromEp) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        extractFromEndpoint(fromEp);
        if ((fromEp instanceof LocalGateImpl) && (! ((LocalGateImpl)fromEp).getSendInventory())) {
            inventory = null;
            armor = null;
        }
    }

    // vehicle moving into gate
    public Reservation(Vehicle vehicle, LocalEndpointImpl fromEp) throws ReservationException {
        addGateLock(vehicle);
        extractVehicle(vehicle);
        extractFromEndpoint(fromEp);
        if ((fromEp instanceof LocalGateImpl) && (! ((LocalGateImpl)fromEp).getSendInventory())) {
            inventory = null;
            armor = null;
        }
    }

    // player direct to gate
    public Reservation(Player player, EndpointImpl toEp) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        extractToEndpoint(toEp);
    }

    // player direct to location on this server
    public Reservation(Player player, Location location) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        toLocation = location;
    }

    // player direct to remote server, default world, spawn location
    public Reservation(Player player, Server server) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        toServer = server;
    }

    // player direct to remote server, specified world, spawn location
    public Reservation(Player player, Server server, String worldName) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        toServer = server;
        toWorldName = worldName;
    }

    // player direct to remote server, specified world, specified location
    public Reservation(Player player, Server server, String worldName, double x, double y, double z) throws ReservationException {
        addGateLock(player);
        extractPlayer(player);
        toServer = server;
        toWorldName = worldName;
        toLocation = new Location(null, x, y, z);
    }

    // reception of reservation from sending server
    public Reservation(Message in, Server server) throws ReservationException {
        remoteId = in.getInt("id");
        try {
            entityType = Utils.valueOf(EntityType.class, in.getString("entityType"));
        } catch (IllegalArgumentException e) {
            throw new ReservationException("unknown or ambiguous entityType '%s'", in.getString("entityType"));
        }
        remoteEntityId = in.getInt("entityId");
        playerName = in.getString("playerName");
        if (playerName != null) {
            Reservation other = get(playerName);
            if (other != null)
                remove(other);
                //throw new ReservationException("a reservation for player '%s' already exists", playerName);
            player = Global.plugin.getServer().getPlayer(playerName);
        }
        playerPin = in.getString("playerPin");
        clientAddress = in.getString("clientAddress");
        fromLocation = new Location(null, in.getDouble("fromX"), in.getDouble("fromY"), in.getDouble("fromZ"), in.getFloat("pitch"), in.getFloat("yaw"));
        fromVelocity = new Vector(in.getDouble("velX"), in.getDouble("velY"), in.getDouble("velZ"));
        inventory = decodeItemStackArray(in.getMessageList("inventory"));
        health = in.getInt("health");
        remainingAir = in.getInt("remainingAir");
        fireTicks = in.getInt("fireTicks");
        foodLevel = in.getInt("foodLevel");
        exhaustion = in.getFloat("exhaustion");
        saturation = in.getFloat("saturation");
        gameMode = in.getString("gameMode");

        heldItemSlot = in.getInt("heldItemSlot");
        armor = decodeItemStackArray(in.getMessageList("armor"));

        xp = in.getFloat("xp");
        
        fromWorldName = in.getString("fromWorld");

        fromServer = server;

        if (in.get("toX") != null)
            toLocation = new Location(null, in.getDouble("toX"), in.getDouble("toY"), in.getDouble("toZ"));

        toWorldName = in.getString("toWorldName");
        if (toWorldName != null) {
            toWorld = Global.plugin.getServer().getWorld(toWorldName);
            if (toWorld == null)
                throw new ReservationException("unknown world '%s'", toWorldName);
        }

        fromEpName = in.getString("fromEp");
        if (fromEpName != null) {
            fromEpName = server.getName() + "." + fromEpName;
            fromEp = Endpoints.get(fromEpName);
            if (fromEp == null)
                throw new ReservationException("unknown fromEp '%s'", fromEpName);
            if (fromEp.isSameServer())
                throw new ReservationException("fromEp '%s' is not a remote endpoint", fromEpName);
            try {
                fromDirection = Utils.valueOf(BlockFace.class, in.getString("fromEpDirection"));
            } catch (IllegalArgumentException e) {
                throw new ReservationException("unknown or ambiguous fromEpDirection '%s'", in.getString("fromEpDirection"));
            }
        }

        toEpName = in.getString("toEp");
        if (toEpName != null) {
            toEpName = toEpName.substring(toEpName.indexOf(".") + 1);
            toEp = Endpoints.get(toEpName);
            if (toEp == null)
                throw new ReservationException("unknown toEp '%s'", toEpName);
            if (! toEp.isSameServer())
                throw new ReservationException("toEp '%s' is not a local endpoint", toEpName);
            toEpLocal = (LocalEndpointImpl)toEp;
            toDirection = toEpLocal.getDirection();
            toWorld = toEpLocal.getWorld();
            toWorldName = toWorld.getName();
            if (fromDirection == null)
                fromDirection = toDirection;
        }
    }

    private void extractPlayer(Player player) {
        entityType = EntityType.PLAYER;
        entity = player;
        localEntityId = player.getEntityId();
        this.player = player;
        playerName = player.getName();
        playerPin = Pins.get(player);
        clientAddress = player.getAddress().getAddress().getHostAddress();
        health = player.getHealth();
        remainingAir = player.getRemainingAir();
        fireTicks = player.getFireTicks();
        foodLevel = player.getFoodLevel();
        exhaustion = player.getExhaustion();
        saturation = player.getSaturation();
        gameMode = player.getGameMode().toString();
        PlayerInventory inv = player.getInventory();
        inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        heldItemSlot = inv.getHeldItemSlot();
        armor = inv.getArmorContents();
        xp = player.getExp();
        fromLocation = player.getLocation();
        fromVelocity = player.getVelocity();
        fromWorldName = player.getWorld().getName();
        Utils.debug("player location: %s", fromLocation);
        Utils.debug("player velocity: %s", fromVelocity);
    }

    private void extractVehicle(Vehicle vehicle) {
        if (vehicle.getPassenger() instanceof Player)
            extractPlayer((Player)vehicle.getPassenger());

        if (vehicle instanceof Minecart)
            entityType = EntityType.MINECART;
        else if (vehicle instanceof PoweredMinecart)
            entityType = EntityType.POWERED_MINECART;
        else if (vehicle instanceof StorageMinecart) {
            entityType = EntityType.STORAGE_MINECART;
            Inventory inv = ((StorageMinecart)vehicle).getInventory();
            inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        } else if (vehicle instanceof Boat)
            entityType = EntityType.BOAT;
        else
            throw new IllegalArgumentException("can't create state for " + vehicle.getClass().getName());
        entity = vehicle;
        localEntityId = vehicle.getEntityId();
        fireTicks = vehicle.getFireTicks();
        fromLocation = vehicle.getLocation();
        fromVelocity = vehicle.getVelocity();
        fromWorldName = vehicle.getWorld().getName();
        Utils.debug("vehicle location: %s", fromLocation);
        Utils.debug("vehicle velocity: %s", fromVelocity);
    }

    private void extractFromEp(LocalEndpointImpl fromEp) throws ReservationException {
        this.fromEp = fromEpLocal = fromEp;
        fromEpName = fromEp.getFullName();
        fromDirection = fromEp.getDirection();
        fromWorld = fromEp.getWorld();
        fromWorldName = fromEp.getWorld().getName();

        if (fromEp.getSendNextLink())
            try {
                fromEp.nextLink();
            } catch (EndpointException ee) {
                throw new ReservationException(ee.getMessage());
            }
        
        try {
            toEp = fromEpLocal.getDestinationGate();
        } catch (GateException ge) {
            throw new ReservationException(ge.getMessage());
        }

        toEpName = toEp.getFullName();
        if (toEp.isSameServer()) {
            toEpLocal = (LocalEndpointImpl)toEp;
            toWorld = toEpLocal.getWorld();
        } else
            toServer = (Server)((RemoteEndpointImpl)toEp).getRemoteServer();

        if ((! fromEp.getSendInventory()) ||
            ((toEpLocal != null) && (! toEpLocal.getReceiveInventory()))) {
            inventory = null;
            armor = null;
        }
    }

    private void extractToEndpoint(EndpointImpl toEp) {
        this.toEp = toEp;
        toEpName = toEp.getFullName();
        if (toEp.isSameServer()) {
            toEpLocal = (LocalEndpointImpl)toEp;
            toWorld = toEpLocal.getWorld();
            toDirection = toEpLocal.getDirection();
            if (fromDirection == null)
                fromDirection = toDirection;
            if (! toEpLocal.getReceiveInventory()) {
                inventory = null;
                armor = null;
            }
        } else
            toServer = (Server)((RemoteGateImpl)toEp).getRemoteServer();
    }

    public Message encode() {
        Message out = new Message();
        out.put("id", localId);
        out.put("entityType", entityType.toString());
        out.put("entityId", localEntityId);
        out.put("playerName", playerName);
        out.put("playerPin", playerPin);
        out.put("clientAddress", clientAddress);
        out.put("velX", fromVelocity.getX());
        out.put("velY", fromVelocity.getY());
        out.put("velZ", fromVelocity.getZ());
        out.put("fromX", fromLocation.getX());
        out.put("fromY", fromLocation.getY());
        out.put("fromZ", fromLocation.getZ());
        out.put("fromPitch", fromLocation.getPitch());
        out.put("fromYaw", fromLocation.getYaw());
        out.put("fromWorld", fromWorldName);
        out.put("inventory", encodeItemStackArray(inventory));
        out.put("health", health);
        out.put("remainingAir", remainingAir);
        out.put("fireTicks", fireTicks);
        out.put("foodLevel", foodLevel);
        out.put("exhaustion", exhaustion);
        out.put("saturation", saturation);
        out.put("gameMode", gameMode);
        out.put("heldItemSlot", heldItemSlot);
        out.put("armor", encodeItemStackArray(armor));
        out.put("xp", xp);
        out.put("fromEp", fromEpName);
        if (fromDirection != null)
            out.put("fromEpDirection", fromDirection.toString());
        out.put("toEp", toEpName);
        out.put("toWorldName", toWorldName);
        if (toLocation != null) {
            out.put("toX", toLocation.getX());
            out.put("toY", toLocation.getY());
            out.put("toZ", toLocation.getZ());
        }
        return out;
    }

    private List<Message> encodeItemStackArray(ItemStack[] isa) {
        if (isa == null) return null;
        List<Message> inv = new ArrayList<Message>();
        for (int slot = 0; slot < isa.length; slot++)
                inv.add(encodeItemStack(isa[slot]));
        return inv;
    }

    private ItemStack[] decodeItemStackArray(List<Message> inv) {
        if (inv == null) return null;
        ItemStack[] decoded = new ItemStack[inv.size()];
        for (int slot = 0; slot < inv.size(); slot++)
            decoded[slot] = decodeItemStack(inv.get(slot));
        return decoded;
    }

    private Message encodeItemStack(ItemStack stack) {
        if (stack == null) return null;
        Message s = new Message();
        s.put("type", stack.getTypeId());
        s.put("amount", stack.getAmount());
        s.put("durability", stack.getDurability());
        MaterialData data = stack.getData();
        if (data != null)
            s.put("data", (int)data.getData());
        Message ench = new Message();
        for (Enchantment e : stack.getEnchantments().keySet())
            ench.put(e.getName(), stack.getEnchantments().get(e));
        s.put("enchantments", ench);
        return s;
    }

    private ItemStack decodeItemStack(Message s) {
        if (s == null) return null;
        ItemStack stack = new ItemStack(
            s.getInt("type"),
            s.getInt("amount"),
            (short)s.getInt("durability"));
        if (s.containsKey("data")) {
            MaterialData data = stack.getData();
            if (data != null)
                data.setData((byte)s.getInt("data"));
        }
        Message ench = s.getMessage("enchantments");
        if (ench != null)
            for (String name : ench.keySet())
                stack.addEnchantment(Enchantment.getByName(name), ench.getInt(name));
        return stack;
    }

    // called to handle departure on the sending side
    public void depart() throws ReservationException {
        put(this);
        try {
            addEndpointLock(entity);
            if (entity != player)
                addEndpointLock(player);

            checkLocalDepartureEndpoint();

            if (toServer == null) {
                // staying on this server
                checkLocalArrivalEndpoint();
                arrive();
                completeLocalDepartureEndpoint();

            } else {
                // going to remote server
                try {
                    Utils.debug("sending reservation for %s to %s...", getTraveler(), getDestination());
                    toServer.sendReservation(this);

                    // setup delayed task to remove the reservation on this side if it doesn't work out
                    final Reservation me = this;
                    Utils.fireDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (! remove(me)) return;
                            Utils.warning("reservation for %s to %s timed out", getTraveler(), getDestination());
                        }
                    }, Config.getArrivalWindow());

                } catch (ServerException e) {
                    Utils.severe(e, "reservation send for %s to %s failed:", getTraveler(), getDestination());
                    remove(this);
                    throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
                }
            }
        } catch (ReservationException e) {
            remove(this);
            throw e;
        }
    }

    // called on the receiving side to indicate this reservation has been sent from the sender
    public void receive() {
        try {
            Utils.debug("received reservation for %s to %s from %s...", getTraveler(), getDestination(), fromServer.getName());
            if (playerName != null) {
                try {
                    Permissions.connect(playerName);
                } catch (PermissionsException e) {
                    throw new ReservationException(e.getMessage());
                }
            }
            checkLocalArrivalEndpoint();
            put(this);
            try {
                fromServer.sendReservationApproved(remoteId);
            } catch (ServerException e) {
                Utils.severe(e, "send reservation approval for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
                remove(this);
                return;
            }

            Utils.debug("reservation for %s to %s approved", getTraveler(), getDestination());

            if (playerName == null) {
                // there's no player coming, so handle the "arrival" now
                try {
                    arrive();
                } catch (ReservationException e) {
                    Utils.warning("reservation arrival for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName(), e.getMessage());
                }
            } else {
                // set up a delayed task to cancel the arrival if they never arrive
                final Reservation res = this;
                Utils.fireDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (! remove(res)) return;
                        Utils.warning("reservation for %s to %s timed out", getTraveler(), getDestination());
                        try {
                            fromServer.sendReservationTimeout(remoteId);
                        } catch (ServerException e) {
                            Utils.severe(e, "send reservation timeout for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
                        }
                    }
                }, Config.getArrivalWindow());
            }

        } catch (ReservationException e) {
            Utils.debug("reservation for %s to %s denied: %s", getTraveler(), getDestination(), e.getMessage());
            remove(this);
            try {
                fromServer.sendReservationDenied(remoteId, e.getMessage());
            } catch (ServerException e2) {
                Utils.severe(e, "send reservation denial for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
            }
        }
    }

    // called on the receiving side to handle arrival
    public void arrive() throws ReservationException {
        remove(this);

        if (toEpLocal != null)
            toEpLocal.attach(fromEp);

        prepareDestination();
        prepareTraveler();
        addEndpointLock(entity);
        if (entity != player)
            addEndpointLock(player);
        if ((player != null) && (playerPin != null))
            Pins.add(player, playerPin);
        if (! entity.teleport(toLocation)) {
            rollbackTraveler();
            throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
        }
        commitTraveler();

        Utils.debug("%s arrived at %s", getTraveler(), getDestination());

        completeLocalArrivalEndpoint();

        if (fromServer == null)
            arrived();
        else
            try {
                fromServer.sendReservationArrived(remoteId);
            } catch (ServerException e) {
                Utils.severe(e, "send reservation arrival for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
            }
    }

    // called on the sending side to confirm reception of the valid reservation on the receiving side
    public void approved() {
        Utils.debug("reservation to send %s to %s was approved", getTraveler(), getDestination());

        if (player != null) {
            Context ctx = new Context(player);

            completeLocalDepartureEndpoint();

            // TODO: handle cluster setting
            // if toServer.getCluster().equals(Network.getCluster()) then send Cluster Redirect, otherwise send Client Redirect
            String addr = toServer.getReconnectAddressForClient(player.getAddress());
            if (addr == null) {
                Utils.warning("reconnect address for '%s' is null?", toServer.getName());
                return;
            }
            final String[] addrParts = addr.split("/");
            if (addrParts.length == 1) {
                // this is a client based reconnect
                Utils.debug("sending player '%s' @%s to '%s' via client reconnect", player.getName(), player.getAddress().getAddress().getHostAddress(), addrParts[0]);
                player.kickPlayer("[Redirect] please reconnect to: " + addrParts[0]);
            } else {
                // this is a proxy based reconnect
                Utils.debug("sending player '%s' @%s to '%s,%s' via proxy reconnect", player.getName(), player.getAddress().getAddress().getHostAddress(), addrParts[0], addrParts[1]);
                player.kickPlayer("[Redirect] please reconnect to: " + addrParts[0] + "," + addrParts[1]);
            }
        }
        if ((entity != null) && (entity != player))
            entity.remove();
    }

    // called on the sending side to indicate a reservation was denied by the receiving side
    public void denied(final String reason) {
        remove(this);
        if (player == null)
            Utils.warning("reservation to send %s to %s was denied: %s", getTraveler(), getDestination(), reason);
        else {
            Context ctx = new Context(player);
            ctx.warn(reason);
        }
    }

    // called on the sending side to indicate an expeceted arrival arrived on the receiving side
    public void arrived() {
        remove(this);
        Utils.debug("reservation to send %s to %s was completed", getTraveler(), getDestination());

        if ((toServer != null) && (player != null)) {
            if ((fromEpLocal != null) && fromEpLocal.getDeleteInventory()) {
                PlayerInventory inv = player.getInventory();
                inv.clear();
                inv.setBoots(new ItemStack(Material.AIR));
                inv.setHelmet(new ItemStack(Material.AIR));
                inv.setChestplate(new ItemStack(Material.AIR));
                inv.setLeggings(new ItemStack(Material.AIR));
                player.saveData();
            }
        }
    }

    // called on the sending side to indicate an expected arrival never happened on the receiving side
    public void timeout() {
        remove(this);
        Utils.warning("reservation to send %s to %s timed out", getTraveler(), getDestination());
    }




    // called after arrival to get the destination on the local server where the entity arrived
    public Location getToLocation() {
        return toLocation;
    }






    private void checkLocalDepartureEndpoint() throws ReservationException {
        if (fromEpLocal == null) return;

        if (player != null) {
            // player permission
            try {
                Permissions.require(player, "trp.endpoint.use." + fromEpLocal.getFullName());
            } catch (PermissionsException e) {
                throw new ReservationException(e.getMessage());
            }
            // player PIN
            if (fromEpLocal.getRequirePin()) {
                if (playerPin == null)
                    throw new ReservationException("this endpoint requires a pin");
                if (! fromEpLocal.hasPin(playerPin))
                    throw new ReservationException("this endpoint rejected your pin");
            }
            // player cost
            double cost = fromEpLocal.getSendCost(toEp);
            if (cost > 0)
                try {
                    Economy.requireFunds(player, cost);
                } catch (EconomyException e) {
                    throw new ReservationException("this endpoint requires %s", Economy.format(cost));
                }
            if ((toEp != null) && toEp.isSameServer()) {
                cost += ((LocalEndpointImpl)toEp).getReceiveCost(fromEpLocal);
                if (cost > 0)
                    try {
                        Economy.requireFunds(player, cost);
                    } catch (EconomyException e) {
                        throw new ReservationException("total travel cost requires %s", Economy.format(cost));
                    }
            }
        }

        // check gate permission
        if ((toEp != null) && Config.getUseEndpointPermissions()) {
            try {
                Permissions.require(fromEpLocal.getWorld().getName(), fromEpLocal.getName(), "trp.send." + toEp.getGlobalName());
            } catch (PermissionsException e) {
                throw new ReservationException("this endpoint is not permitted to send to the remote endpoint");
            }
        }
    }

    private void checkLocalArrivalEndpoint() throws ReservationException {
        if (toEpLocal == null) return;

        if (player != null) {
            // player permission
            try {
                Permissions.require(player, "trp.endpoint.use." + toEpLocal.getFullName());
            } catch (PermissionsException e) {
                throw new ReservationException(e.getMessage());
            }
            // player PIN
            if (toEpLocal.getRequirePin()) {
                if (playerPin == null)
                    throw new ReservationException("remote endpoint requires a pin");
                if ((! toEpLocal.hasPin(playerPin)) && toEpLocal.getRequireValidPin())
                    throw new ReservationException("remote endpoint rejected your pin");
            }
            // player game mode
            if (toEpLocal.getReceiveGameMode()) {
                if (! toEpLocal.isAllowedGameMode(gameMode))
                    throw new ReservationException("remote endpoint rejected your game mode");
            }
            // player cost
            if (fromServer != null) {
                // only check this side since the departure side already checked itself
                double cost = toEpLocal.getReceiveCost(fromEp);
                if (cost > 0)
                    try {
                        Economy.requireFunds(player, cost);
                    } catch (EconomyException e) {
                        throw new ReservationException("remote endpoint requires %s", Economy.format(cost));
                    }
            }
        }

        // check inventory
        // this is only checked on the arrival side
        if ((! toEpLocal.isAcceptableInventory(inventory)) ||
            (! toEpLocal.isAcceptableInventory(armor)))
            throw new ReservationException("remote endpoint won't allow some inventory items");

        // check gate permission
        if ((fromEp != null) && Config.getUseEndpointPermissions()) {
            try {
                Permissions.require(toEpLocal.getWorld().getName(), toEpLocal.getName(), "trp.receive." + fromEpLocal.getGlobalName());
            } catch (PermissionsException e) {
                throw new ReservationException("the remote endpoint is not permitted to receive from this endpoint");
            }
        }

    }

    private void completeLocalDepartureEndpoint() {
        if (fromEpLocal == null) return;

        // Handle lightning strike...
        
        fromEpLocal.onSend(entity);
        
        if (player != null) {

            Context ctx = new Context(player);

            // player cost
            double cost = fromEpLocal.getSendCost(toEp);
            if ((toEp != null) && toEp.isSameServer())
                cost += ((LocalEndpointImpl)toEp).getReceiveCost(fromEpLocal);
            if (cost > 0)
                try {
                    if (Economy.deductFunds(player, cost))
                        ctx.send("debited %s for travel costs", Economy.format(cost));
                } catch (EconomyException e) {
                    // too late to do anything useful
                    Utils.warning("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
                }
        }

    }

    private void completeLocalArrivalEndpoint() {
        if (toEpLocal == null) return;

        // Handle lightning strike...
        
        toEpLocal.onReceive(entity);
        
        if (player != null) {

            Context ctx = new Context(player);

            if (toEpLocal.getTeleportFormat() != null) {
                String format = toEpLocal.getTeleportFormat();
                format = format.replace("%player%", player.getDisplayName());
                format = format.replace("%toNameCtx%", toEpLocal.getName(ctx));
                format = format.replace("%toName%", toEpLocal.getName());
                format = format.replace("%toWorld%", toEpLocal.getWorld().getName());
                format = format.replace("%fromNameCtx%", (fromEp == null) ? "" : fromEp.getName(ctx));
                format = format.replace("%fromName%", (fromEp == null) ? "" : fromEp.getName());
                format = format.replace("%fromWorld%", fromWorldName);
                format = format.replace("%fromServer%", (fromServer == null) ? "local" : fromServer.getName());
                if (! format.isEmpty())
                    ctx.send(format);
            }

            // player PIN
            if (toEpLocal.getRequirePin() &&
                (! toEpLocal.hasPin(playerPin)) &&
                (! toEpLocal.getRequireValidPin()) &&
                (toEpLocal.getInvalidPinDamage() > 0)) {
                ctx.send("invalid pin");
                player.damage(toEpLocal.getInvalidPinDamage());
            }

            // player cost
            if (fromServer != null) {
                // only deduct this side since the departure side already deducted itself
                double cost = toEpLocal.getReceiveCost(fromEp);
                if (cost > 0)
                    try {
                        if (Economy.deductFunds(player, cost))
                            ctx.sendLog("debited %s for travel costs", Economy.format(cost));
                    } catch (EconomyException e) {
                        // too late to do anything useful
                        Utils.warning("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
                    }
            }
        } else {
            Utils.debug("%s arrived at '%s'", getTraveler(), toEpLocal.getFullName());
        }
    }

    private void prepareDestination() {
        if (toEpLocal != null) {
            GateBlock block = toEpLocal.getSpawnBlocks().randomBlock();
            toLocation = block.getLocation().clone();
            toLocation.add(0.5, 0, 0.5);
            toLocation.setYaw(block.getDetail().getSpawn().calculateYaw(fromLocation.getYaw(), fromDirection, toGateLocal.getDirection()));
            toLocation.setPitch(fromLocation.getPitch());
            toVelocity = fromVelocity.clone();
            Utils.rotate(toVelocity, fromDirection, toEpLocal.getDirection());
        } else {
            if (toLocation == null) {
                if (toWorld == null)
                    toWorld = Global.plugin.getServer().getWorlds().get(0);
                toLocation = toWorld.getSpawnLocation();
            } else if (toLocation.getWorld() == null)
                toLocation.setWorld(Global.plugin.getServer().getWorlds().get(0));
            toLocation.setYaw(fromLocation.getYaw());
            toLocation.setPitch(fromLocation.getPitch());
            toVelocity = fromVelocity.clone();
        }
        Utils.prepareChunk(toLocation);

        // tweak velocity so we don't get buried in a block
        Location nextLocation = toLocation.clone().add(toVelocity.getX(), toVelocity.getY(), toVelocity.getZ());
        Utils.prepareChunk(nextLocation);
        switch (nextLocation.getBlock().getType()) {
            // there are probably others
            case AIR:
            case WATER:
            case STATIONARY_WATER:
            case LAVA:
            case STATIONARY_LAVA:
            case WEB:
            case TORCH:
            case REDSTONE_TORCH_OFF:
            case REDSTONE_TORCH_ON:
            case SIGN_POST:
            case RAILS:
                break;
            default:
                // should we try to zero just each ordinate and test again?
                Utils.debug("zeroing velocity to avoid block");
                toVelocity.zero();
                break;
        }

        Utils.debug("destination location: %s", toLocation);
        Utils.debug("destination velocity: %s", toVelocity);
    }

    private void prepareTraveler() throws ReservationException {
        Utils.debug("prepareTraveler %s", entityType);
        if ((player == null) && (playerName != null)) {
            player = Global.plugin.getServer().getPlayer(playerName);
            if (player == null)
                throw new ReservationException("player '%s' not found", playerName);
        }
        
        if (toEpLocal != null) {
            // filter inventory
            boolean invFiltered = toEpLocal.filterInventory(inventory);
            boolean armorFiltered = toEpLocal.filterInventory(armor);
            if (invFiltered || armorFiltered) {
                if (player == null)
                    Utils.debug("some inventory items where filtered by the arrival gate");
                else
                    (new Context(player)).send("some inventory items where filtered by the arrival gate");
            }
        }
        
        if (entity == null) {
            switch (entityType) {
                case PLAYER:
                    entity = player;
                    break;
                case MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, Minecart.class);
                    createdEntity = true;
                    if (player != null)
                        ((Minecart)entity).setPassenger(player);
                    break;
                case POWERED_MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, PoweredMinecart.class);
                    createdEntity = true;
                    break;
                case STORAGE_MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, StorageMinecart.class);
                    createdEntity = true;
                    break;
                case BOAT:
                    entity = toLocation.getWorld().spawn(toLocation, Boat.class);
                    createdEntity = true;
                    break;
                default:
                    throw new ReservationException("unknown entity type '%s'", entityType);
            }
        }
        if (player != null) {
            if (health < 0) health = 0;
            player.setHealth(health);
            if (remainingAir < 0) remainingAir = 0;
            player.setRemainingAir(remainingAir);
            if (foodLevel < 0) foodLevel = 0;
            player.setFoodLevel(foodLevel);
            if (exhaustion < 0) exhaustion = 0;
            player.setExhaustion(exhaustion);
            if (saturation < 0) saturation = 0;
            player.setSaturation(saturation);
            if ((toEpLocal != null) && toEpLocal.getReceiveGameMode())
                player.setGameMode(Utils.valueOf(GameMode.class, gameMode));
            if ((toEpLocal != null) && toEpLocal.getReceiveXP()) {
                if (xp < 0) xp = 0;
                player.setExp(xp);
            }
        }
        switch (entityType) {
            case PLAYER:
                player.setFireTicks(fireTicks);
                player.setVelocity(toVelocity);
                if (inventory != null) {
                    PlayerInventory inv = player.getInventory();
                    for (int slot = 0; slot < inventory.length; slot++) {
                        if (inventory[slot] == null) continue;
                        inv.setItem(slot, inventory[slot]);
                    }
                    // PENDING: This doesn't work as expected. it replaces whatever's
                    // in slot 0 with whatever's in the held slot. There doesn't appear to
                    // be a way to change just the slot of the held item
                    //inv.setItemInHand(inv.getItem(heldItemSlot));
                }
                if (armor != null) {
                    PlayerInventory inv = player.getInventory();
                    inv.setArmorContents(armor);
                }
                break;
            case MINECART:
                entity.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if ((player != null) && (entity.getPassenger() != player))
                    entity.setPassenger(player);
                break;
            case POWERED_MINECART:
                entity.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                break;
            case STORAGE_MINECART:
                entity.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if (inventory != null) {
                    StorageMinecart mc = (StorageMinecart)entity;
                    Inventory inv = mc.getInventory();
                    for (int slot = 0; slot <  inventory.length; slot++)
                        inv.setItem(slot, inventory[slot]);
                }
                break;
            case BOAT:
                entity.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if ((player != null) && (entity.getPassenger() != player))
                    entity.setPassenger(player);
                break;
        }
    }

    private void rollbackTraveler() {
        if (createdEntity)
            entity.remove();
    }

    private void commitTraveler() {
        // TODO: something?
    }

    public String getTraveler() {
        if (entityType == EntityType.PLAYER)
            return String.format("player '%s'", playerName);
        if (playerName == null)
            return entityType.toString();
        return String.format("player '%s' as a passenger on a %s", playerName, entityType);
    }

    public String getDestination() {
        if (toEpName != null)
            return "'" + toEpName + "'";
        String dst;
        if (toServer != null)
            dst = String.format("server '%s'", toServer.getName());
        else if (toWorld != null)
            dst = String.format("world '%s'", toWorld.getName());
        else
            dst = "unknown";
        if (toLocation != null)
            dst += String.format(" @ %s,%s,%s", toLocation.getBlockX(), toLocation.getBlockY(), toLocation.getBlockZ());
        return dst;
    }

    private enum EntityType {
        PLAYER,
        MINECART,
        POWERED_MINECART,
        STORAGE_MINECART,
        BOAT
    }

}
