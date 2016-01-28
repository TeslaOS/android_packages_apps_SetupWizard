/*
 * Copyright (C) 2015 The Tesla OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tesla.setupwizard.setup;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.android.internal.telephony.MccTable;
import com.tesla.setupwizard.R;
import com.tesla.setupwizard.ui.LocalePicker;
import com.tesla.setupwizard.ui.SetupPageFragment;
import com.tesla.setupwizard.util.SetupWizardUtils;

import java.util.List;
import java.util.Locale;

public class WelcomePage extends SetupPage {

    public static final String TAG = "WelcomePage";

    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";

    public WelcomePage(Context context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        Fragment fragment = fragmentManager.findFragmentByTag(getKey());
        if (fragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            fragment = new WelcomeFragment();
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public int getTitleResId() {
        return R.string.setup_welcome;
    }

    @Override
    public boolean doNextAction() {
            if (mWelcomeFragment != null) {
                mWelcomeFragment.sendLocaleStats();
            }
            return super.doNextAction();
        }
    }

    @Override
    public boolean doPreviousAction() {
        Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        ActivityOptions options =
                ActivityOptions.makeCustomAnimation(mContext,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out);
        mContext.startActivity(intent, options.toBundle());
        return true;
    }

    @Override
    public String getKey() {
        return TAG;
    }

    @Override
    public int getPrevButtonTitleResId() {
        return R.string.emergency_call;
    }

    private boolean isLocked() {
        boolean isAuthorized = ((SetupWizardApp) mContext.getApplicationContext()).isAuthorized();
        if (SetupWizardUtils.isDeviceLocked()) {
            return !isAuthorized;
        }
        return false;
    }

    public void simChanged() {
        if (mWelcomeFragment != null) {
            mWelcomeFragment.fetchAndUpdateSimLocale();
        }
    }

    public static class WelcomeFragment extends SetupPageFragment {

        private ArrayAdapter<com.android.internal.app.LocalePicker.LocaleInfo> mLocaleAdapter;
        private Locale mInitialLocale;
        private Locale mCurrentLocale;
        private int[] mAdapterIndices;
        private boolean mIgnoreSimLocale;
        private LocalePicker mLanguagePicker;
        private FetchUpdateSimLocaleTask mFetchUpdateSimLocaleTask;
        private final Handler mHandler = new Handler();

        private final Runnable mUpdateLocale = new Runnable() {
            public void run() {
                if (mCurrentLocale != null) {
                    mLanguagePicker.setEnabled(false);
                    com.android.internal.app.LocalePicker.updateLocale(mCurrentLocale);
                }
            }
        };

        @Override
        protected void initializePage() {
            mLanguagePicker = (LocalePicker) mRootView.findViewById(R.id.locale_list);
            loadLanguages();
            final boolean brandedDevice = getResources().getBoolean(
                    R.bool.branded_device);
            if (brandedDevice) {
                mRootView.findViewById(R.id.powered_by_logo).setVisibility(View.VISIBLE);
            }
        }

        private void loadLanguages() {
            mLocaleAdapter = com.android.internal.app.LocalePicker.constructAdapter(getActivity(), R.layout.locale_picker_item, R.id.locale);
            mCurrentLocale = mInitialLocale = Locale.getDefault();
            fetchAndUpdateSimLocale();
            mAdapterIndices = new int[mLocaleAdapter.getCount()];
            int currentLocaleIndex = 0;
            String [] labels = new String[mLocaleAdapter.getCount()];
            for (int i=0; i<mAdapterIndices.length; i++) {
                com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo = mLocaleAdapter.getItem(i);
                Locale localLocale = localLocaleInfo.getLocale();
                if (localLocale.equals(mCurrentLocale)) {
                    currentLocaleIndex = i;
                }
                mAdapterIndices[i] = i;
                labels[i] = localLocaleInfo.getLabel();
            }
            mLanguagePicker.setDisplayedValues(labels);
            mLanguagePicker.setMaxValue(labels.length - 1);
            mLanguagePicker.setValue(currentLocaleIndex);
            mLanguagePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            mLanguagePicker.setOnValueChangedListener(new LocalePicker.OnValueChangeListener() {
                public void onValueChange(LocalePicker picker, int oldVal, int newVal) {
                    setLocaleFromPicker();
                }
            });
            mLanguagePicker.setOnScrollListener(new LocalePicker.OnScrollListener() {
                @Override
                public void onScrollStateChange(LocalePicker view, int scrollState) {
                    if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                        mIgnoreSimLocale = true;
                    }
                }
            });
        }

        private void setLocaleFromPicker() {
            mIgnoreSimLocale = true;
            int i = mAdapterIndices[mLanguagePicker.getValue()];
            final com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo = mLocaleAdapter.getItem(i);
            onLocaleChanged(localLocaleInfo.getLocale());
        }

        private void onLocaleChanged(Locale paramLocale) {
            mLanguagePicker.setEnabled(true);
            Resources localResources = getActivity().getResources();
            Configuration localConfiguration1 = localResources.getConfiguration();
            Configuration localConfiguration2 = new Configuration();
            localConfiguration2.locale = paramLocale;
            localResources.updateConfiguration(localConfiguration2, null);
            localResources.updateConfiguration(localConfiguration1, null);
            mHandler.removeCallbacks(mUpdateLocale);
            mCurrentLocale = paramLocale;
            mHandler.postDelayed(mUpdateLocale, 1000);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.setup_welcome_page;
        }

        public void fetchAndUpdateSimLocale() {
            if (mIgnoreSimLocale || isDetached()) {
                return;
            }
            if (mFetchUpdateSimLocaleTask != null) {
                mFetchUpdateSimLocaleTask.cancel(true);
            }
            mFetchUpdateSimLocaleTask = new FetchUpdateSimLocaleTask();
            mFetchUpdateSimLocaleTask.execute();
        }

        private class FetchUpdateSimLocaleTask extends AsyncTask<Void, Void, Locale> {
            @Override
            protected Locale doInBackground(Void... params) {
                Locale locale = null;
                Activity activity = getActivity();
                if (activity != null) {
                    final SubscriptionManager subscriptionManager =
                            SubscriptionManager.from(activity);
                    List<SubscriptionInfo> activeSubs =
                            subscriptionManager.getActiveSubscriptionInfoList();
                    if (activeSubs == null || activeSubs.isEmpty()) {
                        return null;
                    }

                    // Fetch locale for active sim's MCC
                    int mcc = activeSubs.get(0).getMcc();
                    locale = MccTable.getLocaleFromMcc(activity, mcc, null);

                    // If that fails, fall back to preferred languages reported
                    // by the sim
                    if (locale == null) {
                        TelephonyManager telephonyManager = (TelephonyManager) activity.
                                getSystemService(Context.TELEPHONY_SERVICE);
                        String localeString = telephonyManager.getLocaleFromDefaultSim();
                        if (localeString != null) {
                            locale = Locale.forLanguageTag(localeString);

                        }
                    }
                }
                return locale;
            }

            @Override
            protected void onPostExecute(Locale simLocale) {
                if (simLocale != null && !simLocale.equals(mCurrentLocale)) {
                    if (!mIgnoreSimLocale && !isDetached()) {
                        String label = getString(R.string.sim_locale_changed,
                                simLocale.getDisplayName());
                        Toast.makeText(getActivity(), label, Toast.LENGTH_SHORT).show();
                        onLocaleChanged(simLocale);
                    }
                }
            }
        }
    }

}
