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

import org.bennedum.transporter.api.Callback;
import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteWorld;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RemoteWorldImpl implements RemoteWorld {

    private Server server;
    private String name;
    
    public RemoteWorldImpl(Server server, String name) {
        this.server = server;
        if (name == null) throw new IllegalArgumentException("name is required");
        this.name = name;
    }
    
    @Override
    public RemoteServer getServer() {
        return server;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void getFullTime(Callback<Long> cb) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void getTime(Callback<Long> cb) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
