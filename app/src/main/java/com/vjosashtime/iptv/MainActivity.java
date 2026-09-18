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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int NAVY = Color.rgb(5, 16, 40);
    private static final int NAVY_2 = Color.rgb(9, 29, 67);
    private static final int BLUE = Color.rgb(18, 96, 225);
    private static final int BLUE_2 = Color.rgb(35, 132, 255);
    private static final int PURPLE = Color.rgb(96, 49, 230);
    private static final int PANEL = Color.rgb(13, 21, 40);
    private static final int PANEL_2 = Color.rgb(20, 31, 55);
    private static final int TEXT = Color.rgb(239, 245, 255);
    private static final int MUTED = Color.rgb(160, 178, 205);
    private static final int BORDER = Color.rgb(51, 72, 108);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();

    private SharedPreferences prefs;
    private ExoPlayer player;
    private PlayerView playerView;
    private EditText playlistUrl;
    private EditText directUrl;
    private EditText search;
    private TextView status;
    private TextView count;
    private ProgressBar progress;
    private ListView groupList;
    private ListView channelList;
    private ChannelAdapter channelAdapter;
    private GroupAdapter groupAdapter;
    private Button favoriteButton;

    private String selectedGroup = "Të gjitha";
    private boolean favoritesOnly = false;
    private String activeSection = "Live TV";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        prefs = getSharedPreferences("vjosa_shtime", MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));
        player = new ExoPlayer.Builder(this).build();

        showHome();
    }

    private void showHome() {
        if (player != null) player.pause();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(16));
        root.setBackground(gradient(NAVY, NAVY_2, 0));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("VJOSA SHTIME");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(26);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(logo, new LinearLayout.LayoutParams(0, dp(58), 1f));

        TextView profile = squareIcon("◉");
        header.addView(profile, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        LinearLayout topMenu = new LinearLayout(this);
        topMenu.setOrientation(LinearLayout.HORIZONTAL);
        topMenu.setGravity(Gravity.CENTER);
        topMenu.setPadding(0, dp(10), 0, dp(12));
        topMenu.addView(topButton("☷ Sports guide", v -> toast("Sports guide mund ta shtojmë si funksion të veçantë.")), weighted());
        topMenu.addView(topButton("⇄ Change Server", v -> showBrowser("Live TV")), weightedWithMargin());
        topMenu.addView(topButton("⚙ Cilësimet", v -> toast("Cilësimet do t’i shtojmë sipas dëshirës tënde.")), weightedWithMargin());
        root.addView(topMenu, fullWrap());

        TextView welcome = new TextView(this);
        welcome.setText("Zgjidh një seksion");
        welcome.setTextColor(MUTED);
        welcome.setTextSize(13);
        welcome.setPadding(dp(4), dp(4), 0, dp(10));
        root.addView(welcome);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(bigCard("▣", "Live TV", v -> showBrowser("Live TV")), cardParams(false));
        row1.addView(bigCard("▶", "Filmat", v -> showBrowser("Filmat")), cardParams(true));
        root.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        row2p.topMargin = dp(12);
        row2.addView(bigCard("◉", "Serialet", v -> showBrowser("Serialet")), cardParams(false));
        row2.addView(bigCard("↶", "Përsëritje", v -> showBrowser("Përsëritje")), cardParams(true));
        root.addView(row2, row2p);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, dp(12), 0, 0);

        Button video = bottomButton("▶ Video direkt");
        video.setOnClickListener(v -> showBrowser("Video"));
        bottom.addView(video, weighted());

        Button fav = bottomButton("★ Favoritet");
        fav.setOnClickListener(v -> {
            favoritesOnly = true;
            showBrowser("Favoritet");
        });
        bottom.addView(fav, weightedWithMargin());
        root.addView(bottom, fullWrap());

        setContentView(root);
    }

    private void showBrowser(String section) {
        activeSection = section;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(8));
        root.setBackgroundColor(NAVY);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        Button back = smallButton("‹");
        back.setTextSize(28);
        back.setOnClickListener(v -> showHome());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = new TextView(this);
        title.setText("Vjosa Shtime  •  " + section);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(0, dp(48), 1f);
        titleP.leftMargin = dp(8);
        titleRow.addView(title, titleP);

        favoriteButton = smallButton("☆");
        favoriteButton.setTextSize(22);
        favoriteButton.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            updateFavoriteButton();
            applyFilter();
        });
        titleRow.addView(favoriteButton, new LinearLayout.LayoutParams(dp(52), dp(48)));
        root.addView(titleRow);

        LinearLayout loadRow = new LinearLayout(this);
        loadRow.setOrientation(LinearLayout.HORIZONTAL);
        loadRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams loadRowP = fullWrap();
        loadRowP.topMargin = dp(8);
        root.addView(loadRow, loadRowP);

        playlistUrl = input("Linku i listës M3U / M3U8");
        playlistUrl.setText(prefs.getString("last_playlist", ""));
        loadRow.addView(playlistUrl, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button load = smallButton("NGARKO");
        load.setOnClickListener(v -> loadPlaylist());
        LinearLayout.LayoutParams loadP = new LinearLayout.LayoutParams(dp(96), dp(46));
        loadP.leftMargin = dp(7);
        loadRow.addView(load, loadP);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerView.setPlayer(player);
        LinearLayout.LayoutParams playerP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        playerP.topMargin = dp(8);
        root.addView(playerView, playerP);

        LinearLayout directRow = new LinearLayout(this);
        directRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams directP = fullWrap();
        directP.topMargin = dp(7);
        root.addView(directRow, directP);

        directUrl = input("Link direkt video / stream");
        directRow.addView(directUrl, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button play = smallButton("LUAJ");
        play.setOnClickListener(v -> playUrl(directUrl.getText().toString().trim(), "Video"));
        LinearLayout.LayoutParams playP = new LinearLayout.LayoutParams(dp(82), dp(44));
        playP.leftMargin = dp(7);
        directRow.addView(play, playP);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(2), dp(6), dp(2), dp(6));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));

        status = new TextView(this);
        status.setText(allChannels.isEmpty() ? "Gati. Ngarko listën IPTV." : "Lista është gati.");
        status.setTextColor(MUTED);
        status.setTextSize(11);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusP.leftMargin = dp(6);
        statusRow.addView(status, statusP);

        count = new TextView(this);
        count.setTextColor(BLUE_2);
        count.setTextSize(11);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusRow.addView(count);
        root.addView(statusRow, fullWrap());

        search = input("Kërko kanal ose kategori...");
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView labels = new TextView(this);
        labels.setText("GRUPET                                      KANALET");
        labels.setTextColor(MUTED);
        labels.setTextSize(10);
        labels.setPadding(dp(5), dp(7), 0, dp(5));
        root.addView(labels, fullWrap());

        LinearLayout listsRow = new LinearLayout(this);
        listsRow.setOrientation(LinearLayout.HORIZONTAL);

        groupList = new ListView(this);
        groupList.setDividerHeight(dp(3));
        groupList.setBackgroundColor(PANEL);
        groupAdapter = new GroupAdapter(this, groups);
        groupList.setAdapter(groupAdapter);
        groupList.setOnItemClickListener((parent, view, position, id) -> {
            selectedGroup = groups.get(position);
            groupAdapter.notifyDataSetChanged();
            applyFilter();
        });
        listsRow.addView(groupList, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f));

        channelList = new ListView(this);
        channelList.setDividerHeight(dp(3));
        channelList.setBackgroundColor(PANEL_2);
        channelAdapter = new ChannelAdapter(this, visibleChannels);
        channelList.setAdapter(channelAdapter);
        channelList.setOnItemClickListener((parent, view, position, id) -> {
            Channel c = visibleChannels.get(position);
            playUrl(c.url, c.name);
        });
        channelList.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleFavorite(visibleChannels.get(position));
            return true;
        });
        LinearLayout.LayoutParams channelP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        channelP.leftMargin = dp(5);
        listsRow.addView(channelList, channelP);

        root.addView(listsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Preke kanalin për ta luajtur • Mbaje shtypur për Favorite");
        hint.setTextColor(MUTED);
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(5), 0, 0);
        root.addView(hint, fullWrap());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        rebuildGroups();
        autoSelectGroupForSection(section);
        updateFavoriteButton();
        applyFilter();
        setContentView(root);
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
                    rebuildGroups();
                    autoSelectGroupForSection(activeSection);
                    applyFilter();
                    status.setText(channels.isEmpty()
                            ? "Nuk u gjetën kanale në listë."
                            : "Lista u ngarkua. Zgjidh grupin majtas.");
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

    private void rebuildGroups() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add("Të gjitha");
        for (Channel c : allChannels) {
            String g = c.group == null || c.group.trim().isEmpty() ? "Pa kategori" : c.group.trim();
            unique.add(g);
        }
        groups.clear();
        groups.addAll(unique);

        if (!groups.contains(selectedGroup)) selectedGroup = "Të gjitha";
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
    }

    private void autoSelectGroupForSection(String section) {
        if (groups.isEmpty()) return;
        String[] keys = null;
        if ("Filmat".equals(section)) keys = new String[]{"film", "movie", "vod", "cinema"};
        else if ("Serialet".equals(section)) keys = new String[]{"serial", "series", "show"};
        else if ("Përsëritje".equals(section)) keys = new String[]{"replay", "catch", "24/7", "perserit", "përsërit"};
        else if ("Favoritet".equals(section)) favoritesOnly = true;

        if (keys != null) {
            for (String group : groups) {
                String lower = group.toLowerCase(Locale.ROOT);
                for (String key : keys) {
                    if (lower.contains(key)) {
                        selectedGroup = group;
                        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
                        return;
                    }
                }
            }
        }
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleChannels.clear();

        for (Channel c : allChannels) {
            String group = c.group == null || c.group.trim().isEmpty() ? "Pa kategori" : c.group.trim();
            boolean groupOk = "Të gjitha".equals(selectedGroup) || group.equals(selectedGroup);
            boolean textOk = q.isEmpty()
                    || c.name.toLowerCase(Locale.ROOT).contains(q)
                    || group.toLowerCase(Locale.ROOT).contains(q);
            boolean favOk = !favoritesOnly || favorites.contains(c.url);

            if (groupOk && textOk && favOk) visibleChannels.add(c);
        }

        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
        if (count != null) count.setText(visibleChannels.size() + " kanale");
    }

    private String download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Vjosa-Shtime/2.0");

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > 12_000_000) throw new IllegalStateException("Lista është shumë e madhe");
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
        Pattern tvgNamePattern = Pattern.compile("tvg-name=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);

        String[] lines = text.replace("\r", "").split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                String title = comma >= 0 ? line.substring(comma + 1).trim() : "";

                Matcher gm = groupPattern.matcher(line);
                currentGroup = gm.find() ? gm.group(1).trim() : "";

                Matcher nm = tvgNamePattern.matcher(line);
                String tvgName = nm.find() ? nm.group(1).trim() : "";
                currentName = !title.isEmpty() ? title : (!tvgName.isEmpty() ? tvgName : "Kanal");
            } else if (!line.startsWith("#") && isHttpUrl(line)) {
                String name = currentName == null || currentName.isEmpty()
                        ? "Kanal " + (result.size() + 1)
                        : currentName;
                String group = currentGroup == null || currentGroup.isEmpty() ? "Pa kategori" : currentGroup;
                result.add(new Channel(name, group, line));
                currentName = null;
                currentGroup = "";
            }
        }
        return result;
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
        if (status != null) status.setText("Po luhet: " + name);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    private void updateFavoriteButton() {
        if (favoriteButton != null) {
            favoriteButton.setText(favoritesOnly ? "★" : "☆");
            favoriteButton.setBackground(roundRect(favoritesOnly ? PURPLE : BLUE, 12));
        }
    }

    private boolean isHttpUrl(String value) {
        if (value == null) return false;
        String s = value.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(12);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundRect(PANEL_2, 11, BORDER));
        return e;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackground(roundRect(BLUE, 12));
        return b;
    }

    private Button topButton(String text, View.OnClickListener listener) {
        Button b = smallButton(text);
        b.setOnClickListener(listener);
        return b;
    }

    private Button bottomButton(String text) {
        Button b = smallButton(text);
        b.setTextSize(12);
        return b;
    }

    private TextView squareIcon(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundRect(BLUE, 12));
        return t;
    }

    private View bigCard(String icon, String label, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(16), dp(10), dp(16));
        card.setBackground(gradient(BLUE, PURPLE, 18));
        card.setOnClickListener(listener);

        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextColor(Color.WHITE);
        i.setTextSize(48);
        i.setGravity(Gravity.CENTER);
        card.addView(i, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTextSize(22);
        l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        l.setGravity(Gravity.CENTER);
        card.addView(l, fullWrap());

        return card;
    }

    private LinearLayout.LayoutParams cardParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (withLeftMargin) p.leftMargin = dp(12);
        return p;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private LinearLayout.LayoutParams weightedWithMargin() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.leftMargin = dp(7);
        return p;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private GradientDrawable gradient(int startColor, int endColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        d.setCornerRadius(dp(radiusDp));
        return d;
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

    private final class GroupAdapter extends ArrayAdapter<String> {
        GroupAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_list_item_1, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = (TextView) super.getView(position, convertView, parent);
            String group = getItem(position);
            boolean selected = group != null && group.equals(selectedGroup);

            row.setText(group == null ? "" : group);
            row.setTextColor(Color.WHITE);
            row.setTextSize(12);
            row.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(12), dp(7), dp(12));
            row.setSingleLine(true);
            row.setBackground(selected ? gradient(PURPLE, Color.rgb(202, 30, 127), 10) : roundRect(PANEL, 8));
            return row;
        }
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
                t1.setTextColor(Color.WHITE);
                t1.setTextSize(13);
                t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

                t2.setText(c.group);
                t2.setTextColor(MUTED);
                t2.setTextSize(10);
            }

            row.setPadding(dp(7), dp(3), dp(5), dp(3));
            row.setBackgroundColor(PANEL_2);
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
