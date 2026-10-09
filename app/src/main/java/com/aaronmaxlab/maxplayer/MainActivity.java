package com.aaronmaxlab.maxplayer;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.aaronmaxlab.maxplayer.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //  Enable EdgeToEdge before setting content view
        EdgeToEdge.enable(this);

        //  Inflate layout after super.onCreate()
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //  Control navigation bar icon contrast
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(false);




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
        adjustBottomNavGap(binding.bottomNavigation);
    }

    private void adjustBottomNavGap(BottomNavigationView bottomNav) {
        bottomNav.post(() -> {
            View menuView = bottomNav.getChildAt(0);
            if (menuView instanceof ViewGroup) {
                ViewGroup menuViewGroup = (ViewGroup) menuView;
                for (int i = 0; i < menuViewGroup.getChildCount(); i++) {
                    View itemView = menuViewGroup.getChildAt(i);
                    View labelGroup = itemView.findViewById(com.google.android.material.R.id.navigation_bar_item_labels_group);
                    if (labelGroup != null) {
                        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) labelGroup.getLayoutParams();

                        // Convert 4dp to pixels dynamically
                        params.topMargin = (int) (4 * getResources().getDisplayMetrics().density);

                        labelGroup.setLayoutParams(params);
                    }
                }
            }
        });
    }
}

