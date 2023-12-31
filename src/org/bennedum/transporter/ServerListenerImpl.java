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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ServerListenerImpl implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        Context ctx = new Context(event.getSender());
        String cmd = event.getCommand();
        if (cmd.equalsIgnoreCase("save-all")) {
            Config.save(ctx);
            Gates.save(ctx);
        }
    }

}
