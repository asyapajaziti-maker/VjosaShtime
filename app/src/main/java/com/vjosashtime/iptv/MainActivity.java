package com.vjosashtime.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
    private static final int NAVY = Color.rgb(3, 18, 42);
    private static final int NAVY_2 = Color.rgb(5, 39, 92);
    private static final int BLUE = Color.rgb(0, 109, 255);
    private static final int BLUE_2 = Color.rgb(0, 63, 190);
    private static final int CYAN = Color.rgb(67, 211, 255);
    private static final int WHITE = Color.WHITE;
    private static final int SOFT = Color.rgb(191, 217, 255);
    private static final int CARD_BORDER = Color.rgb(40, 146, 255);

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
    private String sectionFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        prefs = getSharedPreferences("vjosa_shtime", MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));

        player = new ExoPlayer.Builder(this).build();
        buildHome();
    }

    private void buildHome() {
        if (player != null) player.pause();
        playerView = null;
        sectionFilter = "";

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(diagonalBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("vjosashtime");
        logo.setTextColor(Color.rgb(8, 18, 34));
        logo.setTextSize(25);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(dp(18), dp(13), dp(18), dp(13));
        logo.setBackground(roundRect(Color.WHITE, 14));
        brandRow.addView(logo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView privateTag = new TextView(this);
        privateTag.setText("  PRIVATE PLAYER");
        privateTag.setTextColor(SOFT);
        privateTag.setTextSize(11);
        privateTag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandRow.addView(privateTag);
        root.addView(brandRow, fullWrap());

        HorizontalScrollView menuScroll = new HorizontalScrollView(this);
        menuScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setPadding(0, dp(14), 0, dp(8));
        menuScroll.addView(menu);

        Button sports = smallTopButton("▤  Sports guide");
        sports.setOnClickListener(v -> toast("Sports guide do të shtohet në versionin tjetër."));
        menu.addView(sports, topMenuParams());

        Button server = smallTopButton("▦  Change Server");
        server.setOnClickListener(v -> {
            buildPlayerScreen("", "Change Server");
            if (playlistUrl != null) {
                playlistUrl.requestFocus();
                playlistUrl.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(playlistUrl, InputMethodManager.SHOW_IMPLICIT);
                }, 250);
            }
        });
        menu.addView(server, topMenuParams());

        Button settings = smallTopButton("⚙  Cilësimet");
        settings.setOnClickListener(v -> showSettings());
        menu.addView(settings, topMenuParams());
        root.addView(menuScroll, fullWrap());

        TextView welcome = new TextView(this);
        welcome.setText("Zgjidh çfarë dëshiron të shikosh");
        welcome.setTextColor(Color.WHITE);
        welcome.setTextSize(17);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        welcome.setPadding(dp(2), dp(12), 0, dp(12));
        root.addView(welcome, fullWrap());

        GridLayout grid = new GridLayout(this);
        int columns = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        addDashboardCard(grid, "▣", "Live TV", "Kanalet live", () -> buildPlayerScreen("", "Live TV"), columns);
        addDashboardCard(grid, "▶", "Filmat", "Filma nga lista", () -> buildPlayerScreen("film movie cinema vod", "Filmat"), columns);
        addDashboardCard(grid, "◎", "Serialet", "Seriale & episode", () -> buildPlayerScreen("serial series episode", "Serialet"), columns);
        addDashboardCard(grid, "↶", "Përsëritje", "Catch-up / Replay", () -> buildPlayerScreen("replay catchup catch-up archive", "Përsëritje"), columns);
        root.addView(grid, fullWrap());

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setPadding(0, dp(18), 0, dp(8));

        Button refresh = tinyButton("⟳  Rifresko");
        refresh.setOnClickListener(v -> {
            String saved = prefs.getString("last_playlist", "");
            if (saved.isEmpty()) toast("Ende nuk ke ruajtur listë IPTV.");
            else {
                buildPlayerScreen("", "Live TV");
                playlistUrl.setText(saved);
                loadPlaylist();
            }
        });
        quick.addView(refresh, quickParams());

        Button direct = tinyButton("▰  Video");
        direct.setOnClickListener(v -> buildPlayerScreen("", "Video Player"));
        quick.addView(direct, quickParams());

        Button fav = tinyButton("★  Favoritet");
        fav.setOnClickListener(v -> {
            buildPlayerScreen("", "Favoritet");
            favoritesOnly.setChecked(true);
        });
        quick.addView(fav, quickParams());
        root.addView(quick, fullWrap());

        TextView note = new TextView(this);
        note.setText("Vjosa Shtime • IPTV & Video Player");
        note.setTextColor(Color.rgb(130, 178, 234));
        note.setGravity(Gravity.CENTER);
        note.setTextSize(11);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note, fullWrap());

        setContentView(scroll);
    }

    private void addDashboardCard(GridLayout grid, String icon, String title, String subtitle, Runnable action, int columns) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(22), dp(10), dp(20));
        card.setBackground(blueCard());
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> action.run());

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(Color.rgb(190, 235, 255));
        iconView.setTextSize(46);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, fullWrap());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView, fullWrap());

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(Color.rgb(180, 217, 255));
        subView.setTextSize(11);
        subView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subP = fullWrap();
        subP.topMargin = dp(5);
        card.addView(subView, subP);

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = dp(columns == 4 ? 220 : 190);
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(5), dp(6), dp(5), dp(6));
        grid.addView(card, gp);
    }

    private void buildPlayerScreen(String initialFilter, String screenTitle) {
        sectionFilter = initialFilter == null ? "" : initialFilter;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.setBackground(diagonalBackground());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back = smallTopButton("‹  Kryefaqja");
        back.setOnClickListener(v -> buildHome());
        top.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        TextView title = new TextView(this);
        title.setText(screenTitle);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        tp.leftMargin = dp(8);
        top.addView(title, tp);
        root.addView(top, fullWrap());

        LinearLayout serverCard = new LinearLayout(this);
        serverCard.setOrientation(LinearLayout.VERTICAL);
        serverCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        serverCard.setBackground(roundRect(Color.argb(145, 7, 39, 92), 16, Color.rgb(31, 114, 222)));
        LinearLayout.LayoutParams scp = fullWrap();
        scp.topMargin = dp(8);
        root.addView(serverCard, scp);

        playlistUrl = darkInput("Linku i listës M3U / M3U8");
        playlistUrl.setText(prefs.getString("last_playlist", ""));
        serverCard.addView(playlistUrl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        Button load = button("NGARKO LISTËN");
        load.setOnClickListener(v -> loadPlaylist());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(7);
        serverCard.addView(load, lp);

        LinearLayout directRow = new LinearLayout(this);
        directRow.setOrientation(LinearLayout.HORIZONTAL);
        directRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams drp = fullWrap();
        drp.topMargin = dp(8);
        root.addView(directRow, drp);

        directUrl = darkInput("Link direkt video / stream");
        directRow.addView(directUrl, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button play = button("LUAJ");
        play.setOnClickListener(v -> playUrl(directUrl.getText().toString().trim(), "Video"));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(88), dp(46));
        pp.leftMargin = dp(7);
        directRow.addView(play, pp);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerView.setPlayer(player);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        playerParams.topMargin = dp(8);
        root.addView(playerView, playerParams);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(4), dp(6), dp(4), dp(6));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        infoRow.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));

        status = new TextView(this);
        status.setText(allChannels.isEmpty() ? "Gati. Shto listën tënde IPTV." : "Lista është gati.");
        status.setTextSize(11);
        status.setTextColor(SOFT);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = dp(6);
        infoRow.addView(status, sp);

        count = new TextView(this);
        count.setText("0 kanale");
        count.setTextColor(CYAN);
        count.setTextSize(12);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoRow.addView(count);
        root.addView(infoRow, fullWrap());

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        search = darkInput("Kërko kanal ose kategori...");
        filterRow.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1f));

        favoritesOnly = new CheckBox(this);
        favoritesOnly.setText("★ Favoritet");
        favoritesOnly.setTextColor(Color.WHITE);
        favoritesOnly.setTextSize(11);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        fp.leftMargin = dp(3);
        filterRow.addView(favoritesOnly, fp);
        root.addView(filterRow, fullWrap());

        listView = new ListView(this);
        listView.setDividerHeight(dp(4));
        listView.setBackgroundColor(Color.TRANSPARENT);
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
        hint.setTextColor(Color.rgb(130, 178, 234));
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, fullWrap());

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });
        favoritesOnly.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilter());

        setContentView(root);
        applyFilter();
    }

    private EditText darkInput(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(13);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(151, 183, 222));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundRect(Color.rgb(11, 39, 77), 12, Color.rgb(36, 105, 190)));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(blueButton());
        return b;
    }

    private Button smallTopButton(String text) {
        Button b = button(text);
        b.setTextSize(11);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private Button tinyButton(String text) {
        Button b = button(text);
        b.setTextSize(10);
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private LinearLayout.LayoutParams topMenuParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(142), dp(44));
        p.rightMargin = dp(7);
        return p;
    }

    private LinearLayout.LayoutParams quickParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(43), 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private GradientDrawable diagonalBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(2, 12, 32), Color.rgb(4, 31, 82), Color.rgb(2, 16, 42)});
    }

    private GradientDrawable blueButton() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(0, 76, 210), Color.rgb(0, 142, 255)});
        d.setCornerRadius(dp(13));
        d.setStroke(dp(1), Color.rgb(54, 175, 255));
        return d;
    }

    private GradientDrawable blueCard() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(0, 43, 150), Color.rgb(0, 119, 255), Color.rgb(4, 39, 132)});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), CARD_BORDER);
        return d;
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

    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Vjosa Shtime")
                .setMessage("Player privat për lista M3U/M3U8 dhe video stream.\n\nLista e fundit ruhet vetëm në telefonin tënd. Favoritet ruhen lokalisht.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void loadPlaylist() {
        if (playlistUrl == null) return;
        String url = playlistUrl.getText().toString().trim();
        if (!isHttpUrl(url)) {
            toast("Vendos një link http:// ose https://.");
            return;
        }
        prefs.edit().putString("last_playlist", url).apply();
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (status != null) status.setText("Po ngarkohet lista...");

        executor.execute(() -> {
            try {
                String text = download(url);
                List<Channel> channels = parseM3u(text);
                runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    allChannels.clear();
                    allChannels.addAll(channels);
                    applyFilter();
                    if (status != null) {
                        status.setText(channels.isEmpty() ? "Nuk u gjetën kanale në listë." : "Lista u ngarkua me sukses.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (status != null) status.setText("Gabim gjatë ngarkimit.");
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
        c.setRequestProperty("User-Agent", "Vjosa-Shtime/2.0");
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
        if (search == null || favoritesOnly == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean favOnly = favoritesOnly.isChecked();
        visibleChannels.clear();

        String[] sectionWords = sectionFilter.trim().isEmpty() ? new String[0] : sectionFilter.toLowerCase(Locale.ROOT).split("\\s+");
        for (Channel c : allChannels) {
            String hay = (c.name + " " + c.group).toLowerCase(Locale.ROOT);
            boolean textOk = q.isEmpty() || hay.contains(q);
            boolean sectionOk = sectionWords.length == 0;
            for (String word : sectionWords) {
                if (!word.isEmpty() && hay.contains(word)) {
                    sectionOk = true;
                    break;
                }
            }
            boolean favOk = !favOnly || favorites.contains(c.url);
            if (textOk && sectionOk && favOk) visibleChannels.add(c);
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
        if (status != null) status.setText("Po luhet: " + name);
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
    public void onBackPressed() {
        if (playlistUrl != null && playlistUrl.getParent() != null) {
            buildHome();
            playlistUrl = null;
            return;
        }
        super.onBackPressed();
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
                t1.setTextColor(Color.WHITE);
                t1.setTextSize(14);
                t2.setText(c.group.isEmpty() ? "IPTV" : c.group);
                t2.setTextColor(Color.rgb(158, 194, 235));
                t2.setTextSize(11);
            }
            row.setPadding(dp(8), dp(2), dp(8), dp(2));
            row.setBackground(roundRect(Color.rgb(7, 35, 73), 9, Color.rgb(22, 78, 142)));
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
