package com.taruna.grid;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import java.util.List;

public class MainActivity extends Activity {
    // UI sesuai foto
    TextView tvLog, tvRpm, tvMap, tvAdv, tvRow, tvCol, tvStatus;
    Button btnDtr, btnRts, btnConnect, btnGetMap;
    
    UsbSerialPort port;
    boolean dtrOn = false; // HARUS OFF BOS! Anti-mati!
    boolean rtsOn = false; // HARUS OFF!
    int[][] advMap = new int[16][16];
    int currentRpm = 0, currentMap = 0, currentAdv = 0;

    // GRID dari firmware lu
    int[] RPM_GRID = {500,700,900,1100,1300,1500,1800,2200,2600,3000,3500,4000,4500,5000,5750,6500};
    int[] MAP_GRID = {20,25,30,35,40,45,50,55,60,65,70,75,80,85,92,100};

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        // findViewById semua...

        btnDtr.setOnClickListener(v -> {
            dtrOn = !dtrOn;
            if(port != null) try{ port.setDTR(dtrOn); }catch(Exception e){}
            btnDtr.setText("DTR " + (dtrOn?"ON":"OFF"));
        });
        btnRts.setOnClickListener(v -> {
            rtsOn = !rtsOn;
            if(port != null) try{ port.setRTS(rtsOn); }catch(Exception e){}
            btnRts.setText("RTS " + (rtsOn?"ON":"OFF"));
        });

        btnGetMap.setOnClickListener(v -> {
            // KIRIM $GET* sesuai firmware lu
            sendCommand("$GET*");
            tvLog.append("\n> $GET* - minta peta...");
        });

        btnConnect.setOnClickListener(v -> connectLGT8F());
    }

    void connectLGT8F(){
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if(drivers.isEmpty()){
            tvLog.setText("No LGT8F found! Colok OTG!");
            return;
        }
        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();
        UsbDeviceConnection conn = manager.openDevice(device);
        port = driver.getPorts().get(0);
        try{
            port.open(conn);
            port.setParameters(115200,8,1,UsbSerialPort.PARITY_NONE);
            port.setDTR(false); // ANTI-MATI!
            port.setRTS(false); // ANTI-MATI!
            tvStatus.setText("LIVE - LGT8F CONNECTED");
            // start reading thread...
            startReading();
        }catch(Exception e){ tvLog.setText("Error: "+e.getMessage()); }
    }

    void startReading(){
        new Thread(() -> {
            byte[] buf = new byte[1024];
            StringBuilder sb = new StringBuilder();
            while(true){
                try{
                    int n = port.read(buf, 1000);
                    if(n>0){
                        String s = new String(buf,0,n);
                        sb.append(s);
                        String all = sb.toString();
                        // parse line by line * 
                        while(all.contains("*")){
                            int idx = all.indexOf("*");
                            String line = all.substring(0,idx+1);
                            all = all.substring(idx+1);
                            parseLine(line);
                        }
                        sb = new StringBuilder(all);
                    }
                }catch(Exception e){}
            }
        }).start();
    }

    void parseLine(String line){
        // $OK,RPM,1250,MAP,65,ADV,12*
        if(line.startsWith("$OK,RPM,")){
            try{
                String[] p = line.split(",");
                int rpm = Integer.parseInt(p[1]);
                int map = Integer.parseInt(p[3]);
                int adv = Integer.parseInt(p[5].replace("*",""));
                runOnUiThread(() -> {
                    tvRpm.setText(String.valueOf(rpm));
                    tvMap.setText(map+" kPa");
                    tvAdv.setText(adv+"°");
                    // Hitung ROW COL
                    int r = findIndex(map, MAP_GRID);
                    int c = findIndex(rpm, RPM_GRID);
                    tvRow.setText("ROW "+r);
                    tvCol.setText("COL "+c);
                });
            }catch(Exception e){}
        }
        // $MAPR,0,16,16,19,...
        else if(line.startsWith("$MAPR,")){
            // simpan ke advMap
        }
    }

    int findIndex(int val, int[] grid){
        for(int i=0;i<15;i++) if(val>=grid[i] && val<grid[i+1]) return i;
        return 15;
    }

    void sendCommand(String cmd){
        try{ port.write(cmd.getBytes(),1000); }catch(Exception e){}
    }
}
