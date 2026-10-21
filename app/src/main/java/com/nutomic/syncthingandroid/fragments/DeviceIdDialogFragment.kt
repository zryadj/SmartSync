package com.nutomic.syncthingandroid.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.theme.ApplicationTheme

class DeviceIdDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val deviceName = args.getString(ARG_DEVICE_NAME)!!
        val deviceId = args.getString(ARG_DEVICE_ID)!!
        val isCurrentDevice = args.getBoolean(ARG_IS_CURRENT_DEVICE)

        val qrCode = generateQrCode(deviceId)

        return Dialog(requireContext()).apply {
            setContentView(
                ComposeView(context).apply {
                    setContent {
                        DeviceIdDialog(
                            onDismiss = { dismiss() },
                            deviceName,
                            deviceId,
                            qrCode,
                            onCopy = { copyDeviceId(deviceId) },
                            onShare = { shareDeviceId(deviceId) },
                            isCurrentDevice,
                        )
                    }
                }
            )
        }
    }

    private fun generateQrCode(deviceId: String): Bitmap {
        val qrSize = 232
        val black = 0xFF000000
        val white = 0x00000000

        val bitMatrix = MultiFormatWriter()
            .encode(deviceId, BarcodeFormat.QR_CODE, qrSize, qrSize)
        val bitMap = createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)

        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                val pixel = if (bitMatrix[x, y]) black else white
                bitMap[x, y] = pixel.toInt()
            }
        }

        return bitMap
    }

    private fun copyDeviceId(id: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(getString(R.string.device_id), id)
        clipboard.setPrimaryClip(clip)

        // Android 13+ shows a system confirmation automatically
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                requireContext(),
                R.string.device_id_copied_to_clipboard,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareDeviceId(deviceId: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, deviceId)
        }

        startActivity(
            Intent.createChooser(shareIntent, getString(R.string.share_device_id_chooser))
        )
    }


    companion object {
        private const val ARG_DEVICE_NAME = "device_name"
        private const val ARG_DEVICE_ID = "device_id"
        private const val ARG_IS_CURRENT_DEVICE = "is_current_device"

        fun show(
            fm: FragmentManager,
            deviceName: String,
            deviceId: String,
            isCurrentDevice: Boolean = false,
        ) {
            DeviceIdDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_NAME, deviceName)
                    putString(ARG_DEVICE_ID, deviceId)
                    putBoolean(ARG_IS_CURRENT_DEVICE, isCurrentDevice)
                }
            }.show(fm, "DeviceIdDialog")
        }
    }
}

/** Smartisan device-ID card: white r20, ink title, QR, mono ID, ghost actions. */
@Composable
fun DeviceIdDialog(
    onDismiss: () -> Unit,
    deviceName: String,
    deviceId: String,
    qrCode: Bitmap,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    isCurrentDevice: Boolean,
) {
    ApplicationTheme {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorResource(R.color.sm_card),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.device_id),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.sm_ink),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            deviceName,
                            fontSize = 13.sp,
                            color = colorResource(R.color.sm_value_gray),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isCurrentDevice) {
                            Text(
                                " · " + stringResource(R.string.this_device),
                                fontSize = 13.sp,
                                color = colorResource(R.color.sm_value_gray),
                                softWrap = false,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Image(
                            bitmap = qrCode.asImageBitmap(),
                            contentDescription = stringResource(R.string.device_id),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorResource(R.color.sm_card_inner),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            deviceId,
                            modifier = Modifier.padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = colorResource(R.color.sm_ink),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GhostButton(
                            onCopy,
                            Icons.Outlined.ContentCopy,
                            stringResource(R.string.copy),
                            Modifier.weight(1f),
                        )
                        GhostButton(
                            onShare,
                            Icons.Outlined.Share,
                            stringResource(R.string.share_title),
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.finish),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.sm_ink),
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable(onClick = onDismiss)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GhostButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorResource(R.color.sm_card_inner),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colorResource(R.color.sm_ink),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = colorResource(R.color.sm_ink))
        }
    }
}
