package com.vchat.app.adapters;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vchat.app.R;
import com.vchat.app.models.NotificationModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(String docId, NotificationModel model);
        void onMarkAllReadClick();
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MARK_ALL = 1;
    private static final int TYPE_ITEM = 2;

    private static final String ENTRY_NEW = "entry_new";
    private static final String ENTRY_EARLIER = "entry_earlier";
    private static final String ENTRY_MARK_ALL = "entry_mark_all";

    private final List<NotificationEntry> displayItems = new ArrayList<>();
    private final OnNotificationClickListener listener;

    public NotificationsAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<NotificationRow> rows) {
        List<NotificationEntry> next = buildEntries(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return displayItems.size(); }

            @Override
            public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return displayItems.get(oldItemPosition).stableId.equals(next.get(newItemPosition).stableId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                NotificationEntry oldEntry = displayItems.get(oldItemPosition);
                NotificationEntry newEntry = next.get(newItemPosition);
                if (oldEntry.type != newEntry.type) return false;
                if (oldEntry.type == TYPE_ITEM) {
                    NotificationModel oldN = oldEntry.model;
                    NotificationModel newN = newEntry.model;
                    return safe(oldN.getTitle()).equals(safe(newN.getTitle()))
                            && safe(oldN.getBody()).equals(safe(newN.getBody()))
                            && safe(oldN.getType()).equals(safe(newN.getType()))
                            && oldN.isRead() == newN.isRead()
                            && oldN.getCreatedAtMillis() == newN.getCreatedAtMillis();
                }
                return safe(oldEntry.label).equals(safe(newEntry.label));
            }
        });
        displayItems.clear();
        displayItems.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position).type;
    }

    @Override
    public long getItemId(int position) {
        return displayItems.get(position).stableId.hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER || viewType == TYPE_MARK_ALL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_header, parent, false);
            return new HeaderHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NotificationEntry entry = displayItems.get(position);
        if (entry.type == TYPE_HEADER) {
            HeaderHolder hh = (HeaderHolder) holder;
            hh.tvHeader.setText(entry.label);
            hh.tvHeader.setTextColor(hh.itemView.getResources().getColor(R.color.text_secondary));
            hh.itemView.setOnClickListener(null);
            return;
        }
        if (entry.type == TYPE_MARK_ALL) {
            HeaderHolder hh = (HeaderHolder) holder;
            hh.tvHeader.setText("Mark all as read");
            hh.tvHeader.setTextColor(hh.itemView.getResources().getColor(R.color.colorAccent));
            hh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMarkAllReadClick();
            });
            return;
        }

        NotificationModel item = entry.model;
        NotificationViewHolder vh = (NotificationViewHolder) holder;
        vh.tvBody.setText(item.getBody());
        vh.tvTime.setText(TimeUtils.getFormattedTime(item.getCreatedAtMillis()));
        vh.tvType.setText(getTypeLabel(item.getType()));
        vh.tvUnreadDot.setVisibility(item.isRead() ? View.GONE : View.VISIBLE);
        vh.itemView.setAlpha(item.isRead() ? 0.75f : 1f);

        String title = item.getTitle();
        SpannableStringBuilder builder = new SpannableStringBuilder(title);
        int split = title.indexOf(" ");
        if (split > 0) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        vh.tvTitle.setText(builder);

        vh.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNotificationClick(entry.docId, item);
        });
    }

    private String getTypeLabel(String type) {
        if (TextUtils.isEmpty(type)) return "UPDATE";
        if ("chat_request".equals(type)) return "REQUEST";
        if ("request_accepted".equals(type)) return "ACCEPTED";
        if ("group_role_change".equals(type)) return "ROLE";
        return "UPDATE";
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    private List<NotificationEntry> buildEntries(List<NotificationRow> rows) {
        List<NotificationEntry> entries = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean hasUnread = false;
        boolean addedNewHeader = false;
        boolean addedEarlierHeader = false;

        for (NotificationRow row : rows) {
            if (row == null || row.model == null) continue;
            if (!row.model.isRead()) hasUnread = true;
            boolean isNewUnread = !row.model.isRead() && (now - row.model.getCreatedAtMillis()) < 86400000L;

            if (isNewUnread && !addedNewHeader) {
                entries.add(NotificationEntry.header(ENTRY_NEW, "New"));
                if (hasUnread) entries.add(NotificationEntry.markAll(ENTRY_MARK_ALL));
                addedNewHeader = true;
            }
            if (!isNewUnread && !addedEarlierHeader) {
                entries.add(NotificationEntry.header(ENTRY_EARLIER, "Earlier"));
                addedEarlierHeader = true;
            }
            entries.add(NotificationEntry.item(row.docId, row.model));
        }
        return entries;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class NotificationRow {
        public final String docId;
        public final NotificationModel model;

        public NotificationRow(String docId, NotificationModel model) {
            this.docId = docId == null ? "" : docId;
            this.model = model;
        }
    }

    private static class NotificationEntry {
        final int type;
        final String stableId;
        final String label;
        final String docId;
        final NotificationModel model;

        private NotificationEntry(int type, String stableId, String label, String docId, NotificationModel model) {
            this.type = type;
            this.stableId = stableId;
            this.label = label;
            this.docId = docId;
            this.model = model;
        }

        static NotificationEntry header(String key, String label) {
            return new NotificationEntry(TYPE_HEADER, key, label, "", null);
        }

        static NotificationEntry markAll(String key) {
            return new NotificationEntry(TYPE_MARK_ALL, key, "Mark all as read", "", null);
        }

        static NotificationEntry item(String docId, NotificationModel model) {
            String id = TextUtils.isEmpty(docId) ? "row_" + model.getCreatedAtMillis() : "item_" + docId;
            return new NotificationEntry(TYPE_ITEM, id, "", docId, model);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_notification_header);
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvBody;
        TextView tvTime;
        TextView tvUnreadDot;
        TextView tvType;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notification_title);
            tvBody = itemView.findViewById(R.id.tv_notification_body);
            tvTime = itemView.findViewById(R.id.tv_notification_time);
            tvUnreadDot = itemView.findViewById(R.id.tv_unread_dot);
            tvType = itemView.findViewById(R.id.tv_notification_type);
        }
    }
}
