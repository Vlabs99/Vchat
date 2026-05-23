package com.vchat.app.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vchat.app.R;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;
import com.vchat.app.utils.TimeUtils;

import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder> {

    private final List<UserModel> userList;
    private final OnRequestClickListener listener;

    public interface OnRequestClickListener {
        void onRequestClick(UserModel user);
    }

    public UsersAdapter(List<UserModel> userList, OnRequestClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserModel user = userList.get(position);

        String username = (user.getUsername() == null || user.getUsername().trim().isEmpty())
                ? "User"
                : user.getUsername().trim();
        holder.tvUsername.setText(username);

        if (user.isOnline()) {
            holder.tvEmail.setText("Online");
        } else {
            holder.tvEmail.setText(TimeUtils.getLastSeenFormatted(user.getLastSeen()));
        }
        String stateLabel = user.getStateLabel();
        if (!TextUtils.isEmpty(stateLabel)) {
            holder.tvStateChip.setText(stateLabel.toUpperCase());
            applyChipStyle(holder.tvStateChip, user.getFriendshipState());
            holder.tvStateChip.setVisibility(View.VISIBLE);
        } else {
            holder.tvStateChip.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(user.getProfileImage())) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getProfileImage())
                    .placeholder(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfileImage);
        } else {
            holder.ivProfileImage.setImageResource(R.drawable.ic_profile);
        }

        String friendshipState = user.getFriendshipState();
        if (RelationshipStateHelper.VIEW_FRIENDS.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(false);
            holder.btnSendRequest.setText("Friends");
        } else if (RelationshipStateHelper.VIEW_PENDING_OUT.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(false);
            holder.btnSendRequest.setText("Pending");
        } else if (RelationshipStateHelper.VIEW_PENDING_IN.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(false);
            holder.btnSendRequest.setText("Pending");
        } else if (RelationshipStateHelper.VIEW_BLOCKED_BY_ME.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(true);
            holder.btnSendRequest.setText("Unblock");
        } else if (RelationshipStateHelper.VIEW_BLOCKED_ME.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(false);
            holder.btnSendRequest.setText("You are blocked");
        } else if (RelationshipStateHelper.VIEW_REMOVED.equals(friendshipState)) {
            holder.btnSendRequest.setEnabled(true);
            holder.btnSendRequest.setText("Add Friend Again");
        } else {
            holder.btnSendRequest.setEnabled(true);
            holder.btnSendRequest.setText("Request");
        }

        holder.btnSendRequest.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRequestClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfileImage;
        TextView tvUsername;
        TextView tvEmail;
        TextView tvStateChip;
        Button btnSendRequest;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.iv_profile_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            tvStateChip = itemView.findViewById(R.id.tv_state_chip);
            btnSendRequest = itemView.findViewById(R.id.btn_send_request);
        }
    }

    private void applyChipStyle(TextView chip, String friendshipState) {
        if (chip == null) return;
        if (RelationshipStateHelper.VIEW_FRIENDS.equals(friendshipState)) {
            chip.setBackgroundResource(R.drawable.bg_chip_friend);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.colorAccent));
        } else if (RelationshipStateHelper.VIEW_PENDING_OUT.equals(friendshipState)
                || RelationshipStateHelper.VIEW_PENDING_IN.equals(friendshipState)) {
            chip.setBackgroundResource(R.drawable.bg_chip_pending);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.warning_orange));
        } else if (RelationshipStateHelper.VIEW_REMOVED.equals(friendshipState)) {
            chip.setBackgroundResource(R.drawable.bg_chip_removed);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.text_muted));
        } else if (RelationshipStateHelper.VIEW_BLOCKED_BY_ME.equals(friendshipState)
                || RelationshipStateHelper.VIEW_BLOCKED_ME.equals(friendshipState)) {
            chip.setBackgroundResource(R.drawable.bg_chip_blocked);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.error_red));
        } else {
            chip.setBackgroundResource(R.drawable.bg_state_chip);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.text_secondary));
        }
    }
}
