package net.hboj.hmobcoins.drops;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class DropTable {

    private final String id;
    private final boolean enabled;
    private final double chance;
    private final int minRolls;
    private final int maxRolls;
    private final List<DropEntry> entries;
    private final int totalWeight;

    public DropTable(String id, boolean enabled, double chance, int minRolls, int maxRolls, Collection<DropEntry> entries) {
        this.id = id;
        this.enabled = enabled;
        this.chance = chance;
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
        this.entries = List.copyOf(entries);
        this.totalWeight = this.entries.stream().mapToInt(DropEntry::weight).sum();
    }

    public static DropTable empty(String id) {
        return new DropTable(id, false, 0.0, 1, 1, new ArrayList<>());
    }

    public boolean canRoll(ThreadLocalRandom random) {
        return enabled && !entries.isEmpty() && totalWeight > 0 && random.nextDouble(100.0) < chance;
    }

    public int rollCount(ThreadLocalRandom random) {
        if (minRolls == maxRolls) {
            return minRolls;
        }
        return random.nextInt(minRolls, maxRolls + 1);
    }

    public Optional<DropEntry> selectEntry(ThreadLocalRandom random) {
        if (entries.isEmpty() || totalWeight <= 0) {
            return Optional.empty();
        }

        int selectedWeight = random.nextInt(totalWeight) + 1;
        int cursor = 0;
        for (DropEntry entry : entries) {
            cursor += entry.weight();
            if (selectedWeight <= cursor) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }

    public String id() {
        return id;
    }

    public double chance() {
        return chance;
    }
}
