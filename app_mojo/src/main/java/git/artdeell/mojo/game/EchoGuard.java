package git.artdeell.mojo.game;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import git.artdeell.mojo.prefs.LauncherPreferences;

import java.io.File;

/**
 * Voice chat echo guard (experimental, off by default). The Simple Voice Chat mod has no echo canceller, so the game
 * sound that leaves the speaker comes back through the mic. Android's call audio mode turns on the phone's own echo
 * and noise processing for the microphone, which is the only lever the launcher has.
 */
public final class EchoGuard {
    private static boolean sActive;

    private EchoGuard() {}

    public static void start(Context ctx, File gameDir) {
        try {
            if(!LauncherPreferences.DEFAULT_PREF.getBoolean("spiritVoiceEcho", false)) return;
            if(!new File(gameDir, "config/voicechat").isDirectory()) return; // no voice chat mod in this instance
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if(am == null) return;
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            // In call mode the sound goes to the earpiece unless the speaker is asked for, but never over headphones
            if(!hasHeadphones(am)) am.setSpeakerphoneOn(true);
            sActive = true;
        } catch (RuntimeException ignored) { /* audio settings are a bonus, never a reason to stop the game */ }
    }

    public static void stop(Context ctx) {
        if(!sActive) return;
        sActive = false;
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if(am == null) return;
            am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (RuntimeException ignored) { /* nothing to do */ }
    }

    private static boolean hasHeadphones(AudioManager am) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return am.isWiredHeadsetOn() || am.isBluetoothA2dpOn();
        for(AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch(d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}
