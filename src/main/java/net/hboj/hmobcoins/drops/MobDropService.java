package net.hboj.hmobcoins.drops;

import net.hboj.hmobcoins.MobCoinsPlugin;
import net.hboj.hmobcoins.config.MobCoinsConfig;
import net.hboj.hmobcoins.hooks.ItemsAdderHook;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MobDropService {

    private static final Set<String> CITIZENS_METADATA_KEYS = Set.of("NPC", "CitizensNPC");
    private static final Set<String> MYTHIC_METADATA_KEYS = Set.of(
            "MythicMob",
            "MythicMobType",
            "MythicMobLevel",
            "mythicmob"
    );
    private static final Set<String> BLOCKED_SPAWNER_REASONS = Set.of("SPAWNER", "TRIAL_SPAWNER");

    private final MobCoinsPlugin plugin;
    private final ItemsAdderHook itemsAdderHook;
    private final NamespacedKey spawnerMobKey;

    private MobCoinsConfig config;
    private Method paperSpawnReasonMethod;

    public MobDropService(MobCoinsPlugin plugin, ItemsAdderHook itemsAdderHook) {
        this.plugin = plugin;
        this.itemsAdderHook = itemsAdderHook;
        this.spawnerMobKey = new NamespacedKey(plugin, "blocked_spawner_mob");
        try {
            this.paperSpawnReasonMethod = Entity.class.getMethod("getEntitySpawnReason");
        } catch (NoSuchMethodException ignored) {
            this.paperSpawnReasonMethod = null;
        }
    }

    public void reload(MobCoinsConfig config) {
        this.config = config;
    }

    public void markSpawnerMob(LivingEntity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        if (config == null || !config.settings().preventSpawnerMobs()) {
            return;
        }

        if (isBlockedSpawnerReason(spawnReason.name())) {
            entity.getPersistentDataContainer().set(spawnerMobKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public void handleDeath(LivingEntity entity) {
        if (config == null || !config.settings().enabled()) {
            return;
        }

        if (config.settings().onlyPlayerKills()) {
            Player killer = resolveKiller(entity);
            if (killer == null) {
                debug("Skipping " + entity.getType().name() + ": no real player killer.");
                return;
            }
        }

        if (!isRewardableMob(entity)) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = entity.getLocation();
        rollTable(config.globalDropTable(), location, random);

        DropTable mobTable = config.mobDropTable(entity.getType());
        if (mobTable != null) {
            rollTable(mobTable, location, random);
        }
    }

    private Player resolveKiller(LivingEntity entity) {
        EntityDamageEvent lastDamage = entity.getLastDamageCause();
        if (!(lastDamage instanceof EntityDamageByEntityEvent damageByEntityEvent)) {
            return null;
        }

        Entity damager = damageByEntityEvent.getDamager();
        if (damager instanceof Player player && isRealPlayer(player)) {
            return player;
        }

        if (config.settings().allowProjectileKills() && damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player && isRealPlayer(player)) {
                return player;
            }
        }

        return null;
    }

    private boolean isRealPlayer(Player player) {
        return player.isOnline() && player.getType() == EntityType.PLAYER && !hasAnyMetadata(player, CITIZENS_METADATA_KEYS);
    }

    private boolean isRewardableMob(LivingEntity entity) {
        EntityType entityType = entity.getType();

        if (entity instanceof Player || entity instanceof ArmorStand) {
            debug("Skipping " + entityType.name() + ": players and armor stands are not rewardable mobs.");
            return false;
        }

        if (!MobCoinsConfig.isSupportedHostileMob(entityType)) {
            debug("Skipping " + entityType.name() + ": unsupported mob type.");
            return false;
        }

        if (config.settings().preventCitizens() && isCitizensNpc(entity)) {
            debug("Skipping " + entityType.name() + ": Citizens NPC detected.");
            return false;
        }

        if (config.settings().preventCustomNamedMobs() && entity.customName() != null) {
            debug("Skipping " + entityType.name() + ": custom-named mob detected.");
            return false;
        }

        if (isCustomPluginMob(entity)) {
            debug("Skipping " + entityType.name() + ": configured custom mob marker detected.");
            return false;
        }

        if (config.settings().preventMythicMobs() && isMythicMob(entity)) {
            debug("Skipping " + entityType.name() + ": MythicMobs custom mob detected.");
            return false;
        }

        if (config.settings().preventSpawnerMobs() && isSpawnerMob(entity)) {
            debug("Skipping " + entityType.name() + ": blocked spawner mob detected.");
            return false;
        }

        return true;
    }

    private void rollTable(DropTable table, Location location, ThreadLocalRandom random) {
        if (table == null || !table.canRoll(random)) {
            return;
        }

        int rolls = table.rollCount(random);
        for (int index = 0; index < rolls; index++) {
            Optional<DropEntry> selectedEntry = table.selectEntry(random);
            if (selectedEntry.isEmpty()) {
                continue;
            }

            DropEntry entry = selectedEntry.get();
            int amount = entry.rollAmount(random);
            Optional<ItemStack> itemStack = itemsAdderHook.createItemStack(entry.itemId());
            if (itemStack.isEmpty()) {
                debug("Skipping drop '" + entry.key() + "' from table '" + table.id() + "': item could not be created.");
                continue;
            }

            dropStack(location, itemStack.get(), amount);
            debug("Dropped " + amount + "x " + entry.itemId() + " from table '" + table.id() + "'.");
        }
    }

    private void dropStack(Location location, ItemStack itemStack, int amount) {
        World world = location.getWorld();
        if (world == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        int maxStackSize = Math.max(1, itemStack.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStackSize);
            ItemStack stack = itemStack.clone();
            stack.setAmount(stackAmount);
            world.dropItemNaturally(location, stack);
            remaining -= stackAmount;
        }
    }

    private boolean isCitizensNpc(Entity entity) {
        return hasAnyMetadata(entity, CITIZENS_METADATA_KEYS) || hasAnyScoreboardTag(entity, CITIZENS_METADATA_KEYS);
    }

    private boolean isCustomPluginMob(Entity entity) {
        Set<String> customMobMetadataKeys = config.settings().customMobMetadataKeys();
        return hasAnyMetadata(entity, customMobMetadataKeys) || hasAnyScoreboardTag(entity, customMobMetadataKeys);
    }

    private boolean isMythicMob(Entity entity) {
        if (hasAnyMetadata(entity, MYTHIC_METADATA_KEYS) || hasMythicScoreboardTag(entity)) {
            return true;
        }

        return isMythicMobByApi(entity.getUniqueId());
    }

    private boolean isMythicMobByApi(UUID entityId) {
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) {
            return false;
        }

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mythicBukkit.getClass().getMethod("getMobManager").invoke(mythicBukkit);
            Method isActiveMob = mobManager.getClass().getMethod("isActiveMob", UUID.class);
            Object result = isActiveMob.invoke(mobManager, entityId);
            return result instanceof Boolean activeMob && activeMob;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private boolean isSpawnerMob(Entity entity) {
        Byte markedSpawnerMob = entity.getPersistentDataContainer().get(spawnerMobKey, PersistentDataType.BYTE);
        if (markedSpawnerMob != null && markedSpawnerMob == (byte) 1) {
            return true;
        }

        if (paperSpawnReasonMethod == null) {
            return false;
        }

        try {
            Object spawnReason = paperSpawnReasonMethod.invoke(entity);
            return spawnReason != null && isBlockedSpawnerReason(spawnReason.toString());
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private boolean isBlockedSpawnerReason(String spawnReason) {
        return BLOCKED_SPAWNER_REASONS.contains(spawnReason.toUpperCase(Locale.ROOT));
    }

    private boolean hasAnyMetadata(Entity entity, Set<String> keys) {
        for (String key : keys) {
            if (entity.hasMetadata(key)) {
                for (MetadataValue value : entity.getMetadata(key)) {
                    if (value.asBoolean() || value.value() != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAnyScoreboardTag(Entity entity, Set<String> tags) {
        for (String tag : entity.getScoreboardTags()) {
            for (String blockedTag : tags) {
                if (tag.equalsIgnoreCase(blockedTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMythicScoreboardTag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            String normalizedTag = tag.toLowerCase(Locale.ROOT);
            if (normalizedTag.startsWith("mythicmobs:") || normalizedTag.startsWith("mythicmob:")) {
                return true;
            }
        }
        return false;
    }

    private void debug(String message) {
        if (config != null && config.settings().debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
