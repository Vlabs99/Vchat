package com.vchat.app.chat;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.vchat.app.models.MessageModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageSender {

    private final FirebaseFirestore db;

    public MessageSender(FirebaseFirestore db) {
        this.db = db;
    }

    public void sendTextMessage(
            String chatId,
            String currentUserId,
            String otherUserId,
            boolean isGroup,
            String messageText,
            MessageModel message,
            SendCallback callback
    ) {

        if (TextUtils.isEmpty(chatId)) {
            callback.onError("Invalid chat");
            return;
        }

        if (TextUtils.isEmpty(messageText)) {
            callback.onError("Message is empty");
            return;
        }

        final String messageId = UUID.randomUUID().toString();
        final long timestamp = System.currentTimeMillis();

        message.setMessageId(messageId);
        message.setTimestamp(timestamp);

        db.collection("chats")
                .document(chatId)
                .get()
                .addOnSuccessListener(chatSnap -> {

                    Map<String, Boolean> participants =
                            chatSnap == null
                                    ? null
                                    : (Map<String, Boolean>) chatSnap.get("participants");

                    boolean hasCurrent =
                            participants != null
                                    && Boolean.TRUE.equals(
                                    participants.get(currentUserId)
                            );

                    boolean hasOther =
                            isGroup
                                    || (
                                    participants != null
                                            && Boolean.TRUE.equals(
                                            participants.get(otherUserId)
                                    )
                            );

                    if (!isGroup && (!hasCurrent || !hasOther)) {
                        callback.onError("Chat membership invalid");
                        return;
                    }

                    db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .set(message)
                            .addOnSuccessListener(unused -> {

                                 Map<String, Object> updates =
                                         new HashMap<>();

                                 updates.put("lastMessage", messageText);
                                 updates.put(
                                         "lastMessageTimestamp",
                                         timestamp
                                 );

                                 if (participants != null) {
                                     for (String uid : participants.keySet()) {
                                         if (!uid.equals(currentUserId)) {
                                             updates.put("personalDeletedFor." + uid, com.google.firebase.firestore.FieldValue.delete());
                                             updates.put("deletedFor." + uid, com.google.firebase.firestore.FieldValue.delete());
                                             updates.put("groupDeletedFor." + uid, com.google.firebase.firestore.FieldValue.delete());
                                         }
                                     }
                                 }

                                 db.collection("chats")
                                         .document(chatId)
                                         .update(updates);

                                 callback.onSuccess();
                             })
                            .addOnFailureListener(e -> {

                                if (e != null) {
                                    callback.onError(e.getMessage());
                                } else {
                                    callback.onError("Unknown error");
                                }
                            });
                })
                .addOnFailureListener(e -> {

                    if (e != null) {
                        callback.onError(e.getMessage());
                    } else {
                        callback.onError("Unknown error");
                    }
                });
    }

    public interface SendCallback {

        void onSuccess();

        void onError(String error);
    }
}