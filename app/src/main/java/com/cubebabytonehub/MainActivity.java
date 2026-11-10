package com.cubebabytonehub;

import android.app.Activity;
import android.content.Context;
import android.media.midi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.*;

import java.io.IOException;

public class MainActivity extends Activity {

    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiOutputPort outputPort;
    private TextView result;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int rxCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        midiManager = (MidiManager)
                getSystemService(Context.MIDI_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB - STEP 13");
        title.setTextSize(21);
        layout.addView(title);

        Button scan = new Button(this);
        scan.setText("SCAN MIDI DEVICES");
        layout.addView(scan);

        Button start = new Button(this);
        start.setText("START LISTENING (MIDI IN)");
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("STOP LISTENING");
        layout.addView(stop);

        ScrollView scroll = new ScrollView(this);

        result = new TextView(this);
        result.setTextSize(15);
        result.setText("Ready. Connect CUBE BABY.");
        result.setTextIsSelectable(true);

        scroll.addView(result);

        layout.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0, 1));

        setContentView(layout);

        scan.setOnClickListener(v -> scanDevices());
        start.setOnClickListener(v -> startListening());
        stop.setOnClickListener(v -> stopListening());
    }

    private void scanDevices() {

        if (midiManager == null) {
            result.setText("MIDI service unavailable.");
            return;
        }

        MidiDeviceInfo[] devices = midiManager.getDevices();

        StringBuilder text = new StringBuilder();

        text.append("MIDI DEVICES: ")
                .append(devices.length).append("\n\n");

        for (MidiDeviceInfo info : devices) {

            text.append("Device ID: ")
                    .append(info.getId()).append("\n");

            text.append("Type: ")
                    .append(info.getType()).append("\n");

            text.append("MIDI IN: ")
                    .append(info.getInputPortCount()).append("\n");

            text.append("MIDI OUT: ")
                    .append(info.getOutputPortCount()).append("\n\n");
        }

        result.setText(text.toString());
    }

    private void startListening() {

        stopListening();

        MidiDeviceInfo selected = null;

        for (MidiDeviceInfo info : midiManager.getDevices()) {

            if (info.getType() == MidiDeviceInfo.TYPE_USB
                    && info.getOutputPortCount() > 0) {

                selected = info;
                break;
            }
        }

        if (selected == null) {
            result.append("\nUSB MIDI device not found.");
            return;
        }

        result.append("\nOpening MIDI device...");

        midiManager.openDevice(selected, device -> {

            if (device == null) {
                result.append("\nFailed to open device.");
                return;
            }

            midiDevice = device;

            try {

                outputPort = midiDevice.openOutputPort(0);

                if (outputPort == null) {
                    result.append("\nMIDI port unavailable.");
                    stopListening();
                    return;
                }

                rxCount = 0;

                outputPort.connect(new MidiReceiver() {

                    @Override
                    public void onSend(
                            byte[] data,
                            int offset,
                            int count,
                            long timestamp) {

                        StringBuilder hex = new StringBuilder();

                        for (int i = 0; i < count; i++) {

                            hex.append(String.format(
                                    "%02X ",
                                    data[offset + i] & 0xFF));
                        }

                        handler.post(() -> {

                            rxCount++;

                            result.append(
                                    "\nRX #" + rxCount + ": "
                                            + hex.toString());
                        });
                    }
                });

                result.append(
                        "\nLISTENING - Waiting for MIDI data...");

            } catch (Exception e) {

                result.append("\nERROR: " + e.getMessage());
                stopListening();
            }

        }, handler);
    }

    private void stopListening() {

        if (outputPort != null) {
            try {
                outputPort.close();
            } catch (IOException ignored) {}
            outputPort = null;
        }

        if (midiDevice != null) {
            try {
                midiDevice.close();
            } catch (IOException ignored) {}
            midiDevice = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
