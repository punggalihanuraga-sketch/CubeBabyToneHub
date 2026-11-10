package com.cubebabytonehub;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String ACTION =
            "com.cubebabytonehub.USB_PERMISSION";

    private UsbManager usb;
    private TextView output;

    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION.equals(intent.getAction())) {
                boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);

                output.append(granted
                        ? "\nUSB permission granted."
                        : "\nUSB permission denied.");
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        usb = (UsbManager) getSystemService(USB_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        int pad = (int) (18 * getResources()
                .getDisplayMetrics().density);

        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB");
        title.setTextSize(24);
        layout.addView(title);

        TextView intro = new TextView(this);
        intro.setText("\nUSB OTG connection test."
                + "\nConnect the pedal, then scan.");
        layout.addView(intro);

        Button scan = new Button(this);
        scan.setText("SCAN USB");
        layout.addView(scan);

        output = new TextView(this);
        output.setText("\nWaiting for scan.");
        output.setTextSize(16);
        layout.addView(output);

        TextView note = new TextView(this);
        note.setText("\nStarter version: USB detection "
                + "and permission only.");
        layout.addView(note);

        setContentView(layout);

        scan.setOnClickListener(v -> scanUsb());

        IntentFilter filter = new IntentFilter(ACTION);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void scanUsb() {

        HashMap<String, UsbDevice> devices =
                usb.getDeviceList();

        if (devices.isEmpty()) {
            output.setText(
                    "No USB device detected.\n"
                    + "Check OTG support, cable, "
                    + "and pedal connection.");
            return;
        }

        StringBuilder text = new StringBuilder(
                "Found " + devices.size()
                + " USB device(s):\n");

        for (UsbDevice device : devices.values()) {

            text.append("\nDevice: ")
                    .append(device.getDeviceName());

            text.append("\nVendor ID: ")
                    .append(device.getVendorId());

            text.append("\nProduct ID: ")
                    .append(device.getProductId());

            text.append("\nInterfaces: ")
                    .append(device.getInterfaceCount())
                    .append("\n");

            if (!usb.hasPermission(device)) {

                Intent permissionIntent = new Intent(ACTION);

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;

                if (Build.VERSION.SDK_INT >= 23) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pending =
                        PendingIntent.getBroadcast(
                                this,
                                0,
                                permissionIntent,
                                flags);

                usb.requestPermission(device, pending);

                text.append("Requesting USB permission...\n");

            } else {
                text.append("USB permission already granted.\n");
            }
        }

        output.setText(text.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }
              }
