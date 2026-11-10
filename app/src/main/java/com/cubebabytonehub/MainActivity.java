package com.cubebabytonehub;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION = "com.cubebabytonehub.USB_PERMISSION";
    private UsbManager usb;
    private MidiManager midi;
    private TextView output;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION.equals(intent.getAction())) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                output.append(granted ? "\nUSB permission granted." : "\nUSB permission denied.");
                if (granted) scanUsb();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        midi = (MidiManager) getSystemService(MIDI_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB — STEP 12");
        title.setTextSize(21);
        layout.addView(title);

        TextView intro = new TextView(this);
        intro.setText("\nUSB + Android MIDI detection. Connect the pedal using OTG.");
        layout.addView(intro);

        Button scanUsbButton = new Button(this);
        scanUsbButton.setText("SCAN USB INTERFACES");
        layout.addView(scanUsbButton);

        Button scanMidiButton = new Button(this);
        scanMidiButton.setText("SCAN ANDROID MIDI DEVICES");
        layout.addView(scanMidiButton);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setText("\nReady. Connect CUBE BABY, then scan.");
        output.setTextSize(14);
        output.setTextIsSelectable(true);
        scroll.addView(output);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView note = new TextView(this);
        note.setText("\nStep 12 only checks USB descriptors and Android MIDI recognition. " +
                "It does not send MIDI messages, read/write presets, or change pedal settings.");
        layout.addView(note);
        setContentView(layout);

        scanUsbButton.setOnClickListener(v -> scanUsb());
        scanMidiButton.setOnClickListener(v -> scanAndroidMidi());

        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    private void scanUsb() {
        HashMap<String, UsbDevice> devices = usb.getDeviceList();
        if (devices.isEmpty()) {
            output.setText("USB SCAN\nNo USB device detected. Check OTG cable and connection.");
            return;
        }
        StringBuilder text = new StringBuilder("USB SCAN\nFound ").append(devices.size()).append(" device(s)\n");
        for (UsbDevice device : devices.values()) {
            text.append("\n================================\nDEVICE: ").append(device.getDeviceName())
                .append("\nVID: 0x").append(hex4(device.getVendorId()))
                .append("\nPID: 0x").append(hex4(device.getProductId()))
                .append("\nInterfaces: ").append(device.getInterfaceCount()).append("\n");
            if (!usb.hasPermission(device)) {
                requestUsbPermission(device);
                text.append("USB permission: requesting...\n");
                continue;
            }
            text.append("USB permission: granted\n");
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                int cls = intf.getInterfaceClass();
                int sub = intf.getInterfaceSubclass();
                text.append("\nInterface ").append(i)
                    .append(" | ID ").append(intf.getId())
                    .append(" | Alt ").append(intf.getAlternateSetting())
                    .append("\nClass/Subclass/Protocol: 0x").append(hex2(cls)).append(" / 0x")
                    .append(hex2(sub)).append(" / 0x").append(hex2(intf.getInterfaceProtocol()))
                    .append("\nEndpoints: ").append(intf.getEndpointCount());
                if (cls == 0x01 && sub == 0x03) text.append("  [AUDIO / MIDI STREAMING CLASS]");
                text.append("\n");
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    text.append("  Endpoint ").append(e).append(": ")
                        .append(hex2(ep.getAddress())).append(" | ")
                        .append(ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT")
                        .append(" | ").append(typeName(ep.getType()))
                        .append(" | packet ").append(ep.getMaxPacketSize())
                        .append(" | interval ").append(ep.getInterval()).append("\n");
                }
            }
        }
        output.setText(text.toString());
    }

    private void scanAndroidMidi() {
        if (midi == null) {
            output.setText("ANDROID MIDI SCAN\nMidiManager is not available on this Android device.");
            return;
        }
        MidiDeviceInfo[] devices = midi.getDevices();
        StringBuilder text = new StringBuilder("ANDROID MIDI SCAN\nDetected MIDI devices: ")
                .append(devices.length).append("\n");
        if (devices.length == 0) {
            text.append("\nAndroid has not exposed any MIDI device currently.\n")
                .append("This does not prove the pedal has no MIDI function. The USB interface may need a different driver or connection mode.");
        }
        for (int i = 0; i < devices.length; i++) {
            MidiDeviceInfo info = devices[i];
            text.append("\n--- MIDI DEVICE ").append(i + 1).append(" ---\n")
                .append("ID: ").append(info.getId()).append("\n")
                .append("Type: ").append(midiType(info.getType())).append("\n")
                .append("Input ports (phone can send to device): ").append(info.getInputPortCount()).append("\n")
                .append("Output ports (phone can receive from device): ").append(info.getOutputPortCount()).append("\n");
            android.os.Bundle props = info.getProperties();
            appendProperty(text, props, MidiDeviceInfo.PROPERTY_NAME, "Name");
            appendProperty(text, props, MidiDeviceInfo.PROPERTY_MANUFACTURER, "Manufacturer");
            appendProperty(text, props, MidiDeviceInfo.PROPERTY_PRODUCT, "Product");
        }
        text.append("\nNo MIDI messages were sent or received in this scan.");
        output.setText(text.toString());
    }

    private void appendProperty(StringBuilder text, android.os.Bundle props, String key, String label) {
        String value = props.getString(key);
        if (value != null && !value.isEmpty()) text.append(label).append(": ").append(value).append("\n");
    }

    private String midiType(int type) {
        switch (type) {
            case MidiDeviceInfo.TYPE_USB: return "USB";
            case MidiDeviceInfo.TYPE_VIRTUAL: return "VIRTUAL";
            case MidiDeviceInfo.TYPE_BLUETOOTH: return "BLUETOOTH";
            default: return "OTHER (" + type + ")";
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        Intent intent = new Intent(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(this, 0, intent, flags);
        usb.requestPermission(device, pending);
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
    private String hex2(int value) { return String.format("%02X", value & 0xFF); }
    private String hex4(int value) { return String.format("%04X", value & 0xFFFF); }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
    }
}
