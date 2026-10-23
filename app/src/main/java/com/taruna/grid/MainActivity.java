package com.taruna.grid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
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
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        usbReceiver = new BroadcastReceiver(){
            public void onReceive(Context context, Intent intent){
                String action = intent.getAction();
                if(ACTION_USB_PERMISSION.equals(action)){
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                        if(device!=null){
                            tvLog.append("\nPermission OK! Klik CONNECT lagi bos!");
                        }
                    }else{
                        tvLog.setText("Permission ditolak! Cabut OTG colok lagi + Allow!");
                    }
                }
            }
        };
        registerReceiver(usbReceiver, filter);

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
    }

    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().isEmpty()){
                tvLog.setText("No USB! Colok LGT8F via OTG dulu bos!");
                return;
            }
            UsbDevice dev=um.getDeviceList().values().iterator().next();
            if(!um.hasPermission(dev)){
                tvLog.setText("Minta permission OTG...\nPop-up ALLOW muncul, klik OK!");
                um.requestPermission(dev, permissionIntent);
                return;
            }
            conn=um.openDevice(dev);
            if(conn==null){
                tvLog.setText("Gagal open USB! Cabut colok lagi OTG + ALLOW!");
                return;
            }
            UsbInterface intf=dev.getInterface(0);
            conn.claimInterface(intf,true);
            for(int i=0;i<intf.getEndpointCount();i++){
                UsbEndpoint ep=intf.getEndpoint(i);
                if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                    if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep;
                    else epOut=ep;
                }
            }
            conn.controlTransfer(0x40,0xA1,0,0,null,0,1000);
            conn.controlTransfer(0x40,0xA4,0x50,0,null,0,1000);
            setDtrRts();
            tvStatus.setText("LIVE - V7.4");
            tvStatus.setTextColor(0xFF00FF00);
            btnConnect.setText("CONNECTED");
            isLive=true;
            startRead();
            tvLog.setText("CONNECTED 115200 DTR OFF RTS OFF\nMenunggu $OK,RPM,*\nKirim $GET*");
        }catch(Exception e){
            tvLog.setText("ERR: "+e.getMessage());
        }
    }

    void setDtrRts(){
        try{
            if(conn==null) return;
            int val=(dtrOn?1:0)|(rtsOn?2:0);
            conn.controlTransfer(0x40,0xA4,val,0,null,0,1000);
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
            byte[] buf=new byte[1024];
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
                if(line.startsWith("$OK,RPM,")){
                    String[] p=line.replace("*","").replace("\n","").replace("\r","").split(",");
                    if(p.length>=7){
                        int rpm=Integer.parseInt(p[2]);
                        int map=Integer.parseInt(p[4]);
                        int adv=Integer.parseInt(p[6]);
                        tvRpm.setText(String.valueOf(rpm));
                        tvMap.setText(map+"");
                        tvAdv.setText(adv+"°");
                        tvRowCol.setText("LIVE");
                    }
                }else{
                    tvLog.append("\n"+line.trim());
                }
            }catch(Exception e){}
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
