package com.vjosashtime.iptv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(9, 25, 47);
    private static final int BLUE = Color.rgb(28, 98, 181);
    private static final int BG = Color.rgb(245, 248, 252);
    private static final int TEXT = Color.rgb(24, 34, 48);
    private static final int MUTED = Color.rgb(102, 116, 136);
    private static final int BORDER = Color.rgb(220, 227, 236);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();

    private SharedPreferences prefs;
    private EditText playlistUrl;
    private EditText directUrl;
    private EditText search;
    private CheckBox favoritesOnly;
    private TextView status;
    private TextView count;
    private ProgressBar progress;
    private ListView listView;
    private ChannelAdapter adapter;
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.WHITE);

        prefs = getSharedPreferences("vjosa_shtime", MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));

        buildUi();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playlistUrl.setText(prefs.getString("last_playlist", ""));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackground(roundRect(NAVY, 20));

        TextView title = new TextView(this);
        title.setText("Vjosa Shtime");
        title.setTextSize(27);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, fullWrap());

        TextView sub = new TextView(this);
        sub.setText("Private IPTV & Video Player");
        sub.setTextSize(12);
        sub.setTextColor(Color.rgb(190, 210, 235));
        sub.setGravity(Gravity.CENTER);
        header.addView(sub, fullWrap());
        root.addView(header, fullWrap());

        playlistUrl = input("Linku i listës M3U / M3U8");
        LinearLayout.LayoutParams p1 = fullWrap();
        p1.topMargin = dp(10);
        root.addView(playlistUrl, p1);

        Button load = button("NGARKO LISTËN");
        load.setOnClickListener(v -> loadPlaylist());
        LinearLayout.LayoutParams p2 = fullWrap();
        p2.topMargin = dp(6);
        root.addView(load, p2);

        LinearLayout directRow = new LinearLayout(this);
        directRow.setOrientation(LinearLayout.HORIZONTAL);
        directRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p3 = fullWrap();
        p3.topMargin = dp(8);
        root.addView(directRow, p3);

        directUrl = input("Link direkt video / stream");
        directRow.addView(directUrl, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button play = button("LUAJ");
        play.setOnClickListener(v -> playUrl(directUrl.getText().toString().trim(), "Video"));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(84), dp(46));
        playParams.leftMargin = dp(7);
        directRow.addView(play, playParams);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(185));
        playerParams.topMargin = dp(8);
        root.addView(playerView, playerParams);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(4), dp(6), dp(4), dp(6));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        infoRow.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24)));
        status = new TextView(this);
        status.setText("Gati. Shto listën tënde IPTV.");
        status.setTextSize(12);
        status.setTextColor(MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = dp(6);
        infoRow.addView(status, sp);
        count = new TextView(this);
        count.setText("0 kanale");
        count.setTextColor(BLUE);
        count.setTextSize(12);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoRow.addView(count);
        root.addView(infoRow, fullWrap());

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        search = input("Kërko kanal ose kategori...");
        filterRow.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1f));
        favoritesOnly = new CheckBox(this);
        favoritesOnly.setText("★ Favoritet");
        favoritesOnly.setTextSize(12);
        favoritesOnly.setTextColor(TEXT);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        fp.leftMargin = dp(4);
        filterRow.addView(favoritesOnly, fp);
        root.addView(filterRow, fullWrap());

        listView = new ListView(this);
        listView.setDividerHeight(dp(5));
        adapter = new ChannelAdapter(this, visibleChannels);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Channel c = visibleChannels.get(position);
            playUrl(c.url, c.name);
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleFavorite(visibleChannels.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Preke për ta luajtur • Mbaje shtypur për Favorite");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, fullWrap());

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });
        favoritesOnly.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilter());

        setContentView(root);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundRect(Color.WHITE, 12, BORDER));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(BLUE, 12));
        return b;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = roundRect(color, radiusDp);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void loadPlaylist() {
        String url = playlistUrl.getText().toString().trim();
        if (!isHttpUrl(url)) {
            toast("Vendos një link http:// ose https://.");
            return;
        }
        prefs.edit().putString("last_playlist", url).apply();
        progress.setVisibility(View.VISIBLE);
        status.setText("Po ngarkohet lista...");

        executor.execute(() -> {
            try {
                String text = download(url);
                List<Channel> channels = parseM3u(text);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    allChannels.clear();
                    allChannels.addAll(channels);
                    applyFilter();
                    status.setText(channels.isEmpty() ? "Nuk u gjetën kanale në listë." : "Lista u ngarkua me sukses.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("Gabim gjatë ngarkimit.");
                    toast("Nuk u ngarkua lista: " + (e.getMessage() == null ? "gabim" : e.getMessage()));
                });
            }
        });
    }

    private String download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Vjosa-Shtime/1.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > 8_000_000) throw new IllegalStateException("Lista është shumë e madhe");
            }
        } finally {
            c.disconnect();
        }
        return out.toString();
    }

    private List<Channel> parseM3u(String text) {
        List<Channel> result = new ArrayList<>();
        String currentName = null;
        String currentGroup = "";
        Pattern groupPattern = Pattern.compile("group-title=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
        String[] lines = text.replace("\r", "").split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                currentName = comma >= 0 ? line.substring(comma + 1).trim() : "Kanal";
                Matcher m = groupPattern.matcher(line);
                currentGroup = m.find() ? m.group(1).trim() : "";
            } else if (!line.startsWith("#") && isHttpUrl(line)) {
                String name = currentName == null || currentName.isEmpty() ? "Kanal " + (result.size() + 1) : currentName;
                result.add(new Channel(name, currentGroup, line));
                currentName = null;
                currentGroup = "";
            }
        }
        return result;
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean favOnly = favoritesOnly != null && favoritesOnly.isChecked();
        visibleChannels.clear();
        for (Channel c : allChannels) {
            boolean textOk = q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q) || c.group.toLowerCase(Locale.ROOT).contains(q);
            boolean favOk = !favOnly || favorites.contains(c.url);
            if (textOk && favOk) visibleChannels.add(c);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (count != null) count.setText(visibleChannels.size() + " kanale");
    }

    private void toggleFavorite(Channel c) {
        if (favorites.contains(c.url)) {
            favorites.remove(c.url);
            toast("U hoq nga Favoritet");
        } else {
            favorites.add(c.url);
            toast("U shtua në Favoritet");
        }
        prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
        applyFilter();
    }

    private void playUrl(String url, String name) {
        if (!isHttpUrl(url)) {
            toast("Linku nuk është i vlefshëm.");
            return;
        }
        status.setText("Po luhet: " + name);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    private boolean isHttpUrl(String value) {
        if (value == null) return false;
        String s = value.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class ChannelAdapter extends ArrayAdapter<Channel> {
        ChannelAdapter(Context context, List<Channel> items) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            TextView t1 = row.findViewById(android.R.id.text1);
            TextView t2 = row.findViewById(android.R.id.text2);
            Channel c = getItem(position);
            if (c != null) {
                t1.setText((favorites.contains(c.url) ? "★ " : "") + c.name);
                t1.setTextColor(TEXT);
                t1.setTextSize(14);
                t2.setText(c.group.isEmpty() ? "IPTV" : c.group);
                t2.setTextColor(MUTED);
                t2.setTextSize(11);
            }
            row.setBackgroundColor(Color.WHITE);
            return row;
        }
    }

    private static final class Channel {
        final String name;
        final String group;
        final String url;

        Channel(String name, String group, String url) {
            this.name = name == null ? "" : name;
            this.group = group == null ? "" : group;
            this.url = url == null ? "" : url;
        }
    }
}
