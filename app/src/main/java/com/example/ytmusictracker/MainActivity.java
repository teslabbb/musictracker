package com.example.ytmusictracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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
                "1) Toca el boton y activa el acceso a notificaciones.\n" +
                "2) Abri YouTube Music y reproduci algo.\n" +
                "3) Datos en:\nAndroid/data/" + getPackageName() + "/files/"
        );
        layout.addView(info);

        Button grantButton = new Button(this);
        grantButton.setText("Activar acceso a notificaciones");
        grantButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
        layout.addView(grantButton);

        setContentView(layout);
    }
}
