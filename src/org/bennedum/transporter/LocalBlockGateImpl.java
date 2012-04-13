/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
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
import java.util.List;
import java.util.regex.Pattern;
import org.bennedum.transporter.api.LocalBlockGate;
import org.bennedum.transporter.config.Configuration;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class LocalBlockGateImpl extends LocalGateImpl implements LocalBlockGate {
    
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\\\n");
    
    private String designName;
    private boolean restoreOnClose;
    
    private List<GateBlock> blocks;
    private List<SavedBlock> savedBlocks = null;

    // creation from file
    public LocalBlockGateImpl(World world, Configuration conf) throws GateException {
        super(world, conf);
        designName = conf.getString("designName");
        restoreOnClose = conf.getBoolean("restoreOnClose", false);
        
        List<ConfigurationNode> nodes = conf.getNodeList("blocks");
        if (nodes == null)
            throw new GateException("missing blocks");
        blocks = new ArrayList<GateBlock>();
        for (ConfigurationNode node : nodes) {
            try {
                GateBlock block = new GateBlock(node);
                block.setWorld(world);
                blocks.add(block);
            } catch (BlockException be) {
                throw new GateException(be.getMessage());
            }
        }

        nodes = conf.getNodeList("saved", null);
        if (nodes != null) {
            savedBlocks = new ArrayList<SavedBlock>();
            for (ConfigurationNode node : nodes) {
                try {
                    SavedBlock block = new SavedBlock(node);
                    block.setWorld(world);
                    savedBlocks.add(block);
                } catch (BlockException be) {
                    throw new GateException(be.getMessage());
                }
            }
            if (savedBlocks.isEmpty()) savedBlocks = null;
        }

        calculateCenter();
        validate();
    }
    
    // creation from design
    public LocalBlockGateImpl(World world, String gateName, String playerName, BlockFace direction, Design design, List<GateBlock> blocks) throws GateException {
        super(world, gateName, playerName, direction);
        
        designName = design.getName();
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
    
    // Abstract implementations
    
    @Override
    public GateType getType() { return GateType.BLOCK; }
    
    @Override
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
    public void onProtect(Location loc) {
        updateScreens();
    }
    
    @Override
    protected void calculateCenter() {
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
    
    @Override
    protected void onOpen() {
        openPortal();
    }
    
    @Override
    protected void onClose() {
        closePortal();
    }
    
    @Override
    protected void onNameChanged() {
        updateScreens();
    }
    
    @Override
    protected void onDestinationChanged() {
        updateScreens();
    }
    
    @Override
    protected void onSave(Configuration conf) {
        conf.setProperty("designName", designName);
        conf.setProperty("restoreOnClose", restoreOnClose);

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
    }

    
    
    
    // Overrides
    
    @Override
    public String toString() {
        return "LocalBlockGate[" + getLocalName() + "]";
    }
    
    @Override
    public void initialize() {
        super.initialize();
        if (portalOpen)
            Gates.addPortalBlocks(getPortalBlocks());
        if (protect)
            Gates.addProtectBlocks(getBuildBlocks());
    }

    @Override
    protected void validate() throws GateException {
        super.validate();
        if (designName == null)
            throw new GateException("designName is required");
        if (! Design.isValidName(designName))
            throw new GateException("designName is not valid");
        if (blocks.isEmpty())
            throw new GateException("must have at least one block");
    }
    
    @Override
    public void onDestroy(boolean unbuild) {
        super.onDestroy(unbuild);
        if (unbuild) {
            for (GateBlock gb : blocks) {
                if (! gb.getDetail().isBuildable()) continue;
                Block b = gb.getLocation().getBlock();
                b.setTypeIdAndData(0, (byte)0, false);
            }
        }
    }
    
    // Custom methods
    
    public String getDesignName() {
        return designName;
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

    private void updateScreens() {
        GateMap screens = getScreenBlocks();
        if (screens.size() == 0) return;
        
        String format;
        GateImpl toGate = null;
        
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
                else if (! ((LocalGateImpl)toGate).isSameWorld(world))
                    format = getLinkWorldFormat();
                else
                    format = getLinkLocalFormat();
            }
        }
        List<String> lines = new ArrayList<String>();
        
        if ((format != null) && (! format.equals("-"))) {
            format = format.replace("%fromGate%", this.getName());
            format = format.replace("%fromWorld%", this.getWorld().getName());
            if (toGate != null) {
                format = format.replace("%toGate%", toGate.getName());
                if (toGate.isSameServer()) {
                    format = format.replace("%toWorld%", ((LocalGateImpl)toGate).getWorld().getName());
                    format = format.replace("%toServer%", "local");
                } else {
                    format = format.replace("%toWorld%", ((RemoteGateImpl)toGate).getRemoteWorld().getName());
                    format = format.replace("%toServer%", ((RemoteGateImpl)toGate).getRemoteServer().getName());
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

    
    
    
    
    
    
    /* Begin options */
    
    public boolean getRestoreOnClose() {
        return restoreOnClose;
    }

    public void setRestoreOnClose(boolean b) {
        restoreOnClose = b;
    }

    // TODO: add Options object handling
    
    /* End options */
 

}
