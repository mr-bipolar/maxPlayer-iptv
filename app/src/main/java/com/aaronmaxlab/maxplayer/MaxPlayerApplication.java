package com.aaronmaxlab.maxplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

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

        registerActivityLifecycleCallbacks(
                new ActivityLifecycleCallbacks() {

                    @Override
                    public void onActivityResumed(Activity activity) {
                        currentActivity = activity;
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (currentActivity == activity) {
                            currentActivity = null;
                        }
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (currentActivity == activity) {
                            currentActivity = null;
                        }
                    }

                    @Override
                    public void onActivityCreated(
                            Activity activity, Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            Activity activity, Bundle outState) {
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
}