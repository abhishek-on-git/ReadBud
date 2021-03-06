package com.wcp.readassist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EBooksAdaper extends RecyclerView.Adapter<EBooksAdaper.EbookHolder> {
    List<String> mEbookNames;

    public interface OnItemClickListener {
        void onItemClicked(int position);
        void onDeleteClicked(int position);
    }

    OnItemClickListener mListener;

    public EBooksAdaper(List<String> list, OnItemClickListener listener) {
        mEbookNames = list;
        mListener = listener;
    }

    @NonNull
    @Override
    public EbookHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View item = inflater.inflate(R.layout.ebook_page_item, parent, false);
        EbookHolder holder = new EbookHolder(item);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull EbookHolder holder, int position) {
        holder.mEBookName.setText(mEbookNames.get(position));
        holder.mPosition = position;
    }

    @Override
    public int getItemCount() {
        return mEbookNames.size();
    }

    public class EbookHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView mEBookName;
        View mDeleteButton;
        int mPosition;
        public EbookHolder(@NonNull View itemView) {
            super(itemView);
            mEBookName = itemView.findViewById(R.id.ebook_name_view);
            mDeleteButton = itemView.findViewById(R.id.delete_button);
            itemView.setOnClickListener(this);
            mDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onDeleteClicked(mPosition);
                }
            });
        }

        @Override
        public void onClick(View v) {
            mListener.onItemClicked(mPosition);
        }
    }
}
