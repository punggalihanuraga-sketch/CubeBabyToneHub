package com.cubebabytonehub;

import android.app.Activity;
import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

public class MainActivity extends Activity {
private MidiManager midi;
private TextView output;
private MidiDevice openedDevice;
private MidiOutputPort receivePort;
private final Handler mainHandler = new Handler(Looper.getMainLooper());
private int receivedCount = 0;

@Override
public void onCreate(Bundle state) {
    super.onCreate(state);
    midi = (MidiManager) getSystemService(Context.MIDI_SERVICE);

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);

    int pad = (int) (18 * getResources().getDisplayMetrics().density);
    layout.setPadding(pad, pad, pad, pad);

    TextView title = new TextView(this);
    title.setText("CUBE BABY TONE HUB - STEP 13");
    title.setTextSize(21);
    layout.addView(title);

    TextView intro = new TextView(this);
    intro.setText("\nPassive MIDI Monitor. Connect CUBE BABY using OTG.");
    layout.addView(intro);

    Button scanButton = new Button(this);
    scanButton.setText("SCAN MIDI DEVICES");
    layout.addView(scanButton);

    Button listenButton = new Button(this);
    listenButton.setText("START LISTENING (MIDI IN)");
    layout.addView(listenButton);

    Button stopButton = new Button(this);
    stopButton.setText("STOP LISTENING");
    layout.addView(stopButton);

    ScrollView scroll = new ScrollView(this);
    output = new TextView(this);
    output.setText("\nReady. Scan devices first.");
    output.setTextSize(14);
    output.setTextIsSelectable(true);
    scroll.addView(output);

    layout.addView(scroll,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f
        )
    );

    TextView note = new TextView(this);
    note.setText("\nThis step only listens for MIDI data FROM the pedal. It does not send commands or change presets.");
    layout.addView(note);

    setContentView(layout);

    scanButton.setOnClickListener(v -> scanDevices());
    listenButton.setOnClickListener(v -> startListening());
    stopButton.setOnClickListener(v -> stopListening());
}

private void scanDevices() {
    if (midi == null) {
        output.setText("Android MIDI service unavailable.");
        return;
    }

    MidiDeviceInfo[] devices = midi.getDevices();
    StringBuilder b = new StringBuilder();
    b.append("MIDI DEVICES: ").append(devices.length).append("\n");

    for (int i = 0; i < devices.length; i++) {
        MidiDeviceInfo info = devices[i];

        b.append("\nDevice ").append(i + 1)
         .append(" | ID ").append(info.getId())
         .append(" | Type ").append(info.getType())
         .append("\nInput ports (phone to pedal): ")
         .append(info.getInputPortCount())
         .append("\nOutput ports (pedal to phone): ")
         .append(info.getOutputPortCount()).append("\n");

        String name = info.getProperties()
            .getString(MidiDeviceInfo.PROPERTY_NAME);

        String product = info.getProperties()
            .getString(MidiDeviceInfo.PROPERTY_PRODUCT);

        if (name != null) b.append("Name: ").append(name).append("\n");
        if (product != null) b.append("Product: ").append(product).append("\n");
    }

    output.setText(b.toString());
}

private void startListening() {
    if (midi == null) {
        output.setText("Android MIDI service unavailable.");
        return;
    }

    stopListening();

    MidiDeviceInfo target = null;

    for (MidiDeviceInfo info : midi.getDevices()) {
        if (info.getType() == MidiDeviceInfo.TYPE_USB
                && info.getOutputPortCount() > 0) {
            target = info;
            break;
        }
    }

    if (target == null) {
        output.append("\nNo USB MIDI device with output port found.");
        return;
    }

    final MidiDeviceInfo selected = target;
    output.append("\nOpening USB MIDI device ID " + selected.getId());

    midi.openDevice(selected, device -> {
        if (device == null) {
            output.append("\nCould not open MIDI device.");
            return;
        }

        openedDevice = device;

        try {
            receivePort = openedDevice.openOutputPort(0);

            if (receivePort == null) {
                output.append("\nCould not open MIDI output port.");
                stopListening();
                return;
            }

            receivedCount = 0;

            receivePort.connect(new MidiReceiver() {
                @Override
                public void onSend(
                        byte[] data,
                        int offset,
                        int count,
                        long timestamp) {

                    final String line = formatBytes(data, offset, count);

                    mainHandler.post(() -> {
                        receivedCount++;
                        output.append(
                            "\nRX #" + receivedCount + "  " + line
                        );
                    });
                }
            });

            output.append(
                "\nLISTENING. Try normal pedal controls."
            );

        } catch (IOException | IllegalStateException e) {
            output.append("\nMIDI error: " + e.getMessage());
            stopListening();
        }

    }, mainHandler);
}

private String formatBytes(byte[] data, int offset, int count) {
    StringBuilder b = new StringBuilder();

    for (int i = 0; i < count; i++) {
        if (i > 0) b.append(" ");
        b.append(String.format("%02X", data[offset + i] & 0xFF));
    }

    return b.length() == 0 ? "(empty packet)" : b.toString();
}

private void stopListening() {
    try {
        if (receivePort != null) receivePort.close();
    } catch (IOException ignored) {}

    receivePort = null;

    try {
        if (openedDevice != null) openedDevice.close();
    } catch (IOException ignored) {}

    openedDevice = null;
}

@Override
protected void onDestroy() {
    stopListening();
    super.onDestroy();
}

}
