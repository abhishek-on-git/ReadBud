package com.wcp.readassist;

import android.graphics.Bitmap;

public class DigitalPage {
    private String mText;
    private Bitmap mImage;
    private String mPageNumber;

    public void setText(String text) {
        mText = text;
    }

    public void setImage(Bitmap bitmap) {
        mImage = bitmap;
    }

    public Bitmap getImage() {
        return mImage;
    }

    public void setPageNumber(String pageNumber) {
        mPageNumber = mPageNumber;
    }

    public String getText() {
        return mText;
    }
}
