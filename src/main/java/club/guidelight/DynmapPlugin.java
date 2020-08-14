package club.guidelight;

import club.guidelight.helper.NukkitHelper;
import cn.nukkit.OfflinePlayer;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.permission.Permission;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.level.biome.Biome;
import org.dynmap.*;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.utils.MapChunkCache;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class DynmapPlugin extends PluginBase implements DynmapCommonAPI {
    public DynmapCore core = new DynmapCore();
    private NukkitEnableCoreCallback enabCoreCB = new NukkitEnableCoreCallback();
    private String version;
    public NukkitHelper helper = new NukkitHelper();
    private Permission permissions;
    public static DynmapPlugin plugin;
    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
    /* Lookup cache */
    private Level last_world;
    private Level last_bworld;
    /*
    toLoc function
     */
    private static DynmapLocation toLoc(Location l) {
        return new DynmapLocation(DynmapWorld.normalizeWorldName(l.getLevel().getName()), l.getX(), l.getY(), l.getZ());
    }
    /*
    EnableCoreCallback event
     */
    private class NukkitEnableCoreCallback extends DynmapCore.EnableCoreCallbacks{
        @Override
        public void configurationLoaded() {
            File st = new File(core.getDataFolder(), "renderdata/spout-texture.txt");
            if(st.exists()) {
                st.delete();
            }
        }
    }

    /* Handler for generic console command sender */
    public class NukkitCommandSender implements DynmapCommandSender {
        private CommandSender sender;

        public NukkitCommandSender(CommandSender send) {
            sender = send;
        }
        
        @Override
        public boolean hasPrivilege(String privid) {
            if (sender != null)
                return sender.hasPermission(privid);
            return false;
        }

        @Override
        public void sendMessage(String msg) {
            if(sender != null)
                sender.sendMessage(msg);
        }

        @Override
        public boolean isConnected() {
            if(sender != null)
                return true;
            return false;
        }
        @Override
        public boolean isOp() {
            if(sender != null)
                return sender.isOp();
            else
                return false;
        }
        @Override
        public boolean hasPermissionNode(String node) {
            if (sender != null) {
                return sender.hasPermission(node);
            }
            return false;
        }
    }

    /*
    DynmapPlugin function
     */
    public DynmapPlugin() {
        plugin = this;
    }

    /**
     * Player access abstraction class
     */
    public class NukkitPlayer extends NukkitCommandSender implements DynmapPlayer {
        private Player player;
        private OfflinePlayer offplayer;
        private String skinurl;
        private UUID uuid;

        public NukkitPlayer(Player p) {
            super(p);
            player = p;
            //offplayer = p.getPlayer();
            uuid = p.getUniqueId();
            //skinurl = helper.getSkinURL(p);
        }
        public NukkitPlayer(OfflinePlayer p) {
            super(null);
            offplayer = p;
        }
        @Override
        public boolean isConnected() {
            return offplayer.isOnline();
        }
        @Override
        public String getName() {
            return offplayer.getName();
        }
        @Override
        public String getDisplayName() {
            if(player != null)
                return player.getDisplayName();
            else
                return offplayer.getName();
        }
        @Override
        public boolean isOnline() {
            return offplayer.isOnline();
        }
        @Override
        public DynmapLocation getLocation() {
            if(player == null) {
                return null;
            }
            Location loc = player.getLocation(); // Use eye location, since we show head
            return toLoc(loc);
        }
        @Override
        public String getWorld() {
            if(player == null) {
                return null;
            }
            Level w = player.getLevel();
            int levelid = w.getId();
            if(w != null)
                return DynmapPlugin.this.getServer().getLevel(levelid).getName();
            return null;
        }
        @Override
        public InetSocketAddress getAddress() {
            InetSocketAddress address = new InetSocketAddress(player.getAddress(),player.getPort());
            if(player != null)
                return address;
            return null;
        }
        @Override
        public boolean isSneaking() {
            if(player != null)
                return player.isSneaking();
            return false;
        }
        @Override
        public double getHealth() {
            if(player != null) {
                return Math.ceil(2.0 * player.getHealth() / player.getMaxHealth() * player.getHealth()) / 2.0;
            }
            else
                return 0;
        }
        @Override
        public int getArmorPoints() {
            if(player != null)
                return helper.getArmorPoints(player);
            else
                return 0;
        }
        @Override
        public DynmapLocation getBedSpawnLocation() {
            Location loc = offplayer.getPlayer().getSpawn().getLocation();
            if(loc != null) {
                return toLoc(loc);
            }
            return null;
        }
        @Override
        public long getLastLoginTime() {
            return offplayer.getLastPlayed();
        }
        @Override
        public long getFirstLoginTime() {
            return offplayer.getFirstPlayed();
        }
        @Override
        public boolean isInvisible() {
            if(player != null) {
                return player.hasEffect(14);
            }
            return false;
        }
        @Override
        public int getSortWeight() {
            Integer wt = sortWeights.get(getName());
            if (wt != null)
                return wt;
            return 0;
        }
        @Override
        public void setSortWeight(int wt) {
            if (wt == 0) {
                sortWeights.remove(getName());
            }
            else {
                sortWeights.put(getName(), wt);
            }
        }
        @Override
        public String getSkinURL() {
            return skinurl;
        }
        @Override
        public UUID getUUID() {
            return uuid;
        }
        /**
         * Send title and subtitle text (called from server thread)
         */
        @Override
        public void sendTitleText(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
            if (player != null) {
                helper.sendTitleText(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
            }
        }
    }
    /*
    The "BukkitServer" class in the original DynMap
     */
    public class NukkitServer extends DynmapServerInterface {
        /*
        The original getBlockIDAt
        Conflict with Nukkit, so rename it to getNukkitBlockIDAt
         */
        @Override
        public int getBlockIDAt(String wname, int x, int y, int z) {
            Level w = getServer().getLevelByName(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                return w.getBlockIdAt(x,  y,  z);
            }
            return -1;
        }
        @Override
        public int isSignAt(String wname, int x, int y, int z) {
            Level w = getServer().getLevelByName(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                Block b = w.getBlock(x, y, z);
                String s = b.getName();

                if (s == "sign") {
                    return 1;
                } else {
                    return 0;
                }
            }
            return -1;
        }
        /*
        The scheduleServerTask
        The original code for it is getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, run, delay);
        But I can't find the scheduleSyncDelayedTask
         */
        @Override
        public void scheduleServerTask(Runnable run, long delay) {
            getServer().getScheduler().scheduleDelayedTask(DynmapPlugin.this, run, (int) delay);
        }

        @Override
        public DynmapPlayer[] getOnlinePlayers() {
            Player[] players = helper.getOnlinePlayers();
            DynmapPlayer[] dplay = new DynmapPlayer[players.length];
            for(int i = 0; i < players.length; i++)
                dplay[i] = new NukkitPlayer(players[i]);
            return dplay;
        }

        public <T> Future<T> callSyncMethod(Callable<T> task) {
            return null;
        }

        public void reload() {

        }

        public DynmapPlayer getPlayer(String name) {
            return null;
        }

        public DynmapPlayer getOfflinePlayer(String name) {
            return null;
        }

        public Set<String> getIPBans() {
            return null;
        }

        public String getServerName() {
            return null;
        }

        public boolean isPlayerBanned(String pid) {
            return false;
        }

        public String stripChatColor(String s) {
            return null;
        }

        public boolean requestEventNotification(DynmapListenerManager.EventType type) {
            return false;
        }

        public boolean sendWebChatEvent(String source, String name, String msg) {
            return false;
        }

        public void broadcastMessage(String msg) {

        }

        public String[] getBiomeIDs() {
            return new String[0];
        }

        public double getCacheHitRate() {
            return 0;
        }

        public void resetCacheStats() {

        }

        public DynmapWorld getWorldByName(String wname) {
            return null;
        }

        public Set<String> checkPlayerPermissions(String player, Set<String> perms) {
            return null;
        }

        public boolean checkPlayerPermission(String player, String perm) {
            return false;
        }

        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks, boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
            return null;
        }

        public int getMaxPlayers() {
            return 0;
        }

        public int getCurrentPlayers() {
            return 0;
        }

        public double getServerTPS() {
            return 0;
        }

        public String getServerIP() {
            return null;
        }


    }
    public MarkerAPI getMarkerAPI() {
        return null;
    }

    public boolean markerAPIInitialized() {
        return false;
    }

    public boolean sendBroadcastToWeb(String s, String s1) {
        return false;
    }

    public int triggerRenderOfVolume(String s, int i, int i1, int i2, int i3, int i4, int i5) {
        return 0;
    }

    public int triggerRenderOfBlock(String s, int i, int i1, int i2) {
        return 0;
    }

    public void setPauseFullRadiusRenders(boolean b) {

    }

    public boolean getPauseFullRadiusRenders() {
        return false;
    }

    public void setPauseUpdateRenders(boolean b) {

    }

    public boolean getPauseUpdateRenders() {
        return false;
    }

    public void setPlayerVisiblity(String s, boolean b) {

    }

    public boolean getPlayerVisbility(String s) {
        return false;
    }

    public void assertPlayerInvisibility(String s, boolean b, String s1) {

    }

    public void assertPlayerVisibility(String s, boolean b, String s1) {

    }

    public void postPlayerMessageToWeb(String s, String s1, String s2) {

    }

    public void postPlayerJoinQuitToWeb(String s, String s1, boolean b) {

    }

    public String getDynmapCoreVersion() {
        return null;
    }

    public boolean setDisableChatToWebProcessing(boolean b) {
        return false;
    }

    public boolean testIfPlayerVisibleToPlayer(String s, String s1) {
        return false;
    }

    public boolean testIfPlayerInfoProtected() {
        return false;
    }

    public void processSignChange(String s, String s1, int i, int i1, int i2, String[] strings, String s2) {

    }

    @Override
    public void onEnable(){
        String mcver = "1.0.0";
        File dataDirectory = this.getDataFolder();
        if(dataDirectory.exists() == false)
            dataDirectory.mkdirs();
        core.setPluginJarFile(this.getFile());
        core.setPluginVersion(version, "Nukkit");
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        //core.setServer(new this.getServer());
        core.enableCore(enabCoreCB);
    }
}
