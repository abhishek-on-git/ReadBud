package com.wcp.readassist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.wcp.readassist.database.DictionaryDBHelper;
import com.wcp.readassist.utils.AdsHelper;
import com.wcp.readassist.utils.ReadAssistUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ReadAssistUtils.TaskStatusCallback {

    private HandlerThread mHandlerThread;
    private HandlerThread mDictionaryHandlerThread;
    private Handler mHandler;
    private Handler mDictionaryHandler;
    private Context mContext;
    private ProgressDialog mDialog;
    private DictionaryDBHelper mDBHelper;
    private int mCardCounter;

    private View mMainBackground;
    private View mMainBlurredBackground;
    private View mCameraCard;
    private View mGalleryCard;
    private View mEBookCard;
    private View mDictionaryCard;
    private View mAppHeaderView;
    private View mMyEbooksBtn;
    private AdView mAdView;

    private ObjectAnimator mBlurredBackgroundAnim;
    private ObjectAnimator mAppNameAnim;
    private ObjectAnimator mCameraCardAnim;
    private ObjectAnimator mGalleryAnim;
    private ObjectAnimator mEBookAnim;
    private ObjectAnimator mDictionaryAnim;

    private SharedPreferences mPreferences;

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }

        mContext = this;
        mDBHelper = new DictionaryDBHelper(mContext);

        mDialog = new ProgressDialog(this);

        mPreferences = getSharedPreferences(ReadAssistUtils.INTERSTITIAL_AD_ENABLED_PREFERENCE, Context.MODE_PRIVATE);

        init();
        initDB();
        if (getIntent().getData() != null) {
            mDialog.setMessage(getString(R.string.creating_ebook_message));
            final String result = getIntent().getData().toString();
            startBackgroundThread();
            mDialog.show();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ReadAssistUtils.extractTextFromPDF(mContext, "", Uri.parse(result));
                }
            });
        }
    }

    private void init() {
        if(!isInterstitialAdEnabled()) {
            checkAndUpdateInterstitialStatus();
        } else {
            AdsHelper.toggleInterstitialAds(true);
        }
        AdsHelper.initializeAdsSDK(this);
        ensurePermissions();
        mAdView = (AdView) findViewById(R.id.main_screen_banner_adView);
        mMainBackground = findViewById(R.id.main_screen_background);
        mMainBlurredBackground = findViewById(R.id.main_screen_blurred_background);
        mAppHeaderView = findViewById(R.id.header_container);
        mCameraCard = findViewById(R.id.camera_capture_container);
        mGalleryCard = findViewById(R.id.gallery_image_container);
        mEBookCard = findViewById(R.id.ebook_container);
        mDictionaryCard = findViewById(R.id.dictionary_container);
        mMyEbooksBtn = findViewById(R.id.my_ebooks_button);
        mBlurredBackgroundAnim = ObjectAnimator.ofFloat(mMainBlurredBackground, "alpha", 0f, 1f);
        mCameraCardAnim = ObjectAnimator.ofFloat(mCameraCard, "alpha", 0f, 1f);
        mCameraCardAnim.setDuration(1000);
        mGalleryAnim = ObjectAnimator.ofFloat(mGalleryCard, "alpha", 0f, 1f);
        mGalleryAnim.setDuration(1000);
        mGalleryAnim.setStartDelay(100);
        mEBookAnim = ObjectAnimator.ofFloat(mEBookCard, "alpha", 0f, 1f);
        mEBookAnim.setDuration(1000);
        mEBookAnim.setStartDelay(200);
        mDictionaryAnim = ObjectAnimator.ofFloat(mDictionaryCard, "alpha", 0f, 1f);
        mDictionaryAnim.setDuration(1000);
        mDictionaryAnim.setStartDelay(300);
        mBlurredBackgroundAnim.setDuration(1000);
        mAppNameAnim = ObjectAnimator.ofFloat(mAppHeaderView, "alpha", 1f, 0f);
        mAppNameAnim.setDuration(500);
        //setCardAnimListeners();
        View appName = findViewById(R.id.main_screen_app_name);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        int yStart = height/2 - getResources().getDimensionPixelSize(R.dimen.header_view_top_margin);
        int xStart = width/4;

        final Handler delayHandler = new Handler(getMainLooper());
        mAppHeaderView.setX(xStart);
        mAppHeaderView.setY(yStart);
        mAppHeaderView.setScaleX(1.2f);
        mAppHeaderView.setScaleY(1.2f);
        final ObjectAnimator[] cardAnims = {mCameraCardAnim, mGalleryAnim, mEBookAnim, mDictionaryAnim};//new ObjectAnimator[4];
        mCardCounter = 0;
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mAppHeaderView.animate().translationX(0).translationY(0).setDuration(1000).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mCameraCardAnim.start();
                        mGalleryAnim.start();
                        mEBookAnim.start();
                        mDictionaryAnim.start();
                        mMyEbooksBtn.animate().alpha(1f).setDuration(200).start();
                        mMyEbooksBtn.setVisibility(View.VISIBLE);
                    }
                }).start();
                mAppHeaderView.animate().scaleX(1f).scaleY(1f).setDuration(1000).start();
                mBlurredBackgroundAnim.start();
            }
        }, 1000);
        setClickListeners();
        AdsHelper.loadBannerAd(mAdView);
    }

    private void checkAndUpdateInterstitialStatus() {
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
        mFirebaseRemoteConfig.fetch(0).addOnCompleteListener(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull com.google.android.gms.tasks.Task<Void> task) {
                if(task.isSuccessful()) {
                    mFirebaseRemoteConfig.activateFetched();
                    boolean enabled = mFirebaseRemoteConfig.getBoolean("interstitial_ads_enabled");
                    if(enabled) {
                        updateInterstitialAdsAvailable();
                    }
                } else {
                    Log.e(ReadAssistUtils.TAG, "remote config completed but fetch failed");
                }
            }
        }).addOnFailureListener(this, new com.google.android.gms.tasks.OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(ReadAssistUtils.TAG, "remote config fetch failed"+ e);
            }
        });
    }

    private void updateInterstitialAdsAvailable() {
        if(mPreferences != null) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(ReadAssistUtils.INTERSTITIAL_AD_ENABLED_PREFERENCE_KEY, true);
            editor.apply();
        }
        AdsHelper.toggleInterstitialAds(true);
    }

    private boolean isInterstitialAdEnabled() {
        return mPreferences.getBoolean(ReadAssistUtils.INTERSTITIAL_AD_ENABLED_PREFERENCE_KEY, false);
    }

    private void ensurePermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[permissionsNeeded.size()]), PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    showPermissionError();
                }
            }
        }
    }

    private void showPermissionError() {
        final View permErrorView = findViewById(R.id.permission_error_view);
        View denyBtn = findViewById(R.id.deny_button);
        View affirmBtn = findViewById(R.id.affirm_button);
        denyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        affirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permErrorView.setVisibility(View.GONE);
                ensurePermissions();
            }
        });
        permErrorView.setVisibility(View.VISIBLE);

    }

    private void setClickListeners() {
        mMyEbooksBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startEbooksScreen();
            }
        });
        mCameraCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraCaptureScreen();
            }
        });
        mGalleryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerGalleryForImages();
            }
        });
        mEBookCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDocumentViewActivity();
            }
        });
        mDictionaryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDictionaryScreen();
            }
        });
    }

    private void startEbooksScreen() {
        Intent intent = new Intent(this, EbookListScreen.class);
        startActivity(intent);
    }

    private void startCameraCaptureScreen() {
        Intent intent = new Intent(this, CaptureImageScreen.class);
        startActivity(intent);
    }

    private void triggerGalleryForImages() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Choose Picture"), ReadAssistUtils.GALLERY_IMG_REQUEST_CODE);
    }

    private void startDictionaryScreen() {
        Intent intent = new Intent(this, DictionaryActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == ReadAssistUtils.APP_UPDATE_REQUEST_CODE) {
            if(resultCode != RESULT_OK) {
                Toast.makeText(MainActivity.this,getString(R.string.update_failed_message), Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == ReadAssistUtils.GALLERY_IMG_REQUEST_CODE) {
            if (mHandler == null) {
                startBackgroundThread();
            }
            if (resultCode == RESULT_OK) {
                if (mDialog != null) {
                    mDialog.setMessage(getString(R.string.extracting_text_message));
                    mDialog.show();
                }
                final List<Uri> imageUris = new ArrayList<>();
                if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    int count = clipData.getItemCount();
                    for (int i = 0; i < count; i++) {
                        imageUris.add(clipData.getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    imageUris.add(data.getData());
                }
                ReadAssistUtils.extractTextFromImage(this, imageUris, mHandler);
            }
        }
    }

    private void startDocumentViewActivity() {
        Intent intent = new Intent(this, DocumentsScreen.class);
        startActivity(intent);
    }

    private void initDB() {
        if (mDBHelper.dbExists()) {
            openDB();
        } else {
            createDB();
        }
    }

    private void openDB() {
        try {
            mDBHelper.openDB();
        } catch (Exception e) {

        }
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread(ReadAssistUtils.PDF_EXTRACTION_BACKGROUND_THREAD);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private void startDictionaryBackgroundThread() {
        mDictionaryHandlerThread = new HandlerThread(ReadAssistUtils.DICTIONARY_CREATION_BACKGROUND_THREAD);
        mDictionaryHandlerThread.start();
        mDictionaryHandler = new Handler(mDictionaryHandlerThread.getLooper());
    }

    private void endDictionaryBackgroundThread() {
        if (mDictionaryHandlerThread == null) {
            return;
        }
        mDictionaryHandlerThread.quitSafely();
        try {
            mDictionaryHandlerThread.join();
            mDictionaryHandlerThread = null;
            mDictionaryHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createDB() {
        if (mDictionaryHandler == null) {
            startDictionaryBackgroundThread();
        }
        mDialog.setMessage(getString(R.string.db_loading_message));
        mDictionaryHandler.post(new Runnable() {
            @Override
            public void run() {
                mDBHelper.createDB();
            }
        });
    }

    private void endBackgroundThread() {
        if (mHandlerThread == null) {
            return;
        }
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startDummyActivity() {
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.SOURCE, 1);
        startActivity(intent);
    }

    @Override
    public void onTextRecognitionCompleted(String eBookName, List<DigitalPage> digitalPageList) {
        ReadAssistUtils.writeToFile(mContext, eBookName);
    }

    @Override
    public void onFileWritten(String eBookName) {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.BOOK_NAME, eBookName);
        intent.putExtra(ReadAssistUtils.SOURCE, 0);
        startActivity(intent);
    }

    @Override
    public void onFileRead(String eBookName, String[] text) {

    }

    @Override
    public void onTextExtractionComplete(String eBookName) {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.SOURCE, 1);
        intent.putExtra(ReadAssistUtils.BOOK_NAME, eBookName);
        startActivity(intent);
        endBackgroundThread();
    }

    @Override
    public void onPageExtractionComplete(int pageNumber) {
        String message = pageNumber + " " + getString(R.string.pages_processed_message);
        mDialog.setMessage(message);
    }

    @Override
    public void onError(boolean showMessage, String fileName) {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if(showMessage) {
            Toast.makeText(this, getString(R.string.app_common_error_message), Toast.LENGTH_LONG).show();
        }
        ReadAssistUtils.deleteEbook(this, fileName);
    }

    @Override
    public void onDBcreated() {
        Log.e(ReadAssistUtils.TAG, "DB created");
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if (mHandler != null) {
            openDB();
            endDictionaryBackgroundThread();
        }
    }
    /*
    TODO : USE Cases :
     1. User installs app, but doesn't open it. Then opens a PDF with app in chooser
     */

    @Override
    protected void onResume() {
        super.onResume();
        ReadAssistUtils.registerTaskStatusCalback(this);
        int navBarHeight = ReadAssistUtils.getNavigationBarHeight(this);
        if (navBarHeight == 0) {
            navBarHeight = getResources().getDimensionPixelSize(R.dimen.nav_bar_fallback_height);
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAdView.getLayoutParams();
        params.setMargins(0, 0, 0, navBarHeight);
        mAdView.setLayoutParams(params);
        updateIfPossible();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    public void updateIfPossible() {
        final AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(this);
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(new com.google.android.play.core.tasks.OnSuccessListener<AppUpdateInfo>() {
            @Override
            public void onSuccess(AppUpdateInfo appUpdateInfo) {
                if(appUpdateInfo == null) {
                    Log.e(ReadAssistUtils.TAG, "appUpdateInfo is null!");
                    return;
                }
                Log.d(ReadAssistUtils.TAG, "onSuccess called appUpdateInfo = "+appUpdateInfo +
                        "availability = " + appUpdateInfo.updateAvailability());
                if ((appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        || appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    try {
                        Log.d(ReadAssistUtils.TAG, "requesting update");
                        appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                AppUpdateType.IMMEDIATE,
                                MainActivity.this,
                                ReadAssistUtils.APP_UPDATE_REQUEST_CODE);
                    } catch (Exception e) {
                        Log.e(ReadAssistUtils.TAG, "Exception in update : "+ e);
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Log.e(ReadAssistUtils.TAG, "update failed with exception : "+ e);
            }
        });
    }
}