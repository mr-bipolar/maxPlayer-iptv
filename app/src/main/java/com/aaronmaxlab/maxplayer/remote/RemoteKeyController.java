package com.aaronmaxlab.maxplayer.remote;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;

public class RemoteKeyController implements RemoteInputManager.KeyListener {

    private final MaxPlayerApplication application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RemoteKeyController(MaxPlayerApplication application) {
        this.application = application;
    }

    @Override
    public void onRemoteKeyReceived(String key) {
        mainHandler.post(() -> dispatchKey(key));
    }


    private void dispatchKey(String key) {

        Log.d("RemoteKeyController", "Received key: " + key);

        Activity activity = application.getCurrentActivity();

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Back uses Android's normal back navigation.
        if ("back".equals(key)) {
            handleBack(activity);
            return;
        }

        // Convert remote key into Android key code.
        int keyCode;

        switch (key) {
            case "up":
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;

            case "down":
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;

            case "left":
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;

            case "right":
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;

            case "center":
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
                break;

            default:
                return;
        }

        // Convert Android key code into focus direction.
        int direction;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = View.FOCUS_UP;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = View.FOCUS_DOWN;
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = View.FOCUS_LEFT;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = View.FOCUS_RIGHT;
                break;

            default:
                // Center / OK
                activity.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                activity.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                return;
        }

        View current = activity.getCurrentFocus();

        if (current == null) {
            current = activity.getWindow().getDecorView();
        }

        View next = current.focusSearch(direction);

        if (next != null && next != current) {
            boolean focused = next.requestFocus();

            Log.d("RemoteKeyController",
                    "Focus move: " + key
                            + " | from: " + current.getClass().getSimpleName()
                            + " | to: " + next.getClass().getSimpleName()
                            + " | success: " + focused);
        } else {
            Log.d("RemoteKeyController",
                    "No focus target for direction: " + key);
        }
    }

    private void handleBack(Activity activity) {

        if (activity.isFinishing() || activity.isDestroyed()
                || application.getCurrentActivity() != activity) {
            return;
        }

        View decorView = activity.getWindow().getDecorView();

        WindowInsetsCompat insets =
                ViewCompat.getRootWindowInsets(decorView);

        // First dismiss the native keyboard if it is visible.
        if (insets != null
                && insets.isVisible(WindowInsetsCompat.Type.ime())) {

            WindowInsetsControllerCompat controller =
                    ViewCompat.getWindowInsetsController(decorView);

            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.ime());
            }

            return;
        }

        // Otherwise perform normal Back navigation.
        if (activity instanceof ComponentActivity) {
            ((ComponentActivity) activity)
                    .getOnBackPressedDispatcher()
                    .onBackPressed();
        } else {
            activity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            activity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        }
    }
}