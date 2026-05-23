package com.vchat.app.chat;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vchat.app.models.MessageModel;

public class ReplyManager {

    private final ChatComposerController composerController;

    private final LinearLayout layoutReplyPreview;

    private final TextView tvReplyingTo;

    private final EditText etMessage;

    public ReplyManager(
            ChatComposerController composerController,
            LinearLayout layoutReplyPreview,
            TextView tvReplyingTo,
            EditText etMessage
    ) {

        this.composerController = composerController;

        this.layoutReplyPreview = layoutReplyPreview;

        this.tvReplyingTo = tvReplyingTo;

        this.etMessage = etMessage;
    }

    public void clearReplyState() {

        composerController.clearReply();

        if (layoutReplyPreview != null) {
            layoutReplyPreview.setVisibility(View.GONE);
        }
    }

    public void startReply(MessageModel message) {

        if (message == null) {
            return;
        }

        composerController.setReplyMessage(message);

        tvReplyingTo.setText(
                "Replying to: "
                        + composerController.getReplyPreview()
        );

        layoutReplyPreview.setVisibility(View.VISIBLE);

        etMessage.requestFocus();
    }
}