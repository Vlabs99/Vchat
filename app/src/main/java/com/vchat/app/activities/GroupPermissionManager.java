package com.vchat.app.chat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class GroupPermissionManager {

    private final FirebaseFirestore db;

    public GroupPermissionManager(FirebaseFirestore db) {
        this.db = db;
    }

    public void checkAdminPermission(
            String chatId,
            String currentUserId,
            PermissionCallback callback
    ) {

        db.collection("chats")
                .document(chatId)
                .get()
                .addOnSuccessListener(chatSnap -> {

                    Map<String, Boolean> admins =
                            (Map<String, Boolean>)
                                    chatSnap.get("admins");

                    boolean isAdmin =
                            admins != null
                                    && Boolean.TRUE.equals(
                                    admins.get(currentUserId)
                            );

                    callback.onResult(isAdmin);
                })
                .addOnFailureListener(e ->
                        callback.onResult(false)
                );
    }

    public interface PermissionCallback {
        void onResult(boolean allowed);
    }
}