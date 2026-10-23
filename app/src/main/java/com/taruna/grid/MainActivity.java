// Tambah di atas class
private static final String ACTION_USB_PERMISSION = "com.taruna.grid.USB_PERMISSION";
PendingIntent permissionIntent;
BroadcastReceiver usbReceiver;

// Di onCreate paling atas tambah:
permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
usbReceiver = new BroadcastReceiver(){
    public void onReceive(Context context, Intent intent){
        String action = intent.getAction();
        if(ACTION_USB_PERMISSION.equals(action)){
            synchronized(this){
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                    if(device!=null){
                        tvLog.setText("Permission OK! CONNECT lagi bos!");
                        // auto connect lagi
                        connectAfterPermission(device);
                    }
                }else{
                    tvLog.setText("Permission ditolak! Cabut OTG colok lagi + Allow!");
                }
            }
        }
    }
};
registerReceiver(usbReceiver, filter);

// Ganti fungsi connect jadi:
void connect(){
    try{
        UsbManager um=(UsbManager)getSystemService(USB_SERVICE);
        if(um.getDeviceList().isEmpty()){
            tvLog.setText("No USB Device! Colok LGT8F via OTG!");
            return;
        }
        UsbDevice dev=um.getDeviceList().values().iterator().next();
        if(!um.hasPermission(dev)){
            tvLog.setText("Minta permission OTG...\nPop-up Allow akan muncul!\nKlik OK / Allow!");
            um.requestPermission(dev, permissionIntent);
            return;
        }
        // kalau sudah ada permission lanjut...
        connectAfterPermission(dev);
    }catch(Exception e){ tvLog.setText("Err: "+e.getMessage()); }
}

void connectAfterPermission(UsbDevice dev){
    // pindahkan semua isi connect lama kesini
    // dari conn = um.openDevice(dev); sampai startRead()
    }
