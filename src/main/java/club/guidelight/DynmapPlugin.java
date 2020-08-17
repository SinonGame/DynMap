package club.guidelight;

import club.guidelight.helper.NukkitLevel;
import club.guidelight.helper.NukkitVersionHelper;
import club.guidelight.helper.SnapshotCache;
import cn.nukkit.OfflinePlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.SignChangeEvent;
import cn.nukkit.event.level.SpawnChangeEvent;
import cn.nukkit.event.player.PlayerBedLeaveEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.permission.Permission;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.AsyncTask;
import org.dynmap.*;
import org.dynmap.common.*;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.nukkit.permissions.LuckPerms5Permissions;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DynmapPlugin extends PluginBase implements DynmapCommonAPI {
    public DynmapCore core = new DynmapCore();
    private NukkitEnableCoreCallback enabCoreCB = new NukkitEnableCoreCallback();
    public PluginManager pm;
    private HashMap<String, NukkitLevel> world_by_name = new HashMap<String, NukkitLevel>();
    String version;
    public static DynmapPlugin plugin;
    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
    public Permission permissions;
    /* Lookup cache */
    private Level last_world;
    private NukkitLevel last_bworld;
    // TPS calculator
    private double tps;
    private long lasttick;
    private long perTickLimit;
    private long cur_tick_starttime;
    private long avgticklen = 50000000;
    private int chunks_in_cur_tick = 0;
    private long cur_tick;
    private long prev_tick;
    /*
    Luckperm
    */
    public LuckPerms5Permissions luckperm;


    private NukkitVersionHelper helper;

    private final NukkitLevel getWorldByName(String name) {
        if((last_world != null) && (last_world.getName().equals(name))) {
            return last_bworld;
        }
        return world_by_name.get(name);
    }
    private final NukkitLevel getWorld(Level w) {
        if(last_world == w) {
            return last_bworld;
        }
        NukkitLevel bw = world_by_name.get(w.getName());
        if(bw == null) {
            bw = new NukkitLevel(w);
            world_by_name.put(w.getName(), bw);
        }
        else if(bw.isLoaded() == false) {
            bw.setWorldLoaded(w);
        }
        last_world = w;
        last_bworld = bw;

        return bw;
    }
    final void removeWorld(Level w) {
        world_by_name.remove(w.getName());
        if(w == last_world) {
            last_world = null;
            last_bworld = null;
        }
    }
    /*
    EnableCoreCallback event
     */
    private class NukkitEnableCoreCallback extends DynmapCore.EnableCoreCallbacks {
        @Override
        public void configurationLoaded() {
            File st = new File(core.getDataFolder(), "renderdata/spout-texture.txt");
            if(st.exists()) {
                st.delete();
            }
        }
    }
    private static class BlockToCheck {
        Location loc;
        int typeid;
        byte data;
        String trigger;
    };
    private LinkedList<BlockToCheck> blocks_to_check = null;
    private LinkedList<BlockToCheck> blocks_to_check_accum = new LinkedList<BlockToCheck>();
    /*
    DynmapPlugin function
    */
    public DynmapPlugin() {
        plugin = this;
    }
    /**
     * Server access abstraction class
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

        @Override
        public void reload() {
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.disablePlugin(DynmapPlugin.this);
            pluginManager.enablePlugin(DynmapPlugin.this);
        }

        @Override
        public DynmapPlayer getPlayer(String name) {
            Player p = getServer().getPlayerExact(name);
            if (p != null) {
                return new NukkitPlayer(p);
            }
            return null;
        }

        @Override
        public Set<String> getIPBans() {
            return (Set<String>) getServer().getIPBans();
        }
        /*
        Don't know whether this will work.....
         */
        @Override
        public <T> Future<T> callSyncMethod(Callable<T> task) {
            if (DynmapPlugin.this.isEnabled())
                return (Future<T>) getServer().getScheduler().scheduleAsyncTask(DynmapPlugin.this, (AsyncTask) task);
            else
                return null;
        }

        private boolean noservername = false;

        @Override
        public String getServerName() {
            return getServer().getMotd();
        }

        @Override
        public boolean isPlayerBanned(String pid) {
            OfflinePlayer p = (OfflinePlayer) getServer().getOfflinePlayer(pid);
            if ((p != null) && p.isBanned())
                return true;
            return false;
        }

        @Override
        public boolean isServerThread() {
            return Server.getInstance().isPrimaryThread();
        }
        /*
        No idea how to work around this, leave it here for now
         */
        @Override
        public String stripChatColor(String s) {
            //return ChatColor.stripColor(s);
            return s;
        }

        private Set<DynmapListenerManager.EventType> registered = new HashSet<DynmapListenerManager.EventType>();

        @Override
        public boolean requestEventNotification(DynmapListenerManager.EventType type) {
            if (registered.contains(type))
                return true;
            switch (type) {
                case WORLD_LOAD:
                case WORLD_UNLOAD:
                    /* Already called for normal world activation/deactivation */
                    break;
                case WORLD_SPAWN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                        public void onSpawnChange(SpawnChangeEvent evt) {
                            NukkitLevel w = getWorld(evt.getLevel());
                            core.listenerManager.processWorldEvent(DynmapListenerManager.EventType.WORLD_SPAWN_CHANGE, w);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                    /* Already handled */
                    break;
                case PLAYER_BED_LEAVE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new NukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(DynmapListenerManager.EventType.PLAYER_BED_LEAVE, p);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_CHAT:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                        public void onPlayerChat(PlayerChatEvent evt) {
                            final Player p = evt.getPlayer();
                            final String msg = evt.getMessage();
                            getServer().getScheduler().scheduleTask(DynmapPlugin.this, new Runnable() {
                                public void run() {
                                    DynmapPlayer dp = null;
                                    if (p != null)
                                        dp = new NukkitPlayer(p);
                                    core.listenerManager.processChatEvent(DynmapListenerManager.EventType.PLAYER_CHAT, dp, msg);
                                }
                            });
                        }
                    }, DynmapPlugin.this);
                    break;
                case BLOCK_BREAK:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            Block b = evt.getBlock();
                            if (b == null) return;   /* Work around for stupid mods.... */
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(DynmapListenerManager.EventType.BLOCK_BREAK, b.getName(),
                                    getWorld(l.getLevel()).getName(), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getX())), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getY())), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getZ())));
                        }
                    }, DynmapPlugin.this);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                        public void onSignChange(SignChangeEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();    /* Note: changes to this change event - intentional */
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if (p != null) dp = new NukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(DynmapListenerManager.EventType.SIGN_CHANGE, b.getName(),
                                    getWorld(l.getLevel()).getName(), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getX())), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getY())), Integer.parseInt(new java.text.DecimalFormat("0").format(l.getZ())), lines, dp);
                        }
                    }, DynmapPlugin.this);
                    break;
                default:
                    Log.severe("Unhandled event type: " + type);
                    return false;
            }
            registered.add(type);
            return true;
        }

        @Override
        public boolean sendWebChatEvent(String source, String name, String msg) {
            DynmapWebChatEvent evt = new DynmapWebChatEvent(source, name, msg);
            getServer().getPluginManager().callEvent(evt);
            return ((evt.isCancelled() == false) && (evt.isProcessed() == false));
        }

        @Override
        public void broadcastMessage(String msg) {
            getServer().broadcastMessage(msg);
        }

        @Override
        public String[] getBiomeIDs() {
            BiomeMap[] b = BiomeMap.values();
            String[] bname = new String[b.length];
            for (int i = 0; i < bname.length; i++)
                bname[i] = b[i].toString();
            return bname;
        }

        @Override
        public double getCacheHitRate() {
            return SnapshotCache.sscache.getHitRate();
        }

        @Override
        public void resetCacheStats() {
            SnapshotCache.sscache.resetStats();
        }

        @Override
        public DynmapWorld getWorldByName(String wname) {
            return DynmapPlugin.this.getWorldByName(wname);
        }

        @Override
        public DynmapPlayer getOfflinePlayer(String name) {
            OfflinePlayer op = (OfflinePlayer) getServer().getOfflinePlayer(name);
            if (op != null) {
                return new NukkitPlayer(op);
            }
            return null;
        }

        @Override
        public Set<String> checkPlayerPermissions(String player, Set<String> perms) {
            Player p = (Player) getServer().getOfflinePlayer(player);
            if (p.isBanned())
                return new HashSet<String>();
            for(String value: perms){
                System.out.println(value);
            }
            Set<String> rslt = luckperm.hasOfflinePermissions(player, perms);
            if (rslt == null) {
                rslt = new HashSet<String>();
                if (p.isOp()) {
                    rslt.addAll(perms);
                }
            }
            return rslt;
        }

        @Override
        public boolean checkPlayerPermission(String player, String perm) {
            OfflinePlayer p = (OfflinePlayer) getServer().getOfflinePlayer(player);
            if (p.isBanned())
                return false;
            boolean rslt = luckperm.hasOfflinePermission(player, perm);
            return rslt;
        }

        /**
         * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
         */
        @Override
        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
                                                 boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
            MapChunkCache c = w.getChunkCache(chunks);
            if (c == null) { /* Can fail if not currently loaded */
                return null;
            }
            if (w.visibility_limits != null) {
                for (VisibilityLimit limit : w.visibility_limits) {
                    c.setVisibleRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if (w.hidden_limits != null) {
                for (VisibilityLimit limit : w.hidden_limits) {
                    c.setHiddenRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if (c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false) {
                Log.severe("CraftBukkit build does not support biome APIs");
            }
            if (chunks.size() == 0) {    /* No chunks to get? */
                c.loadChunks(0);
                return c;
            }

            final MapChunkCache cc = c;

            while (!cc.isDoneLoading()) {
                Future<Boolean> f = core.getServer().callSyncMethod(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        boolean exhausted = true;

                        if (prev_tick != cur_tick) {
                            prev_tick = cur_tick;
                            cur_tick_starttime = System.nanoTime();
                        }
                        if (chunks_in_cur_tick > 0) {
                            boolean done = false;
                            while (!done) {
                                int cnt = chunks_in_cur_tick;
                                if (cnt > 5) cnt = 5;
                                chunks_in_cur_tick -= cc.loadChunks(cnt);
                                exhausted = (chunks_in_cur_tick == 0) || ((System.nanoTime() - cur_tick_starttime) > perTickLimit);
                                done = exhausted || cc.isDoneLoading();
                            }
                        }
                        return exhausted;
                    }
                });
                if (f == null) {
                    return null;
                }
                Boolean delay;
                try {
                    delay = f.get();
                } catch (CancellationException cx) {
                    return null;
                } catch (ExecutionException ex) {
                    Log.severe("Exception while fetching chunks: ", ex.getCause());
                    return null;
                } catch (Exception ix) {
                    Log.severe(ix);
                    return null;
                }
                if ((delay != null) && delay.booleanValue()) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ix) {
                    }
                }
            }
            /* If cancelled due to world unload return nothing */
            if (w.isLoaded() == false)
                return null;
            return c;
        }

        @Override
        public int getMaxPlayers() {
            return getServer().getMaxPlayers();
        }

        @Override
        public int getCurrentPlayers() {
            return helper.getOnlinePlayers().length;
        }

        @Override
        public boolean isModLoaded(String name) {
            return false;
        }

        @Override
        public String getModVersion(String name) {
            return null;
        }


        @Override
        public double getServerTPS() {
            return tps;
        }

        @Override
        public String getServerIP() {
            return Server.getInstance().getIp();
        }

        @Override
        public Map<Integer, String> getBlockIDMap() {
            String[] bsn = helper.getBlockNames();
            HashMap<Integer, String> map = new HashMap<Integer, String>();
            for (int i = 0; i < bsn.length; i++) {
                if (bsn[i] != null) {
                    if (bsn[i].indexOf(':') < 0)
                        map.put(i, "minecraft:" + bsn[i]);
                    else
                        map.put(i, bsn[i]);
                }
            }
            return map;
        }
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
                return Armor.getArmorPoints(player);
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
    /* Handler for generic console command sender */
    public class NukkitCommandSender implements DynmapCommandSender {
        private CommandSender sender;

        public NukkitCommandSender(CommandSender send) {
            sender = send;
        }

        @Override
        public boolean hasPrivilege(String privid) {
            if(sender != null)
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
    public void loadExtraBiomes(String mcver) {
        int cnt = 0;

        BiomeMap.loadWellKnownByVersion(mcver);
        /* Find array of biomes in biomebase */
        Object[] biomelist = helper.getBiomeBaseList();
        //Log.info("biomelist length = " + biomelist.length);
        /* Loop through list, skipping well known biomes */
        for(int i = 0; i < biomelist.length; i++) {
            Object bb = biomelist[i];
            if(bb != null) {
                float tmp = helper.getBiomeBaseTemperature(bb);
                float hum = helper.getBiomeBaseHumidity(bb);
                int watermult = helper.getBiomeBaseWaterMult(bb);
                //Log.info("biome[" + i + "]: hum=" + hum + ", tmp=" + tmp + ", mult=" + Integer.toHexString(watermult));

                BiomeMap bmap = BiomeMap.byBiomeID(i);
                if (bmap.isDefault()) {
                    String id =  helper.getBiomeBaseIDString(bb);
                    if(id == null) {
                        id = "BIOME_" + i;
                    }
                    bmap = new BiomeMap(i, id, tmp, hum);
                    //Log.info("Add custom biome [" + bmap.toString() + "] (" + i + ")");
                    cnt++;
                }
                else {
                    bmap.setTemperature(tmp);
                    bmap.setRainfall(hum);
                }
                if (watermult != -1) {
                    bmap.setWaterColorMultiplier(watermult);
                    //Log.info("Set watercolormult for " + bmap.toString() + " (" + i + ") to " + Integer.toHexString(watermult));
                }
            }
        }
        if(cnt > 0) {
            Log.info("Added " + cnt + " custom biome mappings");
        }
    }


    @Override
    public void onLoad() {
        Log.setLogger((Logger) this.getLogger(), "");

        helper = NukkitVersionHelper.helper;
        pm = this.getServer().getPluginManager();

    }


    /*
    toLoc function
     */
    private static DynmapLocation toLoc(Location l) {
        return new DynmapLocation(DynmapWorld.normalizeWorldName(l.getLevel().getName()), l.getX(), l.getY(), l.getZ());
    }


    /*
    The "BukkitServer" class in the original DynMap
     */
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
