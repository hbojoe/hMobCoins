package net.hboj.hmobcoins.hooks;

import net.hboj.hmobcoins.MobCoinsPlugin;
import net.hboj.hmobcoins.config.MobCoinsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ItemsAdderHook {

    private final MobCoinsPlugin plugin;
    private final Set<String> warnedInvalidItems = new HashSet<>();

    private boolean enabled;
    private boolean warnInvalidItems;
    private boolean apiAvailable;
    private boolean warnedMissingApi;
    private Method getInstanceMethod;
    private Method getItemStackMethod;

    public ItemsAdderHook(MobCoinsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(MobCoinsConfig config) {
        this.enabled = config.itemsAdderSettings().enabled();
        this.warnInvalidItems = config.itemsAdderSettings().warnInvalidItems();
        this.warnedMissingApi = false;
        this.warnedInvalidItems.clear();
        this.apiAvailable = false;
        this.getInstanceMethod = null;
        this.getItemStackMethod = null;

        if (!enabled) {
            return;
        }

        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            warnMissingApi();
            return;
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            this.getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            this.getItemStackMethod = customStackClass.getMethod("getItemStack");
            this.apiAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().warning("ItemsAdder is installed, but the CustomStack API could not be found. Coin drops are disabled.");
        }
    }

    public Optional<ItemStack> createItemStack(String namespacedId) {
        if (!enabled) {
            return Optional.empty();
        }

        if (!isValidNamespaceId(namespacedId)) {
            warnInvalidItem(namespacedId, "expected a namespace ID like 'namespace:item_name'");
            return Optional.empty();
        }

        if (!apiAvailable) {
            warnMissingApi();
            return Optional.empty();
        }

        try {
            Object customStack = getInstanceMethod.invoke(null, namespacedId);
            if (customStack == null) {
                warnInvalidItem(namespacedId, "ItemsAdder returned no custom stack");
                return Optional.empty();
            }

            Object result = getItemStackMethod.invoke(customStack);
            if (!(result instanceof ItemStack itemStack) || itemStack.getType() == Material.AIR) {
                warnInvalidItem(namespacedId, "ItemsAdder returned an empty item stack");
                return Optional.empty();
            }

            ItemStack clone = itemStack.clone();
            clone.setAmount(1);
            return Optional.of(clone);
        } catch (IllegalAccessException exception) {
            plugin.getLogger().warning("Could not access the ItemsAdder CustomStack API. Coin drops are disabled.");
            return Optional.empty();
        } catch (InvocationTargetException exception) {
            warnInvalidItem(namespacedId, "ItemsAdder failed to create the item");
            return Optional.empty();
        }
    }

    private boolean isValidNamespaceId(String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank()) {
            return false;
        }

        int separatorIndex = namespacedId.indexOf(':');
        return separatorIndex > 0 && separatorIndex < namespacedId.length() - 1;
    }

    private void warnMissingApi() {
        if (!warnInvalidItems || warnedMissingApi) {
            return;
        }

        warnedMissingApi = true;
        plugin.getLogger().warning("ItemsAdder is missing or not enabled. Configured coin items will not be dropped.");
    }

    private void warnInvalidItem(String namespacedId, String reason) {
        if (!warnInvalidItems || !warnedInvalidItems.add(namespacedId)) {
            return;
        }

        plugin.getLogger().warning("Invalid ItemsAdder item '" + namespacedId + "': " + reason + ".");
    }
}
