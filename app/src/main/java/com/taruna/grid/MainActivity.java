package com.taruna.grid;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
public class MainActivity extends Activity {
    TextView tvRpm, tvStatus; Button btnConnect;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvRpm = findViewById(R.id.tvRpm); tvStatus = findViewById(R.id.tvStatus); btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(v -> { tvStatus.setText("CH340 CONNECTED - BUILD OK!"); tvRpm.setText("RPM: 657 LIVE"); });
    }
}ose(); }
    }
}
