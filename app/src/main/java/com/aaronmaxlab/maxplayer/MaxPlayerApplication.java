package com.aaronmaxlab.maxplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.aaronmaxlab.maxplayer.remote.RemoteInputController;
import com.aaronmaxlab.maxplayer.remote.RemoteInputManager;
import com.aaronmaxlab.maxplayer.remote.RemoteKeyController;

public class MaxPlayerApplication extends Application {

    private RemoteInputManager remoteInputManager;
    private RemoteInputController remoteInputController;
    private RemoteKeyController remoteKeyController;

    private Activity currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();

        ThemePreferences.applySaved(this);

        registerActivityLifecycleCallbacks(
                new ActivityLifecycleCallbacks() {

                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        currentActivity = activity;
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                        if (currentActivity == activity) {
                            currentActivity = null;
                        }
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        if (currentActivity == activity) {
                            currentActivity = null;
                        }
                    }

                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity, Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity, @NonNull Bundle outState) {
                    }
                }
        );

        remoteInputManager = new RemoteInputManager();
        remoteInputController = new RemoteInputController(this);
        remoteInputManager.setInputListener(remoteInputController);
        remoteKeyController = new RemoteKeyController(this);
        remoteInputManager.setKeyListener(remoteKeyController);
    }

    public RemoteInputManager getRemoteInputManager() {
        return remoteInputManager;
    }

    public RemoteInputController getRemoteInputController() {
        return remoteInputController;
    }

    public Activity getCurrentActivity() {
        return currentActivity;
    }

    public RemoteKeyController getRemoteKeyController() {
        return remoteKeyController;
    }
}
