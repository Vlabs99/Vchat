package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vchat.app.R;
import com.vchat.app.adapters.FriendPickerAdapter;
import com.vchat.app.models.UserModel;
import com.vchat.app.utils.RelationshipStateHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NewChatActivity extends AppCompatActivity {
    private static final String TAG = "NewChatActivity";
    private static final String DEBUG_TAG = "VCHAT_NEWCHAT_DEBUG";
    private static final String REL_SYNC_TAG = "VCHAT_REL_SYNC";
    private static final String REL_CLEANUP_TAG = "VCHAT_REL_CLEANUP";
    private static final String CHATLIST_STATE_TAG = "VCHAT_CHATLIST_STATE";
    private EditText etSearch;
    private TextView tvEmpty;
    private ProgressBar progress;
    private FriendPickerAdapter adapter;

    private final List<UserModel> rows = new ArrayList<>();
    private final Map<String, UserModel> userByUid = new HashMap<>();
    private final Map<String, Map<String, Object>> relationshipByUid = new HashMap<>();
    private final Map<String, ListenerRegistration> userListeners = new HashMap<>();

    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private ListenerRegistration relationshipsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_chat);
        Toolbar toolbar = findViewById(R.id.toolbar_new_chat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.et_search_friend);
        tvEmpty = findViewById(R.id.tv_new_chat_empty);
        progress = findViewById(R.id.progress_new_chat);
        RecyclerView rv = findViewById(R.id.rv_new_chat_friends);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendPickerAdapter(this::openOrCreateChat);
        rv.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        Log.d(DEBUG_TAG, "onCreate currentUserId=" + currentUser.getUid());

        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                adapter.filter(s == null ? "" : s.toString());
                toggleState();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        loadRelationshipRows();
    }

    private void loadRelationshipRows() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("Loading contacts...");
        if (relationshipsListener != null) relationshipsListener.remove();
        String myUid = currentUser == null ? "" : currentUser.getUid();
        String relPath = "users/" + myUid + "/relationships";
        Log.d(DEBUG_TAG, "Starting relationships listener path=" + relPath + ", filters=none");
        relationshipsListener = db.collection("users")
                .document(myUid)
                .collection("relationships")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "relationships listener failed", error);
                        Log.d(DEBUG_TAG, "Listener failure path=" + relPath + ", exception=" + Log.getStackTraceString(error));
                        progress.setVisibility(View.GONE);
                        if (adapter.size() == 0) {
                            tvEmpty.setText("Could not load contacts");
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                        return;
                    }
                    if (value == null) {
                        Log.d(DEBUG_TAG, "Listener returned null snapshot path=" + relPath);
                        progress.setVisibility(View.GONE);
                        return;
                    }
                    Log.d(DEBUG_TAG, "Listener success path=" + relPath + ", docs=" + value.size());

                    Set<String> liveUids = new HashSet<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String peerUid = doc.getString("peerUid");
                        Log.d(DEBUG_TAG, "Relationship doc id=" + doc.getId() + ", peerUid=" + peerUid + ", raw=" + String.valueOf(doc.getData()));
                        if (TextUtils.isEmpty(peerUid) || TextUtils.equals(peerUid, currentUser.getUid())) continue;
                        liveUids.add(peerUid);
                        relationshipByUid.put(peerUid, doc.getData() == null ? new HashMap<>() : doc.getData());
                        ensureUserListener(peerUid);
                    }

                    cleanupStaleUserListeners(liveUids);
                    Log.d(TAG, "relationships loaded uids=" + liveUids.size());
                    rebuildRowsAndRender();
                    progress.setVisibility(View.GONE);
                });
    }

    private void ensureUserListener(String peerUid) {
        if (userListeners.containsKey(peerUid)) return;
        String userPath = "users/" + peerUid;
        Log.d(DEBUG_TAG, "Starting user listener path=" + userPath + ", sourcePeerUid=" + peerUid);
        ListenerRegistration reg = db.collection("users").document(peerUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.d(DEBUG_TAG, "User listener failure path=" + userPath + ", exception=" + Log.getStackTraceString(error));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        Log.d(DEBUG_TAG, "User listener empty snapshot path=" + userPath);
                        userByUid.remove(peerUid);
                        rebuildRowsAndRender();
                        return;
                    }
                    Log.d(DEBUG_TAG, "User listener success path=" + userPath + ", fields=" + String.valueOf(snapshot.getData()));
                    UserModel model = snapshot.toObject(UserModel.class);
                    if (model == null) {
                        userByUid.remove(peerUid);
                        rebuildRowsAndRender();
                        return;
                    }
                    if (TextUtils.isEmpty(model.getUid())) {
                        model.setUid(snapshot.getId());
                    }
                    userByUid.put(peerUid, model);
                    rebuildRowsAndRender();
                });
        userListeners.put(peerUid, reg);
    }

    private void cleanupStaleUserListeners(Set<String> liveUids) {
        List<String> stale = new ArrayList<>();
        for (String uid : userListeners.keySet()) {
            if (!liveUids.contains(uid)) stale.add(uid);
        }
        for (String uid : stale) {
            ListenerRegistration reg = userListeners.remove(uid);
            if (reg != null) reg.remove();
            userByUid.remove(uid);
            relationshipByUid.remove(uid);
        }
    }

    private void rebuildRowsAndRender() {
        rows.clear();
        for (Map.Entry<String, Map<String, Object>> entry : relationshipByUid.entrySet()) {
            String uid = entry.getKey();
            UserModel source = userByUid.get(uid);
            if (source == null) continue;
            UserModel item = copyUser(source);
            applyRelationshipState(item, entry.getValue());
            rows.add(item);
        }

        rows.sort((a, b) -> safeName(a).compareToIgnoreCase(safeName(b)));
        Log.d(TAG, "Submitting new chat rows size=" + rows.size());
        Log.d(DEBUG_TAG, "Adapter submit rows=" + rows.size() + ", relationshipCount=" + relationshipByUid.size() + ", userCount=" + userByUid.size());
        adapter.submit(rows);
        adapter.filter(etSearch.getText() == null ? "" : etSearch.getText().toString());
        toggleState();
    }

    private void applyRelationshipState(UserModel user, Map<String, Object> rel) {
        String state = asString(rel.get("state"));
        String blockedBy = asString(rel.get("blockedBy"));
        String initiatedBy = asString(rel.get("initiatedBy"));
        String myUid = currentUser == null ? "" : currentUser.getUid();
        String canonicalState = RelationshipStateHelper.resolveCanonicalState(state, blockedBy, myUid);
        Log.d(REL_SYNC_TAG, "source=NewChatActivity rawState=" + state + " normalizedState=" + canonicalState
                + " blockedBy=" + blockedBy + " peerUid=" + user.getUid());

        if (RelationshipStateHelper.CANON_BLOCKED.equals(canonicalState)) {
            user.setFriendshipState(RelationshipStateHelper.VIEW_BLOCKED_BY_ME);
            user.setStateLabel("BLOCKED");
            Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=BLOCKED normalizedState=" + canonicalState + " peerUid=" + user.getUid());
            return;
        }
        if (RelationshipStateHelper.CANON_BLOCKED_BY_USER.equals(canonicalState)) {
            user.setFriendshipState(RelationshipStateHelper.VIEW_BLOCKED_ME);
            user.setStateLabel("BLOCKED YOU");
            Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=BLOCKED YOU normalizedState=" + canonicalState + " peerUid=" + user.getUid());
            return;
        }
        if (RelationshipStateHelper.CANON_PENDING.equals(canonicalState)) {
            if (TextUtils.equals(myUid, initiatedBy)) {
                user.setFriendshipState(RelationshipStateHelper.VIEW_PENDING_OUT);
                user.setStateLabel("REQUEST PENDING");
            } else {
                user.setFriendshipState(RelationshipStateHelper.VIEW_PENDING_IN);
                user.setStateLabel("REQUEST PENDING");
            }
            Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=REQUEST PENDING normalizedState=" + canonicalState + " peerUid=" + user.getUid());
            return;
        }
        if (RelationshipStateHelper.CANON_REMOVED.equals(canonicalState)) {
            user.setFriendshipState(RelationshipStateHelper.VIEW_REMOVED);
            user.setStateLabel("NOT FRIENDS");
            Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=NOT FRIENDS normalizedState=" + canonicalState + " peerUid=" + user.getUid());
            return;
        }
        if (RelationshipStateHelper.CANON_FRIEND.equals(canonicalState)) {
            user.setFriendshipState(RelationshipStateHelper.VIEW_FRIENDS);
            user.setStateLabel("FRIEND");
            Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=FRIEND normalizedState=" + canonicalState + " peerUid=" + user.getUid());
            return;
        }
        user.setFriendshipState(RelationshipStateHelper.VIEW_REMOVED);
        user.setStateLabel("NOT FRIENDS");
        Log.d(REL_CLEANUP_TAG, "source=NewChatActivity finalUiState=NOT FRIENDS normalizedState=" + canonicalState + " peerUid=" + user.getUid());
    }

    private void toggleState() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        if (adapter.size() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(TextUtils.isEmpty(q) ? "No contacts available for new chat" : "No contacts match \"" + q + "\"");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void openOrCreateChat(UserModel other) {
        if (other == null || TextUtils.isEmpty(other.getUid()) || currentUser == null) return;
        String myUid = currentUser.getUid();
        String state = other.getFriendshipState();

        if (RelationshipStateHelper.VIEW_BLOCKED_BY_ME.equals(state)) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("You blocked this user. Unblock from friend actions first.");
            return;
        }
        if (RelationshipStateHelper.VIEW_BLOCKED_ME.equals(state)) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("You are blocked");
            return;
        }
        if (RelationshipStateHelper.VIEW_REMOVED.equals(state)) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Send friend request again to continue chatting");
            return;
        }
        if (RelationshipStateHelper.VIEW_PENDING_OUT.equals(state) || RelationshipStateHelper.VIEW_PENDING_IN.equals(state)) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Friend request pending");
            return;
        }
        if (!RelationshipStateHelper.VIEW_FRIENDS.equals(state)) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Only accepted friends can be started as a new chat");
            return;
        }

        String chatId = myUid.compareTo(other.getUid()) < 0 ? myUid + "_" + other.getUid() : other.getUid() + "_" + myUid;
        db.collection("chats").document(chatId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Map<String, Object> raw = doc.getData();
                Map<String, Boolean> personalDeleted = raw != null ? (Map<String, Boolean>) raw.get("personalDeletedFor") : null;
                Map<String, Boolean> legacyDeleted = raw != null ? (Map<String, Boolean>) raw.get("deletedFor") : null;
                boolean hidden = (personalDeleted != null && Boolean.TRUE.equals(personalDeleted.get(myUid)))
                        || (legacyDeleted != null && Boolean.TRUE.equals(legacyDeleted.get(myUid)));
                Log.d(CHATLIST_STATE_TAG, "chatId=" + chatId + " deletedState=" + hidden + " restorePath=" + (hidden ? "restore_hidden_chat" : "open_existing_chat"));
                if (hidden) {
                    db.collection("chats").document(chatId)
                            .update(
                                    "personalDeletedFor." + myUid, com.google.firebase.firestore.FieldValue.delete(),
                                    "deletedFor." + myUid, com.google.firebase.firestore.FieldValue.delete(),
                                    "lastActionAt", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(unused -> {
                                Log.d(CHATLIST_STATE_TAG, "chatId=" + chatId + " deletedState=false restorePath=restore_success");
                                goChat(chatId, other);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(CHATLIST_STATE_TAG, "chatId=" + chatId + " restorePath=restore_failure", e);
                                goChat(chatId, other);
                            });
                    return;
                }
                goChat(chatId, other);
                return;
            }
            Map<String, Object> chatData = new HashMap<>();
            Map<String, Boolean> participants = new HashMap<>();
            participants.put(myUid, true);
            participants.put(other.getUid(), true);
            chatData.put("chatId", chatId);
            chatData.put("isGroup", false);
            chatData.put("participants", participants);
            chatData.put("createdAt", System.currentTimeMillis());
            chatData.put("lastMessage", "");
            chatData.put("lastMessageTimestamp", 0L);
            chatData.put("lastActionAt", FieldValue.serverTimestamp());
            db.collection("chats").document(chatId)
                    .set(chatData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> goChat(chatId, other));
        });
    }

    private void goChat(String chatId, UserModel other) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("chatId", chatId);
        i.putExtra("isGroup", false);
        i.putExtra("otherUserId", other.getUid());
        i.putExtra("otherUserName", safeName(other));
        startActivity(i);
        finish();
    }

    private UserModel copyUser(UserModel source) {
        UserModel copy = new UserModel();
        copy.setUid(source.getUid());
        copy.setUsername(source.getUsername());
        copy.setEmail(source.getEmail());
        copy.setBio(source.getBio());
        copy.setProfileImage(source.getProfileImage());
        copy.setOnline(source.isOnline());
        copy.setLastSeen(source.getLastSeenRaw());
        copy.setTypingTo(source.getTypingTo());
        return copy;
    }

    private String safeName(UserModel model) {
        if (model == null) return "Unknown user";
        if (!TextUtils.isEmpty(model.getUsername())) return model.getUsername().trim();
        if (!TextUtils.isEmpty(model.getEmail())) return model.getEmail().trim();
        if (!TextUtils.isEmpty(model.getUid())) return model.getUid();
        return "Unknown user";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (relationshipsListener != null) {
            relationshipsListener.remove();
            relationshipsListener = null;
        }
        for (ListenerRegistration registration : userListeners.values()) {
            if (registration != null) registration.remove();
        }
        userListeners.clear();
        userByUid.clear();
        relationshipByUid.clear();
    }
}
