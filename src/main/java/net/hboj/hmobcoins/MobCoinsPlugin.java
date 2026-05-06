package net.hboj.hmobcoins;

import net.hboj.hmobcoins.commands.MobCoinsCommand;
import net.hboj.hmobcoins.config.MobCoinsConfig;
import net.hboj.hmobcoins.drops.MobDropService;
import net.hboj.hmobcoins.hooks.ItemsAdderHook;
import net.hboj.hmobcoins.listeners.MobDeathListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobCoinsPlugin extends JavaPlugin {

    private MobCoinsConfig mobCoinsConfig;
    private ItemsAdderHook itemsAdderHook;
    private MobDropService mobDropService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.itemsAdderHook = new ItemsAdderHook(this);
        this.mobDropService = new MobDropService(this, itemsAdderHook);
        reloadMobCoins();

        getServer().getPluginManager().registerEvents(new MobDeathListener(mobDropService), this);

        MobCoinsCommand commandExecutor = new MobCoinsCommand(this);
        PluginCommand command = getCommand("mobcoins");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        getLogger().info("hMobCoins enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("hMobCoins disabled.");
    }

    public void reloadMobCoins() {
        reloadConfig();
        this.mobCoinsConfig = MobCoinsConfig.load(this);
        this.itemsAdderHook.reload(mobCoinsConfig);
        this.mobDropService.reload(mobCoinsConfig);
    }

    public MobCoinsConfig mobCoinsConfig() {
        return mobCoinsConfig;
    }
}
