package com.wcp.readassist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class TextureSpinnerAdapter extends BaseAdapter {
    Context mContext;
    List<TextureItem> mTextures;
    LayoutInflater mInflater;

    public TextureSpinnerAdapter(Context context, List<TextureItem> list) {
        mContext = context;
        mTextures = list;
        mInflater = LayoutInflater.from(mContext);
    }
    @Override
    public int getCount() {
        return mTextures.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = mInflater.inflate(R.layout.texture_spinner_item, null);
        ImageView textureIcon = convertView.findViewById(R.id.texture_preview_image);
        TextView textureName = convertView.findViewById(R.id.texture_preview_text);
        TextureItem texture = mTextures.get(position);
        textureIcon.setBackgroundResource(texture.getResourceId());
        textureName.setText(texture.getTextureName());
        return convertView;
    }
}
