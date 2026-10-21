package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.graphics.PorterDuff;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.annimon.stream.function.Consumer;
import android.widget.LinearLayout;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.fragments.DeviceIdDialogFragment;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.fragments.StatusFragment;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.onboarding.OnboardingActivity;
import com.nutomic.syncthingandroid.settings.SettingsActivity;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.AppUpdater;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.PermissionUtil;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.CompletionRingView;
import com.nutomic.syncthingandroid.widget.SmWidgetProvider;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Shows {@link FolderListFragment} and
 * {@link DeviceListFragment} in different tabs, and
 * {@link DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "MainActivity";

    private Boolean ENABLE_VERBOSE_LOG = false;

    private static final int FOLDER_FRAGMENT_ID = 0;
    private static final int DEVICE_FRAGMENT_ID = 1;
    private static final int STATUS_FRAGMENT_ID = 2;
    
    /**
     * Intent action to exit app.
     */
    public static final String ACTION_EXIT =
            ".MainActivity.EXIT";

    /**
     * Time after first start when usage reporting dialog should be shown.
     * See {@link #showUsageReportingDialog}
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);
    private static final Boolean DEBUG_FORCE_USAGE_REPORTING_DIALOG = false;

    private AlertDialog mUsageReportingDialog;
    private Dialog mRestartDialog;

    private SyncthingService.State mSyncthingServiceState = SyncthingService.State.INIT;

    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private StatusFragment     mStatusFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;

    private Intent mLastIntent;
    private Boolean oneTimeShot = true;

    @Inject public SharedPreferences mPreferences;
    private ConfigRouter mConfig;
    private final Handler mUIRefreshHandler = new Handler();

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                // Close drawer on back button press.
                closeDrawer();
            } else {
                /**
                 * Leave MainActivity in its state as the home button was pressed.
                 * This will avoid waiting for the loading spinner when getting back
                 * and give changes to do UI updates based on EventProcessor in the future.
                 */
                moveTaskToBack(true);
            }
        }
    };

    private Runnable mUIRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mUIRefreshHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL);
        }
    };

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mSyncthingServiceState = currentState;
        if (oneTimeShot) {
            updateViewPager();
            oneTimeShot = false;
        }

        // Update status light indicating if syncthing is running.
        updateStatusLight(currentState);

        switch (currentState) {
            case ACTIVE:
                // Check if the usage reporting minimum delay passed by.
                Boolean usageReportingDelayPassed = (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY);
                RestApi restApi = getApi();
                if (        (
                                DEBUG_FORCE_USAGE_REPORTING_DIALOG
                            ) || (
                                usageReportingDelayPassed &&
                                restApi != null &&
                                restApi.isConfigLoaded() &&
                                !restApi.isUsageReportingDecided()
                            )) {
                    showUsageReportingDialog(restApi);
                }
                break;
            case ERROR:
                finish();
                break;
        }
        updateTotalSyncProgressBar();
    }

    /**
     * Updates the status light (on the status page) indicating if syncthing is running.
     * Called on service state changes and when the status page view is (re)created.
     */
    public void updateStatusLight(SyncthingService.State currentState) {
        // The legacy status dot on the status page was replaced by the completion ring,
        // which is driven by {@link #updateTotalSyncProgressBar()}. Kept as a no-op so
        // existing callers remain valid.
    }

    public SyncthingService.State getSyncthingServiceState() {
        return mSyncthingServiceState;
    }

    /**
     * Returns the unix timestamp at which the app was first installed.
     */
    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        long firstInstallTime = 0;
        try {
            firstInstallTime = pm.getPackageInfo(getPackageName(), 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "This should never happen", e);
        }
        return firstInstallTime;
    }

    /**
     * Initializes tab navigation.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        mConfig = new ConfigRouter(MainActivity.this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        if (ENABLE_VERBOSE_LOG) {
            Util.testPathEllipsis();
        }

        setContentView(R.layout.activity_main);
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mViewPager = findViewById(R.id.pager);
        
        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState != null) {
            mFolderListFragment = (FolderListFragment) fm.getFragment(
                    savedInstanceState, FolderListFragment.class.getName());
            mDeviceListFragment = (DeviceListFragment) fm.getFragment(
                    savedInstanceState, DeviceListFragment.class.getName());
            mStatusFragment = (StatusFragment) fm.getFragment(
                    savedInstanceState, StatusFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
        }
        if (mFolderListFragment == null) {
            mFolderListFragment = new FolderListFragment();
        }
        if (mDeviceListFragment == null) {
            mDeviceListFragment = new DeviceListFragment();
        }
        if (mStatusFragment == null) {
            mStatusFragment = new StatusFragment();
        }
        if (mDrawerFragment == null) {
            mDrawerFragment = new DrawerFragment();
        }

        fm.beginTransaction().replace(R.id.drawer, mDrawerFragment).commitAllowingStateLoss();
        mDrawerToggle = new Toggle(this, mDrawerLayout, mDrawerFragment);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        /**
         * SyncthingService needs to be started from this activity as the user
         * can directly launch this activity from the recent activity switcher.
         */
        Intent serviceIntent = new Intent(this, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        onNewIntent(getIntent());

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }
    
    
    @Override
    protected void onNewIntent(Intent intent) {
        mLastIntent = intent;
        super.onNewIntent(intent);
    };


    /**
     * Updates the ViewPager to show tabs depending on the service state.
     */
    private void updateViewPager() {
        final int numPages = 3;
        FragmentStatePagerAdapter mSectionsPagerAdapter =
                new FragmentStatePagerAdapter(getSupportFragmentManager()) {

            @Override
            public Fragment getItem(int position) {
                switch (position) {
                    case FOLDER_FRAGMENT_ID:
                        return mFolderListFragment;
                    case DEVICE_FRAGMENT_ID:
                        return mDeviceListFragment;
                    case STATUS_FRAGMENT_ID:
                        return mStatusFragment;
                    default:
                        return null;
                }
            }

            @Override
            public int getItemPosition(Object object) {
                return this.POSITION_NONE;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                // Guard against the known FragmentStatePagerAdapter restore bug
                // (b/36956111): after a rotation / dark-mode reconfiguration the
                // fragment manager may already hold the restored fragment, and
                // re-adding it crashes with "Fragment already added" inside the
                // ViewPager's first onMeasure. Hand back the existing fragment.
                String tag = "android:switcher:" + container.getId() + ":" + position;
                Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
                if (existing != null && existing.isAdded()) {
                    return existing;
                }
                return super.instantiateItem(container, position);
            }

            @Override
            public int getCount() {
                return numPages;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case FOLDER_FRAGMENT_ID:
                        return getResources().getString(R.string.folders_fragment_title);
                    case DEVICE_FRAGMENT_ID:
                        return getResources().getString(R.string.devices_fragment_title);
                    case STATUS_FRAGMENT_ID:
                        return getResources().getString(R.string.status_fragment_title);
                    default:
                        return String.valueOf(position);
                }
            }
        };
        try {
            mViewPager.setAdapter(mSectionsPagerAdapter);
        } catch (IllegalStateException e) {
            /**
             * IllegalStateException happens due to a bug in FragmentStatePagerAdapter.
             * For more information see:
             * - https://issuetracker.google.com/issues/36956111
             */
            Log.e(TAG, "updateViewPager: IllegalStateException in setAdapter.", e);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.exception_known_bug_notice, getString(R.string.issue_tracker_url), "108"))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
                    .show();
        }
        LinearLayout segmentedTabs = findViewById(R.id.tabContainer);
        setupSegmentedTabs(segmentedTabs);
    }

    /**
     * Smartisan segmented control wiring: click switches the page, page
     * selection updates the selected pill (driven by state_selected).
     */
    private void setupSegmentedTabs(LinearLayout container) {
        if (container.getTag(R.id.tabContainer) != null) {
            updateSegmentedSelection(container, mViewPager.getCurrentItem());
            return;
        }
        container.setTag(R.id.tabContainer, Boolean.TRUE);
        View.OnClickListener listener = v -> {
            int position = container.indexOfChild(v);
            if (mViewPager != null) {
                mViewPager.setCurrentItem(position, true);
            }
        };
        for (int i = 0; i < container.getChildCount(); i++) {
            container.getChildAt(i).setOnClickListener(listener);
        }
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    updateSegmentedSelection(container, position);
                    ImageButton overflow = findViewById(R.id.actionOverflow);
                    if (overflow != null) {
                        updateOverflowVisibility(overflow);
                    }
                }
        });
        updateSegmentedSelection(container, mViewPager.getCurrentItem());
    }

    private void updateSegmentedSelection(LinearLayout container, int position) {
        for (int i = 0; i < container.getChildCount(); i++) {
            container.getChildAt(i).setSelected(i == position);
        }
    }

    @Override
    public void onPause() {
        stopUIRefreshHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        // Check if storage permission has been revoked at runtime.
        if (!PermissionUtil.haveStoragePermission(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            this.finish();
            super.onResume();
            return;
        }

        // Evaluate run conditions to detect changes made to the metered wifi flags.
        SyncthingService mSyncthingService = getService();
        if (mSyncthingService != null) {
            mSyncthingService.evaluateRunConditions();
        }

        startUIRefreshHandler();

        String action = mLastIntent.getAction();
        if (action != null) {
            if (ACTION_EXIT.equals(action)) {
                Log.i(TAG, "Exit app requested by notification action");
                stopService(new Intent(this, SyncthingService.class));
                finishAndRemoveTask();
            }
        }

        maybeAutoCheckForUpdate();

        super.onResume();
    }

    /**
     * Silently checks for a new in-app update at most once per day and shows
     * a themed dialog when an update is available.
     */
    private void maybeAutoCheckForUpdate() {
        if (mPreferences == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = mPreferences.getLong("last_update_check", 0L);
        if (now - last < 24L * 60L * 60L * 1000L) {
            return;
        }
        mPreferences.edit().putLong("last_update_check", now).apply();
        AppUpdater.fetchUpdateInfo(info -> {
            if (info == null || isFinishing() || isDestroyed()) {
                return;
            }
            if (!AppUpdater.isNewer(info, this)) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                String changelog = info.getChangelog() == null ? "" : info.getChangelog();
                new AlertDialog.Builder(this)
                        .setTitle(R.string.update_available_title)
                        .setMessage(getString(R.string.update_available_text,
                                info.getVersionName(), changelog))
                        .setPositiveButton(R.string.update_download, (dialog, which) ->
                                AppUpdater.downloadApk(MainActivity.this, info,
                                        progress -> { },
                                        file -> {
                                            if (file == null) {
                                                Toast.makeText(this,
                                                        R.string.update_download_failed,
                                                        Toast.LENGTH_LONG).show();
                                                return;
                                            }
                                            if (!AppUpdater.canInstall(this)) {
                                                AppUpdater.openInstallPermissionSettings(this);
                                                return;
                                            }
                                            AppUpdater.installApk(this, file);
                                        }))
                        .setNegativeButton(R.string.update_later, null)
                        .show();
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService mSyncthingService = getService();
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this);
            mSyncthingService.unregisterOnServiceStateChangeListener(mDrawerFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mFolderListFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mDeviceListFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mStatusFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = syncthingServiceBinder.getService();
        syncthingService.registerOnServiceStateChangeListener(this);
        syncthingService.registerOnServiceStateChangeListener(mDrawerFragment);
        syncthingService.registerOnServiceStateChangeListener(mFolderListFragment);
        syncthingService.registerOnServiceStateChangeListener(mDeviceListFragment);
        syncthingService.registerOnServiceStateChangeListener(mStatusFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        FragmentManager fm = getSupportFragmentManager();
        fm.executePendingTransactions();
        Consumer<Fragment> putFragment = fragment -> {
            if (fragment != null && fragment.isAdded()) {
                fm.putFragment(outState, fragment.getClass().getName(), fragment);
            }
        };
        putFragment.accept(mFolderListFragment);
        putFragment.accept(mDeviceListFragment);
        putFragment.accept(mStatusFragment);

        Util.dismissDialogSafe(mRestartDialog, this);
        Util.dismissDialogSafe(mUsageReportingDialog, this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.btn_menu);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }
            });
        }

        mDrawerToggle.syncState();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            // Smartisan header uses its own centered title view.
            actionBar.setDisplayShowTitleEnabled(false);
        }

        setupOverflowMenu();
    }

    /**
     * Smartisan toolbar kebab: collapses refresh + add into one overflow popup.
     * Menu contents depend on the active segmented page; hidden on the status page.
     */
    private static final int MENU_REFRESH = 1;
    private static final int MENU_ADD = 2;

    private void setupOverflowMenu() {
        ImageButton overflow = findViewById(R.id.actionOverflow);
        if (overflow == null) {
            return;
        }
        overflow.setOnClickListener(v -> showOverflowMenu(overflow));

        // Status page shortcut: gear icon opens settings directly.
        ImageButton settings = findViewById(R.id.actionSettings);
        if (settings != null) {
            settings.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }
        updateOverflowVisibility(overflow);
    }

    private void updateOverflowVisibility(View overflow) {
        ImageButton settings = findViewById(R.id.actionSettings);
        if (mViewPager == null) {
            overflow.setVisibility(View.GONE);
            if (settings != null) {
                settings.setVisibility(View.GONE);
            }
            return;
        }
        boolean onStatus = mViewPager.getCurrentItem() == STATUS_FRAGMENT_ID;
        // Status page shows the settings shortcut; other pages the kebab menu.
        overflow.setVisibility(onStatus ? View.GONE : View.VISIBLE);
        if (settings != null) {
            settings.setVisibility(onStatus ? View.VISIBLE : View.GONE);
        }
    }

    private void showOverflowMenu(View anchor) {
        if (mViewPager == null) {
            return;
        }
        int page = mViewPager.getCurrentItem();
        boolean isFolderPage = page == FOLDER_FRAGMENT_ID;
        boolean isDevicePage = page == DEVICE_FRAGMENT_ID;
        if (!isFolderPage && !isDevicePage) {
            return;
        }
        int[] itemTitles = isFolderPage
                ? new int[]{R.string.refresh, R.string.add_folder}
                : new int[]{R.string.refresh, R.string.add_device};

        // Smartisan compact popup, matching the RecentChanges menu: the white
        // rounded card is the POPUP WINDOW background, so its outline drives a
        // real elevation shadow and the panel visibly floats above the page.
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padV = dp2px(8);
        container.setPadding(0, padV, 0, padV);

        final PopupWindow[] popupHolder = new PopupWindow[1];
        for (int i = 0; i < itemTitles.length; i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.sm_hairline));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp2px(1));
                dividerParams.setMargins(dp2px(16), 0, dp2px(16), 0);
                container.addView(divider, dividerParams);
            }
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp2px(40));
            row.setPadding(dp2px(14), 0, dp2px(16), 0);

            ImageView icon = new ImageView(this);
            icon.setImageResource(i == 0
                    ? R.drawable.baseline_refresh_24
                    : R.drawable.ic_add_smartisan);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.sm_ink));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dp2px(16), dp2px(16));
            row.addView(icon, iconParams);

            TextView item = new TextView(this);
            item.setText(itemTitles[i]);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            item.setTextColor(ContextCompat.getColor(this, R.color.sm_ink));
            item.setSingleLine(true);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMargins(dp2px(10), 0, 0, 0);
            row.addView(item, textParams);
            row.setOnClickListener(v -> {
                if (popupHolder[0] != null) {
                    popupHolder[0].dismiss();
                }
                handleOverflowAction(index == 0 ? MENU_REFRESH : MENU_ADD, page);
            });
            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        PopupWindow popup = new PopupWindow(container,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        // The card itself is the window background: its rounded outline is what
        // makes the 12dp elevation render as a real floating shadow.
        popup.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_popup_menu));
        popup.setElevation(dp2px(12));
        popupHolder[0] = popup;

        // Absolute positioning so the menu's right edge lines up with the
        // kebab icon itself (Gravity.END aligns to the whole 42dp button and
        // reads as overshooting the three dots).
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = container.getMeasuredWidth();
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int iconWidth = dp2px(24); // kebab glyph, centered in the 42dp button
        int iconRight = loc[0] + (anchor.getWidth() + iconWidth) / 2;
        int x = Math.max(dp2px(8), iconRight - popupWidth);
        // Panel top sits flush against the toolbar bottom — no gap.
        View toolbar = findViewById(R.id.toolbar);
        int y;
        if (toolbar != null) {
            int[] tloc = new int[2];
            toolbar.getLocationOnScreen(tloc);
            y = tloc[1] + toolbar.getHeight();
        } else {
            y = loc[1] + anchor.getHeight();
        }
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private void handleOverflowAction(int action, int page) {
        if (action == MENU_REFRESH) {
            if (page == FOLDER_FRAGMENT_ID && mFolderListFragment != null) {
                mFolderListFragment.rescanAll();
            } else if (page == DEVICE_FRAGMENT_ID && mDeviceListFragment != null) {
                mDeviceListFragment.refresh();
            }
        } else if (action == MENU_ADD) {
            if (page == FOLDER_FRAGMENT_ID && mFolderListFragment != null) {
                mFolderListFragment.onAddFolder();
            } else if (page == DEVICE_FRAGMENT_ID && mDeviceListFragment != null) {
                mDeviceListFragment.onAddDevice();
            }
        }
    }

    private int dp2px(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void startUIRefreshHandler() {
        LogV("startUIRefreshHandler");
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable);
        mUIRefreshHandler.post(mUIRefreshRunnable);
    }

    private void stopUIRefreshHandler() {
        LogV("stopUIRefreshHandler");
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable);
    }

    public void showRestartDialog(){
        mRestartDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_confirm_restart)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i1) -> this.startService(new Intent(this, SyncthingService.class)
                        .setAction(SyncthingService.ACTION_RESTART)))
                .setNegativeButton(android.R.string.no, null)
                .create();
        mRestartDialog.show();
    }

    public void showQrCodeDialog() {
        String deviceId = mPreferences.getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(deviceId)) {
            Toast.makeText(this, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show();
            return;
        }
        RestApi restApi = getApi();
        List<Device> devices = mConfig.getDevices(restApi, true);
        String deviceName = "";

        for (Device d : devices) {
            if (d.deviceID.equals(deviceId)) {
                deviceName = d.getDisplayName();
                break;
            }
        }

        DeviceIdDialogFragment.Companion.show(
                getSupportFragmentManager(),
                deviceName.trim(),
                deviceId,
                true
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Handles drawer opened and closed events, toggling option menu state.
     */
    private static class Toggle extends ActionBarDrawerToggle {

        private final DrawerFragment drawerFragment;

        public Toggle(AppCompatActivity activity, DrawerLayout drawerLayout, DrawerFragment drawerFragment) {
            super(activity, drawerLayout, R.string.open_main_menu, R.string.close_main_menu);
            setDrawerIndicatorEnabled(false);
            this.drawerFragment = drawerFragment;
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            drawerFragment.drawerOpened();
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            super.onDrawerSlide(drawerView, 0);
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    /**
     * Toggles the drawer on menu button press.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            } else {
                closeDrawer();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    /**
     * Displays dialog asking user to accept/deny usage reporting.
     */
    private void showUsageReportingDialog(RestApi restApi) {
        Log.v(TAG, "showUsageReportingDialog triggered.");
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            try {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        restApi.setUsageReporting(true);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        restApi.setUsageReporting(false);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        final Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                        intent.putExtra(WebViewActivity.EXTRA_WEB_URL, getString(R.string.syncthing_usage_stats_url));
                        startActivity(intent);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "showUsageReportingDialog:OnClickListener", e);
            }
        };

        restApi.getUsageReport(report -> {
            @SuppressLint("InflateParams")
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.dialog_usage_reporting, null);
            TextView tv = v.findViewById(R.id.example);
            tv.setText(report);
            Util.dismissDialogSafe(mUsageReportingDialog, MainActivity.this);
            mUsageReportingDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.usage_reporting_dialog_title)
                    .setView(v)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(R.string.open_website, listener)
                    .show();
        });
    }

    // The timer is periodically triggering while the user is looking at the UI.
    private void onTimerEvent() {
        updateTotalSyncProgressBar();
    }

    /**
     * Mirrors the current app state to the Smartisan home screen widgets
     * (2x2 / 4x2). Called on service state changes and on every sync
     * progress refresh, so the widget is live while the app runs.
     */
    private void pushWidgetUpdate() {
        RestApi restApi = getApi();
        boolean running = mSyncthingServiceState == SyncthingService.State.ACTIVE;
        boolean starting = mSyncthingServiceState == SyncthingService.State.STARTING;
        int folders = 0;
        int devices = 0;
        int percent = -1;
        if (restApi != null && restApi.isConfigLoaded()) {
            folders = restApi.getFolders().size();
            devices = restApi.getDevices(false).size();
            if (running) {
                percent = restApi.getTotalSyncCompletion();
                if (percent == -1) {
                    percent = restApi.getLocalFolderCompletion();
                }
            }
        }
        SmWidgetProvider.pushUpdate(this, running, starting, folders, devices, percent);
    }

    @SuppressLint("SetTextI18n")
    private void updateTotalSyncProgressBar() {
        pushWidgetUpdate();
        CompletionRingView completionRing = (CompletionRingView) findViewById(R.id.completionRing);
        TextView tvRingSub = (TextView) findViewById(R.id.tvRingSub);
        if (completionRing == null || tvRingSub == null) {
            return;
        }

        RestApi restApi = getApi();
        if (restApi == null || !restApi.isConfigLoaded()) {
            completionRing.setVisibility(View.GONE);
            tvRingSub.setVisibility(View.GONE);
            return;
        }

        int totalSyncCompletePercent = restApi.getTotalSyncCompletion();
        if (totalSyncCompletePercent == -1) {
            // No remote devices configured: fall back to local folder completion so the
            // completion ring still renders instead of leaving an empty card.
            totalSyncCompletePercent = restApi.getLocalFolderCompletion();
        }
        boolean showTotalSyncProgress =
                (mSyncthingServiceState == SyncthingService.State.ACTIVE);
        if (!showTotalSyncProgress) {
            completionRing.setVisibility(View.GONE);
            tvRingSub.setVisibility(View.GONE);
            return;
        }
        completionRing.setVisibility(View.VISIBLE);
        tvRingSub.setVisibility(View.VISIBLE);
        completionRing.setProgress(totalSyncCompletePercent);
        completionRing.setCenterText(totalSyncCompletePercent + "%");

        // "全局状态 · 已同步 X / Y" sub-caption.
        long[] bytes = restApi.getTotalSyncBytes();
        String synced = Util.readableFileSize(this, bytes[0]);
        String total = Util.readableFileSize(this, bytes[1]);
        tvRingSub.setText(getString(R.string.global_status_synced, synced, total));
    }

    /**
     * Exits the application by stopping the service and finishing the activity.
     * This method is called directly from the FAB since it's only visible when safe to exit.
     * Also called from drawer's exit button
     */
    public void doExit() {
        if (isFinishing()) {
            return;
        }
        Log.i(TAG, "Exiting app on user request");
        stopService(new Intent(this, SyncthingService.class));
        finishAndRemoveTask();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

}
