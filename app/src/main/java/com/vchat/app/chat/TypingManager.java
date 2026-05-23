package com.vchat.app.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.TimeUtils;

public class TypingManager {

    private final FirebaseFirestore db;
    private final FirebaseUser currentUser;
    private final String chatId;
    private final String otherUserId;
    private final EditText etMessage;
    private final Toolbar toolbarChat;
    private final boolean isGroup;
    private final TypingRestrictionCallback restrictionCallback;

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingStopRunnable;
    private String lastTypingValue = "";
    private ListenerRegistration userListener;

    private static final long TYPING_STOP_DELAY_MS = 1200L;

    public interface TypingRestrictionCallback {
        boolean isDirectChatRestricted();
    }

    public TypingManager(
            FirebaseFirestore db,
            FirebaseUser currentUser,
            String chatId,
            String otherUserId,
            EditText etMessage,
            Toolbar toolbarChat,
            boolean isGroup,
            TypingRestrictionCallback restrictionCallback
    ) {
        this.db = db;
        this.currentUser = currentUser;
        this.chatId = chatId;
        this.otherUserId = otherUserId;
        this.etMessage = etMessage;
        this.toolbarChat = toolbarChat;
        this.isGroup = isGroup;
        this.restrictionCallback = restrictionCallback;
    }

    public void setupTypingIndicator() {
        if (isGroup) return;

        typingStopRunnable = () -> updateTypingStatus("");

        etMessage.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (restrictionCallback.isDirectChatRestricted()) {
                    updateTypingStatus("");
                    return;
                }

                String nextValue = s.length() > 0 ? chatId : "";

                updateTypingStatus(nextValue);

                typingHandler.removeCallbacks(typingStopRunnable);

                if (s.length() > 0) {
                    typingHandler.postDelayed(
                            typingStopRunnable,
                            TYPING_STOP_DELAY_MS
                    );
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void updateTypingStatus(String typingTo) {

        if (currentUser == null) return;

        if (restrictionCallback.isDirectChatRestricted()
                && !TextUtils.isEmpty(typingTo)) {
            return;
        }

        if (typingTo.equals(lastTypingValue)) return;

        lastTypingValue = typingTo;

        db.collection("users")
                .document(currentUser.getUid())
                .update("typingTo", typingTo)
                .addOnFailureListener(e ->
                        Log.e(
                                "TypingManager",
                                "Typing status update failed",
                                e
                        )
                );
    }

    public void listenForOtherUserPresence() {

        if (isGroup) return;

        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }

        userListener = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null || !value.exists()) {
                        return;
                    }

                    if (restrictionCallback.isDirectChatRestricted()) {
                        toolbarChat.setSubtitle("");
                        return;
                    }

                    UserModel user = value.toObject(UserModel.class);

                    if (user == null) return;

                    if (chatId.equals(user.getTypingTo())) {

                        toolbarChat.setSubtitle("Typing...");

                    } else if (user.isOnline()) {

                        toolbarChat.setSubtitle("Online");

                    } else {

                        toolbarChat.setSubtitle(
                                TimeUtils.getLastSeenFormatted(
                                        user.getLastSeen()
                                )
                        );
                    }
                });
    }

    public void onPause() {
        updateTypingStatus("");
    }

    public void onDestroy() {

        if (typingHandler != null && typingStopRunnable != null) {
            typingHandler.removeCallbacks(typingStopRunnable);
        }

        if (userListener != null) {
            userListener.remove();
        }

        updateTypingStatus("");
    }

    public String getLastTypingValue() {
        return lastTypingValue;
    }

    public void setLastTypingValue(String lastTypingValue) {
        this.lastTypingValue = lastTypingValue;
    }
}