package com.vchat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.vchat.app.R;
import com.vchat.app.models.ChatRequestModel;
import com.vchat.app.models.UserModel;
import java.util.List;

public class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.RequestViewHolder> {

    private List<ChatRequestModel> requestList;
    private List<UserModel> senderList;
    private OnRequestActionClickListener listener;

    public interface OnRequestActionClickListener {
        void onAcceptClick(ChatRequestModel request);
        void onRejectClick(ChatRequestModel request);
    }

    public PendingRequestsAdapter(List<ChatRequestModel> requestList, List<UserModel> senderList, OnRequestActionClickListener listener) {
        this.requestList = requestList;
        this.senderList = senderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        ChatRequestModel request = requestList.get(position);
        UserModel sender = senderList.get(position);

        holder.tvUsername.setText(sender.getUsername());
        holder.tvEmail.setText(sender.getEmail());

        holder.btnAccept.setOnClickListener(v -> {
            if (listener != null) listener.onAcceptClick(request);
        });

        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onRejectClick(request);
        });
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }

    public static class RequestViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfileImage;
        TextView tvUsername;
        TextView tvEmail;
        Button btnAccept;
        Button btnReject;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.iv_profile_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }
    }
}
