package com.ghoul.carlaunch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 無障礙服務：打開「最近使用」→ 找「全部清除」→ 按下去。
 * 螢幕鎖著的時候打不開最近使用，會等解鎖後再做（3 小時內有效）。
 */
public class ClearService extends AccessibilityService {

    static ClearService instance;

    private static final String[] LABELS = {
            "全部清除", "清除全部", "全部關閉", "關閉全部", "清除所有", "全部清理",
            "clear all", "close all", "dismiss all"
    };
    private static final int MAX_STEPS = 30;
    private static final long PENDING_VALID_MS = 3 * 60 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean working = false;
    private boolean pending = false;
    private long pendingAt = 0;
    private int steps = 0;
    private boolean forward = true;
    private int dirSwitches = 0;

    private final BroadcastReceiver unlockRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (pending && System.currentTimeMillis() - pendingAt < PENDING_VALID_MS) {
                Store.log(ClearService.this, "已解鎖 → 清除最近使用的 App");
                h.postDelayed(ClearService.this::startClear, 1200);
            }
            pending = false;
        }
    };

    /** 無障礙服務是否已在系統設定裡開啟 */
    public static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        String me = new ComponentName(c, ClearService.class).flattenToString();
        String meShort = new ComponentName(c, ClearService.class).flattenToShortString();
        for (String part : s.split(":")) {
            if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    /** 給外部呼叫：沒開服務就回 false */
    public static boolean request(Context c) {
        if (instance == null) {
            Store.log(c, "⚠ 無障礙服務沒開，無法自動清除");
            return false;
        }
        instance.requestClear();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockRx, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(unlockRx, f);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            unregisterReceiver(unlockRx);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要：用計時輪詢畫面
    }

    @Override
    public void onInterrupt() {
    }

    void requestClear() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            pending = true;
            pendingAt = System.currentTimeMillis();
            Store.log(this, "螢幕鎖著，解鎖後再清除最近使用的 App");
            return;
        }
        startClear();
    }

    void startClear() {
        if (working) return;
        working = true;
        steps = 0;
        forward = true;
        dirSwitches = 0;
        performGlobalAction(GLOBAL_ACTION_RECENTS);
        h.postDelayed(this::step, 1000);
    }

    private void step() {
        steps++;
        List<AccessibilityNodeInfo> roots = roots();

        AccessibilityNodeInfo btn = null;
        for (AccessibilityNodeInfo r : roots) {
            btn = findClear(r);
            if (btn != null) break;
        }
        if (btn != null && click(btn)) {
            Store.log(this, "✅ 已按下「全部清除」");
            working = false;
            return;
        }

        if (steps >= MAX_STEPS) {
            Store.log(this, "⚠ 找不到「全部清除」按鈕（可能沒有可清除的 App）");
            performGlobalAction(GLOBAL_ACTION_HOME);
            working = false;
            return;
        }

        // 沒看到按鈕 → 捲動最近使用的清單，往盡頭找
        AccessibilityNodeInfo sc = null;
        for (AccessibilityNodeInfo r : roots) {
            sc = findScrollable(r);
            if (sc != null) break;
        }
        if (sc != null) {
            int act = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            boolean ok = sc.performAction(act);
            if (!ok && dirSwitches < 2) {
                forward = !forward;
                dirSwitches++;
            }
        }
        h.postDelayed(this::step, 400);
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) out.add(r);
            }
        } catch (Exception ignored) {
        }
        AccessibilityNodeInfo a = getRootInActiveWindow();
        if (a != null) out.add(a);
        return out;
    }

    private static boolean matches(AccessibilityNodeInfo n) {
        String id = n.getViewIdResourceName();
        if (id != null) {
            String lid = id.toLowerCase(Locale.ROOT);
            if (lid.endsWith("/clear_all") || lid.endsWith("clear_all_button")
                    || lid.endsWith("/clearall") || lid.endsWith("/clear_all_btn")) {
                return true;
            }
        }
        String t = (n.getText() == null ? "" : n.getText().toString()) + " "
                + (n.getContentDescription() == null ? "" : n.getContentDescription().toString());
        t = t.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty() || t.length() > 20) return false;
        for (String l : LABELS) {
            if (t.contains(l)) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findClear(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 3000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (matches(n)) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 3000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isScrollable()) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo n) {
        // 1) 自己或上層可點的元件直接點
        AccessibilityNodeInfo p = n;
        for (int i = 0; i < 4 && p != null; i++) {
            if (p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            p = p.getParent();
        }
        // 2) 用手勢點按鈕中心
        if (!n.isVisibleToUser()) return false;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        if (b.isEmpty()) return false;
        Path path = new Path();
        path.moveTo(b.centerX(), b.centerY());
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        return dispatchGesture(g, null, null);
    }
}
