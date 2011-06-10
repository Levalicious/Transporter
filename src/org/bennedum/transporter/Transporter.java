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
import java.util.List;
import org.bennedum.transporter.command.CommandException;
import org.bennedum.transporter.command.CommandProcessor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;


// TODO: auto add gates to their own group on creation
// TODO: lightning blocks
// TODO: new gate design: Jail
// TODO: on arrival confirmation, delete player's inventory?
// TODO: add client patching

// TODO: wiki pages:
// Commands, ServerToServer, GateDesign, FAQ, HowToUseAGate, Permissions, GlobalConfiguration
// DirectoryStructure

// TODO: TEST: fix phantom connection issues
// TODO: TEST: remote teleport entities
// TODO: TEST: make sure new designs are installed
// TODO: TEST: add go command

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Transporter extends JavaPlugin {

    private ServerListenerImpl serverListener = new ServerListenerImpl();
    private BlockListenerImpl blockListener = new BlockListenerImpl();
    private PlayerListenerImpl playerListener = new PlayerListenerImpl();
    private VehicleListenerImpl vehicleListener = new VehicleListenerImpl();

    @Override
    public void onEnable() {
        PluginDescriptionFile pdf = getDescription();
        Global.plugin = this;
        Global.pluginName = pdf.getName();
        Global.pluginVersion = pdf.getVersion();

        Context ctx = new Context();

        // install/update resources

        File dataFolder = Global.plugin.getDataFolder();
        if (! dataFolder.exists()) {
            ctx.sendLog("creating data folder");
            dataFolder.mkdirs();
        }
        Utils.copyFileFromJar("/resources/LICENSE.txt", new File(dataFolder, "LICENSE.txt"), true);
        Utils.copyFileFromJar("/resources/README.txt", new File(dataFolder, "README.txt"), true);
        Utils.copyFileFromJar("/resources/materials.txt", new File(dataFolder, "materials.txt"), true);
        
        if (Utils.copyFileFromJar("/resources/config.yml", new File(dataFolder, "config.yml"), false))
            ctx.sendLog("installed default configuration");
        if (Utils.copyFileFromJar("/resources/permissions.properties", new File(dataFolder, "permissions.properties"), false))
            ctx.sendLog("installed default basic permissions");

        File designsFolder = new File(dataFolder, "designs");
        if (Utils.copyFilesFromJar("/resources/designs/manifest", designsFolder, false))
            ctx.sendLog("installed default designs");

        File overviewerFolder = new File(dataFolder, "overviewer");
        Utils.copyFilesFromJar("/resources/overviewer/manifest", overviewerFolder, true);

        Utils.loadConfig(ctx);

        Global.network.start(ctx);
        Global.designs.loadAll(ctx);
        Global.gates.loadAll(ctx);
        Global.servers.loadAll(ctx);

        PluginManager pm = getServer().getPluginManager();

        pm.registerEvent(Type.SERVER_COMMAND, serverListener, Priority.Monitor, this);  // save-all
        pm.registerEvent(Type.SIGN_CHANGE, blockListener, Priority.Monitor, this);  // create gate
        pm.registerEvent(Type.BLOCK_DAMAGE, blockListener, Priority.Normal, this);  // protection
        pm.registerEvent(Type.BLOCK_BREAK, blockListener, Priority.Normal, this);  // destroy gate, protection
        pm.registerEvent(Type.PLAYER_INTERACT, playerListener, Priority.Normal, this); // open gate/change link
        pm.registerEvent(Type.PLAYER_MOVE, playerListener, Priority.Normal, this); // outgoing player teleport
        pm.registerEvent(Type.PLAYER_JOIN, playerListener, Priority.Normal, this); // incoming player teleport
        pm.registerEvent(Type.PLAYER_CHAT, playerListener, Priority.Monitor, this); // server-server chat
        pm.registerEvent(Type.VEHICLE_MOVE, vehicleListener, Priority.Monitor, this); // outgoing vehicle teleport

        ctx.sendLog("this is v%s", pdf.getVersion());

    }

    @Override
    public void onDisable() {
        Context ctx = new Context();
        Global.network.stop(ctx);
        Utils.saveConfig(ctx);
        Global.gates.saveAll(ctx);
        ctx.sendLog("disabled");
        Global.plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] rawArgs) {
        // Rebuild quoted arguments
        List<String> args = new ArrayList<String>();
        boolean inQuotes = false;
        StringBuilder argBuffer = null;
        for (String arg : rawArgs) {
            if (inQuotes) {
                argBuffer.append(" ");
                argBuffer.append(arg);
                if (arg.endsWith("\"")) {
                    argBuffer.deleteCharAt(argBuffer.length() - 1);
                    inQuotes = false;
                    args.add(argBuffer.toString());
                    argBuffer = null;
                }
            } else if (arg.startsWith("\"")) {
                argBuffer = new StringBuilder(arg);
                argBuffer.deleteCharAt(0);
                if ((arg.length() > 1) && arg.endsWith("\"")) {
                    argBuffer.deleteCharAt(argBuffer.length() - 1);
                    args.add(argBuffer.toString());
                    argBuffer = null;
                } else
                    inQuotes = true;
            } else
                args.add(arg);
        }
        if (argBuffer != null)
            args.add(argBuffer.toString());

        Context ctx = new Context(sender);
        if (args.isEmpty()) {
            ctx.send("this is v%s", Global.pluginVersion);
            return true;
        }

        // Find the matching commands
        List<CommandProcessor> cps = new ArrayList<CommandProcessor>();
        for (CommandProcessor cp : Global.commands) {
            if (! cp.matches(cmd, args)) continue;
            if (cp.requiresPlayer() && (! ctx.isPlayer())) continue;
            if (cp.requiresOp() && (! ctx.isOp())) continue;
            if (cp.requiresConsole() && (! ctx.isConsole())) continue;
            cps.add(cp);
        }
        // Execute the matching command
        try {
            if (cps.isEmpty())
                throw new CommandException("huh? try %strp help", (ctx.isPlayer() ? "/" : ""));
            if (cps.size() > 1)
                throw new CommandException("ambiguous command; try %strp help", (ctx.isPlayer() ? "/" : ""));
            cps.get(0).process(ctx, cmd, args);
            return true;
        } catch (TransporterException te) {
            ctx.warn(te.getMessage());
            return true;
        }
    }

}