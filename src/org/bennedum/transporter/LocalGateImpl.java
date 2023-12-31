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

import org.bennedum.transporter.api.TransporterException;
import org.bennedum.transporter.api.GateException;
import org.bennedum.transporter.api.GateType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bennedum.transporter.api.LocalGate;
import org.bennedum.transporter.command.CommandException;
import org.bennedum.transporter.config.Configuration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class LocalGateImpl extends GateImpl implements LocalGate, OptionsListener {

    public static LocalGateImpl load(World world, File file) throws GateException {
        if (! file.exists())
            throw new GateException("%s not found", file.getAbsolutePath());
        if (! file.isFile())
            throw new GateException("%s is not a file", file.getAbsolutePath());
        if (! file.canRead())
            throw new GateException("unable to read %s", file.getAbsoluteFile());
        Configuration conf = new Configuration(file);
        conf.load();
        String typeStr = conf.getString("type", "BLOCK");
        GateType type;
        try {
            type = Utils.valueOf(GateType.class, typeStr);
        } catch (IllegalArgumentException iae) {
            throw new GateException(iae.getMessage() + " gate type '%s'", typeStr);
        }
        
        switch (type) {
            case BLOCK:
                return new LocalBlockGateImpl(world, conf);
            case AREA:
                return new LocalAreaGateImpl(world, conf);
        }
        throw new GateException("unknown gate type '%s'", type.toString());
    }
    
    protected static final Set<String> BASEOPTIONS = new HashSet<String>();
    
    static {
        BASEOPTIONS.add("duration");
        BASEOPTIONS.add("linkLocal");
        BASEOPTIONS.add("linkWorld");
        BASEOPTIONS.add("linkServer");
        BASEOPTIONS.add("linkNoneFormat");
        BASEOPTIONS.add("linkUnselectedFormat");
        BASEOPTIONS.add("linkOfflineFormat");
        BASEOPTIONS.add("linkLocalFormat");
        BASEOPTIONS.add("linkWorldFormat");
        BASEOPTIONS.add("linkServerFormat");
        BASEOPTIONS.add("multiLink");
        BASEOPTIONS.add("protect");
        BASEOPTIONS.add("requirePin");
        BASEOPTIONS.add("requireValidPin");
        BASEOPTIONS.add("invalidPinDamage");
        BASEOPTIONS.add("sendChat");
        BASEOPTIONS.add("sendChatDistance");
        BASEOPTIONS.add("receiveChat");
        BASEOPTIONS.add("receiveChatDistance");
        BASEOPTIONS.add("requireAllowedItems");
        BASEOPTIONS.add("receiveInventory");
        BASEOPTIONS.add("deleteInventory");
        BASEOPTIONS.add("receiveGameMode");
        BASEOPTIONS.add("allowGameModes");
        BASEOPTIONS.add("gameMode");
        BASEOPTIONS.add("receiveXP");
        BASEOPTIONS.add("receivePotions");
        BASEOPTIONS.add("requireAllowedPotions");
        BASEOPTIONS.add("randomNextLink");
        BASEOPTIONS.add("sendNextLink");
        BASEOPTIONS.add("teleportFormat");
        BASEOPTIONS.add("noLinksFormat");
        BASEOPTIONS.add("noLinkSelectedFormat");
        BASEOPTIONS.add("invalidLinkFormat");
        BASEOPTIONS.add("unknownLinkFormat");
        BASEOPTIONS.add("linkLocalCost");
        BASEOPTIONS.add("linkWorldCost");
        BASEOPTIONS.add("linkServerCost");
        BASEOPTIONS.add("sendLocalCost");
        BASEOPTIONS.add("sendWorldCost");
        BASEOPTIONS.add("sendServerCost");
        BASEOPTIONS.add("receiveLocalCost");
        BASEOPTIONS.add("receiveWorldCost");
        BASEOPTIONS.add("receiveServerCost");
        BASEOPTIONS.add("markerFormat");
    }

    protected File file;
    protected World world;
    protected Vector center;
    protected String creatorName;
    protected BlockFace direction;
    
    protected int duration;
    protected boolean linkLocal;
    protected boolean linkWorld;
    protected boolean linkServer;
    protected String linkNoneFormat;
    protected String linkUnselectedFormat;
    protected String linkOfflineFormat;
    protected String linkLocalFormat;
    protected String linkWorldFormat;
    protected String linkServerFormat;
    protected boolean multiLink;
    protected boolean requirePin;
    protected boolean requireValidPin;
    protected int invalidPinDamage;
    protected boolean protect;
    protected boolean sendChat;
    protected int sendChatDistance;
    protected boolean receiveChat;
    protected int receiveChatDistance;
    protected boolean requireAllowedItems;
    protected boolean receiveInventory;
    protected boolean deleteInventory;
    protected boolean receiveGameMode;
    protected String allowGameModes;
    protected GameMode gameMode;
    protected boolean receiveXP;
    protected boolean receivePotions;
    protected boolean requireAllowedPotions;
    protected boolean randomNextLink;
    protected boolean sendNextLink;
    protected String teleportFormat;
    protected String noLinksFormat;
    protected String noLinkSelectedFormat;
    protected String invalidLinkFormat;
    protected String unknownLinkFormat;
    protected String markerFormat;

    protected double linkLocalCost;
    protected double linkWorldCost;
    protected double linkServerCost;
    protected double sendLocalCost;
    protected double sendWorldCost;
    protected double sendServerCost;
    protected double receiveLocalCost;
    protected double receiveWorldCost;
    protected double receiveServerCost;

    protected final List<String> links = new ArrayList<String>();
    protected final Set<String> pins = new HashSet<String>();
    protected final Set<String> bannedItems = new HashSet<String>();
    protected final Set<String> allowedItems = new HashSet<String>();
    protected final Map<String,String> replaceItems = new HashMap<String,String>();
    protected final Set<String> bannedPotions = new HashSet<String>();
    protected final Set<String> allowedPotions = new HashSet<String>();
    protected final Map<String,String> replacePotions = new HashMap<String,String>();

    protected Set<String> incoming = new HashSet<String>();
    protected String outgoing = null;

    protected boolean dirty = false;
    protected boolean portalOpen = false;
    protected long portalOpenTime = 0;
    protected Options options = new Options(this, BASEOPTIONS, "trp.gate", this);

    protected LocalGateImpl(World world, Configuration conf) throws GateException {
        this.file = conf.getFile();
        this.world = world;
        name = conf.getString("name");
        creatorName = conf.getString("creatorName");
        try {
            direction = Utils.valueOf(BlockFace.class, conf.getString("direction", "NORTH"));
        } catch (IllegalArgumentException iae) {
            throw new GateException(iae.getMessage() + " direction");
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
        links.addAll(conf.getStringList("links", new ArrayList<String>()));
        pins.addAll(conf.getStringList("pins", new ArrayList<String>()));
        portalOpen = conf.getBoolean("portalOpen", false);

        String gameModeStr = conf.getString("gameMode", null);
        if (gameModeStr == null)
            gameMode = null;
        else {
            try {
                gameMode = Utils.valueOf(GameMode.class, gameModeStr);
            } catch (IllegalArgumentException iae) {
                throw new GateException(iae.getMessage() + " game mode '%s'", gameModeStr);
            }
        }
        
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

        List<String> potions = conf.getStringList("bannedPotions", new ArrayList<String>());
        for (String potion : potions) {
            String p = PotionEffects.normalizePotion(potion);
            if (p == null)
                throw new GateException("invalid banned potion effect '%s'", potion);
            bannedPotions.add(p);
        }

        potions = conf.getStringList("allowedPotions", new ArrayList<String>());
        for (String potion : potions) {
            String p = PotionEffects.normalizePotion(potion);
            if (p == null)
                throw new GateException("invalid allowed potion effect '%s'", potion);
            allowedPotions.add(p);
        }

        potions = conf.getKeys("replacePotions");
        if (potions != null) {
            for (String oldPotion : potions) {
                String op = PotionEffects.normalizePotion(oldPotion);
                if (op == null)
                    throw new GateException("invalid replace potion effect '%s'", oldPotion);
                String newPotion = conf.getString("replacePotions." + oldPotion);
                String np = PotionEffects.normalizePotion(newPotion);
                if (np == null)
                    throw new GateException("invalid replace potion effect '%s'", newPotion);
                replacePotions.put(op, np);
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
        receiveInventory = conf.getBoolean("receiveInventory", true);
        deleteInventory = conf.getBoolean("deleteInventory", false);
        receiveGameMode = conf.getBoolean("receiveGameMode", false);
        allowGameModes = conf.getString("allowGameModes", "*");
        receiveXP = conf.getBoolean("receiveXP", false);
        receivePotions = conf.getBoolean("receivePotions", false);
        requireAllowedPotions = conf.getBoolean("requireAllowedPotions", true);
        randomNextLink = conf.getBoolean("randomNextLink", false);
        sendNextLink = conf.getBoolean("sendNextLink", false);
        teleportFormat = conf.getString("teleportFormat", "%GOLD%teleported to '%toGateCtx%'");
        noLinksFormat = conf.getString("noLinksFormat", "this gate has no links");
        noLinkSelectedFormat = conf.getString("noLinkSelectedFormat", "no link is selected");
        invalidLinkFormat = conf.getString("invalidLinkFormat", "invalid link selected");
        unknownLinkFormat = conf.getString("unknownLinkFormat", "unknown or offline destination gate");
        markerFormat = conf.getString("markerFormat", "%name%");

        incoming.addAll(conf.getStringList("incoming", new ArrayList<String>()));
        outgoing = conf.getString("outgoing");

        linkLocalCost = conf.getDouble("linkLocalCost", 0);
        linkWorldCost = conf.getDouble("linkWorldCost", 0);
        linkServerCost = conf.getDouble("linkServerCost", 0);
        sendLocalCost = conf.getDouble("sendLocalCost", 0);
        sendWorldCost = conf.getDouble("sendWorldCost", 0);
        sendServerCost = conf.getDouble("sendServerCost", 0);
        receiveLocalCost = conf.getDouble("receiveLocalCost", 0);
        receiveWorldCost = conf.getDouble("receiveWorldCost", 0);
        receiveServerCost = conf.getDouble("receiveServerCost", 0);
    }

    protected LocalGateImpl(World world, String gateName, String creatorName, BlockFace direction) throws GateException {
        this.world = world;
        name = gateName;
        this.creatorName = creatorName;
        this.direction = direction;

        setDuration(-1);
        setLinkLocal(true);
        setLinkWorld(true);
        setLinkServer(true);
        setLinkNoneFormat(null);
        setLinkUnselectedFormat(null);
        setLinkOfflineFormat(null);
        setLinkLocalFormat(null);
        setLinkWorldFormat(null);
        setLinkServerFormat(null);
        setMultiLink(true);
        setRequirePin(false);
        setRequireValidPin(true);
        setInvalidPinDamage(0);
        setProtect(false);
        setSendChat(false);
        setSendChatDistance(1000);
        setReceiveChat(false);
        setReceiveChatDistance(1000);
        setRequireAllowedItems(true);
        setReceiveInventory(true);
        setDeleteInventory(false);
        setReceiveGameMode(false);
        setAllowGameModes("*");
        setGameMode(null);
        setReceiveXP(false);
        setReceivePotions(false);
        setRequireAllowedPotions(true);
        setRandomNextLink(false);
        setSendNextLink(false);
        setTeleportFormat(null);
        setNoLinksFormat(null);
        setNoLinkSelectedFormat(null);
        setInvalidLinkFormat(null);
        setUnknownLinkFormat(null);
        setMarkerFormat(null);

        setLinkLocalCost(0);
        setLinkWorldCost(0);
        setLinkServerCost(0);
        setSendLocalCost(0);
        setSendWorldCost(0);
        setSendServerCost(0);
        setReceiveLocalCost(0);
        setReceiveWorldCost(0);
        setReceiveServerCost(0);
    }
    
    @Override
    public abstract GateType getType();
    public abstract Location getSpawnLocation(Location fromLoc, BlockFace fromDirection);
    
    public abstract void onSend(Entity entity);
    public abstract void onReceive(Entity entity);
    public abstract void onProtect(Location loc);
    
    protected abstract void onValidate() throws GateException;
    protected abstract void onDestroy(boolean unbuild);
    protected abstract void onAdd();
    protected abstract void onRemove();
    protected abstract void onOpen();
    protected abstract void onClose();
    protected abstract void onNameChanged();
    protected abstract void onDestinationChanged();
    protected abstract void onSave(Configuration conf);

    protected abstract void calculateCenter();
    
    // Gate interface
    
    @Override
    public String getLocalName() {
        return world.getName() + "." + getName();
    }
    
    @Override
    public String getFullName() {
        return getLocalName();
    }
    
    // GateImpl overrides
    
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
    protected void attach(GateImpl origin) {
        if (origin != null) {
            String originName = origin.getFullName();
            if (incoming.contains(originName)) return;
            incoming.add(originName);
            dirty = true;
        }
        onOpen();

        // try to attach to our destination
        if ((outgoing == null) || (! hasLink(outgoing))) {
            if (! isLinked())
                outgoing = null;
            else
                outgoing = getLinks().get(0);
            onDestinationChanged();
        }
        if (outgoing != null) {
            GateImpl gate = Gates.get(outgoing);
            if (gate != null)
                gate.attach(this);
        }
    }

    @Override
    protected void detach(GateImpl origin) {
        String originName = origin.getFullName();
        if (! incoming.contains(originName)) return;

        incoming.remove(originName);
        dirty = true;
        closeIfAllowed();
    }

    // End interfaces and implementations
    
    public void onRenameComplete() {
        file.delete();
        generateFile();
        save(true);
        onNameChanged();
    }
    
    public void onGateAdded(GateImpl gate) {
        if (gate == this)
            onAdd();
        else {
            if ((outgoing != null) && outgoing.equals(gate.getFullName()))
                onDestinationChanged();
        }
    }

    public void onGateRemoved(GateImpl gate) {
        if (gate == this)
            onRemove();
        else {
            String gateName = gate.getFullName();
            if (gateName.equals(outgoing)) {
                //outgoing = null;
                //dirty = true;
                onDestinationChanged();
            }
            closeIfAllowed();
        }
    }

    public void onGateDestroyed(GateImpl gate) {
        if (gate == this) return;
        String gateName = gate.getFullName();
        if (removeLink(gateName))
            dirty = true;
        if (gateName.equals(outgoing)) {
            outgoing = null;
            dirty = true;
            onDestinationChanged();
        }
        if (incoming.contains(gateName)) {
            incoming.remove(gateName);
            dirty = true;
        }
        closeIfAllowed();
    }
    
    public void onGateRenamed(GateImpl gate, String oldFullName) {
        if (gate == this) return;
        String newName = gate.getFullName();
        if (links.contains(oldFullName)) {
            links.set(links.indexOf(oldFullName), newName);
            dirty = true;
        }
        if (oldFullName.equals(outgoing)) {
            outgoing = newName;
            dirty = true;
            onDestinationChanged();
        }
        if (incoming.contains(oldFullName)) {
            incoming.remove(oldFullName);
            incoming.add(newName);
            dirty = true;
        }
    }
    
    public void destroy(boolean unbuild) {
        close();
        file.delete();
        file = null;
        onDestroy(unbuild);
    }

    public boolean isOpen() {
        return portalOpen;
    }

    public boolean isClosed() {
        return ! portalOpen;
    }

    public boolean isSameWorld(World world) {
        return this.world == world;
    }
    
    public World getWorld() {
        return world;
    }
    
    public void open() throws GateException {
        if (portalOpen) return;

        // try to get our destination
        if ((outgoing == null) || (! hasLink(outgoing))) {
            if (! isLinked())
                throw new GateException("this gate has no links");
            outgoing = getLinks().get(0);
            dirty = true;
        }
        GateImpl gate = Gates.get(outgoing);
        if (gate == null)
            throw new GateException("unknown or offline gate '%s'", outgoing);

        portalOpen = true;
        portalOpenTime = System.currentTimeMillis();
        gate.attach(this);
        onOpen();
        onDestinationChanged();

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
        portalOpen = false;

        incoming.clear();
        onClose();
        onDestinationChanged();

        // try to detach from our destination
        if (outgoing != null) {
            GateImpl gate = Gates.get(outgoing);
            if (gate != null)
                gate.detach(this);
        }
    }

    @Override
    public void save(boolean force) {
        if ((! dirty) && (! force)) return;
        if (file == null) return;
        dirty = false;

        Configuration conf = new Configuration(file);
        conf.setProperty("name", name);
        conf.setProperty("type", getType().toString());
        conf.setProperty("creatorName", creatorName);
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
        conf.setProperty("receiveInventory", receiveInventory);
        conf.setProperty("deleteInventory", deleteInventory);
        conf.setProperty("receiveGameMode", receiveGameMode);
        conf.setProperty("allowGameModes", allowGameModes);
        conf.setProperty("gameMode", gameMode);
        conf.setProperty("receiveXP", receiveXP);
        conf.setProperty("receivePotions", receivePotions);
        conf.setProperty("requireAllowedPotions", requireAllowedPotions);
        conf.setProperty("bannedPotions", new ArrayList<String>(bannedPotions));
        conf.setProperty("allowedPotions", new ArrayList<String>(allowedPotions));
        conf.setProperty("replacePotions", replacePotions);
        conf.setProperty("randomNextLink", randomNextLink);
        conf.setProperty("sendNextLink", sendNextLink);
        conf.setProperty("teleportFormat", teleportFormat);
        conf.setProperty("noLinksFormat", noLinksFormat);
        conf.setProperty("noLinkSelectedFormat", noLinkSelectedFormat);
        conf.setProperty("invalidLinkFormat", invalidLinkFormat);
        conf.setProperty("unknownLinkFormat", unknownLinkFormat);
        conf.setProperty("markerFormat", markerFormat);
        conf.setProperty("portalOpen", portalOpen);

        if (! incoming.isEmpty()) conf.setProperty("incoming", new ArrayList<String>(incoming));
        if (outgoing != null) conf.setProperty("outgoing", outgoing);

        conf.setProperty("linkLocalCost", linkLocalCost);
        conf.setProperty("linkWorldCost", linkWorldCost);
        conf.setProperty("linkServerCost", linkServerCost);
        conf.setProperty("sendLocalCost", sendLocalCost);
        conf.setProperty("sendWorldCost", sendWorldCost);
        conf.setProperty("sendServerCost", sendServerCost);
        conf.setProperty("receiveLocalCost", receiveLocalCost);
        conf.setProperty("receiveWorldCost", receiveWorldCost);
        conf.setProperty("receiveServerCost", receiveServerCost);

        onSave(conf);
        
        File parent = file.getParentFile();
        if (! parent.exists())
            parent.mkdirs();
        conf.save();
    }

    protected void validate() throws GateException {
        if (name == null)
            throw new GateException("name is required");
        if (! isValidName(name))
            throw new GateException("name is not valid");
        if (creatorName == null)
            throw new GateException("creatorName is required");
        onValidate();
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

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public void setDuration(int i) {
        duration = i;
        dirty = true;
    }

    @Override
    public boolean getLinkLocal() {
        return linkLocal;
    }

    @Override
    public void setLinkLocal(boolean b) {
        linkLocal = b;
        dirty = true;
    }

    @Override
    public boolean getLinkWorld() {
        return linkWorld;
    }

    @Override
    public void setLinkWorld(boolean b) {
        linkWorld = b;
        dirty = true;
    }

    @Override
    public boolean getLinkServer() {
        return linkServer;
    }

    @Override
    public void setLinkServer(boolean b) {
        linkServer = b;
        dirty = true;
    }

    @Override
    public String getLinkNoneFormat() {
        return linkNoneFormat;
    }
    
    @Override
    public void setLinkNoneFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n\\n<none>";
        linkNoneFormat = s;
        dirty = true;
    }

    @Override
    public String getLinkUnselectedFormat() {
        return linkUnselectedFormat;
    }
    
    @Override
    public void setLinkUnselectedFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n\\n<unselected>";
        linkUnselectedFormat = s;
        dirty = true;
    }

    @Override
    public String getLinkOfflineFormat() {
        return linkOfflineFormat;
    }
    
    @Override
    public void setLinkOfflineFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n\\n<offline>";
        linkOfflineFormat = s;
        dirty = true;
    }

    @Override
    public String getLinkLocalFormat() {
        return linkLocalFormat;
    }
    
    @Override
    public void setLinkLocalFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n%toGate%";
        linkLocalFormat = s;
        dirty = true;
    }

    @Override
    public String getLinkWorldFormat() {
        return linkWorldFormat;
    }
    
    @Override
    public void setLinkWorldFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n%toWorld%\\n%toGate%";
        linkWorldFormat = s;
        dirty = true;
    }

    @Override
    public String getLinkServerFormat() {
        return linkServerFormat;
    }
    
    @Override
    public void setLinkServerFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%fromGate%\\n%toServer%\\n%toWorld%\\n%toGate%";
        linkServerFormat = s;
        dirty = true;
    }
        
    @Override
    public boolean getMultiLink() {
        return multiLink;
    }

    @Override
    public void setMultiLink(boolean b) {
        multiLink = b;
        dirty = true;
    }

    @Override
    public boolean getProtect() {
        return protect;
    }

    @Override
    public void setProtect(boolean b) {
        protect = b;
        dirty = true;
    }

    @Override
    public boolean getRequirePin() {
        return requirePin;
    }

    @Override
    public void setRequirePin(boolean b) {
        requirePin = b;
        dirty = true;
    }

    @Override
    public boolean getRequireValidPin() {
        return requireValidPin;
    }

    @Override
    public void setRequireValidPin(boolean b) {
        requireValidPin = b;
        dirty = true;
    }

    @Override
    public int getInvalidPinDamage() {
        return invalidPinDamage;
    }

    @Override
    public void setInvalidPinDamage(int i) {
        if (i < 0)
            throw new IllegalArgumentException("invalidPinDamage must be at least 0");
        invalidPinDamage = i;
        dirty = true;
    }

    @Override
    public boolean getSendChat() {
        return sendChat;
    }

    @Override
    public void setSendChat(boolean b) {
        sendChat = b;
        dirty = true;
    }

    @Override
    public int getSendChatDistance() {
        return sendChatDistance;
    }

    @Override
    public void setSendChatDistance(int i) {
        sendChatDistance = i;
        dirty = true;
    }

    @Override
    public boolean getReceiveChat() {
        return receiveChat;
    }

    @Override
    public void setReceiveChat(boolean b) {
        receiveChat = b;
        dirty = true;
    }

    @Override
    public int getReceiveChatDistance() {
        return receiveChatDistance;
    }

    @Override
    public void setReceiveChatDistance(int i) {
        receiveChatDistance = i;
        dirty = true;
    }

    @Override
    public boolean getRequireAllowedItems() {
        return requireAllowedItems;
    }

    @Override
    public void setRequireAllowedItems(boolean b) {
        requireAllowedItems = b;
        dirty = true;
    }

    @Override
    public boolean getReceiveInventory() {
        return receiveInventory;
    }

    @Override
    public void setReceiveInventory(boolean b) {
        receiveInventory = b;
        dirty = true;
    }

    @Override
    public boolean getDeleteInventory() {
        return deleteInventory;
    }

    @Override
    public void setDeleteInventory(boolean b) {
        deleteInventory = b;
        dirty = true;
    }

    @Override
    public boolean getReceiveGameMode() {
        return receiveGameMode;
    }

    @Override
    public void setReceiveGameMode(boolean b) {
        receiveGameMode = b;
        dirty = true;
    }

    @Override
    public String getAllowGameModes() {
        return allowGameModes;
    }

    @Override
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
                throw new IllegalArgumentException(e.getMessage() + " game mode '" + part + "'");
            }
        }
        allowGameModes = modes.substring(0, modes.length() - 1);
        dirty = true;
    }

    @Override
    public GameMode getGameMode() {
        return gameMode;
    }
    
    @Override
    public void setGameMode(GameMode m) {
        gameMode = m;
        dirty = true;
    }
    
    @Override
    public boolean getReceiveXP() {
        return receiveXP;
    }

    @Override
    public void setReceiveXP(boolean b) {
        receiveXP = b;
        dirty = true;
    }
    
    @Override
    public boolean getReceivePotions() {
        return receivePotions;
    }
    
    @Override
    public void setReceivePotions(boolean b) {
        receivePotions = b;
        dirty = true;
    }

    @Override
    public boolean getRequireAllowedPotions() {
        return requireAllowedPotions;
    }

    @Override
    public void setRequireAllowedPotions(boolean b) {
        requireAllowedPotions = b;
        dirty = true;
    }
    
    @Override
    public boolean getRandomNextLink() {
        return randomNextLink;
    }

    @Override
    public void setRandomNextLink(boolean b) {
        randomNextLink = b;
        dirty = true;
    }
    
    @Override
    public boolean getSendNextLink() {
        return sendNextLink;
    }

    @Override
    public void setSendNextLink(boolean b) {
        sendNextLink = b;
        dirty = true;
    }
    
    @Override
    public String getTeleportFormat() {
        return teleportFormat;
    }

    @Override
    public void setTeleportFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%GOLD%teleported to '%toGateCtx%'";
        teleportFormat = s;
        dirty = true;
    }

    @Override
    public String getNoLinksFormat() {
        return noLinksFormat;
    }

    @Override
    public void setNoLinksFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "this gate has no links";
        noLinksFormat = s;
        dirty = true;
    }

    @Override
    public String getNoLinkSelectedFormat() {
        return noLinkSelectedFormat;
    }

    @Override
    public void setNoLinkSelectedFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "no link is selected";
        noLinkSelectedFormat = s;
        dirty = true;
    }

    @Override
    public String getInvalidLinkFormat() {
        return invalidLinkFormat;
    }

    @Override
    public void setInvalidLinkFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "invalid link selected";
        invalidLinkFormat = s;
        dirty = true;
    }

    @Override
    public String getUnknownLinkFormat() {
        return unknownLinkFormat;
    }

    @Override
    public void setUnknownLinkFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "unknown or offline destination gate";
        unknownLinkFormat = s;
        dirty = true;
    }

    @Override
    public String getMarkerFormat() {
        return markerFormat;
    }

    @Override
    public void setMarkerFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        if (s == null) s = "%name%";
        markerFormat = s;
        dirty = true;
    }

    @Override
    public double getLinkLocalCost() {
        return linkLocalCost;
    }

    @Override
    public void setLinkLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkLocalCost must be at least 0");
        linkLocalCost = cost;
        dirty = true;
    }

    @Override
    public double getLinkWorldCost() {
        return linkWorldCost;
    }

    @Override
    public void setLinkWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkWorldCost must be at least 0");
        linkWorldCost = cost;
        dirty = true;
    }

    @Override
    public double getLinkServerCost() {
        return linkServerCost;
    }

    @Override
    public void setLinkServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("linkServerCost must be at least 0");
        linkServerCost = cost;
        dirty = true;
    }

    @Override
    public double getSendLocalCost() {
        return sendLocalCost;
    }

    @Override
    public void setSendLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendLocalCost must be at least 0");
        sendLocalCost = cost;
        dirty = true;
    }

    @Override
    public double getSendWorldCost() {
        return sendWorldCost;
    }

    @Override
    public void setSendWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendWorldCost must be at least 0");
        sendWorldCost = cost;
        dirty = true;
    }

    @Override
    public double getSendServerCost() {
        return sendServerCost;
    }

    @Override
    public void setSendServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("sendServerCost must be at least 0");
        sendServerCost = cost;
        dirty = true;
    }

    @Override
    public double getReceiveLocalCost() {
        return receiveLocalCost;
    }

    @Override
    public void setReceiveLocalCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveLocalCost must be at least 0");
        receiveLocalCost = cost;
        dirty = true;
    }

    @Override
    public double getReceiveWorldCost() {
        return receiveWorldCost;
    }

    @Override
    public void setReceiveWorldCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveWorldCost must be at least 0");
        receiveWorldCost = cost;
        dirty = true;
    }

    @Override
    public double getReceiveServerCost() {
        return receiveServerCost;
    }

    @Override
    public void setReceiveServerCost(double cost) {
        if (cost < 0)
            throw new IllegalArgumentException("receiveServerCost must be at least 0");
        receiveServerCost = cost;
        dirty = true;
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
        ctx.send("option '%s' set to '%s' for gate '%s'", name, value, getName(ctx));
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name + "." + name;
    }

    /* End options */

    public double getSendCost(GateImpl toGate) {
        if (toGate == null) return 0;
        if (! toGate.isSameServer()) return sendServerCost;
        if (((LocalGateImpl)toGate).isSameWorld(world)) return sendLocalCost;
        return sendWorldCost;
    }

    public double getReceiveCost(GateImpl fromGate) {
        if (fromGate == null) return 0;
        if (! fromGate.isSameServer()) return receiveServerCost;
        if (((LocalGateImpl)fromGate).isSameWorld(world)) return receiveLocalCost;
        return receiveWorldCost;
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

    public void addLink(Context ctx, String toGateName) throws TransporterException {
        Permissions.require(ctx.getPlayer(), "trp.gate.link.add." + getLocalName());

        if (isLinked() && (! getMultiLink()))
            throw new GateException("gate '%s' cannot accept multiple links", getName(ctx));

        GateImpl toGate = Gates.find(ctx, toGateName);
        if (toGate == null)
            throw new GateException("gate '%s' cannot be found", toGateName);
        
        if (toGate.isSameServer()) {
            LocalGateImpl toGateLocal = (LocalGateImpl)toGate;
            if (isSameWorld(toGateLocal.getWorld()) && (! Config.getAllowLinkLocal()))
                throw new CommandException("linking to on-world gates is not permitted");
            else if (! Config.getAllowLinkWorld())
                throw new CommandException("linking to off-world gates is not permitted");
            if (isSameWorld(toGateLocal.getWorld()))
                Economy.requireFunds(ctx.getPlayer(), getLinkLocalCost());
            else 
                Economy.requireFunds(ctx.getPlayer(), getLinkWorldCost());

        } else {
            if (! Config.getAllowLinkServer())
                throw new CommandException("linking to remote gates is not permitted");
            Economy.requireFunds(ctx.getPlayer(), getLinkServerCost());
        }

        if (! addLink(toGate.getFullName()))
            throw new GateException("gate '%s' already links to '%s'", getName(ctx), toGate.getName(ctx));

        ctx.sendLog("added link from '%s' to '%s'", getName(ctx), toGate.getName(ctx));

        try {
            if (toGate.isSameServer()) {
                LocalGateImpl toGateLocal = (LocalGateImpl)toGate;
                if (isSameWorld(toGateLocal.getWorld()) && Economy.deductFunds(ctx.getPlayer(), getLinkLocalCost()))
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

    protected boolean addLink(String link) {
        if (links.contains(link)) return false;
        links.add(link);
        if (links.size() == 1)
            outgoing = link;
        onDestinationChanged();
        dirty = true;
        return true;
    }

    public void removeLink(Context ctx, String toGateName) throws TransporterException {
        Permissions.require(ctx.getPlayer(), "trp.gate.link.remove." + getFullName());

        GateImpl toGate = Gates.find(toGateName);
        if (toGate != null) toGateName = toGate.getFullName();

        if (! removeLink(toGateName))
            throw new GateException("gate '%s' does not have a link to '%s'", getName(ctx), toGateName);

        ctx.sendLog("removed link from '%s' to '%s'", getName(ctx), toGateName);
    }
    
    protected boolean removeLink(String link) {
        if (! links.contains(link)) return false;
        links.remove(link);
        if (link.equals(outgoing))
            outgoing = null;
        onDestinationChanged();
        closeIfAllowed();
        dirty = true;
        return true;
    }
    
    public void nextLink() throws GateException {
        // trivial case of single link to prevent needless detach/attach
        if ((links.size() == 1) && links.contains(outgoing)) {
            //updateScreens();
            return;
        }

        // detach from the current gate
        if (portalOpen && (outgoing != null)) {
            GateImpl gate = Gates.get(outgoing);
            if (gate != null)
                gate.detach(this);
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

        onDestinationChanged();

        // attach to the next gate
        if (portalOpen && (outgoing != null)) {
            GateImpl gate = Gates.get(outgoing);
            if (gate != null)
                gate.attach(this);
            else {
                Utils.debug("closing if allowed");
                closeIfAllowed();
            }
        }
        getDestinationGate();
    }
    
    public boolean isLastLink() {
        if (outgoing == null)
            return links.isEmpty();
        return links.indexOf(outgoing) == (links.size() - 1);
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

    public GateImpl getDestinationGate() throws GateException {
        if (outgoing == null) {
            if (! isLinked())
                throw new GateException(getNoLinksFormat());
            else
                throw new GateException(getNoLinkSelectedFormat());
        } else if (! hasLink(outgoing))
            throw new GateException(getInvalidLinkFormat());
        GateImpl gate = Gates.get(outgoing);
        if (gate == null)
            throw new GateException(getUnknownLinkFormat());
        return gate;
    }

    @Override
    public boolean addPin(String pin) throws GateException {
        if (! Pins.isValidPin(pin))
            throw new GateException("invalid pin");
        if (pins.contains(pin)) return false;
        pins.add(pin);
        dirty = true;
        return true;
    }

    @Override
    public boolean removePin(String pin) {
        if (pins.contains(pin)) return false;
        pins.remove(pin);
        dirty = true;
        return true;
    }

    @Override
    public void removeAllPins() {
        pins.clear();
        dirty = true;
    }

    @Override
    public boolean hasPin(String pin) {
        return pins.contains(pin);
    }

    @Override
    public Set<String> getBannedItems() {
        return bannedItems;
    }
    
    @Override
    public boolean addBannedItem(String item) throws GateException {
        try {
            if (! Inventory.appendItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeBannedItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemList(bannedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllBannedItems() {
        bannedItems.clear();
        dirty = true;
    }

    @Override
    public Set<String> getAllowedItems() {
        return allowedItems;
    }
    
    @Override
    public boolean addAllowedItem(String item) throws GateException {
        try {
            if (! Inventory.appendItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeAllowedItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemList(allowedItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllAllowedItems() {
        allowedItems.clear();
        dirty = true;
    }

    @Override
    public Map<String,String> getReplaceItems() {
        return replaceItems;
    }
    
    @Override
    public boolean addReplaceItem(String fromItem, String toItem) throws GateException {
        try {
            if (! Inventory.appendItemMap(replaceItems, fromItem, toItem)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeReplaceItem(String item) throws GateException {
        try {
            if (! Inventory.removeItemMap(replaceItems, item)) return false;
        } catch (InventoryException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllReplaceItems() {
        replaceItems.clear();
        dirty = true;
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

    @Override
    public Set<String> getBannedPotions() {
        return bannedPotions;
    }
    
    @Override
    public boolean addBannedPotion(String potion) throws GateException {
        try {
            if (! PotionEffects.appendPotionList(bannedPotions, potion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeBannedPotion(String potion) throws GateException {
        try {
            if (! PotionEffects.removePotionList(bannedPotions, potion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllBannedPotions() {
        bannedPotions.clear();
        dirty = true;
    }

    @Override
    public Set<String> getAllowedPotions() {
        return allowedPotions;
    }
    
    @Override
    public boolean addAllowedPotion(String potion) throws GateException {
        try {
            if (! PotionEffects.appendPotionList(allowedPotions, potion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeAllowedPotion(String potion) throws GateException {
        try {
            if (! PotionEffects.removePotionList(allowedPotions, potion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllAllowedPotions() {
        allowedPotions.clear();
        dirty = true;
    }

    @Override
    public Map<String,String> getReplacePotions() {
        return replacePotions;
    }
    
    @Override
    public boolean addReplacePotion(String fromPotion, String toPotion) throws GateException {
        try {
            if (! PotionEffects.appendPotionMap(replacePotions, fromPotion, toPotion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean removeReplacePotion(String potion) throws GateException {
        try {
            if (! PotionEffects.removePotionMap(replacePotions, potion)) return false;
        } catch (PotionEffectException e) {
            throw new GateException(e.getMessage());
        }
        dirty = true;
        return true;
    }

    @Override
    public void removeAllReplacePotions() {
        replacePotions.clear();
        dirty = true;
    }
    
    public boolean isAcceptablePotions(PotionEffect[] effects) {
        if (effects == null) return true;
        if (! requireAllowedPotions) return true;
        for (int i = 0; i < effects.length; i++) {
            PotionEffect effect = effects[i];
            if (effect == null) continue;
            try {
                PotionEffects.filterPotionEffect(effect, replacePotions, allowedPotions, bannedPotions);
            } catch (PotionEffectException pee) {
                return false;
            }
        }
        return true;
    }
    
    public boolean filterPotions(PotionEffect[] effects) {
        if (effects == null) return false;
        boolean filtered = false;
        PotionEffect newEffect;
        for (int i = 0; i < effects.length; i++) {
            try {
                newEffect = PotionEffects.filterPotionEffect(effects[i], replacePotions, allowedPotions, bannedPotions);
            } catch (PotionEffectException pee) {
                newEffect = null;
            }
            if (newEffect != effects[i]) {
                effects[i] = newEffect;
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

    protected void generateFile() {
        File worldFolder = Worlds.worldPluginFolder(world);
        File gatesFolder = new File(worldFolder, "gates");
        String fileName = name.replaceAll("[^\\w-\\.]", "_");
        if (name.hashCode() > 0) fileName += "-";
        fileName += name.hashCode();
        fileName += ".yml";
        file = new File(gatesFolder, fileName);
    }

    private void closeIfAllowed() {
        if (! portalOpen) return;
        if (canClose()) close();
    }

    private boolean canClose() {
        if (duration < 1)
            return (! hasValidDestination()) && incoming.isEmpty();

        // temporary gate
        boolean expired = ((System.currentTimeMillis() - portalOpenTime) + 50) > duration;

        // handle mutually paired gates
        if ((outgoing != null) && hasValidDestination() && incoming.contains(outgoing) && (incoming.size() == 1)) return expired;

        if (incoming.isEmpty())
            return (outgoing == null) || expired;

        return false;
    }

}
