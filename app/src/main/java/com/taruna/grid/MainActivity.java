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

public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    GridLayout gridMap;
    boolean dtrOn=false, rtsOn=false;
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
                        tvLog.setText("Permission OK! Auto CONNECT...");
                        runOnUiThread(new Runnable(){ public void run(){ connect(); } });
                    }else tvLog.setText("Permission ditolak!");
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_EXPORTED);
        else registerReceiver(usbReceiver,f);
        initMapData(); buildGrid();
        btnDtr.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ dtrOn=!dtrOn; btnDtr.setText(dtrOn?"DTR ON":"DTR OFF"); setDtrRts(); }});
        btnRts.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ rtsOn=!rtsOn; btnRts.setText(rtsOn?"RTS ON":"RTS OFF"); setDtrRts(); }});
        btnGetMap.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ sendCmd("$GET*
"); }});
        btnSaveMap.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ sendCmd("$SAVE*
"); }});
        btnConnect.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ connect(); }});
    }

    void initMapData(){ for(int r=0;r<16;r++) for(int c=0;c<16;c++) mapData[r][c]=9; }

    void buildGrid(){
        gridMap.removeAllViews();
        int W=95; int H=75;
        TextView corner=new TextView(this); corner.setText("R\M"); corner.setWidth(W); corner.setHeight(H); corner.setGravity(Gravity.CENTER); corner.setBackgroundColor(0xFF333333); corner.setTextColor(0xFFFFFFFF); corner.setTextSize(14); gridMap.addView(corner);
        for(int c=0;c<16;c++){ TextView h=new TextView(this); h.setText(String.valueOf(mapBins[c])); h.setWidth(W); h.setHeight(H); h.setGravity(Gravity.CENTER); h.setBackgroundColor(0xFF444444); h.setTextColor(0xFF00BFFF); h.setTextSize(13); gridMap.addView(h); }
        for(int r=0;r<16;r++){
            TextView rh=new TextView(this); rh.setText(String.valueOf(rpmBins[r])); rh.setWidth(W); rh.setHeight(H); rh.setGravity(Gravity.CENTER); rh.setBackgroundColor(0xFF444444); rh.setTextColor(0xFF00FF00); rh.setTextSize(12); gridMap.addView(rh);
            for(int c=0;c<16;c++){
                TextView cell=new TextView(this); cell.setText(String.valueOf(mapData[r][c])); cell.setWidth(W); cell.setHeight(H); cell.setGravity(Gravity.CENTER); cell.setBackgroundColor(0xFF111111); cell.setTextColor(0xFFFFFFFF); cell.setTextSize(18);
                final int rr=r; final int cc=c;
                cell.setOnClickListener(new android.view.View.OnClickListener(){ public void onClick(android.view.View v){ mapData[rr][cc]++; if(mapData[rr][cc]>45) mapData[rr][cc]=45; cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); }});
                cell.setOnLongClickListener(new android.view.View.OnLongClickListener(){ public boolean onLongClick(android.view.View v){ mapData[rr][cc]--; if(mapData[rr][cc]<0) mapData[rr][cc]=0; cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); return true; }});
                cellViews[r][c]=cell; gridMap.addView(cell);
            }
        }
    }

    void highlightCell(int rpm,int map){
        int rIdx=0; int cIdx=0;
        for(int i=0;i<15;i++) if(rpm>=rpmBins[i]) rIdx=i;
        for(int i=0;i<15;i++) if(map>=mapBins[i]) cIdx=i;
        if(rIdx==currentRpmBin && cIdx==currentMapBin) return;
        if(currentRpmBin>=0){ try{ cellViews[currentRpmBin][currentMapBin].setBackgroundColor(0xFF111111); cellViews[currentRpmBin][currentMapBin].setTextColor(0xFFFFFFFF); }catch(Exception e){} }
        currentRpmBin=rIdx; currentMapBin=cIdx;
        try{ cellViews[rIdx][cIdx].setBackgroundColor(0xFFFFFF00); cellViews[rIdx][cIdx].setTextColor(0xFF000000); tvRowCol.setText("LIVE ROW "+rIdx+" COL "+cIdx+" -> "+mapData[rIdx][cIdx]+"°"); }catch(Exception e){}
    }

    void sendSet(int r,int c,int val){ sendCmd("$SET,"+r+","+c+","+val+"*
"); }

    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().isEmpty()){ tvLog.setText("No USB!"); return; }
            UsbDevice dev=um.getDeviceList().values().iterator().next();
            if(!um.hasPermission(dev)){ um.requestPermission(dev,permissionIntent); return; }
            conn=um.openDevice(dev); if(conn==null) return;
            for(int i=0;i<dev.getInterfaceCount();i++) try{ conn.claimInterface(dev.getInterface(i),true); }catch(Exception e){}
            epIn=null; epOut=null;
            for(int i=0;i<dev.getInterfaceCount();i++){ UsbInterface intf=dev.getInterface(i); for(int j=0;j<intf.getEndpointCount();j++){ UsbEndpoint ep=intf.getEndpoint(j); if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){ if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep; else epOut=ep; } } }
            tvStatus.setText("LIVE - V7.4 JUMBO"); tvStatus.setTextColor(0xFF00FF00); btnConnect.setText("CONNECTED"); isLive=true; startRead(); tvLog.setText("CONNECTED JUMBO!"); sendCmd("$GET*
");
        }catch(Exception e){ tvLog.setText("ERR: "+e.getMessage()); }
    }

    void setDtrRts(){ try{ if(conn==null) return; int v=(dtrOn?1:0)|(rtsOn?2:0); conn.controlTransfer(0x21,0x22,v,0,null,0,1000); }catch(Exception e){} }
    void sendCmd(String s){ try{ if(conn!=null && epOut!=null) conn.bulkTransfer(epOut,s.getBytes(),s.length(),1000); }catch(Exception e){} }
    void startRead(){ new Thread(new Runnable(){ public void run(){ byte[] buf=new byte[4096]; StringBuilder sb=new StringBuilder(); while(isLive){ try{ int n=conn.bulkTransfer(epIn,buf,buf.length,1000); if(n>0){ String chunk=new String(buf,0,n); sb.append(chunk); String all=sb.toString(); while(all.contains("*")){ int idx=all.indexOf("*"); String line=all.substring(0,idx+1); all=all.substring(idx+1); parse(line); } sb=new StringBuilder(all); } }catch(Exception e){} } }}).start(); }

    void parse(String line){
        runOnUiThread(new Runnable(){
            public void run(){
                try{
                    String clean=line.trim().replace("
","").replace("
","");
                    if(clean.length()<5) return;
                    if(clean.startsWith("$OK,RPM,")){
                        String noStar=clean.replace("*","").replace("$OK,","").replace("$","");
                        String[] p=noStar.split(",");
                        int rpm=0; int map=0; int adv=0;
                        for(int i=0;i<p.length-1;i++){
                            if(p[i].equals("RPM")) rpm=Integer.parseInt(p[i+1]);
                            if(p[i].equals("MAP")) map=Integer.parseInt(p[i+1]);
                            if(p[i].equals("ADV")) adv=Integer.parseInt(p[i+1]);
                        }
                        tvRpm.setText(String.valueOf(rpm)); tvMap.setText(map+" kPa"); tvAdv.setText(adv+"°"); tvStatus.setText("LIVE - "+rpm+" RPM"); highlightCell(rpm,map);
                    }else if(clean.startsWith("$MAPR,")){
                        String[] p=clean.replace("*","").split(","); int row=Integer.parseInt(p[1]); for(int c=0;c<16 && c+2<p.length;c++){ mapData[row][c]=Integer.parseInt(p[c+2]); cellViews[row][c].setText(String.valueOf(mapData[row][c])); }
                    }
                }catch(Exception e){}
            }
        });
    }

    @Override protected void onDestroy(){ super.onDestroy(); isLive=false; try{ if(conn!=null) conn.close(); }catch(Exception e){} try{ unregisterReceiver(usbReceiver); }catch(Exception e){} }
}
                                                                          
