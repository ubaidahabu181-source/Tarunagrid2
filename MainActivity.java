package com.taruna.grid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.HashMap;

public class MainActivity extends Activity {

    // --- CONFIG LGT8F USER ---
    // GANTI INI SESUAI MOTOR KAMU
    int[] rpmBins = {800,1200,1600,2000,2500,3000,3500,4000,4500,5000,5500,6000,6500,7000,7500,8000}; // 16 bins LOGIC
    int[] mapBins = {20,30,40,50,60,70,80,90,100,110,120,130,140,150,160,170}; // 16 bins LOGIC
    // Visible di UI cuma 8x8 biar ringan di AIDE
    int[][] ignitionGrid = new int[16][16]; 

    // --- USB CH340 QINHENG VID 6790 PID 7523 ---
    UsbManager usbManager;
    UsbDevice usbDevice;
    UsbDeviceConnection connection;
    UsbInterface usbInterface;
    UsbEndpoint epIn, epOut;
    boolean isRunning = false;
    static final String ACTION_USB_PERMISSION = "com.taruna.grid.USB_PERMISSION";
    static final int VID_QINHENG = 6790; // 0x1A86
    static final int PID_CH340 = 7523;   // 0x7523

    TextView tvRpm, tvMap, tvAdv, tvLog, tvAntiMati;
    GridLayout gridMap;
    int currentRpm = 0, currentMap = 0, currentAdv = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvRpm = findViewById(R.id.tvRpm);
        tvMap = findViewById(R.id.tvMap);
        tvAdv = findViewById(R.id.tvAdv);
        tvLog = findViewById(R.id.tvLog);
        tvAntiMati = findViewById(R.id.tvAntiMati);
        gridMap = findViewById(R.id.gridMap);

        // Init default grid 14 derajat
        for(int r=0;r<16;r++) for(int c=0;c<16;c++) ignitionGrid[r][c]=14;
        buildGridUI();

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        checkUsbDevice();

        findViewById(R.id.btnGet).setOnClickListener(v -> sendCommand("$GET*"));
        findViewById(R.id.btnSet).setOnClickListener(v -> {
            // Contoh kirim grid baris 0
            StringBuilder sb = new StringBuilder("$SET,0,");
            for(int i=0;i<16;i++){ sb.append(ignitionGrid[0][i]); if(i<15) sb.append(","); }
            sb.append("*");
            sendCommand(sb.toString());
        });

        // Receiver permission
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        IntentFilter attached = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReceiver, attached);
    }

    void log(String s){ runOnUiThread(() -> tvLog.append("\n> "+s)); }

    void buildGridUI(){
        gridMap.removeAllViews();
        // Tampilkan 8x8 saja (ambil tiap 2 index logic 16x16)
        for(int r=0;r<8;r++){
            for(int c=0;c<8;c++){
                final int lr = r*2, lc = c*2;
                TextView tv = new TextView(this);
                tv.setText(String.valueOf(ignitionGrid[lr][lc]));
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(0xFF1E1E20);
                GridLayout.LayoutParams p = new GridLayout.LayoutParams();
                p.width = 0; p.height = 0;
                p.columnSpec = GridLayout.spec(c,1f);
                p.rowSpec = GridLayout.spec(r,1f);
                p.setMargins(2,2,2,2);
                tv.setLayoutParams(p);
                tv.setOnClickListener(v -> {
                    ignitionGrid[lr][lc] +=1;
                    if(ignitionGrid[lr][lc]>45) ignitionGrid[lr][lc]=0;
                    tv.setText(String.valueOf(ignitionGrid[lr][lc]));
                });
                gridMap.addView(tv);
            }
        }
    }

    void checkUsbDevice(){
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        for(UsbDevice dev : list.values()){
            if(dev.getVendorId()==VID_QINHENG){
                log("CH340 Ditemukan VID 6790 PID "+dev.getProductId());
                usbDevice = dev;
                requestPermission();
                return;
            }
        }
        log("Colok USB CH340 dulu...");
    }

    void requestPermission(){
        PendingIntent pi = PendingIntent.getBroadcast(this,0,new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
        usbManager.requestPermission(usbDevice, pi);
    }

    BroadcastReceiver usbReceiver = new BroadcastReceiver(){
        public void onReceive(Context ctx, Intent intent){
            String action = intent.getAction();
            if(ACTION_USB_PERMISSION.equals(action)){
                synchronized(this){
                    UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                        if(dev!=null) openSerial(dev);
                    } else log("Permission Ditolak");
                }
            } else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
                checkUsbDevice();
            }
        }
    };

    void openSerial(UsbDevice device){
        UsbInterface intf = device.getInterface(0);
        UsbDeviceConnection conn = usbManager.openDevice(device);
        if(conn==null || !conn.claimInterface(intf,true)){ log("Gagal claim interface"); return; }

        connection = conn;
        usbInterface = intf;

        // Cari endpoint
        for(int i=0;i<intf.getEndpointCount();i++){
            UsbEndpoint ep = intf.getEndpoint(i);
            if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn = ep;
                else epOut = ep;
            }
        }

        // --- CH340 INIT - ANTI MATI CORE ---
        // Sequence standard CH340: Baud 9600, 8N1
        // Vendor requests: DTR OFF RTS OFF = 0x0000
        // Ini kunci biar LGT8F / Arduino tidak reset saat connect
        try{
            // Set baudrate 9600 -> CH340 factor
            connection.controlTransfer(0x40, 0x9A, 0x1312, 0xD9, null,0,1000); // 0x1312 = 9600 divisor
            connection.controlTransfer(0x40, 0x9A, 0x0F2C, 0x0008, null,0,1000);
            // Set line coding 8N1
            byte[] lineCoding = { (byte)0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08 };
            connection.controlTransfer(0x21, 0x20, 0,0, lineCoding, lineCoding.length,1000);
            
            // --- DTR OFF RTS OFF ---
            // Request 0xA4 = CH340 DTR/RTS control
            // Value 0x0000 = DTR=0 RTS=0 -> ANTI RESET
            int ret1 = connection.controlTransfer(0x40, 0xA4, 0x0000, 0x0000, null,0,1000);
            int ret2 = connection.controlTransfer(0x40, 0xA4, 0x00FF, 0x0000, null,0,1000); // clear
            int ret3 = connection.controlTransfer(0x40, 0xA4, 0x0000, 0x0000, null,0,1000); // pastikan OFF

            log("CH340 INIT OK DTR OFF RTS OFF ret:"+ret1+","+ret2+","+ret3);
            tvAntiMati.setText("ANTI-MATI AKTIF: DTR OFF | RTS OFF | OK");
            tvAntiMati.setBackgroundColor(0xFF162016);
        }catch(Exception e){ log("CH340 INIT ERR: "+e.getMessage()); }

        startReadThread();
    }

    void startReadThread(){
        isRunning = true;
        new Thread(() -> {
            byte[] buffer = new byte[256];
            StringBuilder sb = new StringBuilder();
            while(isRunning){
                try{
                    int len = connection.bulkTransfer(epIn, buffer, buffer.length, 200);
                    if(len>0){
                        String chunk = new String(buffer,0,len);
                        sb.append(chunk);
                        // Parser frame $OK,RPM,657,MAP,36,ADV,14*
                        int star;
                        while((star = sb.indexOf("*"))!=-1){
                            int dollar = sb.lastIndexOf("$", star);
                            if(dollar!=-1){
                                String frame = sb.substring(dollar, star+1);
                                parseFrame(frame);
                                sb.delete(0, star+1);
                            } else sb.delete(0, star+1);
                        }
                    }
                }catch(Exception e){ log("Read err "+e.getMessage()); }
            }
        }).start();
    }

    void parseFrame(String frame){
        // Format: $OK,RPM,657,MAP,36,ADV,14*
        if(!frame.startsWith("$OK")) return;
        try{
            frame = frame.replace("$","").replace("*",""); // OK,RPM,657,MAP,36,ADV,14
            String[] p = frame.split(",");
            // p[0]=OK p[1]=RPM p[2]=657 p[3]=MAP p[4]=36 p[5]=ADV p[6]=14
            for(int i=0;i<p.length-1;i++){
                if(p[i].equals("RPM")) currentRpm = Integer.parseInt(p[i+1]);
                if(p[i].equals("MAP")) currentMap = Integer.parseInt(p[i+1]);
                if(p[i].equals("ADV")) currentAdv = Integer.parseInt(p[i+1]);
            }
            runOnUiThread(() -> {
                tvRpm.setText("RPM\n"+currentRpm);
                tvMap.setText("MAP\n"+currentMap);
                tvAdv.setText("ADV\n"+currentAdv+"°");
            });
            log(frame);
        }catch(Exception e){ }
    }

    void sendCommand(String cmd){
        if(connection==null || epOut==null){ log("USB Belum Connect"); return; }
        try{
            byte[] data = (cmd+"\n").getBytes();
            connection.bulkTransfer(epOut, data, data.length, 500);
            log("TX: "+cmd);
        }catch(Exception e){ log("TX ERR "+e.getMessage()); }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        isRunning = false;
        try{ unregisterReceiver(usbReceiver);}catch(Exception e){}
        if(connection!=null){ connection.releaseInterface(usbInterface); connection.close(); }
    }
}