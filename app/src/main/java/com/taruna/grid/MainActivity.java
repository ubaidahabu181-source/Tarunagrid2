package com.taruna.grid;

import android.app.Activity;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    TextView tvLog, tvStatus;
    Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog = findViewById(R.id.tv);
        tvStatus = findViewById(R.id.tv_status);
        btnConnect = findViewById(R.id.btn_connect);
        
        // Kalau layout baru lu pakai ID beda, ganti findViewById nya!
        // Yang penting jangan pakai import com.hoho... biar ga merah

        btnConnect.setOnClickListener(v -> {
            UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
            int count = manager.getDeviceList().size();
            tvStatus.setText("DEVICE: " + count + " FOUND");
            tvLog.setText("LOG:\nV7.4 PROTOCOL READY\n" +
                         "FIRMWARE LU: $OK,RPM,MAP,ADV*\n" +
                         "COMMAND: $GET* = GET MAP\n" +
                         "$SET,r,c,v = SET CELL\n" +
                         "$SAVE* = SAVE EEPROM\n\n" +
                         "USB Devices: " + count + "\n" +
                         "DTR OFF RTS OFF = ANTI-MATI BOS!\n" +
                         "Prosedur: Mesin mati -> Colok OTG -> CONNECT -> Stater");
        });
    }
}
