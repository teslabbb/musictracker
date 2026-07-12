package com.example.ytmusictracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee la MediaSession de YouTube Music (play/pause/metadata reales, no inferidos)
 * y acumula tiempo total escuchado por cancion y por artista en archivos CSV,
 * guardados en el almacenamiento privado de la app (no requiere permisos extra).
 */
public class PlaybackTrackerService extends NotificationListenerService {

    private static final String YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";
    private static final String CHANNEL_ID = "tracker_channel";
    private static final int NOTIF_ID = 1;
    private static final long FLUSH_INTERVAL_MS = 15000; // guarda a disco cada 15s

    private MediaSessionManager sessionManager;
    private MediaController activeController;
    private MediaController.Callback controllerCallback;

    private String currentTitle = "";
    private String currentArtist = "";
    private boolean isPlaying = false;
    private long lastEventTimeMillis = 0;

    // clave "titulo|||artista" -> segundos acumulados
    private final Map<String, Double> songSeconds = new HashMap<>();
    private final Map<String, Double> artistSeconds = new HashMap<>();

    private Handler handler;
    private Runnable flushRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Esperando reproduccion..."));

        handler = new Handler(Looper.getMainLooper());
        flushRunnable = new Runnable() {
            @Override
            public void run() {
                flushElapsed();
                writeCsvFiles();
                handler.postDelayed(this, FLUSH_INTERVAL_MS);
            }
        };
        handler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        attachToYtMusicIfPresent();
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    controllers -> attachToYtMusicIfPresent(),
                    new ComponentName(this, PlaybackTrackerService.class));
        } catch (SecurityException ignored) {
        }
    }

    private void attachToYtMusicIfPresent() {
        try {
            List<MediaController> controllers =
                    sessionManager.getActiveSessions(new ComponentName(this, PlaybackTrackerService.class));
            for (MediaController mc : controllers) {
                if (YT_MUSIC_PACKAGE.equals(mc.getPackageName())) {
                    if (activeController != null && controllerCallback != null) {
                        activeController.unregisterCallback(controllerCallback);
                    }
                    activeController = mc;
                    controllerCallback = new MediaController.Callback() {
                        @Override
                        public void onPlaybackStateChanged(PlaybackState state) {
                            handleStateChange(state);
                        }

                        @Override
                        public void onMetadataChanged(MediaMetadata metadata) {
                            handleMetadataChange(metadata);
                        }
                    };
                    activeController.registerCallback(controllerCallback, handler);
                    handleMetadataChange(activeController.getMetadata());
                    handleStateChange(activeController.getPlaybackState());
                    break;
                }
            }
        } catch (SecurityException e) {
            // Falta activar el permiso de acceso a notificaciones para esta app.
        }
    }

    private void handleMetadataChange(MediaMetadata metadata) {
        if (metadata == null) return;
        String title = safe(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = safe(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        if (title.isEmpty()) return;

        if (!title.equals(currentTitle) || !artist.equals(currentArtist)) {
            flushElapsed(); // cierra el tramo de la cancion anterior antes de cambiar
            currentTitle = title;
            currentArtist = artist;
            lastEventTimeMillis = System.currentTimeMillis();
            logChange(title, artist);
        }
    }

    private void handleStateChange(PlaybackState state) {
        if (state == null) return;
        boolean nowPlaying = state.getState() == PlaybackState.STATE_PLAYING;
        if (nowPlaying && !isPlaying) {
            lastEventTimeMillis = System.currentTimeMillis();
        } else if (!nowPlaying && isPlaying) {
            flushElapsed();
        }
        isPlaying = nowPlaying;
        updateNotification();
    }

    private void flushElapsed() {
        if (isPlaying && !currentTitle.isEmpty()) {
            long now = System.currentTimeMillis();
            double elapsedSec = (now - lastEventTimeMillis) / 1000.0;
            lastEventTimeMillis = now;
            if (elapsedSec > 0) {
                String key = currentTitle + "|||" + currentArtist;
                songSeconds.put(key, (songSeconds.containsKey(key) ? songSeconds.get(key) : 0) + elapsedSec);

                for (String artist : currentArtist.split(",")) {
                    String a = artist.trim();
                    if (a.isEmpty()) continue;
                    artistSeconds.put(a, (artistSeconds.containsKey(a) ? artistSeconds.get(a) : 0) + elapsedSec);
                }
            }
        }
    }

    private void logChange(String title, String artist) {
        try {
            File dir = getExternalFilesDir(null);
            File f = new File(dir, "log_reproducciones.csv");
            boolean isNew = !f.exists();
            FileWriter fw = new FileWriter(f, true);
            if (isNew) fw.append("timestamp_exacto,titulo,artista\n");
            String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
            fw.append(csvEscape(ts)).append(",").append(csvEscape(title)).append(",").append(csvEscape(artist)).append("\n");
            fw.close();
        } catch (IOException ignored) {
        }
    }

    private void writeCsvFiles() {
        try {
            File dir = getExternalFilesDir(null);

            FileWriter songW = new FileWriter(new File(dir, "tiempo_por_cancion.csv"), false);
            songW.append("titulo,artistas,segundos_totales,hh_mm_ss\n");
            for (Map.Entry<String, Double> e : songSeconds.entrySet()) {
                String[] parts = e.getKey().split("\\|\\|\\|", -1);
                int secs = e.getValue().intValue();
                songW.append(csvEscape(parts[0])).append(",")
                        .append(csvEscape(parts.length > 1 ? parts[1] : "")).append(",")
                        .append(String.valueOf(secs)).append(",").append(hhmmss(secs)).append("\n");
            }
            songW.close();

            FileWriter artW = new FileWriter(new File(dir, "tiempo_por_artista.csv"), false);
            artW.append("artista,segundos_totales,hh_mm_ss\n");
            for (Map.Entry<String, Double> e : artistSeconds.entrySet()) {
                int secs = e.getValue().intValue();
                artW.append(csvEscape(e.getKey())).append(",")
                        .append(String.valueOf(secs)).append(",").append(hhmmss(secs)).append("\n");
            }
            artW.close();
        } catch (IOException ignored) {
        }
    }

    private String hhmmss(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void updateNotification() {
        String text = currentTitle.isEmpty()
                ? "Esperando reproduccion..."
                : (isPlaying ? "Sonando: " : "Pausado: ") + currentTitle + " - " + currentArtist;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("YT Music Tracker")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Tracker", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        flushElapsed();
        writeCsvFiles();
        if (handler != null && flushRunnable != null) handler.removeCallbacks(flushRunnable);
        super.onDestroy();
    }
}
