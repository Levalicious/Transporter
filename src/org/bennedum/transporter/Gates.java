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
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages a collection of both local and remote gates.
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Gates {

    // Gate build blocks that are protected
    private static final GateMap protectBlocks = new GateMap();

    // Gate screens for local gates
    private static final GateMap screenBlocks = new GateMap();

    // Gate switches for local gates
    private static final GateMap switchBlocks = new GateMap();

    // Gate triggers for local gates
    private static final GateMap triggerBlocks = new GateMap();

    // Portal blocks for open, local gates
    private static final GateMap portalBlocks = new GateMap();

    private static Map<Integer,LocalGateImpl> selectedGates = new HashMap<Integer,LocalGateImpl>();

    public static void add(LocalGateImpl gate) throws EndpointException {
        screenBlocks.putAll(gate.getScreenBlocks());
        triggerBlocks.putAll(gate.getTriggerBlocks());
        switchBlocks.putAll(gate.getSwitchBlocks());
    }

    public static void remove(LocalGateImpl gate) throws EndpointException {
        screenBlocks.removeGate(gate);
        switchBlocks.removeGate(gate);
        triggerBlocks.removeGate(gate);
        deselectGate(gate);
    }

    public static LocalGateImpl findGateForProtection(Location loc) {
        return protectBlocks.getGate(loc);
    }

    public static LocalGateImpl findGateForScreen(Location loc) {
        return screenBlocks.getGate(loc);
    }

    public static LocalGateImpl findGateForSwitch(Location loc) {
        return switchBlocks.getGate(loc);
    }

    public static LocalGateImpl findGateForTrigger(Location loc) {
        return triggerBlocks.getGate(loc);
    }

    public static LocalGateImpl findGateForPortal(Location loc) {
        return portalBlocks.getGate(loc);
    }

    public static void addPortalBlocks(GateMap blocks) {
        portalBlocks.putAll(blocks);
    }

    public static void removePortalBlocks(LocalGateImpl gate) {
        portalBlocks.removeGate(gate);
    }

    public static void addProtectBlocks(GateMap blocks) {
        protectBlocks.putAll(blocks);
    }

    public static void removeProtectBlocks(LocalGateImpl gate) {
        protectBlocks.removeGate(gate);
    }

    public static void setSelectedGate(Player player, LocalGateImpl gate) {
        selectedGates.put((player == null) ? Integer.MAX_VALUE : player.getEntityId(), gate);
    }

    public static LocalGateImpl getSelectedGate(Player player) {
        return selectedGates.get((player == null) ? Integer.MAX_VALUE : player.getEntityId());
    }

    public static void deselectGate(LocalGateImpl gate) {
        for (Integer playerId : new ArrayList<Integer>(selectedGates.keySet()))
            if (selectedGates.get(playerId) == gate)
                selectedGates.remove(playerId);
    }

}
