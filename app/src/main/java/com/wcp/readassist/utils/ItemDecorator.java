package com.wcp.readassist.utils;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ItemDecorator extends RecyclerView.ItemDecoration {
    private int mGap;
    public ItemDecorator(int gap) {
        mGap = gap;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.left = mGap;
        outRect.right = mGap;
        outRect.bottom = mGap;
        outRect.top = 0;
    }
}
