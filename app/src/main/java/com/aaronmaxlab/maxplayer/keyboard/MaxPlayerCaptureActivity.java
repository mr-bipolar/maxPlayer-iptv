package com.aaronmaxlab.maxplayer.keyboard;

import android.content.pm.ActivityInfo;
import android.os.Bundle;


import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.journeyapps.barcodescanner.CaptureActivity;


public class MaxPlayerCaptureActivity extends CaptureActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        );
        UIUtils.setLightNavigationBars(this, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.setLightNavigationBars(this, false);
    }


}