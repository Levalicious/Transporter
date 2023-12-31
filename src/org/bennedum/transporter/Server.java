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

import java.lang.reflect.Method;
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
import org.bennedum.transporter.api.GateException;
import org.bennedum.transporter.api.GateType;
import org.bennedum.transporter.api.RemoteException;
import org.bennedum.transporter.api.RemoteGate;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;
import org.bennedum.transporter.api.ReservationException;
import org.bennedum.transporter.api.TransporterException;
import org.bennedum.transporter.api.event.RemoteGateCreateEvent;
import org.bennedum.transporter.api.event.RemoteGateDestroyEvent;
import org.bennedum.transporter.api.event.RemotePlayerChangeWorldEvent;
import org.bennedum.transporter.api.event.RemotePlayerDeathEvent;
import org.bennedum.transporter.api.event.RemotePlayerJoinEvent;
import org.bennedum.transporter.api.event.RemotePlayerKickEvent;
import org.bennedum.transporter.api.event.RemotePlayerQuitEvent;
import org.bennedum.transporter.api.event.RemoteServerConnectEvent;
import org.bennedum.transporter.api.event.RemoteServerDisconnectEvent;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bennedum.transporter.net.Connection;
import org.bennedum.transporter.net.Message;
import org.bennedum.transporter.net.Network;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
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
    private static final Map<String,Method> MESSAGE_HANDLERS = new HashMap<String,Method>();
    
    static {
        OPTIONS.add("pluginAddress");
        OPTIONS.add("key");
        OPTIONS.add("publicAddress");
        OPTIONS.add("privateAddress");
        OPTIONS.add("sendChat");
        OPTIONS.add("receiveChat");
        OPTIONS.add("announcePlayers");
        
        MESSAGE_HANDLERS.put("nop", null);
        MESSAGE_HANDLERS.put("error", null);
        addMessageHandler("ping");
        addMessageHandler("pong");
        addMessageHandler("refresh");
        addMessageHandler("refreshData");
        addMessageHandler("gateCreated");
        addMessageHandler("gateAdded");
        addMessageHandler("gateRenamed");
        addMessageHandler("gateRemoved");
        addMessageHandler("gateDestroyed");
        addMessageHandler("gateAttach");
        addMessageHandler("gateDetach");
        addMessageHandler("reservation");
        addMessageHandler("reservationApproved");
        addMessageHandler("reservationDenied");
        addMessageHandler("reservationArrived");
        addMessageHandler("reservationTimeout");
        addMessageHandler("linkAdd");
        addMessageHandler("linkAddComplete");
        addMessageHandler("linkRemove");
        addMessageHandler("linkRemoveComplete");
        addMessageHandler("playerChangeWorld");
        addMessageHandler("playerJoin");
        addMessageHandler("playerQuit");
        addMessageHandler("playerKick");
        addMessageHandler("playerDeath");
        addMessageHandler("chat");
        addMessageHandler("privateMessage");
        addMessageHandler("apiRequest");
        addMessageHandler("apiResult");
    }

    private static void addMessageHandler(String name) {
        try {
            String mname = "receive" + name.substring(0, 1).toUpperCase() + name.substring(1);
            MESSAGE_HANDLERS.put(name, Server.class.getDeclaredMethod(mname, Message.class));
        } catch (NoSuchMethodException nsme) {
            nsme.printStackTrace(System.err);
        }
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

    private boolean readyForAPI = false;
    
    private Map<String,RemotePlayerImpl> remotePlayers = new HashMap<String,RemotePlayerImpl>();
    private Map<String,RemoteWorldImpl> remoteWorlds = new HashMap<String,RemoteWorldImpl>();
    private Map<String,RemoteGateImpl> remoteGates = new HashMap<String,RemoteGateImpl>();
    
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
    public Set<RemoteGate> getRemoteGates() {
        return new HashSet<RemoteGate>(remoteGates.values());
    }
 
    @Override
    public RemoteWorld getRemoteWorld(String worldName) {
        return remoteWorlds.get(worldName);
    }

    @Override
    public RemoteGate getRemoteGate(String gateName) {
        return remoteGates.get(gateName);
    }
    
    @Override
    public boolean isConnected() {
        return readyForAPI;
    }
    
    @Override
    public void broadcast(final Callback<Integer> cb, String message, String permission) {
        Message args = new Message();
        args.put("message", message);
        args.put("permission", permission);
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getInt("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "broadcast", args);
    }
    
    @Override
    public void broadcastMessage(final Callback<Integer> cb, String message) {
        Message args = new Message();
        args.put("message", message);
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getInt("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "broadcastMessage", args);
    }
    
    @Override
    public void dispatchCommand(final Callback<Boolean> cb, CommandSender sender, String commandLine) {
        Message args = new Message();
        if ((sender instanceof ConsoleCommandSender) || (sender instanceof RemoteConsoleCommandSender))
            args.put("sender", "console");
        else if (sender instanceof Player) {
            args.put("sender", "player");
            args.put("name", sender.getName());
        }
        args.put("commandLine", commandLine);
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getBoolean("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "dispatchCommand", args);
    }
    
    @Override
    public void getDefaultGameMode(final Callback<GameMode> cb) {
        Message args = new Message();
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(Utils.valueOf(GameMode.class, m.getString("result")));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "getDefaultGameMode", args);
    }

    @Override
    public void getName(final Callback<String> cb) {
        Message args = new Message();
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getString("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "getName", args);
    }
    
    @Override
    public void getServerId(final Callback<String> cb) {
        Message args = new Message();
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getString("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "getServerId", args);
    }
    
    @Override
    public void getVersion(final Callback<String> cb) {
        Message args = new Message();
        sendAPIRequest(new APICallback<Message>() {
            @Override
            public void onSuccess(Message m) {
                if (cb != null) cb.onSuccess(m.getString("result"));
            }
            @Override
            public void onFailure(RemoteException re) {
                if (cb != null) cb.onFailure(re);
            }
        }, "server", "getVersion", args);
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

    /* Begin options */

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        if ((key == null) || key.isEmpty())
            throw new IllegalArgumentException("key is required");
        this.key = key;
    }
    
    @Override
    public String getPublicAddress() {
        return publicAddress;
    }

    @Override
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

    @Override
    public String getPrivateAddress() {
        return privateAddress;
    }

    @Override
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

    @Override
    public boolean getSendChat() {
        return sendChat;
    }

    @Override
    public void setSendChat(boolean b) {
        sendChat = b;
    }

    @Override
    public boolean getReceiveChat() {
        return receiveChat;
    }

    @Override
    public void setReceiveChat(boolean b) {
        receiveChat = b;
    }

    @Override
    public boolean getAnnouncePlayers() {
        return announcePlayers;
    }

    @Override
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
        ctx.send("option '%s' set to '%s' for server '%s'", name, value, getName());
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name;
    }

    /* End options */

    /* Custom methods */
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean en) {
        enabled = en;
        if (enabled)
            connect();
        else
            disconnect(false);
    }

    public String getNormalizedPublicAddress() {
        return normalizedPublicAddress;
    }

    public InetSocketAddress getNormalizedPrivateAddress() {
        return normalizedPrivateAddress;
    }
    
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
        if (isConnectionConnected() || Network.isStopped() || isIncoming()) return;
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

    public boolean isConnectionConnected() {
        if (connection == null) return false;
        return connection.isOpen();
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
        if (isConnectionConnected() || Network.isStopped() || isIncoming()) return;
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
        if (! isConnectionConnected())
            connect();
        else {
            Message message = createMessage("refresh");
            sendMessage(message);
        }
    }

    public void checkKeepAlive() {
        if (! isConnectionConnected()) return;
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
                receiveRefresh(null);
            }
        });
    }

    public void onDisconnected() {
        if (connected) {
            Utils.info("disconnected from '%s' (%s)", getName(), connection.getName());
            connected = false;
        }
        readyForAPI = false;
        connection = null;
        if (Network.isStopped()) {
            Gates.removeGatesForServer(this);
            remotePlayers.clear();
            remoteGates.clear();
            remoteWorlds.clear();
        } else {
            reconnect();
            final Server me = this;
            Utils.fire(new Runnable() {
                @Override
                public void run() {
                    RemoteServerDisconnectEvent event = new RemoteServerDisconnectEvent(me);
                    Global.plugin.getServer().getPluginManager().callEvent(event);        
                    Gates.removeGatesForServer(me);
                    remotePlayers.clear();
                    remoteGates.clear();
                    remoteWorlds.clear();
                }
            });
        }
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
        if (! isConnectionConnected()) return;
        if ((System.currentTimeMillis() - connection.getLastMessageSentTime()) < SEND_KEEPALIVE_INTERVAL) return;
        Utils.debug("sending keepalive to '%s'", name);
        Message message = createMessage("nop");
        sendMessage(message);
    }

    public void sendPing(Player player) {
        if (! isConnectionConnected()) return;
        final Message message = createMessage("ping");
        message.put("time", System.currentTimeMillis());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendGateCreated(LocalGateImpl gate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateCreated");
        message.put("type", gate.getType().toString());
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }
    
    public void sendGateAdded(LocalGateImpl gate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateAdded");
        message.put("type", gate.getType().toString());
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateRenamed(String oldLocalName, String newName) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateRenamed");
        message.put("oldName", oldLocalName);
        message.put("newName", newName);
        sendMessage(message);
    }

    public void sendGateRemoved(LocalGateImpl gate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateRemoved");
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateDestroyed(LocalGateImpl gate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateDestroyed");
        message.put("name", gate.getLocalName());
        sendMessage(message);
    }

    public void sendGateAttach(RemoteGateImpl toGate, LocalGateImpl fromGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateAttach");
        message.put("to", toGate.getLocalName());
        message.put("from", fromGate.getLocalName());
        sendMessage(message);
    }

    public void sendGateDetach(RemoteGateImpl toGate, LocalGateImpl fromGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("gateDetach");
        message.put("to", toGate.getLocalName());
        message.put("from", fromGate.getLocalName());
        sendMessage(message);
    }

    public void sendReservation(ReservationImpl res) throws ServerException {
        if (! isConnectionConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservation");
        message.put("reservation", res.encode());
        sendMessage(message);
    }

    public void sendReservationApproved(long id) throws ServerException {
        if (! isConnectionConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationApproved");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendReservationDenied(long id, String reason) throws ServerException {
        if (! isConnectionConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationDenied");
        message.put("id", id);
        message.put("reason", reason);
        sendMessage(message);
    }

    public void sendReservationArrived(long id) throws ServerException {
        if (! isConnectionConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationArrived");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendReservationTimeout(long id) throws ServerException {
        if (! isConnectionConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationTimeout");
        message.put("id", id);
        sendMessage(message);
    }

    public void sendChat(Player player, String msg, Set<RemoteGateImpl> toGates) {
        if (! isConnectionConnected()) return;
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

    public void sendLinkAdd(Player player, LocalGateImpl fromGate, RemoteGateImpl toGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("linkAdd");
        message.put("from", fromGate.getLocalName());
        message.put("to", toGate.getLocalName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkAddComplete(String playerName, LocalGateImpl fromGate, RemoteGateImpl toGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("linkAddComplete");
        message.put("from", fromGate.getLocalName());
        message.put("to", toGate.getLocalName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendLinkRemove(Player player, LocalGateImpl fromGate, RemoteGateImpl toGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("linkRemove");
        message.put("from", fromGate.getLocalName());
        message.put("to", toGate.getLocalName());
        message.put("player", (player == null) ? null : player.getName());
        sendMessage(message);
    }

    public void sendLinkRemoveComplete(String playerName, LocalGateImpl fromGate, RemoteGateImpl toGate) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("linkRemoveComplete");
        message.put("from", fromGate.getLocalName());
        message.put("to", toGate.getLocalName());
        message.put("player", playerName);
        sendMessage(message);
    }

    public void sendPlayerChangeWorld(Player player) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("playerChangeWorld");
        message.put("player", player.getName());
        message.put("world", player.getWorld().getName());
        sendMessage(message);
    }

    public void sendPlayerJoin(Player player, boolean hasReservation) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("playerJoin");
        message.put("name", player.getName());
        message.put("displayName", player.getDisplayName());
        message.put("world", player.getWorld().getName());
        message.put("hasReservation", hasReservation);
        sendMessage(message);
    }

    public void sendPlayerQuit(Player player, boolean hasReservation) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("playerQuit");
        message.put("name", player.getName());
        message.put("hasReservation", hasReservation);
        sendMessage(message);
    }

    public void sendPlayerKick(Player player, boolean hasReservation) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("playerKick");
        message.put("name", player.getName());
        message.put("hasReservation", hasReservation);
        sendMessage(message);
    }

    public void sendPlayerDeath(Player player) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("playerDeath");
        message.put("name", player.getName());
        sendMessage(message);
    }

    public void sendPrivateMessage(Player fromPlayer, RemotePlayer toPlayer, String msg) {
        if (! isConnectionConnected()) return;
        Message message = createMessage("privateMessage");
        if (fromPlayer != null)
            message.put("from", fromPlayer.getName());
        message.put("to", toPlayer.getName());
        message.put("message", msg);
        sendMessage(message);
    }
    
    public void sendAPIRequest(APICallback<Message> cb, String target, String method, Message args) {
        if (! isConnectionConnected()) {
            cb.onFailure(new RemoteException("not connected"));
            return;
        }
        final long rid = nextRequestId++;
        Message out = createMessage("apiRequest");
        out.put("requestId", rid);
        out.put("target", target);
        out.put("method", method);
        out.put("args", args);
        Utils.debug("api request to %s: %s", name, out);
        cb.setRequestId(rid);
        requests.put(rid, cb);
        sendMessage(out);
        
        // setup delayed task to timeout the request on this side if we don't get a response
        Utils.fireDelayed(new Runnable() {
            @Override
            public void run() {
                Callback<Message> cb = requests.remove(rid);
                if (cb != null) {
                    cb.onFailure(new RemoteException("timeout"));
                    Utils.debug("api request %s to %s timed out", rid, name);
                }
            }
        }, APIBackend.getTimeout());
        
    }
    
    // End remote commands
    
    // Message handling

    // run in the main thread
    private void receiveMessage(Message message, String command) {
        try {
            if (! MESSAGE_HANDLERS.containsKey(command)) {
                Utils.warning("receive unrecognized command '%s' from '%s'", command, getName());
                return;
            }
            Method handler = MESSAGE_HANDLERS.get(command);
            if (handler == null) return;
            handler.invoke(this, message);
        } catch (Throwable t) {
            Throwable c = t.getCause();
            if (c instanceof TransporterException) {
                Utils.warning( "while processing command '%s' from '%s': %s", command, getName(), c.getMessage());
                if (isConnectionConnected()) {
                    Message response = createMessage("error");
                    response.put("success", false);
                    response.put("error", c.getMessage());
                    sendMessage(response);
                }
            } else {
                Utils.severe(t, "while processing command '%s' from '%s': %s", command, getName(), t.getMessage());
                if (isConnectionConnected()) {
                    Message response = createMessage("error");
                    response.put("success", false);
                    response.put("error", t.getMessage());
                    sendMessage(response);
                }
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
    
    private void receiveRefresh(Message message) {
        if (! isConnectionConnected()) return;
        
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
        List<Message> gates = new ArrayList<Message>();
        for (LocalGateImpl gate : Gates.getLocalGates()) {
            Message gm = new Message();
            gm.put("type", gate.getType().toString());
            gm.put("name", gate.getLocalName());
            gates.add(gm);
        }
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
        
        // gates
        Collection<Message> gates = message.getMessageList("gates");
        if (gates == null)
            throw new ServerException("gate list required");
        remoteGates.clear();
        Gates.removeGatesForServer(this);
        for (Message gm : gates) {
            try {
                String gTypeStr = gm.getString("type");
                GateType gType = Utils.valueOf(GateType.class, gTypeStr);
                String gName = gm.getString("name");
                RemoteGateImpl gate = RemoteGateImpl.create(this, gType, gName);
                remoteGates.put(gate.getLocalName(), gate);
                try {
                    Gates.add(gate, false);
                } catch (GateException ge) {
                    remoteGates.remove(gate.getLocalName());
                    throw new IllegalArgumentException();
                }
            } catch (GateException ge) {
                Utils.warning("received bad gate from '%s'", getName());
            } catch (IllegalArgumentException iae) {
                Utils.warning("received bad gate from '%s'", getName());
            }
        }
        Utils.debug("received %d gates from '%s'", remoteGates.size(), getName());
        
        if (! readyForAPI) {
            readyForAPI = true;
            RemoteServerConnectEvent event = new RemoteServerConnectEvent(this);
            Global.plugin.getServer().getPluginManager().callEvent(event);        
        }
    }

    private void receiveGateCreated(Message message) {
        if (remoteWorlds.isEmpty()) {
            Utils.debug("ignored premature gateCreated command");
            return;
        }
        try {
            String gTypeStr = message.getString("type");
            GateType gType = Utils.valueOf(GateType.class, gTypeStr);
            String gName = message.getString("name");
            RemoteGateImpl gate = RemoteGateImpl.create(this, gType, gName);
            remoteGates.put(gate.getLocalName(), gate);
            try {
                Gates.add(gate, false);
            } catch (GateException ge) {
                remoteGates.remove(gate.getLocalName());
                throw new IllegalArgumentException();
            }
            Utils.debug("received gate '%s' from '%s'", gate.getLocalName(), getName());
            RemoteGateCreateEvent event = new RemoteGateCreateEvent(gate);
            Global.plugin.getServer().getPluginManager().callEvent(event);        
        } catch (GateException ge) {
            Utils.warning("received bad gate from '%s'", getName());
        } catch (IllegalArgumentException iae) {
            Utils.warning("received bad gate from '%s'", getName());
        }
    }
    
    private void receiveGateAdded(Message message) {
        if (remoteWorlds.isEmpty()) {
            Utils.debug("ignored premature gateAdded command");
            return;
        }
        try {
            String gTypeStr = message.getString("type");
            GateType gType = Utils.valueOf(GateType.class, gTypeStr);
            String gName = message.getString("name");
            RemoteGateImpl gate = RemoteGateImpl.create(this, gType, gName);
            if (remoteGates.containsKey(gate.getLocalName())) return;
            remoteGates.put(gate.getLocalName(), gate);
            try {
                Gates.add(gate, false);
            } catch (GateException ge) {
                remoteGates.remove(gate.getLocalName());
                throw new IllegalArgumentException();
            }
            Utils.debug("received gate '%s' from '%s'", gate.getLocalName(), getName());
        } catch (GateException ge) {
            Utils.warning("received bad gate from '%s'", getName());
        } catch (IllegalArgumentException iae) {
            Utils.warning("received bad gate from '%s'", getName());
        }
    }

    private void receiveGateRenamed(Message message) throws ServerException {
        String oldName = message.getString("oldName");
        if (oldName == null)
            throw new ServerException("missing oldName");
        String newName = message.getString("newName");
        if (newName == null)
            throw new ServerException("missing newName");
        
        RemoteGateImpl gate = remoteGates.get(oldName);
        if (gate == null)
            throw new ServerException("old gate '%s' not found", oldName);
        String oldFullName = gate.getFullName();
        remoteGates.remove(oldName);
        gate.setName(newName);
        remoteGates.put(gate.getLocalName(), gate);
        Gates.rename(gate, oldFullName);
    }

    private void receiveGateRemoved(Message message) throws ServerException {
        String lname = message.getString("name");
        if (lname == null)
            throw new ServerException("missing name");
        RemoteGateImpl gate = remoteGates.get(lname);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", lname);
        remoteGates.remove(lname);
        try {
            Gates.remove(gate);
        } catch (GateException ge) {}
    }

    private void receiveGateDestroyed(Message message) throws ServerException {
        String lname = message.getString("name");
        if (lname == null)
            throw new ServerException("missing name");
        RemoteGateImpl gate = remoteGates.get(lname);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", lname);
        remoteGates.remove(lname);
        RemoteGateDestroyEvent event = new RemoteGateDestroyEvent(gate);
        Global.plugin.getServer().getPluginManager().callEvent(event);        
        Gates.destroy(gate, false);
    }

    private void receiveGateAttach(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        LocalGateImpl toGate = Gates.getLocalGate(toName);
        if (toGate == null)
            throw new ServerException("unknown destination gate '%s'", toName);
        RemoteGateImpl fromGate = remoteGates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);
        toGate.attach(fromGate);
    }

    private void receiveGateDetach(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing to");
        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing from");
        LocalGateImpl toGate = Gates.getLocalGate(toName);
        if (toGate == null)
            throw new ServerException("unknown destination endpoint '%s'", toName);
        RemoteGateImpl fromGate = remoteGates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);
        toGate.detach(fromGate);
    }

    private void receiveReservation(Message message) throws ServerException {
        Message resMsg = message.getMessage("reservation");
        if (resMsg == null)
            throw new ServerException("missing reservation");
        ReservationImpl res;
        try {
            res = new ReservationImpl(resMsg, this);
            res.receive();
        } catch (ReservationException e) {
            throw new ServerException("invalid reservation: %s", e.getMessage());
        }
    }

    private void receiveReservationApproved(Message message) throws ServerException {
        long id = message.getLong("id");
        ReservationImpl res = ReservationImpl.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.approved();
    }

    private void receiveReservationDenied(Message message) throws ServerException {
        long id = message.getLong("id");
        ReservationImpl res = ReservationImpl.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        String reason = message.getString("reason");
        if (reason == null)
            throw new ServerException("missing reason");
        res.denied(reason);
    }

    private void receiveReservationArrived(Message message) throws ServerException {
        long id = message.getLong("id");
        ReservationImpl res = ReservationImpl.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.arrived();
    }

    private void receiveReservationTimeout(Message message) throws ServerException {
        long id = message.getLong("id");
        ReservationImpl res = ReservationImpl.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.timeout();
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
        
        LocalGateImpl fromGate = Gates.getLocalGate(toName);
        if (fromGate == null)
            throw new ServerException("unknown destination gate '%s'", toName);
        RemoteGateImpl toGate = remoteGates.get(fromName);
        if (toGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);

        fromGate.addLink(new Context(playerName), toGate.getFullName());
        sendLinkAddComplete(playerName, fromGate, toGate);
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
        
        LocalGateImpl fromGate = Gates.getLocalGate(toName);
        if (fromGate == null)
            throw new ServerException("unknown destination gate '%s'", toName);
        RemoteGateImpl toGate = remoteGates.get(fromName);
        if (toGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);

        Context ctx = new Context(playerName);
        ctx.sendLog("added link from '%s' to '%s'", toGate.getName(ctx), fromGate.getName(ctx));
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
        
        LocalGateImpl fromgate = Gates.getLocalGate(toName);
        if (fromgate == null)
            throw new ServerException("unknown destination gate '%s'", toName);
        RemoteGateImpl toGate = remoteGates.get(fromName);
        if (toGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);

        fromgate.removeLink(new Context(playerName), toGate.getFullName());
        sendLinkRemoveComplete(playerName, fromgate, toGate);
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
        
        LocalGateImpl fromGate = Gates.getLocalGate(toName);
        if (fromGate == null)
            throw new ServerException("unknown destination gate '%s'", toName);
        RemoteGateImpl toGate = remoteGates.get(fromName);
        if (toGate == null)
            throw new ServerException("unknown origin gate '%s'", fromName);

        Context ctx = new Context(playerName);
        ctx.sendLog("removed link from '%s' to '%s'", toGate.getName(ctx), fromGate.getName(ctx));
    }

    private void receivePlayerChangeWorld(Message message) throws ServerException {
        String playerName = message.getString("player");
        if (playerName == null)
            throw new ServerException("missing player");
        String worldName = message.getString("world");
        if (worldName == null)
            throw new ServerException("missing world");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null) return;
        player.setWorld(worldName);
        RemotePlayerChangeWorldEvent event = new RemotePlayerChangeWorldEvent(player);
        Global.plugin.getServer().getPluginManager().callEvent(event);        
    }

    private void receivePlayerJoin(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        String displayName = message.getString("displayName");
        if (displayName == null)
            throw new ServerException("missing displayName");
        String worldName = message.getString("world");
        if (worldName == null)
            throw new ServerException("missing world");
        boolean hasReservation = message.getBoolean("hasReservation");
        RemotePlayerImpl player = new RemotePlayerImpl(this, playerName, displayName, worldName);
        remotePlayers.put(playerName, player);
        if (! hasReservation) {
            RemotePlayerJoinEvent event = new RemotePlayerJoinEvent(player);
            Global.plugin.getServer().getPluginManager().callEvent(event);        
            if (getAnnouncePlayers())
                Global.plugin.getServer().broadcastMessage(Chat.colorize(player.format(Config.getServerJoinFormat())));
        }
    }

    private void receivePlayerQuit(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean hasReservation = message.getBoolean("hasReservation");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null) return;
            //throw new ServerException("unknown player '%s'", playerName);
        remotePlayers.remove(playerName);
        if (! hasReservation) {
            RemotePlayerQuitEvent event = new RemotePlayerQuitEvent(player);
            Global.plugin.getServer().getPluginManager().callEvent(event);        
            if (getAnnouncePlayers())
                Global.plugin.getServer().broadcastMessage(Chat.colorize(player.format(Config.getServerQuitFormat())));
        }
    }

    private void receivePlayerKick(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        boolean hasReservation = message.getBoolean("hasReservation");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null) return;
        remotePlayers.remove(playerName);
        if (! hasReservation) {
            RemotePlayerKickEvent event = new RemotePlayerKickEvent(player);
            Global.plugin.getServer().getPluginManager().callEvent(event);        
            if (getAnnouncePlayers())
                Global.plugin.getServer().broadcastMessage(Chat.colorize(player.format(Config.getServerKickFormat())));
        }
    }

    private void receivePlayerDeath(Message message) throws ServerException {
        String playerName = message.getString("name");
        if (playerName == null)
            throw new ServerException("missing name");
        RemotePlayerImpl player = remotePlayers.get(playerName);
        if (player == null) return;
        RemotePlayerDeathEvent event = new RemotePlayerDeathEvent(player);
        Global.plugin.getServer().getPluginManager().callEvent(event);        
        if (getAnnouncePlayers())
            Global.plugin.getServer().broadcastMessage(Chat.colorize(player.format(Config.getServerDeathFormat())));
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
        if (player == null) return;
        Chat.receive(player, msg, toGates);
    }

    private void receivePrivateMessage(Message message) throws ServerException {
        String fromPlayerName = message.getString("from");
        String toPlayerName = message.getString("to");
        if (toPlayerName == null)
            throw new ServerException("missing to player");
        String msg = message.getString("message");
        if (msg == null)
            throw new ServerException("missing message");
        RemotePlayerImpl fromPlayer = null;
        if (fromPlayerName != null) {
            fromPlayer = remotePlayers.get(fromPlayerName);
            if (fromPlayer == null) return;
        }
        Chat.receivePrivateMessage(fromPlayer, toPlayerName, msg);
    }

    private void receiveApiRequest(Message message) throws ServerException {
        String target = message.getString("target");
        if (target == null)
            throw new ServerException("missing target");
        String method = message.getString("method");
        if (method == null)
            throw new ServerException("missing method");
        long rid = message.getLong("requestId");
        Message args = message.getMessage("args");
        
        Message out = createMessage("apiResult");
        out.put("requestId", rid);
        try {
            APIBackend.invoke(target, method, args, out);
        } catch (Throwable t) {
            out.put("failure", t.getMessage());
        }
        sendMessage(out);
    }
    
    private void receiveApiResult(Message message) throws ServerException {
        long rid = message.getLong("requestId");
        Callback<Message> cb = requests.remove(rid);
        if (cb == null) {
            Utils.debug("received result for unknown api request %s from %s (maybe it timed out?)", rid, name);
            return;
        }
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

    private void sendMessage(final Message message) {
        Utils.debug("sending command '%s' to %s", message.getString("command", "<none>"), name);
        Utils.worker(new Runnable() {
            @Override
            public void run() {
                connection.sendMessage(message, true);
            }
        });
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
