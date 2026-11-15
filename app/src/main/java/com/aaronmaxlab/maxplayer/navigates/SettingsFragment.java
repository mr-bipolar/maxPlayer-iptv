package com.aaronmaxlab.maxplayer.navigates;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.ThemePreferences;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.keyboard.RemoteKeyboardActivity;
import com.aaronmaxlab.maxplayer.keyboard.RemotePairingActivity;
import com.aaronmaxlab.maxplayer.player.PlayerEngineType;
import com.aaronmaxlab.maxplayer.player.PlayerSettings;
import com.aaronmaxlab.maxplayer.playlist.PlaylistViewPreferences;
import com.aaronmaxlab.maxplayer.remote.RemoteKeyController;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.ThemeTypography;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared settings surface used by the phone navigation graph and the TV SettingsActivity. */
public class SettingsFragment extends Fragment {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RemoteKeyController remoteKeys;
    private TextView themeSummary;
    private TextView engineSummary;
    private TextView subtitleSummary;
    private TextView remoteSummary;
    private TextView playlistViewSummary;
    private AlertDialog themeDialog;

    public SettingsFragment() { }


    private View tvRoot;
    private boolean isTV;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        isTV = DeviceUtils.isTv(requireContext());

        View root = inflater.inflate( R.layout.fragment_settings, container, false);

        LinearLayout appearance = root.findViewById(R.id.settings_appearance_card);
        LinearLayout player = root.findViewById(R.id.settings_player_card);
        LinearLayout library = root.findViewById(R.id.settings_library_card);
        LinearLayout about = root.findViewById(R.id.settings_about_card);

        backPress(root.findViewById(R.id.settings_back));
        remoteKeys =
                ((MaxPlayerApplication) requireContext().getApplicationContext())
                        .getRemoteKeyController();

        populateAppearance(appearance);
        populatePlayer(player);
        populateLibrary(library, isTV);
        populateAbout(about);

        if (isTV) {
            tvRoot = root;
            setupTvTabs(root);
        }

        updateThemeSummary();
        updatePlayerSummaries();
        updateRemoteSummary();

        return root;
    }

    private  void backPress(View view){
        ClickUtils.applyTouchEffect(view, v -> requireActivity().finish());
    }

    private void populateAppearance(LinearLayout appearance) {
        View themeRow = addRow(
                appearance,
                R.drawable.ic_theme,
                R.string.settings_theme,
                R.string.settings_theme_summary,
                true,
                v -> showThemePicker()
        );

        themeSummary = themeRow.findViewById(R.id.settings_row_summary);
    }

    private void populatePlayer(LinearLayout player) {
        View engineRow = addRow(
                player,
                R.drawable.ic_decoder,
                R.string.settings_engine,
                R.string.settings_engine_summary,
                true,
                v -> showEnginePicker()
        );
        engineSummary = engineRow.findViewById(R.id.settings_row_summary);

        View subtitleRow = addRow(
                player,
                R.drawable.ic_subtitles,
                R.string.settings_subtitle_size,
                R.string.settings_subtitle_summary,
                true,
                v -> showSubtitleSizePicker()
        );
        subtitleSummary = subtitleRow.findViewById(R.id.settings_row_summary);
    }

    private void populateLibrary(LinearLayout library, boolean isTV) {
        addRow(
                library,
                R.drawable.ic_clock,
                R.string.settings_clear_history,
                R.string.settings_clear_history_summary,
                true,
                v -> confirmClearHistory()
        );

        if (!isTV) {
            View playlistViewRow = addRow(
                    library,
                    R.drawable.ic_clock,
                    R.string.settings_playlist_view,
                    R.string.settings_playlist_view_list,
                    true,
                    v -> showPlaylistViewPicker()
            );

            playlistViewSummary = playlistViewRow.findViewById(
                    R.id.settings_row_summary
            );

            updatePlaylistViewSummary();
        }

        View remoteRow = addRow(
                library,
                R.drawable.ic_remote,
                R.string.settings_remote,
                R.string.settings_remote_summary,
                true,
                v -> startActivity(new Intent(
                        requireContext(),
                        isTV ? RemotePairingActivity.class : RemoteKeyboardActivity.class
                ))
        );

        remoteSummary = remoteRow.findViewById(R.id.settings_row_summary);
    }

    private void populateAbout(LinearLayout about) {
        View versionRow = addRow(
                about,
                R.drawable.ic_info,
                R.string.app_name,
                R.string.settings_version_summary,
                false,
                null
        );

        ((TextView) versionRow.findViewById(R.id.settings_row_summary))
                .setText(getVersionSummary());

        addRow(
                about,
                R.drawable.ic_info,
                R.string.settings_credits,
                R.string.settings_credits_summary,
                true,
                v -> showPlaybackCredits()
        );

        addRow(
                about,
                R.drawable.ic_info,
                R.string.settings_privacy_policy,
                R.string.settings_privacy_policy_summary,
                true,
                v -> openPrivacyPolicy()
        );

        addRow(
                about,
                R.drawable.ic_info,
                R.string.settings_share_app,
                R.string.settings_share_app_summary,
                true,
                v -> shareApp()
        );
    }

    private void setupTvTabs(View root) {

        root.findViewById(R.id.tabAppearance).setOnClickListener(v ->
                showSettingsTab(root, 0));

        root.findViewById(R.id.tabPlayer).setOnClickListener(v ->
                showSettingsTab(root, 1));

        root.findViewById(R.id.tabLibrary).setOnClickListener(v ->
                showSettingsTab(root, 2));

        root.findViewById(R.id.tabAbout).setOnClickListener(v ->
                showSettingsTab(root, 3));

        showSettingsTab(root, 0);

    }


    private void showSettingsTab(View root, int selectedTab) {

        int[] tabIds = {
                R.id.tabAppearance,
                R.id.tabPlayer,
                R.id.tabLibrary,
                R.id.tabAbout
        };

        int[] contentIds = {
                R.id.contentAppearance,
                R.id.contentPlayer,
                R.id.contentLibrary,
                R.id.contentAbout
        };

        for (int i = 0; i < tabIds.length; i++) {

            View tab = root.findViewById(tabIds[i]);

            tab.setSelected(i == selectedTab);

            if (tab instanceof TextView) {
                ((TextView) tab).setTextColor(
                        requireContext().getColor(
                                i == selectedTab
                                        ? R.color.accent
                                        : R.color.ink_dim
                        )
                );
            }

            root.findViewById(contentIds[i]).setVisibility(
                    i == selectedTab ? View.VISIBLE : View.GONE
            );
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        updateThemeSummary();
        updatePlayerSummaries();
        updateRemoteSummary();
    }



    private View addRow(LinearLayout parent, @DrawableRes int icon, @StringRes int title,
                        @StringRes int summary, boolean interactive, View.OnClickListener listener) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_settings_row, parent, false);
        ((ImageView) row.findViewById(R.id.settings_row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.settings_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.settings_row_summary)).setText(summary);
        row.setClickable(interactive);
        row.setFocusable(interactive);
        if (listener != null) row.setOnClickListener(listener);
        if (!interactive) row.findViewById(R.id.settings_row_chevron).setVisibility(View.GONE);
        parent.addView(row);
        return row;
    }

    private void updateThemeSummary() {
        if (themeSummary == null || !isAdded()) return;
        String selected = ThemePreferences.getSelected(requireContext());
        int label = ThemePreferences.SYSTEM.equals(selected) ? R.string.theme_system
                : ThemePreferences.DARK.equals(selected) ? R.string.theme_dark_choice
                : R.string.theme_primary_choice;
        themeSummary.setText(label);
    }

    private void showThemePicker() {
        String current = ThemePreferences.getSelected(requireContext());
        LinearLayout options = new LinearLayout(requireContext());
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(18), dp(8), dp(18), dp(8));
        addThemeOption(options, ThemePreferences.SYSTEM, R.string.theme_system,
                R.string.theme_system_summary, new int[]{0xFF272E3D, 0xFF323B4E, 0xFFCC5200}, current);
        addThemeOption(options, ThemePreferences.PRIMARY, R.string.theme_primary_choice,
                R.string.theme_primary_summary, new int[]{0xFF272E3D, 0xFF323B4E, 0xFFCC5200}, current);
        addThemeOption(options, ThemePreferences.DARK, R.string.theme_dark_choice,
                R.string.theme_dark_summary, new int[]{0xFF0A0A0C, 0xFF17181D, 0xFFF5A524}, current);
        themeDialog = new AlertDialog.Builder(requireContext(), R.style.PlayerDialogTheme)
                .setTitle(R.string.settings_theme)
                .setView(options)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        themeDialog.show();

        remoteKeys.registerDialog(themeDialog);
        themeDialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(themeDialog));

    }

    private void addThemeOption(LinearLayout parent, String key, @StringRes int title,
                                @StringRes int description, int[] colors, String current) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(12), dp(10), dp(12));
        row.setBackgroundResource(R.drawable.bg_settings_row);
        row.setFocusable(true);
        row.setClickable(true);

        View swatch = new View(requireContext());
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        gradient.setCornerRadius(dp(9));
        gradient.setStroke(dp(1), requireContext().getColor(R.color.line));
        swatch.setBackground(gradient);
        row.addView(swatch, new LinearLayout.LayoutParams(dp(46), dp(34)));

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(13), 0, dp(10), 0);
        row.addView(textColumn, textParams);
        TextView heading = new TextView(requireContext());
        heading.setText(title);
        heading.setTextColor(requireContext().getColor(R.color.ink));
        heading.setTextSize(15);
        heading.setTypeface(ThemeTypography.bold(requireContext()));
        textColumn.addView(heading);
        TextView detail = new TextView(requireContext());
        detail.setText(description);
        detail.setTextColor(requireContext().getColor(R.color.ink_dim));
        detail.setTextSize(12);
        textColumn.addView(detail);

        boolean selected = key.equals(current);
        TextView check = new TextView(requireContext());
        check.setText(selected ? "✓ Selected" : "Select");
        check.setTextColor(requireContext().getColor(selected ? R.color.primary_highlight : R.color.ink_faint));
        check.setTextSize(14);
        row.addView(check);
        row.setContentDescription(getString(title) + (selected ? ", " + getString(R.string.selected) : ""));
        row.setOnClickListener(v -> {
            if (themeDialog != null) themeDialog.dismiss();
            ThemePreferences.setSelected(requireContext(), key);
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void updatePlayerSummaries() {
        if (!isAdded()) return;
        if (engineSummary != null) {
            PlayerEngineType engine = PlayerSettings.getEngine(requireContext());
            engineSummary.setText(engine == PlayerEngineType.MPV
                    ? R.string.player_engine_mpv : R.string.player_engine_media3);
        }
        if (subtitleSummary != null) {
            String[] labels = getResources().getStringArray(R.array.settings_subtitle_sizes);
            int index = Math.max(0, Math.min(PlayerSettings.getSubtitleSizeIndex(requireContext()), labels.length - 1));
            subtitleSummary.setText(getString(R.string.settings_subtitle_summary_format, labels[index]));
        }
    }

    private void showSubtitleSizePicker() {
        String[] labels = getResources().getStringArray(R.array.settings_subtitle_sizes);
        int selected = PlayerSettings.getSubtitleSizeIndex(requireContext());
        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(),
                R.style.PlayerDialogTheme
        )
                .setTitle(R.string.settings_subtitle_size)
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    PlayerSettings.setSubtitleSizeIndex(requireContext(), which);
                    updatePlayerSummaries();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));

    }

    private void showEnginePicker() {
        PlayerEngineType current = PlayerSettings.getEngine(requireContext());

        CharSequence[] labels = {
                getString(R.string.player_engine_media3),
                getString(R.string.player_engine_mpv)
        };

        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(),
                R.style.PlayerDialogTheme)
                .setTitle(R.string.settings_engine)
                .setSingleChoiceItems(labels, current.ordinal(), (d, which) -> {
                    PlayerSettings.setEngine(
                            requireContext(),
                            PlayerEngineType.values()[which]
                    );
                    updatePlayerSummaries();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        ListView listView = dialog.getListView();
        Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        if (listView != null && cancelButton != null) {
            cancelButton.setFocusable(true);
            cancelButton.setFocusableInTouchMode(true);

            listView.setSelection(current.ordinal());

            // Connect the last choice row to the Cancel button.
            listView.post(() -> {
                int lastPosition = listView.getCount() - 1;
                int lastChildPosition = lastPosition - listView.getFirstVisiblePosition();

                View lastRow = listView.getChildAt(lastChildPosition);

                if (lastRow != null) {
                    lastRow.setNextFocusDownId(cancelButton.getId());
                }
            });
        }

        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));
    }


    private void updateRemoteSummary() {
        if (remoteSummary == null || !isAdded()) return;
        MaxPlayerApplication app = (MaxPlayerApplication) requireActivity().getApplication();
        boolean connected = app.getRemoteInputManager() != null
                && app.getRemoteInputManager().isClientConnected();
        remoteSummary.setText(connected ? R.string.settings_remote_connected
                : R.string.settings_remote_disconnected);
    }

    private void confirmClearHistory() {
        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(),
                R.style.PlayerDialogTheme)
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_clear, (d, which) -> clearHistory())
                .create();

        dialog.show();

        Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancelButton != null) {
            cancelButton.setFocusable(true);
            cancelButton.setFocusableInTouchMode(true);
            cancelButton.requestFocus();
        }

        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));
    }

    private void clearHistory() {
        android.content.Context appContext = requireContext().getApplicationContext();
        ioExecutor.execute(() -> {
            boolean cleared;
            SqlDbHelper db = new SqlDbHelper(appContext);
            try {
                cleared = db.clearHistory();
            } catch (Exception e) {
                cleared = false;
            } finally {
                db.close();
            }
            boolean result = cleared;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), result ? R.string.settings_history_cleared
                        : R.string.settings_history_clear_failed, Toast.LENGTH_SHORT).show();
            });
        });
    }


    private void showPlaybackCredits() {

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_playback_credits);

        TextView linkMpvAndroid = dialog.findViewById(R.id.linkMpvAndroid);
        TextView linkMpvEngine = dialog.findViewById(R.id.linkMpvEngine);
        Button btnClose = dialog.findViewById(R.id.btnCloseCredits);

        linkMpvAndroid.setOnClickListener(v ->
                openCreditUrl(
                        "https://github.com/mpv-android/mpv-android/releases/tag/2026-09-17"
                )
        );

        linkMpvEngine.setOnClickListener(v ->
                openCreditUrl(
                        "https://github.com/mr-bipolar/mpv-android-engine"
                )
        );

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Show dialog once
        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);

            boolean isTV = DeviceUtils.isTv(requireContext());

            float density = getResources().getDisplayMetrics().density;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;

            int width = isTV
                    ? (int) (480 * density)
                    : (int) (screenWidth * 0.90f);

            window.setLayout(
                    Math.min(width, screenWidth - (int) (48 * density)),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));
    }

    private void openCreditUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppToast.warning(requireActivity(), "Unable to open link");
        }
    }



    private String getVersionSummary() {
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            return getString(R.string.settings_version_summary_format,
                    version == null ? getString(R.string.unknown) : version);
        } catch (Exception e) {
            return getString(R.string.settings_version_unknown);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }


    private void openPrivacyPolicy() {
        String url = getString(R.string.privacy_policy_url);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppToast.warning(requireActivity(), "Unable to open privacy policy");
        }
    }

    private void shareApp() {
        String appName = getString(R.string.app_name);

        String playStoreUrl = "https://play.google.com/store/apps/details?id="
                + requireContext().getPackageName();

        String message = appName + "\n\n"
                + "Download the app:\n" + playStoreUrl;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, appName);
        intent.putExtra(Intent.EXTRA_TEXT, message);

        startActivity(Intent.createChooser(intent, "Share app"));
    }

    private void showPlaylistViewPicker() {

        String[] options = {
                getString(R.string.settings_playlist_view_list),
                getString(R.string.settings_playlist_view_grid)
        };

        boolean current = PlaylistViewPreferences.isGridEnabled(requireContext());

        new AlertDialog.Builder(requireContext(), R.style.PlayerDialogTheme)
                .setTitle(R.string.settings_playlist_view)
                .setSingleChoiceItems(options, current ? 1 : 0, (dialog, which) -> {

                    PlaylistViewPreferences.setGridEnabled(
                            requireContext(),
                            which == 1
                    );

                    updatePlaylistViewSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updatePlaylistViewSummary() {
        if (playlistViewSummary == null || !isAdded()) return;

        playlistViewSummary.setText(
                PlaylistViewPreferences.isGridEnabled(requireContext())
                        ? R.string.settings_playlist_view_grid
                        : R.string.settings_playlist_view_list
        );
    }

    @Override
    public void onDestroy() {
        if (themeDialog != null && themeDialog.isShowing()) themeDialog.dismiss();
        ioExecutor.shutdown();
        super.onDestroy();
    }

}
