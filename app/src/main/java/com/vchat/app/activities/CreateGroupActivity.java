package com.vchat.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vchat.app.R;
import com.vchat.app.data.GroupRepository;
import com.vchat.app.models.UserModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateGroupActivity extends AppCompatActivity {
    private static final String TAG_GROUP_CREATE = "VCHAT_GROUP_CREATE";

    private EditText etGroupName;
    private EditText etFriendSearch;
    private EditText etMemberEmails;
    private TextView tvSelectedFriends;
    private TextView tvFriendEmpty;
    private ListView lvFriends;
    private Button btnCreate;
    private GroupRepository repository;
    private final List<UserModel> allFriends = new ArrayList<>();
    private final List<UserModel> filteredFriends = new ArrayList<>();
    private final List<String> friendLabels = new ArrayList<>();
    private final Set<String> selectedUids = new HashSet<>();
    private ArrayAdapter<String> friendsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        Toolbar toolbar = findViewById(R.id.toolbar_create_group);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etGroupName = findViewById(R.id.et_group_name);
        etFriendSearch = findViewById(R.id.et_friend_search);
        etMemberEmails = findViewById(R.id.et_member_emails);
        tvSelectedFriends = findViewById(R.id.tv_selected_friends);
        tvFriendEmpty = findViewById(R.id.tv_friend_empty);
        lvFriends = findViewById(R.id.lv_friends);
        btnCreate = findViewById(R.id.btn_create_group);
        repository = new GroupRepository();

        friendsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, friendLabels);
        lvFriends.setAdapter(friendsAdapter);
        lvFriends.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredFriends.size()) return;
            String uid = filteredFriends.get(position).getUid();
            if (TextUtils.isEmpty(uid)) return;
            if (selectedUids.contains(uid)) {
                selectedUids.remove(uid);
            } else {
                selectedUids.add(uid);
            }
            renderSelectedUsers();
        });
        etFriendSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFriendFilter(s == null ? "" : s.toString());
            }
        });

        btnCreate.setOnClickListener(v -> createGroup());
        loadFriends();
    }

    private void loadFriends() {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            Log.e(TAG_GROUP_CREATE, "loadFriends aborted: currentUser null");
            return;
        }
        Log.d(TAG_GROUP_CREATE, "loadFriends start currentUserId=" + current.getUid());
        repository.fetchConnectedFriends(current.getUid(), new GroupRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<UserModel> users) {
                Log.d(TAG_GROUP_CREATE, "loadFriends success usersCount=" + users.size());
                allFriends.clear();
                allFriends.addAll(users);
                applyFriendFilter(etFriendSearch.getText() == null ? "" : etFriendSearch.getText().toString());
            }

            @Override
            public void onError(String message) {
                Log.e(TAG_GROUP_CREATE, "loadFriends failure message=" + message);
                Toast.makeText(CreateGroupActivity.this, message, Toast.LENGTH_SHORT).show();
                applyFriendFilter("");
            }
        });
    }

    private void applyFriendFilter(String queryRaw) {
        String query = queryRaw == null ? "" : queryRaw.trim().toLowerCase();
        filteredFriends.clear();
        friendLabels.clear();
        for (UserModel user : allFriends) {
            String username = user.getUsername() == null ? "" : user.getUsername();
            String email = user.getEmail() == null ? "" : user.getEmail();
            if (TextUtils.isEmpty(query)
                    || username.toLowerCase().contains(query)
                    || email.toLowerCase().contains(query)) {
                filteredFriends.add(user);
                friendLabels.add(username + (TextUtils.isEmpty(email) ? "" : "  •  " + email));
            }
        }
        friendsAdapter.notifyDataSetChanged();
        syncCheckedStates();
        tvFriendEmpty.setVisibility(filteredFriends.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void syncCheckedStates() {
        for (int i = 0; i < filteredFriends.size(); i++) {
            UserModel user = filteredFriends.get(i);
            lvFriends.setItemChecked(i, selectedUids.contains(user.getUid()));
        }
    }

    private void renderSelectedUsers() {
        if (selectedUids.isEmpty()) {
            tvSelectedFriends.setText("No members selected");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UserModel user : allFriends) {
            if (selectedUids.contains(user.getUid())) {
                names.add(TextUtils.isEmpty(user.getUsername()) ? user.getUid() : user.getUsername());
            }
        }
        tvSelectedFriends.setText("Selected (" + selectedUids.size() + "): " + TextUtils.join(", ", names));
    }

    private void createGroup() {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            Log.e(TAG_GROUP_CREATE, "createGroup aborted: currentUser null");
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etGroupName.getText().toString().trim();
        Log.d(TAG_GROUP_CREATE, "createGroup action currentUserId=" + current.getUid() + " rawGroupName=" + name);
        Log.d(TAG_GROUP_CREATE, "createGroup selectedMembersFromPicker=" + selectedUids);
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter group name", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false);

        List<String> memberUids = new ArrayList<>(selectedUids);
        String fallbackEmail = etMemberEmails.getText() == null ? "" : etMemberEmails.getText().toString().trim().toLowerCase();
        Log.d(TAG_GROUP_CREATE, "createGroup fallbackEmail=" + fallbackEmail);
        if (!TextUtils.isEmpty(fallbackEmail)) {
            List<String> fallbackEmails = new ArrayList<>();
            fallbackEmails.add(fallbackEmail);
            repository.resolveMembersByEmails(fallbackEmails, new GroupRepository.MembersResolvedCallback() {
                @Override
                public void onSuccess(List<String> resolvedUids) {
                    Log.d(TAG_GROUP_CREATE, "createGroup fallback resolvedUids=" + resolvedUids);
                    for (String uid : resolvedUids) {
                        if (!memberUids.contains(uid)) memberUids.add(uid);
                    }
                    createGroupWithMembers(current.getUid(), name, memberUids);
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG_GROUP_CREATE, "createGroup fallback resolve failed message=" + message);
                    createGroupWithMembers(current.getUid(), name, memberUids);
                }
            });
            return;
        }
        createGroupWithMembers(current.getUid(), name, memberUids);
    }

    private void createGroupWithMembers(String ownerUid, String groupName, List<String> memberUids) {
        Log.d(TAG_GROUP_CREATE, "createGroupWithMembers start currentUserId=" + ownerUid
                + " selectedMembers=" + memberUids + " memberCount=" + memberUids.size());
        repository.createGroup(ownerUid, groupName, memberUids)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG_GROUP_CREATE, "createGroupWithMembers success");
                    Toast.makeText(CreateGroupActivity.this, "Group created", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG_GROUP_CREATE, "createGroupWithMembers failure: " + Log.getStackTraceString(e));
                    btnCreate.setEnabled(true);
                    Toast.makeText(CreateGroupActivity.this, "Failed to create group", Toast.LENGTH_SHORT).show();
                });
    }
}
