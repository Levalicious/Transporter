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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bennedum.transporter.config.ConfigurationNode;
import org.bukkit.World;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Worlds {

    public static final File WorldBaseFolder = Utils.BukkitBaseFolder;
    private static final Map<String,WorldProxy> worlds = new HashMap<String,WorldProxy>();

    public static void onConfigLoad(Context ctx) {
        worlds.clear();
        List<ConfigurationNode> worldNodes = Config.getNodeList("worlds");
        if (worldNodes != null) {
            for (ConfigurationNode node : worldNodes) {
                try {
                    WorldProxy world = new WorldProxy(node);
                    add(world);
                    if (Global.started && Config.getAutoLoadWorlds() && world.getAutoLoad())
                        world.load(ctx);
                } catch (WorldException e) {
                    ctx.warn(e.getMessage());
                }
            }
        }

        // add default worlds if they don't exist
        for (String name : new String[] { "world", "world_nether", "world_the_end"} ) {
            World world = Global.plugin.getServer().getWorld(name);
            if (world == null) continue;
            try {
                add(world);
            } catch (WorldException e) {}
        }

        if (Global.started)
            autoLoad(ctx);
    }

    public static void onConfigSave() {
        List<Map<String,Object>> worldNodes = new ArrayList<Map<String,Object>>();
        for (WorldProxy world : worlds.values())
            worldNodes.add(world.encode());
        Config.setPropertyDirect("worlds", worldNodes);
    }

    public static void autoLoad(Context ctx) {
        if (! Config.getAutoLoadWorlds()) return;
        for (WorldProxy world : worlds.values())
            if (world.getAutoLoad())
                world.load(ctx);
    }

    public static WorldProxy add(World world) throws WorldException {
        if (worlds.containsKey(world.getName())) return null;
        WorldProxy wp = new WorldProxy(world.getName(), world.getEnvironment().toString(), null, world.getSeed() + "");
        add(wp);
        return wp;
    }

    public static void add(WorldProxy world) {
        worlds.put(world.getName(), world);
    }

    public static void remove(WorldProxy world) {
        worlds.remove(world.getName());
    }

    public static WorldProxy get(String name) {
        if (worlds.containsKey(name)) return worlds.get(name);
        WorldProxy world = null;
        name = name.toLowerCase();
        for (String key : worlds.keySet()) {
            if (key.toLowerCase().startsWith(name)) {
                if (world == null) world = worlds.get(key);
                else return null;
            }
        }
        return world;
    }

    public static List<WorldProxy> getAll() {
        return new ArrayList<WorldProxy>(worlds.values());
    }

    public static boolean isEmpty() {
        return size() == 0;
    }

    public static int size() {
        return worlds.size();
    }

    public static File worldFolder(String name) {
        return new File(WorldBaseFolder, name);
    }

    public static File worldFolder(World world) {
        return new File(WorldBaseFolder, world.getName());
    }

    public static File worldPluginFolder(World world) {
        return new File(worldFolder(world), Global.pluginName);
    }

}
