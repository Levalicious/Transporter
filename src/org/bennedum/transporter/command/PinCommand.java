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
import org.bennedum.transporter.Pins;
import org.bennedum.transporter.api.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class PinCommand extends TrpCommandProcessor {

    private static final String GROUP = "pin ";

    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase()) &&
               ctx.isPlayer();
    }

    @Override
    public List<String> getUsage(Context ctx) {
        if (! ctx.isPlayer()) return null;
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "<pin>");
        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        if (! ctx.isPlayer())
            throw new CommandException("this command can only be used by a player");
        args.remove(0);
        if (args.isEmpty())
            throw new CommandException("pin required");
        String pin = args.remove(0);
        Pins.add(ctx.getPlayer(), pin);
        ctx.send("gate pin set");
    }

}
