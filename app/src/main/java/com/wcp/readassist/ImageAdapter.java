package com.wcp.readassist;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageHolder> {

    List<Bitmap> mBitmapList;

    public ImageAdapter() {
        mBitmapList = new ArrayList<>();
    }

    @NonNull
    @Override
    public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemLayout = inflater.inflate(R.layout.image_list_item, parent, false);
        ImageHolder holder = new ImageHolder(itemLayout);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
        holder.mPageView.setImageBitmap(mBitmapList.get(position));
    }

    @Override
    public int getItemCount() {
        return mBitmapList.size();
    }

    public static class ImageHolder extends RecyclerView.ViewHolder {
        ImageView mPageView;
        public ImageHolder(@NonNull View itemView) {
            super(itemView);
            mPageView = (ImageView) itemView.findViewById(R.id.page_image);
        }
    }

    public int addToImageList(Bitmap bitmap) {
        mBitmapList.add(bitmap);
        notifyDataSetChanged();
        return mBitmapList.size();
    }

    public void clearImageList() {
        mBitmapList.clear();
    }

    public List<Bitmap> getImageList() {
        return mBitmapList;
    }
}
