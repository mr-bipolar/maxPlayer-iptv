package com.aaronmaxlab.maxplayer;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.aaronmaxlab.maxplayer.databinding.ActivityMainBinding;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

public class MainActivity extends AppCompatActivity {

    private   ActivityMainBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //  Enable EdgeToEdge before setting content view
        EdgeToEdge.enable(this);

        //  Inflate layout after super.onCreate()
         binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);


        //  Fix WindowInsets padding (prevents double bottom height/extra white space)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        //  Connect Navigation Controller to BottomNavigationView
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        //  Adjust gap between Icon and Text label dynamically
       UIUtils.adjustBottomNavGap(binding.bottomNavigation);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

