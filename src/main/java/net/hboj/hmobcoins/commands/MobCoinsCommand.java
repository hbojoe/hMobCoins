package net.hboj.hmobcoins.commands;

import net.hboj.hmobcoins.MobCoinsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MobCoinsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "mobcoins.admin";

    private final MobCoinsPlugin plugin;

    public MobCoinsCommand(MobCoinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }

            plugin.reloadMobCoins();
            sender.sendMessage(Component.text("hMobCoins config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of("reload");
        }
        return List.of();
    }
}
