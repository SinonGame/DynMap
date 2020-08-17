package club.guidelight.helper;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import javafx.event.EventType;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.query.QueryOptions;

public class NukkitHelper extends PluginBase {
    /*
    Luckperm
    */
    LuckPerms luckpermapi = LuckPermsProvider.get();

    public Player[] getOnlinePlayers(){
        Player[] players = this.getServer().getOnlinePlayers().values().toArray(new Player[] {});
        return players;
    }
    private static final int armorPoints[] = {3, 6, 8, 3};

    public static final int getArmorPoints(Player player) {
        int currentDurability = 0;
        int baseDurability = 0;
        int baseArmorPoints = 0;
        Item[] itm = new Item[4];
        PlayerInventory inv = player.getInventory();
        itm[0] = inv.getBoots();
        itm[1]= inv.getLeggings();
        itm[2] = inv.getChestplate();
        itm[3] = inv.getHelmet();
        for(int i = 0; i < 4; i++) {
            if(itm[i] == null) continue;
            int dur = itm[i].getDamage();
            int max = itm[i].getMaxDurability();
            if(max <= 0) continue;
            if(i == 2)
                max = max + 1;	/* Always 1 too low for chestplate */
            else
                max = max - 3;	/* Always 3 too high, versus how client calculates it */
            baseDurability += max;
            currentDurability += max - dur;
            baseArmorPoints += armorPoints[i];
        }
        int ap = 0;
        if(baseDurability > 0)
            ap = ((baseArmorPoints - 1) * currentDurability) / baseDurability + 1;
        return ap;
    }
    /*
    Haven't implement fadeInTicks etc
     */
    public void sendTitleText(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
        player.sendTitle(title, subtitle);
    }
}
