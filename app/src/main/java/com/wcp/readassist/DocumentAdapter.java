package com.wcp.readassist;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.DocumentHolder> {

    List<String> mDocumentPathList;
    OnItemClickListener mListener;

    public interface OnItemClickListener {
        void onItemClicked(int position);
    }

    public DocumentAdapter(List<String> list, OnItemClickListener listener) {
        mDocumentPathList = list;
        mListener = listener;
    }

    @NonNull
    @Override
    public DocumentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemLayout = inflater.inflate(R.layout.document_grid_item, parent, false);
        DocumentAdapter.DocumentHolder holder = new DocumentAdapter.DocumentHolder(itemLayout);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentHolder holder, int position) {
        String path = mDocumentPathList.get(position);
        int indexToStartFrom = path.lastIndexOf("/") + 1;
        int indexToEndAt = path.length();
        String fileName = path.substring(indexToStartFrom, indexToEndAt);
        holder.mDocumentName.setText(fileName);
        holder.mPosition = position;
    }

    @Override
    public int getItemCount() {
        return mDocumentPathList.size();
    }

    public class DocumentHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        ImageView mDocumentIcon;
        TextView mDocumentName;
        int mPosition;
        public DocumentHolder(@NonNull View itemView) {
            super(itemView);
            mDocumentIcon = (ImageView) itemView.findViewById(R.id.document_icon);
            mDocumentName = (TextView) itemView.findViewById(R.id.document_name);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            mListener.onItemClicked(mPosition);
        }
    }
}
