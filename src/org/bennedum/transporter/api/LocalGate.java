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

import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public interface LocalGate extends Gate {
    
    public void save(boolean force);
    public void rebuild();

    public boolean addPin(String pin) throws GateException;
    public boolean removePin(String pin);
    public void removeAllPins();
    public boolean hasPin(String pin);

    public Set<String> getBannedItems();
    public boolean addBannedItem(String item) throws GateException;
    public boolean removeBannedItem(String item) throws GateException;
    public void removeAllBannedItems();
    public Set<String> getAllowedItems();
    public boolean addAllowedItem(String item) throws GateException;
    public boolean removeAllowedItem(String item) throws GateException;
    public void removeAllAllowedItems();
    public Map<String,String> getReplaceItems();
    public boolean addReplaceItem(String fromItem, String toItem) throws GateException;
    public boolean removeReplaceItem(String item) throws GateException;
    public void removeAllReplaceItems();

    public Set<String> getBannedPotions();
    public boolean addBannedPotion(String potion) throws GateException;
    public boolean removeBannedPotion(String potion) throws GateException;
    public void removeAllBannedPotions();
    public Set<String> getAllowedPotions();
    public boolean addAllowedPotion(String potion) throws GateException;
    public boolean removeAllowedPotion(String potion) throws GateException;
    public void removeAllAllowedPotions();
    public Map<String,String> getReplacePotions();
    public boolean addReplacePotion(String fromPotion, String toPotion) throws GateException;
    public boolean removeReplacePotion(String potion) throws GateException;
    public void removeAllReplacePotions();
    
    /* Options */
    
    public int getDuration();
    public void setDuration(int i);
    public boolean getLinkLocal();
    public void setLinkLocal(boolean b);
    public boolean getLinkWorld();
    public void setLinkWorld(boolean b);
    public boolean getLinkServer();
    public void setLinkServer(boolean b);
    public String getLinkNoneFormat();
    public void setLinkNoneFormat(String s);
    public String getLinkUnselectedFormat();
    public void setLinkUnselectedFormat(String s);
    public String getLinkOfflineFormat();
    public void setLinkOfflineFormat(String s);
    public String getLinkLocalFormat();
    public void setLinkLocalFormat(String s);
    public String getLinkWorldFormat();
    public void setLinkWorldFormat(String s);
    public String getLinkServerFormat();
    public void setLinkServerFormat(String s);
    public boolean getMultiLink();
    public void setMultiLink(boolean b);
    public boolean getProtect();
    public void setProtect(boolean b);
    public boolean getRequirePin();
    public void setRequirePin(boolean b);
    public boolean getRequireValidPin();
    public void setRequireValidPin(boolean b);
    public int getInvalidPinDamage();
    public void setInvalidPinDamage(int i);
    public boolean getSendChat();
    public void setSendChat(boolean b);
    public int getSendChatDistance();
    public void setSendChatDistance(int i);
    public boolean getReceiveChat();
    public void setReceiveChat(boolean b);
    public int getReceiveChatDistance();
    public void setReceiveChatDistance(int i);
    public boolean getRequireAllowedItems();
    public void setRequireAllowedItems(boolean b);
    public boolean getReceiveInventory();
    public void setReceiveInventory(boolean b);
    public boolean getDeleteInventory();
    public void setDeleteInventory(boolean b);
    public boolean getReceiveGameMode();
    public void setReceiveGameMode(boolean b);
    public String getAllowGameModes();
    public void setAllowGameModes(String s);
    public GameMode getGameMode();
    public void setGameMode(GameMode m);
    public boolean getReceiveXP();
    public void setReceiveXP(boolean b);
    public boolean getReceivePotions();
    public void setReceivePotions(boolean b);
    public boolean getRequireAllowedPotions();
    public void setRequireAllowedPotions(boolean b);
    public boolean getRandomNextLink();
    public void setRandomNextLink(boolean b);
    public boolean getSendNextLink();
    public void setSendNextLink(boolean b);
    public String getTeleportFormat();
    public void setTeleportFormat(String s);
    public String getNoLinksFormat();
    public void setNoLinksFormat(String s);
    public String getNoLinkSelectedFormat();
    public void setNoLinkSelectedFormat(String s);
    public String getInvalidLinkFormat();
    public void setInvalidLinkFormat(String s);
    public String getUnknownLinkFormat();
    public void setUnknownLinkFormat(String s);
    public String getMarkerFormat();
    public void setMarkerFormat(String s);
    public double getLinkLocalCost();
    public void setLinkLocalCost(double cost);
    public double getLinkWorldCost();
    public void setLinkWorldCost(double cost);
    public double getLinkServerCost();
    public void setLinkServerCost(double cost);
    public double getSendLocalCost();
    public void setSendLocalCost(double cost);
    public double getSendWorldCost();
    public void setSendWorldCost(double cost);
    public double getSendServerCost();
    public void setSendServerCost(double cost);
    public double getReceiveLocalCost();
    public void setReceiveLocalCost(double cost);
    public double getReceiveWorldCost();
    public void setReceiveWorldCost(double cost);
    public double getReceiveServerCost();
    public void setReceiveServerCost(double cost);
    
    /* End Options */
    
}
