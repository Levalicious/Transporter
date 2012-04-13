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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Economy;
import org.bennedum.transporter.EndpointImpl;
import org.bennedum.transporter.Endpoints;
import org.bennedum.transporter.Gates;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.LocalEndpointImpl;
import org.bennedum.transporter.LocalGateImpl;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.RemoteEndpointImpl;
import org.bennedum.transporter.RemoteGateImpl;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GateCommand extends TrpCommandProcessor {

    private static final String GROUP = "gate ";

    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase());
    }

    @Override
    public List<String> getUsage(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "list");
        cmds.add(getPrefix(ctx) + GROUP + "select <gate>");
        cmds.add(getPrefix(ctx) + GROUP + "info [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "open [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "close [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "rebuild [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "destroy [<gate>] [unbuild]");
        cmds.add(getPrefix(ctx) + GROUP + "rename <newname> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "link add [<from>] <to> [rev]");
        cmds.add(getPrefix(ctx) + GROUP + "link remove [<from>] <to> [rev]");
        cmds.add(getPrefix(ctx) + GROUP + "link next [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "pin add <pin> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "pin remove <pin>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "ban add <item> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "ban remove <item>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "allow add <item> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "allow remove <item>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "replace add <old> <new> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "replace remove <olditem>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "get <option>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "set <option> <value> [<gate>]");
        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        args.remove(0);
        if (args.isEmpty())
            throw new CommandException("do what with a gate?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.gate.list");

            List<LocalGateImpl> localGates = new ArrayList<LocalGateImpl>(Endpoints.getLocalGates());
            if (localGates.isEmpty())
                ctx.send("there are no local gates");
            else {
                Collections.sort(localGates, new Comparator<LocalGateImpl>() {
                    @Override
                    public int compare(LocalGateImpl a, LocalGateImpl b) {
                        return a.getLocalName().compareToIgnoreCase(b.getLocalName());
                    }
                });
                ctx.send("%d local gates:", localGates.size());
                for (LocalGateImpl gate : localGates)
                    ctx.send("  %s", gate.getLocalName());
            }
            List<RemoteGateImpl> remoteGates = new ArrayList<RemoteGateImpl>(Endpoints.getRemoteGates());
            if (remoteGates.isEmpty())
                ctx.send("there are no remote gates");
            else {
                Collections.sort(remoteGates, new Comparator<RemoteGateImpl>() {
                    @Override
                    public int compare(RemoteGateImpl a, RemoteGateImpl b) {
                        return a.getFullName().compareToIgnoreCase(b.getFullName());
                    }
                });
                ctx.send("%d remote gates:", remoteGates.size());
                for (RemoteGateImpl gate : remoteGates)
                    ctx.send("  %s", gate.getFullName());
            }
            return;
        }

        if ("select".startsWith(subCmd)) {
            LocalGateImpl gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.select." + gate.getFullName());
            Gates.setSelectedGate(ctx.getPlayer(), gate);
            ctx.send("selected gate '%s'", gate.getFullName());
            return;
        }

        if ("info".startsWith(subCmd)) {
            LocalGateImpl gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.info." + gate.getFullName());
            ctx.send("Full name: %s", gate.getFullName());
            ctx.send("Design: %s", gate.getDesignName());
            ctx.send("Creator: %s", gate.getCreatorName());
            if (Economy.isAvailable()) {
                if (gate.getLinkLocal())
                    ctx.send("On-world travel cost: %s/%s",
                            Economy.format(gate.getSendLocalCost()),
                            Economy.format(gate.getReceiveLocalCost()));
                if (gate.getLinkWorld())
                    ctx.send("Off-world travel cost: %s/%s",
                            Economy.format(gate.getSendWorldCost()),
                            Economy.format(gate.getReceiveWorldCost()));
                if (gate.getLinkServer())
                    ctx.send("Off-server travel cost: %s/%s",
                            Economy.format(gate.getSendServerCost()),
                            Economy.format(gate.getReceiveServerCost()));
            }
            List<String> links = gate.getLinks();
            ctx.send("Links: %d", links.size());
            for (String link : links)
                ctx.send(" %s%s", link.equals(gate.getDestinationLink()) ? "*": "", link);
            return;
        }

        if ("open".startsWith(subCmd)) {
            LocalGateImpl gate = getGate(ctx, args);
            if (gate.isOpen())
                ctx.warn("gate '%s' is already open", gate.getName(ctx));
            else {
                Permissions.require(ctx.getPlayer(), "trp.gate.open." + gate.getFullName());
                gate.open();
                ctx.sendLog("opened gate '%s'", gate.getName(ctx));
            }
            return;
        }

        if ("close".startsWith(subCmd)) {
            LocalGateImpl gate = getGate(ctx, args);
            if (gate.isOpen()) {
                Permissions.require(ctx.getPlayer(), "trp.gate.close." + gate.getFullName());
                gate.close();
                ctx.sendLog("closed gate '%s'", gate.getName(ctx));
            } else
                ctx.warn("gate '%s' is already closed", gate.getName(ctx));
            return;
        }

        if ("rebuild".startsWith(subCmd)) {
            LocalGateImpl gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.rebuild." + gate.getFullName());
            gate.rebuild();
            ctx.sendLog("rebuilt gate '%s'", gate.getName(ctx));
            return;
        }

        if ("destroy".startsWith(subCmd)) {
            boolean unbuild = false;
            if ("unbuild".startsWith(args.get(args.size() - 1).toLowerCase())) {
                unbuild = true;
                args.remove(args.size() - 1);
            }
            LocalGateImpl gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.destroy." + gate.getFullName());
            Endpoints.destroy(gate, unbuild);
            ctx.sendLog("destroyed gate '%s'", gate.getName(ctx));
            return;
        }

        if ("rename".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("new name required");
            String newName = args.remove(0);
            LocalGateImpl gate = getGate(ctx, args);
            String oldName = gate.getName(ctx);
            Permissions.require(ctx.getPlayer(), "trp.gate.rename");
            Endpoints.rename(gate, newName);
            ctx.sendLog("renamed gate '%s' to '%s'", oldName, gate.getName(ctx));
            return;
        }

        if ("link".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a link?");
            subCmd = args.remove(0).toLowerCase();
            
            if ("next".startsWith(subCmd)) {
                LocalGateImpl fromGate = getGate(ctx, args);
                fromGate.nextLink();
                return;
            }
            
            if (args.isEmpty())
                throw new CommandException("destination endpoint required");
            boolean reverse = false;
            if ("reverse".startsWith(args.get(args.size() - 1).toLowerCase())) {
                reverse = true;
                args.remove(args.size() - 1);
            }
            if (args.isEmpty())
                throw new CommandException("destination endpoint required");

            String toEpName = args.remove(args.size() - 1);
            LocalGateImpl fromGate = getGate(ctx, args);
            
            EndpointImpl toEp = Endpoints.find(ctx, toEpName);

            if ("add".startsWith(subCmd)) {
                fromGate.addLink(ctx, toEpName);
                if (reverse && (ctx.getSender() != null) && (toEp != null)) {
                    if (toEp.isSameServer())
                        Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link add \"" + toEp.getFullName() + "\" \"" + fromGate.getFullName() + "\"");
                    else {
                        Server server = (Server)((RemoteEndpointImpl)toEp).getRemoteServer();
                        if (! server.isConnected())
                            ctx.send("unable to add reverse link from offline server");
                        else
                            server.sendLinkAdd(ctx.getPlayer(), (LocalEndpointImpl)fromGate, (RemoteEndpointImpl)toEp);
                    }
                }
                return;
            }

            if ("remove".startsWith(subCmd)) {
                fromGate.removeLink(ctx, toEpName);
                if (reverse && (ctx.getSender() != null) && (toEp != null)) {
                    if (toEp.isSameServer())
                        Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link remove \"" + fromGate.getFullName() + "\" \"" + toEp.getFullName() + "\"");
                    else {
                        Server server = (Server)((RemoteEndpointImpl)toEp).getRemoteServer();
                        if (! server.isConnected())
                            ctx.send("unable to remove reverse link from offline server");
                        else
                            server.sendLinkRemove(ctx.getPlayer(), (LocalEndpointImpl)fromGate, (RemoteEndpointImpl)toEp);
                    }
                }
                return;
            }
            throw new CommandException("do what with a link?");
        }

        if ("pin".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a pin?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("pin required");
            String pin = args.remove(0);
            LocalGateImpl gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.pin.add." + gate.getFullName());
                if (gate.addPin(pin))
                    ctx.send("added pin to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("pin is already added");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.pin.remove." + gate.getFullName());
                if (pin.equals("*")) {
                    gate.removeAllPins();
                    ctx.send("removed all pins from '%s'", gate.getName(ctx));
                } else if (gate.removePin(pin))
                    ctx.send("removed pin from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("pin not found");
                return;
            }
            throw new CommandException("do what with a pin?");
        }

        if ("ban".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a ban?");
            subCmd = args.remove(0).toLowerCase();
            
            LocalGateImpl gate;
            
            if ("list".startsWith(subCmd)) {
                gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.ban.list." + gate.getFullName());
                List<String> items = new ArrayList<String>(gate.getBannedItems());
                Collections.sort(items);
                ctx.send("%s items", items.size());
                for (String item : items)
                    ctx.send("  %s", item);
                return;
            }
            
            if (args.isEmpty())
                throw new CommandException("item required");
            String item = args.remove(0);
            gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.ban.add." + gate.getFullName());
                if (gate.addBannedItem(item))
                    ctx.send("added banned item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already banned");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.ban.remove." + gate.getFullName());
                if (item.equals("*")) {
                    gate.removeAllBannedItems();
                    ctx.send("removed all banned items from '%s'", gate.getName(ctx));
                } else if (gate.removeBannedItem(item))
                    ctx.send("removed banned item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("banned item not found");
                return;
            }
            throw new CommandException("do what with a ban?");
        }

        if ("allow".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with an allow?");
            subCmd = args.remove(0).toLowerCase();

            LocalGateImpl gate;
            
            if ("list".startsWith(subCmd)) {
                gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.allow.list." + gate.getFullName());
                List<String> items = new ArrayList<String>(gate.getAllowedItems());
                Collections.sort(items);
                ctx.send("%s items", items.size());
                for (String item : items)
                    ctx.send("  %s", item);
                return;
            }
            
            if (args.isEmpty())
                throw new CommandException("item required");
            String item = args.remove(0);
            gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.allow.add." + gate.getFullName());
                if (gate.addAllowedItem(item))
                    ctx.send("added allowed item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already allowed");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.allow.remove." + gate.getFullName());
                if (item.equals("*")) {
                    gate.removeAllAllowedItems();
                    ctx.send("removed all allowed items from '%s'", gate.getName(ctx));
                } else if (gate.removeAllowedItem(item))
                    ctx.send("removed allowed item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("allowed item not found");
                return;
            }
            throw new CommandException("do what with an allow?");
        }

        if ("replace".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a replace?");
            subCmd = args.remove(0).toLowerCase();
            
            LocalGateImpl gate;
            
            if ("list".startsWith(subCmd)) {
                gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.replace.list." + gate.getFullName());
                Map<String,String> items = new HashMap<String,String>(gate.getReplaceItems());
                List<String> keys = new ArrayList<String>(items.keySet());
                Collections.sort(keys);
                ctx.send("%s items", items.size());
                for (String key : keys)
                    ctx.send("  %s => %s", key, items.get(key));
                return;
            }
            
            if (args.isEmpty())
                throw new CommandException("item required");
            String oldItem = args.remove(0);

            if ("add".startsWith(subCmd)) {
                if (args.isEmpty())
                    throw new CommandException("new item required");
                String newItem = args.remove(0);
                gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.replace.add." + gate.getFullName());
                if (gate.addReplaceItem(oldItem, newItem))
                    ctx.send("added replace item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already replaced");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.replace.remove." + gate.getFullName());
                if (oldItem.equals("*")) {
                    gate.removeAllReplaceItems();
                    ctx.send("removed all replace items from '%s'", gate.getName(ctx));
                } else if ( gate.removeReplaceItem(oldItem))
                    ctx.send("removed replace item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("replace item not found");
                return;
            }
            throw new CommandException("do what with a replace?");
        }

        if ("set".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            if (args.isEmpty())
                throw new CommandException("option value required");
            String value = args.remove(0);
            LocalGateImpl gate = getGate(ctx, args);
            gate.setOption(ctx, option, value);
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            LocalGateImpl gate = getGate(ctx, args);
            gate.getOptions(ctx, option);
            return;
        }

        throw new CommandException("do what with a gate?");
    }

    private LocalGateImpl getGate(Context ctx, List<String> args) throws CommandException {
        EndpointImpl ep;
        if (! args.isEmpty()) {
            ep = Endpoints.find(ctx, args.get(0));
            if ((ep == null) || (! (ep instanceof LocalGateImpl)))
                throw new CommandException("unknown gate '%s'", args.get(0));
            args.remove(0);
        } else
            ep = Gates.getSelectedGate(ctx.getPlayer());
        if (ep == null)
            throw new CommandException("gate name required");
        if (! ep.isSameServer())
            throw new CommandException("this command cannot be used on a remote endpoint");
        return (LocalGateImpl)ep;
    }

}
