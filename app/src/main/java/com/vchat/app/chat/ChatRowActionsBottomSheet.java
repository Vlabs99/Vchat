package com.vchat.app.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.vchat.app.R;

public class ChatRowActionsBottomSheet {

    public interface ActionListener {
        void onPin();
        void onArchive();
        void onMute();
        void onDelete();
        void onMarkUnread();
        void onInfo();
    }

    public static void show(Context context, String title, boolean isGroup, ActionListener listener) {
        show(context, title, isGroup, false, false, listener);
    }

    public static void show(Context context, String title, boolean isGroup, boolean isPinned, boolean isArchived, ActionListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View content = LayoutInflater.from(context).inflate(R.layout.bottomsheet_chat_row_actions, null, false);
        dialog.setContentView(content);

        TextView tvTitle = content.findViewById(R.id.tv_action_sheet_title);
        tvTitle.setText(title);
        TextView tvPinLabel = content.findViewById(R.id.tv_pin_label);
        TextView tvArchiveLabel = content.findViewById(R.id.tv_archive_label);
        tvPinLabel.setText(isPinned ? (isGroup ? "Unpin Group" : "Unpin chat") : (isGroup ? "Pin Group" : "Pin chat"));
        tvArchiveLabel.setText(isArchived ? (isGroup ? "Unarchive Group" : "Unarchive") : (isGroup ? "Archive Group" : "Archive"));

        bind(content, R.id.row_pin, dialog, listener::onPin);
        bind(content, R.id.row_archive, dialog, listener::onArchive);
        bind(content, R.id.row_mute, dialog, listener::onMute);
        bind(content, R.id.row_delete, dialog, listener::onDelete);
        bind(content, R.id.row_unread, dialog, listener::onMarkUnread);
        TextView tvInfo = content.findViewById(R.id.tv_info_label);
        tvInfo.setText(isGroup ? "Group info" : "Chat info");
        bind(content, R.id.row_info, dialog, listener::onInfo);

        dialog.show();
    }

    private static void bind(View content, int id, BottomSheetDialog dialog, Runnable action) {
        View row = content.findViewById(id);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
    }
}
