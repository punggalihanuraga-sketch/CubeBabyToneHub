package com.cubebabytonehub;

import android.app.Activity;
import android.content.Context;
import android.media.midi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends Activity {

    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiInputPort inputPort;
    private MidiOutputPort outputPort;

    private TextView result;
    private Handler handler = new Handler(Looper.getMainLooper());

    private ByteArrayOutputStream sysexBuffer =
            new ByteArrayOutputStream();

    private boolean receiving = false;
    private boolean waitingRead = false;

    private static final int MEMORY = 5;
    private static final int ADDRESS = 0;
    private static final int LENGTH = 48;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        midiManager = (MidiManager)
                getSystemService(Context.MIDI_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB - STEP 15");
        title.setTextSize(21);
        layout.addView(title);

        Button scan = new Button(this);
        scan.setText("SCAN MIDI DEVICES");
        layout.addView(scan);

        Button connect = new Button(this);
        connect.setText("CONNECT CUBE BABY");
        layout.addView(connect);

        Button read = new Button(this);
        read.setText("READ PRESET A / B / C");
        layout.addView(read);

        Button stop = new Button(this);
        stop.setText("DISCONNECT");
        layout.addView(stop);

        ScrollView scroll = new ScrollView(this);

        result = new TextView(this);
        result.setTextSize(14);
        result.setTextIsSelectable(true);
        result.setText("STEP 15 READY");

        scroll.addView(result);

        layout.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0, 1));

        setContentView(layout);

        scan.setOnClickListener(v -> scanDevices());
        connect.setOnClickListener(v -> connectMidi());
        read.setOnClickListener(v -> readPresets());
        stop.setOnClickListener(v -> stopConnection());
    }

    private void scanDevices() {

        MidiDeviceInfo[] devices = midiManager.getDevices();

        StringBuilder text = new StringBuilder();

        text.append("MIDI DEVICES: ")
                .append(devices.length).append("\n\n");

        for (MidiDeviceInfo info : devices) {

            text.append("ID: ")
                    .append(info.getId()).append("\n");

            text.append("TYPE: ")
                    .append(info.getType()).append("\n");

            text.append("INPUT: ")
                    .append(info.getInputPortCount()).append("\n");

            text.append("OUTPUT: ")
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
            result.setText("USB MIDI DEVICE NOT FOUND");
            return;
        }

        result.setText("OPENING CUBE BABY...");

        midiManager.openDevice(selected, device -> {

            if (device == null) {
                result.append("\nOPEN DEVICE FAILED");
                return;
            }

            midiDevice = device;

            try {

                inputPort = midiDevice.openInputPort(0);
                outputPort = midiDevice.openOutputPort(0);

                if (inputPort == null || outputPort == null) {
                    result.append("\nMIDI PORT UNAVAILABLE");
                    stopConnection();
                    return;
                }

                receiving = false;
                waitingRead = false;

                outputPort.connect(new MidiReceiver() {

                    @Override
                    public void onSend(
                            byte[] data,
                            int offset,
                            int count,
                            long timestamp) {

                        receiveMidi(data, offset, count);
                    }
                });

                result.append("\nMIDI CONNECTED SUCCESSFULLY");
                result.append("\nCUBE BABY READY");

            } catch (Exception e) {

                result.append("\nERROR: " + e.getMessage());
                stopConnection();
            }

        }, handler);
    }

    private void receiveMidi(
            byte[] data,
            int offset,
            int count) {

        synchronized (sysexBuffer) {

            for (int i = offset; i < offset + count; i++) {

                int value = data[i] & 0xFF;

                if (value == 0xF0) {
                    sysexBuffer.reset();
                    receiving = true;
                }

                if (receiving) {
                    sysexBuffer.write(value);
                }

                if (value == 0xF7 && receiving) {

                    receiving = false;

                    byte[] message = sysexBuffer.toByteArray();

                    handler.post(() ->
                            processResponse(message));
                }
            }
        }
    }

    private void readPresets() {

        if (inputPort == null) {
            result.append("\nCONNECT CUBE BABY FIRST");
            return;
        }

        waitingRead = true;

        result.append("\nSENDING IDENTITY REQUEST...");

        byte[] identity = encodeEnvelope(
                0x11,
                new byte[0]
        );

        try {

            inputPort.send(identity, 0, identity.length);

            handler.postDelayed(() -> {

                if (!waitingRead || inputPort == null) return;

                result.append("\nSENDING PRESET READ REQUEST...");

                byte[] content = new byte[] {
                        0x05,
                        0x00, 0x00, 0x00, 0x00,
                        0x30, 0x00, 0x00
                };

                byte[] request = encodeEnvelope(0x23, content);

                try {
                    inputPort.send(request, 0, request.length);
                } catch (IOException e) {
                    waitingRead = false;
                    result.append("\nSEND ERROR: " + e.getMessage());
                }

            }, 500);

            handler.postDelayed(() -> {
                if (waitingRead) {
                    waitingRead = false;
                    result.append("\nTIMEOUT: NO PRESET RESPONSE");
                }
            }, 4000);

        } catch (IOException e) {

            waitingRead = false;
            result.append("\nERROR: " + e.getMessage());
        }
    }

    private byte[] encodeEnvelope(
            int messageType,
            byte[] content) {

        ByteArrayOutputStream clear = new ByteArrayOutputStream();

        clear.write(0x00);
        clear.write(0x59);
        clear.write(messageType);

        int length = content.length;

        clear.write(length & 0xFF);
        clear.write((length >> 8) & 0xFF);
        clear.write((length >> 16) & 0xFF);

        for (byte b : content) {
            clear.write(b & 0xFF);
        }

        clear.write(checksum(content));

        byte[] encoded = encodeSevenBit(clear.toByteArray());

        ByteArrayOutputStream sysex = new ByteArrayOutputStream();

        sysex.write(0xF0);

        for (byte b : encoded) {
            sysex.write(b & 0xFF);
        }

        sysex.write(0xF7);

        return sysex.toByteArray();
    }

    private int checksum(byte[] data) {

        int sum = 0;

        for (byte b : data) {
            sum = (sum + (b & 0xFF)) & 0xFF;
        }

        return (~sum) & 0xFF;
    }

    private byte[] encodeSevenBit(byte[] source) {

        ByteArrayOutputStream destination =
                new ByteArrayOutputStream();

        int bitCount = 0;
        long accumulator = 0;

        for (byte b : source) {

            accumulator |=
                    ((long) (b & 0xFF)) << bitCount;

            bitCount++;

            while (true) {

                destination.write((int) (accumulator & 0x7F));

                accumulator >>>= 7;

                if (bitCount < 7) break;

                bitCount -= 7;
            }
        }

        if (accumulator > 0) {
            destination.write((int) (accumulator & 0x7F));
        }

        int paddedLength = (source.length * 8 + 6) / 7;

        while (destination.size() < paddedLength) {
            destination.write(0);
        }

        return destination.toByteArray();
    }

    private byte[] decodeSevenBit(byte[] source) {

        ByteArrayOutputStream destination =
                new ByteArrayOutputStream();

        int bitCount = 0;
        int lastValue = 0;

        for (byte b : source) {

            int value = b & 0xFF;

            int mask = bitCount == 0
                    ? 0
                    : -1 >>> (32 - bitCount);

            int accumulator = value & mask;

            if (bitCount > 0) {

                destination.write(
                        ((lastValue & 0x7F)
                                | (accumulator << (8 - bitCount)))
                                & 0xFF
                );
            }

            lastValue = value >>> bitCount;
            bitCount = (bitCount + 1) % 8;
        }

        if (lastValue > 0) {
            destination.write(lastValue);
        }

        return destination.toByteArray();
    }

    private int readUint24(byte[] data, int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16);
    }

    private long readUint32(byte[] data, int offset) {

        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private void processResponse(byte[] sysex) {

        if (sysex.length < 8) return;

        if ((sysex[0] & 0xFF) != 0xF0
                || (sysex[sysex.length - 1] & 0xFF) != 0xF7) {
            return;
        }

        byte[] encoded = Arrays.copyOfRange(
                sysex, 1, sysex.length - 1);

        byte[] clear = decodeSevenBit(encoded);

        if (clear.length < 7) return;

        if ((clear[0] & 0xFF) != 0x00
                || (clear[1] & 0xFF) != 0x59) {
            return;
        }

        int messageType = clear[2] & 0xFF;
        int contentLength = readUint24(clear, 3);

        if (clear.length != 7 + contentLength) return;

        byte[] content = Arrays.copyOfRange(
                clear, 6, 6 + contentLength);

        int receivedChecksum =
                clear[6 + contentLength] & 0xFF;

        if (receivedChecksum != checksum(content)) {
            result.append("\nINVALID CHECKSUM");
            return;
        }

        if (messageType == 0x11) {
            result.append("\nIDENTITY RESPONSE RECEIVED");
            return;
        }

        if (messageType != 0x23 || content.length < 8) {
            return;
        }

        int memory = content[0] & 0xFF;
        long address = readUint32(content, 1);
        int length = readUint24(content, 5);

        byte[] presetData = Arrays.copyOfRange(
                content, 8, content.length);

        if (memory != MEMORY
                || address != ADDRESS
                || length != LENGTH
                || presetData.length != LENGTH) {

            result.append("\nUNEXPECTED MEMORY RESPONSE");
            return;
        }

        waitingRead = false;

        displayPresets(presetData);
    }

    private void displayPresets(byte[] data) {

        String[] names = {"A", "B", "C"};

        String[] parameters = {
                "Type",
                "Gain",
                "Tone",
                "Reverb",
                "Feedback",
                "Volume",
                "Time",
                "Mix",
                "Modulation",
                "Cabinet",
                "IR Section",
                "Delay Section",
                "Tone Section"
        };

        StringBuilder text = new StringBuilder();

        text.append("\n\nPRESET BANK READ SUCCESSFULLY\n");

        for (int slot = 0; slot < 3; slot++) {

            text.append("\n========== PRESET ")
                    .append(names[slot])
                    .append(" ==========\n");

            int start = slot * 16;

            for (int i = 0; i < 13; i++) {

                text.append(parameters[i])
                        .append(": ")
                        .append(data[start + i] & 0xFF)
                        .append("\n");
            }

            text.append("RAW: ");

            for (int i = 0; i < 16; i++) {

                text.append(String.format(
                        "%02X ",
                        data[start + i] & 0xFF
                ));
            }

            text.append("\n");
        }

        result.append(text.toString());
    }

    private void stopConnection() {

        waitingRead = false;
        receiving = false;

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

        result.setText("MIDI DISCONNECTED");
    }

    @Override
    protected void onDestroy() {
        stopConnection();
        super.onDestroy();
    }
}
