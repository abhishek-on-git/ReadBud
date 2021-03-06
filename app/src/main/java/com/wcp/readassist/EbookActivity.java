package com.wcp.readassist;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wcp.readassist.database.DictionaryDBHelper;
import com.wcp.readassist.utils.AdsHelper;
import com.wcp.readassist.utils.ReadAssistUtils;

import org.w3c.dom.Text;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class EbookActivity extends AppCompatActivity implements BookPagerAdapter.WordClickCallback {


    private Context mContext;
    private Resources mRes;
    private SharedPreferences mPreferences;
    private TextView mEBookNameView;
    private TextView mPageNumberView;
    private ViewPager2 mEBookPager;
    private String mEBookName;
    private String[] mPages;
    private View mNextButton;
    private View mPreviousButton;
    private View mCloseButton;
    private View mWordDetailView;
    private View mWordDetailCloseButton;
    private SeekBar mFontSizeSeekBar;
    private TextView mFontSize;
    private TextView mWordView;
    private TextView mErrorTextView;
    private RecyclerView mDefinitionsView;
    private Spinner mTextureSpinner;
    private TextView mOkButton;
    private TextView mGoogleButton;
    private BookPagerAdapter mAdapter;
    private View mGoToPageContainer;
    private View mResumeView;
    private Button mResumeButton;
    private Button mStartOverButton;
    private EditText mGoToPageView;

    private int mPageNumber = 1;
    private int mCurrentPageIndex = 0;
    private int mTexture = 1;
    private int mEBookSize = 0;
    private float mDefaultFontSize;
    private float mMaxFontSize;
    private boolean mShowAds = true;
    private boolean mWasTextureUpdated = false;
    private String mCurrentWord;
    private String mSelectedTexture;
    private String[] mTextureNames;
    private int mSource = 1;
    private int[] mTextureResources = {
            R.color.ebook_page_default_color,
            R.drawable.white_page_bg,
            R.drawable.ancient_page_bg,
            R.drawable.ancient_page_bg_2,
            R.drawable.warm_page_bg,
            R.drawable.cold_page_bg
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ebook);
        initialize();
    }

    private void initialize() {
        mContext = this;
        mRes = getResources();
        mPreferences = getSharedPreferences(ReadAssistUtils.EBOOK_STATE_PREFERENCE, Context.MODE_PRIVATE);
        mDefaultFontSize = (float) mRes.getDimensionPixelSize(R.dimen.page_content_font_size);
        mMaxFontSize = (float) mRes.getDimensionPixelSize(R.dimen.page_content_max_font_size);
        mEBookName = getIntent().getStringExtra(ReadAssistUtils.BOOK_NAME);
        mSource = getIntent().getIntExtra(ReadAssistUtils.SOURCE, 1);
        mCloseButton = findViewById(R.id.close_button_ebook);
        mEBookPager = (ViewPager2) findViewById(R.id.ebook_pager);
        mEBookNameView = (TextView) findViewById(R.id.ebook_name);
        mPageNumberView = (TextView) findViewById(R.id.page_number_view);
        mGoToPageContainer = findViewById(R.id.go_to_page_container);
        mGoToPageView = (EditText) findViewById(R.id.go_to_edit_text_view);
        mNextButton = findViewById(R.id.next_button);
        mPreviousButton = findViewById(R.id.previous_button);
        mFontSizeSeekBar = (SeekBar) findViewById(R.id.font_size_controller);
        mFontSize = (TextView) findViewById(R.id.text_size_view);
        mWordDetailView = findViewById(R.id.word_meaning_view);
        mWordDetailCloseButton = findViewById(R.id.close_button_word_meaning);
        mWordView = (TextView) findViewById(R.id.word);
        mErrorTextView = (TextView) findViewById(R.id.error_text_view);
        mOkButton = (TextView) findViewById(R.id.ok_button);
        mGoogleButton = (TextView) findViewById(R.id.google_button);
        mDefinitionsView = (RecyclerView) findViewById(R.id.definitions_recycler_view);
        mTextureSpinner = (Spinner) findViewById(R.id.texture_spinner);
        mResumeView = findViewById(R.id.resume_reading_view);
        mResumeButton = (Button) findViewById(R.id.resume_affirmative_button);
        mStartOverButton = (Button) findViewById(R.id.resume_start_over_button);
        List<TextureItem> textureList = new ArrayList<>();
        mTextureNames = getResources().getStringArray(R.array.texture_names);
        for(int i = 0; i < mTextureNames.length; i++) {
            TextureItem texture = new TextureItem();
            texture.setTextureName(mTextureNames[i]);
            texture.setResourceId(mTextureResources[i]);
            textureList.add(texture);
        }
        TextureSpinnerAdapter adapter = new TextureSpinnerAdapter(this, textureList);
        mTextureSpinner.setAdapter(adapter);
        mTextureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mTexture = position + 1;
                updateTexture(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        if(mSource == 0) {
            mGoToPageContainer.setVisibility(View.GONE);
        }
        if(!TextUtils.isEmpty(mEBookName)) {
            mEBookNameView.setText(mEBookName);
        }
        mEBookNameView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mEBookNameView.setSelected(true);
            }
        }, 3000);
        mPages = ReadAssistUtils.readFromFile(this, mEBookName, mSource);
        mEBookSize = mPages != null ? mPages.length : 0;
        updatePageNumber();
        mAdapter = new BookPagerAdapter(this, mPages);
        mAdapter.registerWordClickCallback(this);
        mAdapter.updateTextSize(mDefaultFontSize);
        mEBookPager.setAdapter(mAdapter);

        final int savedPageNo = mPreferences.getInt(getPreferenceKey(), 0);
        int texture = mPreferences.getInt(ReadAssistUtils.TEXTURE_SHARED_PREFERENCE_KEY, 1);
        if(savedPageNo > 1) {
            mResumeView.setVisibility(View.VISIBLE);
            mResumeView.setAlpha(1f);
            mResumeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEBookPager.setCurrentItem(savedPageNo - 1);
                    hideResumeView();
                }
            });
            mStartOverButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPageNumber = 1;
                    hideResumeView();
                }
            });
        } else {
            mResumeView.setVisibility(View.GONE);
        }
        if(texture > 1) {
            mTextureSpinner.setSelection(texture - 1);
        }
        showHelpIfNeeded();
        mOkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideWordDetailView();
            }
        });
        mGoogleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowAds = false;
                ReadAssistUtils.searchOnGoogle(EbookActivity.this, mCurrentWord);
                hideWordDetailView();
            }
        });
        ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                mPageNumber = position + 1;
                updatePageNumber();
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        };
        mEBookPager.registerOnPageChangeCallback(pageChangeCallback);
        mNextButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mNextButton.setAlpha(1f);
                        mNextButton.setScaleX(1f);
                        mNextButton.setScaleY(1f);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mNextButton.setAlpha(0.8f);
                        mNextButton.setScaleX(0.9f);
                        mNextButton.setScaleY(0.9f);
                        break;
                    case MotionEvent.ACTION_MOVE:
                    default:
                        break;
                }
                return false;
            }
        });
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currItem = mEBookPager.getCurrentItem();
                if(mPages.length > currItem + 1) {
                    mCurrentPageIndex = currItem + 1;
                    mEBookPager.setCurrentItem(mCurrentPageIndex);
                } else {
                    Toast.makeText(mContext, "You are at the last page", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mPreviousButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mPreviousButton.setAlpha(1f);
                        mPreviousButton.setScaleX(1f);
                        mPreviousButton.setScaleY(1f);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mPreviousButton.setAlpha(0.8f);
                        mPreviousButton.setScaleX(0.9f);
                        mPreviousButton.setScaleY(0.9f);
                        break;
                    case MotionEvent.ACTION_MOVE:
                    default:
                        break;
                }
                return false;
            }
        });
        mPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currItem = mEBookPager.getCurrentItem();
                if(currItem - 1 >= 0) {
                    mCurrentPageIndex = currItem - 1;
                    mEBookPager.setCurrentItem(mCurrentPageIndex);
                } else {
                    Toast.makeText(mContext, "You are at the first page", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newTextSize = getContentTextSize((float) progress);
                mAdapter.updateTextSize(newTextSize);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mGoToPageView.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_GO) {
                    submitPageNumber();
                    hideSoftKeyboard();
                    mGoToPageView.clearFocus();
                }
                return false;
            }
        });
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mWordDetailCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideWordDetailView();
            }
        });
    }

    private void clearPageNumberFocus() {
        mGoToPageView.clearFocus();
    }

    private void showHelpIfNeeded() {
        final View helpView = findViewById(R.id.help_view);
        boolean isHelpNeeded = mPreferences.getBoolean(ReadAssistUtils.HELP_NEEDED_PREFERENCE_KEY, true);
        if(isHelpNeeded) {
            helpView.setVisibility(View.VISIBLE);
            Button gotItButton = (Button) findViewById(R.id.got_it_button);
            final CheckBox doNotShowAgain = (CheckBox) findViewById(R.id.donot_show_again_checkbox);
            gotItButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(mPreferences != null) {
                        SharedPreferences.Editor textureEditor = mPreferences.edit();
                        textureEditor.putBoolean(ReadAssistUtils.HELP_NEEDED_PREFERENCE_KEY, !doNotShowAgain.isChecked());
                        textureEditor.apply();
                    }
                    helpView.setVisibility(View.GONE);
                }
            });
        } else {
            helpView.setVisibility(View.GONE);
        }
    }

    private void hideResumeView() {
        mResumeView.animate().alpha(0f).setInterpolator(new AccelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mResumeView.setVisibility(View.GONE);
            }
        }).setDuration(100).start();
    }

    private String getPreferenceKey() {
        return ReadAssistUtils.PAGE_NUMBER_PREFERENCE_HEAD + mEBookName;
    }

    private void saveStates() {
        if(mSource == 1) {
            savePageNumberPreference();
        }
        saveTexturePreference();
    }

    private void saveTexturePreference() {
        if(mPreferences != null) {
            SharedPreferences.Editor textureEditor = mPreferences.edit();
            textureEditor.putInt(ReadAssistUtils.TEXTURE_SHARED_PREFERENCE_KEY, mTexture);
            textureEditor.apply();
        }
    }

    private void savePageNumberPreference() {
        if(mPreferences != null) {
            SharedPreferences.Editor textureEditor = mPreferences.edit();
            textureEditor.putInt(getPreferenceKey(), mPageNumber);
            textureEditor.apply();
        }
    }

    private void updateTexture(int position) {
        mSelectedTexture = mTextureNames[position];
        mEBookPager.setBackgroundResource(mTextureResources[position]);
    }

    private void updatePageNumber() {
        String text = mSource == 1 ?
                String.format(getResources().getString(R.string.page_number_text), mPageNumber, mEBookSize) :
                String.format(getResources().getString(R.string.page_number_text_for_image), mPageNumber, mEBookSize);
        mPageNumberView.setText(text);
    }

    private void submitPageNumber() {
        String pageNoText = mGoToPageView.getText().toString();
        if(pageNoText == null || pageNoText.isEmpty()) {
            return;
        }
        int pageNumber = Integer.parseInt(mGoToPageView.getText().toString());
        pageNumber = pageNumber == 0 ? 1 : pageNumber;
        if(pageNumber < 0 || pageNumber > mEBookSize) {
            String errorMessage = String.format(getString(R.string.max_pages_toast_text), mEBookSize);
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_LONG);
            TextView textView = (TextView) toast.getView().findViewById(android.R.id.message);
            if(textView != null) {
                textView.setGravity(Gravity.CENTER_HORIZONTAL);
            }
            toast.show();
            return;
        }
        mCurrentPageIndex = pageNumber - 1;
        mEBookPager.setCurrentItem(mCurrentPageIndex);
    }

    private float getContentTextSize(float progress) {
        float diff = mMaxFontSize - mDefaultFontSize;
        float percentIncrease = diff * progress/100f;
        return mDefaultFontSize + percentIncrease;
    }

    @Override
    public void onWordClicked(String word) {
        showWordDetailView(word);
    }

    private void hideWordDetailView() {
        mCurrentWord = null;
        mWordDetailView.setVisibility(View.GONE);
    }

    private void showWordDetailView(String word) {
        if(" ".equals(word) || word.length() <= 1) {
            return;
        }
        String wordMeaning = null;
        String wordType = null;
        List<DefinitionItem> itemList = new ArrayList<>();
        Cursor row = DictionaryDBHelper.getWordMeaning(word);
        if(row != null && row.getCount() > 0) {
            mErrorTextView.setVisibility(View.INVISIBLE);
            while(row.moveToNext()) {
                wordMeaning = row.getString(row.getColumnIndex("definition"));
                wordMeaning = wordMeaning.replaceAll("[\\s&&[^\\n]]+", " ");
                wordType = row.getString(row.getColumnIndex("wordtype"));
                mWordView.setText(word);
                populateItemList(wordType, wordMeaning, itemList);
            }
            fetchAndCreateDefinitions(itemList);
            mDefinitionsView.setVisibility(View.VISIBLE);
        } else {
            boolean singularFound = false;
            if(word.endsWith("s") || word.endsWith("ly")) {
                int length = word.length();
                String baseWord = word.endsWith("s") ? word.substring(0, length-1) : word.substring(0, length-2);
                Cursor baseRow = DictionaryDBHelper.getWordMeaning(baseWord);
                if(baseRow != null && baseRow.getCount() > 0) {
                    mErrorTextView.setVisibility(View.INVISIBLE);
                    while (baseRow.moveToNext()) {
                        wordMeaning = baseRow.getString(row.getColumnIndex("definition"));
                        wordMeaning = wordMeaning.replaceAll("[\\s&&[^\\n]]+", " ");
                        wordType = baseRow.getString(row.getColumnIndex("wordtype"));
                        word = baseWord;
                        mWordView.setText(baseWord);
                        populateItemList(wordType, wordMeaning, itemList);
                    }
                    singularFound = true;
                    fetchAndCreateDefinitions(itemList);
                    mDefinitionsView.setVisibility(View.VISIBLE);
                }
            }
            if(!singularFound) {
                mErrorTextView.setVisibility(View.VISIBLE);
                mDefinitionsView.setVisibility(View.GONE);
            }
        }
        mWordView.setText(word);
        mCurrentWord = word;
        mWordDetailView.setVisibility(View.VISIBLE);
    }

    private void populateItemList(String type, String definition, List<DefinitionItem> itemList) {
        DefinitionItem item = new DefinitionItem();
        item.setWordType(ReadAssistUtils.getFullWordType(type));
        item.setDefinition(definition);
        itemList.add(item);
    }

    private void fetchAndCreateDefinitions(List<DefinitionItem> itemList) {
        EbookDefinitionAdaper adapter = new EbookDefinitionAdaper(itemList); // Make item list
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mDefinitionsView.setLayoutManager(layoutManager);
        mDefinitionsView.setAdapter(adapter);
    }

    private void hideSoftKeyboard() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(ReadAssistUtils.TAG, "showing ad from Ebook screen");
        if(mShowAds) {
            AdsHelper.showInterstitialAd();
        }
        saveStates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mShowAds = true;
        clearPageNumberFocus();
    }
}