package com.cubebabytonehub;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String ACTION = "com.cubebabytonehub.USB_PERMISSION";
    private UsbManager usb;
    private TextView output;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION.equals(intent.getAction())) {
                boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);
                output.append(granted
                        ? "\nUSB permission granted."
                        : "\nUSB permission denied.");
                if (granted) scanUsb();
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        usb = (UsbManager) getSystemService(USB_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB");
        title.setTextSize(24);
        layout.addView(title);

        TextView intro = new TextView(this);
        intro.setText("\nUSB interface and endpoint scanner."
                + "\nConnect the pedal, then scan.");
        layout.addView(intro);

        Button scan = new Button(this);
        scan.setText("SCAN USB");
        layout.addView(scan);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setText("\nWaiting for scan.");
        output.setTextSize(14);
        output.setTextIsSelectable(true);
        scroll.addView(output);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        layout.addView(scroll, scrollParams);

        TextView note = new TextView(this);
        note.setText("\nScanner only reads USB descriptors. "
                + "It does not send commands or modify pedal presets.");
        layout.addView(note);

        setContentView(layout);
        scan.setOnClickListener(v -> scanUsb());

        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void scanUsb() {
        HashMap<String, UsbDevice> devices = usb.getDeviceList();

        if (devices.isEmpty()) {
            output.setText("No USB device detected.\n"
                    + "Check OTG support, cable, and pedal connection.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Found ").append(devices.size()).append(" USB device(s)\n");

        for (UsbDevice device : devices.values()) {
            text.append("\n================================\n");
            text.append("DEVICE: ").append(device.getDeviceName()).append("\n");
            text.append("VID: ").append(device.getVendorId())
                    .append(" (0x").append(hex4(device.getVendorId())).append(")\n");
            text.append("PID: ").append(device.getProductId())
                    .append(" (0x").append(hex4(device.getProductId())).append(")\n");
            text.append("Device class/subclass/protocol: ")
                    .append(hex2(device.getDeviceClass())).append(" / ")
                    .append(hex2(device.getDeviceSubclass())).append(" / ")
                    .append(hex2(device.getDeviceProtocol())).append("\n");
            text.append("Interfaces: ").append(device.getInterfaceCount()).append("\n");

            if (!usb.hasPermission(device)) {
                requestUsbPermission(device);
                text.append("USB permission: requesting...\n");
                continue;
            }

            text.append("USB permission: granted\n");

            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                text.append("\n--- INTERFACE ").append(i).append(" ---\n");
                text.append("Interface ID: ").append(intf.getId()).append("\n");
                text.append("Alternate setting: ").append(intf.getAlternateSetting()).append("\n");
                text.append("Class: ").append(hex2(intf.getInterfaceClass())).append("\n");
                text.append("Subclass: ").append(hex2(intf.getInterfaceSubclass())).append("\n");
                text.append("Protocol: ").append(hex2(intf.getInterfaceProtocol())).append("\n");
                text.append("Endpoints: ").append(intf.getEndpointCount()).append("\n");

                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    text.append("  Endpoint ").append(e)
                            .append(": address=").append(hex2(ep.getAddress()))
                            .append(", direction=").append(directionName(ep.getDirection()))
                            .append(", type=").append(typeName(ep.getType()))
                            .append(", maxPacketSize=").append(ep.getMaxPacketSize())
                            .append(", interval=").append(ep.getInterval())
                            .append("\n");
                }
            }
        }
        output.setText(text.toString());
    }

    private void requestUsbPermission(UsbDevice device) {
        Intent permissionIntent = new Intent(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        usb.requestPermission(device, pending);
    }

    private String directionName(int direction) {
        if (direction == UsbConstants.USB_DIR_IN) return "IN (device to phone)";
        if (direction == UsbConstants.USB_DIR_OUT) return "OUT (phone to device)";
        return "UNKNOWN";
    }

    private String typeName(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL: return "CONTROL";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "ISOCHRONOUS";
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "BULK";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "INTERRUPT";
            default: return "UNKNOWN";
        }
    }

    private String hex2(int value) {
        return String.format("%02X", value & 0xFF);
    }

    private String hex4(int value) {
        return String.format("%04X", value & 0xFFFF);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) { }
    }
                }
