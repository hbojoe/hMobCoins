package net.hboj.hmobcoins.listeners;

import net.hboj.hmobcoins.drops.MobDropService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class MobDeathListener implements Listener {

    private final MobDropService mobDropService;

    public MobDeathListener(MobDropService mobDropService) {
        this.mobDropService = mobDropService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        mobDropService.markSpawnerMob(event.getEntity(), event.getSpawnReason());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        mobDropService.handleDeath(entity);
    }
}
