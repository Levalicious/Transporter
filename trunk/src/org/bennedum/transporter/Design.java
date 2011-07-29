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
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Design {

    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    private static String checkItem(String item) {
        return LocalGate.checkItem(item);
    }

    private String name;
    private int duration;
    private boolean buildable;
    private boolean buildFromInventory;
    private boolean linkLocal;
    private boolean linkWorld;
    private boolean linkServer;
    private boolean multiLink;
    private boolean restoreOnClose;
    private boolean requirePin;
    private boolean requireValidPin;
    private int invalidPinDamage;
    private boolean relayChat;
    private int relayChatDistance;
    private boolean requireAllowedItems;

    // iConomy
    private double buildCost;
    private double createCost;
    private double linkLocalCost;
    private double linkWorldCost;
    private double linkServerCost;
    private double sendLocalCost;
    private double sendWorldCost;
    private double sendServerCost;
    private double receiveLocalCost;
    private double receiveWorldCost;
    private double receiveServerCost;

    private Set<String> bannedItems = new HashSet<String>();
    private Set<String> allowedItems = new HashSet<String>();
    private Map<String,String> replaceItems = new HashMap<String,String>();

    private List<Pattern> buildWorlds = null;
    private List<DesignBlock> blocks = null;

    private int sizeX, sizeY, sizeZ;    // calculated

    @SuppressWarnings("unchecked")
    public Design(File file) throws DesignException, BlockException {
        if (! file.exists())
            throw new DesignException("%s not found", file.getAbsolutePath());
        if (! file.isFile())
            throw new DesignException("%s is not a file", file.getAbsolutePath());
        if (! file.canRead())
            throw new DesignException("unable to read %s", file.getAbsoluteFile());
        Configuration conf = new Configuration(file);
        conf.load();

        name = conf.getString("name");
        duration = conf.getInt("duration", -1);
        buildable = conf.getBoolean("buildable", true);
        buildFromInventory = conf.getBoolean("buildFromInventory", false);
        linkLocal = conf.getBoolean("linkLocal", true);
        linkWorld = conf.getBoolean("linkWorld", true);
        linkServer = conf.getBoolean("linkServer", true);
        multiLink = conf.getBoolean("multiLink", true);
        restoreOnClose = conf.getBoolean("restoreOnClose", false);
        requirePin = conf.getBoolean("requirePin", false);
        requireValidPin = conf.getBoolean("requireValidPin", true);
        invalidPinDamage = conf.getInt("invalidPinDamage", 0);
        relayChat = conf.getBoolean("relayChat", false);
        relayChatDistance = conf.getInt("relayChatDistance", 1000);
        requireAllowedItems = conf.getBoolean("requireAllowedItems", true);

        List<String> items = conf.getStringList("bannedItems", new ArrayList<String>());
        for (String item : items) {
            String i = checkItem(item);
            if (i == null)
                throw new DesignException("invalid banned item '%s'", item);
            bannedItems.add(i);
        }

        items = conf.getStringList("allowedItems", new ArrayList<String>());
        for (String item : items) {
            String i = checkItem(item);
            if (i == null)
                throw new DesignException("invalid allowed item '%s'", item);
            allowedItems.add(i);
        }

        items = conf.getKeys("replaceItems");
        if (items != null) {
            for (String oldItem : items) {
                String oi = checkItem(oldItem);
                if (oi == null)
                    throw new DesignException("invalid replace item '%s'", oldItem);
                String newItem = conf.getString("replaceItems." + oldItem);
                String ni = checkItem(newItem);
                if (ni == null)
                    throw new DesignException("invalid replace item '%s'", newItem);
                replaceItems.put(oi, ni);
            }
        }

        // iConomy
        buildCost = conf.getDouble("buildCost", 0);
        createCost = conf.getDouble("createCost", 0);
        linkLocalCost = conf.getDouble("linkLocalCost", 0);
        linkWorldCost = conf.getDouble("linkWorldCost", 0);
        linkServerCost = conf.getDouble("linkServerCost", 0);
        sendLocalCost = conf.getDouble("sendLocalCost", 0);
        sendWorldCost = conf.getDouble("sendWorldCost", 0);
        sendServerCost = conf.getDouble("sendServerCost", 0);
        receiveLocalCost = conf.getDouble("receiveLocalCost", 0);
        receiveWorldCost = conf.getDouble("receiveWorldCost", 0);
        receiveServerCost = conf.getDouble("receiveServerCost", 0);

        buildWorlds = new ArrayList<Pattern>();
        String pattern = conf.getString("buildWorlds");
        if (pattern != null)
            try {
                buildWorlds.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException pse) {
                throw new DesignException("invalid buildWorld pattern '%s': %s", pattern, pse.getMessage());
            }
        else {
            List<String> patternList = conf.getStringList("buildWorlds", null);
            if (patternList == null)
                buildWorlds.add(Pattern.compile(".*"));
            else {
                for (String pat : patternList) {
                    try {
                        buildWorlds.add(Pattern.compile(pat));
                    } catch (PatternSyntaxException pse) {
                        throw new DesignException("invalid buildWorld pattern '%s': %s", pat, pse.getMessage());
                    }
                }
            }
        }

        List<String> blockKeys = conf.getKeys("blockKey");
        if (blockKeys == null)
            throw new DesignException("blockKey mappings are required");
        Map<Character,DesignBlockDetail> blockKey = new HashMap<Character,DesignBlockDetail>();
        for (String key : blockKeys) {
            if (key.length() > 1)
                throw new DesignException("blockKey keys must be a single character: %s", key);
            DesignBlockDetail db;
            ConfigurationNode blockKeyNode = conf.getNode("blockKey." + key);
            if (blockKeyNode == null) {
                String blockType = conf.getString("blockKey." + key);
                if (blockType == null)
                    throw new DesignException("missing material for blockKey key '%s'", key);
                else
                    db = new DesignBlockDetail(blockType);
            } else
                db = new DesignBlockDetail(blockKeyNode);
            blockKey.put(key.charAt(0), db);
        }

        blocks = new ArrayList<DesignBlock>();
        sizeX = sizeY = sizeZ = -1;
        int x, y, z;
        List<Object> blocksNode = conf.getList("blocks");
        if (blocksNode == null)
            throw new DesignException("at least one block slice is required");
        z = sizeZ = blocksNode.size();
        for (Object o : blocksNode) {
            z--;
            if ((! (o instanceof List)) ||
                ((List)o).isEmpty() ||
                (! (((List)o).get(0) instanceof String)))
                throw new DesignException("block slice %d is not a list of strings", sizeZ - z);
            List<String> lines = (List<String>)o;
            if (sizeY == -1)
                sizeY = lines.size();
            else if (sizeY != lines.size())
                throw new DesignException("block slice %d does not have %d lines", sizeZ - z, sizeY);
            y = sizeY;
            for (String line : lines) {
                y--;
                line = line.trim();
                if (sizeX == -1)
                    sizeX = line.length();
                else if (sizeX != line.length())
                    throw new DesignException("block slice %d, line %d does not have %d blocks", sizeZ - z, sizeY - y, sizeX);
                x = sizeX;
                for (char ch : line.toCharArray()) {
                    x--;
                    if (! blockKey.containsKey(ch))
                        throw new DesignException("block slice %d, line %d, block %d '%s' does not have a mapping in the blockKey", sizeZ - z, sizeY - y, sizeX - x, ch);
                    DesignBlockDetail db = blockKey.get(ch);
                    if (db == null)
                        throw new DesignException("unknown block key '%s'", ch);
                    blocks.add(new DesignBlock(x, y, z, db));
                }
            }
        }

        if (name == null)
            throw new DesignException("name is required");
        if (! isValidName(name))
            throw new DesignException("name is not valid");

        if (sizeX > 255)
            throw new DesignException("must be less than 255 blocks wide");
        if (sizeY > 127)
            throw new DesignException("must be less than 127 blocks high");
        if (sizeZ > 255)
            throw new DesignException("must be less than 255 blocks deep");
        if ((sizeX * sizeY * sizeZ) < 4)
            throw new DesignException("volume of gate must be at least 4 cubic meters");

        int screenCount = 0,
            triggerCount = 0,
            switchCount = 0,
            spawnCount = 0,
            portalCount = 0,
            insertCount = 0;
        for (DesignBlock db : blocks) {
            DesignBlockDetail d = db.getDetail();
            if (d.isScreen()) screenCount++;
            if (d.isTrigger()) triggerCount++;
            if (d.isSwitch()) switchCount++;
            if (d.isPortal()) portalCount++;
            if (d.isInsert()) insertCount++;
            if (d.isSpawn()) spawnCount++;
        }

        if (screenCount == 0)
            throw new DesignException("must have at least one screen block");
        if (insertCount != 1)
            throw new DesignException("must have exactly one insert block");
        if (triggerCount == 0)
            throw new DesignException("must have at least one trigger block");
        if (portalCount == 0)
            throw new DesignException("must have at least one portal block");
        if (multiLink && (switchCount == 0))
            throw new DesignException("must have at least one switch block because multiLink is true");
        if (spawnCount == 0)
            throw new DesignException("must have at least one spawn block");
    }

    public void dump(Context ctx) {
        Utils.debug("Design:");
        Utils.debug("  name = " + name);
        Utils.debug("  duration = " + duration);
        Utils.debug("  buildable = " + buildable);

        String pats = "";
        for (Pattern p : buildWorlds) {
            if (pats.length() > 0) pats += ", ";
            pats += p.toString();
        }
        Utils.debug("  buildWorlds = " + pats);

        Utils.debug("  linkLocal = " + linkLocal);
        Utils.debug("  linkWorld = " + linkWorld);
        Utils.debug("  linkServer = " + linkServer);
        Utils.debug("  multiLink = " + multiLink);
        Utils.debug("  restoreOnClose = " + restoreOnClose);
        Utils.debug("  requirePin = " + requirePin);
        Utils.debug("  requireValidPin = " + requireValidPin);
        Utils.debug("  invalidPinDamage = " + invalidPinDamage);
        Utils.debug("  relayChat = " + relayChat);
        Utils.debug("  relayChatDistance = " + relayChatDistance);

        Utils.debug("  buildCost = " + buildCost);
        Utils.debug("  createCost = " + createCost);
        Utils.debug("  linkLocalCost = " + linkLocalCost);
        Utils.debug("  linkWorldCost = " + linkWorldCost);
        Utils.debug("  linkServerCost = " + linkServerCost);
        Utils.debug("  sendLocalCost = " + sendLocalCost);
        Utils.debug("  sendWorldCost = " + sendWorldCost);
        Utils.debug("  sendServerCost = " + sendServerCost);
        Utils.debug("  receiveLocalCost = " + receiveLocalCost);
        Utils.debug("  receiveWorldCost = " + receiveWorldCost);
        Utils.debug("  receiveServerCost = " + receiveServerCost);

        Utils.debug("  Blocks:");
        for (DesignBlock db : blocks) {
            Utils.debug("    %s", db);
        }
    }

    public String getName() {
        return name;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isAlwaysOpen() {
        return duration == -1;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public boolean mustBuildFromInventory() {
        return buildFromInventory;
    }

    public boolean getLinkLocal() {
        return linkLocal;
    }

    public boolean getLinkWorld() {
        return linkWorld;
    }

    public boolean getLinkServer() {
        return linkServer;
    }

    public boolean getMultiLink() {
        return multiLink;
    }

    public boolean getRestoreOnClose() {
        return restoreOnClose;
    }

    public boolean getRequirePin() {
        return requirePin;
    }

    public boolean getRequireValidPin() {
        return requireValidPin;
    }

    public int getInvalidPinDamage() {
        return invalidPinDamage;
    }

    public boolean getRelayChat() {
        return relayChat;
    }

    public int getRelayChatDistance() {
        return relayChatDistance;
    }

    public boolean getRequireAllowedItems() {
        return requireAllowedItems;
    }

    public Set<String> getBannedItems() {
        return bannedItems;
    }

    public Set<String> getAllowedItems() {
        return allowedItems;
    }

    public Map<String,String> getReplaceItems() {
        return replaceItems;
    }

    public double getBuildCost() {
        return buildCost;
    }

    public double getCreateCost() {
        return createCost;
    }

    public double getLinkLocalCost() {
        return linkLocalCost;
    }

    public double getLinkWorldCost() {
        return linkWorldCost;
    }

    public double getLinkServerCost() {
        return linkServerCost;
    }

    public double getSendLocalCost() {
        return sendLocalCost;
    }

    public double getSendWorldCost() {
        return sendWorldCost;
    }

    public double getSendServerCost() {
        return sendServerCost;
    }

    public double getReceiveLocalCost() {
        return receiveLocalCost;
    }

    public double getReceiveWorldCost() {
        return receiveWorldCost;
    }

    public double getReceiveServerCost() {
        return receiveServerCost;
    }

    private Collection<DesignBlock> getScreenBlocks() {
        Collection<DesignBlock> screens = new ArrayList<DesignBlock>();
        for (DesignBlock db : blocks)
            if (db.getDetail().isScreen())
                screens.add(db);
        return screens;
    }

    private DesignBlock getInsertBlock() {
        for (DesignBlock db : blocks)
            if (db.getDetail().isInsert()) return db;
        return null;
    }

    public boolean isBuildableInWorld(World world) {
        String worldName = world.getName();
        for (Pattern pattern : buildWorlds)
            if (pattern.matcher(worldName).matches()) return true;
        return false;
    }

    public Map<Material,Integer> getInventoryBlocks() {
        Map<Material,Integer> ib = new EnumMap<Material,Integer>(Material.class);
        for (DesignBlock db : blocks)
            if (db.getDetail().isInventory()) {
                Material m = db.getDetail().getBuildBlock().getMaterial();
                if (ib.containsKey(m))
                    ib.put(m, ib.get(m) + 1);
                else
                    ib.put(m, 1);
            }
        return ib;
    }

    public List<SavedBlock> build(Location location) throws DesignException {
        DesignBlock insertBlock = getInsertBlock();

        BlockFace direction;
        double yaw = location.getYaw();
        while (yaw < 0) yaw += 360;
        if ((yaw > 315) || (yaw <= 45)) direction = BlockFace.WEST;
        else if ((yaw > 45) && (yaw <= 135)) direction = BlockFace.NORTH;
        else if ((yaw > 135) && (yaw <= 225)) direction = BlockFace.EAST;
        else direction = BlockFace.SOUTH;

        // adjust location to represent 0,0,0 of design blocks
        switch (direction) {
            case NORTH:
                translate(location, insertBlock.getZ(), -insertBlock.getY(), -insertBlock.getX());
                break;
            case EAST:
                translate(location, insertBlock.getX(), -insertBlock.getY(), insertBlock.getZ());
                break;
            case SOUTH:
                translate(location, -insertBlock.getZ(), -insertBlock.getY(), insertBlock.getX());
                break;
            case WEST:
                translate(location, -insertBlock.getX(), -insertBlock.getY(), -insertBlock.getZ());
                break;
        }

        if ((location.getBlockY() + sizeY) > 127)
            throw new DesignException("insertion point is too high to build");
        if (location.getBlockY() < 0)
            throw new DesignException("insertion point is too low to build");

        List<GateBlock> gateBlocks = generateGateBlocks(location, direction);

        // check blocks that will be replaced (can't build in bedrock)
        for (GateBlock gb : gateBlocks) {
            if (! gb.getDetail().isBuildable()) continue;
            if (gb.getLocation().getBlock().getType() == Material.BEDROCK)
                throw new DesignException("unable to build in bedrock");
        }

        // build it!
        List<SavedBlock> savedBlocks = new ArrayList<SavedBlock>();
        for (GateBlock gb : gateBlocks) {
            if (! gb.getDetail().isBuildable()) continue;
            savedBlocks.add(new SavedBlock(gb.getLocation()));
            gb.getDetail().getBuildBlock().build(gb.getLocation());
        }
        return savedBlocks;
    }

    // Returns a new gate if a match is found, otherwise null.
    // The location must contain a sign block that matches one the design's screens.
    public LocalGate create(Location location, String gateName, String playerName) throws GateException {

        // must be in a buildable world
        World world = location.getWorld();
        String worldName = world.getName();
        boolean matched = false;
        for (Pattern pattern : buildWorlds)
            if (pattern.matcher(worldName).matches()) {
                matched = true;
                break;
            }
        if (! matched) return null;

        Block targetBlock = location.getBlock();
        location = null;
        BlockFace direction = null;
        List<GateBlock> gateBlocks = null;
        matched = false;

        // iterate over each screen trying to find a match with what's around the targetBlock
        for (DesignBlock screenBlock : getScreenBlocks()) {
            location = targetBlock.getLocation();
            direction = screenBlock.getDetail().getBuildBlock().matchTypeAndDirection(targetBlock);
            if (direction == null) continue;

            // adjust location to represent 0,0,0 of design blocks
            switch (direction) {
                case NORTH:
                    translate(location, screenBlock.getZ(), -screenBlock.getY(), -screenBlock.getX());
                    break;
                case EAST:
                    translate(location, screenBlock.getX(), -screenBlock.getY(), screenBlock.getZ());
                    break;
                case SOUTH:
                    translate(location, -screenBlock.getZ(), -screenBlock.getY(), screenBlock.getX());
                    break;
                case WEST:
                    translate(location, -screenBlock.getX(), -screenBlock.getY(), -screenBlock.getZ());
                    break;
                default:
                    continue;
            }

            gateBlocks = generateGateBlocks(location, direction);

            // check the target blocks to make sure they match the design
            matched = true;
            for (GateBlock gb : gateBlocks) {
                if (gb.getDetail().isMatchable() &&
                    (! gb.getDetail().getBuildBlock().matches(gb.getLocation()))) {
                    matched = false;
                    break;
                }
            }
            if (matched) break;
        }

        if (! matched) return null;

        // create the gate
        LocalGate gate = new LocalGate(world, gateName, playerName, this, gateBlocks, direction);
        return gate;
    }

    private List<GateBlock> generateGateBlocks(Location location, BlockFace direction) {
        List<GateBlock> gateBlocks = new ArrayList<GateBlock>();
        Map<DesignBlockDetail,DesignBlockDetail> cache = new HashMap<DesignBlockDetail,DesignBlockDetail>();
        for (DesignBlock db : blocks) {
            DesignBlockDetail detail;
            if (cache.containsKey(db.getDetail())) {
                detail = cache.get(db.getDetail());
            } else {
                detail = new DesignBlockDetail(db.getDetail(), direction);
                cache.put(db.getDetail(), detail);
            }
            gateBlocks.add(new GateBlock(detail, rotate(location, direction, db.getX(), db.getY(), db.getZ())));
        }
        return gateBlocks;
    }

    private Location translate(Location loc, int dx, int dy, int dz) {
        loc.setX(loc.getBlockX() + dx);
        loc.setY(loc.getBlockY() + dy);
        loc.setZ(loc.getBlockZ() + dz);
        return loc;
    }

    private Location rotate(Location loc, BlockFace facing, int offX, int offY, int offZ) {
        switch (facing) {
            case NORTH:
                return new Location(loc.getWorld(),
                        loc.getBlockX() - offZ,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() + offX);
            case EAST:
                return new Location(loc.getWorld(),
                        loc.getBlockX() - offX,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() - offZ);
            case SOUTH:
                return new Location(loc.getWorld(),
                        loc.getBlockX() + offZ,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() - offX);
            case WEST:
                return new Location(loc.getWorld(),
                        loc.getBlockX() + offX,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() + offZ);
        }
        return null;
    }

}