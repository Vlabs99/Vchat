package com.vchat.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;
import com.vchat.app.R;
import com.vchat.app.databinding.ActivityMainBinding;
import com.vchat.app.fragments.ChatsFragment;
import com.vchat.app.fragments.GroupsFragment;
import com.vchat.app.fragments.ProfileFragment;
import com.vchat.app.fragments.UsersFragment;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private SearchView chatsSearchView;
    private int selectedBottomTabId = R.id.nav_chats;
    private ListenerRegistration notificationsBadgeListener;
    private ListenerRegistration pendingBadgeListener;
    private TextView tvNotificationsBadge;
    private View notificationsDot;
    private TextView tvPendingBadge;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted ->
                    Log.d("MainActivity", "POST_NOTIFICATIONS granted: " + isGranted));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        setSupportActionBar(binding.mainToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("VChat");
        }

        requestNotificationPermissionIfNeeded();
        fetchAndSaveFcmToken();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ChatsFragment())
                    .commit();
        }

        binding.bottomNavView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_chats) {
                selectedFragment = new ChatsFragment();
                selectedBottomTabId = R.id.nav_chats;
            } else if (itemId == R.id.nav_groups) {
                selectedFragment = new GroupsFragment();
                selectedBottomTabId = R.id.nav_groups;
            } else if (itemId == R.id.nav_users) {
                selectedFragment = new UsersFragment();
                selectedBottomTabId = R.id.nav_users;
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
                selectedBottomTabId = R.id.nav_profile;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                invalidateOptionsMenu();
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search_chats);
        setupToolbarBadges(menu);
        if (searchItem != null) {
            chatsSearchView = (SearchView) searchItem.getActionView();
            if (chatsSearchView != null) {
                chatsSearchView.setQueryHint("Search chats");
                chatsSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        pushChatSearchQuery(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        pushChatSearchQuery(newText);
                        return true;
                    }
                });

                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        pushChatSearchQuery("");
                        return true;
                    }
                });
            }
        }

        return true;
    }

    private void setupToolbarBadges(Menu menu) {
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        if (notificationItem != null) {
            View actionView = getLayoutInflater().inflate(R.layout.menu_icon_badge, null);
            ImageView icon = actionView.findViewById(R.id.iv_menu_icon);
            icon.setImageResource(R.drawable.ic_notifications);
            tvNotificationsBadge = actionView.findViewById(R.id.tv_menu_badge);
            notificationsDot = actionView.findViewById(R.id.view_menu_dot);
            actionView.setOnClickListener(v -> onOptionsItemSelected(notificationItem));
            notificationItem.setActionView(actionView);
        }

        MenuItem requestItem = menu.findItem(R.id.action_pending_requests);
        if (requestItem != null) {
            View actionView = getLayoutInflater().inflate(R.layout.menu_icon_badge, null);
            ImageView icon = actionView.findViewById(R.id.iv_menu_icon);
            icon.setImageResource(android.R.drawable.ic_menu_agenda);
            tvPendingBadge = actionView.findViewById(R.id.tv_menu_badge);
            actionView.findViewById(R.id.view_menu_dot).setVisibility(View.GONE);
            actionView.setOnClickListener(v -> onOptionsItemSelected(requestItem));
            requestItem.setActionView(actionView);
        }
        attachBadgeListeners();
    }

    private void attachBadgeListeners() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        if (tvNotificationsBadge == null || tvPendingBadge == null) return;
        if (notificationsBadgeListener != null) notificationsBadgeListener.remove();
        notificationsBadgeListener = firestore.collection("users").document(uid).collection("notifications")
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (tvNotificationsBadge == null || notificationsDot == null) return;
                    if (error != null) return;
                    int count = value == null ? 0 : value.size();
                    if (count < 0) count = 0;
                    tvNotificationsBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    notificationsDot.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    tvNotificationsBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                });

        if (pendingBadgeListener != null) pendingBadgeListener.remove();
        pendingBadgeListener = firestore.collection("chat_requests")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (tvPendingBadge == null) return;
                    if (error != null) return;
                    int count = value == null ? 0 : value.size();
                    if (count < 0) count = 0;
                    tvPendingBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    tvPendingBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search_chats);
        boolean showSearch = selectedBottomTabId == R.id.nav_chats;

        if (searchItem != null) {
            searchItem.setVisible(showSearch);
            if (!showSearch && searchItem.isActionViewExpanded()) {
                searchItem.collapseActionView();
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
            return true;
        }

        if (id == R.id.action_pending_requests) {
            startActivity(new Intent(this, PendingRequestsActivity.class));
            return true;
        }
        if (id == R.id.action_starred_messages) {
            startActivity(new Intent(this, StarredMessagesActivity.class));
            return true;
        }
        if (id == R.id.action_blocked_users) {
            startActivity(new Intent(this, BlockedUsersActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void pushChatSearchQuery(String query) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof ChatsFragment) {
            ((ChatsFragment) current).applySearchQuery(query);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void fetchAndSaveFcmToken() {
        if (auth.getCurrentUser() == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty() || auth.getCurrentUser() == null) return;

                    firestore.collection("users")
                            .document(auth.getCurrentUser().getUid())
                            .update("fcmToken", token)
                            .addOnFailureListener(e ->
                                    Log.e("MainActivity", "Failed to save FCM token", e));
                })
                .addOnFailureListener(e ->
                        Log.e("MainActivity", "Failed to fetch FCM token", e));
    }

    private void updateOnlineStatus(boolean isOnline) {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);
        updates.put("typingTo", "");

        if (!isOnline) {
            updates.put("lastSeen", FieldValue.serverTimestamp());
        }

        firestore.collection("users")
                .document(uid)
                .update(updates)
                .addOnFailureListener(e -> Log.e("MainActivity", "Presence update failed", e));
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateOnlineStatus(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        updateOnlineStatus(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsBadgeListener != null) notificationsBadgeListener.remove();
        if (pendingBadgeListener != null) pendingBadgeListener.remove();
    }
}
