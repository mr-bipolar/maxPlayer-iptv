package com.aaronmaxlab.maxplayer.playlist;


import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.DialogSaveChannelBinding;
import com.aaronmaxlab.maxplayer.remote.RemoteKeyController;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;

import java.util.List;

public final class SaveChannelDialog {

    public interface OnSaveListener {
        void onSave(@NonNull String category);
    }

    private SaveChannelDialog() {}

    /** Shows the dialog. Keep the returned reference to dismiss it in onDestroy. */
    @NonNull
    public static AlertDialog show(@NonNull Context context,
                                   @NonNull String channelName,
                                   @NonNull List<String> categories,
                                   @Nullable String preselected,
                                   @NonNull OnSaveListener listener) {

        DialogSaveChannelBinding b =
                DialogSaveChannelBinding.inflate(LayoutInflater.from(context));

        final String[] selected = {preselected};

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.PlayerDialogTheme)
                .setView(b.getRoot())
                .create();

        b.txtChannelName.setText(channelName);
        render(context, b, selected[0]);

        // tap the box -> choose one category in a second window
        b.categoryField.setOnClickListener(v -> {
            CharSequence[] items = categories.toArray(new CharSequence[0]);
            int checked = selected[0] == null ? -1 : categories.indexOf(selected[0]);

            AlertDialog pickerDialog = new AlertDialog.Builder(context, R.style.PlayerDialogTheme)
                    .setTitle(R.string.save_channel_choose_category)
                    .setSingleChoiceItems(items, checked, (picker, which) -> {
                        selected[0] = categories.get(which);
                        render(context, b, selected[0]);
                        picker.dismiss();
                    })
                    .create();
            pickerDialog.show();
            style(context, pickerDialog);
        });

        b.btnCancel.setOnClickListener(v -> dialog.dismiss());
        b.btnSubmit.setOnClickListener(v -> {
            if (selected[0] == null) return;
            listener.onSave(selected[0]);
            dialog.dismiss();
        });

        dialog.show();
        style(context, dialog);
        if (DeviceUtils.isTv(context)) {
            b.categoryField.post(b.categoryField::requestFocus);

            RemoteKeyController remoteKeys =
                    ((MaxPlayerApplication) context.getApplicationContext())
                            .getRemoteKeyController();

            remoteKeys.registerDialog(dialog);
            dialog.setOnDismissListener(d ->
                    remoteKeys.unregisterDialog(dialog)
            );
        }
        return dialog;
    }


    public static void showCategoryPicker(
            @NonNull Context context,
            @NonNull List<String> categories,
            int preselectedIndex,
            @NonNull OnCategorySelectedListener listener
    ) {
        AlertDialog dialog = new AlertDialog.Builder(
                context,
                R.style.PlayerDialogTheme
        )
                .setTitle(R.string.save_channel_choose_category)
                .setSingleChoiceItems(
                        categories.toArray(new CharSequence[0]),
                        preselectedIndex,
                        (dialogInterface, which) -> {
                            listener.onSelected(which);
                            dialogInterface.dismiss();
                        }
                )
                .create();

        RemoteKeyController remoteKeys =
                ((MaxPlayerApplication) context.getApplicationContext())
                        .getRemoteKeyController();

        dialog.setOnDismissListener(d ->
                remoteKeys.unregisterDialog(dialog)
        );

        dialog.show();

        remoteKeys.registerDialog(dialog);

        // Focus the selected category in the TV picker.
        dialog.getListView().post(() -> {
            dialog.getListView().requestFocus();

            if (preselectedIndex >= 0) {
                dialog.getListView().setSelection(preselectedIndex);
            }
        });
    }

    public interface OnCategorySelectedListener {
        void onSelected(int index);
    }



    private static void render(Context c, DialogSaveChannelBinding b, @Nullable String category) {
        boolean has = category != null;

        b.txtCategory.setText(has ? category : c.getString(R.string.save_channel_category_hint));
        b.txtCategory.setTextColor(androidx.core.content.ContextCompat.getColor(
                c, has ? R.color.ink : R.color.white_alpha_45));

        b.btnSubmit.setEnabled(has);
        b.btnSubmit.setAlpha(has ? 1f : 0.4f);
    }

    private static void style(@NonNull Context c, @NonNull AlertDialog d) {
        boolean tv = DeviceUtils.isTv(c);
        Window window = d.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(tv
                    ? R.drawable.bg_tv_dialog_panel : R.drawable.bg_dialog_black);
            if (tv) {
                DisplayMetrics metrics = c.getResources().getDisplayMetrics();
                int margin = Math.round(32 * metrics.density);
                int preferred = Math.round(600 * metrics.density);
                int width = Math.max(Math.round(320 * metrics.density),
                        Math.min(preferred, metrics.widthPixels - margin * 2));
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
        TextView title = d.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) {
            title.setTextColor(ContextCompat.getColor(c, R.color.primary_highlight));
            title.setTypeface(com.aaronmaxlab.maxplayer.utils.ThemeTypography.bold(c));
        }
    }
}
