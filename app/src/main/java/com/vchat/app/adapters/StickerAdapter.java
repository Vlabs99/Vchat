package com.vchat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vchat.app.R;

import java.util.List;

public class StickerAdapter extends RecyclerView.Adapter<StickerAdapter.Holder> {

    public interface OnStickerClickListener {
        void onStickerClick(int stickerResId);
    }

    private final List<Integer> stickers;
    private final OnStickerClickListener listener;

    public StickerAdapter(List<Integer> stickers, OnStickerClickListener listener) {
        this.stickers = stickers;
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        int resId = stickers.get(position);
        holder.ivSticker.setImageResource(resId);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStickerClick(resId);
        });
    }

    @Override
    public int getItemCount() {
        return stickers.size();
    }

    @Override
    public long getItemId(int position) {
        return stickers.get(position);
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ImageView ivSticker;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivSticker = itemView.findViewById(R.id.iv_sticker);
        }
    }
}

