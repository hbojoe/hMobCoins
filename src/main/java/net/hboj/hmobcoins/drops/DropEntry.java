package net.hboj.hmobcoins.drops;

import java.util.concurrent.ThreadLocalRandom;

public record DropEntry(
        String key,
        String itemId,
        int weight,
        int minAmount,
        int maxAmount
) {

    public int rollAmount(ThreadLocalRandom random) {
        if (minAmount == maxAmount) {
            return minAmount;
        }
        return random.nextInt(minAmount, maxAmount + 1);
    }
}
