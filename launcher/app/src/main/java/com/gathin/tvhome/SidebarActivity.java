package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translucent quick app sidebar (Ubuntu TV / Unity style overlay) matching the tvOS aesthetic:
 * opened by the remote's Menu button from any app or Home. Runs in its own task over whatever
 * app is playing, allowing the user to seamlessly switch apps or return with Back/Menu.
 */
public final class SidebarActivity extends Activity {

    static final class SidebarApp {
        final String packageName;
        final String className;
        final String label;
        final Drawable icon;

        SidebarApp(String packageName, String className, String label, Drawable icon) {
            this.packageName = packageName;
            this.className = className;
            this.label = label;
            this.icon = icon;
        }
    }

    private RecyclerView recyclerView;
    private SidebarAdapter adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(R.anim.sidebar_slide_in, 0);
        setContentView(R.layout.activity_sidebar);

        findViewById(R.id.sidebar_scrim).setOnClickListener(v -> closeSidebar());

        View homeButton = findViewById(R.id.sidebar_home_button);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent home = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setPackage(getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(home);
                closeSidebar();
            });
            homeButton.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.04f : 1.0f).scaleY(hasFocus ? 1.04f : 1.0f).setDuration(120).start();
            });
        }

        recyclerView = findViewById(R.id.sidebar_apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadApps();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Config config = Config.load(this);
        List<ResolveInfo> leanback = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        if (leanback == null || leanback.isEmpty()) {
            leanback = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        }

        Set<String> hidden = new HashSet<>(config.hiddenApps);
        List<String> dock = config.dockApps;
        Order order = Order.load(Order.file(this));
        Map<String, Integer> ranks = Order.ranks(order, dock);

        List<SidebarApp> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (leanback != null) {
            for (ResolveInfo ri : leanback) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || seen.contains(pkg)) continue;
                seen.add(pkg);
                CharSequence label = ri.loadLabel(pm);
                Drawable icon = ri.activityInfo.loadBanner(pm);
                if (icon == null) icon = ri.loadIcon(pm);
                apps.add(new SidebarApp(pkg, ri.activityInfo.name, label != null ? label.toString() : pkg, icon));
            }
        }

        // Sort: dock apps first, then saved order / alphabetical
        apps.sort((a, b) -> {
            int dockIdxA = dock.indexOf(a.packageName);
            int dockIdxB = dock.indexOf(b.packageName);
            if (dockIdxA != -1 && dockIdxB != -1) return Integer.compare(dockIdxA, dockIdxB);
            if (dockIdxA != -1) return -1;
            if (dockIdxB != -1) return 1;

            int rankComp = Order.compare(ranks, a.packageName + "/" + a.className, b.packageName + "/" + b.className);
            if (rankComp != 0) return rankComp;
            return a.label.compareToIgnoreCase(b.label);
        });

        adapter = new SidebarAdapter(apps);
        recyclerView.setAdapter(adapter);
        if (!apps.isEmpty()) {
            recyclerView.post(() -> {
                View first = recyclerView.getChildAt(0);
                if (first != null) first.requestFocus();
            });
        }
    }

    private void closeSidebar() {
        finish();
        overridePendingTransition(0, R.anim.sidebar_slide_out);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || keyCode == KeyEvent.KEYCODE_BACK) {
            closeSidebar();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeSidebar();
    }

    final class SidebarAdapter extends RecyclerView.Adapter<SidebarViewHolder> {
        private final List<SidebarApp> apps;

        SidebarAdapter(List<SidebarApp> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public SidebarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sidebar_app, parent, false);
            return new SidebarViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SidebarViewHolder holder, int position) {
            SidebarApp app = apps.get(position);
            holder.label.setText(app.label);
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon);
            }
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);
                    if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        startActivity(launch);
                    }
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(SidebarActivity.this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
                closeSidebar();
            });
            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.04f : 1.0f).scaleY(hasFocus ? 1.04f : 1.0f).setDuration(120).start();
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    static final class SidebarViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        SidebarViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.sidebar_item_icon);
            label = itemView.findViewById(R.id.sidebar_item_label);
        }
    }
}
