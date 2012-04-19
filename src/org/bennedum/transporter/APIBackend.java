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

import org.bennedum.transporter.api.RemoteException;
import org.bennedum.transporter.net.Message;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class APIBackend {
    
    public static void invoke(String target, String method, Message args, Message out) throws TransporterException {
        if (target.equals("server"))
            invokeServerMethod(method, args, out);
        else if (target.equals("world"))
            invokeWorldMethod(method, args, out);
        else if (target.equals("player"))
            invokePlayerMethod(method, args, out);
        
        else
            throw new RemoteException("unknown API target '%s'", target);
    }
    
    private static void invokeServerMethod(String method, Message args, Message out) throws TransporterException {
        if (method.equals("getVersion"))
            out.put("result", Global.plugin.getServer().getVersion());
        
        else
            throw new RemoteException("unknown server method '%s'", method);
    }
    
    private static void invokeWorldMethod(String method, Message args, Message out) throws TransporterException {
        String worldName = args.getString("world");
        if (worldName == null)
            throw new RemoteException("world is required");
        World world = Global.plugin.getServer().getWorld(worldName);
        if (world == null)
            throw new RemoteException("world '%s' is unknown", worldName);
        
        if (method.equals("getTime"))
            out.put("result", world.getTime());
        else if (method.equals("getFullTime"))
            out.put("result", world.getFullTime());
        
        else
            throw new RemoteException("unknown world method '%s'", method);
    }
    
    private static void invokePlayerMethod(String method, Message args, Message out) throws TransporterException {
        String playerName = args.getString("player");
        if (playerName == null)
            throw new RemoteException("player is required");
        Player player = Global.plugin.getServer().getPlayer(playerName);
        if (player == null)
            throw new ServerException("player '%s' is unknown", playerName);
        
        if (method.equals("getWorld"))
            out.put("result", player.getWorld().getName());
        else if (method.equals("getLocation")) {
            Message locMsg = new Message();
            Location loc = player.getLocation();
            locMsg.put("world", loc.getWorld().getName());
            locMsg.put("x", loc.getX());
            locMsg.put("y", loc.getY());
            locMsg.put("z", loc.getZ());
            out.put("result", locMsg);
        } else if (method.equals("sendMessage")) {
            player.sendMessage(args.getString("message"));
        } else if (method.equals("sendRawMessage")) {
            player.sendRawMessage(args.getString("message"));
        } else
            throw new ServerException("unknown player method '%s'", method);
    }
    
}
