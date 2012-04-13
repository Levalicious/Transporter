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

import org.bennedum.transporter.api.LocalEndpoint;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class LocalEndpointImpl extends EndpointImpl implements LocalEndpoint {

    public abstract void addLink(Context ctx, String toEpName) throws TransporterException;
    public abstract void removeLink(Context ctx, String toEpName) throws TransporterException;
    public abstract void nextLink() throws TransporterException;
    
    public abstract void onRenameComplete();
    
    public abstract void onEndpointAdded(EndpointImpl ep);
    public abstract void onEndpointRemoved(EndpointImpl ep);
    public abstract void onEndpointDestroyed(EndpointImpl ep);
    public abstract void onEndpointRenamed(EndpointImpl ep, String oldFullName);
    public abstract void onSend(Entity entity);
    public abstract void onReceive(Entity entity);
    public abstract void onDestroy(boolean unbuild);

    public abstract boolean isSameWorld(World world);
    
    public abstract World getWorld();
    
    public abstract void save();
    
}
