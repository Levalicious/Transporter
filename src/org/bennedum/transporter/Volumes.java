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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Volumes {
    
    private static Map<Integer,LocalVolumeImpl> selectedVolumes = new HashMap<Integer,LocalVolumeImpl>();

    public static void add(LocalVolumeImpl vol) throws EndpointException {
    }

    public static void remove(LocalVolumeImpl vol) throws EndpointException {
        deselectVolume(vol);
    }
    
    public static void setSelectedVolume(Player player, LocalVolumeImpl vol) {
        selectedVolumes.put((player == null) ? Integer.MAX_VALUE : player.getEntityId(), vol);
    }

    public static LocalVolumeImpl getSelectedVolume(Player player) {
        return selectedVolumes.get((player == null) ? Integer.MAX_VALUE : player.getEntityId());
    }

    public static void deselectVolume(LocalVolumeImpl vol) {
        for (Integer playerId : new ArrayList<Integer>(selectedVolumes.keySet()))
            if (selectedVolumes.get(playerId) == vol)
                selectedVolumes.remove(playerId);
    }
    
}
