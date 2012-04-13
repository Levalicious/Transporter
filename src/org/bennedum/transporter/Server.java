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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.api.Callback;
import org.bennedum.transporter.api.RemoteEndpoint;
import org.bennedum.transporter.api.RemoteException;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bennedum.transporter.net.Connection;
import org.bennedum.transporter.net.Network;
import org.bennedum.transporter.net.Message;
import org.bukkit.Location;
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

    private Map<String,RemotePlayerImpl> remotePlayers = new HashMap<String,RemotePlayerImpl>();
    private Map<String,RemoteWorldImpl> remoteWorlds = new HashMap<String,RemoteWorldImpl>();
    private Map<String,RemoteEndpointImpl> remoteEndpoints = new HashMap<String,RemoteEndpointImpl>();
    
    private long nextRequestId = 1;
    private Map<Long,Callback<Message>> requests = new HashMap<Long,Callback<Message>>();
    
    // TODO: add a way to expire old API requests
    
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
    public Set<RemotePlayer> getRemotePlayers() {
        return new HashSet<RemotePlayer>(remotePlayers.values());
    }
    
    @Override
    public Set<RemoteWorld> getRemoteWorlds() {
        return new HashSet<RemoteWorld>(remoteWorlds.values());
    }
    
    @Override
    public Set<RemoteEndpoint> getRemoteEndpoints() {
        return new HashSet<RemoteEndpoint>(remoteEndpoints.values());
    }
 
    @Override
    public RemoteWorld getRemoteWorld(String worldName) {
        return remoteWorlds.get(worldName);
    }

    @Override
    public RemoteEndpoint getRemoteEndpoint(String epName) {
        return remoteEndpoints.get(epName);
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

    // Connection callbacks, called from main network thread.

    // outbound connection
    public void onConnected(String version) {
        allowReconnect = true;
        connected = true;
        connectionAttempts = 0;
        remoteVersion = version;
        cancelOutbound();
        Utils.info("connected to '%s' (%s), running v%s", getName(), connection.getName(), remoteVersion);
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                receiveRefresh();
            }
        });
    }

    public void onDisconnected() {
        if (connected) {
            Utils.info("disconnected from '%s' (%s)", getName(), connection.getName());
            connected = false;
        }
        connection = null;
        reconnect();
        final Server me = this;
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                remotePlayers.clear();
                remoteEndpoints.clear();
                remoteWorlds.clear();
                Endpoints.removeEndpointsForServer(me);
            }
        });
    }

    public void onMessage(final Message message) {
        String error = message.getString("error");
        if (error != null) {
            Utils.warning("server '%s' complained: %s", getName(), error);
            return;
        }
        final String command = message.getString("command");
        if (command == null) {
            Utils.warning("missing command from connection with %s", connection);
            disconnect(true);
            return;
        }
        Utils.debug("received command '%s' from %s", command, getName());
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                receiveMessage(message, command);
            }
        });
    }
        
    // Remote commands
    
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

    public void sendEndpointAdded(LocalEndpointImpl ep) {
        if (! isConnected()) return;
        Message message = createMessage("endpointAdded");
        if (ep instanceof LocalGateImpl)
            message.put("type", "gate");
        else if (ep instanceof LocalVolumeImpl)
            message.put("type", "volume");
        else
            return;
        message.put("name", ep.getLocalName());
        sendMessage(message);
    }

    public void sendEndpointRenamed(String oldLocalName, String newName) {
        if (! isConnected()) return;
        Message message = createMessage("endpointRenamed");
        message.put("oldName", oldLocalName);
        message.put("newName", newName);
        sendMessage(message);
    }

    public void sendEndpointRemoved(LocalEndpointImpl ep) {
        if (! isConnected()) return;
        Message message = createMessage("endpointRemoved");
        message.put("name", ep.getLocalName());
        sendMessage(message);
    }

    public void sendEndpointDestroyed(LocalEndpointImpl ep) {
        if (! isConnected()) return;
        Message message = createMessage("endpointDestroyed");
        message.put("name", ep.getLocalName());
        sendMessage(message);
    }

    public void sendEndpointAttach(RemoteEndpointImpl toEp, LocalEndpointImpl fromEp) {
        if (! isConnected()) return;
        Message message = createMessage("endpointAttach");
        message.put("to", toEp.getLocalName());
        message.put("from", fromEp.getLocalName());
        sendMessage(message);
    }

    public void sendEndpointDetach(RemoteEndpointImpl toEp, LocalEndpointImpl fromEp) {
        if (! isConnected()) return;
        Message message = createMessage("endpointDetach");
        message.put("to", toEp.getLocalName());
        message.put("from", fromEp.getLocalName());
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

    public void sendChat(Player player, String msg, Set<RemoteGateImpl> toGates) {
        if (! isConnected()) return;
        Message message = createMessage("chat");
        message.put("player", player.getName());
        message.put("message", msg);
        if (toGates != null) {
            List<String> gates = new ArrayList<String>(toGates.size());
            for (RemoteGateImpl gate : toGates)
                gates.add(gate.getLocalName());
            message.put("toGates", gates);
        }
        sendMessage(message);
    }

    public void sendLinkAdd(Player player, LocalEndpointImpl fromEp, RemoteEndpointImpl toEp) {
        if (! isConnected()) return;
        Message message = createMessage("linkAdd");
        message.put("from", fromEp.getLocalName());
        message.put("to", toEp.getLocalName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkAddComplete(String playerName, LocalEndpointImpl fromEp, RemoteEndpointImpl toEp) {
        if (! isConnected()) return;
        Message message = createMessage("linkAddComplete");
        message.put("from", fromEp.getLocalName());
        message.put("to", toEp.getLocalName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendLinkRemove(Player player, LocalEndpointImpl fromEp, RemoteEndpointImpl toEp) {
        if (! isConnected()) return;
        Message message = createMessage("linkRemove");
        message.put("from", fromEp.getLocalName());
        message.put("to", toEp.getLocalName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkRemoveComplete(String playerName, LocalEndpointImpl fromEp, RemoteEndpointImpl toEp) {
        if (! isConnected()) return;
        Message message = createMessage("linkRemoveComplete");
        message.put("from", fromEp.getLocalName());
        message.put("to", toEp.getLocalName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendPlayerChangedWorld(Player player) {
        if (! isConnected()) return;
        Message message = createMessage("playerChangedWorld");
        message.put("player", player.getName());
        message.put("world", player.getWorld().getName());
        sendMessage(message);
    }

    public void sendPlayerJoined(Player player, boolean announce) {
        if (! isConnected()) return;
        Message message = createMessage("playerJoined");
        message.put("name", player.getName());
        message.put("displayName", player.getDisplayName());
        message.put("worldName", player.getWorld().getName());
        message.put("announce", announce);
        sendMessage(message);
    }

    public void sendPlayerQuit(Player player, boolean announce) {
        if (! isConnected()) return;
        Message message = createMessage("playerQuit");
        message.put("name", player.getName());
        message.put("announce", announce);
        sendMessage(message);
    }

    public void sendPlayerKicked(Player player, boolean announce) {
        if (! isConnected()) return;
        Message message = createMessage("playerKicked");
        message.put("name", player.getName());
        message.put("announce", announce);
        sendMessage(message);
    }

    public void sendAPI(Callback<Message> cb, String api, Message args) {
        if (! isConnected()) {
            cb.onFailure(new RemoteException("not connected"));
            return;
        }
        long rid = nextRequestId++;
        Message out = createMessage("api");
        out.put("method", api);
        out.put("requestId", rid);
        out.put("args", args);
        cb.setRequestId(rid);
        requests.put(rid, cb);
        sendMessage(args);
    }
    
    // End remote commands
    
    // Message handling

    // run in the main thread
    private void receiveMessage(Message message, String command) {
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
            else if (command.equals("endpointAdded"))
                receiveEndpointAdded(message);
            else if (command.equals("endpointRenamed"))
                receiveEndpointRenamed(message);
            else if (command.equals("endpointRemoved"))
                receiveEndpointRemoved(message);
            else if (command.equals("endpointDestroyed"))
                receiveEndpointDestroyed(message);
            else if (command.equals("endpointAttach"))
                receiveEndpointAttach(message);
            else if (command.equals("endpointDetach"))
                receiveEndpointDetach(message);
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
            else if (command.equals("chat"))
                receiveChat(message);
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
            else if (command.equals("api"))
                receiveAPI(message);
            else if (command.equals("apiResult"))
                receiveAPIResult(message);
            else
                Utils.warning("receive unrecognized command '%s' from '%s'", command, getName());
        } catch (TransporterException te) {
            Utils.warning( "while processing command '%s' from '%s': %s", command, getName(), te.getMessage());
            if (isConnected()) {
                Message response = createMessage("error");
                response.put("success", false);
                response.put("error", te.getMessage());
                sendMessage(response);
            }
        } catch (Throwable t) {
            Utils.severe(t, "while processing command '%s' from '%s': %s", command, getName(), t.getMessage());
            if (isConnected()) {
                Message response = createMessage("error");
                response.put("success", false);
                response.put("error", t.getMessage());
                sendMessage(response);
            }
        }
    }

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
        
        // endpoints
        List<Message> endpoints = new ArrayList<Message>();
        for (LocalEndpointImpl ep : Endpoints.getLocalEndpoints()) {
            Message epm = new Message();
            if (ep instanceof LocalGateImpl)
                epm.put("type", "gate");
            else if (ep instanceof LocalVolumeImpl)
                epm.put("type", "volume");
            else
                continue;
            epm.put("name", ep.getLocalName());
            endpoints.add(epm);
        }
        out.put("endpoints", endpoints);

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
        remoteWorlds.clear();
        for (String worldName : worlds) {
            try {
                RemoteWorldImpl world = new RemoteWorldImpl(this, worldName);
                remoteWorlds.put(world.getName(), world);
            } catch (IllegalArgumentException iae) {
                Utils.warning("received bad world from '%s'", getName());
            }
        }
        Utils.debug("received %d worlds from '%s'", remoteWorlds.size(), getName());
        
        // players
        Collection<Message> players = message.getMessageList("players");
        if (players == null)
            throw new ServerException("player list required");
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
        
        // endpoints
        Collection<Message> endpoints = message.getMessageList("endpoints");
        if (endpoints == null)
            throw new ServerException("endpoint list required");
        remoteEndpoints.clear();
        Endpoints.removeEndpointsForServer(this);
        for (Message epm : endpoints) {
            String epType = epm.getString("type");
            String epName = epm.getString("name");
            RemoteEndpointImpl ep;
            try {
                if (epType.equals("gate"))
                    ep = new RemoteGateImpl(this, epName);
                else if (epType.equals("volume"))
                    ep = new RemoteVolumeImpl(this, epName);
                else
                    continue;
                remoteEndpoints.put(ep.getLocalName(), ep);
                try {
                    Endpoints.add(ep);
                } catch (EndpointException ee) {
                    remoteEndpoints.remove(ep.getLocalName());
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException iae) {
                Utils.warning("received bad endpoint from '%s'", getName());
            }
        }
        Utils.debug("received %d endpoints from '%s'", remoteEndpoints.size(), getName());
    }

    private void receiveEndpointAdded(Message message) {
        String epType = message.getString("type");
        String epName = message.getString("name");
        RemoteEndpointImpl ep;
        try {
            if (epType.equals("gate"))
                ep = new RemoteGateImpl(this, epName);
            else if (epType.equals("volume"))
                ep = new RemoteVolumeImpl(this, epName);
            else
                throw new IllegalArgumentException();
            remoteEndpoints.put(ep.getLocalName(), ep);
            try {
                Endpoints.add(ep);
            } catch (EndpointException ee) {
                remoteEndpoints.remove(ep.getLocalName());
                throw new IllegalArgumentException();
            }
            Utils.debug("received endpoint '%s' from '%s'", ep.getLocalName(), getName());
        } catch (IllegalArgumentException iae) {
            Utils.warning("received bad endpoint from '%s'", getName());
        }
    }

    private void receiveEndpointRenamed(Message message) throws ServerException {
        String oldName = message.getString("oldName");
        if (oldName == null)
            throw new ServerException("missing oldName");
        String newName = message.getString("newName");
        if (newName == null)
            throw new ServerException("missing newName");
        
        RemoteEndpointImpl ep = (RemoteEndpointImpl)getRemoteEndpoint(oldName);
        if (ep == null)
            throw new ServerException("old endpoint '%s' not found", oldName);
        String oldFullName = ep.getFullName();
        remoteEndpoints.remove(oldName);
        ep.setName(newName);
        remoteEndpoints.put(ep.getLocalName(), ep);
        Endpoints.rename(ep, oldFullName);
    }

    private void receiveEndpointRemoved(Message message) throws ServerException {
        String lname = message.getString("name");
        if (lname == null)
            throw new ServerException("missing name");
        RemoteEndpointImpl ep = remoteEndpoints.get(lname);
        if (ep == null)
            throw new ServerException("unknown endpoint '%s'", lname);
        remoteEndpoints.remove(lname);
        try {
            Endpoints.remove(ep);
        } catch (EndpointException ee) {}
    }

    private void receiveEndpointDestroyed(Message message) throws ServerException {
        String lname = message.getString("name");
        if (lname == null)
            throw new ServerException("missing name");
        RemoteEndpointImpl ep = remoteEndpoints.get(lname);
        if (ep == null)
            throw new ServerException("unknown endpoint '%s'", lname);
        remoteEndpoints.remove(lname);
        Endpoints.destroy(ep, false);
    }

    private void receiveEndpointAttach(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        LocalEndpointImpl toEp = Endpoints.getLocalEndpoint(toName);
        if (toEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl fromEp = remoteEndpoints.get(fromName);
        if (fromEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);
        toEp.attach(fromEp);
    }

    private void receiveEndpointDetach(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        LocalEndpointImpl toEp = Endpoints.getLocalEndpoint(toName);
        if (toEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl fromEp = remoteEndpoints.get(fromName);
        if (fromEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);
        toEp.detach(fromEp);
    }

    private void receiveReservation(Message message) throws ServerException {
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

    private void receiveChat(Message message) throws ServerException {
        String playerName = message.getString("player");
        if (playerName == null)
            throw new ServerException("missing player");
        String msg = message.getString("message");
        if (msg == null)
            throw new ServerException("missing message");
        List<String> toGates = message.getStringList("toGates");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null)
            throw new ServerException("unknown player '%s'", playerName);
        Chat.receive(player, msg, toGates);
    }

    private void receiveLinkAdd(Message message) throws TransporterException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");

        // reverse the sense of direction
        
        LocalEndpointImpl fromEp = Endpoints.getLocalEndpoint(toName);
        if (fromEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl toEp = remoteEndpoints.get(fromName);
        if (toEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);

        fromEp.addLink(new Context(playerName), toEp.getFullName());
        sendLinkAddComplete(playerName, fromEp, toEp);
    }

    private void receiveLinkAddComplete(Message message) throws ServerException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");

        // reverse the sense of direction
        
        LocalEndpointImpl fromEp = Endpoints.getLocalEndpoint(toName);
        if (fromEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl toEp = remoteEndpoints.get(fromName);
        if (toEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);

        Context ctx = new Context(playerName);
        ctx.sendLog("added link from '%s' to '%s'", toEp.getName(ctx), fromEp.getName(ctx));
    }

    private void receiveLinkRemove(Message message) throws TransporterException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");

        // reverse the sense of direction
        
        LocalEndpointImpl fromEp = Endpoints.getLocalEndpoint(toName);
        if (fromEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl toEp = remoteEndpoints.get(fromName);
        if (toEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);

        fromEp.removeLink(new Context(playerName), toEp.getFullName());
        sendLinkRemoveComplete(playerName, fromEp, toEp);
    }

    private void receiveLinkRemoveComplete(Message message) throws ServerException {
        String playerName = message.getString("player");

        // "to" and "from" are from perspective of message sender!!!

        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");

        // reverse the sense of direction
        
        LocalEndpointImpl fromEp = Endpoints.getLocalEndpoint(toName);
        if (fromEp == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteEndpointImpl toEp = remoteEndpoints.get(fromName);
        if (toEp == null)
            throw new ServerException("unknown origin endpoint '%s'", fromName);

        Context ctx = new Context(playerName);
        ctx.sendLog("removed link from '%s' to '%s'", toEp.getName(ctx), fromEp.getName(ctx));
    }

    private void receivePlayerChangedWorld(Message message) throws ServerException {
        String playerName = message.getString("player");
        if (playerName == null)
            throw new ServerException("missing player");
        String worldName = message.getString("world");
        if (worldName == null)
            throw new ServerException("missing world");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null)
            throw new ServerException("unknown player '%s'", playerName);
        player.setWorld(worldName);
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
        RemotePlayerImpl player = new RemotePlayerImpl(this, playerName, displayName, worldName);
        remotePlayers.put(playerName, player);
        if (announce && getAnnouncePlayers()) {
            String format = Config.getServerJoinFormat();
            format = format.replace("%player%", player.getDisplayName());
            format = format.replace("%world%", player.getRemoteWorld().getName());
            format = format.replace("%server%", getName());
            Global.plugin.getServer().broadcastMessage(format);
        }
    }

    private void receivePlayerQuit(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean announce = message.getBoolean("announce");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null)
            throw new ServerException("unknown player '%s'", playerName);
        remotePlayers.remove(playerName);
        if (announce && getAnnouncePlayers()) {
            String format = Config.getServerQuitFormat();
            format = format.replace("%player%", player.getDisplayName());
            format = format.replace("%world%", player.getRemoteWorld().getName());
            format = format.replace("%server%", getName());
            Global.plugin.getServer().broadcastMessage(format);
        }
    }

    private void receivePlayerKicked(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean announce = message.getBoolean("announce");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null)
            throw new ServerException("unknown player '%s'", playerName);
        remotePlayers.remove(playerName);
        if (announce && getAnnouncePlayers()) {
            String format = Config.getServerKickFormat();
            format = format.replace("%player%", player.getDisplayName());
            format = format.replace("%world%", player.getRemoteWorld().getName());
            format = format.replace("%server%", getName());
            Global.plugin.getServer().broadcastMessage(format);
        }
    }

    private void receiveAPI(Message message) throws ServerException {
        String api = message.getString("api");
        if (api == null)
            throw new ServerException("missing api");
        String method = message.getString("method");
        long rid = message.getLong("requestId");
        Message args = message.getMessage("args");
        
        Message out = createMessage("apiResult");
        out.put("requestId", rid);
        try {
            if (method.startsWith("world.")) {
                String subMethod = method.substring(6);
                World world = Global.plugin.getServer().getWorld(args.getString("world"));
                if (world == null)
                    throw new ServerException("world '%s' is unknown", args.getString("world"));
                if (subMethod.equals("getTime"))
                    out.put("result", world.getTime());
                else if (subMethod.equals("getFullTime"))
                    out.put("result", world.getFullTime());
                else
                    throw new ServerException("unknown method '%s'", method);
                
            } else if (method.startsWith("player.")) {
                String subMethod = method.substring(7);
                Player player = Global.plugin.getServer().getPlayer(args.getString("player"));
                if (player == null)
                    throw new ServerException("player '%s' is unknown", args.getString("player"));
                if (subMethod.equals("getWorld"))
                    out.put("result", player.getWorld().getName());
                else if (subMethod.equals("getLocation")) {
                    Message locMsg = new Message();
                    Location loc = player.getLocation();
                    locMsg.put("world", loc.getWorld().getName());
                    locMsg.put("x", loc.getX());
                    locMsg.put("y", loc.getY());
                    locMsg.put("z", loc.getZ());
                    out.put("result", locMsg);
                } else if (subMethod.equals("sendMessage")) {
                    player.sendMessage(message.getString("message"));
                } else if (subMethod.equals("sendRawMessage")) {
                    player.sendRawMessage(message.getString("message"));
                } else
                    throw new ServerException("unknown method '%s'", method);
                
                
            } else
                throw new ServerException("unknown method '%s'", method);
            
        } catch (Throwable t) {
            out.put("failure", t.getMessage());
        }
        sendMessage(out);
    }
    
    private void receiveAPIResult(Message message) throws ServerException {
        long rid = message.getLong("requestId");
        Callback<Message> cb = requests.remove(rid);
        if (cb == null)
            throw new ServerException("unknown requestId %s", rid);
        String failure = message.getString("failure");
        if (failure != null)
            cb.onFailure(new RemoteException(failure));
        else
            cb.onSuccess(message);
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
