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
package org.bennedum.transporter.command;

import java.util.ArrayList;
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bennedum.transporter.api.API;
import org.bennedum.transporter.api.Callback;
import org.bennedum.transporter.api.RemoteException;
import org.bennedum.transporter.api.RemotePlayer;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class DebugCommand extends TrpCommandProcessor {

    private static final String GROUP = "debug ";

    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase()) &&
               ctx.isConsole();
    }

    @Override
    public List<String> getUsage(Context ctx) {
        if (! ctx.isConsole()) return null;
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "submit <player>");
        cmds.add(getPrefix(ctx) + GROUP + "dump player [<player>]");
        cmds.add(getPrefix(ctx) + GROUP + "dump gate <name>");
        cmds.add(getPrefix(ctx) + GROUP + "dump design <name>");
        return cmds;
    }

    @Override
    public void process(final Context ctx, Command cmd, List<String> args)  throws TransporterException {
        args.remove(0);
        if (args.isEmpty())
            throw new CommandException("debug what?");
        String subCmd = args.remove(0).toLowerCase();

        if ("submit".startsWith(subCmd)) {
            String name;
            if (ctx.isPlayer())
                name = ctx.getPlayer().getName();
            else if (args.isEmpty())
                throw new CommandException("player name required");
            else
                name = args.remove(0);
            String message = null;
            if (! args.isEmpty()) {
                message = "";
                while (! args.isEmpty())
                    message = message + " " + args.remove(0);
            }
            ctx.send("requested submission of debug data");
            Utils.submitDebug(name, message);
            return;
        }

        if ("api".startsWith(subCmd)) {
            API api = Global.plugin.getAPI();
            for (RemoteServer server : api.getRemoteServers()) {
                final RemoteServer s = server;
                ctx.send("requesting version from %s", server.getName());
                server.getVersion(new Callback<String>() {
                    @Override
                    public void onSuccess(String version) {
                        ctx.send("%s version: %s", s.getName(), version);
                    }
                    @Override
                    public void onFailure(RemoteException re) {
                        ctx.send("%s failed: %s", s.getName(), re.getMessage());
                    }
                });
                for (RemoteWorld world : server.getRemoteWorlds()) {
                    ctx.send("requesting time from %s.%s", server.getName(), world.getName());
                    final RemoteWorld w = world;
                    world.getTime(new Callback<Long>() {
                        @Override
                        public void onSuccess(Long time) {
                            ctx.send("%s.%s time: %s", s.getName(), w.getName(), time);
                        }
                        @Override
                        public void onFailure(RemoteException re) {
                            ctx.send("%s.%s failed: %s", s.getName(), w.getName(), re.getMessage());
                        }
                    });
                }
                for (RemotePlayer player : server.getRemotePlayers()) {
                    ctx.send("sending message to %s.%s", server.getName(), player.getName());
                    final RemotePlayer p = player;
                    player.sendMessage(null, "hello there");
                    
                }
            }
            return;
        }
        
        /*
        if ("dump".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("dump what?");
            String what = args.remove(0).toLowerCase();

            if ("player".startsWith(what)) {
                Permissions.require(ctx.getPlayer(), "trp.debug.dump.player");
                Player p = ctx.getPlayer();
                if (! args.isEmpty()) {
                    String pname = args.remove(0);
                    p = Global.plugin.getServer().getPlayer(pname);
                    if (p == null)
                        throw new CommandException("unknown player '%s'", pname);
                }
                if (p == null)
                    throw new CommandException("player name required");
                ctx.send("location: %s", p.getLocation());
                ctx.send("velocity: %s", p.getVelocity());
                return;
            }

            if ("gate".startsWith(what)) {
                Permissions.require(ctx.getPlayer(), "trp.debug.dump.gate");
                if (args.isEmpty())
                    throw new CommandException("gate name required");
                String name = args.remove(1);
                GateBase gate = Gates.get(name);
                if (gate == null)
                    throw new CommandException("unknown or offline gate '%s'", name);
                if (gate.isSameServer())
                    ((LocalGateImpl)gate).dump(ctx);
                else
                    ctx.send("gate '%s' is a remote gate", gate.getFullName());
                return;
            }

            if ("design".startsWith(what)) {
                Permissions.require(ctx.getPlayer(), "trp.debug.dump.design");
                if (args.isEmpty())
                    throw new CommandException("design name required");
                String name = args.remove(1);
                Design design = Designs.get(name);
                if (design == null)
                    throw new CommandException("unknown design '%s'", name);
                design.dump(ctx);
                return;
            }
            throw new CommandException("dump what?");
        }
        */
        
        throw new CommandException("debug what?");
    }

}
