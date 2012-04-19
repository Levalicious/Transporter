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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.bennedum.transporter.OldGateMap.Entry;
import org.bukkit.Location;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class OldGateMap {

    private static final int CAPACITY = 100;

    private static int hashLocation(Location loc) {
        return Math.abs(loc.getBlockX() * loc.getBlockY() * loc.getBlockZ()) % CAPACITY;
    }

    private EntryList[] buckets = new EntryList[CAPACITY];

    public OldGateMap() {}

    public void put(LocalGateImpl gate, Location loc) {
        int hash = hashLocation(loc);
        if (buckets[hash] == null)
            buckets[hash] = new EntryList();
        buckets[hash].add(new Entry(gate, loc));
    }

    public void putAll(OldGateMap src) {
        for (int i = 0; i < CAPACITY; i++) {
            if (src.buckets[i] == null) continue;
            if (buckets[i] == null)
                buckets[i] = new EntryList(src.buckets[i]);
            else
                buckets[i].addAll(src.buckets[i]);
        }
    }

    public void remove(Location location) {
        List<Entry> entries = buckets[hashLocation(location)];
        if (entries == null) return;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.isLocation(location)) {
                entries.remove(entry);
                return;
            }
        }
    }
    
    public Entry get(Location location) {
        List<Entry> entries = buckets[hashLocation(location)];
        if (entries == null) return null;
        for (Entry entry : entries)
            if (entry.isLocation(location)) return entry;
        return null;
    }

    public Collection<Entry> values() {
        Collection<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < CAPACITY; i++) {
            if (buckets[i] == null) continue;
            entries.addAll(buckets[i]);
        }
        return entries;
    }

    public LocalGateImpl getGate(Location location) {
        Entry e = get(location);
        if (e == null) return null;
        return e.gate;
    }

    public void removeGate(LocalGateImpl gate) {
        for (int i = 0; i < CAPACITY; i++) {
            List<Entry> entries = buckets[i];
            if (entries == null) continue;
            Iterator<Entry> it = entries.iterator();
            while (it.hasNext())
                if (it.next().gate == gate)
                    it.remove();
        }
    }

    public boolean containsLocation(Location loc) {
        return get(loc) != null;
    }

    /*
    public GateBlock randomBlock() {
        int pos = (int)Math.floor(Math.random() * (double)size());
        for (int i = 0; i < CAPACITY; i++) {
            List<Entry> entries = buckets[i];
            if (entries == null) continue;
            if (pos < entries.size())
                return entries.get(pos).block;
            else
                pos -= entries.size();
        }
        return null;
    }
*/
    
    public int size() {
        int c = 0;
        for (int i = 0; i < CAPACITY; i++) {
            List<Entry> entries = buckets[i];
            if (entries == null) continue;
            c += entries.size();
        }
        return c;
    }

    /*
    public Set<GateBlock> getBlocks() {
        Set<GateBlock> blocks = new HashSet<GateBlock>();
        for (Entry e : values())
            blocks.add(e.block);
        return blocks;
    }
    */
    
    public static class Entry {
        public LocalGateImpl gate;
        public Location location;
        public Entry(LocalGateImpl gate, Location loc) {
            this.gate = gate;
            this.location = loc;
        }
        public boolean isLocation(Location loc) {
            Location l = location;
            return (l.getBlockX() == loc.getBlockX()) &&
                    (l.getBlockY() == loc.getBlockY()) &&
                    (l.getBlockZ() == loc.getBlockZ()) &&
                    (l.getWorld() == loc.getWorld());
        }
    }

    private static class EntryList extends ArrayList<Entry> {
        public EntryList() { super(); }
        public EntryList(EntryList list) { super(list); }
    }

}
