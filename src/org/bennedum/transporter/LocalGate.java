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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bennedum.transporter.GateMap.Entry;
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
public class LocalGate extends Gate implements OptionsListener {

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

    public static String getLocalLinkWorldName(String link) {
        String[] parts = link.split("\\.");
        if (parts.length > 2) return null;
        return parts[0];
    }

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

    public LocalGate(World world, String gateName, String playerName, Design design, List<GateBlock> blocks, BlockFace direction) throws GateException {
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
        protect = false;
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

    public LocalGate(World world, File file) throws GateException, BlockException {
        if (! file.exists())
            throw new GateException("%s not found", file.getAbsolutePath());
        if (! file.isFile())
            throw new GateException("%s is not a file", file.getAbsolutePath());
        if (! file.canRead())
            throw new GateException("unable to read %s", file.getAbsoluteFile());
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
            throw new GateException("invalid or ambiguous direction");
        }
        duration = conf.getInt("duration", -1);
        linkLocal = conf.getBoolean("linkLocal", true);
        linkWorld = conf.getBoolean("linkWorld", true);
        linkServer = conf.getBoolean("linkServer", true);
        
        linkNoneFormat = conf.getString("linkNoneFormat", "%fromGate%\\n\\n<none>");
        linkUnselectedFormat = conf.getString("linkUnselectedFormat", "%fromGate%\\n\\n<unselected>");
        linkOfflineFormat = conf.getString("linkOfflineFormat", "%fromGate%\\n\\n<offline>");
        linkLocalFormat = conf.getString("linkLocalFormat", "%fromGate%\\n%toGate%");
        linkWorldFormat = conf.getString("linkWorldFormat", "%fromGate%\\n%toWorld%\\n%toGate%");
        linkServerFormat = conf.getString("linkServerFormat", "%fromGate%\\n%toServer%\\n%toWorld%\\n%toGate%");
        
        multiLink = conf.getBoolean("multiLink", true);
        restoreOnClose = conf.getBoolean("restoreOnClose", false);
        links.addAll(conf.getStringList("links", new ArrayList<String>()));
        pins.addAll(conf.getStringList("pins", new ArrayList<String>()));

        List<String> items = conf.getStringList("bannedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new GateException("invalid banned item '%s'", item);
            bannedItems.add(i);
        }

        items = conf.getStringList("allowedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new GateException("invalid allowed item '%s'", item);
            allowedItems.add(i);
        }

        items = conf.getKeys("replaceItems");
        if (items != null) {
            for (String oldItem : items) {
                String oi = Inventory.normalizeItem(oldItem);
                if (oi == null)
                    throw new GateException("invalid replace item '%s'", oldItem);
                String newItem = conf.getString("replaceItems." + oldItem);
                String ni = Inventory.normalizeItem(newItem);
                if (ni == null)
                    throw new GateException("invalid replace item '%s'", newItem);
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
        teleportFormat = conf.getString("teleportFormat", ChatColor.GOLD + "teleported to '%toGateCtx%'");
        noLinksFormat = conf.getString("noLinksFormat", "this gate has no links");
        noLinkSelectedFormat = conf.getString("noLinkSelectedFormat", "no link is selected");
        invalidLinkFormat = conf.getString("invalidLinkFormat", "invalid link selected");
        unknownLinkFormat = conf.getString("unknownLinkFormat", "unknown or offline destination gate");
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
            throw new GateException("missing blocks");
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

    public synchronized void save() {
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
        synchronized (links) {
            conf.setProperty("links", links);
        }
        synchronized (pins) {
            conf.setProperty("pins", new ArrayList<String>(pins));
        }
        synchronized (bannedItems) {
            conf.setProperty("bannedItems", new ArrayList<String>(bannedItems));
        }
        synchronized (allowedItems) {
            conf.setProperty("allowedItems", new ArrayList<String>(allowedItems));
        }
        synchronized (replaceItems) {
            conf.setProperty("replaceItems", replaceItems);
        }
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

    public void saveSafe() {
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
    }

    // called from the gate collection when a gate loaded from a file
    public void initialize() {
        if (portalOpen)
            Gates.addPortalBlocks(getPortalBlocks());
        if (protect)
            Gates.addProtectBlocks(getBuildBlocks());
    }

    // called from the gate collection when a gate is destroyed
    public void destroy(boolean unbuild) {
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

    private void validate() throws GateException {
        if (name == null)
            throw new GateException("name is required");
        if (! isValidName(name))
            throw new GateException("name is not valid");
        if (creatorName == null)
            throw new GateException("creatorName is required");
        if (designName == null)
            throw new GateException("designName is required");
        if (! Design.isValidName(designName))
            throw new GateException("designName is not valid");
        if (blocks.isEmpty())
            throw new GateException("must have at least one block");
    }

    public World getWorld() {
        return world;
    }

    public Vector getCenter() {
        return center;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public BlockFace getDirection() {
        return direction;
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
    
    public String getTeleportFormat() {
        return teleportFormat;
    }

    public void setTeleportFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = ChatColor.GOLD + "teleported to '%toGateCtx%'";
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

    /* Gate interface */

    @Override
    public String getWorldName() {
        return world.getName();
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public String getDesignName() {
        return designName;
    }

    @Override
    public double getSendCost(Gate toGate) {
        if (toGate == null) return 0;
        if (toGate.isSameWorld(this)) return sendLocalCost;
        if (toGate.isSameServer()) return sendWorldCost;
        return sendServerCost;
    }

    @Override
    public double getReceiveCost(Gate fromGate) {
        if (fromGate == null) return 0;
        if (fromGate.isSameWorld(this)) return receiveLocalCost;
        if (fromGate.isSameServer()) return receiveWorldCost;
        return receiveServerCost;
    }


    @Override
    public void onRenameComplete() {
        file.delete();
        generateFile();
        saveSafe();
        updateScreens();
    }

    @Override
    protected void attach(Gate fromGate) {
        if (fromGate != null) {
            String gateName = fromGate.getFullName();
            if (incoming.contains(gateName)) return;
            incoming.add(gateName);
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
            Gate gate = Gates.get(outgoing);
            if (gate != null)
                gate.attach(this);
        }
        saveSafe();
    }

    @Override
    protected void detach(Gate fromGate) {
        String gateName = fromGate.getFullName();
        if (! incoming.contains(gateName)) return;

        incoming.remove(gateName);
        dirty = true;
        closeIfAllowed();
        saveSafe();
    }

    @Override
    public void dump(Context ctx) {
        Utils.debug("LocalGate:");
        Utils.debug("  name = %s", name);
        Utils.debug("  creatorName = %s", creatorName);
        Utils.debug("  designName = %s", designName);
        Utils.debug("  world = %s", world.getName());
        Utils.debug("  links: %d", links.size());
        for (String link : links)
            Utils.debug("    %s %s", link, (link.equals(outgoing)) ? "*": "");
        Utils.debug("  incoming: %s", incoming.size());
        for (String link : incoming)
            Utils.debug("    %s", link);
        Utils.debug("  portalOpen = %s", portalOpen);
        Utils.debug("  Portal blocks: ");
        for (Entry e : getPortalBlocks().values())
            Utils.debug("    " + e.block);
    }

    /* End interface */

    public void onGateAdded(Gate gate) {
        if (gate == this) return;
        if ((outgoing != null) && outgoing.equals(gate.getFullName()))
            updateScreens();
    }

    public void onGateRenamed(Gate gate, String oldName) {
        if (gate == this) return;
        String newName = gate.getFullName();
        synchronized (links) {
            if (links.contains(oldName)) {
                links.set(links.indexOf(oldName), newName);
                dirty = true;
            }
        }
        if (oldName.equals(outgoing)) {
            outgoing = newName;
            dirty = true;
            updateScreens();
        }
        if (incoming.contains(oldName)) {
            incoming.remove(oldName);
            incoming.add(newName);
            dirty = true;
        }
        saveSafe();
    }

    public void onGateDestroyed(LocalGate gate) {
        if (gate == this) return;
        String gateName = gate.getFullName();
        if (removeLink(gateName))
            dirty = true;
        if (gateName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        if (incoming.contains(gateName)) {
            incoming.remove(gateName);
            dirty = true;
        }
        closeIfAllowed();
        saveSafe();
    }

    public void onGateDestroyed(RemoteGate gate) {
        String gateName = gate.getFullName();
        if (removeLink(gateName))
            dirty = true;
        if (gateName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        if (incoming.contains(gateName)) {
            incoming.remove(gateName);
            dirty = true;
        }
        closeIfAllowed();
        saveSafe();
    }

    public void onGateRemoved(LocalGate gate) {
        if (gate == this) return;
        String gateName = gate.getFullName();
        if (gateName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        closeIfAllowed();
        saveSafe();
    }

    public void onGateRemoved(RemoteGate gate) {
        String gateName = gate.getFullName();
        if (gateName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            updateScreens();
        }
        closeIfAllowed();
        saveSafe();
    }

    public void onSend(Entity entity) {
        // nop
    }

    public void onReceive(Entity entity) {
        GateMap map = getLightningBlocks();
        GateBlock block = map.randomBlock();
        if (block == null) return;
        world.strikeLightningEffect(block.getLocation());
    }

    public boolean isOpen() {
        return portalOpen;
    }

    public boolean isClosed() {
        return ! portalOpen;
    }

    public void open() throws GateException {
        if (portalOpen) return;

        // try to get our destination
        if ((outgoing == null) || (! hasLink(outgoing))) {
            if (! isLinked())
                throw new GateException("this gate has no links");
            outgoing = getLinks().get(0);
            dirty = true;
            //updateScreens();
        }
        Gate gate = Gates.get(outgoing);
        if (gate == null)
            throw new GateException("unknown or offline gate '%s'", outgoing);

        openPortal();
        gate.attach(this);
        updateScreens();
        saveSafe();

        if (duration > 0) {
            final LocalGate myself = this;
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
            Gate gate = Gates.get(outgoing);
            if (gate != null)
                gate.detach(this);
        }
        saveSafe();
    }

    public boolean addLink(String link) {
        synchronized (links) {
            if (links.contains(link)) return false;
            links.add(link);
            if (links.size() == 1)
                outgoing = link;
        }
        dirty = true;
        updateScreens();
        saveSafe();
        return true;
    }

    public boolean removeLink(String link) {
        synchronized (links) {
            if (! links.contains(link)) return false;
            links.remove(link);
            if (link.equals(outgoing))
                outgoing = null;
        }
        dirty = true;
        updateScreens();
        closeIfAllowed();
        saveSafe();
        return true;
    }

    public List<String> getLinks() {
        synchronized (links) {
            return new ArrayList<String>(links);
        }
    }

    public boolean hasLink(String link) {
        synchronized (links) {
            return links.contains(link);
        }
    }

    public boolean isLinked() {
        synchronized (links) {
            return ! links.isEmpty();
        }
    }

    public boolean isLastLink() {
        synchronized (links) {
            if (outgoing == null)
                return links.isEmpty();
            return links.indexOf(outgoing) == (links.size() - 1);
        }
    }

    public void nextLink() throws GateException {
        synchronized (links) {
            // trivial case of single link to prevent needless detach/attach
            if ((links.size() == 1) && links.contains(outgoing)) {
                //updateScreens();
                return;
            }
        }

        // detach from the current gate
        if (portalOpen && (outgoing != null)) {
            Gate gate = Gates.get(outgoing);
            if (gate != null)
                gate.detach(this);
        }

        synchronized (links) {
            // select next link
            if ((outgoing == null) || (! links.contains(outgoing))) {
                if (! links.isEmpty()) {
                    outgoing = links.get(0);
                    dirty = true;
                }
            } else {
                int i = links.indexOf(outgoing) + 1;
                if (i >= links.size()) i = 0;
                outgoing = links.get(i);
                dirty = true;
            }
        }

        updateScreens();

        // attach to the next gate
        if (portalOpen && (outgoing != null)) {
            Gate gate = Gates.get(outgoing);
            if (gate != null)
                gate.attach(this);
        }
        saveSafe();
        getDestinationGate();
    }

    public boolean hasValidDestination() {
        try {
            getDestinationGate();
            return true;
        } catch (GateException e) {
            return false;
        }
    }

    public String getDestinationLink() {
        return outgoing;
    }

    public Gate getDestinationGate() throws GateException {
        if (outgoing == null) {
            if (! isLinked())
                throw new GateException(getNoLinksFormat());
            else
                throw new GateException(getNoLinkSelectedFormat());
        } else if (! hasLink(outgoing))
            throw new GateException(getInvalidLinkFormat());
        Gate gate = Gates.get(outgoing);
        if (gate == null)
            throw new GateException(getUnknownLinkFormat());
        return gate;
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

    public boolean addPin(String pin) throws GateException {
        if (! isValidPin(pin))
            throw new GateException("invalid pin");
        synchronized (pins) {
            if (pins.contains(pin)) return false;
            pins.add(pin);
        }
        return true;
    }

    public boolean removePin(String pin) {
        synchronized (pins) {
            if (pins.contains(pin)) return false;
            pins.remove(pin);
        }
        saveSafe();
        return true;
    }

    public void removeAllPins() {
        synchronized (pins) {
            pins.clear();
        }
        saveSafe();
    }

    public boolean hasPin(String pin) {
        synchronized (pins) {
            return pins.contains(pin);
        }
    }

    public boolean addBannedItem(String item) throws GateException {
        try {
            if (! Inventory.appendItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public boolean removeBannedItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public void removeAllBannedItems() {
        synchronized (bannedItems) {
            bannedItems.clear();
        }
        dirty = true;
        saveSafe();
    }

    public boolean addAllowedItem(String item) throws GateException {
        try {
            if (! Inventory.appendItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public boolean removeAllowedItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public void removeAllAllowedItems() {
        synchronized (allowedItems) {
            allowedItems.clear();
        }
        dirty = true;
        saveSafe();
    }

    public boolean addReplaceItem(String fromItem, String toItem) throws GateException {
        try {
            if (! Inventory.appendItemMap(replaceItems, fromItem, toItem)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public boolean removeReplaceItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemMap(replaceItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        saveSafe();
        return true;
    }

    public void removeAllReplaceItems() {
        synchronized (replaceItems) {
            replaceItems.clear();
        }
        dirty = true;
        saveSafe();
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

    private GateMap getLightningBlocks() {
        GateMap map = new GateMap();
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isLightning()) continue;
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
        String format;
        Gate toGate = null;
        
        if (outgoing == null) {
            if (! isLinked())
                format = getLinkNoneFormat();
            else
                format = getLinkUnselectedFormat();
        } else {
            toGate = Gates.get(outgoing);
            if (toGate == null)
                format = getLinkOfflineFormat();
            else {
                if (! toGate.isSameServer())
                    format = getLinkServerFormat();
                else if (! toGate.isSameWorld(world))
                    format = getLinkWorldFormat();
                else
                    format = getLinkLocalFormat();
            }
        }
        List<String> lines = new ArrayList<String>();
        
        if ((format != null) && (! format.equals("-"))) {
            format = format.replace("%fromGate%", this.getName());
            format = format.replace("%fromWorld%", this.getWorldName());
            if (toGate != null) {
                format = format.replace("%toGate%", toGate.getName());
                format = format.replace("%toWorld%", toGate.getWorldName());
                format = format.replace("%toServer%", (toGate.getServerName() == null) ? "local" : toGate.getServerName());
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
        
        for (GateBlock gb : blocks) {
            if (! gb.getDetail().isScreen()) continue;
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
