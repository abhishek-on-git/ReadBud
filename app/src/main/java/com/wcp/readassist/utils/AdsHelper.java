package com.wcp.readassist.utils;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;


public class AdsHelper {
    private static boolean mInterstitialAdsEnabled = false;
    private static final String TAG = "AdsHelper";
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-8970396160161366/9137545925";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8970396160161366/2590101832";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";

    private static InterstitialAd mInterstitialAd;
    public static void initializeAdsSDK(final Context context) {
        MobileAds.initialize(context, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                Log.e(TAG, "Ad SDK initialized");
                if(!mInterstitialAdsEnabled)  {
                    return;
                }
                loadInterstitialAd(context);
            }
        });
    }

    public static void toggleInterstitialAds(boolean enable) {
        mInterstitialAdsEnabled = enable;
        Log.e(TAG, "Interstitial Ads enabled");
    }

    public static void loadBannerAd(AdView adView) {
//        List<String> testDeviceIds = Arrays.asList("425711838916486305C8C6DE31DCCBF7");
//        RequestConfiguration configuration =
//                new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
//        MobileAds.setRequestConfiguration(configuration);
        adView.setAdListener(new AdListener(){
            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                super.onAdFailedToLoad(error);
                int errorCode = error.getCode();
                String errorMessage = error.getMessage();
                AdError cause = error.getCause();
                Log.e(TAG, "Banner Ad failed to load : "+errorMessage + " error code = "+errorCode+" cause = "+cause);
            }
        });
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    public static void loadInterstitialAd(final Context context) {
        if(!mInterstitialAdsEnabled)  {
            return;
        }
        mInterstitialAd = new InterstitialAd(context);
        mInterstitialAd.setAdUnitId(INTERSTITIAL_AD_UNIT_ID);
        AdRequest adRequest = new AdRequest.Builder().build();
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                super.onAdFailedToLoad(error);
                int errorCode = error.getCode();
                String errorMessage = error.getMessage();
                AdError cause = error.getCause();
                Log.e(TAG, "Interstitial Ad failed to load : "+errorMessage + " error code = "+errorCode+" cause = "+cause);
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
            }

            @Override
            public void onAdClosed() {
                super.onAdClosed();
                loadInterstitialAd(context);
            }

            @Override
            public void onAdLeftApplication() {
                super.onAdLeftApplication();
                Log.e(ReadAssistUtils.TAG, "onAdLeftApplication");
            }
        });
        mInterstitialAd.loadAd(adRequest);
    }

    public static void showInterstitialAd() {
        if(!mInterstitialAdsEnabled)  {
            return;
        }
        if(mInterstitialAd != null && mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
    }
}
