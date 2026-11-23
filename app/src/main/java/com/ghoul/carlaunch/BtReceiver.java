package com.ghoul.carlaunch;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** 系統通知「某個藍牙裝置連上了」→ 如果是設定過的裝置就開 App */
@SuppressLint("MissingPermission")
public class BtReceiver extends BroadcastReceiver {

    /** 同一台裝置 2 分鐘內重複連線不再重開（車子啟動時藍牙常會斷了又連） */
    private static final long DEBOUNCE_MS = 2 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;

        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null) return;

        String addr;
        try {
            addr = d.getAddress();
        } catch (SecurityException e) {
            return;
        }

        Store.Rule r = Store.get(context, addr);
        if (r == null) return;              // 不是設定過的裝置
        if (!r.enabled) {
            Store.log(context, "連上 " + r.name + "（已停用，不動作）");
            return;
        }
        if (r.apps.isEmpty()) {
            Store.log(context, "連上 " + r.name + "（還沒選 App）");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - Store.lastRun(context, addr) < DEBOUNCE_MS) {
            Store.log(context, "連上 " + r.name + "（2 分鐘內重複連線，略過）");
            return;
        }
        Store.setLastRun(context, addr, now);

        String msg = "連上 " + r.name + " → 開啟 " + r.apps.size() + " 個 App";
        if (!Settings.canDrawOverlays(context)) {
            msg += "（⚠ 沒開「顯示在其他應用程式上層」，可能被系統擋）";
        }
        Store.log(context, msg);

        final PendingResult pr = goAsync();
        AppLauncher.run(context, r.apps, pr::finish);
    }
}
