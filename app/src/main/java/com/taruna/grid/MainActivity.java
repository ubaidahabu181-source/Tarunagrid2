package com.taruna.grid;

import android.app.Activity;
import android.hardware.usb.*;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    boolean dtrOn=false, rtsOn=false;
    UsbDeviceConnection conn;
    UsbEndpoint epIn, epOut;
    boolean isLive=false;

    int[][] mapData = new int[16][16];
    int[] RPM_BINS = {500,700,900,1100,1300,1500,1800,2200,2600,3000,3500,4000,4500,5000,5750,6500};
    int[] MAP_BINS = {20,25,30,35,40,45,50,55,60,65,70,75,80,85,92,100};

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
        UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
        if(um.getDeviceList().isEmpty()){
            tvLog.setText("No USB! Colok LGT8F via OTG!");
            return;
        }
        UsbDevice dev=um.getDeviceList().values().iterator().next();
        conn=um.openDevice(dev);
        UsbInterface intf=dev.getInterface(0);
        conn.claimInterface(intf,true);
        for(int i=0;i<intf.getEndpointCount();i++){
            UsbEndpoint ep=intf.getEndpoint(i);
            if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep;
                else epOut=ep;
            }
        }
        // Init CH340 LGT8F 115200 - TANPA LIBRARY!
        conn.controlTransfer(0x40,0xA1,0,0,null,0,0);
        conn.controlTransfer(0x40,0xA4,0x50,0,null,0,0);
        setDtrRts();
        tvStatus.setText("LIVE - V7.4");
        tvStatus.setTextColor(0xFF00FF00);
        btnConnect.setText("CONNECTED");
        isLive=true;
        startRead();
        tvLog.setText("Connected LGT8F 115200 DTR OFF RTS OFF\nMenunggu $OK,RPM,...*\nKirim $GET* buat MAP");
    }

    void setDtrRts(){
        if(conn==null) return;
        int val=(dtrOn?1:0)|(rtsOn?2:0);
        conn.controlTransfer(0x40,0xA4,val,0,null,0,0);
    }

    void sendCmd(String s){
        if(conn!=null && epOut!=null){
            conn.bulkTransfer(epOut,s.getBytes(),s.length(),1000);
            tvLog.append("\n> "+s);
        }
    }

    void startRead(){
        new Thread(()->{
            byte[] buf=new byte[1024];
            StringBuilder sb=new StringBuilder();
            while(isLive){
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
            }
        }).start();
    }

    void parse(String line){
        runOnUiThread(()->{
            if(line.startsWith("$OK,RPM,")){
                try{
                    // $OK,RPM,1250,MAP,65,ADV,12*
                    String[] p=line.replace("*","").split(",");
                    int rpm=Integer.parseInt(p[2]);
                    int map=Integer.parseInt(p[4]);
                    int adv=Integer.parseInt(p[6]);
                    tvRpm.setText(String.valueOf(rpm));
                    tvMap.setText(map+" kPa");
                    tvAdv.setText(adv+"°");
                    int r=findIdx(map,MAP_BINS);
                    int c=findIdx(rpm,RPM_BINS);
                    tvRowCol.setText("ROW "+r+" COL "+c+" IDLE");
                    tvLog.append("\n"+rpm+" RPM MAP "+map+" ADV "+adv);
                }catch(Exception e){}
            }else if(line.startsWith("$MAPR,")){
                tvLog.append("\n"+line);
            }else if(line.startsWith("$OK,SET")||line.startsWith("$OK,SAVE")||line.startsWith("$MAPDONE")){
                tvLog.append("\n"+line);
            }
        });
    }

    int findIdx(int v,int[] bins){
        for(int i=0;i<bins.length-1;i++) if(v>=bins[i] && v<bins[i+1]) return i;
        return bins.length-1;
    }
            }
