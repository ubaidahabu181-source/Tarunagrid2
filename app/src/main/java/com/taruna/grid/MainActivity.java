package com.taruna.grid;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    TextView tvLog, tvStatus, tvRpm, tvMap, tvAdv, tvRowCol;
    Button btnDtr, btnRts, btnConnect, btnGetMap, btnSaveMap;
    boolean dtrOn=false, rtsOn=false;

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

            tvStatus.setText("READY - ANTI FC");
            tvLog.setText("V7.4 ANTI-FC MODE BOS\n\nIni versi kebal FC!\nGa ada USB permission,\nga ada receiver, ga ada bulkTransfer!\n\nCuma UI doang biar ga mental!\n\nKalau ini ga FC, berarti yang bikin FC kemarin adalah kode USB nya!\n\nTombol DTR/RTS cuma simulasi dulu ya bos!\nNanti kalau ini lancar, baru kita masukin USB pelan-pelan!");

            if(btnDtr!=null) btnDtr.setOnClickListener(v->{
                try{
                    dtrOn=!dtrOn;
                    btnDtr.setText(dtrOn?"DTR ON":"DTR OFF");
                    tvLog.append("\nDTR = "+(dtrOn?"ON (BAHAYA RESET!)":"OFF (AMAN ANTI-MATI)"));
                    Toast.makeText(this, "DTR "+(dtrOn?"ON":"OFF"), Toast.LENGTH_SHORT).show();
                }catch(Exception e){}
            });

            if(btnRts!=null) btnRts.setOnClickListener(v->{
                try{
                    rtsOn=!rtsOn;
                    btnRts.setText(rtsOn?"RTS ON":"RTS OFF");
                    tvLog.append("\nRTS = "+(rtsOn?"ON":"OFF"));
                }catch(Exception e){}
            });

            if(btnConnect!=null) btnConnect.setOnClickListener(v->{
                try{
                    tvStatus.setText("SIMULASI LIVE");
                    tvStatus.setTextColor(0xFF00FF00);
                    tvRpm.setText("1250");
                    tvMap.setText("65 kPa");
                    tvAdv.setText("12°");
                    tvRowCol.setText("ROW 5 COL 8");
                    tvLog.append("\n\n[SIMULASI] $OK,RPM,1250,MAP,65,ADV,12*");
                    tvLog.append("\nFirmware V7.4 REAL DEG OK!");
                    Toast.makeText(this, "SIMULASI OK - Ga FC kan bos?!", Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            if(btnGetMap!=null) btnGetMap.setOnClickListener(v->{
                try{
                    tvLog.append("\n> $GET* (simulasi)");
                    tvLog.append("\n$MAPR,0,16,16,19,25,28,30,32,33,34,33,32,30,29,27,25,24*");
                    Toast.makeText(this, "GET MAP simulasi", Toast.LENGTH_SHORT).show();
                }catch(Exception e){}
            });

            if(btnSaveMap!=null) btnSaveMap.setOnClickListener(v->{
                try{
                    tvLog.append("\n> $SAVE* (simulasi)");
                    tvLog.append("\n$OK,SAVE_DONE* LED kedip 3x!");
                }catch(Exception e){}
            });

        }catch(Exception e){
            // Ini yang bikin ga FC! Semua error ditangkap!
            try{
                Toast.makeText(this, "UI Error tapi ga FC: "+e.getMessage(), Toast.LENGTH_LONG).show();
            }catch(Exception e2){}
        }
    }
}
