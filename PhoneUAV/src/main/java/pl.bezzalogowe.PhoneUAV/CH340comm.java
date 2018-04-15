package pl.bezzalogowe.PhoneUAV;

import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.widget.LinearLayout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;

public class CH340comm {
    /** http://www.wch.cn/download/CH341SER_ANDROID_ZIP.html */

    MainActivity main;
    public static CH34xUARTDriver driver;
    private static final String ACTION_USB_PERMISSION = "cn.wch.wchusbdriver.USB_PERMISSION";
    public byte[] writeBuffer = new byte[512];
    public byte[] readBuffer = new byte[512];
    public readThread handlerThread;
    public boolean isOpen;
    boolean pwmThread = false;

    ScheduledExecutorService executor, executorBlink;

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            /** Prints feedback from device in a text widget. */
/*
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, msg.toString()));
*/
        }
    };

    private class readThread extends Thread {
        public void run() {
            byte[] buffer = new byte[4096];
            while (true) {
                Message msg = Message.obtain();
                if (!isOpen) {
                    break;
                }
                int length = driver.ReadData(buffer, 4096);
                if (length > 0) {
                    String recv = new String(buffer, 0, length);
                    msg.obj = recv;
                    handler.sendMessage(msg);
                }
            }
        }
    }

    public void open(MainActivity argActivity) {
        main = argActivity;
        int retval;
        driver = new CH34xUARTDriver((UsbManager) main.getSystemService(main.USB_SERVICE), main, ACTION_USB_PERMISSION);
        if (!isOpen) {
            retval = driver.ResumeUsbList();
            if (retval == -1) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Open device failed!"));
                driver.CloseDevice();
            } else if (retval == 0) {
                if (!driver.UartInit()) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Device initialization failed!"));
                    return;
                }
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Opened the device successfully!"));
                isOpen = true;
                //openButton.setText("Close");
                //configButton.setEnabled(true);
                //writeButton.setEnabled(true);
                new readThread().start();
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ResumeUsbList > 0"));

            }
        } else {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "isOpen"));
            driver.CloseDevice();
            isOpen = false;
        }
    }

    public void config() {
        int baudRate = 9600;
        byte dataBit = 8;
        byte stopBit = 1;
        byte parity = 0;
        byte flowControl = 0;

        if (driver.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings are successful!"));
            main.outputMode = main.USC16;
        } else {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings failed!"));
        }
    }

    private byte[] toByteArray2(String arg) {
        if (arg != null) {
        /* First remove the String ' ', and then convert the String to a char array */
            char[] NewArray = new char[1000];
            char[] array = arg.toCharArray();
            int length = 0;
            for (int i = 0; i < array.length; i++) {
                if (array[i] != ' ') {
                    NewArray[length] = array[i];
                    length++;
                }
            }
            NewArray[length] = 0x0D;
            NewArray[length + 1] = 0x0A;
            length += 2;

            byte[] byteArray = new byte[length];
            for (int i = 0; i < length; i++) {
                byteArray[i] = (byte) NewArray[i];
            }
            return byteArray;

        }
        return new byte[]{};
    }

    public void write(String command) {
        //byte[] to_send = toByteArray(writeText.getText().toString());
        byte[] to_send = toByteArray2(command);
        int retval = driver.WriteData(to_send, to_send.length);
        if (retval < 0)
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Write failed!"));
    }

    public void SetPosition(int channel, int width, byte speed) {
        /** Sets position of one servo */

        String message;
        if (channel < 0) {
            // if channel number is negative, the servo is inverted
            int inverted = 3000 - width;
            message = "#" + Math.abs(channel) + "P" + inverted + "T" + Byte.toString(speed);
        } else {
            message = "#" + channel + "P" + width + "T" + Byte.toString(speed);
        }
        this.write(message);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
    }

    public void SetPositions(int channel1, int value1, int channel2, int value2, byte speed) {
        /** Sets two different positions of two servos */

        String message;
        if (channel1 < 0) {
            int inverted = 3000 - value1;
            message = "#" + Math.abs(channel1) + "P" + inverted;
        } else {
            message = "#" + channel1 + "P" + value1;
        }

        if (channel2 < 0) {
            int inverted = 3000 - value2;
            message = message + "#" + Math.abs(channel2) + "P" + inverted + "T" + Byte.toString(speed);
        } else {
            message = message + "#" + channel2 + "P" + value2 + "T" + Byte.toString(speed);
        }
        this.write(message);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
    }

    public void SetPositions(int[] channels1, int value1, int[] channels2, int value2, byte speed) {
        /** Sets two different positions to two arrays of servos */

        String message = "";
        if (channels1.length > 0 && channels2.length > 0) {
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    int inverted = 3000 - value1;
                    message += "#" + Math.abs(channels1[i]) + "P" + inverted;
                } else {
                    message += "#" + channels1[i] + "P" + value1;
                }
            }

            for (int j = 0; j < channels2.length; j++) {
                if (channels2[j] < 0) {
                    int inverted = 3000 - value2;
                    message += "#" + Math.abs(channels2[j]) + "P" + inverted;
                } else {
                    message += "#" + channels2[j] + "P" + value2;
                }
            }
            message += "T" + Byte.toString(speed);
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPosition(int[] channels, int width, byte speed) {
        /** Sets position of an array of servos */

        String message = "";
        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int inverted = 3000 - width;
                    message += "#" + Math.abs(channels[i]) + "P" + inverted;
                } else {
                    message += "#" + channels[i] + "P" + width;
                }
            }
            message += "T" + Byte.toString(speed);
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void startPWM(final MainActivity argActivity, int value) {
        if (!pwmThread) {
            executor = Executors.newSingleThreadScheduledExecutor();
            main = argActivity;

            main.startPWMminButton.setVisibility(LinearLayout.GONE);
            main.startPWMmaxButton.setVisibility(LinearLayout.GONE);

            main.throttleUpButton.setVisibility(LinearLayout.VISIBLE);
            main.throttleDownButton.setVisibility(LinearLayout.VISIBLE);
            main.throttleMinButton.setVisibility(LinearLayout.VISIBLE);
            main.throttleMaxButton.setVisibility(LinearLayout.VISIBLE);

            main.throttleStopButton.setAlpha((float) 1);

            main.thr = value;
            float denominator = (main.throttleMax - main.throttleMin) / 100;

            main.seekbarTHROT.setProgress(
                    (int) ((main.thr - main.throttleMin) /
                            denominator)
            );

            executor.scheduleAtFixedRate(new Runnable() {
                public void run() {
            /* 500 ms through 2500 ms range */
                    SetPosition(main.outputsThrottle, main.thr, (byte) 100);
                }
            }, 20, 20, TimeUnit.MILLISECONDS);

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM started"));
            main.sendTelemetry(7, 1);
            startBlinking();
        }
        pwmThread = true;
    }

    public void stopPWM(MainActivity argActivity) {
        if (pwmThread) {
            main = argActivity;

            main.throttleMinButton.setVisibility(LinearLayout.GONE);
            main.throttleMaxButton.setVisibility(LinearLayout.GONE);
            main.throttleUpButton.setVisibility(LinearLayout.INVISIBLE);
            main.throttleDownButton.setVisibility(LinearLayout.INVISIBLE);

            main.startPWMminButton.setVisibility(LinearLayout.VISIBLE);
            main.startPWMmaxButton.setVisibility(LinearLayout.VISIBLE);

            main.throttleStopButton.setAlpha((float) 0.5);

            main.seekbarTHROT.setProgress((main.throttleMin - 500) / 20);
            SetPosition(main.outputsThrottle, main.throttleMin, (byte) 4);

            try {
                executor.shutdownNow();
                if (executor.isShutdown()) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM stopped"));
                }
            } catch (Exception e) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM not stopped"));
            }
            executor = null;
            main.sendTelemetry(7, 0);
            stopBlinking();
        }
        pwmThread = false;
    }

    boolean blink = false;

    public void blink() {
        if (blink) {
            main.update.updateConversationHandler.post(new updateAlphaThread(main.seekbarTHROT, (float) 0.5));
            blink = false;
        } else {
            main.update.updateConversationHandler.post(new updateAlphaThread(main.seekbarTHROT, (float) 1));
            blink = true;
        }
    }

    public void startBlinking() {
        executorBlink = Executors.newSingleThreadScheduledExecutor();
        executorBlink.scheduleAtFixedRate(new Runnable() {
            public void run() {
                blink();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void stopBlinking() {
        try {
            executorBlink.shutdownNow();
            if (executorBlink.isShutdown()) {
            }
        } catch (Exception e) {
        }
        executorBlink = null;
    }
}
