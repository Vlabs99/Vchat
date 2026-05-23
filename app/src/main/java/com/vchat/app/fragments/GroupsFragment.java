package com.vchat.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.vchat.app.R;
import com.vchat.app.activities.ChatActivity;
import com.vchat.app.activities.CreateGroupActivity;
import com.vchat.app.activities.GroupInfoActivity;
import com.vchat.app.adapters.GroupsAdapter;
import com.vchat.app.chat.ChatRowActionsBottomSheet;
import com.vchat.app.data.GroupRepository;
import com.vchat.app.models.ChatModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupsFragment extends Fragment {
    private static final String TAG = "GroupsFragment";
    private static final String RULES_TAG = "VCHAT_GROUP_RULES";
    private static final String TAG_GROUP_CREATE = "VCHAT_GROUP_CREATE";
    private static final String TAG_GROUP_TAB_FIX = "VCHAT_GROUP_TAB_FIX";
    private static final String KEY_PINNED = "groupPinnedBy.";
    private static final String KEY_ARCHIVED = "groupArchivedBy.";
    private static final String KEY_MUTED = "groupMutedBy.";
    private static final String KEY_DELETED = "groupDeletedFor.";
    private static final String KEY_UNREAD = "groupUnreadBy.";

    private RecyclerView rvGroups;
    private TextView tvNoGroups;
    private ProgressBar progressGroups;
    private EditText etGroupSearch;
    private ImageButton btnCreateGroup;

    private final List<ChatModel> allGroups = new ArrayList<>();
    private final List<ChatModel> filteredGroups = new ArrayList<>();
    private final Map<String, ChatModel> groupsById = new HashMap<>();
    private String activeQuery = "";
    private GroupsAdapter adapter;

    private FirebaseUser currentUser;
    private GroupRepository repository;
    private com.google.firebase.firestore.ListenerRegistration groupsListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);

        rvGroups = view.findViewById(R.id.rv_groups);
        tvNoGroups = view.findViewById(R.id.tv_no_groups);
        progressGroups = view.findViewById(R.id.progress_groups);
        etGroupSearch = view.findViewById(R.id.et_group_search);
        btnCreateGroup = view.findViewById(R.id.btn_create_group);

        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setHasFixedSize(true);

        adapter = new GroupsAdapter(filteredGroups, this::openGroupChat, this::showGroupActions);
        rvGroups.setAdapter(adapter);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        repository = new GroupRepository();

        btnCreateGroup.setOnClickListener(v -> {
            String uid = currentUser == null ? "" : currentUser.getUid();
            Log.d(TAG_GROUP_CREATE, "openCreateGroup currentUserId=" + uid);
            if (isAdded() && getContext() != null) {
                startActivity(new Intent(getContext(), CreateGroupActivity.class));
            }
        });

        etGroupSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (currentUser != null) {
            adapter.setCurrentUid(currentUser.getUid());
            listenGroups();
        }

        return view;
    }

    private void listenGroups() {
        progressGroups.setVisibility(View.VISIBLE);
        tvNoGroups.setVisibility(View.GONE);
        String uid = currentUser == null ? "" : currentUser.getUid();
        Log.d(RULES_TAG, "source=GroupsFragment listenerStart path=chats filters=[isGroup==true,participants." + uid + "==true] currentUserId=" + uid);
        groupsListener = repository.listenMyGroups(currentUser.getUid(), (snapshots, error) -> {
            if (!isAdded()) return;
            progressGroups.setVisibility(View.GONE);
            if (error != null || snapshots == null) {
                Log.e(TAG, "Group listener failed", error);
                Log.d(RULES_TAG, "source=GroupsFragment listenerFailure path=chats currentUserId=" + uid
                        + " exception=" + Log.getStackTraceString(error));
                tvNoGroups.setText("Could not load groups");
                tvNoGroups.setVisibility(View.VISIBLE);
                if (filteredGroups.isEmpty()) {
                    rvGroups.setVisibility(View.GONE);
                }
                return;
            }
            Log.d(RULES_TAG, "source=GroupsFragment listenerSuccess path=chats currentUserId=" + uid + " docs=" + snapshots.size());

            List<com.google.firebase.firestore.DocumentChange> changes = snapshots.getDocumentChanges();
            if (changes.isEmpty() && !snapshots.getDocuments().isEmpty()) {
                Log.d(TAG, "No incremental changes; rebuilding from full snapshot size=" + snapshots.size());
                groupsById.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                    String id = doc.getId();
                    if (TextUtils.isEmpty(id)) continue;
                    ChatModel group = doc.toObject(ChatModel.class);
                    if (group == null) continue;
                    group.setChatId(id);
                    Map<String, Boolean> deletedFor = (Map<String, Boolean>) doc.get("groupDeletedFor");
                    if (deletedFor == null) deletedFor = (Map<String, Boolean>) doc.get("deletedFor");
                    if (deletedFor != null && Boolean.TRUE.equals(deletedFor.get(currentUser.getUid()))) continue;
                    groupsById.put(id, group);
                }
            } else {
                for (com.google.firebase.firestore.DocumentChange change : changes) {
                    com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();
                    String id = doc.getId();
                    if (TextUtils.isEmpty(id)) continue;

                    if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        groupsById.remove(id);
                        continue;
                    }

                    ChatModel group = doc.toObject(ChatModel.class);
                    if (group == null) continue;
                    group.setChatId(id);
                    Map<String, Boolean> deletedFor = (Map<String, Boolean>) doc.get("groupDeletedFor");
                    if (deletedFor == null) deletedFor = (Map<String, Boolean>) doc.get("deletedFor");
                    if (deletedFor != null && Boolean.TRUE.equals(deletedFor.get(currentUser.getUid()))) {
                        groupsById.remove(id);
                    } else {
                        groupsById.put(id, group);
                    }
                }
            }
            Log.d(TAG, "Groups map size after update=" + groupsById.size());
            rebuildAndApplyFilter(activeQuery);
        });
    }

    private void applyFilter(String query) {
        activeQuery = query == null ? "" : query.trim().toLowerCase();
        rebuildAndApplyFilter(activeQuery);
    }

    private void rebuildAndApplyFilter(String q) {
        allGroups.clear();
        allGroups.addAll(groupsById.values());
        allGroups.sort((left, right) -> {
            String uid = currentUser == null ? "" : currentUser.getUid();
            int pinnedCompare = Boolean.compare(isPinnedForUser(right, uid), isPinnedForUser(left, uid));
            if (pinnedCompare != 0) return pinnedCompare;

            int archivedCompare = Boolean.compare(isArchivedForUser(left, uid), isArchivedForUser(right, uid));
            if (archivedCompare != 0) return archivedCompare;

            int tsCompare = Long.compare(right.getLastMessageTimestamp(), left.getLastMessageTimestamp());
            if (tsCompare != 0) return tsCompare;

            return safe(left.getChatId()).compareTo(safe(right.getChatId()));
        });

        filteredGroups.clear();
        for (ChatModel group : allGroups) {
            String name = group.getChatName();
            String last = group.getLastMessage();
            if (TextUtils.isEmpty(q)
                    || (!TextUtils.isEmpty(name) && name.toLowerCase().contains(q))
                    || (!TextUtils.isEmpty(last) && last.toLowerCase().contains(q))) {
                filteredGroups.add(group);
            }
        }

        adapter.submitList(filteredGroups);
        boolean hasData = !filteredGroups.isEmpty();
        rvGroups.setVisibility(hasData ? View.VISIBLE : View.GONE);
        tvNoGroups.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) {
            tvNoGroups.setText(TextUtils.isEmpty(q) ? "No groups yet" : "No groups match \"" + q + "\"");
        }
    }

    private void openGroupChat(ChatModel group) {
        if (currentUser == null) return;
        if (isAdded() && getContext() != null) {
            Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("chatId", group.getChatId());
        intent.putExtra("isGroup", true);
        intent.putExtra("groupName", group.getChatName());
        clearUnread(group.getChatId(), currentUser.getUid());
        startActivity(intent);
        }
    }

    private void showGroupActions(ChatModel group, View anchor) {
        String title = TextUtils.isEmpty(group.getChatName()) ? "Group" : group.getChatName();
        boolean pinned = isPinnedForUser(group, currentUser.getUid());
        boolean archived = isArchivedForUser(group, currentUser.getUid());
        String pinLabel = pinned ? "Unpin Group" : "Pin Group";
        String archiveLabel = archived ? "Unarchive Group" : "Archive Group";
        Log.d(TAG_GROUP_TAB_FIX, "chatId=" + group.getChatId() + " isGroup=true pinState=" + pinned
                + " archiveState=" + archived + " displayedLabel=" + pinLabel + "|" + archiveLabel + " selectedAction=open_actions");
        if (isAdded() && getContext() != null) {
            ChatRowActionsBottomSheet.show(getContext(), title, true, pinned, archived, new ChatRowActionsBottomSheet.ActionListener() {
            @Override
            public void onPin() {
                boolean pinned = isPinnedForUser(group, currentUser.getUid());
                Log.d(TAG_GROUP_TAB_FIX, "chatId=" + group.getChatId() + " isGroup=true pinState=" + pinned
                        + " archiveState=" + isArchivedForUser(group, currentUser.getUid())
                        + " displayedLabel=" + (pinned ? "Unpin Group" : "Pin Group")
                        + " selectedAction=" + (pinned ? "unpin" : "pin"));
                updateGroupMeta(group.getChatId(), KEY_PINNED + currentUser.getUid(),
                        pinned ? FieldValue.delete() : true,
                        pinned ? "Group unpinned" : "Group pinned");
            }

            @Override
            public void onArchive() {
                boolean archived = isArchivedForUser(group, currentUser.getUid());
                Log.d(TAG_GROUP_TAB_FIX, "chatId=" + group.getChatId() + " isGroup=true pinState="
                        + isPinnedForUser(group, currentUser.getUid()) + " archiveState=" + archived
                        + " displayedLabel=" + (archived ? "Unarchive Group" : "Archive Group")
                        + " selectedAction=" + (archived ? "unarchive" : "archive"));
                updateGroupMeta(group.getChatId(), KEY_ARCHIVED + currentUser.getUid(),
                        archived ? FieldValue.delete() : true,
                        archived ? "Group unarchived" : "Group archived");
            }

            @Override
            public void onMute() {
                updateGroupMeta(group.getChatId(), KEY_MUTED + currentUser.getUid(), true, "Group muted");
            }

            @Override
            public void onDelete() {
                deleteGroupForCurrentUser(group);
            }

            @Override
            public void onMarkUnread() {
                updateGroupMeta(group.getChatId(), KEY_UNREAD + currentUser.getUid(), true, "Marked as unread");
            }

            @Override
            public void onInfo() {
                if (isAdded() && getContext() != null) {
                    Intent intent = new Intent(getContext(), GroupInfoActivity.class);
                    intent.putExtra("chatId", group.getChatId());
                    startActivity(intent);
                }
            }
        });
        }
    }

    private void updateGroupMeta(String chatId, String key, Object value, String successText) {
        if (currentUser == null || TextUtils.isEmpty(chatId)) return;
        Log.d(TAG, "updateGroupMeta chatId=" + chatId + " key=" + key + " value=" + value);
        ChatModel group = groupsById.get(chatId);
        boolean pinned = group != null && isPinnedForUser(group, currentUser.getUid());
        boolean archived = group != null && isArchivedForUser(group, currentUser.getUid());
        Log.d(TAG_GROUP_TAB_FIX, "chatId=" + chatId + " isGroup=true pinState=" + pinned + " archiveState=" + archived
                + " displayedLabel=n/a selectedAction=firestore_update_start");
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .update(key, value, "lastActionAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG_GROUP_TAB_FIX, "chatId=" + chatId + " isGroup=true pinState=" + pinned + " archiveState=" + archived
                            + " displayedLabel=n/a selectedAction=firestore_update_success");
                    if (isAdded() && getContext() != null) {
                        android.widget.Toast.makeText(getContext(), successText, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG_GROUP_TAB_FIX, "chatId=" + chatId + " isGroup=true pinState=" + pinned + " archiveState=" + archived
                            + " displayedLabel=n/a selectedAction=firestore_update_failure exception=" + Log.getStackTraceString(e));
                    Log.e(TAG, "updateGroupMeta failed for chatId=" + chatId, e);
                });
    }

    private void deleteGroupForCurrentUser(ChatModel group) {
        if (currentUser == null || group == null || TextUtils.isEmpty(group.getChatId())) return;
        String chatId = group.getChatId();
        String uid = currentUser.getUid();
        String field = KEY_DELETED + uid;

        Log.d(TAG, "action=delete_group start chatId=" + chatId + " uid=" + uid);

        // Optimistic local removal for immediate UX.
        ChatModel previous = groupsById.remove(chatId);
        if (previous != null) {
            rebuildAndApplyFilter(activeQuery);
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .update(
                        field, true,
                        "chatDeletedAt." + uid, System.currentTimeMillis(),
                        "lastActionAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {
                    if (isAdded() && getContext() != null) {
                        android.widget.Toast.makeText(getContext(), "Group deleted for you", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "deleteGroupForCurrentUser failed for chatId=" + chatId, e);
                    if (previous != null) {
                        groupsById.put(chatId, previous);
                        rebuildAndApplyFilter(activeQuery);
                    }
                    if (isAdded() && getContext() != null) {
                        android.widget.Toast.makeText(getContext(), "Failed to delete group", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void clearUnread(String chatId, String uid) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(uid)) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .update(KEY_UNREAD + uid, com.google.firebase.firestore.FieldValue.delete(), "unreadBy." + uid, com.google.firebase.firestore.FieldValue.delete());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (groupsListener != null) {
            groupsListener.remove();
            groupsListener = null;
        }
    }

    private boolean isPinnedForUser(ChatModel group, String uid) {
        return isFlagSet(group.getGroupPinnedBy(), uid) || isFlagSet(group.getPinnedBy(), uid);
    }

    private boolean isArchivedForUser(ChatModel group, String uid) {
        return isFlagSet(group.getGroupArchivedBy(), uid) || isFlagSet(group.getArchivedBy(), uid);
    }

    private boolean isFlagSet(Map<String, Boolean> map, String uid) {
        return map != null && !TextUtils.isEmpty(uid) && Boolean.TRUE.equals(map.get(uid));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
