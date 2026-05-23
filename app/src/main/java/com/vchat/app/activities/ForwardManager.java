package com.vchat.app.chat;

import android.text.TextUtils;

import com.google.firebase.firestore.FirebaseFirestore;
import com.vchat.app.models.MessageModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ForwardManager {

    private final FirebaseFirestore db;

    public ForwardManager(FirebaseFirestore db) {
        this.db = db;
    }

    public void performForward(
            MessageModel original,
            String targetChatId,
            String currentUserId,
            boolean isGroup,
            ForwardCallback callback
    ) {

        String newMessageId =
                UUID.randomUUID().toString();

        long timestamp =
                System.currentTimeMillis();

        String forwardedText =
                buildForwardedText(original);

        MessageModel forwarded =
                new MessageModel(
                        newMessageId,
                        currentUserId,
                        forwardedText,
                        timestamp,
                        "sent",
                        original.getMessageType()
                );

        forwarded.setMediaUrl(
                original.getMediaUrl()
        );

        forwarded.setMediaName(
                original.getMediaName()
        );

        forwarded.setPollQuestion(
                original.getPollQuestion()
        );

        forwarded.setPollOptions(
                original.getPollOptions()
        );

        forwarded.setPollVotes(
                new HashMap<>()
        );

        MessageFactory.initializeGroupTracking(
                forwarded,
                isGroup,
                currentUserId
        );

        db.collection("chats")
                .document(targetChatId)
                .collection("messages")
                .document(newMessageId)
                .set(forwarded)
                .addOnSuccessListener(unused -> {

                    Map<String, Object> updates =
                            new HashMap<>();

                    updates.put(
                            "lastMessage",
                            "↪ Forwarded"
                    );

                    updates.put(
                            "lastMessageTimestamp",
                            timestamp
                    );

                    db.collection("chats")
                            .document(targetChatId)
                            .update(updates);

                    callback.onSuccess();
                })
                .addOnFailureListener(e ->
                        callback.onFailure()
                );
    }

    private String buildForwardedText(
            MessageModel original
    ) {

        if (original == null) {
            return "↪ Forwarded message";
        }

        String type =
                original.getMessageType();

        if ("poll".equals(type)) {

            String question =
                    original.getPollQuestion();

            return TextUtils.isEmpty(question)
                    ? "↪ 📊 Forwarded poll"
                    : "↪ 📊 " + question;
        }

        if ("event".equals(type)) {
            return "↪ 📅 Forwarded event";
        }

        if ("contact".equals(type)) {
            return "↪ 📞 "
                    + original.getMessageText();
        }

        if ("image".equals(type)) {

            String caption =
                    original.getMessageText();

            return TextUtils.isEmpty(caption)
                    ? "↪ 🖼 Forwarded image"
                    : "↪ 🖼 " + caption;
        }

        String text =
                original.getMessageText();

        return TextUtils.isEmpty(text)
                ? "↪ Forwarded message"
                : "↪ " + text;
    }

    public interface ForwardCallback {

        void onSuccess();

        void onFailure();
    }
}