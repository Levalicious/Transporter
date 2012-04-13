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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bennedum.transporter.api.Endpoint;
import org.bukkit.World;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Endpoints {
    
    // Indexed by local name
    private static final Map<String,EndpointImpl> endpoints = new HashMap<String,EndpointImpl>();

    public static void load(Context ctx) {
        clearLocalEndpoints();
        for (World world : Global.plugin.getServer().getWorlds())
            loadEndpointsForWorld(ctx, world);
    }

    public static int loadEndpointsForWorld(Context ctx, World world) {
        int count = 0;
        count += loadGatesForWorld(ctx, world);
        count += loadVolumesForWorld(ctx, world);
        return count;
    }
    
    private static int loadGatesForWorld(Context ctx, World world) {
        File worldFolder = Worlds.worldPluginFolder(world);
        File gatesFolder = new File(worldFolder, "gates");
        if (! gatesFolder.exists()) {
            Utils.info("no gates found for world '%s'", world.getName());
            return 0;
        }
        int loadedCount = 0;
        for (File gateFile : Utils.listYAMLFiles(gatesFolder)) {
            try {
                LocalGateImpl gate = new LocalGateImpl(world, gateFile);
                try {
                    add(gate);
                    gate.initialize();
                    ctx.sendLog("loaded gate '%s' for world '%s'", gate.getName(), world.getName());
                    loadedCount++;
                } catch (EndpointException ee) {
                    ctx.warnLog("unable to load gate '%s' for world '%s': %s", gate.getName(), world.getName(), ee.getMessage());
                }
            } catch (TransporterException ge) {
                ctx.warnLog("'%s' contains an invalid gate file for world '%s': %s", gateFile.getPath(), world.getName(), ge.getMessage());
            } catch (Throwable t) {
                Utils.severe(t, "there was a problem loading the gate file '%s' for world '%s':", gateFile.getPath(), world.getName());
            }
        }
        return loadedCount;
    }

    private static int loadVolumesForWorld(Context ctx, World world) {
        File worldFolder = Worlds.worldPluginFolder(world);
        File volumesFolder = new File(worldFolder, "volumes");
        if (! volumesFolder.exists()) {
            Utils.info("no volumes found for world '%s'", world.getName());
            return 0;
        }
        int loadedCount = 0;
        for (File volumeFile : Utils.listYAMLFiles(volumesFolder)) {
            try {
                LocalVolumeImpl volume = new LocalVolumeImpl(world, volumeFile);
                try {
                    add(volume);
                    volume.initialize();
                    ctx.sendLog("loaded volume '%s' for world '%s'", volume.getName(), world.getName());
                    loadedCount++;
                } catch (EndpointException ee) {
                    ctx.warnLog("unable to load volume '%s' for world '%s': %s", volume.getName(), world.getName(), ee.getMessage());
                }
            } catch (TransporterException ge) {
                ctx.warnLog("'%s' contains an invalid volume file for world '%s': %s", volumeFile.getPath(), world.getName(), ge.getMessage());
            } catch (Throwable t) {
                Utils.severe(t, "there was a problem loading the volume file '%s' for world '%s':", volumeFile.getPath(), world.getName());
            }
        }
        return loadedCount;
    }
    
    public static void save(Context ctx) {
        Markers.update();
        if (endpoints.isEmpty()) return;
        for (LocalEndpointImpl ep : getLocalEndpoints()) {
            ep.save();
            ctx.sendLog("saved '%s'", ep.getLocalName());
        }
    }
    
    public static EndpointImpl find(Context ctx, String name) {
        int pos = name.indexOf('.');
        if (pos == -1) {
            // asking for a local endpoint in the player's current world
            if (! ctx.isPlayer()) return null;
            name = ctx.getPlayer().getWorld().getName() + "." + name;
        }
        return find(name);
    }
    
    public static EndpointImpl find(String name) {
        if (endpoints.containsKey(name)) return endpoints.get(name);
        String lname = name.toLowerCase();
        EndpointImpl ep = null;
        for (String key : endpoints.keySet()) {
            if (key.toLowerCase().startsWith(lname)) {
                if (ep == null) ep = endpoints.get(key);
                else return null;
            }
        }
        return ep;
    }

    public static EndpointImpl get(String name) {
        return endpoints.get(name);
    }
    
    public static void add(EndpointImpl ep) throws EndpointException {
        if (endpoints.containsKey(ep.getFullName()))
            throw new EndpointException("an endpoint with the same name already exists here");
        endpoints.put(ep.getFullName(), ep);
        for (LocalEndpointImpl lep : getLocalEndpoints())
            lep.onEndpointAdded(ep);
        if (ep instanceof LocalEndpointImpl) {
            if (ep instanceof LocalGateImpl)
                Gates.add((LocalGateImpl)ep);
            else if (ep instanceof LocalVolumeImpl)
                Volumes.add((LocalVolumeImpl)ep);
            for (Server server : Servers.getAll())
                server.sendEndpointAdded((LocalEndpointImpl)ep);
            Markers.update();
            World world = ((LocalEndpointImpl)ep).getWorld();
            if (Config.getAutoAddWorlds())
                try {
                    WorldProxy wp = Worlds.add(world);
                    if (wp != null)
                        Utils.info("automatically added world '%s' for new volume '%s'", wp.getName(), ep.getName());
                } catch (WorldException we) {}
            else if (Worlds.get(world.getName()) == null)
                Utils.warning("Volume '%s' has been added to world '%s' but the world has not been added to the plugin's list of worlds!", ep.getName(), world.getName());
        }            
    }
    
    public static void remove(EndpointImpl ep) throws EndpointException {
        if (! endpoints.containsKey(ep.getFullName()))
            throw new EndpointException("endpoint not found");
        endpoints.remove(ep.getFullName());
        for (LocalEndpointImpl lep : getLocalEndpoints())
            lep.onEndpointRemoved(ep);
        if (ep instanceof LocalEndpointImpl) {
            if (ep instanceof LocalGateImpl)
                Gates.remove((LocalGateImpl)ep);
            else if (ep instanceof LocalVolumeImpl)
                Volumes.remove((LocalVolumeImpl)ep);
            ((LocalEndpointImpl)ep).save();
            for (Server server : Servers.getAll())
                server.sendEndpointRemoved((LocalEndpointImpl)ep);
            Markers.update();
        }
    }
    
    public static void destroy(EndpointImpl ep, boolean unbuild) {
        endpoints.remove(ep.getFullName());
        for (LocalEndpointImpl lep : getLocalEndpoints())
            lep.onEndpointDestroyed(ep);
        if (ep instanceof LocalEndpointImpl) {
            ((LocalEndpointImpl)ep).onDestroy(unbuild);
            if (ep instanceof LocalGateImpl)
                try {
                    Gates.remove((LocalGateImpl)ep);
                } catch (EndpointException ee) {}
            else if (ep instanceof LocalVolumeImpl)
                try {
                    Volumes.remove((LocalVolumeImpl)ep);
                } catch (EndpointException ee) {}
            for (Server server : Servers.getAll())
                server.sendEndpointDestroyed((LocalEndpointImpl)ep);
            Markers.update();
        }
    }
    
    public static void rename(LocalEndpointImpl localEp, String newName) throws EndpointException {
        String oldName = localEp.getName();
        String oldFullName = localEp.getFullName();
        localEp.setName(newName);
        String newFullName = localEp.getFullName();
        if (endpoints.containsKey(newFullName)) {
            localEp.setName(oldName);
            throw new EndpointException("endpoint name already exists");
        }
        rename((EndpointImpl)localEp, oldFullName);
    }
    
    public static void rename(EndpointImpl ep, String oldFullName) {
        endpoints.remove(oldFullName);
        endpoints.put(ep.getFullName(), ep);
        for (LocalEndpointImpl lep : getLocalEndpoints())
            lep.onEndpointRenamed(ep, oldFullName);
        if (ep instanceof LocalEndpointImpl) {
            ((LocalEndpointImpl)ep).onRenameComplete();
            for (Server server : Servers.getAll())
                server.sendEndpointRenamed(oldFullName, ep.getFullName());
            Markers.update();
        }
    }
    
    public static void removeEndpointsForWorld(World world) {
        for (LocalEndpointImpl lep : getLocalEndpoints()) {
            if (lep.getWorld() == world)
                try {
                    remove(lep);
                } catch (EndpointException ee) {}
        }
    }

    public static void removeEndpointsForServer(Server server) {
        for (RemoteEndpointImpl rep : getRemoteEndpoints())
            if (rep.getRemoteServer() == server)
                try {
                    remove(rep);
                } catch (EndpointException ee) {}
    }

    public static LocalEndpointImpl getLocalEndpoint(String name) {
        EndpointImpl ep = endpoints.get(name);
        if ((ep == null) || (! (ep instanceof LocalEndpointImpl))) return null;
        return (LocalEndpointImpl)ep;
    }
    
    public static Set<LocalEndpointImpl> getLocalEndpoints() {
        Set<LocalEndpointImpl> eps = new HashSet<LocalEndpointImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof LocalEndpointImpl) eps.add((LocalEndpointImpl)ep);
        return eps;
    }
    
    public static Set<RemoteEndpointImpl> getRemoteEndpoints() {
        Set<RemoteEndpointImpl> eps = new HashSet<RemoteEndpointImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof RemoteEndpointImpl) eps.add((RemoteEndpointImpl)ep);
        return eps;
    }

    public static Set<LocalGateImpl> getLocalGates() {
        Set<LocalGateImpl> gates = new HashSet<LocalGateImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof LocalGateImpl) gates.add((LocalGateImpl)ep);
        return gates;
    }
    
    public static Set<RemoteGateImpl> getRemoteGates() {
        Set<RemoteGateImpl> gates = new HashSet<RemoteGateImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof RemoteGateImpl) gates.add((RemoteGateImpl)ep);
        return gates;
    }
    
    public static Set<LocalVolumeImpl> getLocalVolumes() {
        Set<LocalVolumeImpl> vols = new HashSet<LocalVolumeImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof LocalVolumeImpl) vols.add((LocalVolumeImpl)ep);
        return vols;
    }
    
    public static Set<RemoteVolumeImpl> getRemoteVolumes() {
        Set<RemoteVolumeImpl> vols = new HashSet<RemoteVolumeImpl>();
        for (Endpoint ep : endpoints.values())
            if (ep instanceof RemoteVolumeImpl) vols.add((RemoteVolumeImpl)ep);
        return vols;
    }
    
    private static void clearLocalEndpoints() {
        for (EndpointImpl ep : new HashSet<EndpointImpl>(endpoints.values()))
            if (ep instanceof LocalEndpointImpl)
                endpoints.remove(ep.getFullName());
    }
    
}
