package com.vchat.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.vchat.app.R;
import com.vchat.app.activities.ChatActivity;
import com.vchat.app.adapters.ChatsAdapter;
import com.vchat.app.chat.ChatRowActionsBottomSheet;
import com.vchat.app.models.ChatModel;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";
    private static final String CHATLIST_DELETE_TAG = "VCHAT_CHATLIST_DELETE";
    private static final String CHATLIST_STATE_TAG = "VCHAT_CHATLIST_STATE";
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    private static final String KEY_PINNED = "personalPinnedBy.";
    private static final String KEY_ARCHIVED = "personalArchivedBy.";
    private static final String KEY_MUTED = "personalMutedBy.";
    private static final String KEY_DELETED = "personalDeletedFor.";
    private static final String KEY_UNREAD = "personalUnreadBy.";

    private RecyclerView rvChats;
    private TextView tvNoChats;
    private FloatingActionButton fabNewChat;

    private ChatsAdapter adapter;
    private List<ChatModel> chatList;
    private List<UserModel> otherParticipants;
    private final List<ChatModel> allChats = new ArrayList<>();
    private final Map<String, ChatModel> chatsById = new HashMap<>();
    private final Map<String, UserModel> userByUid = new HashMap<>();
    private String searchQuery = "";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ListenerRegistration chatsListener;
    private final Map<String, ListenerRegistration> userPresenceListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> relationshipListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> deliveryListeners = new HashMap<>();

    public ChatsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        rvChats = view.findViewById(R.id.rv_chats);
        tvNoChats = view.findViewById(R.id.tv_no_chats);
        fabNewChat = view.findViewById(R.id.fab_new_chat);

        rvChats.setHasFixedSize(true);
        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));

        chatList = new ArrayList<>();
        otherParticipants = new ArrayList<>();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        adapter = new ChatsAdapter(chatList, otherParticipants, currentUser == null ? "" : currentUser.getUid(), (chat, otherUser) -> {
            if (getContext() == null) return;
            Intent intent = new Intent(getContext(), ChatActivity.class);
            intent.putExtra("chatId", chat.getChatId());
            intent.putExtra("isGroup", chat.isGroup());
            if (chat.isGroup()) {
                intent.putExtra("groupName", chat.getChatName());
            } else {
                String otherUid = "";
                String otherName = "User";
                if (otherUser != null && !TextUtils.isEmpty(otherUser.getUid())) {
                    otherUid = otherUser.getUid();
                    otherName = !TextUtils.isEmpty(otherUser.getUsername()) ? otherUser.getUsername() : "User";
                } else {
                    otherUid = getOtherParticipantUid(chat, currentUser == null ? "" : currentUser.getUid());
                }
                if (TextUtils.isEmpty(otherUid)) {
                    return;
                }
                intent.putExtra("otherUserId", otherUid);
                intent.putExtra("otherUserName", otherName);
            }
            clearUnread(chat.getChatId(), currentUser.getUid());
            startActivity(intent);
        }, (chat, otherUser, anchor) -> showChatActions(chat, otherUser));

        rvChats.setAdapter(adapter);
        fabNewChat.setOnClickListener(v -> startActivity(new Intent(getContext(), com.vchat.app.activities.NewChatActivity.class)));

        db = FirebaseFirestore.getInstance();

        if (currentUser != null) {
            loadChats();
        } else {
            tvNoChats.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        }

        return view;
    }

    private void loadChats() {
        String currentUid = currentUser.getUid();

        chatsListener = db.collection("chats")
                .whereEqualTo("participants." + currentUid, true)
                .whereEqualTo("isGroup", false)
                .addSnapshotListener((value, error) -> {
                    if (!isAdded()) return;
                    if (error != null) {
                        Log.e("ChatsFragment", "Listen failed.", error);
                        return;
                    }

                    if (value == null || value.isEmpty()) {
                        chatsById.clear();
                        allChats.clear();
                        userByUid.clear();
                        clearUserPresenceListeners();
                        clearRelationshipListeners();
                        clearDeliveryListeners();
                        tvNoChats.setVisibility(View.VISIBLE);
                        rvChats.setVisibility(View.GONE);
                        adapter.submitList(Collections.emptyList(), Collections.emptyList());
                        return;
                    }

                    for (DocumentChange change : value.getDocumentChanges()) {
                        String chatId = change.getDocument().getId();
                        if (TextUtils.isEmpty(chatId)) continue;

                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            chatsById.remove(chatId);
                            continue;
                        }

                        ChatModel chat = change.getDocument().toObject(ChatModel.class);
                        if (chat == null) continue;
                        chat.setChatId(chatId);
                        if (chat.isGroup()) continue;
                        Map<String, Boolean> deletedFor = (Map<String, Boolean>) change.getDocument().get("personalDeletedFor");
                        if (deletedFor == null) deletedFor = (Map<String, Boolean>) change.getDocument().get("deletedFor");
                        if (deletedFor != null && Boolean.TRUE.equals(deletedFor.get(currentUid))) {
                            chatsById.remove(chatId);
                            continue;
                        }
                        chatsById.put(chatId, chat);
                    }

                    allChats.clear();
                    allChats.addAll(chatsById.values());
                    allChats.sort(chatComparator(currentUid));
                    syncPresenceAndDeliveryListeners(currentUid);

                    tvNoChats.setVisibility(allChats.isEmpty() ? View.VISIBLE : View.GONE);
                    rvChats.setVisibility(allChats.isEmpty() ? View.GONE : View.VISIBLE);
                    applySearchQuery(searchQuery);
                });
    }

    private String getOtherParticipantUid(ChatModel chat, String currentUid) {
        Map<String, Boolean> participants = chat.getParticipants();
        if (participants == null) return "";

        for (String uid : participants.keySet()) {
            if (!uid.equals(currentUid)) return uid;
        }
        return "";
    }

    private void attachOtherUserListener(String otherUid) {
        ListenerRegistration existing = userPresenceListeners.get(otherUid);
        if (existing != null) return;
        ListenerRegistration registration = db.collection("users").document(otherUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || error != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        UserModel otherUser = snapshot.toObject(UserModel.class);
                        if (otherUser != null) {
                            userByUid.put(otherUid, otherUser);
                            attachRelationshipListener(otherUid);
                            applySearchQuery(searchQuery);
                        }
                    }
                });

        userPresenceListeners.put(otherUid, registration);
    }

    private void attachRelationshipListener(String otherUid) {
        if (currentUser == null || TextUtils.isEmpty(otherUid)) return;

        ListenerRegistration existing = relationshipListeners.get(otherUid);
        if (existing != null) return;

        ListenerRegistration registration = db.collection("users")
                .document(currentUser.getUid())
                .collection("relationships")
                .document(otherUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || error != null) return;
                    UserModel user = userByUid.get(otherUid);
                    if (user == null) user = new UserModel();
                    RelationshipStateHelper.bindRelationship(user, currentUser, snapshot);
                    userByUid.put(otherUid, user);
                    applySearchQuery(searchQuery);
                });

        relationshipListeners.put(otherUid, registration);
    }

    private void attachDeliveredListener(String chatId, String currentUid) {
        ListenerRegistration existing = deliveryListeners.get(chatId);
        if (existing != null) return;
        ListenerRegistration registration = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (!isAdded() || error != null || snapshots == null) return;

                    WriteBatch batch = db.batch();
                    boolean hasUpdates = false;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.ADDED) continue;

                        String senderId = change.getDocument().getString("senderId");
                        String status = change.getDocument().getString("status");
                        boolean isIncoming = senderId != null && !senderId.equals(currentUid);
                        if (isIncoming && "sent".equals(status)) {
                            batch.update(change.getDocument().getReference(), "status", "delivered");
                            hasUpdates = true;
                        }
                    }

                    if (hasUpdates) {
                        batch.commit().addOnFailureListener(e -> Log.e("ChatsFragment", "Delivered update failed", e));
                    }
                });

        deliveryListeners.put(chatId, registration);
    }

    private void syncPresenceAndDeliveryListeners(String currentUid) {
        Set<String> targetUserUids = new HashSet<>();
        Set<String> targetChatIds = new HashSet<>();

        for (ChatModel chat : allChats) {
            String chatId = chat.getChatId();
            if (!TextUtils.isEmpty(chatId)) {
                targetChatIds.add(chatId);
            }
            String otherUid = getOtherParticipantUid(chat, currentUid);
            if (!TextUtils.isEmpty(otherUid)) {
                targetUserUids.add(otherUid);
            }
        }

        removeUnusedListeners(userPresenceListeners, targetUserUids);
        removeUnusedListeners(relationshipListeners, targetUserUids);
        removeUnusedListeners(deliveryListeners, targetChatIds);
        pruneUsersCache(targetUserUids);

        for (String uid : targetUserUids) {
            attachOtherUserListener(uid);
        }
        for (String chatId : targetChatIds) {
            attachDeliveredListener(chatId, currentUid);
        }
    }

    private void removeUnusedListeners(Map<String, ListenerRegistration> registrations, Set<String> activeKeys) {
        List<String> keys = new ArrayList<>(registrations.keySet());
        for (String key : keys) {
            if (activeKeys.contains(key)) continue;
            ListenerRegistration registration = registrations.remove(key);
            if (registration != null) registration.remove();
        }
    }

    private void pruneUsersCache(Set<String> activeUserUids) {
        List<String> cachedUids = new ArrayList<>(userByUid.keySet());
        for (String uid : cachedUids) {
            if (!activeUserUids.contains(uid)) {
                userByUid.remove(uid);
            }
        }
    }

    private void showChatActions(ChatModel chat, UserModel otherUser) {
        if (getContext() == null || chat == null || TextUtils.isEmpty(chat.getChatId())) return;
        String title = otherUser != null && !TextUtils.isEmpty(otherUser.getUsername()) ? otherUser.getUsername() : "Chat";
        boolean pinned = isPinnedForUser(chat, currentUser.getUid());
        boolean archived = isArchivedForUser(chat, currentUser.getUid());
        Log.d(CHATLIST_STATE_TAG, "chatId=" + chat.getChatId() + " deleted=" + isDeletedForUser(chat, currentUser.getUid())
                + " pinState=" + pinned + " archiveState=" + archived + " selectedMenuAction=open_actions");
        ChatRowActionsBottomSheet.show(getContext(), title, false, pinned, archived, new ChatRowActionsBottomSheet.ActionListener() {
            @Override
            public void onPin() {
                boolean pinned = isPinnedForUser(chat, currentUser.getUid());
                Log.d(CHATLIST_STATE_TAG, "chatId=" + chat.getChatId() + " deleted=" + isDeletedForUser(chat, currentUser.getUid())
                        + " pinState=" + pinned + " archiveState=" + isArchivedForUser(chat, currentUser.getUid()) + " selectedMenuAction=" + (pinned ? "unpin" : "pin"));
                updateChatMeta(chat.getChatId(), KEY_PINNED + currentUser.getUid(),
                        pinned ? FieldValue.delete() : true,
                        pinned ? "Chat unpinned" : "Chat pinned");
            }

            @Override
            public void onArchive() {
                boolean archived = isArchivedForUser(chat, currentUser.getUid());
                Log.d(CHATLIST_STATE_TAG, "chatId=" + chat.getChatId() + " deleted=" + isDeletedForUser(chat, currentUser.getUid())
                        + " pinState=" + isPinnedForUser(chat, currentUser.getUid()) + " archiveState=" + archived + " selectedMenuAction=" + (archived ? "unarchive" : "archive"));
                updateChatMeta(chat.getChatId(), KEY_ARCHIVED + currentUser.getUid(),
                        archived ? FieldValue.delete() : true,
                        archived ? "Chat unarchived" : "Chat archived");
            }

            @Override
            public void onMute() {
                if (otherUser != null && !TextUtils.isEmpty(otherUser.getUid())) {
                    db.collection("users").document(currentUser.getUid()).collection("relationships").document(otherUser.getUid()).get()
                            .addOnSuccessListener(rel -> {
                                String state = rel.getString("state");
                                String blockedBy = rel.getString("blockedBy");
                                boolean iBlocked = "blocked".equals(state) && TextUtils.equals(currentUser.getUid(), blockedBy);
                                if (iBlocked) {
                                    updateRelationship(currentUser.getUid(), otherUser.getUid(), RelationshipStateHelper.STATE_REMOVED, "", "User unblocked");
                                } else {
                                    updateRelationship(currentUser.getUid(), otherUser.getUid(), "blocked", currentUser.getUid(), "User blocked");
                                }
                            });
                } else {
                    updateChatMeta(chat.getChatId(), KEY_MUTED + currentUser.getUid(), true, "Chat muted");
                }
            }

            @Override
            public void onDelete() {
                Log.d(CHATLIST_STATE_TAG, "chatId=" + chat.getChatId() + " deleted=" + isDeletedForUser(chat, currentUser.getUid())
                        + " pinState=" + isPinnedForUser(chat, currentUser.getUid()) + " archiveState=" + isArchivedForUser(chat, currentUser.getUid()) + " selectedMenuAction=delete_chat");
                deleteChatForCurrentUser(chat);
            }

            @Override
            public void onMarkUnread() {
                updateChatMeta(chat.getChatId(), KEY_UNREAD + currentUser.getUid(), true, "Marked as unread");
            }

            @Override
            public void onInfo() {
                Intent intent = new Intent(getContext(), com.vchat.app.activities.FriendInfoActivity.class);
                intent.putExtra("otherUserId", otherUser == null ? "" : otherUser.getUid());
                intent.putExtra("otherUserName", title);
                startActivity(intent);
            }
        });
    }

    private void updateChatMeta(String chatId, String field, Object value, String successText) {
        if (currentUser == null || TextUtils.isEmpty(chatId)) return;
        Log.d(TAG, "updateChatMeta chatId=" + chatId + " field=" + field + " value=" + value);
        db.collection("chats").document(chatId)
                .update(field, value, "lastActionAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused ->
                        android.widget.Toast.makeText(getContext(), successText, android.widget.Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Log.e(TAG, "updateChatMeta failed chatId=" + chatId, e));
    }

    private void deleteChatForCurrentUser(ChatModel chat) {
        if (currentUser == null || chat == null || TextUtils.isEmpty(chat.getChatId())) return;
        String chatId = chat.getChatId();
        String uid = currentUser.getUid();
        String field = KEY_DELETED + uid;

        Log.d(CHATLIST_DELETE_TAG, "action=delete_chat start chatId=" + chatId + " uid=" + uid + " field=" + field);

        // Optimistic local removal for immediate UX.
        ChatModel previous = chatsById.remove(chatId);
        if (previous != null) {
            allChats.clear();
            allChats.addAll(chatsById.values());
            allChats.sort(chatComparator(uid));
            syncPresenceAndDeliveryListeners(uid);
            applySearchQuery(searchQuery);
            Log.d(CHATLIST_DELETE_TAG, "action=delete_chat local_remove_applied chatId=" + chatId);
        }

        db.collection("chats").document(chatId)
                .update(
                        field, true,
                        "chatDeletedAt." + uid, System.currentTimeMillis(),
                        "lastActionAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {
                    Log.d(CHATLIST_DELETE_TAG, "action=delete_chat success chatId=" + chatId + " uid=" + uid);
                    android.widget.Toast.makeText(getContext(), "Chat deleted for you", android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(CHATLIST_DELETE_TAG, "action=delete_chat failure chatId=" + chatId + " uid=" + uid, e);
                    if (previous != null) {
                        chatsById.put(chatId, previous);
                        allChats.clear();
                        allChats.addAll(chatsById.values());
                        allChats.sort(chatComparator(uid));
                        syncPresenceAndDeliveryListeners(uid);
                        applySearchQuery(searchQuery);
                        Log.d(CHATLIST_DELETE_TAG, "action=delete_chat local_remove_rollback chatId=" + chatId);
                    }
                    android.widget.Toast.makeText(getContext(), "Failed to delete chat", android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    private void clearUnread(String chatId, String uid) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(uid)) return;
        db.collection("chats").document(chatId)
                .update(KEY_UNREAD + uid, com.google.firebase.firestore.FieldValue.delete(), "unreadBy." + uid, com.google.firebase.firestore.FieldValue.delete());
    }

    private void updateRelationship(String uidA, String uidB, String state, String blockedBy, String toastText) {
        String normalized = RelationshipStateHelper.normalizeState(state);
        String stateToWrite = RelationshipStateHelper.CANON_FRIEND.equals(normalized) ? RelationshipStateHelper.STATE_FRIEND : normalized;
        Log.d(TAG, "updateRelationship state=" + stateToWrite + " uidA=" + uidA + " uidB=" + uidB + " blockedBy=" + blockedBy);
        Map<String, Object> relA = new HashMap<>();
        relA.put("state", stateToWrite);
        relA.put("blockedBy", blockedBy);
        relA.put("peerUid", uidB);
        relA.put("initiatedBy", uidA);
        relA.put("updatedAt", FieldValue.serverTimestamp());
        com.google.android.gms.tasks.Task<Void> taskA = db.collection("users").document(uidA).collection("relationships").document(uidB).set(relA);

        Map<String, Object> relB = new HashMap<>();
        relB.put("state", stateToWrite);
        relB.put("blockedBy", blockedBy);
        relB.put("peerUid", uidA);
        relB.put("initiatedBy", uidA);
        relB.put("updatedAt", FieldValue.serverTimestamp());
        com.google.android.gms.tasks.Task<Void> taskB = db.collection("users").document(uidB).collection("relationships").document(uidA).set(relB);
        Log.d(REL_CLEANUP_TAG, "source=ChatsFragment writePayload state=" + stateToWrite + " normalizedState=" + normalized
                + " uidA=" + uidA + " uidB=" + uidB + " blockedBy=" + blockedBy);

        com.google.android.gms.tasks.Tasks.whenAll(taskA, taskB)
                .addOnSuccessListener(unused -> {
                    Log.d(REL_CLEANUP_TAG, "source=ChatsFragment mirroredUpdates pathA=users/" + uidA + "/relationships/" + uidB
                            + " pathB=users/" + uidB + "/relationships/" + uidA);
                    if ("removed".equals(stateToWrite) || "blocked".equals(stateToWrite)) {
                        clearPendingRequestsBetween(uidA, uidB);
                    }
                    android.widget.Toast.makeText(getContext(), toastText, android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e(TAG, "updateRelationship failed", e));
    }

    private void clearPendingRequestsBetween(String uidA, String uidB) {
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> task1 = db.collection("chat_requests")
                .whereEqualTo("status", "pending")
                .whereEqualTo("senderId", uidA)
                .whereEqualTo("receiverId", uidB)
                .get();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> task2 = db.collection("chat_requests")
                .whereEqualTo("status", "pending")
                .whereEqualTo("senderId", uidB)
                .whereEqualTo("receiverId", uidA)
                .get();

        com.google.android.gms.tasks.Tasks.whenAllSuccess(task1, task2)
                .addOnSuccessListener(results -> {
                    WriteBatch batch = db.batch();
                    boolean hasUpdates = false;
                    for (Object res : results) {
                        com.google.firebase.firestore.QuerySnapshot snapshots = (com.google.firebase.firestore.QuerySnapshot) res;
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            batch.update(doc.getReference(), "status", "cancelled", "updatedAt", FieldValue.serverTimestamp());
                            hasUpdates = true;
                        }
                    }
                    if (hasUpdates) {
                        batch.commit().addOnFailureListener(e -> Log.e(TAG, "Failed clearing pending requests", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Querying pending requests failed", e));
    }

    private void clearUserPresenceListeners() {
        for (ListenerRegistration registration : userPresenceListeners.values()) {
            if (registration != null) registration.remove();
        }
        userPresenceListeners.clear();
    }

    private void clearRelationshipListeners() {
        for (ListenerRegistration registration : relationshipListeners.values()) {
            if (registration != null) registration.remove();
        }
        relationshipListeners.clear();
    }

    private void clearDeliveryListeners() {
        for (ListenerRegistration registration : deliveryListeners.values()) {
            if (registration != null) registration.remove();
        }
        deliveryListeners.clear();
    }

    public void applySearchQuery(String query) {
        if (!isAdded()) return;

        searchQuery = query == null ? "" : query.trim().toLowerCase();

        List<ChatModel> nextChats = new ArrayList<>();
        List<UserModel> nextParticipants = new ArrayList<>();

        for (ChatModel chat : allChats) {
            String otherUid = getOtherParticipantUid(chat, currentUser == null ? "" : currentUser.getUid());
            UserModel other = TextUtils.isEmpty(otherUid) ? new UserModel() : userByUid.get(otherUid);
            if (other == null) other = new UserModel();
            if (isMatch(chat, other, searchQuery)) {
                nextChats.add(chat);
                nextParticipants.add(other);
            }
        }

        adapter.submitList(nextChats, nextParticipants);

        if (nextChats.isEmpty()) {
            tvNoChats.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        } else {
            tvNoChats.setVisibility(View.GONE);
            rvChats.setVisibility(View.VISIBLE);
        }
    }

    private boolean isMatch(ChatModel chat, UserModel other, String query) {
        if (TextUtils.isEmpty(query)) return true;

        if (chat != null && chat.isGroup()) {
            String groupName = chat.getChatName();
            return (!TextUtils.isEmpty(groupName) && groupName.toLowerCase().contains(query))
                    || (!TextUtils.isEmpty(chat.getLastMessage()) && chat.getLastMessage().toLowerCase().contains(query));
        }

        String username = other == null ? "" : other.getUsername();
        if (!TextUtils.isEmpty(username) && username.toLowerCase().contains(query)) return true;

        String email = other == null ? "" : other.getEmail();
        if (!TextUtils.isEmpty(email) && email.toLowerCase().contains(query)) return true;

        String lastMessage = chat == null ? "" : chat.getLastMessage();
        return !TextUtils.isEmpty(lastMessage) && lastMessage.toLowerCase().contains(query);
    }

    private Comparator<ChatModel> chatComparator(String uid) {
        return (left, right) -> {
            int pinnedCompare = Boolean.compare(isPinnedForUser(right, uid), isPinnedForUser(left, uid));
            if (pinnedCompare != 0) return pinnedCompare;

            int archivedCompare = Boolean.compare(isArchivedForUser(left, uid), isArchivedForUser(right, uid));
            if (archivedCompare != 0) return archivedCompare;

            int tsCompare = Long.compare(right.getLastMessageTimestamp(), left.getLastMessageTimestamp());
            if (tsCompare != 0) return tsCompare;

            return safe(left.getChatId()).compareTo(safe(right.getChatId()));
        };
    }

    private boolean isPinnedForUser(ChatModel chat, String uid) {
        return isFlagSet(chat.getPersonalPinnedBy(), uid) || isFlagSet(chat.getPinnedBy(), uid);
    }

    private boolean isArchivedForUser(ChatModel chat, String uid) {
        return isFlagSet(chat.getPersonalArchivedBy(), uid) || isFlagSet(chat.getArchivedBy(), uid);
    }

    private boolean isDeletedForUser(ChatModel chat, String uid) {
        return isFlagSet(chat.getPersonalDeletedFor(), uid) || isFlagSet(chat.getDeletedFor(), uid);
    }

    private boolean isFlagSet(Map<String, Boolean> map, String uid) {
        return map != null && !TextUtils.isEmpty(uid) && Boolean.TRUE.equals(map.get(uid));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }
        clearUserPresenceListeners();
        clearRelationshipListeners();
        clearDeliveryListeners();
    }
}
