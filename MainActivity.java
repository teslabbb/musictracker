package com.example.ytmusictracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 100, 40, 40);

        TextView info = new TextView(this);
        info.setTextSize(16);
        info.setText(
                "YT Music Tracker\n\n" +
                "Pasos:\n" +
                "1) Toca el boton de abajo y activale el acceso a notificaciones a esta app " +
                "(la vas a ver listada como 'YT Music Tracker Service').\n" +
                "2) Abri YouTube Music y reproduci algo.\n" +
                "3) Los datos se van guardando solos en:\n\n" +
                "Android/data/" + getPackageName() + "/files/\n\n" +
                "Archivos generados:\n" +
                "- tiempo_por_cancion.csv\n" +
                "- tiempo_por_artista.csv\n" +
                "- log_reproducciones.csv\n\n" +
                "No hace falta dejar esta pantalla abierta: el servicio queda corriendo en segundo plano " +
                "con una notificacion fija mientras este activo el permiso."
        );
        layout.addView(info);

        Button grantButton = new Button(this);
        grantButton.setText("Activar acceso a notificaciones");
        grantButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        layout.addView(grantButton);

        setContentView(layout);
    }
}
