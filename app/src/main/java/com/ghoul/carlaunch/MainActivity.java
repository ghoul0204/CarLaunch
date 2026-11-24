package com.ghoul.carlaunch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    private static final int REQ_BT = 1;
    private static final int REQ_BT_THEN_PICK = 2;

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF5F5F5);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = U.dp(this, 16);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private boolean hasBt() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasOverlay() {
        return Settings.canDrawOverlays(this);
    }

    private void askBt(int code) {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, code);
        }
    }

    private void render() {
        root.removeAllViews();
        root.addView(U.title(this, "連上藍牙 → 自動開 App"));

        // ① 權限
        root.addView(U.section(this, "① 必要權限"));
        root.addView(U.text(this, (hasBt() ? "✅ " : "❌ ") + "藍牙連線權限"));
        if (!hasBt()) {
            root.addView(U.button(this, "授權藍牙", v -> askBt(REQ_BT)));
        }
        root.addView(U.text(this, (hasOverlay() ? "✅ " : "❌ ") + "顯示在其他應用程式上層（在背景自動開 App 必須要有）"));
        if (!hasOverlay()) {
            root.addView(U.button(this, "去開啟", v -> startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        }
        boolean acc = ClearService.isEnabled(this);
        root.addView(U.text(this, (acc ? "✅ " : "⬜ ") + "無障礙服務（選用：斷線時自動按「全部清除」）"));
        if (!acc) {
            root.addView(U.small(this, "Android 13 以後：先按「開啟本 App 的系統設定」→ 右上角 ⋮ →「允許受限制的設定」，再回來按下面按鈕，找到「車藍牙自動開：清除最近使用」打開。"));
            root.addView(U.button(this, "開啟無障礙設定", v -> startActivity(new Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        }
        root.addView(U.small(this, "小米 / OPPO / vivo / 華為等手機，另外要在 App 資訊裡開「自啟動」、「背景彈出視窗」，電池設為「無限制」。"));
        root.addView(U.button(this, "開啟本 App 的系統設定", v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));

        // ② 間隔
        root.addView(U.section(this, "② 開啟間隔"));
        LinearLayout row = U.row(this);
        TextView gap = U.text(this, "每個 App 間隔 " + Store.gapSec(this) + " 秒");
        gap.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(gap);
        row.addView(U.smallButton(this, "－", v -> {
            Store.setGapSec(this, Store.gapSec(this) - 1);
            render();
        }));
        row.addView(U.smallButton(this, "＋", v -> {
            Store.setGapSec(this, Store.gapSec(this) + 1);
            render();
        }));
        root.addView(row);

        // ③ 裝置
        root.addView(U.section(this, "③ 藍牙裝置與要開的 App"));
        List<Store.Rule> rules = Store.all(this);
        if (rules.isEmpty()) {
            root.addView(U.small(this, "還沒設定。按下面「新增藍牙裝置」選你的車。"));
        }
        for (Store.Rule r : rules) root.addView(ruleCard(r));
        root.addView(U.button(this, "＋ 新增藍牙裝置", v -> pickDevice()));

        // 紀錄
        root.addView(U.section(this, "紀錄"));
        String log = Store.log(this);
        root.addView(U.small(this, log.isEmpty() ? "（尚無）" : log));
        if (!log.isEmpty()) {
            root.addView(U.smallButton(this, "清除紀錄", v -> {
                Store.clearLog(this);
                render();
            }));
        }
    }

    private View ruleCard(final Store.Rule r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = U.dp(this, 12);
        card.setPadding(p, p, p, p);
        card.setBackground(U.cardBg(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = U.dp(this, 10);
        card.setLayoutParams(lp);

        LinearLayout head = U.row(this);
        TextView name = U.bold(this, "🚗 " + r.name);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(name);
        Switch sw = new Switch(this);
        sw.setChecked(r.enabled);
        sw.setOnCheckedChangeListener((b, on) -> {
            r.enabled = on;
            Store.save(this, r);
        });
        head.addView(sw);
        card.addView(head);
        card.addView(U.small(this, r.address));

        if (r.apps.isEmpty()) {
            card.addView(U.text(this, "（還沒選 App）"));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.apps.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(i + 1).append(". ").append(U.label(this, r.apps.get(i)));
            }
            card.addView(U.text(this, sb.toString()));
        }

        CheckBox close = new CheckBox(this);
        close.setText("斷開藍牙時：暫停音樂並回到桌面");
        close.setTextSize(15);
        close.setChecked(r.closeOnDisconnect);
        close.setOnCheckedChangeListener((b, on) -> {
            r.closeOnDisconnect = on;
            Store.save(this, r);
        });
        card.addView(close);

        CheckBox clear = new CheckBox(this);
        clear.setText("斷開藍牙時：也自動按「全部清除」（需開無障礙服務）");
        clear.setTextSize(15);
        clear.setChecked(r.clearRecents);
        clear.setOnCheckedChangeListener((b, on) -> {
            r.clearRecents = on;
            Store.save(this, r);
            if (on && !ClearService.isEnabled(this)) {
                toast("請到上方「① 必要權限」開啟無障礙服務");
            }
        });
        card.addView(clear);

        LinearLayout btns = U.row(this);
        btns.addView(U.smallButton(this, "選擇 App", v -> openPicker(r.address)));
        btns.addView(U.smallButton(this, "測試", v -> {
            if (r.apps.isEmpty()) {
                toast("請先選 App");
                return;
            }
            toast("開始依序開啟…");
            AppLauncher.run(this, r.apps, null);
        }));
        btns.addView(U.smallButton(this, "測試斷開", v -> {
            toast(r.clearRecents ? "3 秒後暫停音樂、回到桌面並全部清除" : "3 秒後暫停音樂並回到桌面");
            Handler hd = new Handler(Looper.getMainLooper());
            hd.postDelayed(() -> AppLauncher.closeAll(this), 3000);
            if (r.clearRecents) {
                hd.postDelayed(() -> ClearService.request(this), 4000);
            }
        }));
        card.addView(btns);

        LinearLayout btns2 = U.row(this);
        btns2.addView(U.smallButton(this, "刪除", v -> new AlertDialog.Builder(this)
                .setMessage("刪除「" + r.name + "」的設定？")
                .setPositiveButton("刪除", (d, w) -> {
                    Store.delete(this, r.address);
                    render();
                })
                .setNegativeButton("取消", null)
                .show()));
        card.addView(btns2);
        return card;
    }

    private void openPicker(String address) {
        Intent i = new Intent(this, PickAppsActivity.class);
        i.putExtra("addr", address);
        startActivity(i);
    }

    private void pickDevice() {
        if (!hasBt()) {
            askBt(REQ_BT_THEN_PICK);
            return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null) {
            toast("這支手機沒有藍牙");
            return;
        }
        Set<BluetoothDevice> set;
        try {
            set = ad.getBondedDevices();
        } catch (SecurityException e) {
            toast("缺少藍牙權限");
            return;
        }
        if (set == null || set.isEmpty()) {
            toast("沒有已配對的裝置。請先打開藍牙，並在系統設定跟車子配對過一次。");
            return;
        }
        final List<BluetoothDevice> list = new ArrayList<>(set);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            BluetoothDevice d = list.get(i);
            boolean added = Store.get(this, d.getAddress()) != null;
            names[i] = (added ? "✔ " : "") + U.devName(d);
        }
        new AlertDialog.Builder(this)
                .setTitle("選擇你的車（已配對的藍牙裝置）")
                .setItems(names, (dlg, w) -> {
                    BluetoothDevice dev = list.get(w);
                    Store.Rule existing = Store.get(this, dev.getAddress());
                    if (existing == null) {
                        Store.Rule nr = new Store.Rule();
                        nr.address = dev.getAddress();
                        nr.name = U.devName(dev);
                        Store.save(this, nr);
                    }
                    openPicker(dev.getAddress());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        render();
        if (code == REQ_BT_THEN_PICK && hasBt()) pickDevice();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
