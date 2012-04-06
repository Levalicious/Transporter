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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bennedum.transporter.net.Connection;
import org.bennedum.transporter.net.Network;
import org.bennedum.transporter.net.Message;
import org.bennedum.transporter.net.Result;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Server implements OptionsListener, RemoteServer {

    public static final int DEFAULT_MC_PORT = 25565;

    private static final int SEND_KEEPALIVE_INTERVAL = 60000;
    private static final int RECV_KEEPALIVE_INTERVAL = 90000;

    private static final Set<String> OPTIONS = new HashSet<String>();

    static {
        OPTIONS.add("pluginAddress");
        OPTIONS.add("key");
        OPTIONS.add("publicAddress");
        OPTIONS.add("privateAddress");
        OPTIONS.add("sendChat");
        OPTIONS.add("receiveChat");
        OPTIONS.add("announcePlayers");
    }

    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    private Options options = new Options(this, OPTIONS, "trp.server", this);

    private String name;
    private String pluginAddress;   // can be IP/DNS name, with opt port
    private String key;
    private boolean enabled;
    private int connectionAttempts = 0;
    private long lastConnectionAttempt = 0;

    // The address we tell players so they can connect to our MC server.
    // This address is given to the plugin on the other end of the connection.
    // The string is a space separated list of values.
    // Each value is a slash (/) delimited list of 1 or 2 items.
    // The first item is the address/port a player should connect to.
    // The second item is a regular expression to match against the player's address.
    // If no second item is provided, it defaults to ".*".
    // The first item can be a "*", which means "use the pluginAddress".
    // The first item can be an interface name, which means use the first address of the named local interface.
    // The default value is "*".
    private String publicAddress = null;
    private String normalizedPublicAddress = null;

    // The address of our MC server host.
    // This address is given to the plugin on the other end of the connection if global setting sendPrivateAddress is true (the default).
    // This is an address/port.
    // The value can be "-", which means don't send a private address to the remote side no matter what the sendPrivateAddress setting is.
    // The value can be a "*", which means use the configured MC server address/port. If the wildcard address was configured, use the first address on the first interface.
    // The value can be an interface name, which means use the first address of the named local interface.
    // The default value is "*".
    private String privateAddress = null;
    private InetSocketAddress normalizedPrivateAddress = null;

    // Should all chat messages on the local server be sent to the remote server?
    private boolean sendChat = false;

    // Should all chat messages received from the remote server be echoed to local users?
    private boolean receiveChat = false;

    // Should all player join/quit/kick messages from the remote server be echoed to local users?
    private boolean announcePlayers = false;

    private Connection connection = null;
    private boolean allowReconnect = true;
    private int reconnectTask = -1;
    private boolean fastReconnect = false;
    private boolean connected = false;
    private String remoteVersion = null;
    private List<AddressMatch> remotePublicAddressMatches = null;
    private String remotePublicAddress = null;
    private String remotePrivateAddress = null;
    private String remoteCluster = null;

    private final Map<String,RemotePlayerImpl> remotePlayers = new HashMap<String,RemotePlayerImpl>();
    private final Map<String,RemoteWorldImpl> remoteWorlds = new HashMap<String,RemoteWorldImpl>();
    private final Map<String,RemoteGateImpl> remoteGates = new HashMap<String,RemoteGateImpl>();
    
    public Server(String name, String plgAddr, String key) throws ServerException {
        try {
            setName(name);
            setPluginAddress(plgAddr);
            setKey(key);
            setPublicAddress("*");
            setPrivateAddress("*");
            enabled = true;
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
    }

    public Server(ConfigurationNode node) throws ServerException {
        try {
            setName(node.getString("name"));
            setPluginAddress(node.getString("pluginAddress"));
            setKey(node.getString("key"));
            enabled = node.getBoolean("enabled", true);
            setPublicAddress(node.getString("publicAddress", "*"));
            setPrivateAddress(node.getString("privateAddress", "*"));
            setSendChat(node.getBoolean("sendChat", false));
            setReceiveChat(node.getBoolean("receiveChat", false));
            setAnnouncePlayers(node.getBoolean("announcePlayers", false));
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
    }

    /* RemoteServer interface */
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<RemotePlayer> getPlayers() {
        synchronized (remotePlayers) {
            return new HashSet<RemotePlayer>(remotePlayers.values());
        }
    }
    
    @Override
    public Set<RemoteWorld> getWorlds() {
        synchronized (remoteWorlds) {
            return new HashSet<RemoteWorld>(remoteWorlds.values());
        }
    }
    
    @Override
    public Set<org.bennedum.transporter.api.RemoteGate> getGates() {
        synchronized (remoteGates) {
            return new HashSet<org.bennedum.transporter.api.RemoteGate>(remoteGates.values());
        }
    }
 
    @Override
    public RemoteWorld getRemoteWorld(String worldName) {
        synchronized (remoteWorlds) {
            if (remoteWorlds.containsKey(worldName)) return remoteWorlds.get(worldName);
            worldName = worldName.toLowerCase();
            RemoteWorld foundWorld = null;
            for (RemoteWorld world : remoteWorlds.values()) {
                if (world.getName().toLowerCase().startsWith(worldName)) {
                    if (foundWorld != null) return null;
                    foundWorld = world;
                }
            }
            return foundWorld;
        }
    }

    @Override
    public org.bennedum.transporter.api.RemoteGate getRemoteGate(String gateName) {
        synchronized (remoteGates) {
            if (remoteGates.containsKey(gateName)) return remoteGates.get(gateName);
            gateName = gateName.toLowerCase();
            org.bennedum.transporter.api.RemoteGate foundGate = null;
            for (org.bennedum.transporter.api.RemoteGate gate : remoteGates.values()) {
                if (gate.getName().toLowerCase().startsWith(gateName)) {
                    if (foundGate != null) return null;
                    foundGate = gate;
                }
            }
            return foundGate;
        }
    }
    
    @Override
    public boolean isConnected() {
        if (connection == null) return false;
        return connection.isOpen();
    }
    
    /* End RemoteServer interface */
    
    public void setName(String name) throws ServerException {
        if (name == null)
            throw new ServerException("name is required");
        if (! isValidName(name))
            throw new ServerException("name is not valid");
        this.name = name;
    }

    public String getPluginAddress() {
        return pluginAddress;
    }

    public void setPluginAddress(String addr) {
        if (addr == null)
            throw new IllegalArgumentException("pluginAddress is required");
        try {
            Network.makeInetSocketAddress(addr, "localhost", Global.DEFAULT_PLUGIN_PORT, false);
        } catch (Exception e) {
            throw new IllegalArgumentException("pluginAddress: " + e.getMessage());
        }
        pluginAddress = addr;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        if ((key == null) || key.isEmpty())
            throw new IllegalArgumentException("key is required");
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean en) {
        enabled = en;
        if (enabled)
            connect();
        else
            disconnect(false);
    }

    /* Begin options */

    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String address) {
        if (address == null)
            throw new IllegalArgumentException("publicAddress is required");
        try {
            normalizePublicAddress(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("publicAddress: " + e.getMessage());
        }
        publicAddress = address;
    }

    public String getNormalizedPublicAddress() {
        return normalizedPublicAddress;
    }

    public String getPrivateAddress() {
        return privateAddress;
    }

    public void setPrivateAddress(String address) {
        if (address == null)
            throw new IllegalArgumentException("privateAddress is required");
        try {
            normalizePrivateAddress(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("privateAddress: " + e.getMessage());
        }
        privateAddress = address;
    }

    public InetSocketAddress getNormalizedPrivateAddress() {
        return normalizedPrivateAddress;
    }

    public boolean getSendChat() {
        return sendChat;
    }

    public void setSendChat(boolean b) {
        sendChat = b;
    }

    public boolean getReceiveChat() {
        return receiveChat;
    }

    public void setReceiveChat(boolean b) {
        receiveChat = b;
    }

    public boolean getAnnouncePlayers() {
        return announcePlayers;
    }

    public void setAnnouncePlayers(boolean b) {
        announcePlayers = b;
    }

    public void getOptions(Context ctx, String name) throws OptionsException, PermissionsException {
        options.getOptions(ctx, name);
    }

    public String getOption(Context ctx, String name) throws OptionsException, PermissionsException {
        return options.getOption(ctx, name);
    }

    public void setOption(Context ctx, String name, String value) throws OptionsException, PermissionsException {
        options.setOption(ctx, name, value);
    }

    @Override
    public void onOptionSet(Context ctx, String name, String value) {
        ctx.sendLog("option '%s' set to '%s' for server '%s'", name, value, getName());
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name;
    }

    /* End options */

    public String getRemotePublicAddress() {
        return remotePublicAddress;
    }

    public String getRemotePrivateAddress() {
        return remotePrivateAddress;
    }

    public String getReconnectAddressForClient(InetSocketAddress clientAddress) {
        String clientAddrStr = clientAddress.getAddress().getHostAddress();

        if (Network.getUsePrivateAddress() && (remotePrivateAddress != null)) {
            InetSocketAddress remoteAddr = (InetSocketAddress)connection.getChannel().socket().getRemoteSocketAddress();
            if (remoteAddr != null) {
                if (remoteAddr.getAddress().getHostAddress().equals(clientAddrStr)) {
                    Utils.debug("reconnect for client %s using private address %s", clientAddrStr, remotePrivateAddress);
                    return remotePrivateAddress;
                }
            }
        }

        if (remotePublicAddressMatches == null) {
            String[] parts = pluginAddress.split(":");
            return parts[0] + ":" + DEFAULT_MC_PORT;
        }

        for (AddressMatch match : remotePublicAddressMatches) {
            for (Pattern pattern : match.patterns)
                if (pattern.matcher(clientAddrStr).matches()) {
                    Utils.debug("client address %s matched pattern %s, so using %s", clientAddrStr, pattern.pattern(), match.connectTo);
                    return match.connectTo;
                }
        }
        return null;
    }

    // incoming connection
    public void setConnection(Connection conn) {
        connection = conn;
        connectionAttempts = 0;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getRemoteVersion() {
        return remoteVersion;
    }

    public String getRemoteCluster() {
        return remoteCluster;
    }

    public Map<String,Object> encode() {
        Map<String,Object> node = new HashMap<String,Object>();
        node.put("name", name);
        node.put("pluginAddress", pluginAddress);
        node.put("key", key);
        node.put("enabled", enabled);
        node.put("publicAddress", publicAddress);
        node.put("privateAddress", privateAddress);
        node.put("sendChat", sendChat);
        node.put("receiveChat", receiveChat);
        node.put("announcePlayers", announcePlayers);
        return node;
    }

    public boolean isIncoming() {
        return (connection != null) && connection.isIncoming();
    }

    public void connect() {
        if (isConnected() || Network.isStopped() || isIncoming()) return;
        allowReconnect = true;
        fastReconnect = false;
        cancelOutbound();
        if (connection != null)
            connection.close();
        connected = false;
        connection = new Connection(this, pluginAddress);
        connectionAttempts++;
        lastConnectionAttempt = System.currentTimeMillis();
        connection.open();
    }

    public void disconnect(boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
        cancelOutbound();
        if (connection == null) return;
        connection.close();
    }

    public boolean isConnecting() {
        return (reconnectTask != -1);
    }


    private void cancelOutbound() {
        if (reconnectTask != -1) {
            Utils.info("cancelling outbound connection attempt to server '%s'", getName());
            Utils.cancelTask(reconnectTask);
            reconnectTask = -1;
        }
    }

    private void reconnect() {
        cancelOutbound();
        if (! allowReconnect) return;
        if (isConnected() || Network.isStopped() || isIncoming()) return;
        if (fastReconnect)
            connect();
        else {
            int time = Network.getReconnectInterval();
            int skew = Network.getReconnectSkew();
            if (time < skew) time = skew;
            time += (Math.random() * (double)(skew * 2)) - skew;

            if (! connectionMessagesSuppressed())
                Utils.info("will attempt to reconnect to '%s' in about %d seconds", getName(), (time / 1000));
            reconnectTask = Utils.fireDelayed(new Runnable() {
                @Override
                public void run() {
                    reconnectTask = -1;
                    connect();
                }
            }, time);
        }

    }

    public boolean connectionMessagesSuppressed() {
        int limit = Network.getSuppressConnectionAttempts();
        return (limit >= 0) && (connectionAttempts > limit);
    }

    public void refresh() {
        if (! isConnected())
            connect();
        else {
            Message message = createMessage("refresh");
            sendMessage(message);
        }
    }

    public void checkKeepAlive() {
        if (! isConnected()) return;
        if ((System.currentTimeMillis() - connection.getLastMessageReceivedTime()) < RECV_KEEPALIVE_INTERVAL) return;
        Utils.warning("no keepalive received from server '%s'", name);
        fastReconnect = true;
        disconnect(true);
    }

    public void sendKeepAlive() {
        if (! isConnected()) return;
        if ((System.currentTimeMillis() - connection.getLastMessageSentTime()) < SEND_KEEPALIVE_INTERVAL) return;
        Utils.debug("sending keepalive to '%s'", name);
        Message message = createMessage("nop");
        sendMessage(message);
    }

    public void sendPing(Player player) {
        if (! isConnected()) return;
        final Message message = createMessage("ping");
        message.put("time", System.currentTimeMillis());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendGateAdded(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("gateAdded");
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateRenamed(String oldLocalName, String newName) {
        if (! isConnected()) return;
        Message message = createMessage("gateRenamed");
        message.put("oldName", oldLocalName);
        message.put("newName", newName);
        sendMessage(message);
    }

    public void sendGateRemoved(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("gateRemoved");
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateDestroyed(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("gateDestroyed");
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateAttach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("gateAttach");
        message.put("to", toGate.getLocalName());
        message.put("from", fromGate.getLocalName());
        sendMessage(message);
    }

    public void sendGateDetach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("gateDetach");
        message.put("to", toGate.getLocalName());
        message.put("from", fromGate.getLocalName());
        sendMessage(message);
    }

    public void sendReservation(Reservation res) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservation");
        message.put("reservation", res.encode());
        sendMessage(message);
    }

    public void sendReservationApproved(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationApproved");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendReservationDenied(long id, String reason) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationDenied");
        message.put("id", id);
        message.put("reason", reason);
        sendMessage(message);
    }

    public void sendReservationArrived(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationArrived");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendReservationTimeout(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationTimeout");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendRelayChat(Player player, String msg, Set<RemoteGate> toGates) {
        if (! isConnected()) return;
        Message message = createMessage("relayChat");
        message.put("player", player.getName());
        message.put("message", msg);
        if (toGates != null) {
            List<String> gates = new ArrayList<String>(toGates.size());
            for (RemoteGate gate : toGates)
                gates.add(gate.getLocalName());
            message.put("toGates", gates);
        }
        sendMessage(message);
    }

    public void sendLinkAdd(Player player, LocalGate fromGate, RemoteGate toGate) {
        if (! isConnected()) return;
        Message message = createMessage("linkAdd");
        message.put("from", fromGate.getLocalName());
        message.put("to", toGate.getLocalName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkAddComplete(String playerName, LocalGate fromGate, RemoteGate toGate) {
        if (! isConnected()) return;
        Message message = createMessage("linkAddComplete");
        message.put("from", fromGate.getFullName());
        message.put("to", toGate.getFullName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendLinkRemove(Player player, LocalGate fromGate, RemoteGate toGate) {
        if (! isConnected()) return;
        Message message = createMessage("linkRemove");
        message.put("from", fromGate.getFullName());
        message.put("to", toGate.getFullName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkRemoveComplete(String playerName, LocalGate fromGate, RemoteGate toGate) {
        if (! isConnected()) return;
        Message message = createMessage("linkRemoveComplete");
        message.put("from", fromGate.getFullName());
        message.put("to", toGate.getFullName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendPlayerChangedWorld(Player player, World world) {
        if (! isConnected()) return;
        Message message = createMessage("playerChangedWorld");
        message.put("player", player.getName());
        message.put("world", world.getName());
        sendMessage(message);
    }

    public void sendPlayerJoined(Player player) {
        if (! isConnected()) return;
        Message message = createMessage("playerJoined");
        message.put("name", player.getName());
        message.put("displayName", player.getDisplayName());
        message.put("worldName", player.getWorld().getName());
        sendMessage(message);
    }

    public void sendPlayerQuit(Player player) {
        if (! isConnected()) return;
        Message message = createMessage("playerQuit");
        message.put("name", player.getName());
        sendMessage(message);
    }

    public void sendPlayerKicked(Player player) {
        if (! isConnected()) return;
        Message message = createMessage("playerKicked");
        message.put("name", player.getName());
        sendMessage(message);
    }

    // Connection callbacks, called from main network thread.
    // If the task is going to take a while, use a worker thread.

    // outbound connection
    public void onConnected(String version) {
        allowReconnect = true;
        connected = true;
        connectionAttempts = 0;
        remoteVersion = version;
        cancelOutbound();
        Utils.info("connected to '%s' (%s), running v%s", getName(), connection.getName(), remoteVersion);
        receiveRefresh();
    }

    public void onDisconnected() {
        if (connected) {
            Utils.info("disconnected from '%s' (%s)", getName(), connection.getName());
            connected = false;
        }
        connection = null;
        Gates.remove(this);
        Players.remove(this);
        reconnect();
    }

    public void onMessage(Message message) {
        String error = message.getString("error");
        if (error != null) {
            Utils.warning("server '%s' complained: %s", getName(), error);
            return;
        }
        String command = message.getString("command");
        if (command == null) {
            Utils.warning("missing command from connection with %s", connection);
            disconnect(true);
            return;
        }
        Message response = null;

        Utils.debug("received command '%s' from %s", command, getName());
        try {
            if (command.equals("nop")) return;
            if (command.equals("error")) return;
            if (command.equals("ping"))
                receivePing(message);
            else if (command.equals("pong"))
                receivePong(message);
            else if (command.equals("refresh"))
                receiveRefresh();
            else if (command.equals("refreshData"))
                receiveRefreshData(message);
            else if (command.equals("gateAdded"))
                receiveGateAdded(message);
            else if (command.equals("gateRenamed"))
                receiveGateRenamed(message);
            else if (command.equals("gateRemoved"))
                receiveGateRemoved(message);
            else if (command.equals("gateDestroy"))
                receiveGateDestroy(message);
            else if (command.equals("gateAttach"))
                receiveGateAttach(message);
            else if (command.equals("gateDetach"))
                receiveGateDetach(message);
            else if (command.equals("reservation"))
                receiveReservation(message);
            else if (command.equals("reservationApproved"))
                receiveReservationApproved(message);
            else if (command.equals("reservationDenied"))
                receiveReservationDenied(message);
            else if (command.equals("reservationArrived"))
                receiveReservationArrived(message);
            else if (command.equals("reservationTimeout"))
                receiveReservationTimeout(message);
            else if (command.equals("relayChat"))
                receiveRelayChat(message);
            else if (command.equals("linkAdd"))
                receiveLinkAdd(message);
            else if (command.equals("linkAddComplete"))
                receiveLinkAddComplete(message);
            else if (command.equals("linkRemove"))
                receiveLinkRemove(message);
            else if (command.equals("linkRemoveComplete"))
                receiveLinkRemoveComplete(message);
            else if (command.equals("playerChangedWorld"))
                receivePlayerChangedWorld(message);
            else if (command.equals("playerJoined"))
                receivePlayerJoined(message);
            else if (command.equals("playerQuit"))
                receivePlayerQuit(message);
            else if (command.equals("playerKicked"))
                receivePlayerKicked(message);

            else
                throw new ServerException("unknown command '%s'", command);
        } catch (TransporterException te) {
            Utils.warning("while processing command '%s' from '%s': %s", command, getName(), te.getMessage());
            response = createMessage("error");
            response.put("success", false);
            response.put("error", te.getMessage());
        } catch (Throwable t) {
            Utils.severe(t, "while processing command '%s' from '%s':", command, getName());
            response = createMessage("error");
            response.put("success", false);
            response.put("error", t.getMessage());
        }
        if ((response != null) && isConnected())
            sendMessage(response);
    }

    // Command processing

    private void receivePing(Message message) {
        message.put("command", "pong");
        sendMessage(message);
    }

    private void receivePong(Message message) {
        long diff = System.currentTimeMillis() - message.getLong("time");
        String playerName = message.getString("player");
        Context ctx = new Context(playerName);
        ctx.send("ping to '%s' took %d millis", name, diff);
    }
    
    private void receiveRefresh() {
        if (! Utils.isMainThread()) {
            Utils.fire(new Runnable() {
                @Override
                public void run() {
                    receiveRefresh();
                }
            });
            return;
        }
        if (! isConnected()) return;
        
        Message out = createMessage("refreshData");

        out.put("publicAddress", normalizedPublicAddress);
        out.put("cluster", Network.getClusterName());

        // NAT stuff
        if (Network.getSendPrivateAddress() &&
            (! privateAddress.equals("-")))
            out.put("privateAddress",
                    normalizedPrivateAddress.getAddress().getHostAddress() + ":" +
                    normalizedPrivateAddress.getPort());

        // worlds
        List<String> worlds = new ArrayList<String>();
        for (World world : Global.plugin.getServer().getWorlds())
            worlds.add(world.getName());
        out.put("worlds", worlds);
        
        // players
        List<Message> players = new ArrayList<Message>();
        for (Player player : Global.plugin.getServer().getOnlinePlayers()) {
            Message msg = new Message();
            msg.put("name", player.getName());
            msg.put("displayName", player.getDisplayName());
            msg.put("worldName", player.getWorld().getName());
            players.add(msg);
        }
        out.put("players", players);
        
        // gates
        List<String> gates = new ArrayList<String>();
        for (LocalGate gate : Gates.getLocalGates())
            gates.add(gate.getLocalName());
        out.put("gates", gates);

        sendMessage(out);
    }

    private void receiveRefreshData(Message message) throws ServerException {
        remotePublicAddress = message.getString("publicAddress");
        remoteCluster = message.getString("cluster");
        try {
            expandPublicAddress(remotePublicAddress);
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
        Utils.debug("received publicAddress '%s' from '%s'", remotePublicAddress, getName());

        // NAT stuff
        remotePrivateAddress = message.getString("privateAddress");
        Utils.debug("received privateAddress '%s' from '%s'", remotePrivateAddress, getName());

        // worlds
        Collection<String> worlds = message.getStringList("worlds");
        if (worlds == null)
            throw new ServerException("world list required");
        synchronized (remoteWorlds) {
            remoteWorlds.clear();
            for (String name : worlds) {
                try {
                    RemoteWorldImpl world = new RemoteWorldImpl(this, name);
                    remoteWorlds.put(world.getName(), world);
                } catch (IllegalArgumentException iae) {
                    Utils.warning("received bad world from '%s'", getName());
                }
            }
            Utils.debug("received %d worlds from '%s'", remoteWorlds.size(), getName());
        }
        
        // players
        Collection<Message> players = message.getMessageList("players");
        if (players == null)
            throw new ServerException("player list required");
        synchronized (remotePlayers) {
            remotePlayers.clear();
            for (Message msg : players) {
                try {
                    RemotePlayerImpl player = new RemotePlayerImpl(this, msg.getString("name"), msg.getString("displayName"), msg.getString("worldName"));
                    remotePlayers.put(player.getName(), player);
                } catch (IllegalArgumentException iae) {
                    Utils.warning("received bad player from '%s'", getName());
                }
            }
            Utils.debug("received %d players from '%s'", remotePlayers.size(), getName());
        }
        
        // gates
        Collection<String> gates = message.getStringList("gates");
        if (gates == null)
            throw new ServerException("gate list required");
        synchronized (remoteGates) {
            remoteGates.clear();
            for (String name : gates) {
                try {
                    RemoteGateImpl gate = new RemoteGateImpl(this, name);
                    remoteGates.put(gate.getLocalName(), gate);
                } catch (IllegalArgumentException iae) {
                    Utils.warning("received bad gate from '%s'", getName());
                }
            }
            Utils.debug("received %d gates from '%s'", remoteGates.size(), getName());
        }
    }

    private void receiveGateAdded(Message message) {
        synchronized (remoteGates) {
            try {
                RemoteGateImpl gate = new RemoteGateImpl(this, message.getString("name"));
                remoteGates.put(gate.getLocalName(), gate);
                Utils.debug("received gate '%s' from '%s'", gate.getLocalName(), getName());
            } catch (IllegalArgumentException iae) {
                Utils.warning("received bad gate from '%s'", getName());
            }
        }
    }

    // TODO: redo this
    private void receiveGateRenamed(Message message) throws ServerException {
        String oldName = message.getString("oldName");
        if (oldName == null)
            throw new ServerException("missing oldName");
        String newName = message.getString("newName");
        if (newName == null)
            throw new ServerException("missing newName");
        
        
        RemoteGateImpl gate = (RemoteGateImpl)getRemoteGate(oldName);
        if (gate == null)
            throw new ServerException("old gate '%s' not found", oldName);
        if (! Utils.isMainThread()) {
            Utils.fire(new Runnable() {
                @Override
                public void run() {
                    
                    
                    
                }
            });
            return;
        }
        
        
        synchronized (remoteGates) {
        gate.rename(newName);
    }

    private void receiveGateRemoved(Message message) throws ServerException {
        String name = message.getString("name");
        if (name == null)
            throw new ServerException("missing name");
        
        gateName = Gate.makeFullName(this, gateName);
        final Gate gate = Gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);

        Utils.fire(new Runnable() {
            @Override
            public void run() {
                Gates.remove((RemoteGate)gate);
            }
        });
    }

    private void receiveDestroyGate(Message message) throws ServerException {
        String gateName = message.getString("name");
        if (gateName == null)
            throw new ServerException("missing name");
        gateName = Gate.makeFullName(this, gateName);
        final Gate gate = Gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);

        Utils.fire(new Runnable() {
            @Override
            public void run() {
                Gates.destroy((RemoteGate)gate);
            }
        });
    }

    private void receiveAttachGate(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        final Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        final Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        Utils.fire(new Runnable() {
            @Override
            public void run() {
                toGate.attach(fromGate);
            }
        });
    }

    private void receiveDetachGate(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        final Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        final Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        Utils.fire(new Runnable() {
            @Override
            public void run() {
                toGate.detach(fromGate);
            }
        });
    }

    private void receiveSendReservation(Message message) throws ServerException {
        Message resMsg = message.getMessage("reservation");
        if (resMsg == null)
            throw new ServerException("missing reservation");
        Reservation res;
        try {
            res = new Reservation(resMsg, this);
            res.receive();
        } catch (ReservationException e) {
            throw new ServerException("invalid reservation: %s", e.getMessage());
        }
    }

    private void receiveReservationApproved(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.approved();
    }

    private void receiveReservationDenied(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        String reason = message.getString("reason");
        if (reason == null)
            throw new ServerException("missing reason");
        res.denied(reason);
    }

    private void receiveReservationArrived(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.arrived();
    }

    private void receiveReservationTimeout(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.timeout();
    }

    private void receiveRelayChat(Message message) throws ServerException {
        String player = message.getString("player");
        if (player == null)
            throw new ServerException("missing player");

        String displayName = message.getString("displayName");
        if (displayName == null)
            displayName = player;

        String world = message.getString("world");
        if (world == null)
            throw new ServerException("missing world");

        String msg = message.getString("message");
        if (msg == null)
            throw new ServerException("missing message");

        List<String> toGates = message.getStringList("toGates");
        if (toGates != null)
            for (int i = 0; i < toGates.size(); i++)
                toGates.set(i, Gate.makeLocalName(toGates.get(i)));

        Chat.receive(player, displayName, world, this, msg, toGates);
    }

    private void receiveAddLink(Message message) throws ServerException, PermissionsException, EconomyException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        // now we flip the sense!!!

        LocalGate fromGateLocal = (LocalGate)toGate;
        RemoteGate toGateRemote = (RemoteGate)fromGate;

        Permissions.require(fromGateLocal.getWorldName(), playerName, "trp.gate.link.add." + fromGateLocal.getFullName());

        if (fromGateLocal.isLinked() && (! fromGateLocal.getMultiLink()))
            throw new ServerException("gate '%s' cannot accept multiple links", fromGate.getFullName());

        if (! Config.getAllowLinkServer())
            throw new ServerException("linking to remote server gates is not permitted");

        Economy.requireFunds(playerName, fromGateLocal.getLinkServerCost());

        if (! fromGateLocal.addLink(toGateRemote.getFullName()))
            throw new ServerException("gate '%s' already links to '%s'", fromGateLocal.getFullName(), toGateRemote.getFullName());

        Utils.info("added link from '%s' to '%s'", fromGateLocal.getFullName(), toGateRemote.getFullName());

        if (Economy.deductFunds(playerName, fromGateLocal.getLinkServerCost()))
            Utils.info("debited %s for off-server linking", Economy.format(fromGateLocal.getLinkServerCost()));

        doAddLinkComplete(playerName, fromGateLocal, toGateRemote);
    }

    private void receiveAddLinkComplete(Message message) throws ServerException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        // now we flip the sense!!!

        final LocalGate fromGateLocal = (LocalGate)toGate;
        final RemoteGate toGateRemote = (RemoteGate)fromGate;

        final Context ctx = new Context(playerName);
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                ctx.sendLog("added link from '%s' to '%s'", toGateRemote.getName(ctx), fromGateLocal.getName(ctx));
            }
        });
    }

    private void receiveRemoveLink(Message message) throws ServerException, PermissionsException, EconomyException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        // now we flip the sense!!!

        LocalGate fromGateLocal = (LocalGate)toGate;
        RemoteGate toGateRemote = (RemoteGate)fromGate;

        Permissions.require(fromGateLocal.getWorldName(), playerName, "trp.gate.link.remove." + fromGateLocal.getFullName());

        if (! fromGateLocal.removeLink(toGateRemote.getFullName()))
            throw new ServerException("gate '%s' doesn't link to '%s'", fromGateLocal.getFullName(), toGateRemote.getFullName());

        Utils.info("remove link from '%s' to '%s'", fromGateLocal.getFullName(), toGateRemote.getFullName());

        doRemoveLinkComplete(playerName, fromGateLocal, toGateRemote);
    }

    private void receiveRemoveLinkComplete(Message message) throws ServerException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is not local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);

        // now we flip the sense!!!

        final LocalGate fromGateLocal = (LocalGate)toGate;
        final RemoteGate toGateRemote = (RemoteGate)fromGate;

        final Context ctx = new Context(playerName);
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                ctx.sendLog("removed link from '%s' to '%s'", toGateRemote.getName(ctx), fromGateLocal.getName(ctx));
            }
        });
    }

    private void receivePlayerChangedWorld(Message message) throws ServerException {
        String playerName = message.getString("player");
        if (playerName == null)
            throw new ServerException("missing player");
        String worldName = message.getString("world");
        if (worldName == null)
            throw new ServerException("missing world");
        Players.remoteChangeWorld(this, playerName, worldName);
    }

    private void receivePlayerJoined(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        String displayName = message.getString("displayName");
        if (displayName == null)
            throw new ServerException("missing displayName");
        String worldName = message.getString("world");
        if (worldName == null)
            throw new ServerException("missing world");
        boolean announce = message.getBoolean("announce");
        Players.remoteJoin(this, playerName, displayName, worldName, announce);
    }

    private void receivePlayerQuit(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean announce = message.getBoolean("announce");
        Players.remoteQuit(this, playerName, announce);
    }

    private void receivePlayerKicked(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean announce = message.getBoolean("announce");
        Players.remoteKick(this, playerName, announce);
    }

    // Utility methods

    private Message createMessage(String command) {
        Message m = new Message();
        m.put("command", command);
        return m;
    }

    private void sendMessage(Message message) {
        Utils.debug("sending command '%s' to %s", message.getString("command", "<none>"), name);
        connection.sendMessage(message, true);
    }

    private void normalizePrivateAddress(String addrStr) {
        if (addrStr.equals("-")) {
            normalizedPrivateAddress = null;
            return;
        }
        String defAddr = "localhost";
        InetAddress a = Network.getInterfaceAddress();
        if (a != null) defAddr = a.getHostAddress();
        normalizedPrivateAddress = Network.makeInetSocketAddress(addrStr, defAddr, Global.plugin.getServer().getPort(), false);
    }

    private void normalizePublicAddress(String addrStr) {
        StringBuilder sb = new StringBuilder();

        String patternMaps[] = addrStr.split("\\s+");
        for (String patternMap : patternMaps) {
            String items[] = patternMap.split("/");
            if (items.length > 1)
                for (int i = 1; i < items.length; i++) {
                    try {
                        Pattern.compile(items[i]);
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("invalid pattern: " + items[i]);
                    }
                }

            String[] parts = items[0].split(":");
            String addrPart;
            String portPart;
            if (parts[0].matches("^\\d+$")) {
                addrPart = "*";
                portPart = parts[0];
            } else {
                addrPart = parts[0];
                portPart = (parts.length > 1) ? parts[1] : Global.plugin.getServer().getPort() + "";
            }

            if (! addrPart.equals("*")) {
                try {
                    NetworkInterface iface = NetworkInterface.getByName(addrPart);
                    InetAddress a = Network.getInterfaceAddress(iface);
                    if (a != null)
                        addrPart = a.getHostAddress();
                } catch (SocketException e) {
                    // assume address is a DNS name or IP address
                }
            }

            try {
                int port = Integer.parseInt(portPart);
                if ((port < 1) || (port > 65535))
                    throw new IllegalArgumentException("invalid port " + portPart);
                portPart = port + "";
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("invalid port " + portPart);
            }

            sb.append(addrPart).append(":").append(portPart);
            if (items.length > 1)
                for (int i = 1; i < items.length; i++)
                    sb.append("/").append(items[i]);
            sb.append(" ");
        }

        normalizedPublicAddress = sb.toString().trim();
    }

    // called on the receiving side to expand the address given by the sending side
    private void expandPublicAddress(String addrStr) {
        if (addrStr == null)
            throw new IllegalArgumentException("publicAddress is required");

        remotePublicAddressMatches = new ArrayList<AddressMatch>();
        StringBuilder sb = new StringBuilder();

        String patternMaps[] = addrStr.split("\\s+");
        for (String patternMap : patternMaps) {
            Set<Pattern> patterns = new HashSet<Pattern>();
            String items[] = patternMap.split("/");
            if (items.length == 1)
                patterns.add(Pattern.compile(".*"));
            else
                for (int i = 1; i < items.length; i++) {
                    try {
                        patterns.add(Pattern.compile(items[i]));
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("invalid pattern: " + items[i]);
                    }
                }

            String[] parts = items[0].split(":");
            String address = parts[0];
            int port = DEFAULT_MC_PORT;
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                    if ((port < 1) || (port > 65535))
                        throw new IllegalArgumentException("invalid port " + parts[1]);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("invalid port " + parts[1]);
                }
            }
            if (address.equals("*"))
                address = pluginAddress.split(":")[0];

            AddressMatch match = new AddressMatch();
            match.connectTo = address + ":" + port;
            match.patterns = patterns;
            remotePublicAddressMatches.add(match);
            sb.append(address).append(":").append(port);
            if (items.length > 1)
                for (int i = 1; i < items.length; i++)
                    sb.append("/").append(items[i]);
            sb.append(" ");
        }
        remotePublicAddress = sb.toString().trim();
    }


    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Server[");
        buf.append(name).append(",");
        buf.append(pluginAddress).append(",");
        buf.append(key);
        buf.append("]");
        return buf.toString();
    }

    private class AddressMatch {
        String connectTo;
        Set<Pattern> patterns;
    }
    
}
