package com.taruna.grid;

import android.app.Activity;
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

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        try{
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

            btnDtr.setOnClickListener(v->{
                dtrOn=!dtrOn;
                btnDtr.setText(dtrOn?"DTR ON":"DTR OFF");
                setDtrRts();
                tvLog.append("\nDTR = "+(dtrOn?"ON (RESET!)":"OFF (AMAN)"));
            });
            btnRts.setOnClickListener(v->{
                rtsOn=!rtsOn;
                btnRts.setText(rtsOn?"RTS ON":"RTS OFF");
                setDtrRts();
            });

            btnGetMap.setOnClickListener(v->{
                if(conn==null){ Toast.makeText(this,"CONNECT dulu bos!",0).show(); return; }
                sendCmd("$GET*\n");
            });
            btnSaveMap.setOnClickListener(v->{
                if(conn==null){ Toast.makeText(this,"CONNECT dulu bos!",0).show(); return; }
                sendCmd("$SAVE*\n");
                Toast.makeText(this,"$SAVE* dikirim, LED kedip 3x = sukses",0).show();
            });
            btnConnect.setOnClickListener(v-> connect());
            
            tvLog.setText("V7.4 READY BOS\nFirmware: REAL DEG + OTG\nDTR OFF RTS OFF = ANTI-MATI\nColok OTG baru CONNECT");

        }catch(Exception e){
            // Anti-FC total
            Toast.makeText(this,"Error UI: "+e.getMessage(),1).show();
        }
    }

    void connect(){
        try{
            UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
            if(um.getDeviceList().isEmpty()){
                tvLog.setText("No USB Device!\nColok LGT8F via OTG!\nPastikan kabel OTG aktif!");
                return;
            }
            UsbDevice dev=um.getDeviceList().values().iterator().next();
            conn=um.openDevice(dev);
            if(conn==null){
                tvLog.setText("Gagal open USB! Kasih permission OTG dulu!");
                return;
            }
            // Cari interface yang ada bulk endpoint
            UsbInterface intf=null;
            for(int i=0;i<dev.getInterfaceCount();i++){
                UsbInterface tmp=dev.getInterface(i);
                for(int j=0;j<tmp.getEndpointCount();j++){
                    UsbEndpoint ep=tmp.getEndpoint(j);
                    if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                        intf=tmp;
                        break;
                    }
                }
                if(intf!=null) break;
            }
            if(intf==null) intf=dev.getInterface(0);
            conn.claimInterface(intf,true);

            for(int i=0;i<intf.getEndpointCount();i++){
                UsbEndpoint ep=intf.getEndpoint(i);
                if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                    if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep;
                    else epOut=ep;
                }
            }

            if(epIn==null || epOut==null){
                tvLog.setText("Endpoint null! LGT8F belum kebaca!\nCoba cabut colok lagi OTG!");
                return;
            }

            // Init CH340 115200
            conn.controlTransfer(0x40,0xA1,0,0,null,0,1000);
            conn.controlTransfer(0x40,0xA4,0x50,0,null,0,1000);
            setDtrRts();

            tvStatus.setText("LIVE - V7.4");
            tvStatus.setTextColor(0xFF00FF00);
            btnConnect.setText("CONNECTED");
            isLive=true;
            startRead();
            tvLog.setText("CONNECTED 115200\nDTR OFF RTS OFF (ANTI-MATI)\nMenunggu $OK,RPM,...*\nPencet GET MAP ($GET*)");

        }catch(Exception e){
            tvLog.setText("CONNECT ERROR: "+e.getMessage()+"\n"+e.toString());
            Toast.makeText(this,"FC Fix: "+e.getMessage(),1).show();
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
        }catch(Exception e){
            tvLog.append("\nSend Err: "+e.getMessage());
        }
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
                        if(tvRpm!=null) tvRpm.setText(String.valueOf(rpm));
                        if(tvMap!=null) tvMap.setText(map+"");
                        if(tvAdv!=null) tvAdv.setText(adv+"°");
                        if(tvRowCol!=null) tvRowCol.setText("LIVE");
                    }
                }else{
                    if(tvLog!=null) tvLog.append("\n"+line.trim());
                }
            }catch(Exception e){}
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        isLive=false;
        try{ if(conn!=null) conn.close(); }catch(Exception e){}
    }
                          }
