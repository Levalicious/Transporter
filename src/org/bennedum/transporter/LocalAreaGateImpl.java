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

import java.util.HashSet;
import java.util.Set;
import org.bennedum.transporter.GateMap.Point;
import org.bennedum.transporter.GateMap.Volume;
import org.bennedum.transporter.api.LocalAreaGate;
import org.bennedum.transporter.config.Configuration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class LocalAreaGateImpl extends LocalGateImpl implements LocalAreaGate {
    
    private static final Set<String> OPTIONS = new HashSet<String>(LocalGateImpl.BASEOPTIONS);
    
    static {
        
    }
    
    private Location p1;
    private Location p2;
    
    // creation from file
    public LocalAreaGateImpl(World world, Configuration conf) throws GateException {
        super(world, conf);
        options = new Options(this, OPTIONS, "trp.gate", this);
        
        try {
            p1 = parseLocation(conf, "p1");
        } catch (GateException ge) {
            throw new GateException("p1: %s", ge.getMessage());
        }
        try {
            p2 = parseLocation(conf, "p2");
        } catch (GateException ge) {
            throw new GateException("p2: %s", ge.getMessage());
        }
        
        calculateCenter();
        validate();
    }
    
    // creation in-game
    public LocalAreaGateImpl(World world, String gateName, String playerName, BlockFace direction, Location location) throws GateException {
        super(world, gateName, playerName, direction);
        options = new Options(this, OPTIONS, "trp.gate", this);
        
        p1 = new Location(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        p2 = new Location(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        
        calculateCenter();
        validate();
        generateFile();
        dirty = true;
    }
    
    // Abstract implementations
    
    @Override
    public GateType getType() { return GateType.AREA; }
    
    @Override
    public Location getSpawnLocation(Location fromLocation, BlockFace fromDirection) {
        // TODO: implement this
        throw new UnsupportedOperationException("not implemented yet");
        
        // have to add options for grounding, in air, in solids, etc
    }
    
    @Override
    public void onSend(Entity entity) {}

    @Override
    public void onReceive(Entity entity) {}
    
    @Override
    public void onProtect(Location loc) {}
    
    @Override
    protected void onValidate() throws GateException {}

    @Override
    protected void onAdd() {
        if (portalOpen)
            Gates.addPortalVolume(getPortalVolume());
    }
    
    @Override
    protected void onRemove() {
        Gates.removePortalVolume(this);
    }
    
    @Override
    protected void onDestroy(boolean unbuild) {
        Gates.removePortalVolume(this);
    }
    
    @Override
    protected void onOpen() {
        Gates.addPortalVolume(getPortalVolume());
    }
    
    @Override
    protected void onClose() {
        Gates.removePortalVolume(this);
    }
    
    @Override
    protected void onNameChanged() {}
    
    @Override
    protected void onDestinationChanged() {}
    
    @Override
    protected void onSave(Configuration conf) {
        conf.setProperty("p1", p1.getBlockX() + "," + p1.getBlockY() + "," + p1.getBlockZ());
        conf.setProperty("p2", p2.getBlockX() + "," + p2.getBlockY() + "," + p2.getBlockZ());
    }

    @Override
    protected void calculateCenter() {
        double cx = (p1.getBlockX() + p2.getBlockX()) / 2;
        double cy = (p1.getBlockY() + p2.getBlockY()) / 2;
        double cz = (p1.getBlockZ() + p2.getBlockZ()) / 2;
        center = new Vector(cx, cy, cz);
    }
    
    
    
    
    // Overrides
    
    @Override
    public String toString() {
        return "LocalAreaGate[" + getLocalName() + "]";
    }
    
    // Custom methods
    
    private Location parseLocation(Configuration conf, String name) throws GateException {
        String v = conf.getString(name);
        if (v == null)
            throw new GateException("missing");
        String[] ords = v.split(",");
        if (ords.length != 3)
            throw new GateException("invalid");
        try {
            int x = Integer.parseInt(ords[0]);
            int y = Integer.parseInt(ords[1]);
            int z = Integer.parseInt(ords[2]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException nfe) {
            throw new GateException("invalid");
        }
    }

    private Volume getPortalVolume() {
        Volume vol = new Volume(this);
        vol.setBounds(new Point(p1), new Point(p2));
        return vol;
    }
    
    /* Begin options */
    
    
    /* End options */
    
}
