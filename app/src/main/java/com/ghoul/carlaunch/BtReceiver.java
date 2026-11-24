package com.ghoul.carlaunch;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** 系統通知藍牙連上 / 斷開 → 如果是設定過的裝置就開 App / 收尾 */
@SuppressLint("MissingPermission")
public class BtReceiver extends BroadcastReceiver {

    /** 同一台裝置 2 分鐘內重複連線不再重開（車子啟動時藍牙常會斷了又連） */
    private static final long DEBOUNCE_MS = 2 * 60 * 1000L;
    /** 斷線後等幾秒確認真的斷了才動作 */
    private static final long DISCONNECT_WAIT_MS = 15 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null || action == null) return;

        String addr;
        try {
            addr = d.getAddress();
        } catch (SecurityException e) {
            return;
        }

        Store.Rule r = Store.get(context, addr);
        if (r == null) return;              // 不是設定過的裝置

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            onConnected(context, r);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            onDisconnected(context, r);
        }
    }

    private void onConnected(Context context, Store.Rule r) {
        long now = System.currentTimeMillis();
        Store.setLastConnect(context, r.address, now);

        if (!r.enabled) {
            Store.log(context, "連上 " + r.name + "（已停用，不動作）");
            return;
        }
        if (r.apps.isEmpty()) {
            Store.log(context, "連上 " + r.name + "（還沒選 App）");
            return;
        }
        if (now - Store.lastRun(context, r.address) < DEBOUNCE_MS) {
            Store.log(context, "連上 " + r.name + "（剛剛才開過，略過）");
            return;
        }
        Store.setLastRun(context, r.address, now);

        String msg = "連上 " + r.name + " → 開啟 " + r.apps.size() + " 個 App";
        if (!Settings.canDrawOverlays(context)) {
            msg += "（⚠ 沒開「顯示在其他應用程式上層」，可能被系統擋）";
        }
        Store.log(context, msg);

        final PendingResult pr = goAsync();
        AppLauncher.run(context, r.apps, pr::finish);
    }

    private void onDisconnected(Context context, Store.Rule r) {
        if (!r.enabled || !r.closeOnDisconnect) return;

        final long disconnectedAt = System.currentTimeMillis();
        final Context app = context.getApplicationContext();
        final String addr = r.address;
        final String name = r.name;
        final boolean clear = r.clearRecents;
        Store.log(app, "斷開 " + name + "，15 秒後確認");

        final PendingResult pr = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (Store.lastConnect(app, addr) > disconnectedAt) {
                    Store.log(app, name + " 又連回來了，不關閉");
                } else {
                    AppLauncher.closeAll(app);
                    // 真的下車了：下次連上就重新開 App
                    Store.setLastRun(app, addr, 0);
                    Store.log(app, "已斷開 " + name + " → 暫停音樂、回到桌面");
                    if (clear) ClearService.request(app);
                }
            } finally {
                pr.finish();
            }
        }, DISCONNECT_WAIT_MS);
    }
}
