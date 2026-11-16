package com.aaronmaxlab.maxplayer.database.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocalDbHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface Listener {
        void onHistoryClick(LocalDbHistory item, boolean resume);
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();

    public LocalDbHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<LocalDbHistory> historyList) {
        rows.clear();

        if (historyList != null) {
            Calendar previousDay = null;

            for (LocalDbHistory item : historyList) {
                long timestamp = item.getCreatedAt();

                Calendar itemDay = Calendar.getInstance();
                itemDay.setTimeInMillis(timestamp);

                if (previousDay == null || !sameDay(previousDay, itemDay)) {
                    rows.add(Row.header(getDateLabel(timestamp)));
                    previousDay = (Calendar) itemDay.clone();
                }

                rows.add(Row.item(item));
            }
        }

        notifyDataSetChanged();
    }



    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(
                    R.layout.item_history_header, parent, false);
            return new HeaderHolder(view);
        }

        View view = inflater.inflate(
                R.layout.item_history, parent, false);
        return new HistoryHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder, int position) {

        Row row = rows.get(position);

        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(row.header);
        } else if (holder instanceof HistoryHolder) {
            ((HistoryHolder) holder).bind(row.item);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR)
                == second.get(Calendar.DAY_OF_YEAR);
    }

    private static String getDateLabel(long timestamp) {
        Calendar item = Calendar.getInstance();
        item.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();

        if (sameDay(item, today)) {
            return "Today";
        }

        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (sameDay(item, yesterday)) {
            return "Yesterday";
        }

        long itemDay = getDayStart(timestamp);
        long todayDay = getDayStart(System.currentTimeMillis());
        long daysAgo = (todayDay - itemDay) / (24L * 60 * 60 * 1000);

        if (daysAgo >= 2 && daysAgo <= 6) {
            return daysAgo + " days ago";
        }

        return new SimpleDateFormat(
                "EEE, d MMM", Locale.getDefault()
        ).format(new Date(timestamp));
    }

    private static long getDayStart(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(
                    Locale.getDefault(), "%d:%02d:%02d",
                    hours, minutes, seconds);
        }

        return String.format(
                Locale.getDefault(), "%d:%02d",
                minutes, seconds);
    }

    private static class Row {
        final String header;
        final LocalDbHistory item;

        private Row(String header, LocalDbHistory item) {
            this.header = header;
            this.item = item;
        }

        static Row header(String title) {
            return new Row(title, null);
        }

        static Row item(LocalDbHistory item) {
            return new Row(null, item);
        }

        boolean isHeader() {
            return item == null;
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.historyGroupTitle);
        }
    }


    class HistoryHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView title;
        final TextView type;
        final TextView subtitle;
        final ProgressBar progress;
        final View resumeButton;
        final View playButton;

        HistoryHolder(View itemView) {
            super(itemView);

            root = itemView.findViewById(R.id.historyRow);
            icon = itemView.findViewById(R.id.mediaIcon);
            title = itemView.findViewById(R.id.mediaTitle);
            type = itemView.findViewById(R.id.mediaType);
            subtitle = itemView.findViewById(R.id.historySubtitle);
            progress = itemView.findViewById(R.id.historyProgress);
            resumeButton = itemView.findViewById(R.id.historyResumeButton);
            playButton = itemView.findViewById(R.id.historyPlayButton);
        }

        void bind(LocalDbHistory item) {
            String mediaType = item.getHistoryType();
            if (mediaType == null) {
                mediaType = "";
            }

            // Reset recycled views before binding this item.
            progress.setMax(100);
            progress.setProgress(0);
            progress.setVisibility(View.GONE);
            resumeButton.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);

            title.setText(UIUtils.toCapitalize(item.getHistoryName()));
            type.setText(mediaType);

            long positionMs = Math.max(0L, item.getHistoryPosition());
            long rawDurationMs = item.getHistoryDuration();

            boolean durationKnown = rawDurationMs > 0;
            long durationMs = durationKnown ? rawDurationMs : 0L;

            boolean isVod = "VOD".equalsIgnoreCase(mediaType);

            // A VOD is unfinished when it has a saved position
            // and has not reached 95% of its known duration.
            boolean unfinished = isVod
                    && positionMs > 0
                    && (!durationKnown
                    || positionMs < durationMs * 0.95);

            if (unfinished) {
                if (durationKnown) {
                    subtitle.setText(String.format(
                            Locale.getDefault(),
                            "%s / %s",
                            formatTime(positionMs),
                            formatTime(durationMs)
                    ));

                    int progressPercent = (int) Math.min(
                            100L,
                            positionMs * 100L / durationMs
                    );

                    progress.setProgress(progressPercent);
                    progress.setVisibility(View.VISIBLE);
                } else {
                    subtitle.setText(formatTime(positionMs));
                    progress.setProgress(0);
                    progress.setVisibility(View.GONE);
                }

                resumeButton.setVisibility(View.VISIBLE);
                playButton.setVisibility(View.GONE);
            } else {
                subtitle.setText(
                        item.getHistoryTag() == null
                                ? ""
                                : item.getHistoryTag()
                );

                progress.setProgress(0);
                progress.setVisibility(View.GONE);
                resumeButton.setVisibility(View.GONE);
                playButton.setVisibility(View.VISIBLE);
            }

            // Media type styling.
            if ("LIVE".equalsIgnoreCase(mediaType)) {
                icon.setImageResource(R.drawable.ic_tv);
                type.setBackgroundResource(R.drawable.bg_tag_live);
                type.setTextColor(
                        itemView.getContext().getColor(R.color.live)
                );
            } else if ("AUDIO".equalsIgnoreCase(mediaType)) {
                icon.setImageResource(R.drawable.ic_radio);
                type.setBackgroundResource(R.drawable.bg_tag_audio);
                type.setTextColor(
                        itemView.getContext().getColor(R.color.primary_highlight)
                );
            } else if("VOD".equalsIgnoreCase(mediaType)){
                icon.setImageResource(R.drawable.ic_play);
                type.setBackgroundResource(R.drawable.bg_tag_vod);
                type.setTextColor(
                        itemView.getContext().getColor(R.color.ok)
                );

            } else {
                icon.setImageResource(R.drawable.ic_cat_movies);
                type.setBackgroundResource(R.drawable.bg_tag_vod);
                type.setTextColor(
                        itemView.getContext().getColor(R.color.ink_dim)
                );
            }

            // Use the current item and its current unfinished state.
            final boolean canResume = unfinished;

            ClickUtils.applyTouchEffect(root, view -> {
                listener.onHistoryClick(item, canResume);
            });

            ClickUtils.applyTouchEffect(resumeButton, view -> {
                listener.onHistoryClick(item, true);
            });

            ClickUtils.applyTouchEffect(playButton, view -> {
                listener.onHistoryClick(item, false);
            });
        }
    }

}
