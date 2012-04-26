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
package org.bennedum.transporter.api;

import org.bukkit.Location;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public interface LocalAreaGate extends LocalGate {
    
    public void resize(int num, ExpandDirection dir);
    
    public String getP1();
    public void setP1(String s);
    public Location getP1Location();
    public void setP1Location(Location l);
    public String getP2();
    public void setP2(String s);
    public Location getP2Location();
    public void setP2Location(Location l);
    public SpawnDirection getSpawnDirection();
    public void setSpawnDirection(SpawnDirection dir);
    public void setSpawnAir(boolean b);
    public boolean getSpawnAir();
    public boolean getSpawnSolid();
    public void setSpawnSolid(boolean b);
    public boolean getSpawnLiquid();
    public void setSpawnLiquid(boolean b);
    public SpawnSearch getSpawnSearch();
    public void setSpawnSearch(SpawnSearch s);
    
}
