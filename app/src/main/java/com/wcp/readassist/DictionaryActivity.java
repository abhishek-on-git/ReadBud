package com.wcp.readassist;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.wcp.readassist.database.DictionaryDBHelper;
import com.wcp.readassist.utils.AdsHelper;
import com.wcp.readassist.utils.ReadAssistUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DictionaryActivity extends AppCompatActivity implements ReadAssistUtils.TaskStatusCallback {

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private View mRootView;
    private SearchView mSearchView;
    private View mDefinitionContainer;
    private View mErrorContainer;
    private TextView mWordView;
    private TextView mErrorWordView;
    private RecyclerView mDefinitionListView;
    private Button mGoogleButton;
    private ProgressDialog mDialog;
    private AdView mAdView;
    private boolean mShowAds = true;

    private SimpleCursorAdapter mSuggestionAdapter;
    private DefinitionAdapter mAdapter;
    private DictionaryDBHelper mDBHelper;

    private String mCurrentWord;
    private String mSharedWord;
    private String mCurrentQuery = "";
    private String[] mFrom = new String[] {"word"};
    private int[] mTo = new int[] {R.id.suggestion_text};
    private static final String TAG = "RB_Dictionary";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);
        Window window = this.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if(Intent.ACTION_SEND.equals(action) && type != null) {
            if(ReadAssistUtils.MIME_TYPE_PLAIN_TEXT. equals(type)) {
                mSharedWord = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        }
        initialize();
    }

    private void initialize() {
        AdsHelper.initializeAdsSDK(this);
        mDBHelper = new DictionaryDBHelper(this);
        mRootView = findViewById(R.id.root_view);
        mDefinitionContainer = findViewById(R.id.definition_container);
        mErrorContainer = findViewById(R.id.error_container);
        hideDefinitionContainer();
        mErrorContainer.setVisibility(View.GONE);
        mWordView = (TextView) findViewById(R.id.word_header_view);
        mErrorWordView = (TextView) findViewById(R.id.error_word_header_view);
        mSearchView = (SearchView) findViewById(R.id.dictionary_search_view);
        mGoogleButton = findViewById(R.id.dictionary_google_button);
        mDialog = new ProgressDialog(this);
        mAdView = (AdView) findViewById(R.id.dictionary_banner_adView);
        ReadAssistUtils.registerTaskStatusCalback(this);

        AdsHelper.loadBannerAd(mAdView);

        initDB();

        View closeButton = (ImageView) findViewById(R.id.dictionary_close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout linearLayout1 = (LinearLayout) mSearchView.getChildAt(0);
        LinearLayout linearLayout2 = (LinearLayout) linearLayout1.getChildAt(2);
        LinearLayout linearLayout3 = (LinearLayout) linearLayout2.getChildAt(1);
        AutoCompleteTextView autoComplete = (AutoCompleteTextView) linearLayout3.getChildAt(0);
        autoComplete.setDropDownBackgroundResource(R.drawable.button_background);
        autoComplete.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus) {
                    hideDefinitionContainer();
                    mErrorContainer.setVisibility(View.GONE);
                    mCurrentWord = null;
                    mErrorWordView.setText("");
                }
            }
        });

        mSuggestionAdapter = new SimpleCursorAdapter(this, R.layout.suggestion_view, null, mFrom, mTo, 0){
            @Override
            public void changeCursor(Cursor cursor) {
                super.swapCursor(cursor);
            }
        };

        mSearchView.setSuggestionsAdapter(mSuggestionAdapter);
        mSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = mSuggestionAdapter.getCursor();
                cursor.moveToPosition(position);
                String tappedWord = cursor.getString(cursor.getColumnIndex("word"));
                mSearchView.setQuery(tappedWord, true);
                mSearchView.clearFocus();
                mSearchView.setFocusable(false);
                return false;
            }
        });

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String text = mSearchView.getQuery().toString();
                Cursor cursor = DictionaryDBHelper.getWordMeaning(text);
                handleQuerySubmission(text, cursor);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(mCurrentQuery.length() < newText.length()) {
                    Cursor suggestionCursor = DictionaryDBHelper.getSuggestions(newText);
                    if(suggestionCursor != null && suggestionCursor.getCount() > 0) {
                        mSuggestionAdapter.changeCursor(suggestionCursor);
                    }
                } else {

                }
                mCurrentQuery = newText;
                //suggestionCursor.close();
                hideDefinitionContainer();
                mErrorContainer.setVisibility(View.GONE);
                return false;
            }
        });

        if(mSharedWord != null) {
            if(mSharedWord.contains("medium.com")){
                int start = 1;//mSharedWord.indexOf('\u0022', 0);
                int end = mSharedWord.indexOf(" ", start) - 1;
                mSharedWord = mSharedWord.substring(start, end);
            }
            mSearchView.setQuery(mSharedWord, true);
            mSharedWord = null;
        }

        mGoogleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowAds = false;
                ReadAssistUtils.searchOnGoogle(DictionaryActivity.this, mCurrentWord);
            }
        });
    }

    private void initDB() {
        if(mDBHelper.dbExists()) {
            try {
                mDBHelper.openDB();
            } catch (Exception e) {
                Log.e(ReadAssistUtils.TAG, "Exception opening DB : "+ e);
            }
        } else {
            createDB();
        }
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

    private void createDB() {
        if(mHandler == null) {
            startBackgroundThread();
        }
        mDialog.setMessage(getString(R.string.db_loading_message));
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDBHelper.createDB();
            }
        });
    }

    private void handleQuerySubmission(String word, Cursor row) {
        String wordMeaning = null;
        String wordType = null;
        List<DefinitionItem> itemList = new ArrayList<>();
        if(row != null && row.getCount() != 0) {
            mErrorContainer.setVisibility(View.GONE);
            mSearchView.clearFocus();
            mSearchView.setFocusable(false);
            while(row.moveToNext()) {
                wordMeaning = row.getString(row.getColumnIndex("definition"));
                wordMeaning = wordMeaning.replaceAll("[\\s&&[^\\n]]+", " ");
                wordType = row.getString(row.getColumnIndex("wordtype"));
                mWordView.setText(word);
                showDefinitionContainer();
                populateItemList(wordType, wordMeaning, itemList);
            }
            fetchAndCreateDefinitions(itemList);
        } else {
            boolean singularFound = false;
            if(word.endsWith("s")) {
                int length = word.length();
                String singularForm = word.substring(0, length-1);
                Cursor singularRow = DictionaryDBHelper.getWordMeaning(singularForm);
                if(singularRow != null) {
                    while (singularRow.moveToNext()) {
                        wordMeaning = singularRow.getString(row.getColumnIndex("definition"));
                        wordMeaning = wordMeaning.replaceAll("[\\s&&[^\\n]]+", " ");
                        wordType = singularRow.getString(row.getColumnIndex("wordtype"));
                        word = singularForm;
                        mErrorContainer.setVisibility(View.GONE);
                        showDefinitionContainer();
                        mWordView.setText(singularForm);
                        populateItemList(wordType, wordMeaning, itemList);
                    }
                    singularFound = true;
                    fetchAndCreateDefinitions(itemList);
                }
            }
            if(!singularFound) {
                mCurrentWord = word;
                mSearchView.setQuery("", false);
                mSearchView.clearFocus();
                mSearchView.setFocusable(false);
                hideDefinitionContainer();
                mErrorContainer.setVisibility(View.VISIBLE);
                mWordView.setText("");
                mErrorWordView.setText(mCurrentWord);
            }
        }
    }

    private void hideDefinitionContainer() {
        mDefinitionContainer.setVisibility(View.GONE);
        mRootView.setBackground(getDrawable(R.drawable.main_screen_background));
    }

    private void showDefinitionContainer() {
        mDefinitionContainer.setVisibility(View.VISIBLE);
        mRootView.setBackground(getDrawable(R.drawable.main_screen_background_blurred));
    }

    private void populateItemList(String type, String definition, List<DefinitionItem> itemList) {
        DefinitionItem item = new DefinitionItem();
        item.setWordType(getFullWordType(type));
        item.setDefinition(definition);
        itemList.add(item);
    }

    private String getFullWordType(String type) {
        switch (type) {
            case "n.":
                return "noun ";
            case "a.":
                return "adjective ";
            case "v. t." :
                return "transitive verb ";
            case "adv." :
                return "adverb ";
            case "pl." :
                return "plural ";
            case "prep." :
                return "preposition ";
            case "conj." :
                return "conjunction ";
        }
        return type;
    }

    private void fetchAndCreateDefinitions(List<DefinitionItem> itemList) {
        mDefinitionListView = (RecyclerView) findViewById(R.id.definition_recycler_view);
        mAdapter = new DefinitionAdapter(itemList); // Make item list
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mDefinitionListView.setLayoutManager(layoutManager);
        mDefinitionListView.setAdapter(mAdapter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        String type = intent.getType();
        if(Intent.ACTION_SEND.equals(action) && type != null) {
            if(ReadAssistUtils.MIME_TYPE_PLAIN_TEXT. equals(type)) {
                mSharedWord = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.d(ReadAssistUtils.TAG, "shared word = "+mSharedWord);
                if(mSharedWord != null) {
                    if(mSharedWord.contains("medium.com")){
                        int start = 1;
                        int end = mSharedWord.indexOf(" ", start) - 1;
                        mSharedWord = mSharedWord.substring(start, end);
                    }
                    mSearchView.setQuery(mSharedWord, true);
                    mSharedWord = null;
                }
            }
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
        mShowAds = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(ReadAssistUtils.TAG, "showing ad from Dictionary");
        if(mShowAds) {
            AdsHelper.showInterstitialAd();
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

    }

    @Override
    public void onPageExtractionComplete(int pageNumber) {

    }

    @Override
    public void onError(boolean showMessage, String fileName) {

    }

    @Override
    public void onDBcreated() {
        Log.e(ReadAssistUtils.TAG, "DB created - Dictionary");
        if(mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if(mHandler != null) {
            mDBHelper.openDB();
            endBackgroundThread();
        }
    }
}