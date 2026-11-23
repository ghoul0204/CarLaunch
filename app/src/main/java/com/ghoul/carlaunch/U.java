package com.ghoul.carlaunch;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 小工具：畫面元件 */
public class U {

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static TextView text(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(0xFF212121);
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return t;
    }

    public static TextView small(Context c, String s) {
        TextView t = text(c, s);
        t.setTextSize(13);
        t.setTextColor(0xFF757575);
        return t;
    }

    public static TextView bold(Context c, String s) {
        TextView t = text(c, s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = bold(c, s);
        t.setTextSize(22);
        t.setTextColor(0xFF1565C0);
        t.setPadding(0, dp(c, 4), 0, dp(c, 8));
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = bold(c, s);
        t.setTextSize(17);
        t.setPadding(0, dp(c, 20), 0, dp(c, 6));
        return t;
    }

    public static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    public static Button smallButton(Context c, String s, View.OnClickListener l) {
        Button b = button(c, s, l);
        b.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static GradientDrawable cardBg(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(c, 12));
        g.setStroke(dp(c, 1), 0xFFBDBDBD);
        return g;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg + "（未安裝）";
        }
    }

    @SuppressLint("MissingPermission")
    public static String devName(BluetoothDevice d) {
        try {
            String n = null;
            if (Build.VERSION.SDK_INT >= 30) n = d.getAlias();
            if (n == null) n = d.getName();
            return n == null ? d.getAddress() : n;
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }
}
