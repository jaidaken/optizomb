package zombie.optizomb;

import zombie.MovingObjectUpdateScheduler;
import zombie.characters.IsoZombie;

/**
 * Opt 53: Separation throttle.
 *
 * Skips separate() for distant zombies based on cullRank from scene culling.
 * Frame-staggered by zombie ID to spread load evenly.
 *
 * Rank 0-149:  every frame
 * Rank 150-299: every 4th frame (staggered)
 * Rank 300+:   skip entirely
 */
public final class OptiZombSeparate {

    public static boolean shouldSkipSeparate(IsoZombie zombie) {
        int rank = zombie.cullRank;
        if (rank < 150) return false;
        if (rank >= 300) return true;
        // Every 4th frame, staggered by zombie ID
        long frame = MovingObjectUpdateScheduler.instance.getFrameCounter();
        return ((zombie.getOnlineID() + frame) & 3) != 0;
    }

    private OptiZombSeparate() {}
}
