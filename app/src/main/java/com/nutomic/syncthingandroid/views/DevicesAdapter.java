package com.nutomic.syncthingandroid.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.Util;

import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<Device> {

    private static final String TAG = "DevicesAdapter";

    /**
     * If (incoming_bits_per_second + outgoing_bits_per_second) top the threshold,
     * we'll assume syncing state for the device reporting that data throughput.
     */
    private static final long ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD = 50 * 1024 * 8;

    // Smartisan neutral text colors (state is conveyed by wording, not color alone).
    // Resolved once per adapter instance so the day/night resource pair applies.
    private final int mColorWeak;
    private final int mColorFaint;
    private final int mColorMedium;

    private final Context mContext;

    private ConfigRouter mConfigRouter;
    private RestApi mRestApi;

    public DevicesAdapter(Context context) {
        super(context, 0);
        mContext = context;
        mColorWeak = androidx.core.content.ContextCompat.getColor(context, R.color.sm_ink_weak);
        mColorFaint = androidx.core.content.ContextCompat.getColor(context, R.color.sm_chevron);
        mColorMedium = androidx.core.content.ContextCompat.getColor(context, R.color.sm_value_gray);
    }

    public void setRestApi(ConfigRouter configRouter, RestApi restApi) {
        mConfigRouter = configRouter;
        mRestApi = restApi;
    }

    static class ViewHolder {
        TextView name;
        TextView lastSeen;
        TextView sharedFoldersTitle;
        TextView sharedFolders;
        TextView status;
        TextView bandwidthUpDown;
        View rateInOutView;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_device_list, parent, false);

            holder = new ViewHolder();
            holder.name = convertView.findViewById(R.id.name);
            holder.lastSeen = convertView.findViewById(R.id.lastSeen);
            holder.sharedFoldersTitle = convertView.findViewById(R.id.sharedFoldersTitle);
            holder.sharedFolders = convertView.findViewById(R.id.sharedFolders);
            holder.status = convertView.findViewById(R.id.status);
            holder.bandwidthUpDown = convertView.findViewById(R.id.bandwidthUpDown);
            holder.rateInOutView = convertView.findViewById(R.id.rateInOutContainer);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Device device = getItem(position);
        holder.name.setText(device.getDisplayName());

        updateDeviceStatusView(holder, device);

        return convertView;
    }

    @SuppressLint("SetTextI18n")
    private void updateDeviceStatusView(ViewHolder holder, Device device) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String deviceLastSeen = sharedPreferences.getString(
            Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + device.deviceID, ""
        );
        final String TIMESTAMP_NEVER_SEEN = "1970-01-01T00:00:00Z";
        holder.lastSeen.setText(mContext.getString(R.string.device_last_seen,
                TextUtils.isEmpty(deviceLastSeen) || deviceLastSeen.equals(TIMESTAMP_NEVER_SEEN) ?
                        mContext.getString(R.string.device_last_seen_never) : Util.formatDateTime(deviceLastSeen))
        );

        // Smartisan: one line, "共享文件夹：A、B" (or the unused hint).
        List<Folder> sharedFolders = mConfigRouter.getSharedFolders(device.deviceID);
        holder.sharedFoldersTitle.setVisibility(GONE);
        if (sharedFolders.size() == 0) {
            holder.sharedFolders.setText(R.string.device_state_unused);
        } else {
            holder.sharedFolders.setText(
                    mContext.getString(R.string.shared_folders_title_colon)
                            + TextUtils.join("、", sharedFolders));
        }

        if (device.paused) {
            holder.rateInOutView.setVisibility(GONE);
            holder.status.setVisibility(VISIBLE);
            holder.status.setText(R.string.device_paused);
            holder.status.setTextColor(mColorWeak);
            return;
        }

        if  (mRestApi == null || !mRestApi.isConfigLoaded()) {
            // Syncthing is not running.
            holder.rateInOutView.setVisibility(GONE);
            holder.status.setVisibility(VISIBLE);
            holder.status.setText(R.string.device_disconnected);
            holder.status.setTextColor(mColorFaint);
            return;
        }

        final Connection conn = mRestApi.getRemoteDeviceStatus(device.deviceID);
        final int completion = mRestApi.getRemoteDeviceCompletion(device.deviceID);
        final double needBytes = mRestApi.getRemoteDeviceNeedBytes(device.deviceID);

        if (conn.connected) {
            holder.status.setVisibility(VISIBLE);

            String bandwidthUpDownText = mContext.getString(R.string.download_title);
            bandwidthUpDownText += " ";
            bandwidthUpDownText += Util.readableTransferRate(getContext(), conn.inBits);
            bandwidthUpDownText += " \u00b7 ";               // middle dot
            bandwidthUpDownText += mContext.getString(R.string.upload_title);
            bandwidthUpDownText += " ";
            bandwidthUpDownText += Util.readableTransferRate(getContext(), conn.outBits);
            holder.bandwidthUpDown.setText(bandwidthUpDownText);
            holder.rateInOutView.setVisibility(VISIBLE);

            Boolean syncingState = !(completion == 100);
            if (!syncingState) {
                /**
                 * UI polish - We distinguish the following cases:
                 * a) conn.completion == 100 because of the model init assignment, data transmission ongoing.
                 * b) conn.completion == 100 because of a finished sync, no data transmission.
                 */
                if ((conn.inBits + conn.outBits) >= ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD) {
                    // case a) device_syncing
                    holder.status.setText(R.string.state_syncing_general);
                    holder.status.setTextColor(mColorMedium);
                } else {
                    // case b) device_up_to_date
                    holder.status.setText(R.string.device_up_to_date);
                    holder.status.setTextColor(mColorWeak);
                }
            } else {
                holder.status.setText(
                        mContext.getString(R.string.device_syncing_percent_bytes,
                                completion,
                                Util.readableFileSize(getContext(), needBytes)
                        )
                );
                holder.status.setTextColor(mColorMedium);
            }
            return;
        }

        // !conn.connected
        holder.rateInOutView.setVisibility(GONE);
        holder.status.setVisibility(VISIBLE);
        if (needBytes == 0) {
            holder.status.setText(R.string.device_disconnected);
        } else {
            holder.status.setText(
                    mContext.getString(R.string.device_disconnected_not_synced,
                            Util.readableFileSize(getContext(), needBytes)
                    )
            );
        }
        holder.status.setTextColor(mColorFaint);
    }
}
