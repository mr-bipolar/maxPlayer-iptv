
package com.aaronmaxlab.maxplayer;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.maxplayer.mpv.MpvPlayer;
import com.maxplayer.mpv.MpvPlayerConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void mpvEngineInitializesAndReleases() {
        Context context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        MpvPlayer player = MpvPlayer.create(
                context,
                MpvPlayerConfig.defaults()
        );

        assertNotNull(player);

        try {
            player.initialize();
        } finally {
            player.release();
        }
    }
}
