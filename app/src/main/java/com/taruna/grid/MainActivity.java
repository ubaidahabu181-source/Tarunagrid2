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
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    GridLayout gridMap;
    boolean dtrOn=false, rtsOn=true; // DTR OFF = tidak reset mesin - SimpleUsbTerminal hijau!
    UsbDeviceConnection conn=null;
    UsbEndpoint epIn=null, epOut=null;
    boolean isLive=false;
    static final String ACTION_USB_PERMISSION = "com.taruna.grid.USB_PERMISSION";
    PendingIntent permissionIntent;
    BroadcastReceiver usbReceiver;
    int[][] mapData = new int[16][16];
    TextView[][] cellViews = new TextView[16][16];
    int currentRpmBin=-1, currentMapBin=-1;
    int[] rpmBins = {500,700,900,1100,1300,1500,1800,2200,2600,3000,3500,4000,4500,5000,5750,6500};
    int[] mapBins = {20,25,30,35,40,45,50,55,60,65,70,75,80,85,92,100};
    StringBuilder rxBuffer = new StringBuilder();

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
        gridMap=findViewById(R.id.grid_map);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbReceiver = new BroadcastReceiver(){
            @Override public void onReceive(Context c, Intent i){
                if(ACTION_USB_PERMISSION.equals(i.getAction())){
                    if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                        tvLog.setText("Permission OK, connect...");
                        runOnUiThread(new Runnable(){ public void run(){ connect(); } });
                    }else tvLog.setText("Permission denied!");
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_EXPORTED);
        else registerReceiver(usbReceiver,f);
        initMapData(); buildGrid();
        btnDtr.setOnClickListener(v->{ dtrOn=!dtrOn; btnDtr.setText(dtrOn?"DTR ON":"DTR OFF"); setDtrRts(); });
        btnRts.setOnClickListener(v->{ rtsOn=!rtsOn; btnRts.setText(rtsOn?"RTS ON":"RTS OFF"); setDtrRts(); });
        btnGetMap.setOnClickListener(v-> sendGet());
        btnSaveMap.setOnClickListener(v-> sendSave());
        btnConnect.setOnClickListener(v-> connect());
        btnDtr.setText("DTR OFF"); btnRts.setText("RTS ON");
        btnGetMap.setEnabled(true); btnSaveMap.setEnabled(true);
        btnGetMap.setAlpha(1f); btnSaveMap.setAlpha(1f);
    }

    void initMapData(){ for(int r=0;r<16;r++) for(int c=0;c<16;c++) mapData[r][c]=9; }

    void buildGrid(){
        gridMap.removeAllViews();
        int W=62; int H=52;
        TextView corner=new TextView(this); corner.setText("R/M"); corner.setWidth(W); corner.setHeight(H); corner.setGravity(Gravity.CENTER); corner.setBackgroundColor(0xFF333333); corner.setTextColor(0xFFFFFFFF); corner.setTextSize(14); gridMap.addView(corner);
        for(int c=0;c<16;c++){ TextView h=new TextView(this); h.setText(String.valueOf(mapBins[c])); h.setWidth(W); h.setHeight(H); h.setGravity(Gravity.CENTER); h.setBackgroundColor(0xFF444444); h.setTextColor(0xFF00BFFF); h.setTextSize(13); gridMap.addView(h); }
        for(int r=0;r<16;r++){
            TextView rh=new TextView(this); rh.setText(String.valueOf(rpmBins[r])); rh.setWidth(W); rh.setHeight(H); rh.setGravity(Gravity.CENTER); rh.setBackgroundColor(0xFF444444); rh.setTextColor(0xFF00FF00); rh.setTextSize(12); gridMap.addView(rh);
            for(int c=0;c<16;c++){
                TextView cell=new TextView(this); cell.setText(String.valueOf(mapData[r][c])); cell.setWidth(W); cell.setHeight(H); cell.setGravity(Gravity.CENTER); cell.setBackgroundColor(0xFF111111); cell.setTextColor(0xFFFFFFFF); cell.setTextSize(18);
                final int rr=r; final int cc=c;
                cell.setOnClickListener(v->{ mapData[rr][cc]++; if(mapData[rr][cc]>45) mapData[rr][cc]=0; cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); });
                cell.setOnLongClickListener(v->{ mapData[rr][cc]--; if(mapData[rr][cc]<0) mapData[rr][cc]=45; cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); return true; });
                cellViews[r][c]=cell; gridMap.addView(cell);
            }
        }
    }

    void highlightCell(int rpm,int map){
        int rIdx=0; int cIdx=0;
        for(int i=0;i<16;i++){ if(rpm>=rpmBins[i]) rIdx=i; }
        for(int i=0;i<16;i++){ if(map>=mapBins[i]) cIdx=i; }
        if(rIdx==currentRpmBin && cIdx==currentMapBin) return;
        if(currentRpmBin>=0){ try{ cellViews[currentRpmBin][currentMapBin].setBackgroundColor(0xFF111111); cellViews[currentRpmBin][currentMapBin].setTextColor(0xFFFFFFFF); }catch(Exception e){} }
        currentRpmBin=rIdx; currentMapBin=cIdx;
        try{ 
            cellViews[rIdx][cIdx].setBackgroundColor(0xFFFFFF00); 
            cellViews[rIdx][cIdx].setTextColor(0xFF000000); 
            tvRowCol.setText("LIVE ROW "+rIdx+" ("+rpmBins[rIdx]+") COL "+cIdx+" ("+mapBins[cIdx]+") = "+mapData[rIdx][cIdx]+" deg");
        }catch(Exception e){}
    }

    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().size()==0){
                tvLog.setText("No USB! Aktifkan OTG + ganti kabel");
                Toast.makeText(this,"No USB device! OTG ON?",Toast.LENGTH_LONG).show();
                return;
            }
            UsbDevice dev=null;
            for(UsbDevice d: um.getDeviceList().values()){
                dev=d; break;
            }
            if(dev==null) return;
            if(!um.hasPermission(dev)){
                um.requestPermission(dev,permissionIntent);
                tvLog.setText("Minta permission USB...");
                return;
            }
            conn=um.openDevice(dev);
            if(conn==null){ tvLog.setText("Gagal open device"); return; }
            for(int i=0;i<dev.getInterfaceCount();i++){
                UsbInterface intf=dev.getInterface(i);
                try{ conn.claimInterface(intf,true); }catch(Exception e){}
            }
            try{
                byte[] lineCoding = new byte[]{ (byte)0x00, (byte)0xC2, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08 };
                conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.length, 1000);
            }catch(Exception e){}
            // DTR OFF RTS ON = 2 - TIDAK RESET MESIN - SimpleUsbTerminal hijau!
            try{
                dtrOn=false; rtsOn=true;
                conn.controlTransfer(0x21, 0x22, 2, 0, null, 0, 1000);
                btnDtr.setText("DTR OFF"); btnRts.setText("RTS ON");
            }catch(Exception e){}
            epIn=null; epOut=null;
            for(int i=0;i<dev.getInterfaceCount();i++){
                UsbInterface intf=dev.getInterface(i);
                for(int j=0;j<intf.getEndpointCount();j++){
                    UsbEndpoint ep=intf.getEndpoint(j);
                    if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                        if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep;
                        else epOut=ep;
                    }
                }
            }
            if(epIn==null || epOut==null){
                tvLog.setText("Endpoint null! Cabut colok OTG");
                return;
            }
            tvStatus.setText("CONNECTED DTR OFF RTS ON - NO RESET"); tvStatus.setTextColor(0xFF00FF00);
            btnConnect.setText("CONNECTED"); btnGetMap.setEnabled(true); btnSaveMap.setEnabled(true);
            btnGetMap.setAlpha(1f); btnSaveMap.setAlpha(1f);
            isLive=true; rxBuffer.setLength(0);
            startRead();
            tvLog.setText("CONNECTED - SimpleUsbTerminal mode DTR OFF tidak reset mesin\nKlik GET MAP untuk load 16x16\n");
        }catch(Exception e){ tvLog.setText("ERR connect: "+e.getMessage()); }
    }

    void setDtrRts(){
        try{
            if(conn==null) return;
            int v=(dtrOn?1:0)|(rtsOn?2:0);
            conn.controlTransfer(0x21,0x22,v,0,null,0,500);
            tvLog.setText("Set DTR="+(dtrOn?"ON":"OFF")+" RTS="+(rtsOn?"ON":"OFF")+" -> "+v);
        }catch(Exception e){ tvLog.setText("DTR/RTS ERR "+e.getMessage()); }
    }

    void sendCmd(String s){
        try{
            if(conn==null || epOut==null){ tvLog.setText("Not connected!"); return; }
            byte[] data=s.getBytes();
            int r=conn.bulkTransfer(epOut,data,data.length,1000);
            tvLog.setText("Kirim "+s.trim()+" -> "+r+" bytes");
        }catch(Exception e){ tvLog.setText("SEND ERR "+e.getMessage()); }
    }

    void sendGet(){
        Toast.makeText(this,"GET MAP $GET*",Toast.LENGTH_SHORT).show();
        sendCmd("$GET*\n");
        new android.os.Handler().postDelayed(()-> sendCmd("$GET*\n"), 600);
    }
    void sendSave(){
        Toast.makeText(this,"SAVE $SAVE*",Toast.LENGTH_SHORT).show();
        sendCmd("$SAVE*\n");
    }
    void sendSet(int r,int c,int val){
        tvRowCol.setText("SET R"+r+" C"+c+" = "+val+" deg");
        sendCmd("$SET,"+r+","+c+","+val+"*\n");
    }

    void startRead(){
        new Thread(()->{
            byte[] buf=new byte[4096];
            while(isLive){
                try{
                    int n=conn.bulkTransfer(epIn,buf,buf.length,1000);
                    if(n>0){
                        String chunk=new String(buf,0,n);
                        rxBuffer.append(chunk);
                        String all=rxBuffer.toString();
                        while(all.contains("*")){
                            int idx=all.indexOf("*");
                            String line=all.substring(0,idx+1);
                            all=all.substring(idx+1);
                            parse(line);
                        }
                        rxBuffer=new StringBuilder(all);
                    }
                }catch(Exception e){
                    try{ Thread.sleep(10); }catch(Exception e2){}
                }
            }
        }).start();
    }

    void parse(String line){
        runOnUiThread(()->{
            try{
                if(line==null) return;
                String clean=line.trim().replace("\r","").replace("\n","");
                int idxDollar=clean.indexOf("$");
                if(idxDollar>=0) clean=clean.substring(idxDollar);
                if(clean.length()<5) return;
                if(clean.contains("$OK,RPM,")){
                    String noStar=clean.replace("*","").replace("$OK,","").replace("$","");
                    String[] p=noStar.split(",");
                    int rpm=0, map=0, adv=0;
                    for(int i=0;i<p.length-1;i++){
                        if(p[i].equals("RPM")) try{ rpm=Integer.parseInt(p[i+1]); }catch(Exception e){}
                        if(p[i].equals("MAP")) try{ map=Integer.parseInt(p[i+1]); }catch(Exception e){}
                        if(p[i].equals("ADV")) try{ adv=Integer.parseInt(p[i+1]); }catch(Exception e){}
                    }
                    tvRpm.setText(String.valueOf(rpm)); tvMap.setText(map+" kPa"); tvAdv.setText(adv+" deg"); tvStatus.setText("LIVE "+rpm+" RPM"); 
                    highlightCell(rpm,map);
                }else if(clean.contains("$MAPR,")){
                    try{
                        String c2=clean.substring(clean.indexOf("$MAPR,"));
                        String[] p=c2.replace("*","").split(",");
                        int row=Integer.parseInt(p[1]);
                        StringBuilder logB=new StringBuilder();
                        for(int c=0;c<16 && c+2<p.length;c++){
                            try{
                                int v=Integer.parseInt(p[c+2].trim());
                                if(v<0) v=0; if(v>45) v=45;
                                mapData[row][c]=v;
                                if(row>=0 && row<16 && c>=0 && c<16){
                                    cellViews[row][c].setText(String.valueOf(v));
                                    cellViews[row][c].setBackgroundColor(0xFF003300);
                                }
                                logB.append(v).append(",");
                            }catch(Exception e){}
                        }
                        tvLog.setText("MAP ROW "+row+" OK: "+logB.toString());
                        if(row==15){
                            Toast.makeText(MainActivity.this,"GET MAP DONE 16 ROW!",Toast.LENGTH_LONG).show();
                            for(int r=0;r<16;r++) for(int cc=0;cc<16;cc++) try{ cellViews[r][cc].setBackgroundColor(0xFF111111); }catch(Exception e){}
                            highlightCell(800,15);
                        }
                    }catch(Exception e){ tvLog.setText("MAPR ERR "+e.getMessage()+" "+clean); }
                }else if(clean.contains("$MAPDONE")){
                    Toast.makeText(MainActivity.this,"MAP DONE 16x16!",Toast.LENGTH_LONG).show();
                    tvLog.setText("GET MAP DONE - Grid penuh!");
                }else if(clean.contains("SAVE_DONE") || clean.contains("$OK,SAVE")){
                    Toast.makeText(MainActivity.this,"SAVE OK! LED kedip 3x",Toast.LENGTH_LONG).show();
                    tvLog.setText("SAVE DONE EEPROM!");
                }else if(clean.contains("V7.4")){
                    tvStatus.setText(clean.replace("*",""));
                    tvLog.setText(clean);
                }
            }catch(Exception e){ tvLog.setText("PARSE ERR "+e.getMessage()); }
        });
    }

    @Override protected void onDestroy(){ super.onDestroy(); isLive=false; try{ if(conn!=null) conn.close(); }catch(Exception e){} try{ unregisterReceiver(usbReceiver); }catch(Exception e){} }
                     }
