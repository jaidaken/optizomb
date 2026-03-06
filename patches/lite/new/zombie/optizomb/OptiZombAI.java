package zombie.optizomb;

import zombie.MovingObjectUpdateScheduler;
import zombie.characters.IsoZombie;

/**
 * Opt 54: AI throttle.
 *
 * Throttles RespondToSound() and updateSearchForCorpse() for distant zombies.
 * Frame-staggered by zombie ID to spread load evenly.
 *
 * Rank 0-199:  every frame
 * Rank 200-399: every 2nd frame (staggered)
 * Rank 400+:   every 4th frame (staggered)
 */
public final class OptiZombAI {

    public static boolean shouldSkipAI(IsoZombie zombie) {
        int rank = zombie.cullRank;
        if (rank < 200) return false;
        long frame = MovingObjectUpdateScheduler.instance.getFrameCounter();
        if (rank < 400) {
            // Every 2nd frame
            return ((zombie.getOnlineID() + frame) & 1) != 0;
        }
        // Every 4th frame
        return ((zombie.getOnlineID() + frame) & 3) != 0;
    }

    private OptiZombAI() {}
}
