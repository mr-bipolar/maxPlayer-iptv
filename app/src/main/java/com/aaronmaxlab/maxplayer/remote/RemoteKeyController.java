package com.aaronmaxlab.maxplayer.remote;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;

import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;

import java.util.ArrayDeque;
import java.util.Deque;

public class RemoteKeyController implements RemoteInputManager.KeyListener {

    private final MaxPlayerApplication application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Dialog activeDialog;
    private final Deque<Dialog> dialogStack = new ArrayDeque<>();

    public RemoteKeyController(MaxPlayerApplication application) {
        this.application = application;
    }

    @Override
    public void onRemoteKeyReceived(String key) {
        mainHandler.post(() -> dispatchKey(key));
    }

    public void registerDialog(Dialog dialog) {
        if (dialog == null) return;

        dialogStack.remove(dialog);
        dialogStack.addLast(dialog);
        activeDialog = dialog;

        Log.d("RemoteKeyController", "Dialog registered: " + dialog);
    }

    public void unregisterDialog(Dialog dialog) {
        if (dialog == null) return;

        dialogStack.remove(dialog);
        activeDialog = dialogStack.peekLast();

        Log.d("RemoteKeyController",
                "Dialog unregistered. Active dialog: " + activeDialog);
    }

    public Dialog getActiveDialog() {
        return activeDialog;
    }


    private void dispatchKey(String key) {

        Log.d("RemoteKeyController", "Received key: " + key);

        Activity activity = application.getCurrentActivity();

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Dialog dialog = activeDialog;

        // Handle DPAD while dialog is open.
        if (dialog != null && dialog.isShowing() && dialog.getWindow() != null) {

            View decorView = dialog.getWindow().getDecorView();

            if ("back".equals(key)) {
                dialog.dismiss();
                return;
            }

            int keyCode = getKeyCode(key);

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                return;
            }

            View current = decorView.findFocus();

            if (current == null) {
                current = decorView;
            }

            // Let Android handle DPAD and CENTER inside dialog lists.
           // AlertDialog single-choice items use ListView internally.
            if (current instanceof AbsListView) {
                current.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                );
                current.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, keyCode)
                );
                return;
            }

            // OK / Center
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (current.isClickable() && current.isEnabled()) {
                    current.performClick();
                }
                return;
            }

            int direction = getFocusDirection(keyCode);

            if (direction == -1) {
                return;
            }

            View next = current.focusSearch(direction);

            if (next != null && next != current
                    && isViewInsideDialog(next, decorView)) {

                boolean focused = next.requestFocus();

                Log.d("RemoteKeyController",
                        "Dialog focus: " + key
                                + " | from: " + current.getClass().getSimpleName()
                                + " | to: " + next.getClass().getSimpleName()
                                + " | success: " + focused);

            } else {
                Log.d("RemoteKeyController",
                        "No dialog focus target: " + key);
            }

            return;
        }

        // Normal Activity Back handling.
        if ("back".equals(key)) {
            handleBack(activity);
            return;
        }

        int keyCode = getKeyCode(key);

        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            activity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            activity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return;
        }

        int direction = getFocusDirection(keyCode);

        if (direction == -1) {
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

    private int getKeyCode(String key) {
        switch (key) {
            case "up":
                return KeyEvent.KEYCODE_DPAD_UP;
            case "down":
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left":
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right":
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "center":
                return KeyEvent.KEYCODE_DPAD_CENTER;
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private int getFocusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return View.FOCUS_RIGHT;
            default:
                return -1;
        }
    }

    private boolean isViewInsideDialog(View view, View dialogDecor) {
        View current = view;

        while (current != null) {
            if (current == dialogDecor) {
                return true;
            }

            if (!(current.getParent() instanceof View)) {
                break;
            }

            current = (View) current.getParent();
        }

        return false;
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