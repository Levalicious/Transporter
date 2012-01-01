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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.generator.NetherChunkGenerator;
import org.bukkit.craftbukkit.generator.NormalChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class WorldProxy implements OptionsListener {

    private static final Set<String> OPTIONS = new HashSet<String>();

    static {
        OPTIONS.add("autoLoad");
    }

    public static boolean isValidName(String name) {
        if (name.length() == 0) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    private Options options = new Options(this, OPTIONS, "trp.world", this);
    private String name;
    private Environment environment;
    private long seed;
    private boolean autoLoad;

    public WorldProxy(WorldCreator wc) throws WorldException {
        try {
            setName(name);
            environment = wc.environment();
            seed = wc.seed();
            autoLoad = true;
        } catch (IllegalArgumentException e) {
            throw new WorldException(e.getMessage());
        }
    }

    public WorldProxy(ConfigurationNode node) throws WorldException {
        try {
            setName(node.getString("name"));

            // try to convert old environment to new environment/generator
            String envStr = node.getString("environment", "NORMAL");
            if (envStr.equals("SKYLANDS")) envStr = "NORMAL";

            setEnvironment(envStr);

            String seedStr = node.getString("seed", "0");
            try {
                setSeed(Long.parseLong(seedStr));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("illegal seed value '" + seedStr + "'");
            }
            setAutoLoad(node.getBoolean("autoLoad", true));
        } catch (IllegalArgumentException e) {
            throw new WorldException(e.getMessage());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null)
            throw new IllegalArgumentException("name is required");
        if (! isValidName(name))
            throw new IllegalArgumentException("name is not valid");
        this.name = name;
    }

    public Environment getEnvironment() {
        return environment;
    }

    private void setEnvironment(String name) {
        if (name == null)
            throw new IllegalArgumentException("environment is required");
        try {
            environment = Utils.valueOf(Environment.class, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("environment is ambiguous or not valid");
        }
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /* Begin options */

    public boolean getAutoLoad() {
        return autoLoad;
    }

    public void setAutoLoad(boolean b) {
        autoLoad = b;
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
        ctx.sendLog("option '%s' set to '%s' for world '%s'", name, value, getName());
    }

    @Override
    public String getOptionPermission(Context ctx, String name) {
        return name;
    }

    /* End options */

    public Map<String,Object> encode() {
        Map<String,Object> node = new HashMap<String,Object>();
        node.put("name", name);
        node.put("environment", environment.toString());
        node.put("seed", seed);
        node.put("autoLoad", autoLoad);
        return node;
    }

    public World getWorld() {
        return Global.plugin.getServer().getWorld(name);
    }

    public World load(Context ctx) {
        World world = getWorld();
        boolean loadGates = (world != null);
        if (world == null) {
            ctx.send("loading world '%s'...", name);
            WorldCreator wc = new WorldCreator(name);
            wc.environment(environment);
            wc.seed(seed);
            wc.generator((String)null);
            world = wc.createWorld();
        } else
            ctx.send("world '%s' is already loaded", name);
        if (loadGates)
            Gates.loadGatesForWorld(ctx, world);
        return world;
    }

    public World unload() {
        World world = getWorld();
        if (world != null) {
            Global.plugin.getServer().unloadWorld(world, true);
            // TODO: remove when Bukkit sends onWorldUnloaded events
            Gates.remove(world);
        }
        return world;
    }

    public boolean isLoaded() {
        return getWorld() != null;
    }

}
