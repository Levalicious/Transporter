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

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class BlockListenerImpl implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        LocalGate gate = Gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
            gate.updateScreens();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        LocalGate gate = Gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
            gate.updateScreens();
            return;
        }
        gate = Gates.findGateForScreen(event.getBlock().getLocation());
        if (gate != null) {
            Context ctx = new Context(event.getPlayer());
            try {
                Permissions.require(ctx.getPlayer(), "trp.gate.destroy." + gate.getFullName());
                Gates.destroy(gate, false);
                ctx.sendLog("destroyed gate '%s'", gate.getName());
            } catch (PermissionsException pe) {
                ctx.warn(pe.getMessage());
                event.setCancelled(true);
                gate.updateScreens();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        LocalGate gate = Gates.findGateForScreen(block.getLocation());
        if (gate != null) return;
        Context ctx = new Context(event.getPlayer());
        String gateName = null;
        String link = null;
        for (String line : event.getLines()) {
            if ((line == null) || (line.trim().length() == 0)) continue;
            if (gateName == null)
                gateName = line;
            else if (link == null)
                link = line;
            else
                link += "." + line;
        }
        try {
            if (gateName == null) return;
            gate = Designs.create(ctx, block.getLocation(), gateName);
            if (gate == null) return;
            ctx.sendLog("created gate '%s'", gate.getName());
            Global.setSelectedGate(event.getPlayer(), gate);

            List<SavedBlock> undoBlocks = Global.getBuildUndo(event.getPlayer());
            if (undoBlocks != null) {
                for (SavedBlock undoBlock : undoBlocks) {
                    if (gate.isOccupyingLocation(undoBlock.getLocation())) {
                        Global.removeBuildUndo(event.getPlayer());
                        break;
                    }
                }
            }

            if (link == null) return;
            ctx.getPlayer().performCommand("trp gate link add \"" + link + "\"");
        } catch (TransporterException te) {
            ctx.warn(te.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockFromTo(BlockFromToEvent event) {
        // This prevents liquid portals from flowing out
        LocalGate gate = Gates.findGateForPortal(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        LocalGate gate = Gates.findGateForTrigger(event.getBlock().getLocation());
        if (gate != null) {
            if (gate.isClosed() && (event.getNewCurrent() > 0)) {
                if (gate.hasValidDestination()) {
                    try {
                        gate.open();
                        Utils.debug("gate '%s' opened via redstone", gate.getName());
                    } catch (GateException ge) {
                        Utils.warning(ge.getMessage());
                    }
                }
            } else if (gate.isOpen() && (event.getNewCurrent() <= 0)) {
                gate.close();
                Utils.debug("gate '%s' closed via redstone", gate.getName());
            }
            return;
        }
        
        gate = Gates.findGateForSwitch(event.getBlock().getLocation());
        if (gate != null) {
            if ((event.getNewCurrent() > 0) && (event.getOldCurrent() == 0)) {
                try {
                    gate.nextLink();
                } catch (GateException ge) {
                    Utils.warning(ge.getMessage());
                }
            }
        }
    }
    
}
