package com.nutomic.syncthingandroid.activities;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 *
 * Also centralizes Smartisan toolbar chrome: icons are inflated from the
 * activity theme, so the Toolbar overlay cannot reach them; we tint them
 * here with the theme's primary text color (auto light/dark).
 */
public abstract class ThemedAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Opt-in to edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Draw edge-to-edge into the display cutout as well: in landscape the
        // cutout sits on the left/right side, and the default mode pushed the
        // whole layout away from it, leaving a bare canvas strip next to the
        // toolbar. Some vendor ROMs (ColorOS etc.) still reserve the safe area
        // under SHORT_EDGES, so prefer the stronger ALWAYS mode on R+, where
        // the content always spans the full width; the hole then only
        // overlaps empty canvas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            getWindow().setAttributes(lp);
        }

        // Load theme.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Integer prefAppTheme = Integer.parseInt(sharedPreferences.getString(
                Constants.PREF_APP_THEME,
                Integer.toString(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        );
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Edge-to-edge is enabled above, so nothing pads the content for us.
        // Apply the system-bar insets by hand: the toolbar grows by the status
        // bar height (its background covers the status bar area, landscape
        // stays flush with the cutout side), and the window content keeps the
        // navigation bar clear at the bottom. Insets are returned untouched so
        // children (the drawer, Compose screens) can still handle their own.
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        View toolbar = findViewById(R.id.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            if (toolbar != null) {
                toolbar.setPadding(0, bars.top, 0, 0);
            }
            v.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            for (int i = 0; i < menu.size(); i++) {
                tintToolbarIcon(toolbar, menu.getItem(i).getIcon());
            }
            tintToolbarIcon(toolbar, toolbar.getNavigationIcon());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Tints a toolbar icon with the activity theme's primary text color so it
     * stays visible on the Smartisan light/dark toolbar chrome.
     */
    private void tintToolbarIcon(Toolbar toolbar, Drawable icon) {
        if (icon == null) {
            return;
        }
        int[] attr = new int[]{ android.R.attr.textColorPrimary };
        TypedArray ta = toolbar.getContext().obtainStyledAttributes(attr);
        int color = ta.getColor(0, androidx.core.content.ContextCompat.getColor(toolbar.getContext(), R.color.sm_ink));
        ta.recycle();
        DrawableCompat.setTint(DrawableCompat.wrap(icon), color);
        DrawableCompat.setTintMode(DrawableCompat.wrap(icon), PorterDuff.Mode.SRC_IN);
    }
}
