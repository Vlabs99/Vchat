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

import com.bumptech.glide.Glide;
import com.vchat.app.R;
import com.vchat.app.models.ChatModel;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ChatViewHolder> {
    private static final String TAG = "ChatsAdapter";

    private final List<ChatModel> chatList;
    private final List<UserModel> otherParticipants;
    private final String currentUid;
    private final OnChatClickListener listener;
    private final OnChatLongClickListener longClickListener;

    public interface OnChatClickListener {
        void onChatClick(ChatModel chat, UserModel otherUser);
    }

    public interface OnChatLongClickListener {
        void onChatLongClick(ChatModel chat, UserModel otherUser, View anchor);
    }

    public ChatsAdapter(List<ChatModel> chatList, List<UserModel> otherParticipants, String currentUid, OnChatClickListener listener,
                        OnChatLongClickListener longClickListener) {
        this.chatList = chatList == null ? new ArrayList<>() : chatList;
        this.otherParticipants = otherParticipants == null ? new ArrayList<>() : otherParticipants;
        this.currentUid = currentUid;
        this.listener = listener;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatModel chat = chatList.get(position);
        UserModel otherUser = otherParticipants.get(position);
        boolean isGroup = chat != null && chat.isGroup();

        String username = isGroup ? chat.getChatName() : "User";
        if (!isGroup && otherUser != null && otherUser.getUsername() != null && !otherUser.getUsername().trim().isEmpty()) {
            username = otherUser.getUsername().trim();
        }
        if (username == null || username.trim().isEmpty()) username = isGroup ? "Unnamed Group" : "User";

        boolean isTyping = !isGroup && otherUser != null && !chat.getChatId().isEmpty() && chat.getChatId().equals(otherUser.getTypingTo());
        holder.tvUsername.setText(username);
        holder.viewOnlineDot.setVisibility(!isGroup && otherUser != null && otherUser.isOnline() ? View.VISIBLE : View.GONE);

        if (isGroup) {
            holder.ivProfileImage.setImageResource(R.drawable.ic_groups);
        } else if (otherUser != null && otherUser.getProfileImage() != null && !otherUser.getProfileImage().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(otherUser.getProfileImage())
                    .placeholder(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ivProfileImage);
        } else {
            holder.ivProfileImage.setImageResource(R.drawable.ic_profile);
        }

        String lastMessage = chat.getLastMessage();
        if (isTyping) {
            lastMessage = "typing...";
            holder.tvLastMessage.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.colorAccent));
        } else {
            holder.tvLastMessage.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
        }
        holder.tvLastMessage.setText((lastMessage == null || lastMessage.trim().isEmpty()) ? "Tap to start chatting" : lastMessage);
        holder.tvTimestamp.setText(TimeUtils.getFormattedTime(chat.getLastMessageTimestamp()));
        boolean pinned = isFlagSet(chat.getPersonalPinnedBy(), currentUid) || isFlagSet(chat.getPinnedBy(), currentUid);
        boolean unread = isFlagSet(chat.getPersonalUnreadBy(), currentUid) || isFlagSet(chat.getUnreadBy(), currentUid);
        boolean archived = isFlagSet(chat.getPersonalArchivedBy(), currentUid) || isFlagSet(chat.getArchivedBy(), currentUid);
        holder.tvPinMarker.setVisibility(pinned ? View.VISIBLE : View.GONE);
        holder.tvUnreadBadge.setVisibility(unread ? View.VISIBLE : View.GONE);
        String state = otherUser == null ? "" : otherUser.getStateLabel();
        if (!isGroup && state != null && !state.trim().isEmpty()) {
            holder.tvStateMarker.setText(compactStateLabel(state));
            applyStateChip(holder.tvStateMarker, state);
            holder.tvStateMarker.setVisibility(View.VISIBLE);
        } else {
            holder.tvStateMarker.setVisibility(View.GONE);
        }
        if (archived && !isTyping) {
            holder.tvLastMessage.setText("[Archived] " + holder.tvLastMessage.getText());
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChatClick(chat, otherUser);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onChatLongClick(chat, otherUser, holder.itemView);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    @Override
    public long getItemId(int position) {
        ChatModel chat = chatList.get(position);
        return chat.getChatId() == null ? position : chat.getChatId().hashCode();
    }

    public void submitList(List<ChatModel> chats, List<UserModel> participants) {
        List<ChatModel> nextChats = chats == null ? new ArrayList<>() : new ArrayList<>(chats);
        List<UserModel> nextParticipants = participants == null ? new ArrayList<>() : new ArrayList<>(participants);
        int pairedSize = Math.min(nextChats.size(), nextParticipants.size());
        if (nextChats.size() != pairedSize) {
            nextChats = new ArrayList<>(nextChats.subList(0, pairedSize));
        }
        if (nextParticipants.size() != pairedSize) {
            nextParticipants = new ArrayList<>(nextParticipants.subList(0, pairedSize));
        }
        final List<ChatModel> newChats = nextChats;
        final List<UserModel> newParticipants = nextParticipants;
        Log.d(TAG, "submitList old=" + chatList.size() + " new=" + newChats.size());

        List<ChatModel> oldChats = new ArrayList<>(chatList);
        List<UserModel> oldParticipants = new ArrayList<>(otherParticipants);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldChats.size();
            }

            @Override
            public int getNewListSize() {
                return newChats.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = oldChats.get(oldItemPosition) == null ? null : oldChats.get(oldItemPosition).getChatId();
                String newId = newChats.get(newItemPosition) == null ? null : newChats.get(newItemPosition).getChatId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatModel oldChat = oldChats.get(oldItemPosition);
                ChatModel newChat = newChats.get(newItemPosition);
                UserModel oldUser = oldParticipants.size() > oldItemPosition ? oldParticipants.get(oldItemPosition) : null;
                UserModel newUser = newParticipants.size() > newItemPosition ? newParticipants.get(newItemPosition) : null;
                if (oldChat == null || newChat == null) return oldChat == newChat && oldUser == newUser;

                return safe(oldChat.getChatName()).equals(safe(newChat.getChatName()))
                        && safe(oldChat.getLastMessage()).equals(safe(newChat.getLastMessage()))
                        && oldChat.getLastMessageTimestamp() == newChat.getLastMessageTimestamp()
                        && isFlagSet(oldChat.getPersonalPinnedBy(), currentUid) == isFlagSet(newChat.getPersonalPinnedBy(), currentUid)
                        && isFlagSet(oldChat.getPinnedBy(), currentUid) == isFlagSet(newChat.getPinnedBy(), currentUid)
                        && isFlagSet(oldChat.getPersonalArchivedBy(), currentUid) == isFlagSet(newChat.getPersonalArchivedBy(), currentUid)
                        && isFlagSet(oldChat.getArchivedBy(), currentUid) == isFlagSet(newChat.getArchivedBy(), currentUid)
                        && isFlagSet(oldChat.getPersonalUnreadBy(), currentUid) == isFlagSet(newChat.getPersonalUnreadBy(), currentUid)
                        && isFlagSet(oldChat.getUnreadBy(), currentUid) == isFlagSet(newChat.getUnreadBy(), currentUid)
                        && sameUserState(oldUser, newUser)
                        && sameTypingTarget(oldUser, newUser);
            }
        });

        chatList.clear();
        chatList.addAll(newChats);
        otherParticipants.clear();
        otherParticipants.addAll(newParticipants);
        diff.dispatchUpdatesTo(this);
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfileImage;
        TextView tvUsername;
        TextView tvLastMessage;
        TextView tvTimestamp;
        TextView tvUnreadBadge;
        TextView tvPinMarker;
        TextView tvStateMarker;
        View viewOnlineDot;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.iv_profile_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
            tvPinMarker = itemView.findViewById(R.id.tv_pin_marker);
            tvStateMarker = itemView.findViewById(R.id.tv_state_marker);
            viewOnlineDot = itemView.findViewById(R.id.view_online_dot);
        }
    }

    private boolean isFlagSet(Map<String, Boolean> map, String uid) {
        return map != null && uid != null && Boolean.TRUE.equals(map.get(uid));
    }

    private String compactStateLabel(String raw) {
        if (raw == null) return "";
        String state = raw.trim().toLowerCase();
        if (state.contains("you blocked")) return "YOU BLOCKED";
        if (state.contains("blocked you")) return "BLOCKED YOU";
        if (state.contains("removed")) return "REMOVED";
        if (state.contains("pending")) return "PENDING";
        if (state.contains("friend")) return "FRIEND";
        return raw.toUpperCase();
    }

    private void applyStateChip(TextView chip, String raw) {
        if (chip == null || raw == null) return;
        String state = raw.trim().toLowerCase();
        if (state.contains("friend")) {
            chip.setBackgroundResource(R.drawable.bg_chip_friend);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.colorAccent));
        } else if (state.contains("pending")) {
            chip.setBackgroundResource(R.drawable.bg_chip_pending);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.warning_orange));
        } else if (state.contains("removed")) {
            chip.setBackgroundResource(R.drawable.bg_chip_removed);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.text_muted));
        } else if (state.contains("blocked")) {
            chip.setBackgroundResource(R.drawable.bg_chip_blocked);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.error_red));
        } else {
            chip.setBackgroundResource(R.drawable.bg_state_chip);
            chip.setTextColor(chip.getContext().getResources().getColor(R.color.text_secondary));
        }
    }

    private boolean sameUserState(UserModel a, UserModel b) {
        if (a == null || b == null) return a == b;
        return safe(a.getUid()).equals(safe(b.getUid()))
                && safe(a.getUsername()).equals(safe(b.getUsername()))
                && safe(a.getProfileImage()).equals(safe(b.getProfileImage()))
                && a.isOnline() == b.isOnline()
                && safe(a.getStateLabel()).equals(safe(b.getStateLabel()));
    }

    private boolean sameTypingTarget(UserModel a, UserModel b) {
        String left = a == null ? "" : a.getTypingTo();
        String right = b == null ? "" : b.getTypingTo();
        return safe(left).equals(safe(right));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
