package skinsrestorer.bukkit;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import skinsrestorer.bukkit.commands.GUICommand;
import skinsrestorer.bukkit.commands.SkinCommand;
import skinsrestorer.bukkit.commands.SrCommand;
import skinsrestorer.bukkit.menu.SkinsGUI;
import skinsrestorer.bukkit.skinfactory.SkinFactory;
import skinsrestorer.bukkit.skinfactory.UniversalSkinFactory;
import skinsrestorer.shared.storage.Config;
import skinsrestorer.shared.storage.CooldownStorage;
import skinsrestorer.shared.storage.Locale;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.MojangAPI.SkinRequestException;
import skinsrestorer.shared.utils.MySQL;
import skinsrestorer.shared.utils.ReflectionUtil;
import skinsrestorer.shared.utils.updater.bukkit.SpigetUpdate;
import skinsrestorer.shared.utils.updater.core.UpdateCallback;
import skinsrestorer.shared.utils.updater.core.VersionComparator;
import java.io.*;
import java.util.List;

public class SkinsRestorer extends JavaPlugin {

    private static SkinsRestorer instance;
    private SkinFactory factory;
    private MySQL mysql;
    private boolean bungeeEnabled;

    public static SkinsRestorer getInstance() {
        return instance;
    }

    public SkinFactory getFactory() {
        return factory;
    }

    public MySQL getMySQL() {
        return mysql;
    }

    public String getVersion() {
        return getDescription().getVersion();
    }
    

    @Override
    public void onEnable() {
    	
        ConsoleCommandSender console = getServer().getConsoleSender();

    	@SuppressWarnings("unused")
        MetricsLite metrics = new MetricsLite(this);
        
        SpigetUpdate updater = new SpigetUpdate(this, 2124);
        updater.setVersionComparator(VersionComparator.EQUAL);
        updater.setVersionComparator(VersionComparator.SEM_VER_BETA);

        instance = this;

        try {
            // Doesn't support Cauldron and stuff..
            Class.forName("net.minecraftforge.cauldron.CauldronHooks");
            console.sendMessage("§e[§2SkinsRestorer§e] §cSkinsRestorer doesn't support Cauldron, Thermos or KCauldron, Sorry :(");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            try {
                // Checking for old versions
                factory = (SkinFactory) Class
                        .forName("skinsrestorer.bukkit.skinfactory.SkinFactory_" + ReflectionUtil.serverVersion)
                        .newInstance();
            } catch (Exception ex) {
                // 1.8+++
                factory = new UniversalSkinFactory();
            }
        }
        console.sendMessage("§e[§2SkinsRestorer§e] §aDetected Minecraft §e" + ReflectionUtil.serverVersion + "§a, using §e"+ factory.getClass().getSimpleName() + "§a.");


        // Multiverse Core support.
        MCoreAPI.init();
        if (MCoreAPI.check())
            console.sendMessage("§e[§2SkinsRestorer§e] §aDetected §eMultiverse-Core§a! Using it for dimensions.");


        // Bungeecord stuff
        try {
            bungeeEnabled = YamlConfiguration.loadConfiguration(new File("spigot.yml"))
                    .getBoolean("settings.bungeecord");
        } catch (Exception e) {
            bungeeEnabled = false;
        }

        if (bungeeEnabled) {

            Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            Bukkit.getMessenger().registerIncomingPluginChannel(this, "SkinsRestorer", new PluginMessageListener() {
                @Override
                public void onPluginMessageReceived(String channel, final Player player, final byte[] message) {
                    if (!channel.equals("SkinsRestorer"))
                        return;

                    Bukkit.getScheduler().runTaskAsynchronously(getInstance(), new Runnable() {

                        @Override
                        public void run() {

                            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));

                            try {
                                String subchannel = in.readUTF();

                                if (subchannel.equalsIgnoreCase("SkinUpdate")) {
                                    try {
                                        factory.applySkin(player,
                                                SkinStorage.createProperty(in.readUTF(), in.readUTF(), in.readUTF()));
                                    } catch (Exception e) {
                                    }
                                    factory.updateSkin(player);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });
            if (Config.UPDATER_ENABLED) {
                updater.checkForUpdate(new UpdateCallback() {
                    @Override
                    public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {
                        if (hasDirectDownload) {
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |---------------|");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |  §eBungee Mode§a  |");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                            console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §c" + getVersion());
                            console.sendMessage("§e[§2SkinsRestorer§e]     A new version is available! Downloading it now...");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                            if (updater.downloadUpdate()) {
                                console.sendMessage("§e[§2SkinsRestorer§e] Update downloaded successfully, it will be applied on the next restart.");
                            } else {
                                // Update failed
                                console.sendMessage("§e[§2SkinsRestorer§e] §cCould not download the update, reason: " + updater.getFailReason());
                            }
                        }
                    }

                    @Override
                    public void upToDate() {
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    |---------------|");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    |  §eBungee Mode§a  |");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §a" + getVersion());
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    This is the latest version!");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                    }
                });
            }

            return;
        }

        // Config stuff
        Config.load(getResource("config.yml"));
        Locale.load();

        if (Config.USE_MYSQL)
            SkinStorage.init(mysql = new MySQL(Config.MYSQL_HOST, Config.MYSQL_PORT, Config.MYSQL_DATABASE,
                    Config.MYSQL_USERNAME, Config.MYSQL_PASSWORD));
        else
            SkinStorage.init(getDataFolder());

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new CooldownStorage(), 0, 1 * 20);

        // Commands
        getCommand("skinsrestorer").setExecutor(new SrCommand());
        getCommand("skin").setExecutor(new SkinCommand());
        getCommand("skins").setExecutor(new GUICommand());
        getCommand("skinver").setExecutor(new CommandExecutor() {

            public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] arg3) {
                sender.sendMessage("§8This server is running §aSkinsRestorer §e"
                        + SkinsRestorer.getInstance().getVersion() + "§8, made with love by §c"
                        + SkinsRestorer.getInstance().getDescription().getAuthors().get(0)
                        + "§8, utilizing Minecraft §a" + ReflectionUtil.serverVersion + "§8.");
                return false;
            }

        });

        Bukkit.getPluginManager().registerEvents(new SkinsGUI(), this);
        Bukkit.getPluginManager().registerEvents(new Listener() {

            // LoginEvent happens on attemptLogin so its the best place to set
            // the skin
            @EventHandler
            public void onLogin(PlayerJoinEvent e) {
                try {
                    if (Config.DISABLE_ONJOIN_SKINS) {
                        factory.applySkin(e.getPlayer(),
                                SkinStorage.getSkinData(SkinStorage.getPlayerSkin(e.getPlayer().getName())));
                        return;
                    }
                    if (Config.DEFAULT_SKINS_ENABLED)
                        if (SkinStorage.getPlayerSkin(e.getPlayer().getName()) == null) {
                            List<String> skins = Config.DEFAULT_SKINS;
                            int randomNum = 0 + (int) (Math.random() * skins.size());
                            factory.applySkin(e.getPlayer(),
                                    SkinStorage.getOrCreateSkinForPlayer(skins.get(randomNum)));
                            return;
                        }
                    factory.applySkin(e.getPlayer(), SkinStorage.getOrCreateSkinForPlayer(e.getPlayer().getName()));
                } catch (Exception ex) {
                }
            }
        }, this);

        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {

            @Override
            public void run() {

                if (Config.UPDATER_ENABLED) {
                    updater.checkForUpdate(new UpdateCallback() {
                        @Override
                        public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {
                            if (hasDirectDownload) {
                                console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                                console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                                console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                                console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                                console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                                console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §c" + getVersion());
                                console.sendMessage("§e[§2SkinsRestorer§e]     A new version is available! Downloading it now...");
                                console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                                if (updater.downloadUpdate()) {
                                	console.sendMessage("§e[§2SkinsRestorer§e] Update downloaded successfully, it will be applied on the next restart.");
                                } else {
                                    // Update failed
                                	console.sendMessage("§e[§2SkinsRestorer§e] §cCould not download the update, reason: " + updater.getFailReason());
                                }
                            }
                        }

                        @Override
                        public void upToDate() {
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                            console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §a" + getVersion());
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    This is the latest version!");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        }
                    });
                }

                if (Config.DEFAULT_SKINS_ENABLED)
                    for (String skin : Config.DEFAULT_SKINS)
                        try {
                            SkinStorage.setSkinData(skin, MojangAPI.getSkinProperty(MojangAPI.getUUID(skin)));
                        } catch (SkinRequestException e) {
                            if (SkinStorage.getSkinData(skin) == null)
                                console.sendMessage( "§e[§2SkinsRestorer§e] §cDefault Skin '" + skin + "' request error: " + e.getReason());
                        }
            }

        });

    }
}
