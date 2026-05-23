package com.vchat.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vchat.app.R;
import com.vchat.app.data.GroupRepository;
import com.vchat.app.models.UserModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupInfoActivity extends AppCompatActivity {

    private EditText etGroupName, etGroupDescription, etGroupRules, etMemberEmail;
    private TextView tvMemberCount;
    private RecyclerView rvMembers;
    private Button btnSaveGroupSettings, btnAddMember, btnAddFromFriends, btnLeaveGroup;
    private SwitchMaterial switchAdminOnlyMessaging, switchMuteGroup;
    private GroupRepository repository;
    private FirebaseFirestore db;
    private String chatId;
    private String myUid;
    private boolean isAdmin;
    private ListenerRegistration groupListener;
    private final List<MemberUiModel> memberItems = new ArrayList<>();
    private final Map<String, String> usernameCache = new HashMap<>();
    private final java.util.Set<String> inFlightMemberUids = new java.util.HashSet<>();
    private String selectedMemberUid = "";
    private String selectedMemberName = "";
    private MemberAdapter memberAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);
        Toolbar toolbar = findViewById(R.id.toolbar_group_info);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        chatId = getIntent().getStringExtra("chatId");
        myUid = FirebaseAuth.getInstance().getUid();

        etGroupName = findViewById(R.id.et_group_name);
        etGroupDescription = findViewById(R.id.et_group_description);
        etGroupRules = findViewById(R.id.et_group_rules);
        etMemberEmail = findViewById(R.id.et_member_email);
        tvMemberCount = findViewById(R.id.tv_member_count);
        rvMembers = findViewById(R.id.rv_members);
        btnSaveGroupSettings = findViewById(R.id.btn_rename_group);
        btnAddMember = findViewById(R.id.btn_add_member);
        btnAddFromFriends = findViewById(R.id.btn_add_from_friends);
        btnLeaveGroup = findViewById(R.id.btn_leave_group);
        switchAdminOnlyMessaging = findViewById(R.id.switch_admin_only_messaging);
        switchMuteGroup = findViewById(R.id.switch_mute_group);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        memberAdapter = new MemberAdapter(memberItems, this::onMemberSelected);
        rvMembers.setAdapter(memberAdapter);

        repository = new GroupRepository();
        db = FirebaseFirestore.getInstance();

        btnSaveGroupSettings.setOnClickListener(v -> saveGroupSettings());
        btnAddMember.setOnClickListener(v -> addMemberByEmail());
        btnAddFromFriends.setOnClickListener(v -> showFriendPicker());
        btnLeaveGroup.setOnClickListener(v -> confirmLeaveGroup());
        switchAdminOnlyMessaging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed() || !isAdmin) return;
            repository.setAdminOnlyMessaging(chatId, isChecked);
        });
        switchMuteGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed() || TextUtils.isEmpty(myUid)) return;
            repository.setGroupMutedForUser(chatId, myUid, isChecked);
        });

        listenGroupInfo();
    }

    private void listenGroupInfo() {
        groupListener = db.collection("chats").document(chatId).addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) return;

            String name = snapshot.getString("chatName");
            etGroupName.setText(name == null ? "" : name);
            etGroupDescription.setText(snapshot.getString("groupDescription") == null ? "" : snapshot.getString("groupDescription"));
            etGroupRules.setText(snapshot.getString("groupRules") == null ? "" : snapshot.getString("groupRules"));

            Map<String, Boolean> admins = (Map<String, Boolean>) snapshot.get("admins");
            isAdmin = admins != null && myUid != null && Boolean.TRUE.equals(admins.get(myUid));

            Map<String, Boolean> participants = (Map<String, Boolean>) snapshot.get("participants");
            Boolean onlyAdminsCanMessage = snapshot.getBoolean("groupSettings.onlyAdminsCanMessage");
            Map<String, Boolean> mutedBy = (Map<String, Boolean>) snapshot.get("mutedBy");
            rebuildMemberUi(participants, admins);
            switchAdminOnlyMessaging.setChecked(Boolean.TRUE.equals(onlyAdminsCanMessage));
            switchMuteGroup.setChecked(mutedBy != null && Boolean.TRUE.equals(mutedBy.get(myUid)));

            btnSaveGroupSettings.setEnabled(isAdmin);
            btnAddMember.setEnabled(true);
            btnAddFromFriends.setEnabled(true);
            etMemberEmail.setEnabled(true);
            etGroupDescription.setEnabled(isAdmin);
            etGroupRules.setEnabled(isAdmin);
            etGroupName.setEnabled(isAdmin);
            switchAdminOnlyMessaging.setEnabled(isAdmin);
        });
    }

    private void rebuildMemberUi(Map<String, Boolean> participants, Map<String, Boolean> admins) {
        memberItems.clear();
        if (participants == null || participants.isEmpty()) {
            tvMemberCount.setText("Current members (0)");
            memberAdapter.notifyDataSetChanged();
            return;
        }
        for (String uid : participants.keySet()) {
            boolean isMemberAdmin = admins != null && Boolean.TRUE.equals(admins.get(uid));
            String cached = usernameCache.get(uid);
            String displayName = TextUtils.isEmpty(cached) ? "Loading..." : cached;
            memberItems.add(new MemberUiModel(uid, displayName, isMemberAdmin));
            if (TextUtils.isEmpty(cached)) {
                fetchAndCacheUsername(uid);
            }
        }
        tvMemberCount.setText("Current members (" + memberItems.size() + ")");
        memberAdapter.notifyDataSetChanged();
    }

    private void fetchAndCacheUsername(String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc == null || !doc.exists()) return;
            String username = doc.getString("username");
            if (TextUtils.isEmpty(username)) return;
            usernameCache.put(uid, username);
            for (MemberUiModel item : memberItems) {
                if (TextUtils.equals(item.uid, uid)) {
                    item.displayName = username;
                }
            }
            memberAdapter.notifyDataSetChanged();
        });
    }

    private void saveGroupSettings() {
        if (!isAdmin) return;

        String name = etGroupName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Group name required", Toast.LENGTH_SHORT).show();
            return;
        }
        String description = etGroupDescription.getText().toString().trim();
        String rules = etGroupRules.getText().toString().trim();

        repository.updateGroupMeta(chatId, name, description, rules)
                .addOnSuccessListener(unused -> Toast.makeText(this, "Group settings updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
    }

    private void showFriendPicker() {
        if (TextUtils.isEmpty(myUid)) return;

        repository.fetchConnectedFriends(myUid, new GroupRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<UserModel> users) {
                if (users.isEmpty()) {
                    Toast.makeText(GroupInfoActivity.this, "No connected friends found", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<String> labels = new ArrayList<>();
                List<UserModel> visibleUsers = new ArrayList<>();
                for (UserModel user : users) {
                    String friendUid = user.getUid();
                    if (TextUtils.isEmpty(friendUid)) continue;
                    if (isAlreadyInGroup(friendUid)) continue;
                    String username = TextUtils.isEmpty(user.getUsername()) ? "User" : user.getUsername().trim();
                    String email = TextUtils.isEmpty(user.getEmail()) ? "" : user.getEmail().trim();
                    labels.add(TextUtils.isEmpty(email) ? username : username + "  •  " + email);
                    visibleUsers.add(user);
                }
                if (visibleUsers.isEmpty()) {
                    Toast.makeText(GroupInfoActivity.this, "All friends are already in this group", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> filteredLabels = new ArrayList<>(labels);
                List<UserModel> filteredUsers = new ArrayList<>(users);
                filteredUsers.clear();
                filteredUsers.addAll(visibleUsers);

                BottomSheetDialog dialog = new BottomSheetDialog(GroupInfoActivity.this);
                android.view.View view = getLayoutInflater().inflate(R.layout.bottomsheet_friend_picker, null, false);
                EditText etSearch = view.findViewById(R.id.et_friend_search);
                ListView listView = view.findViewById(R.id.lv_friend_picker);
                TextView tvState = view.findViewById(R.id.tv_friend_picker_state);
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(GroupInfoActivity.this, android.R.layout.simple_list_item_1, filteredLabels);
                listView.setAdapter(adapter);
                dialog.setContentView(view);
                updatePickerState(tvState, filteredLabels.isEmpty(), false);
                listView.setOnItemClickListener((parent, rowView, position, id) -> {
                    if (position < 0 || position >= filteredUsers.size()) return;
                    String uid = filteredUsers.get(position).getUid();
                    if (TextUtils.isEmpty(uid)) return;

                    addMemberAndSendSystemMessage(uid);
                    dialog.dismiss();
                });
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String q = s == null ? "" : s.toString().trim().toLowerCase();
                        filteredLabels.clear();
                        filteredUsers.clear();
                        for (int i = 0; i < visibleUsers.size(); i++) {
                            UserModel user = visibleUsers.get(i);
                            String username = TextUtils.isEmpty(user.getUsername()) ? "" : user.getUsername().toLowerCase();
                            String email = TextUtils.isEmpty(user.getEmail()) ? "" : user.getEmail().toLowerCase();
                            if (TextUtils.isEmpty(q) || username.contains(q) || email.contains(q)) {
                                filteredUsers.add(user);
                                filteredLabels.add(labels.get(i));
                            }
                        }
                        updatePickerState(tvState, filteredLabels.isEmpty(), false);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void afterTextChanged(Editable s) { }
                });

                dialog.show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(GroupInfoActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onMemberSelected(MemberUiModel member) {
        if (!isAdmin || member == null || TextUtils.isEmpty(member.uid) || TextUtils.equals(member.uid, myUid)) return;
        selectedMemberUid = member.uid;
        selectedMemberName = member.displayName;
        memberAdapter.setSelectedUid(selectedMemberUid);
        String[] actions = member.admin
                ? new String[]{"Remove admin", "Remove member"}
                : new String[]{"Make admin", "Remove member"};
        new AlertDialog.Builder(this)
                .setTitle(selectedMemberName)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        updateAdminState(!member.admin);
                    } else {
                        removeMember();
                    }
                })
                .show();
    }

    private void updateAdminState(boolean makeAdmin) {
        if (!isAdmin || TextUtils.isEmpty(selectedMemberUid)) return;
        repository.setAdmin(chatId, selectedMemberUid, makeAdmin)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, makeAdmin ? "Member promoted" : "Member demoted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Action failed", Toast.LENGTH_SHORT).show());
    }

    private void removeMember() {
        if (!isAdmin || TextUtils.isEmpty(selectedMemberUid)) return;
        final String targetUid = selectedMemberUid;
        final String targetName = selectedMemberName;
        if (inFlightMemberUids.contains(targetUid)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Remove member?")
                .setMessage("Remove " + targetName + " from the group?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    inFlightMemberUids.add(targetUid);
                    repository.removeMember(chatId, targetUid)
                            .addOnSuccessListener(unused -> {
                                inFlightMemberUids.remove(targetUid);
                                db.collection("users").document(myUid).get().addOnSuccessListener(myDoc -> {
                                    String rawMyUsername = myDoc != null && myDoc.exists() ? myDoc.getString("username") : null;
                                    final String myUsername = TextUtils.isEmpty(rawMyUsername) ? "Someone" : rawMyUsername;
                                    
                                    db.collection("users").document(targetUid).get().addOnSuccessListener(targetDoc -> {
                                        String rawTargetUsername = targetDoc != null && targetDoc.exists() ? targetDoc.getString("username") : null;
                                        final String targetUsername = TextUtils.isEmpty(rawTargetUsername) ? targetName : rawTargetUsername;
                                        
                                        String systemMessage = targetUsername + " was removed by " + myUsername;
                                        repository.sendGroupSystemMessage(chatId, myUid, systemMessage)
                                                .addOnFailureListener(e -> android.util.Log.e("GroupInfoActivity", "Failed to send system message", e));
                                    });
                                });
                                Toast.makeText(this, "Member removed", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                inFlightMemberUids.remove(targetUid);
                                Toast.makeText(this, "Remove failed", Toast.LENGTH_SHORT).show();
                            });
                })
                .show();
    }

    private void addMemberByEmail() {
        String email = etMemberEmail.getText().toString().trim().toLowerCase();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter member email", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> emails = new ArrayList<>();
        emails.add(email);

        repository.resolveMembersByEmails(emails, new GroupRepository.MembersResolvedCallback() {
            @Override
            public void onSuccess(List<String> memberUids) {
                if (memberUids.isEmpty()) {
                    Toast.makeText(GroupInfoActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                etMemberEmail.setText("");
                addMemberAndSendSystemMessage(memberUids.get(0));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(GroupInfoActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMemberAndSendSystemMessage(String newMemberUid) {
        if (TextUtils.isEmpty(newMemberUid) || TextUtils.isEmpty(myUid) || TextUtils.isEmpty(chatId)) return;
        if (isAlreadyInGroup(newMemberUid)) {
            Toast.makeText(this, "User is already a member", Toast.LENGTH_SHORT).show();
            return;
        }
        if (inFlightMemberUids.contains(newMemberUid)) {
            return;
        }
        inFlightMemberUids.add(newMemberUid);
        repository.addMember(chatId, newMemberUid)
                .addOnSuccessListener(unused -> {
                    inFlightMemberUids.remove(newMemberUid);
                    db.collection("users").document(myUid).get().addOnSuccessListener(myDoc -> {
                        String rawMyUsername = myDoc != null && myDoc.exists() ? myDoc.getString("username") : null;
                        final String myUsername = TextUtils.isEmpty(rawMyUsername) ? "Someone" : rawMyUsername;
                        
                        db.collection("users").document(newMemberUid).get().addOnSuccessListener(newDoc -> {
                            String rawNewUsername = newDoc != null && newDoc.exists() ? newDoc.getString("username") : null;
                            final String newUsername = TextUtils.isEmpty(rawNewUsername) ? "new member" : rawNewUsername;
                            
                            String systemMessage = myUsername + " added " + newUsername;
                            repository.sendGroupSystemMessage(chatId, myUid, systemMessage)
                                    .addOnFailureListener(e -> android.util.Log.e("GroupInfoActivity", "Failed to send system message", e));
                        });
                    });
                    Toast.makeText(GroupInfoActivity.this, "Member added", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    inFlightMemberUids.remove(newMemberUid);
                    Toast.makeText(GroupInfoActivity.this, "Add member failed", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isAlreadyInGroup(String uid) {
        for (MemberUiModel member : memberItems) {
            if (TextUtils.equals(member.uid, uid)) return true;
        }
        return false;
    }

    private void updatePickerState(TextView tvState, boolean empty, boolean loading) {
        if (tvState == null) return;
        if (loading) {
            tvState.setVisibility(android.view.View.VISIBLE);
            tvState.setText("Loading friends...");
            return;
        }
        tvState.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        tvState.setText("No friends match this search");
    }

    private void confirmLeaveGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Leave group?")
                .setMessage("You will stop receiving messages from this group.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Leave", (dialog, which) -> leaveGroup())
                .show();
    }

    private void leaveGroup() {
        if (TextUtils.isEmpty(myUid)) return;
        repository.leaveGroup(chatId, myUid)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "You left the group", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to leave group", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupListener != null) {
            groupListener.remove();
            groupListener = null;
        }
    }

    private static class MemberUiModel {
        final String uid;
        String displayName;
        final boolean admin;

        MemberUiModel(String uid, String displayName, boolean admin) {
            this.uid = uid;
            this.displayName = displayName;
            this.admin = admin;
        }
    }

    private static class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.Holder> {
        interface OnMemberClickListener {
            void onMemberClick(MemberUiModel member);
        }

        private final List<MemberUiModel> data;
        private final OnMemberClickListener listener;
        private String selectedUid = "";

        MemberAdapter(List<MemberUiModel> data, OnMemberClickListener listener) {
            this.data = data;
            this.listener = listener;
        }

        void setSelectedUid(String uid) {
            selectedUid = uid == null ? "" : uid;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_participant, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MemberUiModel item = data.get(position);
            holder.tvName.setText(item.displayName);
            holder.tvRole.setText(item.admin ? "Admin" : "Member");
            holder.tvRole.setTextColor(item.admin
                    ? holder.itemView.getResources().getColor(R.color.colorAccent)
                    : holder.itemView.getResources().getColor(R.color.text_secondary));
            holder.itemView.setActivated(TextUtils.equals(selectedUid, item.uid));
            holder.itemView.setAlpha(TextUtils.equals(selectedUid, item.uid) ? 0.86f : 1f);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMemberClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvRole;

            Holder(@NonNull android.view.View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_member_name);
                tvRole = itemView.findViewById(R.id.tv_member_role);
            }
        }
    }
}
