package com.vchat.app.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vchat.app.R;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FriendPickerAdapter extends RecyclerView.Adapter<FriendPickerAdapter.Holder> {
    public interface OnFriendClickListener { void onFriendClick(UserModel user); }

    private final List<UserModel> source = new ArrayList<>();
    private final List<UserModel> filtered = new ArrayList<>();
    private final OnFriendClickListener listener;
    private String currentQuery = "";

    public FriendPickerAdapter(OnFriendClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<UserModel> users) {
        source.clear();
        if (users != null) source.addAll(users);
        updateFiltered(applyFilter(source, currentQuery));
    }

    public void filter(String query) {
        currentQuery = query == null ? "" : query;
        updateFiltered(applyFilter(source, currentQuery));
    }

    public int size() { return filtered.size(); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_picker, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        UserModel u = filtered.get(position);
        String username = safeUsername(u);
        String displayName = safeDisplayName(u);
        String presence = u.isOnline() ? "Online" : TimeUtils.getLastSeenFormatted(u.getLastSeen());

        h.tvName.setText(username);
        h.tvSubtitle.setText(displayName + " • " + presence);
        h.tvStatus.setText(relationshipChipLabel(u.getFriendshipState()));

        if (!TextUtils.isEmpty(u.getProfileImage())) {
            Glide.with(h.itemView.getContext())
                    .load(u.getProfileImage())
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(h.iv);
        } else {
            h.iv.setImageResource(R.drawable.ic_profile);
        }
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFriendClick(u);
        });
    }

    @Override
    public int getItemCount() { return filtered.size(); }

    @Override
    public long getItemId(int position) {
        UserModel user = filtered.get(position);
        String uid = user == null ? null : user.getUid();
        return TextUtils.isEmpty(uid) ? position : uid.hashCode();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName;
        TextView tvSubtitle;
        TextView tvStatus;
        Holder(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_friend_avatar);
            tvName = itemView.findViewById(R.id.tv_friend_name);
            tvSubtitle = itemView.findViewById(R.id.tv_friend_subtitle);
            tvStatus = itemView.findViewById(R.id.tv_friend_status);
        }
    }

    private List<UserModel> applyFilter(List<UserModel> base, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<UserModel> next = new ArrayList<>();
        if (TextUtils.isEmpty(q)) {
            next.addAll(base);
            return next;
        }
        for (UserModel u : base) {
            String username = safeUsername(u).toLowerCase(Locale.ROOT);
            String email = safe(u.getEmail()).toLowerCase(Locale.ROOT);
            String display = safeDisplayName(u).toLowerCase(Locale.ROOT);
            String state = safe(u.getStateLabel()).toLowerCase(Locale.ROOT);
            if (username.contains(q) || email.contains(q) || display.contains(q) || state.contains(q)) {
                next.add(u);
            }
        }
        return next;
    }

    private void updateFiltered(List<UserModel> nextFiltered) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return filtered.size(); }

            @Override
            public int getNewListSize() { return nextFiltered.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldUid = filtered.get(oldItemPosition).getUid();
                String newUid = nextFiltered.get(newItemPosition).getUid();
                return !TextUtils.isEmpty(oldUid) && oldUid.equals(newUid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                UserModel oldU = filtered.get(oldItemPosition);
                UserModel newU = nextFiltered.get(newItemPosition);
                return safeUsername(oldU).equals(safeUsername(newU))
                        && safeDisplayName(oldU).equals(safeDisplayName(newU))
                        && oldU.isOnline() == newU.isOnline()
                        && oldU.getLastSeen() == newU.getLastSeen()
                        && relationshipChipLabel(oldU.getFriendshipState()).equals(relationshipChipLabel(newU.getFriendshipState()))
                        && safe(oldU.getProfileImage()).equals(safe(newU.getProfileImage()));
            }
        });
        filtered.clear();
        filtered.addAll(nextFiltered);
        diff.dispatchUpdatesTo(this);
    }

    private String safeUsername(UserModel user) {
        if (user == null) return "Unknown user";
        String username = user.getUsername();
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(username.trim())) return username.trim();
        String email = user.getEmail();
        if (!TextUtils.isEmpty(email) && email.contains("@")) return email.substring(0, email.indexOf('@'));
        String uid = user.getUid();
        if (!TextUtils.isEmpty(uid) && uid.length() >= 6) return "User " + uid.substring(0, 6);
        return "Unknown user";
    }

    private String safeDisplayName(UserModel user) {
        if (user == null) return "No display name";
        String bio = user.getBio();
        if (!TextUtils.isEmpty(bio)) {
            String cleaned = bio.trim();
            if (!cleaned.isEmpty() && !"Hey there! I am using VChat.".equalsIgnoreCase(cleaned)) return cleaned;
        }
        if (!TextUtils.isEmpty(user.getEmail())) return user.getEmail().trim();
        return "No display name";
    }

    private String relationshipChipLabel(String state) {
        if ("pending_outgoing".equals(state) || "pending_incoming".equals(state)) return "REQUEST PENDING";
        if ("removed".equals(state)) return "NOT FRIENDS";
        if ("blocked_by_me".equals(state)) return "BLOCKED";
        if ("blocked_me".equals(state)) return "BLOCKED YOU";
        return "FRIEND";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
