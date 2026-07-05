package com.vchat.app.adapters;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.GestureDetector;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vchat.app.R;
import com.vchat.app.models.MessageModel;
import com.vchat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMessageActionListener {
        void onPinMessage(MessageModel message);
        void onForwardMessage(MessageModel message);
        void onReplyMessage(MessageModel message);
        void onDeleteForMe(MessageModel message);
        void onDeleteMessage(MessageModel message);
        void onMessageInfo(MessageModel message);
        void onCopyMessage(MessageModel message);
        void onReactMessage(MessageModel message, String emoji);
        void onStarMessage(MessageModel message);
        void onOpenRepliedMessage(String messageId);
        void onMessageLongPressed(MessageModel message, int position);
        void onStartMessageMultiSelect(MessageModel message, int position);
    }

    private static final int MSG_TYPE_SENT = 1;
    private static final int MSG_TYPE_RECEIVED = 2;
    private static final int MSG_TYPE_SYSTEM = 3;

    private final List<MessageModel> messageList;
    private final String currentUserId;
    private final String chatId;
    private final boolean isGroupChat;
    private final OnMessageActionListener actionListener;
    private final Map<String, String> senderNameCache = new HashMap<>();
    private final Map<String, ListenerRegistration> senderNameListeners = new HashMap<>();
    private final Set<String> selectedMessageIds = new HashSet<>();
    private boolean selectionMode = false;
    private String highlightedMessageId = "";

    public MessageAdapter(List<MessageModel> messageList,
                          String currentUserId,
                          String chatId,
                          boolean isGroupChat,
                          OnMessageActionListener actionListener) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.chatId = chatId;
        this.isGroupChat = isGroupChat;
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == MSG_TYPE_SENT) {
            return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
        } else if (viewType == MSG_TYPE_SYSTEM) {
            return new SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false));
        }
        return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = messageList.get(position);
        if (holder.getItemViewType() == MSG_TYPE_SYSTEM) {
            SystemMessageViewHolder h = (SystemMessageViewHolder) holder;
            h.tvMessageText.setText(message != null ? message.getMessageText() : "");
            return;
        }
        String time = TimeUtils.getFormattedTime(message.getTimestamp());
        boolean isImage = "image".equals(message.getMessageType());
        boolean isSticker = "sticker".equals(message.getMessageType());
        boolean isPoll = "poll".equals(message.getMessageType());
        boolean isEvent = "event".equals(message.getMessageType());

        String messageId = message.getMessageId();
        boolean isSelected = !TextUtils.isEmpty(messageId) && selectedMessageIds.contains(messageId);
        holder.itemView.setActivated(isSelected);
        holder.itemView.setAlpha(isSelected ? 0.76f : 1f);
        bindHighlight(holder.itemView, messageId);

        if (holder.getItemViewType() == MSG_TYPE_SENT) {
            SentMessageViewHolder h = (SentMessageViewHolder) holder;
            h.tvTimestamp.setText(time);
            bindStatusIcon(h.ivStatus, message.getStatus());
            bindStar(h.tvStar, message);
            bindReactionLine(h.tvReactions, message);
            bindReplyPreview(h.tvReplyPreview, message);
            bindMessageBody(h.ivMessageImage, h.tvMessageText, message, isImage, isSticker, isPoll, isEvent);
            bindCommonGestures(h.itemView, message, position, isPoll, isEvent);
        } else {
            ReceivedMessageViewHolder h = (ReceivedMessageViewHolder) holder;
            h.tvTimestamp.setText(time);
            bindGroupSenderLabel(h, message);
            bindStar(h.tvStar, message);
            bindReactionLine(h.tvReactions, message);
            bindReplyPreview(h.tvReplyPreview, message);
            bindMessageBody(h.ivMessageImage, h.tvMessageText, message, isImage, isSticker, isPoll, isEvent);
            bindCommonGestures(h.itemView, message, position, isPoll, isEvent);
        }
    }

    private void bindCommonGestures(View itemView, MessageModel message, int position, boolean isPoll, boolean isEvent) {
        GestureDetector detector = new GestureDetector(itemView.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (actionListener != null) actionListener.onReactMessage(message, "\u2764");
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90).withEndAction(() ->
                        itemView.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                return true;
            }
        });

        itemView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
        itemView.setOnLongClickListener(v -> {
            if (selectionMode) {
                if (actionListener != null) actionListener.onMessageLongPressed(message, position);
            } else {
                showMessageActionsBottomSheet(v, message);
            }
            return true;
        });
        itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(message);
                return;
            }
            if (isPoll) {
                int selected = nextVoteSelection(message);
                if (selected >= 0) voteOnPoll(message.getMessageId(), selected);
                return;
            }
            if (isEvent) {
                showEventDetailsDialog(v, message.getMessageText());
                return;
            }
        });
    }

    private void bindReplyPreview(TextView view, MessageModel message) {
        if (TextUtils.isEmpty(message.getReplyToMessageId()) || TextUtils.isEmpty(message.getReplyPreview())) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText("Reply: " + message.getReplyPreview());
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onOpenRepliedMessage(message.getReplyToMessageId());
        });
    }

    private void bindStar(TextView view, MessageModel message) {
        if (message.getStarredBy().containsKey(currentUserId)) {
            view.setVisibility(View.VISIBLE);
            view.setText("?");
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void bindMessageBody(ImageView imageView, TextView messageView, MessageModel message, boolean isImage, boolean isSticker, boolean isPoll, boolean isEvent) {
        if (isSticker) {
            int stickerId = resolveStickerResId(imageView, message);
            if (stickerId != 0) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setImageResource(stickerId);
                messageView.setVisibility(View.GONE);
                return;
            }
        }
        if (isImage && !TextUtils.isEmpty(message.getMediaUrl())) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(imageView.getContext()).load(message.getMediaUrl()).placeholder(R.drawable.ic_profile).into(imageView);
            if (TextUtils.isEmpty(message.getMessageText())) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setVisibility(View.VISIBLE);
                messageView.setText(message.getMessageText());
            }
            return;
        }

        imageView.setVisibility(View.GONE);
        messageView.setVisibility(View.VISIBLE);
        if (isPoll) {
            messageView.setText(buildPollPreviewText(message));
        } else if (isEvent) {
            messageView.setText(buildEventPreviewText(message.getMessageText()));
        } else {
            messageView.setText(message.getMessageText());
        }
    }

    private void bindReactionLine(TextView tvReactions, MessageModel message) {
        Map<String, String> reactions = message.getReactions();
        if (reactions.isEmpty()) {
            tvReactions.setVisibility(View.GONE);
            return;
        }
        Map<String, Integer> count = new HashMap<>();
        for (String emoji : reactions.values()) {
            if (TextUtils.isEmpty(emoji)) continue;
            int c = count.containsKey(emoji) ? count.get(emoji) : 0;
            count.put(emoji, c + 1);
        }
        if (count.isEmpty()) {
            tvReactions.setVisibility(View.GONE);
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            parts.add(entry.getKey() + " " + entry.getValue());
        }
        tvReactions.setText(TextUtils.join("   ", parts));
        tvReactions.setVisibility(View.VISIBLE);
    }

    private void bindGroupSenderLabel(ReceivedMessageViewHolder holder, MessageModel message) {
        if (!isGroupChat || message == null || TextUtils.isEmpty(message.getSenderId())) {
            holder.tvSenderName.setVisibility(View.GONE);
            return;
        }

        String senderId = message.getSenderId();
        String cached = senderNameCache.get(senderId);
        if (!TextUtils.isEmpty(cached)) {
            holder.tvSenderName.setText(cached);
            holder.tvSenderName.setVisibility(View.VISIBLE);
            return;
        }

        holder.tvSenderName.setText(senderId);
        holder.tvSenderName.setVisibility(View.GONE);
        if (senderNameListeners.containsKey(senderId)) return;

        ListenerRegistration reg = FirebaseFirestore.getInstance().collection("users").document(senderId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;
                    String username = snapshot.getString("username");
                    if (TextUtils.isEmpty(username)) return;
                    senderNameCache.put(senderId, username);
                    notifyDataSetChanged();
                });
        senderNameListeners.put(senderId, reg);
    }

    private int resolveStickerResId(ImageView view, MessageModel message) {
        if (view == null || message == null || TextUtils.isEmpty(message.getMediaUrl())) return 0;
        String key = message.getMediaUrl();
        return view.getContext().getResources().getIdentifier(key, "drawable", view.getContext().getPackageName());
    }

    private void showMessageActionsBottomSheet(View anchor, MessageModel message) {
        BottomSheetDialog dialog = new BottomSheetDialog(anchor.getContext());
        View content = LayoutInflater.from(anchor.getContext()).inflate(R.layout.bottomsheet_message_actions, null, false);
        dialog.setContentView(content);

        ((TextView) content.findViewById(R.id.reaction_heart)).setText("\u2764");
        ((TextView) content.findViewById(R.id.reaction_laugh)).setText("\uD83D\uDE02");
        ((TextView) content.findViewById(R.id.reaction_like)).setText("\uD83D\uDC4D");
        ((TextView) content.findViewById(R.id.reaction_wow)).setText("\uD83D\uDE2E");
        ((TextView) content.findViewById(R.id.reaction_sad)).setText("\uD83D\uDE22");
        ((TextView) content.findViewById(R.id.reaction_pray)).setText("\uD83D\uDE4F");

        setReactionClick(content, R.id.reaction_heart, "\u2764", message, dialog);
        setReactionClick(content, R.id.reaction_laugh, "\uD83D\uDE02", message, dialog);
        setReactionClick(content, R.id.reaction_like, "\uD83D\uDC4D", message, dialog);
        setReactionClick(content, R.id.reaction_wow, "\uD83D\uDE2E", message, dialog);
        setReactionClick(content, R.id.reaction_sad, "\uD83D\uDE22", message, dialog);
        setReactionClick(content, R.id.reaction_pray, "\uD83D\uDE4F", message, dialog);

        bindAction(content, R.id.action_reply, () -> actionListener.onReplyMessage(message), dialog);
        bindAction(content, R.id.action_forward, () -> actionListener.onForwardMessage(message), dialog);
        bindAction(content, R.id.action_pin, () -> actionListener.onPinMessage(message), dialog);
        bindAction(content, R.id.action_copy, () -> actionListener.onCopyMessage(message), dialog);
        bindAction(content, R.id.action_star, () -> actionListener.onStarMessage(message), dialog);
        bindAction(content, R.id.action_info, () -> actionListener.onMessageInfo(message), dialog);
        bindAction(content, R.id.action_multi_select, () -> actionListener.onStartMessageMultiSelect(message, -1), dialog);
        bindAction(content, R.id.action_delete_for_me, () -> actionListener.onDeleteForMe(message), dialog);
        bindAction(content, R.id.action_delete, () -> actionListener.onDeleteMessage(message), dialog);

        dialog.show();
    }

    private void setReactionClick(View content, int id, String emoji, MessageModel message, BottomSheetDialog dialog) {
        TextView tv = content.findViewById(id);
        tv.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onReactMessage(message, emoji);
            dialog.dismiss();
        });
    }

    private void bindAction(View content, int id, Runnable runnable, BottomSheetDialog dialog) {
        TextView tv = content.findViewById(id);
        tv.setOnClickListener(v -> {
            if (actionListener != null) runnable.run();
            dialog.dismiss();
        });
    }

    private String buildEventPreviewText(String fullText) {
        if (TextUtils.isEmpty(fullText)) return "Event";
        String[] lines = fullText.split("\n");
        String titleLine = lines.length > 1 ? lines[1] : "Event";
        return "Event: " + titleLine.replace("Title:", "").trim() + "\nTap to view details";
    }

    private void showEventDetailsDialog(View anchor, String fullText) {
        if (TextUtils.isEmpty(fullText)) return;
        new androidx.appcompat.app.AlertDialog.Builder(anchor.getContext())
                .setTitle("Event Details")
                .setMessage(fullText.replace("EVENT\n", ""))
                .setPositiveButton("OK", null)
                .show();
    }

    private String buildPollPreviewText(MessageModel pollMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Poll: ").append(pollMsg.getPollQuestion()).append("\n");
        List<String> options = pollMsg.getPollOptions();
        Map<String, Integer> votes = pollMsg.getPollVotes();
        int myVote = votes.containsKey(currentUserId) ? votes.get(currentUserId) : -1;

        for (int i = 0; i < options.size(); i++) {
            int count = pollMsg.getPollVoteCountForOption(i);
            boolean voted = (i == myVote);
            sb.append(i + 1).append(") ").append(options.get(i));
            if (voted) sb.append("  [voted]");
            sb.append("  (").append(count).append(")");
            if (i < options.size() - 1) sb.append("\n");
        }
        sb.append("\nTap poll to vote");
        return sb.toString();
    }

    private int nextVoteSelection(MessageModel pollMsg) {
        List<String> options = pollMsg.getPollOptions();
        if (options.isEmpty()) return -1;

        Map<String, Integer> votes = pollMsg.getPollVotes();
        int current = votes.containsKey(currentUserId) ? votes.get(currentUserId) : -1;
        int next = current + 1;
        if (next >= options.size()) next = 0;
        return next;
    }

    private void voteOnPoll(String messageId, int optionIndex) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(messageId)) return;
        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update("pollVotes." + currentUserId, optionIndex);
    }

    private void bindStatusIcon(ImageView statusView, String status) {
        if ("seen".equals(status)) {
            statusView.setImageResource(android.R.drawable.checkbox_on_background);
            statusView.setColorFilter(Color.parseColor("#53BDEB"));
        } else if ("delivered".equals(status)) {
            statusView.setImageResource(android.R.drawable.checkbox_on_background);
            statusView.setColorFilter(Color.parseColor("#9E9E9E"));
        } else {
            statusView.setImageResource(android.R.drawable.ic_menu_send);
            statusView.setColorFilter(Color.parseColor("#9E9E9E"));
        }
    }

    @Override
    public int getItemCount() {
        return messageList == null ? 0 : messageList.size();
    }

    @Override
    public long getItemId(int position) {
        MessageModel message = messageList.get(position);
        String messageId = message.getMessageId();
        if (!TextUtils.isEmpty(messageId)) return messageId.hashCode();
        return (message.getSenderId() + "_" + message.getTimestamp()).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        MessageModel message = messageList.get(position);
        if (message != null && "system".equals(message.getMessageType())) {
            return MSG_TYPE_SYSTEM;
        }
        String senderId = message == null ? "" : message.getSenderId();
        return (!TextUtils.isEmpty(senderId) && senderId.equals(currentUserId)) ? MSG_TYPE_SENT : MSG_TYPE_RECEIVED;
    }

    public void clear() {
        for (ListenerRegistration reg : senderNameListeners.values()) {
            if (reg != null) reg.remove();
        }
        senderNameListeners.clear();
        senderNameCache.clear();
        selectedMessageIds.clear();
    }

    public void highlightMessage(String messageId) {
        highlightedMessageId = messageId == null ? "" : messageId;
        notifyDataSetChanged();
    }

    private void bindHighlight(View itemView, String messageId) {
        if (!TextUtils.isEmpty(highlightedMessageId) && TextUtils.equals(highlightedMessageId, messageId)) {
            int color = Color.parseColor("#3353BDEB");
            itemView.setBackground(new ColorDrawable(color));
            itemView.animate().setDuration(1400).alpha(1f).withEndAction(() -> {
                itemView.setBackgroundColor(Color.TRANSPARENT);
                highlightedMessageId = "";
            }).start();
        } else if (!itemView.isActivated()) {
            itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) return;
        selectionMode = enabled;
        if (!selectionMode) selectedMessageIds.clear();
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selectedMessageIds.size();
    }

    public List<MessageModel> getSelectedMessages() {
        List<MessageModel> selected = new ArrayList<>();
        if (selectedMessageIds.isEmpty()) return selected;
        for (MessageModel message : messageList) {
            String id = message.getMessageId();
            if (!TextUtils.isEmpty(id) && selectedMessageIds.contains(id)) {
                selected.add(message);
            }
        }
        return selected;
    }

    public void toggleSelection(MessageModel message) {
        if (message == null) return;
        String id = message.getMessageId();
        if (TextUtils.isEmpty(id)) return;
        if (selectedMessageIds.contains(id)) {
            selectedMessageIds.remove(id);
        } else {
            selectedMessageIds.add(id);
        }
        notifyDataSetChanged();
    }

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessageText;
        TextView tvReplyPreview;
        TextView tvTimestamp;
        TextView tvReactions;
        TextView tvStar;
        ImageView ivStatus;
        ImageView ivMessageImage;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tv_message_text);
            tvReplyPreview = itemView.findViewById(R.id.tv_reply_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvReactions = itemView.findViewById(R.id.tv_reactions);
            tvStar = itemView.findViewById(R.id.tv_star);
            ivStatus = itemView.findViewById(R.id.iv_status);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
        }
    }

    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName;
        TextView tvMessageText;
        TextView tvReplyPreview;
        TextView tvTimestamp;
        TextView tvReactions;
        TextView tvStar;
        ImageView ivMessageImage;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tv_sender_name);
            tvMessageText = itemView.findViewById(R.id.tv_message_text);
            tvReplyPreview = itemView.findViewById(R.id.tv_reply_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvReactions = itemView.findViewById(R.id.tv_reactions);
            tvStar = itemView.findViewById(R.id.tv_star);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
        }
    }

    public static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        public TextView tvMessageText;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tv_message_text);
        }
    }
}
