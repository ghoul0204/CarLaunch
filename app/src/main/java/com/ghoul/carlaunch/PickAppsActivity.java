package com.ghoul.carlaunch;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 勾選要開的 App；勾選順序 = 開啟順序 */
public class PickAppsActivity extends Activity {

    private Store.Rule rule;
    private final List<String> order = new ArrayList<>();
    private final Map<String, CheckBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private TextView summary;

    private static class AppItem {
        String pkg;
        String label;
        ResolveInfo ri;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rule = Store.get(this, getIntent().getStringExtra("addr"));
        if (rule == null) {
            finish();
            return;
        }
        setTitle(rule.name);

        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        Map<String, AppItem> uniq = new LinkedHashMap<>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || uniq.containsKey(pkg)) continue;
            AppItem it = new AppItem();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            it.ri = ri;
            uniq.put(pkg, it);
        }

        order.addAll(rule.apps);
        order.retainAll(uniq.keySet()); // 已解除安裝的拿掉

        // 已選的排最上面（照順序），其他照名稱排
        List<AppItem> selected = new ArrayList<>();
        for (String p : order) selected.add(uniq.get(p));
        List<AppItem> others = new ArrayList<>();
        for (AppItem it : uniq.values()) if (!order.contains(it.pkg)) others.add(it);
        final Collator col = Collator.getInstance(Locale.TAIWAN);
        Collections.sort(others, (x, y) -> col.compare(x.label, y.label));
        List<AppItem> items = new ArrayList<>(selected);
        items.addAll(others);

        // ---- 畫面 ----
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(0xFFF5F5F5);
        int p = U.dp(this, 16);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(p, p, p, U.dp(this, 8));
        top.addView(U.bold(this, "連上「" + rule.name + "」時要開的 App"));
        top.addView(U.small(this, "照勾選的順序開啟，最後一個會停在畫面最前面（例如導航放最後）。"));
        summary = U.text(this, "");
        top.addView(summary);
        top.addView(U.button(this, "💾 儲存", v -> save()));
        outer.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(p, 0, p, p);
        sv.addView(list);
        outer.addView(sv);

        int icon = U.dp(this, 36);
        for (AppItem it : items) {
            CheckBox cb = new CheckBox(this);
            cb.setTextSize(16);
            cb.setPadding(U.dp(this, 8), U.dp(this, 6), 0, U.dp(this, 6));
            try {
                Drawable d = it.ri.loadIcon(pm);
                d.setBounds(0, 0, icon, icon);
                cb.setCompoundDrawablesRelative(d, null, null, null);
                cb.setCompoundDrawablePadding(U.dp(this, 10));
            } catch (Exception ignored) {
            }
            cb.setChecked(order.contains(it.pkg));
            final String pkg = it.pkg;
            cb.setOnCheckedChangeListener((btn, on) -> {
                if (on) {
                    if (!order.contains(pkg)) order.add(pkg);
                } else {
                    order.remove(pkg);
                }
                refresh();
            });
            boxes.put(it.pkg, cb);
            labels.put(it.pkg, it.label);
            list.addView(cb);
        }

        setContentView(outer);
        refresh();
    }

    private void refresh() {
        for (Map.Entry<String, CheckBox> e : boxes.entrySet()) {
            int idx = order.indexOf(e.getKey());
            String l = labels.get(e.getKey());
            e.getValue().setText(idx >= 0 ? "【" + (idx + 1) + "】" + l : l);
        }
        if (order.isEmpty()) {
            summary.setText("目前：還沒選");
        } else {
            StringBuilder sb = new StringBuilder("目前：");
            for (int i = 0; i < order.size(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(labels.get(order.get(i)));
            }
            summary.setText(sb.toString());
        }
    }

    private void save() {
        rule.apps = new ArrayList<>(order);
        Store.save(this, rule);
        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
