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
    private MidiInputPort inputPort;
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
        title.setText("CUBE BABY TONE HUB - STEP 14");
        title.setTextSize(21);
        layout.addView(title);

        Button scan = new Button(this);
        scan.setText("SCAN MIDI DEVICES");
        layout.addView(scan);

        Button connect = new Button(this);
        connect.setText("CONNECT MIDI");
        layout.addView(connect);

        Button identify = new Button(this);
        identify.setText("SEND READ-ONLY ID QUERY");
        layout.addView(identify);

        Button stop = new Button(this);
        stop.setText("STOP CONNECTION");
        layout.addView(stop);

        ScrollView scroll = new ScrollView(this);

        result = new TextView(this);
        result.setTextSize(14);
        result.setTextIsSelectable(true);
        result.setText("STEP 14 READY");

        scroll.addView(result);

        layout.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0, 1));

        TextView note = new TextView(this);
        note.setText(
                "\nRead-only identity request. " +
                "No preset writing or memory erasing."
        );
        layout.addView(note);

        setContentView(layout);

        scan.setOnClickListener(v -> scanDevices());
        connect.setOnClickListener(v -> connectMidi());
        identify.setOnClickListener(v -> sendIdentityQuery());
        stop.setOnClickListener(v -> stopConnection());
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

            text.append("MIDI INPUT: ")
                    .append(info.getInputPortCount()).append("\n");

            text.append("MIDI OUTPUT: ")
                    .append(info.getOutputPortCount()).append("\n\n");
        }

        result.setText(text.toString());
    }

    private void connectMidi() {

        stopConnection();

        MidiDeviceInfo selected = null;

        for (MidiDeviceInfo info : midiManager.getDevices()) {

            if (info.getType() == MidiDeviceInfo.TYPE_USB
                    && info.getInputPortCount() > 0
                    && info.getOutputPortCount() > 0) {

                selected = info;
                break;
            }
        }

        if (selected == null) {
            result.append("\nUSB MIDI device not found.");
            return;
        }

        result.append("\nOpening CUBE BABY...");

        midiManager.openDevice(selected, device -> {

            if (device == null) {
                result.append("\nFailed to open MIDI device.");
                return;
            }

            midiDevice = device;

            try {

                // Phone sends data TO pedal
                inputPort = midiDevice.openInputPort(0);

                // Pedal sends data TO phone
                outputPort = midiDevice.openOutputPort(0);

                if (inputPort == null || outputPort == null) {
                    result.append("\nMIDI port unavailable.");
                    stopConnection();
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

                        final String hex =
                                formatHex(data, offset, count);

                        handler.post(() -> {

                            rxCount++;

                            result.append(
                                    "\nRX #" + rxCount + ": " + hex
                            );
                        });
                    }
                });

                result.append(
                        "\nMIDI CONNECTED SUCCESSFULLY."
                );

                result.append(
                        "\nReady for identity query."
                );

            } catch (Exception e) {

                result.append(
                        "\nCONNECTION ERROR: " + e.getMessage()
                );

                stopConnection();
            }

        }, handler);
    }

    private void sendIdentityQuery() {

        if (inputPort == null) {

            result.append(
                    "\nERROR: Connect MIDI first."
            );

            return;
        }

        // CUBE BABY RequestNameVersion
        // Read-only request; no memory write.

        byte[] request = new byte[] {
                (byte) 0xF0,
                0x00,
                0x32,
                0x45,
                0x00,
                0x00,
                0x00,
                0x40,
                0x7F,
                0x00,
                (byte) 0xF7
        };

        try {

            inputPort.send(
                    request,
                    0,
                    request.length
            );

            result.append(
                    "\nIDENTITY QUERY SENT."
            );

            result.append(
                    "\nWaiting for CUBE BABY response..."
            );

        } catch (IOException e) {

            result.append(
                    "\nSEND ERROR: " + e.getMessage()
            );
        }
    }

    private String formatHex(
            byte[] data,
            int offset,
            int count) {

        StringBuilder hex = new StringBuilder();

        for (int i = 0; i < count; i++) {

            if (i > 0) {
                hex.append(" ");
            }

            hex.append(String.format(
                    "%02X",
                    data[offset + i] & 0xFF
            ));
        }

        return hex.toString();
    }

    private void stopConnection() {

        if (outputPort != null) {
            try {
                outputPort.close();
            } catch (IOException ignored) {}

            outputPort = null;
        }

        if (inputPort != null) {
            try {
                inputPort.close();
            } catch (IOException ignored) {}

            inputPort = null;
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
        stopConnection();
        super.onDestroy();
    }
} 
