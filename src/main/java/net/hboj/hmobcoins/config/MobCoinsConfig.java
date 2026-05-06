package net.hboj.hmobcoins.config;

import net.hboj.hmobcoins.MobCoinsPlugin;
import net.hboj.hmobcoins.drops.DropEntry;
import net.hboj.hmobcoins.drops.DropTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class MobCoinsConfig {

    private static final Set<String> SUPPORTED_HOSTILE_MOBS = Set.of(
            "BLAZE",
            "BOGGED",
            "BREEZE",
            "CAVE_SPIDER",
            "CREAKING",
            "CREEPER",
            "DROWNED",
            "ELDER_GUARDIAN",
            "ENDER_DRAGON",
            "ENDERMAN",
            "ENDERMITE",
            "EVOKER",
            "GHAST",
            "GIANT",
            "GUARDIAN",
            "HOGLIN",
            "HUSK",
            "ILLUSIONER",
            "MAGMA_CUBE",
            "PHANTOM",
            "PIGLIN",
            "PIGLIN_BRUTE",
            "PILLAGER",
            "RAVAGER",
            "SHULKER",
            "SILVERFISH",
            "SKELETON",
            "SLIME",
            "SPIDER",
            "STRAY",
            "VEX",
            "VINDICATOR",
            "WARDEN",
            "WITCH",
            "WITHER",
            "WITHER_SKELETON",
            "ZOGLIN",
            "ZOMBIE",
            "ZOMBIE_VILLAGER",
            "ZOMBIFIED_PIGLIN"
    );
    private static final Set<String> DEFAULT_CUSTOM_MOB_METADATA_KEYS = Set.of(
            "CustomMob",
            "custommob",
            "EliteMob",
            "elite_mob",
            "LevelledMobs",
            "levelledmobs"
    );

    private final Settings settings;
    private final ItemsAdderSettings itemsAdderSettings;
    private final DropTable globalDropTable;
    private final Map<EntityType, DropTable> mobDropTables;

    private MobCoinsConfig(
            Settings settings,
            ItemsAdderSettings itemsAdderSettings,
            DropTable globalDropTable,
            Map<EntityType, DropTable> mobDropTables
    ) {
        this.settings = settings;
        this.itemsAdderSettings = itemsAdderSettings;
        this.globalDropTable = globalDropTable;
        this.mobDropTables = Map.copyOf(mobDropTables);
    }

    public static MobCoinsConfig load(MobCoinsPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        Settings settings = new Settings(
                config.getBoolean("settings.enabled", true),
                config.getBoolean("settings.debug", false),
                config.getBoolean("settings.only-player-kills", true),
                config.getBoolean("settings.allow-projectile-kills", true),
                config.getBoolean("settings.prevent-spawner-mobs", true),
                config.getBoolean("settings.prevent-mythicmobs", true),
                config.getBoolean("settings.prevent-citizens", true),
                config.getBoolean("settings.prevent-custom-named-mobs", true),
                loadCustomMobMetadataKeys(config),
                clampChance(config.getDouble("settings.default-drop-chance", 25.0))
        );

        ItemsAdderSettings itemsAdderSettings = new ItemsAdderSettings(
                config.getBoolean("itemsadder.enabled", true),
                config.getBoolean("itemsadder.warn-invalid-items", true)
        );

        DropTable globalDropTable = loadDropTable(
                "global-drops",
                config.getConfigurationSection("global-drops"),
                settings.defaultDropChance(),
                logger
        );

        Map<EntityType, DropTable> mobDropTables = new EnumMap<>(EntityType.class);
        ConfigurationSection mobsSection = config.getConfigurationSection("mobs");
        if (mobsSection != null) {
            for (String rawMobName : mobsSection.getKeys(false)) {
                String mobName = rawMobName.toUpperCase(Locale.ROOT);
                EntityType entityType;
                try {
                    entityType = EntityType.valueOf(mobName);
                } catch (IllegalArgumentException exception) {
                    logger.warning("Ignoring unknown mob type in config: mobs." + rawMobName);
                    continue;
                }

                if (!isSupportedHostileMob(entityType)) {
                    logger.warning("Ignoring unsupported non-hostile or non-vanilla mob type in config: mobs." + rawMobName);
                    continue;
                }

                DropTable table = loadDropTable(
                        "mobs." + mobName,
                        mobsSection.getConfigurationSection(rawMobName),
                        settings.defaultDropChance(),
                        logger
                );
                mobDropTables.put(entityType, table);
            }
        }

        return new MobCoinsConfig(settings, itemsAdderSettings, globalDropTable, mobDropTables);
    }

    private static DropTable loadDropTable(String id, ConfigurationSection section, double defaultChance, Logger logger) {
        if (section == null) {
            return DropTable.empty(id);
        }

        boolean enabled = section.getBoolean("enabled", true);
        double chance = clampChance(section.getDouble("chance", defaultChance));
        int minRolls = Math.max(1, section.getInt("rolls.min", 1));
        int maxRolls = Math.max(minRolls, section.getInt("rolls.max", minRolls));

        Set<DropEntry> entries = new LinkedHashSet<>();
        ConfigurationSection dropsSection = section.getConfigurationSection("drops");
        if (dropsSection == null) {
            logger.warning("Drop table '" + id + "' has no drops section.");
            return new DropTable(id, enabled, chance, minRolls, maxRolls, entries);
        }

        for (String entryKey : dropsSection.getKeys(false)) {
            ConfigurationSection entrySection = dropsSection.getConfigurationSection(entryKey);
            if (entrySection == null) {
                logger.warning("Ignoring invalid drop entry '" + id + ".drops." + entryKey + "'.");
                continue;
            }

            String itemId = entrySection.getString("item", "").trim();
            int weight = entrySection.getInt("weight", 1);
            int minAmount = Math.max(1, entrySection.getInt("amount.min", 1));
            int maxAmount = Math.max(minAmount, entrySection.getInt("amount.max", minAmount));

            if (itemId.isBlank()) {
                logger.warning("Ignoring drop entry '" + id + ".drops." + entryKey + "' because item is blank.");
                continue;
            }

            if (weight <= 0) {
                logger.warning("Ignoring drop entry '" + id + ".drops." + entryKey + "' because weight must be greater than 0.");
                continue;
            }

            entries.add(new DropEntry(entryKey, itemId, weight, minAmount, maxAmount));
        }

        if (entries.isEmpty()) {
            logger.warning("Drop table '" + id + "' has no valid entries.");
        }

        return new DropTable(id, enabled, chance, minRolls, maxRolls, entries);
    }

    private static double clampChance(double chance) {
        return Math.max(0.0, Math.min(100.0, chance));
    }

    private static Set<String> loadCustomMobMetadataKeys(FileConfiguration config) {
        Set<String> metadataKeys = new LinkedHashSet<>(DEFAULT_CUSTOM_MOB_METADATA_KEYS);
        List<String> configuredKeys = config.getStringList("settings.custom-mob-metadata-keys");
        for (String configuredKey : configuredKeys) {
            if (configuredKey != null && !configuredKey.isBlank()) {
                metadataKeys.add(configuredKey.trim());
            }
        }
        return Set.copyOf(metadataKeys);
    }

    public static boolean isSupportedHostileMob(EntityType entityType) {
        return SUPPORTED_HOSTILE_MOBS.contains(entityType.name());
    }

    public Settings settings() {
        return settings;
    }

    public ItemsAdderSettings itemsAdderSettings() {
        return itemsAdderSettings;
    }

    public DropTable globalDropTable() {
        return globalDropTable;
    }

    public DropTable mobDropTable(EntityType entityType) {
        return mobDropTables.get(entityType);
    }

    public record Settings(
            boolean enabled,
            boolean debug,
            boolean onlyPlayerKills,
            boolean allowProjectileKills,
            boolean preventSpawnerMobs,
            boolean preventMythicMobs,
            boolean preventCitizens,
            boolean preventCustomNamedMobs,
            Set<String> customMobMetadataKeys,
            double defaultDropChance
    ) {
    }

    public record ItemsAdderSettings(
            boolean enabled,
            boolean warnInvalidItems
    ) {
    }
}
