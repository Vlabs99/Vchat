package com.vchat.app.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vchat.app.R;
import com.vchat.app.models.UserModel;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.Holder> {
    public interface OnBlockedUserClickListener {
        void onClick(UserModel user);
    }

    private final List<UserModel> users = new ArrayList<>();
    private final OnBlockedUserClickListener listener;

    public BlockedUsersAdapter(OnBlockedUserClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<UserModel> list) {
        users.clear();
        if (list != null) users.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_user, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        UserModel user = users.get(position);
        String name = TextUtils.isEmpty(user.getUsername()) ? "User" : user.getUsername().trim();
        holder.tvUsername.setText(name);
        holder.tvEmail.setText(TextUtils.isEmpty(user.getEmail()) ? "No email" : user.getEmail());
        if (!TextUtils.isEmpty(user.getProfileImage())) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getProfileImage())
                    .placeholder(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfileImage);
        } else {
            holder.ivProfileImage.setImageResource(R.drawable.ic_profile);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView ivProfileImage;
        TextView tvUsername;
        TextView tvEmail;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.iv_profile_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
        }
    }
}
