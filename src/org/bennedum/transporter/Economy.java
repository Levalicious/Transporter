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

//import com.iCo6.iConomy;
//import com.iCo6.system.Account;
//import com.iCo6.system.Holdings;
import com.nijikokun.register.payment.Methods;
import com.nijikokun.register.payment.Method;
import cosine.boseconomy.BOSEconomy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Economy {

//    private static iConomy iconomyPlugin = null;
    private static Method registerPlugin = null;
    private static BOSEconomy boseconomyPlugin = null;

    public static boolean isAvailable() {
        return //iconomyAvailable() ||
               boseconomyAvailable();
    }

    /*
    public static boolean iconomyAvailable() {
        if (! Config.getUseIConomy()) return false;
        return false;
        if (iconomyPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("iConomy");
        if ((p == null) || (! p.getClass().getName().equals("com.iConomy.iConomy")) || (! p.isEnabled())) return false;
        iconomyPlugin = (iConomy)p;
        return true;
    }
    */

    public static boolean registerAvailable() {
        if (! Config.getUseRegisterEconomy()) return false;
        if (registerPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("Register");
        if ((p == null) || (! p.isEnabled())) return false;
        registerPlugin = Methods.getMethod();
        return true;
    }

    public static boolean boseconomyAvailable() {
        if (! Config.getUseBOSEconomy()) return false;
        if (boseconomyPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("BOSEconomy");
        if ((p == null) || (! p.isEnabled())) return false;
        boseconomyPlugin = (BOSEconomy)p;
        return true;
    }

    public static String format(double funds) {
//        if (iconomyAvailable())
//            return iConomy.format(funds);
        if (registerAvailable())
            return registerPlugin.format(funds);
        if (boseconomyAvailable())
            return boseconomyPlugin.getMoneyFormatted(funds);

        // default
        return String.format("$%1.2f", funds);
    }

    public static boolean requireFunds(Player player, double amount) throws EconomyException {
        if (player == null) return false;
        return requireFunds(player.getName(), amount);
    }

    public static boolean requireFunds(String accountName, double amount) throws EconomyException {
        if (accountName == null) return false;
        if (amount <= 0) return false;
        /*
        if (iconomyAvailable()) {
            Account account = iConomy.getAccount(accountName);
            if (account == null)
                throw new EconomyException("iConomy account '%s' not found", accountName);
            Holdings holdings = account.getHoldings();
            if (holdings.balance() < amount)
                throw new EconomyException("insufficient funds");
            return true;
        }
         */

        if (registerAvailable()) {
            Method.MethodAccount account = registerPlugin.getAccount(accountName);
            if (! account.hasEnough(amount))
                throw new EconomyException("insufficient funds");
            return true;
        }
        if (boseconomyAvailable()) {
            double balance = boseconomyPlugin.getPlayerMoneyDouble(accountName);
            if (balance < amount)
                throw new EconomyException("insufficient funds");
            return true;
        }

        // default
        return false;
    }

    public static boolean deductFunds(Player player, double amount) throws EconomyException {
        if (player == null) return false;
        return deductFunds(player.getName(), amount);
    }

    public static boolean deductFunds(String accountName, double amount) throws EconomyException {
        if (accountName == null) return false;
        if (amount <= 0) return false;

        /*
        if (iconomyAvailable()) {
            Account account = iConomy.getAccount(accountName);
            if (account == null)
                throw new EconomyException("iConomy account '%s' not found", accountName);
            Holdings holdings = account.getHoldings();
            if (holdings.balance() < amount)
                throw new EconomyException("insufficient funds");
            holdings.subtract(amount);
            return true;
        }
         */
        if (registerAvailable()) {
            Method.MethodAccount account = registerPlugin.getAccount(accountName);
            if (! account.hasEnough(amount))
                throw new EconomyException("insufficient funds");
            return account.subtract(amount);
        }
        if (boseconomyAvailable()) {
            double balance = boseconomyPlugin.getPlayerMoneyDouble(accountName);
            if (balance < amount)
                throw new EconomyException("insufficient funds");
            return boseconomyPlugin.addPlayerMoney(accountName, -amount, false);
        }

        // default
        return false;
    }

}
