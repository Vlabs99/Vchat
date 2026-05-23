package com.vchat.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.vchat.app.R;
import com.vchat.app.activities.LoginActivity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileFragment extends Fragment {

    private ImageView ivProfileImage;
    private TextView tvChangePhoto;
    private EditText etUsername, etEmail, etBio;
    private Button btnSaveProfile, btnLogout;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;

    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null && isAdded()) {
                        ivProfileImage.setImageURI(selectedImageUri);
                        uploadProfileImage(selectedImageUri);
                    }
                }
            });

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        ivProfileImage = view.findViewById(R.id.iv_profile_image);
        tvChangePhoto = view.findViewById(R.id.tv_change_photo);
        etUsername = view.findViewById(R.id.et_username);
        etEmail = view.findViewById(R.id.et_email);
        etBio = view.findViewById(R.id.et_bio);
        btnSaveProfile = view.findViewById(R.id.btn_save_profile);
        btnLogout = view.findViewById(R.id.btn_logout);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return view;
        }

        loadUserProfile();

        tvChangePhoto.setOnClickListener(v -> openGalleryPicker());
        ivProfileImage.setOnClickListener(v -> openGalleryPicker());

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnLogout.setOnClickListener(v -> logoutUser());

        return view;
    }

    private void loadUserProfile() {
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || snapshot == null || !snapshot.exists()) return;

                    String username = snapshot.getString("username");
                    String email = snapshot.getString("email");
                    String bio = snapshot.getString("bio");
                    String profileImage = snapshot.getString("profileImage");

                    etUsername.setText(username == null ? "" : username);
                    etEmail.setText(email == null ? currentUser.getEmail() : email);
                    etBio.setText(bio == null ? "" : bio);

                    if (!TextUtils.isEmpty(profileImage)) {
                        Glide.with(requireContext())
                                .load(profileImage)
                                .placeholder(R.drawable.ic_profile)
                                .circleCrop()
                                .into(ivProfileImage);
                    } else {
                        ivProfileImage.setImageResource(R.drawable.ic_profile);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadProfileImage(Uri imageUri) {
        if (currentUser == null || imageUri == null) return;

        Toast.makeText(getContext(), "Uploading image...", Toast.LENGTH_SHORT).show();

        StorageReference ref = storage.getReference()
                .child("profile_images")
                .child(currentUser.getUid())
                .child(UUID.randomUUID().toString() + ".jpg");

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> db.collection("users")
                                .document(currentUser.getUid())
                                .update("profileImage", uri.toString())
                                .addOnSuccessListener(unused -> {
                                    if (isAdded()) {
                                        Toast.makeText(getContext(), "Profile image updated", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isAdded()) {
                                        Toast.makeText(getContext(), "Failed to save image URL", Toast.LENGTH_SHORT).show();
                                    }
                                })))
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Image upload failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveProfile() {
        if (currentUser == null) return;

        String username = etUsername.getText().toString().trim();
        String bio = etBio.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(getContext(), "Username cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", username);
        updates.put("bio", bio);

        db.collection("users")
                .document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void logoutUser() {
        if (currentUser != null) {
            db.collection("users")
                    .document(currentUser.getUid())
                    .update("fcmToken", "")
                    .addOnCompleteListener(task -> {
                        auth.signOut();
                        goToLogin();
                    });
        } else {
            auth.signOut();
            goToLogin();
        }
    }

    private void goToLogin() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }
}
