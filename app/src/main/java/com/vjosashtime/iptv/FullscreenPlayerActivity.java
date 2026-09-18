package com.vjosashtime.iptv;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class FullscreenPlayerActivity extends Activity {

    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        enterImmersiveMode();

        String url = getIntent().getStringExtra("url");
        long position = getIntent().getLongExtra("position", 0L);

        PlayerView playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerView.setUseController(true);
        setContentView(playerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.prepare();
            if (position > 0) player.seekTo(position);
            player.play();
        } else {
            finish();
        }
    }

    private void enterImmersiveMode() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();
    }
}
