
package com.aaronmaxlab.maxplayer.remote;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class RemoteInputController implements RemoteInputManager.InputListener {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Set<EditText> registeredInputs =
            Collections.newSetFromMap(new WeakHashMap<>());

    private boolean phoneConnected = false;
    private final MaxPlayerApplication application;

    public RemoteInputController(MaxPlayerApplication application) {
        this.application = application;
    }

    public void registerInputs(EditText... inputs) {
        mainHandler.post(() -> {
            synchronized (registeredInputs) {
                for (EditText input : inputs) {
                    if (input != null) {
                        registeredInputs.add(input);
                        applyKeyboardMode(input);
                    }
                }
            }
        });
    }

    public void unregisterInputs(EditText... inputs) {
        mainHandler.post(() -> {
            synchronized (registeredInputs) {
                for (EditText input : inputs) {
                    if (input != null) {
                        registeredInputs.remove(input);
                        input.setShowSoftInputOnFocus(true);
                    }
                }
            }
        });
    }

    public void setPhoneConnected(boolean connected) {
        mainHandler.post(() -> {
            phoneConnected = false;

            Log.d("RemoteInput", "Disconnected; restoring registered inputs");

            synchronized (registeredInputs) {
                for (EditText input : registeredInputs) {
                    if (input != null) {
                        input.setShowSoftInputOnFocus(true);
                    }
                }
            }
        });
    }

    private void applyKeyboardMode(EditText input) {
        input.setShowSoftInputOnFocus(!phoneConnected);

        if (phoneConnected) {
            hideKeyboard(input);
        } else if (input.hasFocus()) {
            input.post(() -> {
                if (!phoneConnected && input.hasFocus()
                        && input.isAttachedToWindow()) {
                    input.requestFocus();

                    InputMethodManager imm = (InputMethodManager)
                            input.getContext().getSystemService(
                                    Context.INPUT_METHOD_SERVICE);

                    if (imm != null) {
                        imm.showSoftInput(
                                input,
                                InputMethodManager.SHOW_FORCED
                        );
                    }
                }
            });
        }
    }



    private void hideKeyboard(EditText input) {
        InputMethodManager imm = (InputMethodManager)
                input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }


    @Override
    public void onRemoteTextReceived(String text) {
        mainHandler.post(() -> {

            Activity activity = application.getCurrentActivity();

            if (activity == null || activity.isFinishing()
                    || activity.isDestroyed()) {
                return;
            }

            View focusedView = activity.getCurrentFocus();

            if (!(focusedView instanceof EditText)) {
                Log.d("RemoteInput", "No focused EditText found");
                return;
            }

            EditText activeInput = (EditText) focusedView;

            activeInput.setText(text);
            activeInput.setSelection(activeInput.length());
        });
    }


}
