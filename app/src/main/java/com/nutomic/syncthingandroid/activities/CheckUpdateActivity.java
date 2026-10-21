package com.nutomic.syncthingandroid.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.util.AppUpdater;

import java.io.File;

/**
 * Smartisan-style full-page update check: current → latest version comparison,
 * a changelog list, a solid accent "download and install" button and a muted
 * "later" action. Every other state (checking / up-to-date / downloading /
 * failed) is rendered as an inline row on the page instead of a system dialog.
 */
public class CheckUpdateActivity extends SyncthingActivity {

    private TextView mTvStatus;
    private View mUpdateContent;
    private TextView mTvCurrentVersion;
    private TextView mTvLatestVersion;
    private TextView mTvMeta;
    private View mChangelogCard;
    private LinearLayout mChangelogContainer;
    private TextView mBtnDownload;
    private TextView mBtnLater;

    private AppUpdater.UpdateInfo mUpdateInfo;
    private File mPendingApk;
    private boolean mDownloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_update);

        mTvStatus = findViewById(R.id.tvCheckStatus);
        mUpdateContent = findViewById(R.id.updateContent);
        mTvCurrentVersion = findViewById(R.id.tvCurrentVersion);
        mTvLatestVersion = findViewById(R.id.tvLatestVersion);
        mTvMeta = findViewById(R.id.tvUpdateMeta);
        mChangelogCard = findViewById(R.id.changelogCard);
        mChangelogContainer = findViewById(R.id.changelogContainer);
        mBtnDownload = findViewById(R.id.btnDownload);
        mBtnLater = findViewById(R.id.btnLater);

        mBtnDownload.setOnClickListener(v -> startDownload());
        mBtnLater.setOnClickListener(v -> finish());

        checkForUpdate();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // The centered header title is drawn by our own TextView inside the toolbar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just granted "install unknown apps" and come back.
        if (mPendingApk != null && AppUpdater.canInstall(this)) {
            File apk = mPendingApk;
            mPendingApk = null;
            mTvStatus.setVisibility(View.GONE);
            AppUpdater.installApk(this, apk);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForUpdate() {
        mUpdateInfo = null;
        mUpdateContent.setVisibility(View.GONE);
        showStatus(getString(R.string.update_checking), false, null);

        AppUpdater.fetchUpdateInfo(info -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (info == null) {
                showStatus(getString(R.string.check_update_retry), true, v -> checkForUpdate());
                return;
            }
            if (!AppUpdater.isNewer(info, this)) {
                showStatus(getString(R.string.update_up_to_date), false, null);
                return;
            }
            showUpdateAvailable(info);
        }));
    }

    private void showStatus(String text, boolean clickable, View.OnClickListener listener) {
        mTvStatus.setVisibility(View.VISIBLE);
        mTvStatus.setText(text);
        mTvStatus.setClickable(clickable);
        mTvStatus.setOnClickListener(listener);
    }

    private void showUpdateAvailable(AppUpdater.UpdateInfo info) {
        mUpdateInfo = info;
        mDownloading = false;
        mTvStatus.setVisibility(View.GONE);
        mUpdateContent.setVisibility(View.VISIBLE);

        mTvCurrentVersion.setText(withV(AppUpdater.installedVersionName(this)));
        mTvLatestVersion.setText(withV(info.getVersionName()));
        mTvMeta.setText(getString(R.string.check_update_meta, info.getVersionCode()));

        buildChangelog(info.getChangelog());

        mBtnDownload.setEnabled(true);
        mBtnDownload.setAlpha(1f);
        mBtnDownload.setText(R.string.update_download);
    }

    /** Prefixes a bare version name with "v" for display, without doubling it. */
    private String withV(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "";
        }
        String v = version.trim();
        return (v.startsWith("v") || v.startsWith("V")) ? v : "v" + v;
    }

    /**
     * Renders the changelog (one entry per line) as accent-dot bullet rows.
     */
    private void buildChangelog(String changelog) {
        mChangelogContainer.removeAllViews();
        if (changelog == null || changelog.trim().isEmpty()) {
            mChangelogCard.setVisibility(View.GONE);
            return;
        }
        mChangelogCard.setVisibility(View.VISIBLE);

        final float density = getResources().getDisplayMetrics().density;
        final int dotSize = Math.round(5 * density);
        final int dotTopMargin = Math.round(7 * density);
        final int dotEndMargin = Math.round(10 * density);
        final int rowVertical = Math.round(4 * density);

        for (String raw : changelog.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }
            if (line.isEmpty()) {
                continue;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, rowVertical, 0, rowVertical);

            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_dot_accent);
            LinearLayout.LayoutParams dotParams =
                    new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.topMargin = dotTopMargin;
            dotParams.setMarginEnd(dotEndMargin);
            row.addView(dot, dotParams);

            TextView text = new TextView(this);
            text.setText(line);
            text.setTextSize(13.5f);
            text.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.sm_ink));
            text.setLineSpacing(Math.round(2 * density), 1f);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(text, textParams);

            mChangelogContainer.addView(row);
        }
    }

    private void startDownload() {
        if (mUpdateInfo == null || mDownloading) {
            return;
        }
        mDownloading = true;
        mBtnDownload.setEnabled(false);
        mBtnDownload.setAlpha(0.55f);
        showStatus(getString(R.string.update_download_progress, 0), false, null);

        AppUpdater.downloadApk(this, mUpdateInfo,
                percent -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    mTvStatus.setText(getString(R.string.update_download_progress, percent));
                }),
                file -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    mDownloading = false;
                    mBtnDownload.setEnabled(true);
                    mBtnDownload.setAlpha(1f);

                    if (file == null) {
                        showStatus(getString(R.string.update_download_failed),
                                true, v -> startDownload());
                        return;
                    }
                    if (!AppUpdater.canInstall(this)) {
                        mPendingApk = file;
                        showStatus(getString(R.string.update_install_permission_text),
                                true, v -> AppUpdater.openInstallPermissionSettings(this));
                        AppUpdater.openInstallPermissionSettings(this);
                        return;
                    }
                    mTvStatus.setVisibility(View.GONE);
                    AppUpdater.installApk(this, file);
                }));
    }
}
