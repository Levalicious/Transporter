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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bennedum.transporter.api.LocalGate;
import org.bennedum.transporter.command.CommandException;
import org.bennedum.transporter.config.Configuration;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class LocalGateImpl extends LocalEndpointImpl implements LocalGate, OptionsListener {

    public static final Set<String> OPTIONS = new HashSet<String>();
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\\\n");
    
    static {
        OPTIONS.add("duration");
        OPTIONS.add("linkLocal");
        OPTIONS.add("linkWorld");
        OPTIONS.add("linkServer");
        OPTIONS.add("linkNoneFormat");
        OPTIONS.add("linkUnselectedFormat");
        OPTIONS.add("linkOfflineFormat");
        OPTIONS.add("linkLocalFormat");
        OPTIONS.add("linkWorldFormat");
        OPTIONS.add("linkServerFormat");
        OPTIONS.add("multiLink");
        OPTIONS.add("protect");
        OPTIONS.add("restoreOnClose");
        OPTIONS.add("requirePin");
        OPTIONS.add("requireValidPin");
        OPTIONS.add("invalidPinDamage");
        OPTIONS.add("sendChat");
        OPTIONS.add("sendChatDistance");
        OPTIONS.add("receiveChat");
        OPTIONS.add("receiveChatDistance");
        OPTIONS.add("requireAllowedItems");
        OPTIONS.add("sendInventory");
        OPTIONS.add("receiveInventory");
        OPTIONS.add("deleteInventory");
        OPTIONS.add("receiveGameMode");
        OPTIONS.add("allowGameModes");
        OPTIONS.add("receiveXP");
        OPTIONS.add("randomNextLink");
        OPTIONS.add("sendNextLink");
        OPTIONS.add("teleportFormat");
        OPTIONS.add("noLinksFormat");
        OPTIONS.add("noLinkSelectedFormat");
        OPTIONS.add("invalidLinkFormat");
        OPTIONS.add("unknownLinkFormat");
        OPTIONS.add("linkLocalCost");
        OPTIONS.add("linkWorldCost");
        OPTIONS.add("linkServerCost");
        OPTIONS.add("sendLocalCost");
        OPTIONS.add("sendWorldCost");
        OPTIONS.add("sendServerCost");
        OPTIONS.add("receiveLocalCost");
        OPTIONS.add("receiveWorldCost");
        OPTIONS.add("receiveServerCost");
        OPTIONS.add("markerFormat");
    }

    private static boolean isValidPin(String pin) {
        return pin.length() < 20;
    }

    /*
    public static String getLocalLinkWorldName(String link) {
        String[] parts = link.split("\\.");
        if (parts.length > 2) return null;
        return parts[0];
    }
*/
    
    private File file;
    private World world;
    private Vector center;

    private String creatorName;
    private String designName;
    private BlockFace direction;
    private int duration;
    private boolean linkLocal;
    private boolean linkWorld;
    private boolean linkServer;
    private String linkNoneFormat;
    private String linkUnselectedFormat;
    private String linkOfflineFormat;
    private String linkLocalFormat;
    private String linkWorldFormat;
    private String linkServerFormat;
    private boolean multiLink;
    private boolean restoreOnClose;
    private boolean requirePin;
    private boolean requireValidPin;
    private int invalidPinDamage;
    private boolean protect;
    private boolean sendChat;
    private int sendChatDistance;
    private boolean receiveChat;
    private int receiveChatDistance;
    private boolean requireAllowedItems;
    private boolean sendInventory;
    private boolean receiveInventory;
    private boolean deleteInventory;
    private boolean receiveGameMode;
    private String allowGameModes;
    private boolean receiveXP;
    private boolean randomNextLink;
    private boolean sendNextLink;
    private String teleportFormat;
    private String noLinksFormat;
    private String noLinkSelectedFormat;
    private String invalidLinkFormat;
    private String unknownLinkFormat;
    private String markerFormat;

    private double linkLocalCost;
    private double linkWorldCost;
    private double linkServerCost;
    private double sendLocalCost;
    private double sendWorldCost;
    private double sendServerCost;
    private double receiveLocalCost;
    private double receiveWorldCost;
    private double receiveServerCost;

    private final List<String> links = new ArrayList<String>();
    private final Set<String> pins = new HashSet<String>();
    private final Set<String> bannedItems = new HashSet<String>();
    private final Set<String> allowedItems = new HashSet<String>();
    private final Map<String,String> replaceItems = new HashMap<String,String>();

    private List<GateBlock> blocks;

    private Set<String> incoming = new HashSet<String>();
    private String outgoing = null;
    private List<SavedBlock> savedBlocks = null;

    private boolean portalOpen = false;
    private long portalOpenTime = 0;
    private boolean dirty = false;
    private Options options = new Options(this, OPTIONS, "trp.gate", this);

    public LocalGateImpl(World world, String gateName, String playerName, Design design, List<GateBlock> blocks, BlockFace direction) throws EndpointException {
        this.world = world;
        name = gateName;
        creatorName = playerName;
        this.designName = design.getName();
        this.direction = direction;

        duration = design.getDuration();
        linkLocal = design.getLinkLocal();
        linkWorld = design.getLinkWorld();
        linkServer = design.getLinkServer();
        linkNoneFormat = design.getLinkNoneFormat();
        linkUnselectedFormat = design.getLinkUnselectedFormat();
        linkOfflineFormat = design.getLinkOfflineFormat();
        linkLocalFormat = design.getLinkLocalFormat();
        linkWorldFormat = design.getLinkWorldFormat();
        linkServerFormat = design.getLinkServerFormat();
        multiLink = design.getMultiLink();
        restoreOnClose = design.getRestoreOnClose();
        requirePin = design.getRequirePin();
        requireValidPin = design.getRequireValidPin();
        invalidPinDamage = design.getInvalidPinDamage();
        protect = design.getProtect();
        sendChat = design.getSendChat();
        sendChatDistance = design.getSendChatDistance();
        receiveChat = design.getReceiveChat();
        receiveChatDistance = design.getReceiveChatDistance();
        requireAllowedItems = design.getRequireAllowedItems();
        sendInventory = design.getSendInventory();
        receiveInventory = design.getReceiveInventory();
        deleteInventory = design.getDeleteInventory();
        receiveGameMode = design.getReceiveGameMode();
        allowGameModes = design.getAllowGameModes();
        receiveXP = design.getReceiveXP();
        randomNextLink = design.getRandomNextLink();
        sendNextLink = design.getSendNextLink();
        teleportFormat = design.getTeleportFormat();
        noLinksFormat = design.getNoLinksFormat();
        noLinkSelectedFormat = design.getNoLinkSelectedFormat();
        invalidLinkFormat = design.getInvalidLinkFormat();
        unknownLinkFormat = design.getUnknownLinkFormat();
        markerFormat = design.getMarkerFormat();

        linkLocalCost = design.getLinkLocalCost();
        linkWorldCost = design.getLinkWorldCost();
        linkServerCost = design.getLinkServerCost();
        sendLocalCost = design.getSendLocalCost();
        sendWorldCost = design.getSendWorldCost();
        sendServerCost = design.getSendServerCost();
        receiveLocalCost = design.getReceiveLocalCost();
        receiveWorldCost = design.getReceiveWorldCost();
        receiveServerCost = design.getReceiveServerCost();

        bannedItems.addAll(design.getBannedItems());
        allowedItems.addAll(design.getAllowedItems());
        replaceItems.putAll(design.getReplaceItems());

        this.blocks = blocks;

        calculateCenter();
        validate();
        generateFile();
        updateScreens();
        dirty = true;
    }

    public LocalGateImpl(World world, File file) throws EndpointException, BlockException {
        if (! file.exists())
            throw new EndpointException("%s not found", file.getAbsolutePath());
        if (! file.isFile())
            throw new EndpointException("%s is not a file", file.getAbsolutePath());
        if (! file.canRead())
            throw new EndpointException("unable to read %s", file.getAbsoluteFile());
        Configuration conf = new Configuration(file);
        conf.load();

        this.file = file;
        this.world = world;
        name = conf.getString("name");
        creatorName = conf.getString("creatorName");
        designName = conf.getString("designName");
        try {
            direction = Utils.valueOf(BlockFace.class, conf.getString("direction", "NORTH"));
        } catch (IllegalArgumentException iae) {
            throw new EndpointException("invalid or ambiguous direction");
        }
        duration = conf.getInt("duration", -1);
        linkLocal = conf.getBoolean("linkLocal", true);
        linkWorld = conf.getBoolean("linkWorld", true);
        linkServer = conf.getBoolean("linkServer", true);
        
        linkNoneFormat = conf.getString("linkNoneFormat", "%fromName%\\n\\n<none>");
        linkUnselectedFormat = conf.getString("linkUnselectedFormat", "%fromName%\\n\\n<unselected>");
        linkOfflineFormat = conf.getString("linkOfflineFormat", "%fromName%\\n\\n<offline>");
        linkLocalFormat = conf.getString("linkLocalFormat", "%fromName%\\n%toName%");
        linkWorldFormat = conf.getString("linkWorldFormat", "%fromName%\\n%toWorld%\\n%toName%");
        linkServerFormat = conf.getString("linkServerFormat", "%fromName%\\n%toServer%\\n%toWorld%\\n%toName%");
        
        multiLink = conf.getBoolean("multiLink", true);
        restoreOnClose = conf.getBoolean("restoreOnClose", false);
        links.addAll(conf.getStringList("links", new ArrayList<String>()));
        pins.addAll(conf.getStringList("pins", new ArrayList<String>()));

        List<String> items = conf.getStringList("bannedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new EndpointException("invalid banned item '%s'", item);
            bannedItems.add(i);
        }

        items = conf.getStringList("allowedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new EndpointException("invalid allowed item '%s'", item);
            allowedItems.add(i);
        }

        items = conf.getKeys("replaceItems");
        if (items != null) {
            for (String oldItem : items) {
                String oi = Inventory.normalizeItem(oldItem);
                if (oi == null)
                    throw new EndpointException("invalid replace item '%s'", oldItem);
                String newItem = conf.getString("replaceItems." + oldItem);
                String ni = Inventory.normalizeItem(newItem);
                if (ni == null)
                    throw new EndpointException("invalid replace item '%s'", newItem);
                replaceItems.put(oi, ni);
            }
        }

        requirePin = conf.getBoolean("requirePin", false);
        requireValidPin = conf.getBoolean("requireValidPin", true);
        invalidPinDamage = conf.getInt("invalidPinDamage", 0);
        protect = conf.getBoolean("protect", false);
        sendChat = conf.getBoolean("sendChat", false);
        sendChatDistance = conf.getInt("sendChatDistance", 1000);
        receiveChat = conf.getBoolean("receiveChat", false);
        receiveChatDistance = conf.getInt("receiveChatDistance", 1000);
        requireAllowedItems = conf.getBoolean("requireAllowedItems", true);
        sendInventory = conf.getBoolean("sendInventory", true);
        receiveInventory = conf.getBoolean("receiveInventory", true);
        deleteInventory = conf.getBoolean("deleteInventory", false);
        receiveGameMode = conf.getBoolean("receiveGameMode", false);
        allowGameModes = conf.getString("allowGameModes", "*");
        receiveXP = conf.getBoolean("receiveXP", false);
        randomNextLink = conf.getBoolean("randomNextLink", false);
        sendNextLink = conf.getBoolean("sendNextLink", false);
        teleportFormat = conf.getString("teleportFormat", ChatColor.GOLD + "teleported to '%toNameCtx%'");
        noLinksFormat = conf.getString("noLinksFormat", "this gate has no links");
        noLinkSelectedFormat = conf.getString("noLinkSelectedFormat", "no link is selected");
        invalidLinkFormat = conf.getString("invalidLinkFormat", "invalid link selected");
        unknownLinkFormat = conf.getString("unknownLinkFormat", "unknown or offline destination endpoint");
        markerFormat = conf.getString("markerFormat", "%name%");

        incoming.addAll(conf.getStringList("incoming", new ArrayList<String>()));
        outgoing = conf.getString("outgoing");
        portalOpen = conf.getBoolean("portalOpen", false);

        linkLocalCost = conf.getDouble("linkLocalCost", 0);
        linkWorldCost = conf.getDouble("linkWorldCost", 0);
        linkServerCost = conf.getDouble("linkServerCost", 0);
        sendLocalCost = conf.getDouble("sendLocalCost", 0);
        sendWorldCost = conf.getDouble("sendWorldCost", 0);
        sendServerCost = conf.getDouble("sendServerCost", 0);
        receiveLocalCost = conf.getDouble("receiveLocalCost", 0);
        receiveWorldCost = conf.getDouble("receiveWorldCost", 0);
        receiveServerCost = conf.getDouble("receiveServerCost", 0);

        List<ConfigurationNode> nodes = conf.getNodeList("blocks");
        if (nodes == null)
            throw new EndpointException("missing blocks");
        blocks = new ArrayList<GateBlock>();
        for (ConfigurationNode node : nodes) {
            GateBlock block = new GateBlock(node);
            block.setWorld(world);
            blocks.add(block);
        }

        nodes = conf.getNodeList("saved", null);
        if (nodes != null) {
            savedBlocks = new ArrayList<SavedBlock>();
            for (ConfigurationNode node : nodes) {
                SavedBlock block = new SavedBlock(node);
                block.setWorld(world);
                savedBlocks.add(block);
            }
            if (savedBlocks.isEmpty()) savedBlocks = null;
        }

        // convert 6.10 stuff
        int relayChatDistance = conf.getInt("relayChatDistance", Integer.MIN_VALUE);
        if (relayChatDistance != Integer.MIN_VALUE) {
            sendChat = receiveChat = (relayChatDistance > 0);
            if (relayChatDistance > 0)
                sendChatDistance = receiveChatDistance = relayChatDistance;
        }

        calculateCenter();
        validate();
    }

    // Endpoint interface
    
    @Override
    public String getLocalName() {
        return world.getName() + "." + getName();
    }
    
    @Override
    public String getFullName() {
        return getLocalName();
    }
    
    // EndpointImpl overrides
    
    @Override
    public String getName(Context ctx) {
        if ((ctx != null) && ctx.isPlayer()) return getName();
        return getLocalName();
    }
    
    @Override
    public String getGlobalName() {
        return "local." + getLocalName();
    }
    
    @Override
    public boolean isSameServer() {
        return true;
    }

    @Override
    protected void attach(EndpointImpl originEndpoint) {
        if (originEndpoint != null) {
            String epName = originEndpoint.getFullName();
            if (incoming.contains(epName)) return;
            incoming.add(epName);
            dirty = true;
        }
        openPortal();

        // try to attach to our destination
        if ((outgoing == null) || (! hasLink(outgoing))) {
            if (! isLinked())
                outgoing = null;
            else
                outgoing = getLinks().get(0);
            updateScreens();
        }
        if (outgoing != null) {
            EndpointImpl ep = Endpoints.get(outgoing);
            if (ep != null)
                ep.attach(this);
        }
        save();
    }

    @Override
    protected void detach(EndpointImpl originEndpoint) {
        String epName = originEndpoint.getFullName();
        if (! incoming.contains(epName)) return;

        incoming.remove(epName);
        dirty = true;
        closeIfAllowed();
        save();
    }

    // LocalEndpointImpl overrides
    
    @Override
    public void addLink(Context ctx, String toEpName) throws TransporterException {
        Permissions.require(ctx.getPlayer(), "trp.gate.link.add." + getLocalName());

        if (isLinked() && (! getMultiLink()))
            throw new EndpointException("gate '%s' cannot accept multiple links", getName(ctx));

        EndpointImpl toEp = Endpoints.find(toEpName);
        if (toEp == null)
            throw new EndpointException("endpoint '%s' cannot be found", toEpName);
        
        if (toEp.isSameServer()) {
            LocalEndpointImpl toEpLocal = (LocalEndpointImpl)toEp;
            if (isSameWorld(toEpLocal.getWorld()) && (! Config.getAllowLinkLocal()))
                throw new CommandException("linking to on-world endpoints is not permitted");
            else if (! Config.getAllowLinkWorld())
                throw new CommandException("linking to off-world endpoints is not permitted");
            if (isSameWorld(toEpLocal.getWorld()))
                Economy.requireFunds(ctx.getPlayer(), getLinkLocalCost());
            else 
                Economy.requireFunds(ctx.getPlayer(), getLinkWorldCost());

        } else {
            if (! Config.getAllowLinkServer())
                throw new CommandException("linking to remote endpoints is not permitted");
            Economy.requireFunds(ctx.getPlayer(), getLinkServerCost());
        }

        if (! addLink(toEp.getFullName()))
            throw new EndpointException("gate '%s' already links to '%s'", getName(ctx), toEp.getName(ctx));

        ctx.sendLog("added link from '%s' to '%s'", getName(ctx), toEp.getName(ctx));

        try {
            if (toEp.isSameServer()) {
                LocalEndpointImpl toEpLocal = (LocalEndpointImpl)toEp;
                if (isSameWorld(toEpLocal.getWorld()) && Economy.deductFunds(ctx.getPlayer(), getLinkLocalCost()))
                    ctx.sendLog("debited %s for on-world linking", Economy.format(getLinkLocalCost()));
                else if (Economy.deductFunds(ctx.getPlayer(), getLinkWorldCost()))
                    ctx.sendLog("debited %s for off-world linking", Economy.format(getLinkWorldCost()));
            } else {
                if (Economy.deductFunds(ctx.getPlayer(), getLinkServerCost()))
                    ctx.sendLog("debited %s for off-server linking", Economy.format(getLinkServerCost()));
            }
        } catch (EconomyException ee) {
            Utils.warning("unable to debit linking costs for %s: %s", ctx.getPlayer().getName(), ee.getMessage());
        }
    }

    @Override
    public void removeLink(Context ctx, String toEpName) throws TransporterException {
        Permissions.require(ctx.getPlayer(), "trp.gate.link.remove." + getFullName());

        EndpointImpl toEp = Endpoints.find(toEpName);
        if (toEp != null) toEpName = toEp.getFullName();

        if (! removeLink(toEpName))
            throw new EndpointException("gate '%s' does not have a link to '%s'", getName(ctx), toEpName);

        ctx.sendLog("removed link from '%s' to '%s'", getName(ctx), toEpName);
    }
    
    @Override
    public void nextLink() throws TransporterException {
        // trivial case of single link to prevent needless detach/attach
        if ((links.size() == 1) && links.contains(outgoing)) {
            //updateScreens();
            return;
        }

        // detach from the current gate
        if (portalOpen && (outgoing != null)) {
            EndpointImpl ep = Endpoints.get(outgoing);
            if (ep != null)
                ep.detach(this);
        }

        // select next link
        if ((outgoing == null) || (! links.contains(outgoing))) {
            if (! links.isEmpty()) {
                outgoing = links.get(0);
                dirty = true;
            }

        } else if (randomNextLink) {
            List<String> candidateLinks = new ArrayList<String>(links);
            candidateLinks.remove(outgoing);
            if (candidateLinks.size() > 1)
                Collections.shuffle(candidateLinks);
            if (! candidateLinks.isEmpty()) {
                outgoing = candidateLinks.get(0);
                dirty = true;
            }

        } else {
            int i = links.indexOf(outgoing) + 1;
            if (i >= links.size()) i = 0;
            outgoing = links.get(i);
            dirty = true;
        }

        updateScreens();

        // attach to the next gate
        if (portalOpen && (outgoing != null)) {
            EndpointImpl ep = Endpoints.get(outgoing);
            if (ep != null)
                ep.attach(this);
        }
        save();
        getDestinationEndpoint();
    }

    @Override
    public void onRenameComplete() {
        file.delete();
        generateFile();
        save();
        updateScreens();
    }
    
    @Override
    public void onEndpointAdded(EndpointImpl ep) {
        if (ep == this) return;
        if ((outgoing != null) && outgoing.equals(ep.getFullName()))
            updateScreens();
    }

    @Override
    public void onEndpointRemoved(EndpointImpl ep) {
        if (ep == this) return;
        String epName = ep.getFullName();
        if (epName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        closeIfAllowed();
        save();
    }

    @Override
    public void onEndpointDestroyed(EndpointImpl ep) {
        if (ep == this) return;
        String epName = ep.getFullName();
        if (removeLink(epName))
            dirty = true;
        if (epName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        if (incoming.contains(epName)) {
            incoming.remove(epName);
            dirty = true;
        }
        closeIfAllowed();
        save();
    }
    
    @Override
    public void onEndpointRenamed(EndpointImpl ep, String oldFullName) {
        if (ep == this) return;
        String newName = ep.getFullName();
        if (links.contains(oldFullName)) {
            links.set(links.indexOf(oldFullName), newName);
            dirty = true;
        }
        if (oldFullName.equals(outgoing)) {
            outgoing = newName;
            dirty = true;
            updateScreens();
        }
        if (incoming.contains(oldFullName)) {
            incoming.remove(oldFullName);
            incoming.add(newName);
            dirty = true;
        }
        save();
    }
    
    @Override
    public void onSend(Entity entity) {
        GateMap map = getSendLightningBlocks();
        GateBlock block = map.randomBlock();
        if (block == null) return;
        switch (block.getDetail().getSendLightningMode()) {
            case NORMAL:
                world.strikeLightning(block.getLocation());
                break;
            case SAFE:
                world.strikeLightningEffect(block.getLocation());
                break;
        }
    }

    @Override
    public void onReceive(Entity entity) {
        GateMap map = getReceiveLightningBlocks();
        GateBlock block = map.randomBlock();
        if (block == null) return;
        switch (block.getDetail().getReceiveLightningMode()) {
            case NORMAL:
                world.strikeLightning(block.getLocation());
                break;
            case SAFE:
                world.strikeLightningEffect(block.getLocation());
                break;
        }
    }

    @Override
    public void onDestroy(boolean unbuild) {
        close();
        file.delete();
        if (unbuild) {
            for (GateBlock gb : blocks) {
                if (! gb.getDetail().isBuildable()) continue;
                Block b = gb.getLocation().getBlock();
                b.setTypeIdAndData(0, (byte)0, false);
            }
        }
    }


    @Override
    public boolean isSameWorld(World world) {
        return this.world == world;
    }
    
    @Override
    public World getWorld() {
        return world;
    }
    
    @Override
    public void save() {
        if (! dirty) return;
        dirty = false;

        Configuration conf = new Configuration(file);
        conf.setProperty("name", name);
        conf.setProperty("creatorName", creatorName);
        conf.setProperty("designName", designName);
        conf.setProperty("direction", direction.toString());
        conf.setProperty("duration", duration);
        conf.setProperty("linkLocal", linkLocal);
        conf.setProperty("linkWorld", linkWorld);
        conf.setProperty("linkServer", linkServer);
        
        conf.setProperty("linkNoneFormat", linkNoneFormat);
        conf.setProperty("linkUnselectedFormat", linkUnselectedFormat);
        conf.setProperty("linkOfflineFormat", linkOfflineFormat);
        conf.setProperty("linkLocalFormat", linkLocalFormat);
        conf.setProperty("linkWorldFormat", linkWorldFormat);
        conf.setProperty("linkServerFormat", linkServerFormat);
        
        conf.setProperty("multiLink", multiLink);
        conf.setProperty("restoreOnClose", restoreOnClose);
        conf.setProperty("links", links);
        conf.setProperty("pins", new ArrayList<String>(pins));
        conf.setProperty("bannedItems", new ArrayList<String>(bannedItems));
        conf.setProperty("allowedItems", new ArrayList<String>(allowedItems));
        conf.setProperty("replaceItems", replaceItems);
        conf.setProperty("requirePin", requirePin);
        conf.setProperty("requireValidPin", requireValidPin);
        conf.setProperty("invalidPinDamage", invalidPinDamage);
        conf.setProperty("protect", protect);
        conf.setProperty("sendChat", sendChat);
        conf.setProperty("sendChatDistance", sendChatDistance);
        conf.setProperty("receiveChat", receiveChat);
        conf.setProperty("receiveChatDistance", receiveChatDistance);
        conf.setProperty("requireAllowedItems", requireAllowedItems);
        conf.setProperty("sendInventory", sendInventory);
        conf.setProperty("receiveInventory", receiveInventory);
        conf.setProperty("deleteInventory", deleteInventory);
        conf.setProperty("receiveGameMode", receiveGameMode);
        conf.setProperty("allowGameModes", allowGameModes);
        conf.setProperty("receiveXP", receiveXP);
        conf.setProperty("randomNextLink", randomNextLink);
        conf.setProperty("sendNextLink", sendNextLink);
        conf.setProperty("teleportFormat", teleportFormat);
        conf.setProperty("noLinksFormat", noLinksFormat);
        conf.setProperty("noLinkSelectedFormat", noLinkSelectedFormat);
        conf.setProperty("invalidLinkFormat", invalidLinkFormat);
        conf.setProperty("unknownLinkFormat", unknownLinkFormat);
        conf.setProperty("markerFormat", markerFormat);

        if (! incoming.isEmpty()) conf.setProperty("incoming", new ArrayList<String>(incoming));
        if (outgoing != null) conf.setProperty("outgoing", outgoing);
        conf.setProperty("portalOpen", portalOpen);

        conf.setProperty("linkLocalCost", linkLocalCost);
        conf.setProperty("linkWorldCost", linkWorldCost);
        conf.setProperty("linkServerCost", linkServerCost);
        conf.setProperty("sendLocalCost", sendLocalCost);
        conf.setProperty("sendWorldCost", sendWorldCost);
        conf.setProperty("sendServerCost", sendServerCost);
        conf.setProperty("receiveLocalCost", receiveLocalCost);
        conf.setProperty("receiveWorldCost", receiveWorldCost);
        conf.setProperty("receiveServerCost", receiveServerCost);

        List<Object> node = new ArrayList<Object>();
        for (GateBlock block : blocks)
            node.add(block.encode());
        conf.setProperty("blocks", node);

        if (savedBlocks != null) {
            node = new ArrayList<Object>();
            for (SavedBlock block : savedBlocks)
                node.add(block.encode());
            conf.setProperty("saved", node);
        }

        File parent = file.getParentFile();
        if (! parent.exists())
            parent.mkdirs();
        conf.save();
    }

    // End interfaces and implementations
    
    // called when the gate is loaded from a file
    public void initialize() {
        if (portalOpen)
            Gates.addPortalBlocks(getPortalBlocks());
        if (protect)
            Gates.addProtectBlocks(getBuildBlocks());
    }

    private void calculateCenter() {
        double cx = 0, cy = 0, cz = 0;
        for (GateBlock block : blocks) {
            cx += block.getLocation().getBlockX() + 0.5;
            cy += block.getLocation().getBlockY() + 0.5;
            cz += block.getLocation().getBlockZ() + 0.5;
        }
        cx /= blocks.size();
        cy /= blocks.size();
        cz /= blocks.size();
        center = new Vector(cx, cy, cz);
    }

    private void validate() throws EndpointException {
        if (name == null)
            throw new EndpointException("name is required");
        if (! isValidName(name))
            throw new EndpointException("name is not valid");
        if (creatorName == null)
            throw new EndpointException("creatorName is required");
        if (designName == null)
            throw new EndpointException("designName is required");
        if (! Design.isValidName(designName))
            throw new EndpointException("designName is not valid");
        if (blocks.isEmpty())
            throw new EndpointException("must have at least one block");
    }

    public Vector getCenter() {
        return center;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getDesignName() {
        return designName;
    }
    
    public BlockFace getDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return "LocalGate[" + getLocalName() + "]";
    }
    
    /* Begin options */

    public int getDuration() {
        return duration;
    }

    public void setDuration(int i) {
        duration = i;
    }

    public boolean getLinkLocal() {
        return linkLocal;
    }

    public void setLinkLocal(boolean b) {
        linkLocal = b;
    }

    public boolean getLinkWorld() {
        return linkWorld;
    }

    public void setLinkWorld(boolean b) {
        linkWorld = b;
    }

    public boolean getLinkServer() {
        return linkServer;
    }

    public void setLinkServer(boolean b) {
        linkServer = b;
    }

    public String getLinkNoneFormat() {
        return linkNoneFormat;
    }
    
    public void setLinkNoneFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n\n<none>";
        linkNoneFormat = s;
    }

    public String getLinkUnselectedFormat() {
        return linkUnselectedFormat;
    }
    
    public void setLinkUnselectedFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n\n<unselected>";
        linkUnselectedFormat = s;
    }

    public String getLinkOfflineFormat() {
        return linkOfflineFormat;
    }
    
    public void setLinkOfflineFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n\n<offline>";
        linkOfflineFormat = s;
    }

    public String getLinkLocalFormat() {
        return linkLocalFormat;
    }
    
    public void setLinkLocalFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n%toGate%";
        linkLocalFormat = s;
    }

    public String getLinkWorldFormat() {
        return linkWorldFormat;
    }
    
    public void setLinkWorldFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n%toWorld%\n%toGate%";
        linkWorldFormat = s;
    }

    public String getLinkServerFormat() {
        return linkServerFormat;
    }
    
    public void setLinkServerFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\n%toServer%\n%toWorld%\n%toGate%";
        linkServerFormat = s;
    }
        
    public boolean getMultiLink() {
        return multiLink;
    }

    public void setMultiLink(boolean b) {
        multiLink = b;
    }

    public boolean getProtect() {
        return protect;
    }

    public void setProtect(boolean b) {
        protect = b;
        if (protect)
            Gates.addProtectBlocks(getBuildBlocks());
        else
            Gates.removeProtectBlocks(this);
    }

    public boolean getRestoreOnClose() {
        return restoreOnClose;
    }

    public void setRestoreOnClose(boolean b) {
        restoreOnClose = b;
    }

    public boolean getRequirePin() {
        return requirePin;
    }

    public void setRequirePin(boolean b) {
        requirePin = b;
    }

    public boolean getRequireValidPin() {
        return requireValidPin;
    }

    public void setRequireValidPin(boolean b) {
        requireValidPin = b;
    }

    public int getInvalidPinDamage() {
        return invalidPinDamage;
    }

    public void setInvalidPinDamage(int i) {
        if (i < 0)
            throw new IllegalArgumentException("invalidPinDamage must be at least 0");
        invalidPinDamage = i;
    }

    public boolean getSendChat() {
        return sendChat;
    }

    public void setSendChat(boolean b) {
        sendChat = b;
    }

    public int getSendChatDistance() {
        return sendChatDistance;
    }

    public void setSendChatDistance(int i) {
        sendChatDistance = i;
    }

    public boolean getReceiveChat() {
        return receiveChat;
    }

    public void setReceiveChat(boolean b) {
        receiveChat = b;
    }

    public int getReceiveChatDistance() {
        return receiveChatDistance;
    }

    public void setReceiveChatDistance(int i) {
        receiveChatDistance = i;
    }

    public boolean getRequireAllowedItems() {
        return requireAllowedItems;
    }

    public void setRequireAllowedItems(boolean b) {
        requireAllowedItems = b;
    }

    public boolean getSendInventory() {
        return sendInventory;
    }

    public void setSendInventory(boolean b) {
        sendInventory = b;
    }

    public boolean getReceiveInventory() {
        return receiveInventory;
    }

    public void setReceiveInventory(boolean b) {
        receiveInventory = b;
    }

    public boolean getDeleteInventory() {
        return deleteInventory;
    }

    public void setDeleteInventory(boolean b) {
        deleteInventory = b;
    }

    public boolean getReceiveGameMode() {
        return receiveGameMode;
    }

    public void setReceiveGameMode(boolean b) {
        receiveGameMode = b;
    }

    public String getAllowGameModes() {
        return allowGameModes;
    }

    public void setAllowGameModes(String s) {
        if (s != null) {
            if (s.equals("*")) s = null;
        }
        if (s == null) s = "*";
        String[] parts = s.split(",");
        String modes = "";
        for (String part : parts) {
            if (part.equals("*")) {
                modes = "*,";
                break;
            }
            try {
                GameMode mode = Utils.valueOf(GameMode.class, part);
                modes += mode.toString() + ",";
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("allowGameModes: " + e.getMessage());
            }
        }
        allowGameModes = modes.substring(0, modes.length() - 1);
    }

    public boolean getReceiveXP() {
        return receiveXP;
    }

    public void setReceiveXP(boolean b) {
        receiveXP = b;
    }
    
    public boolean getRandomNextLink() {
        return randomNextLink;
    }

    public void setRandomNextLink(boolean b) {
        randomNextLink = b;
    }
    
    public boolean getSendNextLink() {
        return sendNextLink;
    }

    public void setSendNextLink(boolean b) {
        sendNextLink = b;
    }
    
    public String getTeleportFormat() {
        return teleportFormat;
    }

    public void setTeleportFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = ChatColor.GOLD + "teleported to '%toNameCtx%'";
        teleportFormat = s;
    }

    public String getNoLinksFormat() {
        return noLinksFormat;
    }

    public void setNoLinksFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "this gate has no links";
        noLinksFormat = s;
    }

    public String getNoLinkSelectedFormat() {
        return noLinkSelectedFormat;
    }

    public void setNoLinkSelectedFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "no link is selected";
        noLinkSelectedFormat = s;
    }

    public String getInvalidLinkFormat() {
        return invalidLinkFormat;
    }

    public void setInvalidLinkFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "invalid link selected";
        invalidLinkFormat = s;
    }

    public String getUnknownLinkFormat() {
        return unknownLinkFormat;
    }

    public void setUnknownLinkFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "unknown or offline destination gate";
        unknownLinkFormat = s;
    }

    public String getMarkerFormat() {
        return markerFormat;
    }

    public void setMarkerFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%name%";
        markerFormat = s;
    }

    public double getLinkLocalCost() {
        return linkLocalCost;
    }

    public void setLinkLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkLocalCost must be at least 0");
        linkLocalCost = cost;
    }

    public double getLinkWorldCost() {
        return linkWorldCost;
    }

    public void setLinkWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkWorldCost must be at least 0");
        linkWorldCost = cost;
    }

    public double getLinkServerCost() {
        return linkServerCost;
    }

    public void setLinkServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkServerCost must be at least 0");
        linkServerCost = cost;
    }

    public double getSendLocalCost() {
        return sendLocalCost;
    }

    public void setSendLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendLocalCost must be at least 0");
        sendLocalCost = cost;
    }

    public double getSendWorldCost() {
        return sendWorldCost;
    }

    public void setSendWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendWorldCost must be at least 0");
        sendWorldCost = cost;
    }

    public double getSendServerCost() {
        return sendServerCost;
    }

    public void setSendServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendServerCost must be at least 0");
        sendServerCost = cost;
    }

    public double getReceiveLocalCost() {
        return receiveLocalCost;
    }

    public void setReceiveLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveLocalCost must be at least 0");
        receiveLocalCost = cost;
    }

    public double getReceiveWorldCost() {
        return receiveWorldCost;
    }

    public void setReceiveWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveWorldCost must be at least 0");
        receiveWorldCost = cost;
    }

    public double getReceiveServerCost() {
        return receiveServerCost;
    }

    public void setReceiveServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveServerCost must be at least 0");
        receiveServerCost = cost;
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
        dirty = true;
        ctx.sendLog("option '%s' set to '%s' for gate '%s'", name, value, getName(ctx));
        save();
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name + "." + name;
    }

    /* End options */

    public double getSendCost(EndpointImpl toEp) {
        if (toEp == null) return 0;
        if (! toEp.isSameServer()) return sendServerCost;
        if (((LocalEndpointImpl)toEp).isSameWorld(world)) return sendLocalCost;
        return sendWorldCost;
    }

    public double getReceiveCost(EndpointImpl fromEp) {
        if (fromEp == null) return 0;
        if (! fromEp.isSameServer()) return receiveServerCost;
        if (((LocalEndpointImpl)fromEp).isSameWorld(world)) return receiveLocalCost;
        return receiveWorldCost;
    }

    public boolean isOpen() {
        return portalOpen;
    }

    public boolean isClosed() {
        return ! portalOpen;
    }

    public void open() throws EndpointException {
        if (portalOpen) return;

        // try to get our destination
        if ((outgoing == null) || (! hasLink(outgoing))) {
            if (! isLinked())
                throw new EndpointException("this gate has no links");
            outgoing = getLinks().get(0);
            dirty = true;
            //updateScreens();
        }
        EndpointImpl ep = Endpoints.get(outgoing);
        if (ep == null)
            throw new EndpointException("unknown or offline endpoint '%s'", outgoing);

        openPortal();
        ep.attach(this);
        updateScreens();
        save();

        if (duration > 0) {
            final LocalGateImpl myself = this;
            Utils.fireDelayed(new Runnable() {
                @Override
                public void run() {
                    myself.closeIfAllowed();
                }
            }, duration + 100);
        }
    }

    public void close() {
        if (! portalOpen) return;

        incoming.clear();
        closePortal();
        updateScreens();

        // try to detach from our destination
        if (outgoing != null) {
            EndpointImpl ep = Endpoints.get(outgoing);
            if (ep != null)
                ep.detach(this);
        }
        save();
    }

    public List<String> getLinks() {
        return new ArrayList<String>(links);
    }

    public boolean isLinked() {
        return ! links.isEmpty();
    }
    
    public boolean hasLink(String link) {
        return links.contains(link);
    }

    protected boolean addLink(String link) {
        if (links.contains(link)) return false;
        links.add(link);
        if (links.size() == 1)
            outgoing = link;
        dirty = true;
        updateScreens();
        save();
        return true;
    }

    protected boolean removeLink(String link) {
        if (! links.contains(link)) return false;
        links.remove(link);
        if (link.equals(outgoing))
            outgoing = null;
        dirty = true;
        updateScreens();
        closeIfAllowed();
        save();
        return true;
    }
    
    public boolean isLastLink() {
        if (outgoing == null)
            return links.isEmpty();
        return links.indexOf(outgoing) == (links.size() - 1);
    }

    public boolean hasValidDestination() {
        try {
            getDestinationEndpoint();
            return true;
        } catch (EndpointException e) {
            return false;
        }
    }

    public String getDestinationLink() {
        return outgoing;
    }

    public EndpointImpl getDestinationEndpoint() throws EndpointException {
        if (outgoing == null) {
            if (! isLinked())
                throw new EndpointException(getNoLinksFormat());
            else
                throw new EndpointException(getNoLinkSelectedFormat());
        } else if (! hasLink(outgoing))
            throw new EndpointException(getInvalidLinkFormat());
        EndpointImpl ep = Endpoints.get(outgoing);
        if (ep == null)
            throw new EndpointException(getUnknownLinkFormat());
        return ep;
    }

    public void rebuild() {
        GateMap portalBlocks = getPortalBlocks();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isBuildable()) continue;
            if (portalOpen && portalBlocks.containsLocation(gb.getLocation())) continue;
            gb.getDetail().getBuildBlock().build(gb.getLocation());
        }
        updateScreens();
    }

    public boolean addPin(String pin) throws EndpointException {
        if (! isValidPin(pin))
            throw new EndpointException("invalid pin");
        if (pins.contains(pin)) return false;
        pins.add(pin);
        return true;
    }

    public boolean removePin(String pin) {
        if (pins.contains(pin)) return false;
        pins.remove(pin);
        save();
        return true;
    }

    public void removeAllPins() {
        pins.clear();
        save();
    }

    public boolean hasPin(String pin) {
        return pins.contains(pin);
    }

    public Set<String> getBannedItems() {
        return bannedItems;
    }
    
    public boolean addBannedItem(String item) throws EndpointException {
        try {
            if (! Inventory.appendItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public boolean removeBannedItem(String item) throws EndpointException {
        try {
            if (! Inventory.removeItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public void removeAllBannedItems() {
        bannedItems.clear();
        dirty = true;
        save();
    }

    public Set<String> getAllowedItems() {
        return allowedItems;
    }
    
    public boolean addAllowedItem(String item) throws EndpointException {
        try {
            if (! Inventory.appendItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public boolean removeAllowedItem(String item) throws EndpointException {
        try {
            if (! Inventory.removeItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public void removeAllAllowedItems() {
        allowedItems.clear();
        dirty = true;
        save();
    }

    public Map<String,String> getReplaceItems() {
        return replaceItems;
    }
    
    public boolean addReplaceItem(String fromItem, String toItem) throws EndpointException {
        try {
            if (! Inventory.appendItemMap(replaceItems, fromItem, toItem)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public boolean removeReplaceItem(String item) throws EndpointException {
        try {
            if (! Inventory.removeItemMap(replaceItems, item)) return false;
        } catch (InventoryException e) {
            throw new EndpointException(e.getMessage());
        }
        dirty = true;
        save();
        return true;
    }

    public void removeAllReplaceItems() {
        replaceItems.clear();
        dirty = true;
        save();
    }

    public boolean isAllowedGameMode(String mode) {
        if (allowGameModes == null) return false;
        if (allowGameModes.equals("*")) return true;
        for (String part : allowGameModes.split(","))
            if (part.equals(mode)) return true;
        return false;
    }

    public boolean isAcceptableInventory(ItemStack[] stacks) {
        if (stacks == null) return true;
        if (! requireAllowedItems) return true;
        if (stacks == null) return true;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null) continue;
            if (Inventory.filterItemStack(stack, replaceItems, allowedItems, bannedItems) == null) return false;
        }
        return true;
    }

    public boolean filterInventory(ItemStack[] stacks) {
        if (stacks == null) return false;
        boolean filtered = false;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack newStack = Inventory.filterItemStack(stacks[i], replaceItems, allowedItems, bannedItems);
            if (newStack != stacks[i]) {
                stacks[i] = newStack;
                filtered = true;
            }
        }
        return filtered;
    }

    public boolean isInChatSendProximity(Location location) {
        if (! sendChat) return false;
        if (location.getWorld() != world) return false;
        if (sendChatDistance <= 0) return true;
        Vector there = new Vector(location.getX(), location.getY(), location.getZ());
        return (there.distance(center) <= sendChatDistance);
    }

    public boolean isInChatReceiveProximity(Location location) {
        if (! receiveChat) return false;
        if (location.getWorld() != world) return false;
        if (receiveChatDistance <= 0) return true;
        Vector there = new Vector(location.getX(), location.getY(), location.getZ());
        return (there.distance(center) <= receiveChatDistance);
    }

    private GateMap getBuildBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isBuildable()) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getScreenBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isScreen()) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getTriggerBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isTrigger()) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getSwitchBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isSwitch()) continue;
            map.put(this, gb);
        }
        return map;
    }

    private GateMap getPortalBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isPortal()) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getSpawnBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isSpawn()) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getSendLightningBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (gb.getDetail().getSendLightningMode() == LightningMode.NONE) continue;
            map.put(this, gb);
        }
        return map;
    }

    public GateMap getReceiveLightningBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (gb.getDetail().getReceiveLightningMode() == LightningMode.NONE) continue;
            map.put(this, gb);
        }
        return map;
    }

    public boolean isOccupyingLocation(Location location) {
        if (location.getWorld() != world) return false;
        for (GateBlock block : blocks) {
            if (! block.getDetail().isBuildable()) continue;
            if ((location.getBlockX() == block.getLocation().getBlockX()) &&
                (location.getBlockY() == block.getLocation().getBlockY()) &&
                (location.getBlockZ() == block.getLocation().getBlockZ())) return true;
        }
        return false;
    }

    private void generateFile() {
        File worldFolder = Worlds.worldPluginFolder(world);
        File gatesFolder = new File(worldFolder, "gates");
        String fileName = name.replaceAll("[^\\w-\\.]", "_");
        if (name.hashCode() > 0) fileName += "-";
        fileName += name.hashCode();
        fileName += ".yml";
        file = new File(gatesFolder, fileName);
    }

    final public void updateScreens() {
        GateMap screens = getScreenBlocks();
        if (screens.size() == 0) return;
        
        String format;
        EndpointImpl toEp = null;
        
        if (outgoing == null) {
            if (! isLinked())
                format = getLinkNoneFormat();
            else
                format = getLinkUnselectedFormat();
        } else {
            toEp = Endpoints.get(outgoing);
            if (toEp == null)
                format = getLinkOfflineFormat();
            else {
                if (! toEp.isSameServer())
                    format = getLinkServerFormat();
                else if (! ((LocalEndpointImpl)toEp).isSameWorld(world))
                    format = getLinkWorldFormat();
                else
                    format = getLinkLocalFormat();
            }
        }
        List<String> lines = new ArrayList<String>();
        
        if ((format != null) && (! format.equals("-"))) {
            format = format.replace("%fromName%", this.getName());
            format = format.replace("%fromWorld%", this.getWorld().getName());
            if (toEp != null) {
                format = format.replace("%toName%", toEp.getName());
                if (toEp.isSameServer()) {
                    format = format.replace("%toWorld%", ((LocalEndpointImpl)toEp).getWorld().getName());
                    format = format.replace("%toServer%", "local");
                } else {
                    format = format.replace("%toWorld%", ((RemoteEndpointImpl)toEp).getRemoteWorld().getName());
                    format = format.replace("%toServer%", ((RemoteEndpointImpl)toEp).getRemoteServer().getName());
                }
            } else if (outgoing != null) {
                String[] parts = outgoing.split("\\.");
                format = format.replace("%toGate%", parts[parts.length - 1]);
                if (parts.length > 1)
                    format = format.replace("%toWorld%", parts[parts.length - 2]);
                if (parts.length > 2)
                    format = format.replace("%toServer%", parts[parts.length - 3]);
                else
                    format = format.replace("%toServer%", "local");
            }
            lines.addAll(Arrays.asList(NEWLINE_PATTERN.split(format)));
        }
        
        for (GateBlock gb : screens.getBlocks()) {
            Block block = gb.getLocation().getBlock();
            BlockState sign = block.getState();
            if (! (sign instanceof Sign)) continue;
            for (int i = 0; i < 4; i++) {
                if (i >= lines.size())
                    ((Sign)sign).setLine(i, "");
                else
                    ((Sign)sign).setLine(i, lines.get(i));
            }
            sign.update();
        }
    }

    private void openPortal() {
        if (portalOpen) return;
        portalOpen = true;
        portalOpenTime = System.currentTimeMillis();
        savedBlocks = new ArrayList<SavedBlock>();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isOpenable()) continue;
            if (restoreOnClose)
                savedBlocks.add(new SavedBlock(gb.getLocation()));
            gb.getDetail().getOpenBlock().build(gb.getLocation());
        }
        if (savedBlocks.isEmpty()) savedBlocks = null;
        Gates.addPortalBlocks(getPortalBlocks());
        dirty = true;
    }

    private void closeIfAllowed() {
        if (! portalOpen) return;
        if (canClose()) close();
    }

    private boolean canClose() {
        if (duration < 1)
            return (outgoing == null) && incoming.isEmpty();

        // temporary gate
        boolean expired = ((System.currentTimeMillis() - portalOpenTime) + 50) > duration;

        // handle mutually paired gates
        if ((outgoing != null) && incoming.contains(outgoing) && (incoming.size() == 1)) return expired;

        if (incoming.isEmpty())
            return (outgoing == null) || expired;

        return false;
    }

    private void closePortal() {
        if (! portalOpen) return;
        portalOpen = false;
        if (savedBlocks != null) {
            for (SavedBlock b : savedBlocks)
                b.restore();
            savedBlocks = null;
        } else {
            for (GateBlock gb : blocks) {
                if (! gb.getDetail().isOpenable()) continue;
                if (gb.getDetail().isBuildable())
                    gb.getDetail().getBuildBlock().build(gb.getLocation());
                else
                gb.getLocation().getBlock().setTypeIdAndData(0, (byte)0, false);
            }
        }
        Gates.removePortalBlocks(this);
        dirty = true;
    }


}
