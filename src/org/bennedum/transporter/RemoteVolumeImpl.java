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

import org.bennedum.transporter.api.RemoteServer;
import org.bennedum.transporter.api.RemoteVolume;
import org.bennedum.transporter.api.RemoteWorld;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RemoteVolumeImpl extends RemoteEndpointImpl implements RemoteVolume {
    
    private Server server;
    private String worldName;

    public RemoteVolumeImpl(Server server, String name) {
        this.server = server;
        if (name == null) throw new IllegalArgumentException("name is required");
        String[] parts = name.split("\\.", 2);
        worldName = parts[0];
        this.name = parts[1];
    }
    
    @Override
    public String getLocalName() {
        return worldName + "." + getName();
    }
    
    @Override
    public String getFullName() {
        return server.getName() + "." + getLocalName();
    }
    
    @Override
    public String getGlobalName() {
        return getFullName();
    }
    
    @Override
    public String getName(Context ctx) {
        return getFullName();
    }
    
    @Override
    public RemoteWorld getRemoteWorld() {
        return server.getRemoteWorld(worldName);
    }

    @Override
    public RemoteServer getRemoteServer() {
        return server;
    }

    @Override
    public boolean isSameServer() {
        return false;
    }
    
    @Override
    protected void attach(EndpointImpl originEndpoint) {
        if (! (originEndpoint instanceof LocalEndpointImpl)) return;
        server.sendEndpointAttach(this, (LocalEndpointImpl)originEndpoint);
    }

    @Override
    protected void detach(EndpointImpl originEndpoint) {
        if (! (originEndpoint instanceof LocalEndpointImpl)) return;
        server.sendEndpointDetach(this, (LocalEndpointImpl)originEndpoint);
    }
    
    @Override
    public String toString() {
        return "RemoteVolume[" + getFullName() + "]";
    }
    
}
