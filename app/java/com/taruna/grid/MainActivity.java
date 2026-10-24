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
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    GridLayout gridMap;
    boolean dtrOn=false, rtsOn=false; // FIX! DULU true BIKIN RESET TERUS!
    UsbDeviceConnection conn=null;
    UsbEndpoint epIn=null, epOut=null;
    boolean isLive=false;
    static final String ACTION_USB_PERMISSION = "com.taruna.grid.USB_PERMISSION";
    PendingIntent permissionIntent;
    BroadcastReceiver usbReceiver;
    int[][] mapData = new int[16][16];
    TextView[][] cellViews = new TextView[16][16];
    int[] rpmBins = {500,700,900,1100,1300,1500,1800,2200,2600,3000,3500,4000,4500,5000,5750,6500};
    int[] mapBins = {20,25,30,35,40,45,50,55,60,65,70,75,80,85,92,100};
    StringBuilder rxBuffer = new StringBuilder();
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
            gridMap=findViewById(R.id.grid_map);
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbReceiver = new BroadcastReceiver(){
                @Override public void onReceive(Context c, Intent i){
                    if(ACTION_USB_PERMISSION.equals(i.getAction())){
                        boolean granted=i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
                        if(granted){ if(tvLog!=null) tvLog.setText("Permission OK"); runOnUiThread(()-> connect()); } else if(tvLog!=null) tvLog.setText("Permission denied!");
                    }
                }
            };
            IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
            if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_EXPORTED); else registerReceiver(usbReceiver,f);
            initMapData(); buildGrid();
            if(btnDtr!=null) btnDtr.setOnClickListener(v->{ dtrOn=!dtrOn; btnDtr.setText(dtrOn?"DTR ON":"DTR OFF"); setDtrRts(); });
            if(btnRts!=null) btnRts.setOnClickListener(v->{ rtsOn=!rtsOn; btnRts.setText(rtsOn?"RTS ON":"RTS OFF"); setDtrRts(); });
            if(btnGetMap!=null) btnGetMap.setOnClickListener(v-> sendGet());
            if(btnSaveMap!=null) btnSaveMap.setOnClickListener(v-> sendSave());
            if(btnConnect!=null) btnConnect.setOnClickListener(v-> connect());
            if(btnDtr!=null) btnDtr.setText("DTR OFF");
            if(btnRts!=null) btnRts.setText("RTS OFF");
            if(tvLog!=null) tvLog.setText("FIX FC DTR OFF READY");
        }catch(Exception e){ Toast.makeText(this,"ONCREATE: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    void initMapData(){ for(int r=0;r<16;r++) for(int c=0;c<16;c++) mapData[r][c]=9; }
    void buildGrid(){
        try{
            if(gridMap==null) return;
            gridMap.removeAllViews();
            int W=68,H=58;
            TextView corner=new TextView(this); corner.setText("R/M"); corner.setWidth(W); corner.setHeight(H); corner.setGravity(17); corner.setBackgroundColor(0xFF333333); corner.setTextColor(0xFFFFFFFF); gridMap.addView(corner);
            for(int c=0;c<16;c++){ TextView h=new TextView(this); h.setText(String.valueOf(mapBins[c])); h.setWidth(W); h.setHeight(H); h.setGravity(17); h.setBackgroundColor(0xFF444444); h.setTextColor(0xFF00BFFF); gridMap.addView(h); }
            for(int r=0;r<16;r++){
                TextView rh=new TextView(this); rh.setText(String.valueOf(rpmBins[r])); rh.setWidth(W); rh.setHeight(H); rh.setGravity(17); rh.setBackgroundColor(0xFF444444); rh.setTextColor(0xFF00FF00); gridMap.addView(rh);
                for(int c=0;c<16;c++){
                    TextView cell=new TextView(this); cell.setText(String.valueOf(mapData[r][c])); cell.setWidth(W); cell.setHeight(H); cell.setGravity(17); cell.setBackgroundColor(0xFF111111); cell.setTextColor(0xFFFFFFFF); cell.setTextSize(20);
                    final int rr=r,cc=c;
                    cell.setOnClickListener(v->{ mapData[rr][cc]++; if(mapData[rr][cc]>45) mapData[rr][cc]=0; if(cellViews[rr][cc]!=null) cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); });
                    cell.setOnLongClickListener(v->{ mapData[rr][cc]--; if(mapData[rr][cc]<0) mapData[rr][cc]=45; if(cellViews[rr][cc]!=null) cellViews[rr][cc].setText(String.valueOf(mapData[rr][cc])); sendSet(rr,cc,mapData[rr][cc]); return true; });
                    cellViews[r][c]=cell; gridMap.addView(cell);
                }
            }
        }catch(Exception e){}
    }
    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().size()==0){ if(tvLog!=null) tvLog.setText("No USB!"); Toast.makeText(this,"No USB!",Toast.LENGTH_LONG).show(); return; }
            UsbDevice dev=null; for(UsbDevice d: um.getDeviceList().values()){ dev=d; break; } if(dev==null) return;
            if(!um.hasPermission(dev)){ um.requestPermission(dev,permissionIntent); if(tvLog!=null) tvLog.setText("Minta permission..."); return; }
            conn=um.openDevice(dev); if(conn==null){ if(tvLog!=null) tvLog.setText("Gagal open"); return; }
            for(int i=0;i<dev.getInterfaceCount();i++){ try{ conn.claimInterface(dev.getInterface(i),true); }catch(Exception e){} }
            try{ byte[] lc=new byte[]{0x00,(byte)0xC2,0x01,0x00,0x00,0x00,0x08}; conn.controlTransfer(0x21,0x20,0,0,lc,lc.length,1000); }catch(Exception e){}
            try{ dtrOn=false; rtsOn=false; conn.controlTransfer(0x21,0x22,0,0,null,0,1000); if(btnDtr!=null) btnDtr.setText("DTR OFF"); if(btnRts!=null) btnRts.setText("RTS OFF"); }catch(Exception e){}
            epIn=null; epOut=null;
            for(int i=0;i<dev.getInterfaceCount();i++){ UsbInterface intf=dev.getInterface(i); for(int j=0;j<intf.getEndpointCount();j++){ UsbEndpoint ep=intf.getEndpoint(j); if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){ if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep; else epOut=ep; } } }
            if(epIn==null||epOut==null){ if(tvLog!=null) tvLog.setText("Endpoint null"); return; }
            if(tvStatus!=null){ tvStatus.setText("CONNECTED DTR OFF"); tvStatus.setTextColor(0xFF00FF00); }
            if(btnConnect!=null) btnConnect.setText("CONNECTED");
            isLive=true; rxBuffer.setLength(0); startRead();
            if(tvLog!=null) tvLog.setText("CONNECTED");
        }catch(Exception e){ if(tvLog!=null) tvLog.setText("ERR: "+e.getMessage()); }
    }
    void setDtrRts(){ try{ if(conn==null) return; int v=(dtrOn?1:0)|(rtsOn?2:0); conn.controlTransfer(0x21,0x22,v,0,null,0,500); if(tvLog!=null) tvLog.setText("DTR="+(dtrOn?"ON":"OFF")+" RTS="+(rtsOn?"ON":"OFF")); }catch(Exception e){} }
    void sendCmd(String s){ try{ if(conn==null||epOut==null){ if(tvLog!=null) tvLog.setText("Not connected"); return; } byte[] d=s.getBytes(); conn.bulkTransfer(epOut,d,d.length,1000); if(tvLog!=null) tvLog.setText("Kirim "+s.trim()); }catch(Exception e){} }
    void sendGet(){ Toast.makeText(this,"GET MAP",Toast.LENGTH_SHORT).show(); sendCmd("$GET*\n"); new android.os.Handler().postDelayed(()->sendCmd("$GET*\n"),500); }
    void sendSave(){ Toast.makeText(this,"SAVE",Toast.LENGTH_SHORT).show(); sendCmd("$SAVE*\n"); }
    void sendSet(int r,int c,int val){ if(tvRowCol!=null) tvRowCol.setText("SET R"+r+" C"+c+"="+val); sendCmd("$SET,"+r+","+c+","+val+"*\n"); }
    void startRead(){ new Thread(()->{ byte[] buf=new byte[4096]; while(isLive){ try{ int n=conn.bulkTransfer(epIn,buf,buf.length,1000); if(n>0){ String chunk=new String(buf,0,n); rxBuffer.append(chunk); String all=rxBuffer.toString(); while(all.contains("*")){ int idx=all.indexOf("*"); String line=all.substring(0,idx+1); all=all.substring(idx+1); parse(line); } rxBuffer=new StringBuilder(all); } }catch(Exception e){ try{ Thread.sleep(10); }catch(Exception e2){} } } }).start(); }
    void parse(String line){ runOnUiThread(()->{ try{ if(line==null) return; String clean=line.trim().replace("\r","").replace("\n",""); int idx=clean.indexOf("$"); if(idx>=0) clean=clean.substring(idx); if(clean.length()<5) return; if(clean.contains("$OK,RPM,")){ String noStar=clean.replace("*","").replace("$OK,","").replace("$",""); String[] p=noStar.split(","); int rpm=0,map=0,adv=0; for(int i=0;i<p.length-1;i++){ if(p[i].equals("RPM")) try{ rpm=Integer.parseInt(p[i+1]); }catch(Exception e){} if(p[i].equals("MAP")) try{ map=Integer.parseInt(p[i+1]); }catch(Exception e){} if(p[i].equals("ADV")) try{ adv=Integer.parseInt(p[i+1]); }catch(Exception e){} } if(tvRpm!=null) tvRpm.setText(String.valueOf(rpm)); if(tvMap!=null) tvMap.setText(map+" kPa"); if(tvAdv!=null) tvAdv.setText(adv+" deg"); if(tvStatus!=null) tvStatus.setText("LIVE "+rpm+" RPM"); }else if(clean.contains("$MAPR,")){ try{ String c2=clean.substring(clean.indexOf("$MAPR,")); String[] p=c2.replace("*","").split(","); int row=Integer.parseInt(p[1]); for(int c=0;c<16&&c+2<p.length;c++){ try{ int v=Integer.parseInt(p[c+2].trim()); if(v<0)v=0; if(v>45)v=45; mapData[row][c]=v; if(cellViews[row][c]!=null) cellViews[row][c].setText(String.valueOf(v)); }catch(Exception e){} } if(tvLog!=null) tvLog.setText("MAP ROW "+row+" OK"); }catch(Exception e){} }else if(clean.contains("$MAPDONE")){ Toast.makeText(this,"MAP DONE",Toast.LENGTH_LONG).show(); if(tvLog!=null) tvLog.setText("GET DONE"); }else if(clean.contains("SAVE_DONE")||clean.contains("$OK,SAVE")){ Toast.makeText(this,"SAVE OK",Toast.LENGTH_LONG).show(); if(tvLog!=null) tvLog.setText("SAVE DONE"); } }catch(Exception e){} }); }
    @Override protected void onDestroy(){ super.onDestroy(); isLive=false; try{ if(conn!=null) conn.close(); }catch(Exception e){} try{ unregisterReceiver(usbReceiver); }catch(Exception e){} }
}
