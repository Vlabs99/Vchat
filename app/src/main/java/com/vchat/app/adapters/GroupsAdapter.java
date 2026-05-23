package com.vchat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vchat.app.R;
import com.vchat.app.models.ChatModel;
import com.vchat.app.utils.TimeUtils;

import java.util.List;

public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {
    private static final String TAG = "GroupsAdapter";

    public interface OnGroupClickListener {
        void onClick(ChatModel group);
    }
    public interface OnGroupLongClickListener {
        void onLongClick(ChatModel group, View anchor);
    }

    private final List<ChatModel> groups;
    private String currentUid = "";
    private final OnGroupClickListener listener;
    private final OnGroupLongClickListener longClickListener;

    public GroupsAdapter(List<ChatModel> groups, OnGroupClickListener listener, OnGroupLongClickListener longClickListener) {
        this.groups = groups;
        this.listener = listener;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    public void setCurrentUid(String currentUid) {
        this.currentUid = currentUid == null ? "" : currentUid;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        ChatModel group = groups.get(position);
        String title = group.getChatName().trim().isEmpty() ? "Unnamed Group" : group.getChatName().trim();
        holder.tvGroupName.setText(title);
        holder.tvLastMessage.setText(group.getLastMessage().trim().isEmpty() ? "No messages yet" : group.getLastMessage());
        holder.tvTimestamp.setText(TimeUtils.getFormattedTime(group.getLastMessageTimestamp()));
        holder.ivAvatar.setImageResource(R.drawable.ic_groups);

        boolean unread = isFlagSet(group.getGroupUnreadBy(), currentUid) || isFlagSet(group.getUnreadBy(), currentUid);
        boolean pinned = isFlagSet(group.getGroupPinnedBy(), currentUid) || isFlagSet(group.getPinnedBy(), currentUid);
        boolean muted = isFlagSet(group.getGroupMutedBy(), currentUid) || isFlagSet(group.getMutedBy(), currentUid);

        holder.tvUnreadBadge.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.tvUnreadBadge.setText(unread ? "NEW" : "");
        holder.tvPinMarker.setVisibility(pinned ? View.VISIBLE : View.GONE);
        holder.tvMutedMarker.setVisibility(muted ? View.VISIBLE : View.GONE);
        holder.tvLastMessage.setAlpha(muted ? 0.8f : 1f);
        holder.itemView.setOnClickListener(v -> listener.onClick(group));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(group, holder.itemView);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public void submitList(List<ChatModel> next) {
        List<ChatModel> newValues = next == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(next);
        Log.d(TAG, "submitList old=" + groups.size() + " new=" + newValues.size());
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return groups.size();
            }

            @Override
            public int getNewListSize() {
                return newValues.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ChatModel oldItem = groups.get(oldItemPosition);
                ChatModel newItem = newValues.get(newItemPosition);
                String oldId = oldItem == null ? null : oldItem.getChatId();
                String newId = newItem == null ? null : newItem.getChatId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatModel oldItem = groups.get(oldItemPosition);
                ChatModel newItem = newValues.get(newItemPosition);
                if (oldItem == null || newItem == null) return oldItem == newItem;
                return safe(oldItem.getChatName()).equals(safe(newItem.getChatName()))
                        && safe(oldItem.getLastMessage()).equals(safe(newItem.getLastMessage()))
                        && oldItem.getLastMessageTimestamp() == newItem.getLastMessageTimestamp()
                        && isFlagSet(oldItem.getGroupUnreadBy(), currentUid) == isFlagSet(newItem.getGroupUnreadBy(), currentUid)
                        && isFlagSet(oldItem.getUnreadBy(), currentUid) == isFlagSet(newItem.getUnreadBy(), currentUid)
                        && isFlagSet(oldItem.getGroupPinnedBy(), currentUid) == isFlagSet(newItem.getGroupPinnedBy(), currentUid)
                        && isFlagSet(oldItem.getPinnedBy(), currentUid) == isFlagSet(newItem.getPinnedBy(), currentUid)
                        && isFlagSet(oldItem.getGroupMutedBy(), currentUid) == isFlagSet(newItem.getGroupMutedBy(), currentUid)
                        && isFlagSet(oldItem.getMutedBy(), currentUid) == isFlagSet(newItem.getMutedBy(), currentUid);
            }
        });

        groups.clear();
        groups.addAll(newValues);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        ChatModel group = groups.get(position);
        return group.getChatId() == null ? position : group.getChatId().hashCode();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvGroupName, tvLastMessage, tvTimestamp, tvUnreadBadge, tvPinMarker, tvMutedMarker;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_group_avatar);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvLastMessage = itemView.findViewById(R.id.tv_group_last_message);
            tvTimestamp = itemView.findViewById(R.id.tv_group_timestamp);
            tvUnreadBadge = itemView.findViewById(R.id.tv_group_unread_badge);
            tvPinMarker = itemView.findViewById(R.id.tv_group_pin_marker);
            tvMutedMarker = itemView.findViewById(R.id.tv_group_muted_marker);
        }
    }

    private boolean isFlagSet(java.util.Map<String, Boolean> map, String uid) {
        return map != null && uid != null && Boolean.TRUE.equals(map.get(uid));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
