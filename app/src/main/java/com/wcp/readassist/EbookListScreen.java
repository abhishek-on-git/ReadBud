package com.wcp.readassist;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.wcp.readassist.utils.AdsHelper;
import com.wcp.readassist.utils.ReadAssistUtils;

import java.util.Arrays;
import java.util.List;

public class EbookListScreen extends AppCompatActivity implements EBooksAdaper.OnItemClickListener{

    private View mCloseButton;
    private RecyclerView mEBookListView;
    private EBooksAdaper mAdapter;
    private List<String> mEbookNames;
    private View mDeleteConfimationView;
    private View mDeleteButton;
    private View mCancelButton;
    private View mNoEbooksMsg;
    private Button mGetStartedButton;
    private AdView mAdView;

    private boolean mShowAds = true;

    int mToBeDeletedPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        setContentView(R.layout.activity_ebook_list_screen);
    }

    private void initialize() {
        populateNames();
        mAdView = (AdView) findViewById(R.id.ebook_list_banner_adView);
        mCloseButton = findViewById(R.id.ebooks_close_button);
        mEBookListView = (RecyclerView) findViewById(R.id.ebooks_recycler_view);
        mAdapter = new EBooksAdaper(mEbookNames, this);
        mEBookListView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mEBookListView.setAdapter(mAdapter);
        mDeleteConfimationView = findViewById(R.id.delete_confirmation_view);
        mDeleteButton = findViewById(R.id.delete_ebook_button);
        mCancelButton = findViewById(R.id.cancel_button);
        mNoEbooksMsg = findViewById(R.id.no_ebooks_msg_view);
        mGetStartedButton = (Button) findViewById(R.id.get_started_button);

        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleDeleteClicked(mToBeDeletedPosition);
            }
        });
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideDeleteConfirmationView();
            }
        });
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mGetStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowAds = false;
                Intent intent = new Intent(EbookListScreen.this, DocumentsScreen.class);
                startActivity(intent);
            }
        });

        AdsHelper.loadBannerAd(mAdView);
    }

    private void showDeleteConfirmationView() {
        mDeleteConfimationView.setVisibility(View.VISIBLE);
    }

    private void hideDeleteConfirmationView() {
        mDeleteConfimationView.setVisibility(View.GONE);
        mToBeDeletedPosition = -1;
    }

    private void populateNames() {
        mEbookNames = ReadAssistUtils.fetchEbookNames(this);
    }

    private void handleDeleteClicked(int position) {
        String name = mEbookNames.get(position);
        ReadAssistUtils.deleteEbook(this, name);
        mEbookNames.remove(name);
        mAdapter.notifyDataSetChanged();
        hideDeleteConfirmationView();
        handleListVisibility();
    }

    private void handleItemClicked(int position) {
        mShowAds = false;
        String name = mEbookNames.get(position);
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.SOURCE, 1);
        intent.putExtra(ReadAssistUtils.BOOK_NAME, name);
        startActivity(intent);
    }

    @Override
    public void onItemClicked(int position) {
        handleItemClicked(position);
    }

    @Override
    public void onDeleteClicked(int position) {
        mToBeDeletedPosition = position;
        showDeleteConfirmationView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideDeleteConfirmationView();
        Log.d(ReadAssistUtils.TAG, "Showing ad from eBook list screen");
        if(mShowAds) {
            AdsHelper.showInterstitialAd();
        }
    }

    private void handleListVisibility() {
        if(mEbookNames.isEmpty()) {
            mNoEbooksMsg.setVisibility(View.VISIBLE);
            mDeleteConfimationView.setVisibility(View.GONE);
            mEBookListView.setVisibility(View.GONE);
        } else {
            mNoEbooksMsg.setVisibility(View.GONE);
            mEBookListView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialize();
        handleListVisibility();
        int navBarHeight = ReadAssistUtils.getNavigationBarHeight(this);
        if(navBarHeight == 0) {
            navBarHeight = getResources().getDimensionPixelSize(R.dimen.nav_bar_fallback_height);
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAdView.getLayoutParams();
        params.setMargins(0, 0, 0, navBarHeight);
        mAdView.setLayoutParams(params);
    }
}