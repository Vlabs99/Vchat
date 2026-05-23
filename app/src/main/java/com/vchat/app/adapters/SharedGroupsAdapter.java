package com.vchat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vchat.app.R;
import com.vchat.app.models.ChatModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class SharedGroupsAdapter extends RecyclerView.Adapter<SharedGroupsAdapter.Holder> {
    public interface OnGroupClickListener { void onGroupClick(ChatModel group); }
    private final List<ChatModel> groups = new ArrayList<>();
    private final OnGroupClickListener listener;
    private String currentUid = "";

    public SharedGroupsAdapter(OnGroupClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setCurrentUid(String uid) {
        currentUid = uid == null ? "" : uid;
    }

    public void submit(List<ChatModel> values) {
        List<ChatModel> next = values == null ? new ArrayList<>() : new ArrayList<>(values);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return groups.size(); }

            @Override
            public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = groups.get(oldItemPosition).getChatId();
                String newId = next.get(newItemPosition).getChatId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatModel oldG = groups.get(oldItemPosition);
                ChatModel newG = next.get(newItemPosition);
                return safe(oldG.getChatName()).equals(safe(newG.getChatName()))
                        && safe(oldG.getLastMessage()).equals(safe(newG.getLastMessage()))
                        && oldG.getLastMessageTimestamp() == newG.getLastMessageTimestamp()
                        && isUnread(oldG) == isUnread(newG);
            }
        });
        groups.clear();
        groups.addAll(next);
        diff.dispatchUpdatesTo(this);
    }
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_group, parent, false));
    }
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatModel g = groups.get(position);
        holder.tvName.setText(g.getChatName().isEmpty() ? "Group" : g.getChatName());
        String last = g.getLastMessage();
        if (last == null || last.trim().isEmpty()) {
            holder.tvSub.setText("No messages yet");
        } else {
            holder.tvSub.setText(last + "  •  " + TimeUtils.getFormattedTime(g.getLastMessageTimestamp()));
        }
        holder.tvUnread.setVisibility(isUnread(g) ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onGroupClick(g));
    }
    @Override
    public int getItemCount() { return groups.size(); }

    @Override
    public long getItemId(int position) {
        ChatModel g = groups.get(position);
        return g.getChatId() == null ? position : g.getChatId().hashCode();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvName, tvSub, tvUnread;
        Holder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_group_name);
            tvSub = itemView.findViewById(R.id.tv_group_subtitle);
            tvUnread = itemView.findViewById(R.id.tv_group_unread);
        }
    }

    private boolean isUnread(ChatModel g) {
        if (g == null || currentUid.isEmpty()) return false;
        return Boolean.TRUE.equals(g.getGroupUnreadBy().get(currentUid))
                || Boolean.TRUE.equals(g.getUnreadBy().get(currentUid));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
