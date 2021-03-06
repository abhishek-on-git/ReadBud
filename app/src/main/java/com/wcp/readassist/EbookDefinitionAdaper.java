package com.wcp.readassist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EbookDefinitionAdaper extends RecyclerView.Adapter<EbookDefinitionAdaper.DefinitionHolder> {

    List<DefinitionItem> mItemList;

    public EbookDefinitionAdaper(List<DefinitionItem> list) {
        mItemList = list;
    }

    @NonNull
    @Override
    public DefinitionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemLayout = inflater.inflate(R.layout.ebook_definition_item_view, parent, false);
        DefinitionHolder holder = new DefinitionHolder(itemLayout);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull DefinitionHolder holder, int position) {
        holder.mWordType.setText(mItemList.get(position).getWordType());
        holder.mDefinition.setText(mItemList.get(position).getDefinition());
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    public class DefinitionHolder extends RecyclerView.ViewHolder{
        TextView mWordType;
        TextView mDefinition;
        public DefinitionHolder(@NonNull View itemView) {
            super(itemView);
            mWordType = itemView.findViewById(R.id.word_type_view);
            mDefinition = itemView.findViewById(R.id.definition_view);
        }
    }
}

