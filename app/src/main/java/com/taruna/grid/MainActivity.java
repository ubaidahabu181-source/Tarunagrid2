package com.taruna.grid;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.hardware.usb.UsbManager;

public class MainActivity extends Activity {
    TextView tv, tvStatus;
    Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = findViewById(R.id.tv);
        tvStatus = findViewById(R.id.tv_status);
        btnConnect = findViewById(R.id.btn_connect);

        btnConnect.setOnClickListener(v -> {
            UsbManager m = (UsbManager) getSystemService(USB_SERVICE);
            int c = m.getDeviceList().size();
            tvStatus.setText("USB: " + c + " device");
            if(c==0){
                tv.setText("Colok LGT8F via OTG dulu bos!\nDTR OFF RTS OFF biar ga reset pas stater!");
            } else {
                tv.setText("LGT8F Terdeteksi!\nSiap kirim $GET* buat baca MAP 16x16!\nFirmware V7.4 REAL DEG ready!");
            }
        });
    }
}
