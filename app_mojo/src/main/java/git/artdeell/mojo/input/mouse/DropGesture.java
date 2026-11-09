package git.artdeell.mojo.input.mouse;


import android.os.Handler;
import android.view.KeyEvent;

import git.artdeell.mojo.prefs.LauncherPreferences;

import git.artdeell.mojo.CallbackBridge;

// SPIRIT: alag delay (PREF_HOTBAR_DROP_DELAY) aur dheema repeat (500 ms)
public class DropGesture implements Runnable{
    private final Handler mHandler;
    private boolean mActive;

    public DropGesture(Handler mHandler) {
        this.mHandler = mHandler;
    }

    public void submit() {
        if(!mActive) {
            mActive = true;
            mHandler.postDelayed(this, LauncherPreferences.PREF_HOTBAR_DROP_DELAY);
        }
    }

    public void cancel() {
        mActive = false;
        mHandler.removeCallbacks(this);
    }

    @Override
    public void run() {
        if(!mActive) return;
        CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_Q);
        mHandler.postDelayed(this, 500);
    }
}
