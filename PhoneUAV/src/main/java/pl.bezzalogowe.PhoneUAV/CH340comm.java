package pl.bezzalogowe.PhoneUAV;

import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.widget.LinearLayout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;

/*
UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=6790,mProductId=29987,mClass=255,mSubclass=0,mProtocol=0,mManufacturerName=null,mProductName=TOROBOT Virtual COM Portl,mVersion=1.16,mSerialNumber=null,mConfigurations=[
UsbConfiguration[mId=1,mName=null,mAttributes=128,mMaxPower=48,mInterfaces=[
UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=255,mSubclass=1,mProtocol=2,mEndpoints=[
UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=32,mInterval=0]
UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=32,mInterval=0]
UsbEndpoint[mAddress=129,mAttributes=3,mMaxPacketSize=8,mInterval=1]]]]
*/

public class CH340comm {
    public static final String ACTION_USB_PERMISSION = "cn.wch.wchusbdriver.USB_PERMISSION";
    public static CH34xUARTDriver driver;
    public byte[] writeBuffer = new byte[512];
    public byte[] readBuffer = new byte[512];
    public readThread handlerThread;
    public boolean isOpen;

    /** 500 to 2500 μs pulse width range for servos */

    public int servoElevatorMin = 700;
    public int servoElevatorMax = 2300;
    public int servoRudderMin = 500;
    public int servoRudderMax = 2500;
    public int servoElevonMin = 500;
    public int servoElevonMax = 2500;

    /** 1000 to 2000 μs pulse width range for ESC */
    int throttleMin = 1000;
    int throttleMax = 2000;
    float throttleDenominator;

    ScheduledExecutorService executor, executorBlink;
    boolean pwmThread = false;

    /** http://www.wch.cn/download/CH341SER_ANDROID_ZIP.html */

    MainActivity main;
    public  CH340comm(MainActivity argActivity) {
        main = argActivity;
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            /** Prints feedback from device in a text widget. */
            //main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, msg.toString()));
        }
    };
    boolean blink = false;

    private static byte[] toByteArray(String arg) {
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

    public void open() {
        int retval;
        if (!isOpen) {
            driver = new CH34xUARTDriver((UsbManager) main.getSystemService(main.USB_SERVICE), main, ACTION_USB_PERMISSION);
            retval = driver.ResumeUsbList();
            if (retval == -1) {
                main.logObject.saveComment("error: " + "Open device failed!");

                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Open device failed!"));
                driver.CloseDevice();
            } else if (retval == 0) {
                if (!driver.UartInit()) {
                    main.logObject.saveComment("error: " + "Device initialization failed!");

                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Device initialization failed!"));
                    return;
                }
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Opened the device successfully!"));
                try {
                    config();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                main.usbButton.setText("USB\n\"0\"");
                isOpen = true;
                new readThread().start();

                /** sends feedback */
                Thread feedbackC340Thread = new Thread(new Wrap());
                feedbackC340Thread.start();
            } else {
                main.logObject.saveComment("error: " + "ResumeUsbList == " + retval);

                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ResumeUsbList == " + retval));
            }
        }
    }

    public void close()
    {
        if (isOpen) {
            main.logObject.saveComment("error: " + "Disconnected");

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Disconnected"));
            main.usbButton.setText("USB\n\"1\"");
            driver.CloseDevice();
            driver = null;
            isOpen = false;
        }
    }

    public void config() {
        /* 9600,14400,19200,28800,33600,38400,56000,57600,76800,115200,128000,153600,230400,460800,921600,1500000,2000000 */
        int baudRate = 115200;
        byte dataBit = 8;
        byte stopBit = 1;
        byte parity = 0;
        byte flowControl = 0;

        if (driver.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings are successful!"));
            main.outputMode = main.USC16;
        } else {
            main.logObject.saveComment("error: " + "Serial settings failed!");

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings failed!"));
        }
    }

    public void write(String command) {
        byte[] to_send = toByteArray(command);
        if (driver != null) {
            int retval = driver.WriteData(to_send, to_send.length);
            if (retval < 0)
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Write failed!"));
        }
    }

    public void SetPositions(int channel1, int value1, int channel2, int value2, int time) {
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
            message = message + "#" + Math.abs(channel2) + "P" + inverted + "T" + time;
        } else {
            message = message + "#" + channel2 + "P" + value2 + "T" + time;
        }
        this.write(message);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
    }

    public void SetPositions(int[] channels1, int value1, int[] channels2, int value2, int time) {
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
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositions(int[] channels1, int value1, int[] channels2, int value2, int time, int min, int max) {
        /** Sets two different positions to two arrays of servos, with range of movement given */

        float multiplier = (float) (max - min) / 2000;
        String message = "";

        if (channels1.length > 0 && channels2.length > 0) {
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    int compensated = (int) ((2500 - value1) * multiplier + min);
                    message += "#" + Math.abs(channels1[i]) + "P" + compensated;
                } else {
                    int compensated = (int) ((value1 - 500) * multiplier + min);
                    message += "#" + channels1[i] + "P" + compensated;
                }
            }

            for (int j = 0; j < channels2.length; j++) {
                if (channels2[j] < 0) {
                    int compensated = (int) ((2500 - value2) * multiplier + servoElevatorMin);
                    message += "#" + Math.abs(channels2[j]) + "P" + compensated;
                } else {
                    int compensated = (int) ((value2 - 500) * multiplier + servoElevatorMin);
                    message += "#" + channels2[j] + "P" + compensated;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPosition(int[] channels, int width, int time) {
        /** Sets position of one array of servos */

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
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetThrottle(int[] channels, int time) {
        /** Sets position of one array of ESC channels */

        String message = "";

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int inverted = 3000 - main.thr;
                    message += "#" + Math.abs(channels[i]) + "P" + inverted;
                } else {
                    message += "#" + channels[i] + "P" + main.thr;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPosition(int[] channels, int width, int time, int min, int max) {
        /** Sets position of one array of servos, with range of movement given */

        float multiplier = (float) (max - min) / 2000;
        String message = "";

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int compensated = (int) ((2500 - width) * multiplier + min);
                    message += "#" + Math.abs(channels[i]) + "P" + compensated;
                } else {
                    int compensated = (int) ((width - 500) * multiplier + min);
                    message += "#" + channels[i] + "P" + compensated;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositionPrecisely(int[] channels, int position, int time, int min, int max) {
        /** Sets position of an array of servos more precisely, with range of movement given */

        float multiplier = (float) (max - min) / 65534;
        String message = "";
        int compensated = 1500;

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    /** scale down from (-32767, 32767) to (-1000, 1000) range then offset to (~500, ~2500) range */
                    compensated = (int) ((1 - position) * multiplier + min + (max - min) / 2);
                    message += "#" + Math.abs(channels[i]) + "P" + compensated;
                } else {
                    compensated = (int) ((1 + position) * multiplier + min + (max - min) / 2);
                    message += "#" + channels[i] + "P" + compensated;
                }
            }

            message += "T" + time;
            this.write(message);
            if (channels.equals(main.outputsFlaperonLeft) || channels.equals(main.outputsFlaperonRight)) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "outputsFlaperonLeft or outputsFlaperonRight"));
            } else if (channels.equals(main.outputsElevonLeft) || channels.equals(main.outputsElevonRight)) {
                if (channels.equals(main.outputsElevonLeft)) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "elevonLeft (ch " + main.outputsElevonLeft[0] + "): " + compensated));
                }
                if (channels.equals(main.outputsElevonRight)) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "elevonRight (ch " + main.outputsElevonRight[0] + "): " + compensated));
                }
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
            }
        }
    }

    public void SetPositionsPrecisely(int[] channels1, int position1, int[] channels2, int position2, int time, int min, int max) {
        /** Sets positions of two arrays of servos more precisely, with range of movement given */

        float multiplier = (float) (max - min) / 65534;
        String message = "";
        int compensated1 = 1500;
        int compensated2 = 1500;

        if (channels1.length > 0 && channels2.length > 0) {
            /* left elevon */
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    compensated1 = (int) ((1 - position1) * multiplier + min + (max - min) / 2);
                    message += "#" + Math.abs(channels1[i]) + "P" + compensated1;
                } else {
                    compensated1 = (int) ((1 + position1) * multiplier + min + (max - min) / 2);
                    message += "#" + channels1[i] + "P" + compensated1;
                }
            }

            /* right elevon */
            for (int i = 0; i < channels2.length; i++) {
                if (channels2[i] < 0) {
                    compensated2 = (int) ((1 - position2) * multiplier + min + (max - min) / 2);
                    message += "#" + Math.abs(channels2[i]) + "P" + compensated2;
                } else {
                    compensated2 = (int) ((1 + position2) * multiplier + min + (max - min) / 2);
                    message += "#" + channels2[i] + "P" + compensated2;
                }
            }

            message += "T" + time;
            this.write(message);
            if (channels1.equals(main.outputsFlaperonLeft) && channels2.equals(main.outputsFlaperonRight)) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "ch " + main.outputsElevonLeft[0] + ": " + compensated1 + "ch " + main.outputsElevonRight[0] + ": " + compensated2));
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
            }
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

            executor.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    /* 1000 μs through 2000 μs range */
                    SetThrottle(main.outputsThrottle, (byte) 100);
                }
            }, 20, 20, TimeUnit.MILLISECONDS);

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM started (USC-16)"));
            main.sendTelemetry(7, 1);
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

            SetThrottle(main.outputsThrottle, (byte) 4);

            try {
                executor.shutdownNow();
                if (executor.isShutdown()) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM stopped (USC-16)"));
                }
            } catch (Exception e) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM not stopped (USC-16)"));
            }
            executor = null;
            main.sendTelemetry(7, 0);
        }
        pwmThread = false;
    }

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

    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                main.sendTelemetry((byte) 12, isOpen ? true : false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
