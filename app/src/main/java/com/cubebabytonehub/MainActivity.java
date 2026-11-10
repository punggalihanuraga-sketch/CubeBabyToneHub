package com.cubebabytonehub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.midi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends Activity {

    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiInputPort inputPort;
    private MidiOutputPort outputPort;

    private TextView result;
    private TextView presetTitle;

    private LinearLayout editorLayout;
    private LinearLayout rootLayout;

    private Handler handler = new Handler(Looper.getMainLooper());

    private ByteArrayOutputStream sysexBuffer =
            new ByteArrayOutputStream();

    private boolean receiving = false;
    private boolean waitingRead = false;

    private static final int MEMORY = 5;
    private static final int ADDRESS = 0;
    private static final int LENGTH = 48;

    private static final int CREATE_BACKUP = 1001;
    private static final int RESTORE_BACKUP = 1002;

    private byte[] presetData = new byte[48];

    private boolean presetsLoaded = false;
    private boolean updatingSliders = false;

    private int selectedPreset = 0;

    private final String[] presetNames = {"A", "B", "C"};

    private final String[] parameterNames = {
            "Gain",
            "Tone",
            "Reverb",
            "Feedback",
            "Volume",
            "Time",
            "Mix",
            "Modulation"
    };

    private final int[] parameterOffsets = {
            1, 2, 3, 4, 5, 6, 7, 8
    };

    private SeekBar[] sliders = new SeekBar[8];
    private TextView[] values = new TextView[8];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        midiManager = (MidiManager)
                getSystemService(Context.MIDI_SERVICE);

        buildInterface();
    }

    private void buildInterface() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(18, 18, 18, 18);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB");
        title.setTextSize(23);
        title.setPadding(0, 0, 0, 12);
        rootLayout.addView(title);

        Button scan = makeButton("SCAN MIDI DEVICES");
        Button connect = makeButton("CONNECT CUBE BABY");
        Button read = makeButton("READ PRESET A / B / C");
        Button disconnect = makeButton("DISCONNECT");

        rootLayout.addView(scan);
        rootLayout.addView(connect);
        rootLayout.addView(read);
        rootLayout.addView(disconnect);

        scan.setOnClickListener(v -> scanDevices());
        connect.setOnClickListener(v -> connectMidi());
        read.setOnClickListener(v -> readPresets());
        disconnect.setOnClickListener(v -> stopConnection());

        addDivider();

        TextView editorHeading = new TextView(this);
        editorHeading.setText("PRESET EDITOR");
        editorHeading.setTextSize(20);
        rootLayout.addView(editorHeading);

        LinearLayout presetButtons = new LinearLayout(this);
        presetButtons.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < 3; i++) {

            final int index = i;

            Button button = new Button(this);
            button.setText("PRESET " + presetNames[i]);

            presetButtons.addView(button,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    ));

            button.setOnClickListener(v -> selectPreset(index));
        }

        rootLayout.addView(presetButtons);

        presetTitle = new TextView(this);
        presetTitle.setText("PRESET A");
        presetTitle.setTextSize(18);
        presetTitle.setPadding(0, 12, 0, 8);
        rootLayout.addView(presetTitle);

        editorLayout = new LinearLayout(this);
        editorLayout.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < 8; i++) {
            createSlider(i);
        }

        rootLayout.addView(editorLayout);

        Button backup = makeButton("BACKUP PRESETS");
        Button restore = makeButton("RESTORE BACKUP");

        rootLayout.addView(backup);
        rootLayout.addView(restore);

        backup.setOnClickListener(v -> saveBackup());
        restore.setOnClickListener(v -> openBackup());

        addDivider();

        TextView logTitle = new TextView(this);
        logTitle.setText("MIDI STATUS / INFORMATION");
        logTitle.setTextSize(17);
        rootLayout.addView(logTitle);

        ScrollView scroll = new ScrollView(this);

        result = new TextView(this);
        result.setTextSize(13);
        result.setTextIsSelectable(true);
        result.setText("CUBE BABY TONE HUB READY");

        scroll.addView(result);

        rootLayout.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

        setContentView(rootLayout);

        updateEditor();
    }

    private Button makeButton(String text) {

        Button button = new Button(this);
        button.setText(text);

        return button;
    }

    private void addDivider() {

        TextView divider = new TextView(this);
        divider.setText("------------------------------");
        divider.setPadding(0, 8, 0, 8);

        rootLayout.addView(divider);
    }

    private void createSlider(int index) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 5, 0, 5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(this);
        name.setText(parameterNames[index]);
        name.setTextSize(15);

        values[index] = new TextView(this);
        values[index].setText("0");
        values[index].setTextSize(15);

        header.addView(name,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                ));

        header.addView(values[index]);

        row.addView(header);

        sliders[index] = new SeekBar(this);
        sliders[index].setMax(127);

        final int position = index;

        sliders[index].setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser) {

                values[position].setText(
                        String.valueOf(progress)
                );

                if (fromUser && presetsLoaded
                        && !updatingSliders) {

                    int offset =
                            selectedPreset * 16
                            + parameterOffsets[position];

                    presetData[offset] = (byte) progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        row.addView(sliders[index]);
        editorLayout.addView(row);
    }

    private void selectPreset(int index) {

        selectedPreset = index;
        updateEditor();
    }

    private void updateEditor() {

        if (presetTitle == null) return;

        presetTitle.setText(
                "PRESET " + presetNames[selectedPreset]
        );

        updatingSliders = true;

        for (int i = 0; i < 8; i++) {

            int offset =
                    selectedPreset * 16
                    + parameterOffsets[i];

            int value = presetData[offset] & 0xFF;

            sliders[i].setProgress(value);
            values[i].setText(String.valueOf(value));

            sliders[i].setEnabled(presetsLoaded);
        }

        updatingSliders = false;
    }

    private void scanDevices() {

        MidiDeviceInfo[] devices = midiManager.getDevices();

        StringBuilder text = new StringBuilder();

        text.append("MIDI DEVICES: ")
                .append(devices.length)
                .append("\n\n");

        for (MidiDeviceInfo info : devices) {

            text.append("ID: ")
                    .append(info.getId())
                    .append("\n");

            text.append("TYPE: ")
                    .append(info.getType())
                    .append("\n");

            text.append("INPUT: ")
                    .append(info.getInputPortCount())
                    .append("\n");

            text.append("OUTPUT: ")
                    .append(info.getOutputPortCount())
                    .append("\n\n");
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

        byte[] data = Arrays.copyOfRange(
                content, 8, content.length);

        if (memory != MEMORY
                || address != ADDRESS
                || length != LENGTH
                || data.length != LENGTH) {

            result.append("\nUNEXPECTED MEMORY RESPONSE");
            return;
        }

        waitingRead = false;

        presetData = Arrays.copyOf(data, data.length);
        presetsLoaded = true;

        updateEditor();

        displayPresets(data);
    }

    private void displayPresets(byte[] data) {

        StringBuilder text = new StringBuilder();

        text.append("\n\nPRESET BANK READ SUCCESSFULLY\n");
        text.append("EDITOR READY\n");

        for (int slot = 0; slot < 3; slot++) {

            text.append("\n========== PRESET ")
                    .append(presetNames[slot])
                    .append(" ==========\n");

            int start = slot * 16;

            for (int i = 0; i < 13; i++) {

                text.append("PARAM ")
                        .append(i)
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

    private void saveBackup() {

        if (!presetsLoaded) {
            result.append("\nREAD PRESETS BEFORE BACKUP");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(
                Intent.EXTRA_TITLE,
                "CubeBaby_Backup.cbth"
        );

        startActivityForResult(intent, CREATE_BACKUP);
    }

    private void openBackup() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, RESTORE_BACKUP);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        try {

            if (requestCode == CREATE_BACKUP) {

                java.io.OutputStream stream =
                        getContentResolver().openOutputStream(
                                data.getData()
                        );

                if (stream == null) {
                    result.append("\nBACKUP FAILED");
                    return;
                }

                stream.write(presetData);
                stream.close();

                result.append("\nBACKUP SAVED SUCCESSFULLY");
            }

            if (requestCode == RESTORE_BACKUP) {

                InputStream stream =
                        getContentResolver().openInputStream(
                                data.getData()
                        );

                if (stream == null) {
                    result.append("\nRESTORE FAILED");
                    return;
                }

                ByteArrayOutputStream buffer =
                        new ByteArrayOutputStream();

                byte[] temp = new byte[1024];
                int count;

                while ((count = stream.read(temp)) != -1) {
                    buffer.write(temp, 0, count);
                }

                stream.close();

                byte[] restored = buffer.toByteArray();

                if (restored.length != 48) {
                    result.append(
                            "\nINVALID BACKUP FILE - EXPECTED 48 BYTES"
                    );
                    return;
                }

                presetData = Arrays.copyOf(
                        restored,
                        restored.length
                );

                presetsLoaded = true;

                updateEditor();

                result.append("\nBACKUP RESTORED SUCCESSFULLY");
                result.append("\nEDITOR DATA UPDATED");
            }

        } catch (Exception e) {

            result.append("\nFILE ERROR: " + e.getMessage());
        }
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

        if (result != null) {
            result.setText("MIDI DISCONNECTED");
        }
    }

    @Override
    protected void onDestroy() {
        stopConnection();
        super.onDestroy();
    }
}
