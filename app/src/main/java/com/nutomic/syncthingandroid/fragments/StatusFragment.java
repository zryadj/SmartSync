package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.LeadingMarginSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.SystemStatus;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.settings.SettingsActivity;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.SegmentedButton;
import com.nutomic.syncthingandroid.views.SegmentedButton.OnClickListenerSegmentedButton;

import java.util.concurrent.TimeUnit;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION;

/**
 * Displays why syncthing is running or disabled.
 */
public class StatusFragment extends Fragment implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "StatusFragment";

    private Boolean ENABLE_VERBOSE_LOG = false;

    @Inject SharedPreferences mPreferences;

    private Runnable mRestApiQueryRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mRestApiQueryHandler.postDelayed(this, Constants.REST_UPDATE_INTERVAL);
        }
    };

    private MainActivity mActivity;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;
    private final Handler mRestApiQueryHandler = new Handler();
    private Boolean mLastVisibleToUser = false;
    private SegmentedButton btnForceStartStop;
    private View reasonHeader;
    private TextView reasonText;
    private ImageView reasonArrow;
    private boolean reasonExpanded = false;

    // Key/value pair views (card 2).
    private TextView mKvUptime;
    private TextView mKvRam;
    private TextView mKvDownload;
    private TextView mKvUpload;
    private TextView mKvAnnounce;
    /**
     * Object that must be locked upon accessing the status holders.
     */
    private final Object mStatusHolderLock = new Object();

    /**
     * Status holders, filled on callbacks.
     */
    private String mRamUsage = "";
    private String mDownload = "";
    private String mUpload = "";
    private String mAnnounceServer = "";
    private String mUptime = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getActivity().getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // User switched to the current tab, start handler.
            startRestApiQueryHandler();
        } else {
            // User switched away to another tab, stop handler.
            stopRestApiQueryHandler();
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        stopRestApiQueryHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLastVisibleToUser) {
            startRestApiQueryHandler();
        }
        // Refresh the status light + sync percent row (views are recreated with this fragment).
        MainActivity mainActivity = (MainActivity) mActivity;
        if (mainActivity != null) {
            mainActivity.updateStatusLight(mainActivity.getSyncthingServiceState());
        }
    }

    private void startRestApiQueryHandler() {
        LogV("startUpdateListHandler");
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
        mRestApiQueryHandler.post(mRestApiQueryRunnable);
    }

    private void stopRestApiQueryHandler() {
        LogV("stopUpdateListHandler");
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateStatus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Wire up key/value views (card 2).
        mKvUptime = view.findViewById(R.id.kvUptimeVal);
        mKvRam = view.findViewById(R.id.kvRamVal);
        mKvDownload = view.findViewById(R.id.kvDownloadVal);
        mKvUpload = view.findViewById(R.id.kvUploadVal);
        mKvAnnounce = view.findViewById(R.id.kvAnnounceVal);

        // Wire up the collapsible reason row (card 1).
        reasonHeader = view.findViewById(R.id.reasonHeader);
        reasonText = view.findViewById(R.id.reasonText);
        reasonArrow = view.findViewById(R.id.reasonArrow);
        reasonHeader.setOnClickListener(v -> toggleReasonCard());

        updateStatus();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (MainActivity) getActivity();

        // Create button group.
        btnForceStartStop = (SegmentedButton) mActivity.findViewById(R.id.forceStartStop);
        btnForceStartStop.clearButtons();
        btnForceStartStop.addButtons(
                getString(R.string.button_follow_run_conditions),
                getString(R.string.button_force_start),
                getString(R.string.button_force_stop)
        );
        btnForceStartStop.setOnClickListener(new OnClickListenerSegmentedButton() {
                @Override
                public void onClick(int index) {
                    onBtnForceStartStopClick(index);
                }
        });

        // Restore last state of button group.
        int prefBtnStateForceStartStop = mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP);
        btnForceStartStop.setPushedButtonIndex(prefBtnStateForceStartStop);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String pref) {
            if (pref != null && pref.equals(Constants.PREF_BTNSTATE_FORCE_START_STOP))
                btnForceStartStop.setPushedButtonIndex(mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP));
        }
    };

    private void updateStatus() {
        SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
        if (syncthingActivity == null || getView() == null || syncthingActivity.isFinishing()) {
            return;
        }
        SyncthingService syncthingService = syncthingActivity.getService();
        if (syncthingService == null) {
            return;
        }

        // Explanation why syncthing is (not) running goes to the collapsible reason row.
        String reasonTextValue = null;
        switch (mServiceState) {
            case ACTIVE:
            case DISABLED:
                reasonTextValue = "- " + syncthingService.getRunDecisionExplanation().trim().replace("\n", "\n- ");
                break;
            default:
                break;
        }
        updateReasonCard(reasonTextValue);

        // Populate the key/value card with the latest system status holders.
        boolean active = (mServiceState == SyncthingService.State.ACTIVE);
        synchronized (mStatusHolderLock) {
            setKv(mKvUptime, active ? mUptime : "");
            setKv(mKvRam, active ? mRamUsage : "");
            setKv(mKvDownload, active ? mDownload : "");
            setKv(mKvUpload, active ? mUpload : "");
            setKv(mKvAnnounce, active ? mAnnounceServer : "");
        }
    }

    /**
     * Sets a key/value field; shows a dash placeholder when the value is empty.
     */
    private void setKv(TextView view, String value) {
        if (view == null) {
            return;
        }
        view.setText(TextUtils.isEmpty(value) ? "—" : value);
    }

    /**
     * Invokes status callbacks via syncthing's REST API
     * while the user is looking at the current tab.
     */
    private void onTimerEvent() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            updateStatus();
            return;
        }
        RestApi restApi = mainActivity.getApi();
        if (restApi == null) {
            return;
        }
        // LogV("Invoking REST status queries");

        // Force a cache-miss to query status of all devices asynchronously.
        restApi.getRemoteDeviceStatus("");

        // onReceiveSystemStatus will call {@link #updateStatus}.
        restApi.getSystemStatus(this::onReceiveSystemStatus);
    }

    /**
     * Populates status holders with status received via {@link RestApi#getSystemStatus}.
     */
    private void onReceiveSystemStatus(SystemStatus systemStatus) {
        if (getActivity() == null) {
            return;
        }
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        int announceTotal = systemStatus.discoveryMethods;
        int announceConnected =
                announceTotal - Optional.fromNullable(systemStatus.discoveryErrors).transform(Map::size).or(0);
        synchronized (mStatusHolderLock) {
            mRamUsage = Util.readableFileSize(mActivity, systemStatus.sys);
            mAnnounceServer = (announceTotal == 0) ?
                    "" :
                    String.format(Locale.getDefault(), "%1$d/%2$d", announceConnected, announceTotal);

            /**
             * Calculate readable uptime.
             */
            long uptimeDays = TimeUnit.SECONDS.toDays(systemStatus.uptime);
            long uptimeHours = TimeUnit.SECONDS.toHours(systemStatus.uptime) - TimeUnit.DAYS.toHours(uptimeDays);
            long uptimeMinutes = TimeUnit.SECONDS.toMinutes(systemStatus.uptime) - TimeUnit.HOURS.toMinutes(uptimeHours) - TimeUnit.DAYS.toMinutes(uptimeDays);
            if (uptimeDays > 0) {
                mUptime = String.format(Locale.getDefault(), "%dd %02dh %02dm", uptimeDays, uptimeHours, uptimeMinutes);
            } else if (uptimeHours > 0) {
                mUptime = String.format(Locale.getDefault(), "%dh %02dm", uptimeHours, uptimeMinutes);
            } else {
                mUptime = String.format(Locale.getDefault(), "%dm", uptimeMinutes);
            }

            Connection total = new Connection();
            RestApi restApi = getRestApiSafe();
            if  (restApi != null) {
                if (restApi.isConfigLoaded()) {
                    total = restApi.getTotalConnectionStatistic();
                }
            }

            /**
             * "Hide" rates on the UI if they are lower than 1 KByte/sec. We don't like to
             * bother the user looking at discovery or index exchange traffic.
             */
            mDownload = (total.inBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.inBits);
            mDownload += " (" + Util.readableFileSize(mActivity, total.inBytesTotal) + ")";
            mUpload = (total.outBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.outBits);
            mUpload += " (" + Util.readableFileSize(mActivity, total.outBytesTotal) + ")";
        }
        updateStatus();
    }

    private void toggleReasonCard() {
        reasonExpanded = !reasonExpanded;
        reasonText.setVisibility(reasonExpanded ? View.VISIBLE : View.GONE);
        reasonArrow.setRotation(reasonExpanded ? 270 : 90);
    }

    private void updateReasonCard(String explanation) {
        if (reasonHeader == null || reasonText == null) {
            return;
        }
        boolean hasReason = explanation != null && !explanation.trim().isEmpty();
        reasonHeader.setVisibility(hasReason ? View.VISIBLE : View.GONE);
        if (!hasReason) {
            reasonText.setVisibility(View.GONE);
            reasonExpanded = false;
            return;
        }
        // Hanging indent: when a bullet wraps, the next line starts after the "- ".
        SpannableString text = new SpannableString(explanation);
        int indent = (int) (12 * getResources().getDisplayMetrics().density);
        text.setSpan(new LeadingMarginSpan.Standard(0, indent),
                0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        reasonText.setText(text);
        // Periodic status refreshes must not collapse a card the user opened.
        reasonText.setVisibility(reasonExpanded ? View.VISIBLE : View.GONE);
        reasonArrow.setRotation(reasonExpanded ? 270 : 90);
    }

    private void onBtnForceStartStopClick(int index) {
        LogV("onBtnForceStartStopClick");

        // Note: "index" is equivalent to the defined integer "Constants.PREF_BTNSTATE_*" values.
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, index);
        editor.apply();

        // Notify {@link RunConditionMonitor} that the button's state changed.
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mActivity);
        Intent intent = new Intent(ACTION_UPDATE_SHOULDRUN_DECISION);
        localBroadcastManager.sendBroadcast(intent);
    }

    private RestApi getRestApiSafe() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return null;
        }
        if (mainActivity.isFinishing()) {
            return null;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            return null;
        }
        return mainActivity.getApi();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
