package com.taruna.grid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    boolean dtrOn=false, rtsOn=false;
    UsbDeviceConnection conn=null;
    UsbEndpoint epIn=null, epOut=null;
    boolean isLive=false;
    private static final String ACTION_USB_PERMISSION = "com.taruna.grid.USB_PERMISSION";
    PendingIntent permissionIntent;
    BroadcastReceiver usbReceiver;

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            setContentView(R.layout.activity_main);
            tvLog=findViewById(R.id.tv);
            tvStatus=findViewById(R.id.tv_status);
            tvRpm=findViewById(R.id.tv_rpm);
            tvMap=findViewById(R.id.tv_map);
            tvAdv=findViewById(R.id.tv_adv);
            tvRowCol=findViewById(R.id.tv_rowcol);
            btnDtr=findViewById(R.id.btn_dtr);
            btnRts=findViewById(R.id.btn_rts);
            btnConnect=findViewById(R.id.btn_connect);
            btnGetMap=findViewById(R.id.btn_getmap);
            btnSaveMap=findViewById(R.id.btn_savemap);

            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbReceiver = new BroadcastReceiver(){
                public void onReceive(Context context, Intent intent){
                    if(ACTION_USB_PERMISSION.equals(intent.getAction())){
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                            if(device!=null){
                                tvLog.setText("Permission OK! Auto CONNECT...");
                                runOnUiThread(() -> {
                                    try{ Thread.sleep(300); }catch(Exception e){}
                                    connect();
                                });
                            }
                        }else{
                            tvLog.setText("Permission ditolak! Cabut OTG + Allow!");
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if(Build.VERSION.SDK_INT >= 33){
                registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
            }else{
                registerReceiver(usbReceiver, filter);
            }

            tvLog.setText("V7.4 FINAL FIX BOS\nFIRMWARE: $OK,RPM,800,MAP,98,ADV,9*\nPencet CONNECT -> OKE -> Auto LIVE!");

            btnDtr.setOnClickListener(v->{
                dtrOn=!dtrOn;
                btnDtr.setText(dtrOn?"DTR ON":"DTR OFF");
                setDtrRts();
            });
            btnRts.setOnClickListener(v->{
                rtsOn=!rtsOn;
                btnRts.setText(rtsOn?"RTS ON":"RTS OFF");
                setDtrRts();
            });
            btnGetMap.setOnClickListener(v-> sendCmd("$GET*\n"));
            btnSaveMap.setOnClickListener(v-> sendCmd("$SAVE*\n"));
            btnConnect.setOnClickListener(v-> connect());

        }catch(Exception e){
            Toast.makeText(this, "Init Error: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().isEmpty()){
                tvLog.setText("No USB! Colok LGT8F via OTG!");
                return;
            }
            UsbDevice dev=um.getDeviceList().values().iterator().next();
            tvLog.setText("Device: "+dev.getDeviceName()+"\nVID:"+dev.getVendorId()+" PID:"+dev.getProductId()+"\nInterfaces:"+dev.getInterfaceCount());

            if(!um.hasPermission(dev)){
                tvLog.append("\nMinta permission...");
                um.requestPermission(dev, permissionIntent);
                return;
            }

            conn=um.openDevice(dev);
            if(conn==null){ tvLog.append("\nGagal open!"); return; }

            // FIX CDC - SCAN SEMUA INTERFACE CARI BULK
            epIn=null; epOut=null;
            for(int i=0;i<dev.getInterfaceCount();i++){
                UsbInterface intf=dev.getInterface(i);
                tvLog.append("\nCek Interface "+i+" ep:"+intf.getEndpointCount());
                for(int j=0;j<intf.getEndpointCount();j++){
                    UsbEndpoint ep=intf.getEndpoint(j);
                    if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                        if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep;
                        else epOut=ep;
                        tvLog.append("\nFound BULK "+(ep.getDirection()==0?"OUT":"IN")+" di Iface "+i);
                    }
                }
            }

            if(epIn==null || epOut==null){
                tvLog.append("\nEndpoint ga ketemu!");
                return;
            }

            // FIX CDC - CLAIM SEMUA INTERFACE BIAR DATA MASUK! INI KUNCI NYA!
            for(int i=0;i<dev.getInterfaceCount();i++){
                try{ conn.claimInterface(dev.getInterface(i), true); }catch(Exception e){}
            }

            // Set CDC 115200
            try{
                conn.controlTransfer(0x21,0x22,0,0,null,0,1000);
                conn.controlTransfer(0x21,0x20,0,0,new byte[]{(byte)0x00,(byte)0xC2,(byte)0x01,(byte)0x00,0x00,0x00,0x08},7,1000);
            }catch(Exception e){}

            setDtrRts();
            tvStatus.setText("LIVE - V7.4");
            tvStatus.setTextColor(0xFF00FF00);
            btnConnect.setText("CONNECTED");
            isLive=true;
            startRead();
            tvLog.append("\n\nCONNECTED CDC 115200!\nDTR OFF RTS OFF ANTI-MATI\nMenunggu $OK,RPM,800,MAP,98,ADV,9*");

        }catch(Exception e){
            tvLog.setText("CONNECT ERR: "+e.getMessage());
        }
    }

    void setDtrRts(){
        try{
            if(conn==null) return;
            int val=(dtrOn?1:0)|(rtsOn?2:0);
            conn.controlTransfer(0x40,0xA4,val,0,null,0,1000);
            conn.controlTransfer(0x21,0x22,val,0,null,0,1000);
        }catch(Exception e){}
    }

    void sendCmd(String s){
        try{
            if(conn!=null && epOut!=null){
                conn.bulkTransfer(epOut,s.getBytes(),s.length(),1000);
                tvLog.append("\n> "+s.trim());
            }
        }catch(Exception e){}
    }

    void startRead(){
        new Thread(()->{
            byte[] buf=new byte[4096];
            StringBuilder sb=new StringBuilder();
            while(isLive){
                try{
                    int n=conn.bulkTransfer(epIn,buf,buf.length,1000);
                    if(n>0){
                        String chunk=new String(buf,0,n);
                        sb.append(chunk);
                        String all=sb.toString();
                        while(all.contains("*")){
                            int idx=all.indexOf("*");
                            String line=all.substring(0,idx+1);
                            all=all.substring(idx+1);
                            parse(line);
                        }
                        sb=new StringBuilder(all);
                    }
                }catch(Exception e){}
            }
        }).start();
    }

    void parse(String line){
        runOnUiThread(()->{
            try{
                String clean = line.trim().replace("\r","").replace("\n","");
                if(clean.length()<5) return;
                // Hanya proses yang $OK,RPM,
                if(!clean.contains("RPM,") || !clean.contains("MAP,") || !clean.contains("ADV,")){
                    if(clean.startsWith("$")) tvLog.append("\n"+clean);
                    return;
                }
                // clean = $OK,RPM,800,MAP,98,ADV,9*
                String noStar = clean.replace("*","").replace("$OK,","").replace("$","").trim();
                // noStar = RPM,800,MAP,98,ADV,9
                String[] p = noStar.split(",");
                if(p.length>=6){
                    int rpm=0, map=0, adv=0;
                    for(int i=0;i<p.length-1;i++){
                        if(p[i].equals("RPM")) rpm=Integer.parseInt(p[i+1]);
                        if(p[i].equals("MAP")) map=Integer.parseInt(p[i+1]);
                        if(p[i].equals("ADV")) adv=Integer.parseInt(p[i+1]);
                    }
                    tvRpm.setText(String.valueOf(rpm));
                    tvMap.setText(map+" kPa");
                    tvAdv.setText(adv+"°");
                    tvRowCol.setText("LIVE");
                    tvStatus.setText("LIVE - "+rpm+" RPM");
                    tvStatus.setTextColor(0xFF00FF00);
                }
            }catch(Exception e){
                // tvLog.append("\nParse ERR:"+line);
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        isLive=false;
        try{ if(conn!=null) conn.close(); }catch(Exception e){}
        try{ unregisterReceiver(usbReceiver); }catch(Exception e){}
    }
                        }
