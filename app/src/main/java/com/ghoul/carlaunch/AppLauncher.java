package com.ghoul.carlaunch;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** 依序開啟 App：第一個等 1.5 秒，之後每個間隔 N 秒，最後一個會停在最前面 */
public class AppLauncher {

    public static void run(Context ctx, List<String> pkgs, Runnable done) {
        final Context app = ctx.getApplicationContext();
        final List<String> list = new ArrayList<>(pkgs);
        final Handler h = new Handler(Looper.getMainLooper());
        final long first = 1500;
        final long gap = Store.gapSec(app) * 1000L;

        for (int i = 0; i < list.size(); i++) {
            final String pkg = list.get(i);
            h.postDelayed(() -> {
                if (!launch(app, pkg)) {
                    Store.log(app, "⚠ 無法開啟 " + U.label(app, pkg));
                }
            }, first + i * gap);
        }
        if (done != null) {
            h.postDelayed(done, first + Math.max(0, list.size() - 1) * gap + 500);
        }
    }

    /** 斷線動作：暫停正在播放的音樂 / Podcast，然後回到桌面 */
    public static void closeAll(Context ctx) {
        Context c = ctx.getApplicationContext();
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
            }
        } catch (Exception ignored) {
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(home);
        } catch (Exception ignored) {
        }
    }

    public static boolean launch(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
