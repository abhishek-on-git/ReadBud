package com.wcp.readassist;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class BookPagerAdapter extends RecyclerView.Adapter<BookPagerAdapter.BookPageHolder> {
    String[] mPages;
    Context mContext;
    LayoutInflater mInflater;
    private float mFontSize = 0f;
    private WordClickCallback mCallback;
    private int mTextColor;

    public interface WordClickCallback {
        void onWordClicked(String word);
    }

    public BookPagerAdapter(Context context, String[] pages) {
        mContext = context;
        mPages = pages;
        mInflater = LayoutInflater.from(mContext);
        mTextColor = mContext.getResources().getColor(R.color.app_black);
    }

    public void registerWordClickCallback(WordClickCallback callback) {
        mCallback = callback;
    }

    @NonNull
    @Override
    public BookPageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.ebook_page, parent, false);
        BookPageHolder holder = new BookPageHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull BookPageHolder holder, int position) {
        holder.mContentView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mFontSize);
        String content = mPages[position];
        SpannableString spannableString = new SpannableString(content);
        String[] words = content.split("\\W");
        int progressIndex = 0;
        for(final String word : words) {
            ClickableSpan span = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    mCallback.onWordClicked(word);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(mTextColor);
                }
            };
            int startIndex = content.indexOf(word, progressIndex);
            spannableString.setSpan(span, startIndex, startIndex + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            progressIndex += (word.length() + 1);
        }
        if(spannableString.length() < 2) {
            holder.mContentView.setText("No text could be found on this page.");
            holder.mContentView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            holder.mContentView.setText(spannableString);
            holder.mContentView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public int getItemCount() {
        return mPages.length;
    }

    class BookPageHolder extends RecyclerView.ViewHolder{
        TextView mContentView;
        public BookPageHolder(@NonNull View itemView) {
            super(itemView);
            mContentView = itemView.findViewById(R.id.page_content);
        }
    }

    public void updateTextSize(float newSize) {
        mFontSize = newSize;
    }

}
