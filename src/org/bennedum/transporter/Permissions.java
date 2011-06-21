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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Permissions {

    private static final String OPS_FILE = "ops.txt";
    private static final String BANNEDIPS_FILE = "banned-ips.txt";
    private static final String BANNEDPLAYERS_FILE = "banned-players.txt";
    private static final String WHITELIST_FILE = "white-list.txt";
    private static final String SERVERPROPERTIES_FILE = "server.properties";
    private static final String PERMISSIONS_FILE = "permissions.properties";

    private static final File permissionsFile =
            new File(Global.plugin.getDataFolder(), PERMISSIONS_FILE);
    
    private static Map<String,ListFile> listFiles = new HashMap<String,ListFile>();
    private static Map<String,PropertiesFile> propertiesFiles = new HashMap<String,PropertiesFile>();
    
    public static boolean hasBasic(Player player, String perm) {
        return hasBasic(player.getName(), perm);
    }

    public static boolean hasBasic(String name, String perm) {
        Properties permissions = getProperties(permissionsFile);
        String[] parts = perm.split("\\.");
        String builtPerm = null;
        for (String part : parts) {
            if (builtPerm == null)
                builtPerm = part;
            else
                builtPerm = builtPerm + "." + part;
            String prop = permissions.getProperty(builtPerm);
            if (prop == null)
                prop = permissions.getProperty(builtPerm + ".*");
            if (prop == null)
                continue;
            String[] players = prop.split("\\s*,\\s*");
            for (String player : players)
                if (player.equals("*") || player.equals(name)) return true;
        }
        return false;
    }

    public static void requirePermissions(Player player, boolean requireAll, String ... perms) throws PermissionsException {
        requirePermissions(player.getWorld().getName(), player.getName(), requireAll, perms);
    }

    public static void requirePermissions(String worldName, String playerName, boolean requireAll, String ... perms) throws PermissionsException {
        if (isOp(playerName)) return;
        if (Utils.permissionsAvailable()) {
            for (String perm : perms) {
                if (requireAll) {
                    if (! Global.permissionsPlugin.permission(worldName, playerName, perm))
                        throw new PermissionsException("not permitted (Permissions)");
                } else {
                    if (Global.permissionsPlugin.permission(worldName, playerName, perm)) return;
                }
            }
            if (! requireAll)
                throw new PermissionsException("not permitted (Permissions)");
        } else {
            for (String perm : perms) {
                if (requireAll) {
                    if (! hasBasic(playerName, perm))
                        throw new PermissionsException("not permitted (basic)");
                } else {
                    if (hasBasic(playerName, perm)) return;
                }
            }
            if (! requireAll)
                throw new PermissionsException("not permitted (basic)");
        }
    }

    public static boolean isAllowedToConnect(String playerName, String ipAddress) {
        if (getList(new File(BANNEDIPS_FILE)).contains(ipAddress)) return false;
        if (getProperties(new File(SERVERPROPERTIES_FILE)).getProperty("white-list", "false").equalsIgnoreCase("true"))
            return getList(new File(WHITELIST_FILE)).contains(playerName);
        return ! getList(new File(BANNEDPLAYERS_FILE)).contains(playerName);
    }

    public static boolean isOp(String playerName) {
        return getList(new File(OPS_FILE)).contains(playerName);
    }
    
    private static Set<String> getList(File file) {
        ListFile listFile = listFiles.get(file.getAbsolutePath());
        if (listFile == null) {
            listFile = new ListFile();
            listFiles.put(file.getAbsolutePath(), listFile);
        }
        if ((listFile.data == null) || (listFile.lastRead < file.lastModified())) {
            listFile.data = new HashSet<String>();
            try {
                BufferedReader r = new BufferedReader(new FileReader(file));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    listFile.data.add(line);
                }
                r.close();
                listFile.lastRead = System.currentTimeMillis();
            } catch (IOException ioe) {
                Utils.warning("unable to read %s: %s", file.getAbsolutePath(), ioe.getMessage());
            }
        }
        return listFile.data;
    }

    private static Properties getProperties(File file) {
        PropertiesFile propsFile = propertiesFiles.get(file.getAbsolutePath());
        if (propsFile == null) {
            propsFile = new PropertiesFile();
            propertiesFiles.put(file.getAbsolutePath(), propsFile);
        }
        if ((propsFile.data == null) || (propsFile.lastRead < file.lastModified())) {
            propsFile.data = new Properties();
            try {
                propsFile.data.load(new FileInputStream(file));
                propsFile.lastRead = System.currentTimeMillis();
            } catch (IOException ioe) {
                Utils.warning("unable to read %s: %s", file.getAbsolutePath(), ioe.getMessage());
            }
        }
        return propsFile.data;
    }
    
    private static class ListFile {
        Set<String> data = null;
        long lastRead = 0;
    }
    
    private static class PropertiesFile {
        Properties data = null;
        long lastRead = 0;
    }
    
}