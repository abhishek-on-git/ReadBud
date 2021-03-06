package com.wcp.readassist;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.wcp.readassist.utils.AdsHelper;
import com.wcp.readassist.utils.ItemDecorator;
import com.wcp.readassist.utils.ReadAssistUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DocumentsScreen extends AppCompatActivity implements DocumentAdapter.OnItemClickListener, ReadAssistUtils.TaskStatusCallback {

    private List<String> mDocPaths;
    private RecyclerView mDocumentsRecyclerView;
    private DocumentAdapter mAdapter;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ProgressDialog mDialog;
    private View mCloseButton;
    private View mErrorView;
    private AdView mAdView;

    private boolean mShowAds = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_documents_screen);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        ReadAssistUtils.registerTaskStatusCalback(this);
        File file = Environment.getExternalStorageDirectory();
        //fetchAllDocs(file);
        populateAllDocs();
        init();
        if(mDocPaths.size() == 0) {
            mErrorView.setVisibility(View.VISIBLE);
        } else {
            mErrorView.setVisibility(View.GONE);
        }
    }

    private void init() {
        mAdView = (AdView) findViewById(R.id.doc_screen_banner_adView);
        mDialog = new ProgressDialog(this);
        mErrorView = (TextView) findViewById(R.id.no_files_error_view);
        mDocumentsRecyclerView = (RecyclerView) findViewById(R.id.documents_recycler_view);
        mAdapter = new DocumentAdapter(mDocPaths, this);
        mDocumentsRecyclerView.setAdapter(mAdapter);
        mDocumentsRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        mDocumentsRecyclerView.addItemDecoration(new ItemDecorator(30));
        mCloseButton = findViewById(R.id.documents_close_button);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        AdsHelper.loadBannerAd(mAdView);
    }

    private void populateAllDocs() {
        Cursor cursor = ReadAssistUtils.fetchAllDocs(this);
        int dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
        mDocPaths = new ArrayList<>();
        while(cursor.moveToNext()) {
            String path = cursor.getString(dataIndex);
            mDocPaths.add(path);
        }
        Log.d(ReadAssistUtils.TAG, "document path list size : "+mDocPaths.size());
    }

    @Override
    public void onItemClicked(int position) {
        mDialog.setMessage("Creating E-Book...");
        mDialog.show();
        String path = mDocPaths.get(position);
        String fileName = "";
        final Uri uri = Uri.fromFile(new File(path));
        int cut = path.lastIndexOf('/');
        if (cut >= 0) {
            fileName = path.substring(cut + 1);
        }
        startBackgroundThread();
        final String bookName = fileName;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ReadAssistUtils.extractTextFromPDF(DocumentsScreen.this, bookName, uri);
            }
        });
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread(ReadAssistUtils.PDF_EXTRACTION_BACKGROUND_THREAD);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private void endBackgroundThread() {
        if(mHandlerThread == null) {
            return;
        }
        mHandlerThread.quitSafely();
        try{
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTextRecognitionCompleted(String eBookName, List<DigitalPage> digitalPageList) {

    }

    @Override
    public void onFileWritten(String eBookName) {

    }

    @Override
    public void onFileRead(String eBookName, String[] text) {

    }

    @Override
    public void onTextExtractionComplete(String eBookName) {
        Log.e(ReadAssistUtils.TAG, "onPDFExtractionComplete");
        if(mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mShowAds = false;
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.SOURCE, 1);
        intent.putExtra(ReadAssistUtils.BOOK_NAME, eBookName);
        startActivity(intent);
        endBackgroundThread();
    }

    @Override
    public void onPageExtractionComplete(int pageNumber) {
        String message = pageNumber + " Pages processed.";
        mDialog.setMessage(message);
    }

    @Override
    public void onError(boolean showMessage, String fileName) {
        if(mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if(showMessage) {
            Toast.makeText(this, getString(R.string.app_common_error_message), Toast.LENGTH_LONG).show();
        }
        ReadAssistUtils.deleteEbook(this, fileName);
    }

    @Override
    public void onDBcreated() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int navBarHeight = ReadAssistUtils.getNavigationBarHeight(this);
        if(navBarHeight == 0) {
            navBarHeight = getResources().getDimensionPixelSize(R.dimen.nav_bar_fallback_height);
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAdView.getLayoutParams();
        params.setMargins(0, 0, 0, navBarHeight);
        mAdView.setLayoutParams(params);
        //mShowAds = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(ReadAssistUtils.TAG, "showing ad from Documents screen");
        if(mShowAds) {
            AdsHelper.showInterstitialAd();
        }
    }
//    private void fetchAllDocs(File dir) {
//        String pdfPattern = ".pdf";
//        File listFile[] = dir.listFiles();
//        if (listFile != null) {
//            for (int i = 0; i < listFile.length; i++) {
//                if (listFile[i].isDirectory()) {
//                    fetchAllDocs(listFile[i]);
//                } else {
//                    if (listFile[i].getName().endsWith(pdfPattern)){
//                        Log.e("Abhishek", "name = "+listFile[i].toString());
//                    }
//                }
//            }
//        }
//    }
}