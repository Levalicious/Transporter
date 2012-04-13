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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.bennedum.transporter.api.LocalVolume;
import org.bennedum.transporter.config.Configuration;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class LocalVolumeImpl extends LocalEndpointImpl implements LocalVolume, OptionsListener {

    public static final Set<String> OPTIONS = new HashSet<String>();
    
    static {
        OPTIONS.add("receiveInventory");
        OPTIONS.add("receiveGameMode");
        OPTIONS.add("allowGameModes");
        OPTIONS.add("receiveXP");
        OPTIONS.add("teleportFormat");
        OPTIONS.add("markerFormat");
    }
    
    private File file;
    private World world;
    private Vector center;
    private Location p1;
    private Location p2;
    
    private String creatorName;
    private boolean receiveInventory;
    private boolean receiveGameMode;
    private String allowGameModes;
    private boolean receiveXP;
    private String teleportFormat;
    private String markerFormat;

    private Set<String> incoming = new HashSet<String>();

    private boolean dirty = false;
    private Options options = new Options(this, OPTIONS, "trp.volume", this);

    public LocalVolumeImpl(World world, String volumeName, String playerName, Location p1, Location p2) throws EndpointException {
        this.world = world;
        name = volumeName;
        creatorName = playerName;
        this.p1 = p1;
        this.p2 = p2;

        receiveInventory = true;
        receiveGameMode = false;
        allowGameModes = "*";
        receiveXP = false;
        teleportFormat = ChatColor.GOLD + "teleported to '%toNameCtx%'";
        markerFormat = "%name%";
        
        calculateCenter();
        validate();
        generateFile();
        dirty = true;
    }

    public LocalVolumeImpl(World world, File file) throws EndpointException {
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
        
        try {
            p1 = loadLocation(conf, "p1");
        } catch (EndpointException ee) {
            throw new EndpointException("p1: %s", ee.getMessage());
        }
        try {
            p2 = loadLocation(conf, "p2");
        } catch (EndpointException ee) {
            throw new EndpointException("p2: %s", ee.getMessage());
        }
        
        receiveInventory = conf.getBoolean("receiveInventory", true);
        receiveGameMode = conf.getBoolean("receiveGameMode", false);
        allowGameModes = conf.getString("allowGameModes", "*");
        receiveXP = conf.getBoolean("receiveXP", false);
        teleportFormat = conf.getString("teleportFormat", ChatColor.GOLD + "teleported to '%toNameCtx%'");
        markerFormat = conf.getString("markerFormat", "%name%");
        incoming.addAll(conf.getStringList("incoming", new ArrayList<String>()));
        
        calculateCenter();
        validate();
    }

    private Location loadLocation(Configuration conf, String name) throws EndpointException {
        String s = conf.getString(name);
        if (s == null) throw new EndpointException("required");
        String[] coords = s.split(",");
        if (coords.length != 3) throw new EndpointException("3 ordinates are required");
        Location l = new Location(world, 0, 0, 0);
        try {
            l.setX(Double.parseDouble(coords[0]));
            l.setY(Double.parseDouble(coords[1]));
            l.setZ(Double.parseDouble(coords[2]));
        } catch (NumberFormatException nfe) {
            throw new EndpointException("invalid ordinate");
        }
        return l;
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
        save();
    }

    @Override
    protected void detach(EndpointImpl originEndpoint) {
        String epName = originEndpoint.getFullName();
        if (! incoming.contains(epName)) return;
        incoming.remove(epName);
        dirty = true;
        save();
    }

    // LocalEndpointImpl overrides
    
    @Override
    public void addLink(Context ctx, String toEpName) throws TransporterException {}

    @Override
    public void removeLink(Context ctx, String toEpName) throws TransporterException {}
    
    @Override
    public void nextLink() throws TransporterException {}

    @Override
    public void onRenameComplete() {
        file.delete();
        generateFile();
        save();
    }
    
    @Override
    public void onEndpointAdded(EndpointImpl ep) {}

    @Override
    public void onEndpointRemoved(EndpointImpl ep) {}

    @Override
    public void onEndpointDestroyed(EndpointImpl ep) {
        if (ep == this) return;
        String epName = ep.getFullName();
        if (incoming.contains(epName)) {
            incoming.remove(epName);
            dirty = true;
        }
        save();
    }
    
    @Override
    public void onEndpointRenamed(EndpointImpl ep, String oldFullName) {
        if (ep == this) return;
        String newName = ep.getFullName();
        if (incoming.contains(oldFullName)) {
            incoming.remove(oldFullName);
            incoming.add(newName);
            dirty = true;
        }
        save();
    }
    
    @Override
    public void onSend(Entity entity) {}

    @Override
    public void onReceive(Entity entity) {}
    
    @Override
    public void onDestroy(boolean unbuild) {
        file.delete();
    }
    
    @Override
    public boolean isSameWorld(World world) {
        return this.world == world;
    }
    
    @Override
    public World getWorld() {
        return world;
    }
    
    public Vector getCenter() {
        return center;
    }
    
    @Override
    public void save() {
        if (! dirty) return;
        dirty = false;

        Configuration conf = new Configuration(file);
        conf.setProperty("name", name);
        conf.setProperty("creatorName", creatorName);
        conf.setProperty("p1", p1.getX() + "," + p1.getY() + "," + p1.getZ());
        conf.setProperty("p2", p2.getX() + "," + p2.getY() + "," + p2.getZ());
        
        conf.setProperty("receiveInventory", receiveInventory);
        conf.setProperty("receiveGameMode", receiveGameMode);
        conf.setProperty("allowGameModes", allowGameModes);
        conf.setProperty("receiveXP", receiveXP);
        conf.setProperty("teleportFormat", teleportFormat);
        conf.setProperty("markerFormat", markerFormat);

        if (! incoming.isEmpty()) conf.setProperty("incoming", new ArrayList<String>(incoming));

        File parent = file.getParentFile();
        if (! parent.exists())
            parent.mkdirs();
        conf.save();
    }

    // End interfaces and implementations
    
    
    // called when the volume is loaded from a file
    public void initialize() {}
    
    private void calculateCenter() {
        center = new Vector(
                (p1.getX() + p2.getX()) / 2,
                (p1.getY() + p2.getY()) / 2,
                (p1.getZ() + p2.getZ()) / 2
                );
    }

    private void validate() throws EndpointException {
        if (name == null)
            throw new EndpointException("name is required");
        if (! isValidName(name))
            throw new EndpointException("name is not valid");
        if (creatorName == null)
            throw new EndpointException("creatorName is required");
    }

    public String getCreatorName() {
        return creatorName;
    }

    @Override
    public String toString() {
        return "LocalVolume[" + getLocalName() + "]";
    }
    
    /* Begin options */
    
    public boolean getReceiveInventory() {
        return receiveInventory;
    }

    public void setReceiveInventory(boolean b) {
        receiveInventory = b;
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
        if (s == null) s = ChatColor.GOLD + "teleported to '%toNameCtx%'";
        teleportFormat = s;
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
        ctx.sendLog("option '%s' set to '%s' for volume '%s'", name, value, getName(ctx));
        save();
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name + "." + name;
    }

    /* End options */
    
    private void generateFile() {
        File worldFolder = Worlds.worldPluginFolder(world);
        File volumesFolder = new File(worldFolder, "volumes");
        String fileName = name.replaceAll("[^\\w-\\.]", "_");
        if (name.hashCode() > 0) fileName += "-";
        fileName += name.hashCode();
        fileName += ".yml";
        file = new File(volumesFolder, fileName);
    }

    
}
