package com.cubebabytonehub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.midi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends Activity {

    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiInputPort inputPort;
    private MidiOutputPort outputPort;

    private Handler handler =
            new Handler(Looper.getMainLooper());

    private ByteArrayOutputStream sysexBuffer =
            new ByteArrayOutputStream();

    private boolean receiving = false;
    private boolean waitingRead = false;
    private boolean waitingWriteAck = false;
    private boolean presetsLoaded = false;
    private boolean updating = false;
    private boolean verifyingWrite = false;

    private static final int MEMORY = 5;
    private static final int LENGTH = 48;

    private static final int CREATE_BACKUP = 1001;
    private static final int RESTORE_BACKUP = 1002;

    private byte[] presetData = new byte[LENGTH];
    private byte[] expectedWriteData;

    private int selectedPreset = 0;

    private final String[] presetNames = {"A", "B", "C"};

    private final String[] parameterNames = {
            "Gain", "Tone", "Reverb", "Feedback",
            "Volume", "Time", "Mix", "Modulation"
    };

    private final int[] offsets = {
            1, 2, 3, 4, 5, 6, 7, 8
    };

    private SeekBar[] sliders = new SeekBar[8];
    private TextView[] values = new TextView[8];

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        midiManager = (MidiManager)
                getSystemService(Context.MIDI_SERVICE);

        buildUI();
    }

    // =====================================
    // USER INTERFACE
    // =====================================

    private void buildUI() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 15, 20, 20);

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CUBE BABY TONE HUB");
        title.setTextSize(25);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("USB MIDI PRESET EDITOR");
        subtitle.setTextSize(13);
        root.addView(subtitle);

        addButton(root, "SCAN MIDI DEVICES",
                v -> scanDevices());

        addButton(root, "CONNECT CUBE BABY",
                v -> connectMidi());

        addButton(root, "READ PRESET A / B / C",
                v -> readPresets());

        addButton(root, "DISCONNECT",
                v -> stopConnection());

        addDivider(root);

        TextView editorTitle = new TextView(this);
        editorTitle.setText("PRESET EDITOR");
        editorTitle.setTextSize(21);
        root.addView(editorTitle);

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

            button.setOnClickListener(v -> {

                if (verifyingWrite) return;

                selectedPreset = index;
                updateEditor();
            });
        }

        root.addView(presetButtons);

        TextView selected = new TextView(this);
        selected.setTextSize(20);
        selected.setPadding(0, 15, 0, 5);
        selected.setText("PRESET A");
        root.addView(selected);

        for (int i = 0; i < 8; i++) {

            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 8, 0, 8);

            LinearLayout header = new LinearLayout(this);

            TextView name = new TextView(this);
            name.setText(parameterNames[i]);
            name.setTextSize(16);

            values[i] = new TextView(this);
            values[i].setText("0");
            values[i].setTextSize(16);

            header.addView(name,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    ));

            header.addView(values[i]);

            row.addView(header);

            sliders[i] = new SeekBar(this);
            sliders[i].setMax(127);

            sliders[i].setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(
                        SeekBar bar,
                        int progress,
                        boolean fromUser) {

                    values[index].setText(
                            String.valueOf(progress));

                    if (fromUser && presetsLoaded
                            && !updating
                            && !verifyingWrite) {

                        int position =
                                selectedPreset * 16
                                        + offsets[index];

                        presetData[position] =
                                (byte) progress;
                    }
                }

                @Override
                public void onStartTrackingTouch(
                        SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(
                        SeekBar bar) {}
            });

            row.addView(sliders[i]);
            root.addView(row);
        }

        addDivider(root);

        TextView backupTitle = new TextView(this);
        backupTitle.setText("BACKUP & RESTORE");
        backupTitle.setTextSize(21);
        root.addView(backupTitle);

        addButton(root, "BACKUP PRESETS",
                v -> saveBackup());

        addButton(root, "RESTORE BACKUP",
                v -> openBackup());

        addDivider(root);

        TextView saveTitle = new TextView(this);
        saveTitle.setText("PEDAL MEMORY");
        saveTitle.setTextSize(21);
        root.addView(saveTitle);

        addButton(root, "SAVE TO PEDAL A / B / C",
                v -> confirmWrite());

        addDivider(root);

        TextView statusTitle = new TextView(this);
        statusTitle.setText("MIDI STATUS");
        statusTitle.setTextSize(18);
        root.addView(statusTitle);

        status = new TextView(this);
        status.setTextSize(13);
        status.setText("CUBE BABY READY");
        status.setPadding(0, 10, 0, 20);

        root.addView(status);

        setContentView(scroll);

        updateEditor();
    }

    private void addButton(
            LinearLayout root,
            String text,
            android.view.View.OnClickListener listener) {

        Button button = new Button(this);
        button.setText(text);
        root.addView(button);
        button.setOnClickListener(listener);
    }

    private void addDivider(LinearLayout root) {

        TextView line = new TextView(this);
        line.setText("------------------------------");
        line.setPadding(0, 12, 0, 12);
        root.addView(line);
    }

    private void updateEditor() {

        updating = true;

        for (int i = 0; i < 8; i++) {

            int position =
                    selectedPreset * 16 + offsets[i];

            int value = presetData[position] & 255;

            sliders[i].setProgress(value);

            sliders[i].setEnabled(
                    presetsLoaded && !verifyingWrite);

            values[i].setText(String.valueOf(value));
        }

        updating = false;
    }

    // =====================================
    // MIDI DEVICE
    // =====================================

    private void scanDevices() {

        MidiDeviceInfo[] devices = midiManager.getDevices();

        StringBuilder text = new StringBuilder();

        text.append("MIDI DEVICES: ")
                .append(devices.length);

        for (MidiDeviceInfo info : devices) {

            text.append("\nID: ").append(info.getId());
            text.append("\nTYPE: ").append(info.getType());

            text.append("\nINPUT: ")
                    .append(info.getInputPortCount());

            text.append("\nOUTPUT: ")
                    .append(info.getOutputPortCount());

            text.append("\n");
        }

        status.setText(text.toString());
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
            status.setText("USB MIDI DEVICE NOT FOUND");
            return;
        }

        status.setText("OPENING CUBE BABY...");

        midiManager.openDevice(selected, device -> {

            if (device == null) {
                status.setText("OPEN DEVICE FAILED");
                return;
            }

            midiDevice = device;

            try {

                inputPort = device.openInputPort(0);
                outputPort = device.openOutputPort(0);

                if (inputPort == null || outputPort == null) {

                    status.setText("MIDI PORT UNAVAILABLE");
                    stopConnection();
                    return;
                }

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

                status.setText("MIDI CONNECTED SUCCESSFULLY");

            } catch (Exception e) {

                status.setText("ERROR: " + e.getMessage());
                stopConnection();
            }

        }, handler);
    }

    // =====================================
    // RECEIVE MIDI
    // =====================================

    private void receiveMidi(
            byte[] data,
            int offset,
            int count) {

        synchronized (sysexBuffer) {

            for (int i = offset; i < offset + count; i++) {

                int value = data[i] & 255;

                if (value == 0xF0) {
                    sysexBuffer.reset();
                    receiving = true;
                }

                if (receiving) {
                    sysexBuffer.write(value);
                }

                if (value == 0xF7 && receiving) {

                    receiving = false;

                    byte[] message =
                            sysexBuffer.toByteArray();

                    handler.post(() ->
                            processResponse(message));
                }
            }
        }
    }

    // =====================================
    // READ PRESETS
    // =====================================

    private void readPresets() {

        if (inputPort == null) {
            status.setText("CONNECT CUBE BABY FIRST");
            return;
        }

        if (verifyingWrite || waitingRead) {
            status.setText("PLEASE WAIT FOR CURRENT OPERATION");
            return;
        }

        waitingRead = true;
        status.setText("READING PRESETS...");

        byte[] identity =
                encodeEnvelope(0x11, new byte[0]);

        try {

            inputPort.send(
                    identity, 0, identity.length);

            handler.postDelayed(() -> {

                if (!waitingRead || inputPort == null) return;

                sendReadRequest();

            }, 500);

            handler.postDelayed(() -> {

                if (waitingRead) {
                    waitingRead = false;
                    status.setText("READ TIMEOUT");
                }

            }, 5000);

        } catch (IOException e) {

            waitingRead = false;
            status.setText("ERROR: " + e.getMessage());
        }
    }

    private void sendReadRequest() {

        if (inputPort == null) {
            status.setText("MIDI NOT CONNECTED");
            waitingRead = false;
            return;
        }

        byte[] content = {
                0x05,
                0, 0, 0, 0,
                0x30, 0, 0
        };

        byte[] request =
                encodeEnvelope(0x23, content);

        try {

            inputPort.send(
                    request, 0, request.length);

        } catch (IOException e) {

            waitingRead = false;
            status.setText("READ SEND ERROR");
        }
    }

    // =====================================
    // SAVE ALL PRESETS
    // =====================================

    private void confirmWrite() {

        if (inputPort == null) {
            status.setText("CONNECT CUBE BABY FIRST");
            return;
        }

        if (!presetsLoaded) {
            status.setText("READ PRESETS FIRST");
            return;
        }

        if (verifyingWrite || waitingRead) {
            status.setText("PLEASE WAIT FOR CURRENT OPERATION");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("SAVE TO CUBE BABY?")
                .setMessage(
                        "This operation will write all three presets "
                        + "(A, B and C) to the pedal.\n\n"
                        + "Existing pedal presets will be overwritten.\n\n"
                        + "Make sure you have created a backup.\n\n"
                        + "Continue?"
                )
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (dialog, which) -> {

                    writePresetsToPedal();

                })
                .show();
    }

    private void writePresetsToPedal() {

        if (inputPort == null || !presetsLoaded) {
            status.setText("MIDI NOT READY");
            return;
        }

        expectedWriteData =
                Arrays.copyOf(presetData, LENGTH);

        byte[] content = new byte[56];

        content[0] = (byte) MEMORY;

        // Address 0
        content[1] = 0;
        content[2] = 0;
        content[3] = 0;
        content[4] = 0;

        // Length 48 bytes
        content[5] = 48;
        content[6] = 0;
        content[7] = 0;

        System.arraycopy(
                expectedWriteData,
                0,
                content,
                8,
                LENGTH
        );

        byte[] request =
                encodeEnvelope(0x22, content);

        try {

            verifyingWrite = true;
            waitingRead = false;
            waitingWriteAck = true;

            updateEditor();

            status.setText(
                    "WRITING PRESET A / B / C...\n"
                    + "WAITING FOR PEDAL ACK"
            );

            inputPort.send(
                    request,
                    0,
                    request.length
            );

            handler.postDelayed(() -> {

                if (verifyingWrite && waitingWriteAck) {

                    finishWrite(
                            "WRITE ACK TIMEOUT.\n"
                            + "PEDAL DID NOT CONFIRM THE WRITE."
                    );
                }

            }, 5000);

        } catch (IOException e) {

            finishWrite(
                    "WRITE ERROR: " + e.getMessage()
            );
        }
    }

    // =====================================
    // VERIFY WRITE
    // =====================================

    private void requestVerificationRead() {

        if (!verifyingWrite || inputPort == null) {
            finishWrite("MIDI CONNECTION LOST");
            return;
        }

        status.setText(
                "VERIFYING: READING PRESETS FROM PEDAL..."
        );

        byte[] identity =
                encodeEnvelope(0x11, new byte[0]);

        try {

            inputPort.send(
                    identity,
                    0,
                    identity.length
            );

            handler.postDelayed(() -> {

                if (!verifyingWrite || inputPort == null) {
                    return;
                }

                waitingRead = true;
                sendReadRequest();

            }, 500);

            handler.postDelayed(() -> {

                if (verifyingWrite && waitingRead) {

                    finishWrite(
                            "VERIFICATION TIMEOUT.\n"
                            + "PLEASE READ PRESETS MANUALLY."
                    );
                }

            }, 5000);

        } catch (IOException e) {

            finishWrite(
                    "VERIFY REQUEST ERROR: " + e.getMessage()
            );
        }
    }

    private void finishWrite(String message) {

        verifyingWrite = false;
        waitingRead = false;
        waitingWriteAck = false;

        expectedWriteData = null;

        updateEditor();

        status.setText(message);
    }

    // =====================================
    // MIDI ENVELOPE
    // =====================================

    private byte[] encodeEnvelope(
            int messageType,
            byte[] content) {

        ByteArrayOutputStream clear =
                new ByteArrayOutputStream();

        clear.write(0);
        clear.write(0x59);
        clear.write(messageType);

        int length = content.length;

        clear.write(length & 255);
        clear.write((length >> 8) & 255);
        clear.write((length >> 16) & 255);

        for (byte b : content) {
            clear.write(b & 255);
        }

        clear.write(checksum(content));

        byte[] encoded =
                encodeSevenBit(clear.toByteArray());

        ByteArrayOutputStream sysex =
                new ByteArrayOutputStream();

        sysex.write(0xF0);

        for (byte b : encoded) {
            sysex.write(b & 255);
        }

        sysex.write(0xF7);

        return sysex.toByteArray();
    }

    private int checksum(byte[] data) {

        int sum = 0;

        for (byte b : data) {
            sum = (sum + (b & 255)) & 255;
        }

        return (~sum) & 255;
    }

    private byte[] encodeSevenBit(byte[] source) {

        ByteArrayOutputStream destination =
                new ByteArrayOutputStream();

        int bitCount = 0;
        long accumulator = 0;

        for (byte b : source) {

            accumulator |=
                    ((long) (b & 255)) << bitCount;

            bitCount++;

            while (true) {

                destination.write(
                        (int) (accumulator & 127));

                accumulator >>>= 7;

                if (bitCount < 7) break;

                bitCount -= 7;
            }
        }

        if (accumulator > 0) {
            destination.write(
                    (int) (accumulator & 127));
        }

        int paddedLength =
                (source.length * 8 + 6) / 7;

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

            int value = b & 255;

            int mask = bitCount == 0
                    ? 0
                    : -1 >>> (32 - bitCount);

            int accumulator = value & mask;

            if (bitCount > 0) {

                destination.write(
                        ((lastValue & 127)
                                | (accumulator
                                << (8 - bitCount)))
                                & 255
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

    private int readUint24(
            byte[] data,
            int offset) {

        return (data[offset] & 255)
                | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16);
    }

    private long readUint32(
            byte[] data,
            int offset) {

        return ((long) data[offset] & 255)
                | (((long) data[offset + 1] & 255) << 8)
                | (((long) data[offset + 2] & 255) << 16)
                | (((long) data[offset + 3] & 255) << 24);
    }

    // =====================================
    // PROCESS MIDI RESPONSE
    // =====================================

    private void processResponse(byte[] sysex) {

        if (sysex.length < 8) return;

        if ((sysex[0] & 255) != 0xF0
                || (sysex[sysex.length - 1] & 255) != 0xF7) {
            return;
        }

        byte[] encoded = Arrays.copyOfRange(
                sysex, 1, sysex.length - 1);

        byte[] clear = decodeSevenBit(encoded);

        if (clear.length < 7) return;

        if ((clear[0] & 255) != 0
                || (clear[1] & 255) != 0x59) {
            return;
        }

        int messageType = clear[2] & 255;

        int contentLength =
                readUint24(clear, 3);

        if (clear.length != 7 + contentLength) {
            return;
        }

        byte[] content = Arrays.copyOfRange(
                clear, 6, 6 + contentLength);

        if ((clear[6 + contentLength] & 255)
                != checksum(content)) {

            status.setText("INVALID CHECKSUM");
            return;
        }

        // WRITE ACK
        if (messageType == 0x00) {

            if (!verifyingWrite || !waitingWriteAck) {
                return;
            }

            if (content.length < 1) {
                finishWrite("INVALID WRITE ACK");
                return;
            }

            int ackValue = content[0] & 255;

            waitingWriteAck = false;

            if (ackValue == 0) {

                finishWrite(
                        "PEDAL REJECTED WRITE COMMAND.\n"
                        + "PRESET NOT VERIFIED."
                );

                return;
            }

            status.setText(
                    "PEDAL ACK RECEIVED.\n"
                    + "STARTING PRESET VERIFICATION..."
            );

            handler.postDelayed(() -> {

                if (verifyingWrite) {
                    requestVerificationRead();
                }

            }, 250);

            return;
        }

        // MEMORY READ RESPONSE
        if (messageType != 0x23 || content.length < 8) {
            return;
        }

        int memory = content[0] & 255;

        long address = readUint32(content, 1);

        int length = readUint24(content, 5);

        byte[] data = Arrays.copyOfRange(
                content, 8, content.length);

        if (memory != MEMORY
                || address != 0
                || length != LENGTH
                || data.length != LENGTH) {

            status.setText("UNEXPECTED MEMORY RESPONSE");
            return;
        }

        waitingRead = false;

        if (verifyingWrite) {

            boolean matches =
                    expectedWriteData != null
                    && Arrays.equals(expectedWriteData, data);

            presetData = Arrays.copyOf(data, LENGTH);
            presetsLoaded = true;

            if (matches) {

                finishWrite(
                        "SAVE VERIFIED!\n"
                        + "PRESET A / B / C DATA MATCHES."
                );

            } else {

                finishWrite(
                        "VERIFICATION FAILED!\n"
                        + "PEDAL DATA DOES NOT MATCH."
                );
            }

            updateEditor();
            return;
        }

        presetData = Arrays.copyOf(data, LENGTH);

        presetsLoaded = true;

        updateEditor();

        status.setText(
                "PRESET A / B / C READ SUCCESSFULLY"
        );
    }

    // =====================================
    // BACKUP
    // =====================================

    private void saveBackup() {

        if (!presetsLoaded) {
            status.setText("READ PRESETS FIRST");
            return;
        }

        Intent intent =
                new Intent(Intent.ACTION_CREATE_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "CubeBaby_Backup.cbth"
        );

        startActivityForResult(
                intent,
                CREATE_BACKUP
        );
    }

    // =====================================
    // RESTORE BACKUP TO EDITOR
    // =====================================

    private void openBackup() {

        if (verifyingWrite || waitingRead) {
            status.setText("PLEASE WAIT FOR CURRENT OPERATION");
            return;
        }

        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(
                intent,
                RESTORE_BACKUP
        );
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

                try (java.io.OutputStream stream =
                        getContentResolver().openOutputStream(
                                data.getData())) {

                    if (stream == null) {
                        status.setText("BACKUP FAILED");
                        return;
                    }

                    stream.write(presetData);
                }

                status.setText(
                        "BACKUP SAVED SUCCESSFULLY"
                );
            }

            if (requestCode == RESTORE_BACKUP) {

                ByteArrayOutputStream buffer =
                        new ByteArrayOutputStream();

                try (InputStream stream =
                        getContentResolver().openInputStream(
                                data.getData())) {

                    if (stream == null) {
                        status.setText("RESTORE FAILED");
                        return;
                    }

                    byte[] temp = new byte[1024];
                    int count;

                    while ((count = stream.read(temp)) != -1) {
                        buffer.write(temp, 0, count);
                    }
                }

                byte[] restored = buffer.toByteArray();

                if (restored.length != LENGTH) {
                    status.setText("INVALID BACKUP FILE");
                    return;
                }

                presetData = Arrays.copyOf(
                        restored,
                        LENGTH
                );

                presetsLoaded = true;

                updateEditor();

                status.setText(
                        "BACKUP RESTORED TO EDITOR.\n"
                        + "PRESS SAVE TO PEDAL TO SEND IT."
                );
            }

        } catch (Exception e) {

            status.setText(
                    "FILE ERROR: " + e.getMessage()
            );
        }
    }

    // =====================================
    // DISCONNECT
    // =====================================

    private void stopConnection() {

        waitingRead = false;
        waitingWriteAck = false;
        receiving = false;
        verifyingWrite = false;

        expectedWriteData = null;

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

        if (status != null) {
            status.setText("MIDI DISCONNECTED");
        }
    }

    @Override
    protected void onDestroy() {

        stopConnection();

        super.onDestroy();
    }
}
