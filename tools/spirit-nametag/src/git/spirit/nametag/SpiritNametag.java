package git.spirit.nametag;

import net.fabricmc.api.ModInitializer;

/** Only prints a line so latest.log shows the helper was loaded (look for "[SpiritNametag]"). */
public class SpiritNametag implements ModInitializer {
    private static boolean logged;

    @Override
    public void onInitialize() {
        System.out.println("[SpiritNametag] loaded, own name tag helper active");
    }

    /** Called from the mixins the first time they decide for your own player. */
    public static void firstHit(String where) {
        if(logged) return;
        logged = true;
        System.out.println("[SpiritNametag] own name tag forced on via " + where);
    }
}
