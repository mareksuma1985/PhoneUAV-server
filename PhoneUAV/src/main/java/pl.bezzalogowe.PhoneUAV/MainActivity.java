package pl.bezzalogowe.PhoneUAV;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements SensorEventListener {
    final MainActivity main = this;
    final int FORMAT_ASCII = 0;
    final int FORMAT_HEX = 1;
    final int FORMAT_DEC = 2;
    /* declare FT311 interface variables */
    public FT311PWMInterface pwmInterface;
    public FT311UARTInterface uartInterface;
    /* thread to read the data */
    public handler_thread handlerThread;
    byte FT311D_PWM = 0x00;
    byte FT311D_UART = 0x01;
    byte USC16 = 0x02;
    int inputFormat = FORMAT_DEC;
    ServerUDP serverUDP = new ServerUDP(main);
    ServerTCP serverTCP = new ServerTCP(main);
    UpdateUI update = new UpdateUI();
    Input inputObject = new Input();
    SK18comm sk18commObject = new SK18comm();
    CH340comm ch340commObject;
    /* API≥21 */
    Camera2API camObjectLolipop = new Camera2API();
    /* camera number */
    String cameraID;
    double[] device_orientation = new double[3];
    Accelerometer accObject = new Accelerometer();
    Gravity gravityObject = new Gravity();
    Magnetometer magObject = new Magnetometer();
    Barometer pressureObject = new Barometer();
    Location locObject = new Location();
    LogGPX logObject = new LogGPX();
    // saves trackpoints to csv file
    // LogCSV logObject = new LogCSV();
    Autopilot autopilot = new Autopilot();
    String serverpath;
    /* layout components */
    TextView dutyCycleRDR, dutyCycleAIL, dutyCycleELEV, dutyCycleTHROT, dutyCycleELEV_L, dutyCycleELEV_R,
            axis_text_x, axis_text_y, axis_text_z,
            angle_text_pitch, angle_text_roll, angle_text_heading, altitude_text, text_server, text_feedback;
    Button usbButton, resetButton, torchButton, photoButton, videoButton;
    Button startPWMminButton, startPWMmaxButton, throttleUpButton, throttleDownButton, throttleStopButton;
    Button throttleMinButton, throttleMaxButton;
    CheckBox checkbox_stabilistation_pitch, checkbox_stabilistation_roll;
    int period;
    byte outputMode, protocol;
    SeekBar seekbarRDR, seekbarAIL, seekbarELEV, seekbarTHROT, seekbarELEV_L, seekbarELEV_R;

    /** USC-16: output channels for each control surface
     * You can use more than one servo per surface */

    int[] outputsAileron,
          outputsFlaperonLeft,
          outputsFlaperonRight,
          outputsElevator,
          outputsThrottle,
          outputsRudder,
          outputsElevonLeft,
          outputsElevonRight;
    int ail, ele, thr, rdr, flaps;

    // 500 to 2500 milliseconds pulse width range
    int throttleMin = 500;
    int throttleMax = 2500;
    int throttleDenominator;
    int elevatorTrim = 0;

    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            for (int i = 0; i < uartInterface.actualNumBytes[0]; i++) {
                uartInterface.readBufferToChar[i] = (char) uartInterface.readBufferFB[i];
            }
            appendData(uartInterface.readBufferToChar, uartInterface.actualNumBytes[0]);
        }
    };

    Handler handlerCamera = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.arg1 == 1) {
                camObjectLolipop.startRecord();
            } else {
                camObjectLolipop.stopRecord();
            }
            return false;
        }
    });

    Handler handlerTorch = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.arg1 == 1) {
                main.torchButton.setText("\u00D7");
                main.camObjectLolipop.torch = true;
            } else {
                main.torchButton.setText("\uD83D\uDCA1");
                main.camObjectLolipop.torch = false;
            }
            return false;
        }
    });

    /** USB input data handler */
    private class handler_thread extends Thread {
        Handler mHandler;

        handler_thread(Handler h) {
            mHandler = h;
        }

        public void run() {
            Message msg;
            while (true) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }

                uartInterface.status = uartInterface.ReadData(4096, uartInterface.readBufferFB, uartInterface.actualNumBytes);

                if (uartInterface.status == 0x00 && uartInterface.actualNumBytes[0] > 0) {
                    msg = mHandler.obtainMessage();
                    mHandler.sendMessage(msg);
                }
            }
        }
    }

    public static void powerOTG(boolean flipBool) {
        /** http://stackoverflow.com/questions/20932102/execute-shell-command-from-android */
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            String flipString = flipBool ? "1" : "0";
            outputStream.writeBytes("echo \"" + flipString + "\" >> /sys/kernel/debug/regulator/8226_smbbp_otg/enable\n");
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();

            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (shouldAskPermissions()) {
            askPermissions();
        }

/** Path to preferences file: /data/data/pl.bezzalogowe.PhoneUAV/shared_prefs/
Root privilege required to create or modify file by hand */

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);

/** https://www.ef3m.pl/pl/blog/Nadajnik-i-odbiornik-2,4GHz/12 */
/** If flaperon or elevon servos are installed in opposite directions, one flaperons' channels should be negative */

        outputsAileron = getOutputs("outputs-aileron", "8");
        outputsFlaperonLeft = getOutputs("outputs-flaperon-left", "9");
        outputsFlaperonRight = getOutputs("outputs-flaperon-right", "-10");
        outputsElevator = getOutputs("outputs-elevator", "2");
        outputsThrottle = getOutputs("outputs-throttle", "3");
        outputsRudder = getOutputs("outputs-rudder", "4");
        outputsElevonLeft = getOutputs("outputs-elevon-left", "5");
        outputsElevonRight = getOutputs("outputs-elevon-right", "-6");

        Log.d("getPreference", "channel order: " + outputsAileron[0] + ",\t" + outputsFlaperonLeft[0] + ",\t" + outputsFlaperonRight[0] + ",\n" + outputsElevator[0] + ",\t" + outputsThrottle[0] + ",\t" + outputsRudder[0] + ",\t" + outputsElevonLeft[0] + ",\t" + outputsElevonRight[0] + "\n");

/* angle between the device and vehicle orientation */

/*
90 degr: <
180 degr: <<
270 degr: >
*/

        String orientation_string = null;
        try {
            orientation_string = settings.getString("orientation", "0");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        if (!orientation_string.isEmpty()) {
            if (orientation_string.equals("180"))
                device_orientation[2] = 180;
            else
                device_orientation[2] = 0;
        }

        int orientation_int = (int) device_orientation[2];
        switch (orientation_int) {
            case 270:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
            case 180:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            case 90:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        /** https://codepoints.net/ */

        try {
            serverpath = settings.getString("server-path", "http://bezzalogowe.pl/uav/");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        /* TCP or UDP */
        if (settings.getString("protocol", "UDP").equals("TCP")) {
            protocol = 0x01;
        } else {
            protocol = 0x00;
        }

        /** https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_FACING */
        /* LENS_FACING_BACK or LENS_FACING_FRONT assuming that back has id 0 and front has id 1 */
        if (settings.getString("camera-facing", "1").equals("2")) {
            cameraID = "2";
        } else if (settings.getString("camera-facing", "1").equals("0")) {
            cameraID = "1";
        } else {
            cameraID = "0";
        }

        usbButton = (Button) findViewById(R.id.usbButton);
        usbButton.setText("\uD83D\uDD0C");

        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setText("\uD83D\uDD03");

        torchButton = (Button) findViewById(R.id.torchButton);
        torchButton.setText("\uD83D\uDCA1");

        photoButton = (Button) findViewById(R.id.photoButton);
        photoButton.setText("\uD83D\uDCF7");

        videoButton = (Button) findViewById(R.id.videoStartButton);
        videoButton.setText("\u25CF");

        dutyCycleRDR = (TextView) findViewById(R.id.DutyCycle1Text);
        dutyCycleAIL = (TextView) findViewById(R.id.DutyCycle2Text);
        dutyCycleELEV = (TextView) findViewById(R.id.DutyCycle3Text);
        dutyCycleTHROT = (TextView) findViewById(R.id.DutyCycle4Text);
        dutyCycleELEV_L = (TextView) findViewById(R.id.DutyElevonLeftText);
        dutyCycleELEV_R = (TextView) findViewById(R.id.DutyCycleElevonRightText);

        seekbarRDR = (SeekBar) findViewById(R.id.Seekbar1);
        seekbarAIL = (SeekBar) findViewById(R.id.Seekbar2);
        seekbarELEV = (SeekBar) findViewById(R.id.Seekbar3);
        seekbarTHROT = (SeekBar) findViewById(R.id.Seekbar4);
        seekbarTHROT.setAlpha((float) 0.5);
        seekbarELEV_L = (SeekBar) findViewById(R.id.SeekbarElevonLeft);
        seekbarELEV_R = (SeekBar) findViewById(R.id.SeekbarElevonRight);

        startPWMminButton = (Button) findViewById(R.id.StartPWMminButton);
        startPWMmaxButton = (Button) findViewById(R.id.StartPWMmaxButton);

        throttleDownButton = (Button) findViewById(R.id.ThrottleDownButton);
        throttleDownButton.setText("\u22f1");

        throttleUpButton = (Button) findViewById(R.id.ThrottleUpButton);
        throttleUpButton.setText("\u22f0");

        throttleStopButton = (Button) findViewById(R.id.ThrottleStopButton);
        throttleStopButton.setAlpha((float) 0.5);

        /** starts PWM thread with throttle set to minimum */
        startPWMminButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    ch340commObject.startPWM(main, throttleMin);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.startPWM(main, throttleMin);
                }
                /* PWM */
            }
        });

        /** starts PWM thread with throttle set to maximum */
        startPWMmaxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    ch340commObject.startPWM(main, throttleMax);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.startPWM(main, throttleMax);
                }
                /* PWM */
            }
        });

        /** sets throttle to minimum */
        throttleMinButton = (Button) findViewById(R.id.ThrottleMinButton);
        throttleMinButton.setText("\u22a2");
        throttleMinButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                thr = throttleMin;
                if (outputMode == USC16) {
                    ch340commObject.SetPosition(outputsThrottle, thr, (byte) 100);
                }
                seekbarTHROT.setProgress(inputObject.scaleDown(thr));
            }
        });

        /** sets throttle to maximum */
        throttleMaxButton = (Button) findViewById(R.id.ThrottleMaxButton);
        throttleMaxButton.setText("\u22a3");
        throttleMaxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                thr = throttleMax;
                if (outputMode == USC16) {
                    ch340commObject.SetPosition(outputsThrottle, thr, (byte) 100);
                }
                seekbarTHROT.setProgress(inputObject.scaleDown(thr));
            }
        });

        /** slowly throttles down */
        throttleDownButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sk18commObject.startThrottlingDown(main);
            }
        });

        /** slowly throttles up */
        throttleUpButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sk18commObject.startThrottlingUp(main);
            }
        });

        throttleStopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    ch340commObject.stopPWM(main);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.stopPWM(main);
                }
                /* PWM */
            }
        });

        text_server = (TextView) findViewById(R.id.text_server);
        text_feedback = (TextView) findViewById(R.id.text_feedback);
        axis_text_x = (TextView) findViewById(R.id.Axis0Text);
        axis_text_y = (TextView) findViewById(R.id.Axis1Text);
        axis_text_z = (TextView) findViewById(R.id.Axis2Text);

        text_feedback.setTextColor(Color.rgb(0, 255, 0));

        angle_text_pitch = (TextView) findViewById(R.id.Angle0Text);
        angle_text_roll = (TextView) findViewById(R.id.Angle1Text);
        angle_text_heading = (TextView) findViewById(R.id.Angle2Text);
        altitude_text = (TextView) findViewById(R.id.AltitudeText);

        checkbox_stabilistation_pitch = (CheckBox) findViewById(R.id.stabilisation_pitch);
        checkbox_stabilistation_roll = (CheckBox) findViewById(R.id.stabilisation_roll);

        // getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (protocol == 0x01) {
            serverTCP.startServer();
        } else {
            serverUDP.startServer();
        }

        update.updateConversationHandler = new Handler();
        pwmInterface = new FT311PWMInterface(this);
        resetFT311();
        sk18commObject = new SK18comm();
        SharedPreferences sharePrefSettings = getSharedPreferences("UARTLBPref", 0);
        sk18commObject.getPreference(sharePrefSettings);
        uartInterface = new FT311UARTInterface(this, sharePrefSettings);
        initiateFT311();

        ch340commObject = new CH340comm();
        initiateCH340();

        handlerThread = new handler_thread(handler);
        handlerThread.start();

        //FIXME: Not all ports are free - this may cause problems.
        serverTCP.server_port = Integer.valueOf(settings.getString("server-port", "6000"));
        period = Integer.valueOf(settings.getString("period", "3"));

        throttleMin = Integer.valueOf(settings.getString("throttle-min", "500"));
        throttleMax = Integer.valueOf(settings.getString("throttle-max", "2500"));

        startPWMminButton.setText(Integer.toString(throttleMin));
        startPWMmaxButton.setText(Integer.toString(throttleMax));

        if ((throttleMax - throttleMin) != 0) {
            throttleDenominator = (65536 / (throttleMax - throttleMin));
        } else {
            throttleDenominator = 65536 / 2000;
        }

        //FIXME: execute SetPeriod() after ResumeAccessory, not after fixed time.
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                pwmInterface.SetPeriod(period);
            }
        }, 2000);

        magObject.magnetic_declination = Double.valueOf(settings.getString("magnetic-declination", "0,0").replaceAll(",", "."));
        Log.d("magnetic-declination", "read: " + magObject.magnetic_declination);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        pressureObject.pressure_mean_sealevel = Float.valueOf(settings.getString("pressure-mean-sealevel", "1013,25").replaceAll(",", "."));
        Log.d("pressure-mean-sealevel", "read: " + pressureObject.pressure_mean_sealevel);

        inputObject.startController(this);
        accObject.startAccelerometer(this);
        gravityObject.startGravity(this);
        magObject.startMagnetometer(this);
        pressureObject.startBarometer(this);
        locObject.startLocation(this);
        logObject.startLog(this);
        autopilot.startAutopilot(this);

        if (Build.VERSION.SDK_INT >= 21) {
            /* API≥21 */
            setupButtonsLolipop();
        }

        setupWidgets();
        serverTCP.displayAddress();
    }

    public void savePeriodPreference() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("period", String.valueOf(period));
        editor.commit();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.set_port:
                setPortNumber();
                return true;
            case R.id.switch_orientation:
                switchOrientation();
                return true;
            case R.id.power_to_usb:
                powerToUSB();
                return true;
            case R.id.get_info:
                getInfo();
                return true;
            case R.id.options:
                showOptions();
                return true;
            case R.id.help:
                openPage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public int[] getOutputs(String key, String defaults) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        String outputsAsSting = settings.getString(key, defaults);
        if (outputsAsSting.length() > 0) {
            String[] tokens = outputsAsSting.split(",");
            int[] outputs = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                outputs[i] = Integer.valueOf(tokens[i]);
            }

            for (int i = 0; i < outputs.length; i++) {
                Log.d("getOutputs", key + ": " + outputs[i]);
            }
            return outputs;
        } else {
            return null;
        }
    }

    public void initiateCH340() {
        UsbManager usbmanager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        HashMap devicesList = usbmanager.getDeviceList();
        Object[] devices = devicesList.values().toArray();
        for (Object device : devices) {
            if (device.toString().indexOf("mVendorId=6790") != -1 && device.toString().indexOf("mProductId=29987") != -1) {
                update.updateConversationHandler.post(new updateTextThread(text_server, "USC-16 mode"));
                outputMode = USC16;

                try {
                    ch340commObject.open(this);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (ch340commObject.isOpen) {
                        ch340commObject.config();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initiateFT311() {
        //detects "mModel=FTDIPWMDemo" or "mModel=FTDIUARTDemo"
        UsbManager usbmanager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbAccessory[] usbAccessories = usbmanager.getAccessoryList();
        if (usbAccessories != null) {
            for (UsbAccessory object : usbAccessories) {
                update.updateConversationHandler.post(new updateTextThread(text_server, "accessory: " + object.toString()));
            }

            try {
                String model = usbAccessories[0].getModel();
                if (model.equals("FTDIPWMDemo")) {
                    pwmInterface.ResumeAccessory();
                    update.updateConversationHandler.post(new updateTextThread(text_server, "PWM mode"));
                    Log.d("initiateFT311", "PWM mode");
                    outputMode = FT311D_PWM;
                } else if (model.equals("FTDIUARTDemo")) {
                    if (uartInterface.ResumeAccessory() == 0) {
                        update.updateConversationHandler.post(new updateTextThread(text_server, "UART mode"));
                        Log.d("initiateFT311", "UART mode");
                        uartInterface.SetConfig(sk18commObject.baudRate, sk18commObject.dataBit, sk18commObject.stopBit, sk18commObject.parity, sk18commObject.flowControl);
                        outputMode = FT311D_UART;
                    } else
                        update.updateConversationHandler.post(new updateTextThread(text_server, "UART mode FAIL"));
                    Log.d("initiateFT311", "UART mode FAIL");
                } else {

                    update.updateConversationHandler.post(new updateTextThread(text_server, "unknown accessory: " + model));
                    Log.d("initiateFT311", "unknown accessory");
                }
            } catch (Exception e) {
                update.updateConversationHandler.post(new updateTextThread(text_server, "error: " + e.toString()));
                e.printStackTrace();
            }

        } else {
            update.updateConversationHandler.post(new updateTextThread(text_server, "accessory not attached"));
            Log.d("initiateFT311", "accessory not attached");
        }
    }

    protected void resetFT311() {
        pwmInterface.Reset();
        seekbarRDR.setProgress(50);
        seekbarAIL.setProgress(50);
        seekbarELEV.setProgress(50);
        seekbarTHROT.setProgress((throttleMin - 500) / 20);
        seekbarELEV_L.setProgress(50);
        seekbarELEV_R.setProgress(50);

        //TODO: there must at least one channel assigned, this should not be obligatory
        try {
            if (outputMode == FT311D_PWM) {
                pwmInterface.SetDutyCycle((byte) (outputsRudder[0] - 1), (byte) seekbarRDR.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsAileron[0] - 1), (byte) seekbarAIL.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevator[0] - 1), (byte) seekbarELEV.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsThrottle[0] - 1), (byte) seekbarTHROT.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevonLeft[0] - 1), (byte) seekbarELEV_L.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevonRight[0] - 1), (byte) seekbarELEV_R.getProgress());
            } else if (outputMode == FT311D_UART) {
                sk18commObject.SetPosition((byte) outputsRudder[0], seekbarRDR.getProgress() * 10, (byte) 10);
                sk18commObject.SetPosition((byte) outputsAileron[0], seekbarAIL.getProgress() * 10, (byte) 10);
                sk18commObject.SetPosition((byte) outputsElevator[0], seekbarELEV.getProgress() * 10, (byte) 10);
                sk18commObject.SetPosition((byte) outputsThrottle[0], seekbarTHROT.getProgress() * 10, (byte) 10);
                sk18commObject.SetPosition((byte) outputsElevonLeft[0], seekbarELEV_L.getProgress() * 10, (byte) 10);
                sk18commObject.SetPosition((byte) outputsElevonRight[0], seekbarELEV_R.getProgress() * 10, (byte) 10);
            } else {
                //TODO: USC-16
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("resetFT311", "some output channels are undefined");
        }
    }

    @Override
    protected void onResume() {
        // Ideally should implement onResume() and onPause() to take appropriate action when the activity looses focus
        Log.d("called", "onResume");
        super.onResume();

        /*
         accObject.accelerometerManager.registerListener(this,
		 accObject.sensorInstanceAcc, SensorManager.SENSOR_DELAY_NORMAL);
		 magObject.magnetometerManager.registerListener(this,
		 magObject.sensorInstanceMag, SensorManager.SENSOR_DELAY_NORMAL);
		 */

        if (Build.VERSION.SDK_INT >= 21) {
            /* API≥21 */
            if (camObjectLolipop.mPreviewCaptureSession == null) {
                // Preview not initialized
                camObjectLolipop.setListener(this);
                camObjectLolipop.startCameraThread();
                if (camObjectLolipop.mTextureView.isAvailable()) {
                    Log.d("camera", "surface IS available");
                    logObject.saveComment("surface IS available");
                    camObjectLolipop.setupCamera(camObjectLolipop.mTextureView.getWidth(), camObjectLolipop.mTextureView.getHeight());
                } else {
                    Log.d("camera", "surface IS NOT yet available");
                    try {
                        camObjectLolipop.cameraManager = (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
                    } catch (Exception e) {
                        Log.d("camera", "manager: " + e.toString());
                        logObject.saveComment("setup: " + e.toString());
                    }
                }
            } else {
                // Preview initialized, un-hides preview
                camObjectLolipop.mTextureView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onPause() {
        // Ideally should implement onResume() and onPause() to take appropriate
        // action when the activity looses focus
        Log.d("called", "onPause");

        if (Build.VERSION.SDK_INT >= 21) {
            //* API≥21 */
            if (camObjectLolipop.mPreviewCaptureSession == null) {
                // Preview not initialized, improbable situation
                camObjectLolipop.closeActiveCamera();
                camObjectLolipop.stopCameraThread();
            } else {
                // Preview initialized, hides preview
                camObjectLolipop.mTextureView.setVisibility(View.INVISIBLE);
            }
        }
        super.onPause();
        /*
         accObject.accelerometerManager.unregisterListener(this);
		 magObject.magnetometerManager.unregisterListener(this);
		 */
    }

    @Override
    protected void onStop() {
        Log.d("called", "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accObject.processAccelerometer(event);
                break;
            case Sensor.TYPE_GRAVITY:
                gravityObject.processGravity(event);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magObject.calculateMagnetometer(event);
                break;
            case Sensor.TYPE_PRESSURE:
                pressureObject.calculateAltitude(event);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    public void toggleStabiliseRoll(View view) {
        if (autopilot.stabilize_roll)
            autopilot.stabilize_roll = false;
        else
            autopilot.stabilize_roll = true;
    }

    public void toggleStabilisePitch(View view) {
        if (autopilot.stabilize_pitch)
            autopilot.stabilize_pitch = false;
        else
            autopilot.stabilize_pitch = true;
    }

    private void setupButtonsLolipop() {
        camObjectLolipop.createVideoFolder();
        camObjectLolipop.createImageFolder();

        camObjectLolipop.mTextureView = (TextureView) main.findViewById(R.id.preview);

        torchButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (camObjectLolipop.torch) {
                    camObjectLolipop.turnOffTorch();
                    torchButton.setText("\uD83D\uDCA1");
                } else {
                    camObjectLolipop.turnOnTorch();
                    torchButton.setText("\u00D7");
                }
            }
        });

        photoButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                camObjectLolipop.captureImage();
            }
        });

        videoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!camObjectLolipop.isRecording) {
                    camObjectLolipop.startRecord();
                } else {
                    camObjectLolipop.stopRecord();
                }
            }
        });
    }

    private void setThrottleBounds() {
        //FIXME: Setting width.
        RelativeLayout.LayoutParams seekbarParams = (RelativeLayout.LayoutParams) seekbarTHROT.getLayoutParams();
        Log.d("seekbar", "width: " + seekbarParams.width);
        View seekbarMinimum, seekbarMaximum;
        seekbarMinimum = (View) findViewById(R.id.SeekbarMinimum);
        seekbarMaximum = (View) findViewById(R.id.SeekbarMaximum);
/*
        RelativeLayout.LayoutParams seekbarMinimumParams = (RelativeLayout.LayoutParams) seekbarMinimum.getLayoutParams();
        seekbarMinimumParams.width = ((throttleMin-500)/2000) * seekbarParams.width;
        seekbarMinimum.setLayoutParams(seekbarMinimumParams);

        RelativeLayout.LayoutParams seekbarMaximumParams = (RelativeLayout.LayoutParams) seekbarMaximum.getLayoutParams();
        seekbarMaximumParams.width = ((3000-throttleMax)/2000) * seekbarParams.width;
        seekbarMaximum.setLayoutParams(seekbarMaximumParams);
*/
        seekbarMinimum.setTranslationX((int) ((throttleMin - 500) * 0.434));
        seekbarMaximum.setTranslationX((int) ((throttleMax - 2500) * 0.434));
    }

    private void setupWidgets() {
        usbButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                initiateCH340();
            }
        });

        resetButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // resetButton.setBackgroundResource(drawable.start);
                resetFT311();
            }
        });
/*
        periodButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (periodValue.length() == 0x00) {
                    periodValue.setText("3");
                }
                period = Integer.parseInt(periodValue.getText().toString());

                if (period < 1) {
                    period = 1;
                    periodValue.setText("1");
                }

                if (period > 250) {
                    period = 250;
                    periodValue.setText("250");
                }

                periodValue.setSelection(periodValue.getText().length());
                pwmInterface.SetPeriod(period);

                savePUsbManager.getInstanceeriodPreference();
            }
        });
*/

		/** rudder */
        seekbarRDR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleRDR.setText(Integer.toString(progress) + "%");
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsRudder[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsRudder[0], (byte) progress * 10, (byte) 20);
                    } else {
                        ch340commObject.SetPosition(outputsRudder[0], progress * 20 + 500, (byte) 100);
                    }
                } else {
                    // not fromUser
                }
            }
        });

        /** aileron */
        seekbarAIL.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleAIL.setText(Integer.toString(progress) + "%");
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsAileron[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsAileron[0], (byte) progress * 10, (byte) 20);
                    } else {
                        ch340commObject.SetPosition(outputsAileron[0], progress * 20 + 500, (byte) 100);
                        //TODO: take flaps into account
                        ch340commObject.SetPosition(outputsFlaperonLeft, progress * 20 + 500, (byte) 100);
                        ch340commObject.SetPosition(outputsFlaperonRight, (100 - progress) * 20 + 500, (byte) 100);
                    }
                } else {
                    dutyCycleAIL.setText("A " + Byte.toString((byte) progress) + "%");
                }
            }
        });

        /** elevator */
        seekbarELEV.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV.setText(Integer.toString(progress) + "%");
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsElevator[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsElevator[0], (byte) progress * 10, (byte) 20);
                    } else {
                        ch340commObject.SetPosition(outputsElevator[0], progress * 20 + 500, (byte) 100);
                    }
                } else {
                    dutyCycleELEV.setText("A " + Byte.toString((byte) progress) + "%");
                }
            }
        });

		/** throttle */
        seekbarTHROT.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleTHROT.setText(Integer.toString(progress) + "%");
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsThrottle[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsThrottle[0], (byte) progress * 10, (byte) 20);
                    } else {
                        thr = (progress * ((throttleMax - throttleMin) / 100) + throttleMin);
                        ch340commObject.SetPosition(main.outputsThrottle, thr, (byte) 100);
                    }
                } else {
                    // not fromUser
                }
            }
        });

/** elevons (flying wing) */

        /** left elevon */
        seekbarELEV_L.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV_L.setText(Integer.toString(progress));
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsElevonLeft[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsElevonLeft[0], (byte) progress * 10, (byte) 20);
                    } else {
                        //TODO: USC-16
                    }
                } else {
                    // not fromUser
                }
            }
        });

		/** right elevon */
        seekbarELEV_R.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV_R.setText(Integer.toString(progress));
                    if (outputMode == FT311D_PWM) {
                        pwmInterface.SetDutyCycle((byte) (outputsElevonRight[0] - 1), (byte) progress);
                    } else if (outputMode == FT311D_UART) {
                        sk18commObject.SetPosition((byte) outputsElevonRight[0], (byte) progress * 10, (byte) 20);
                    } else {
                        //TODO: USC-16
                    }
                } else {
                    // not fromUser
                }
            }
        });
        setThrottleBounds();
    }

    private void setPortNumber() {
        AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
        helpBuilder.setIcon(android.R.drawable.ic_menu_manage);
        helpBuilder.setTitle(getResources().getString(R.string.set_port_title));
        helpBuilder.setMessage(getResources().getString(R.string.set_port_msg));

        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("");
        helpBuilder.setView(input);
        helpBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {

                try {
                    serverTCP.server_port = Integer.valueOf(input.getText().toString());
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("serverport", String.valueOf(serverTCP.server_port));
                    editor.commit();

                    serverTCP.displayAddress();
                    // TODO reconnect
                    serverTCP.serverSocket.close();
                    serverTCP.serverSocket = new ServerSocket(serverTCP.server_port);
                } catch (NumberFormatException e) {
                    Log.d("NumberFormatException", "Error: " + e);
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("IOException", "Error: " + e);
                }
            }
        });
        AlertDialog helpDialog = helpBuilder.create();
        helpDialog.show();
    }

    private void switchOrientation() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        SharedPreferences.Editor editor = settings.edit();

        if (device_orientation[2] == 180) {
            device_orientation[2] = 0;
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            editor.putString("orientation", String.valueOf("0"));
            editor.commit();

        } else if (device_orientation[2] == 0) {
            device_orientation[2] = 180;
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);

            editor.putString("orientation", String.valueOf("180"));
            editor.commit();
        } else {
            device_orientation[2] = (device_orientation[2] + 180) % 360;
            if (device_orientation[2] >= 270 || device_orientation[2] <= 90)
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            else
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        }
    }

    public void powerToUSB() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_menu_preferences)
                .setTitle(getResources().getString(R.string.power_to_usb_otg_title))
                .setMessage(getResources().getString(R.string.power_to_usb_otg_msg))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            powerOTG(true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    powerOTG(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).setNeutralButton(R.string.cancel, null).show();
    }

    public void getInfo() {
        //FIXME: Doesn't work on some phones
        AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
        helpBuilder.setIcon(android.R.drawable.ic_dialog_info);
        WifiManager wifi;
        WifiInfo info;

        String frequency, pattern;
        int channelWLAN;

        helpBuilder.setTitle("getInfo");

        try {
            /* WiFi information */
            wifi = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);
            info = wifi.getConnectionInfo();
            //System.out.println(info.toString());

            if (Build.VERSION.SDK_INT <= 20) {
                pattern = "Frequency:\\s*(.*)";
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(info.toString());
                if (m.find()) {
                    frequency = m.group(1);
                } else {
                    frequency = "";
                }

                /** http://en.wikipedia.org/wiki/List_of_WLAN_channels */
                channelWLAN = ((Integer.valueOf(frequency) - 2412) / 5) + 1;

                if (channelWLAN < 0)
                    channelWLAN = 0;
                helpBuilder.setMessage("Wi-Fi channel: " + channelWLAN + info.toString().replace(", ", "\n"));
            } else {
                helpBuilder.setMessage("Wi-Fi frequency: " + info.getFrequency() + info.FREQUENCY_UNITS + "\n" +
                "Strength: " + info.getRssi() + "dBm\n" +
                "SSID: " + info.getSSID());
            }

            helpBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        AlertDialog helpDialog = helpBuilder.create();
        helpDialog.show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setTitle(getResources().getString(R.string.confirmation_title))
                .setMessage(getResources().getString(R.string.confirmation_msg))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        logObject.stopLog();

                        if (outputMode == USC16) {
                            if (ch340commObject.isOpen) {
                                ch340commObject.driver.CloseDevice();
                                ch340commObject.driver = null;
                                ch340commObject.isOpen = false;
                            }
                        } else if (outputMode == FT311D_PWM) {
                            pwmInterface.DestroyAccessory();
                        } else if (outputMode == FT311D_UART) {
                            uartInterface.DestroyAccessory(true);
                        }
                        //TODO: Must actually close activity and app.
                        finish();
                        System.exit(0);
                    }
                }).setNegativeButton(R.string.no, null).show();
    }

    private void showOptions() {
        //Intent optionsIntent = new Intent(this, OptionsActivity.class);
        Intent optionsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
        //optionsIntent.putExtra(EXTRA_MESSAGE, "settings");
        startActivity(optionsIntent);
    }

    private void openPage() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://bezzalogowe.pl/"));
        startActivity(browserIntent);
    }

    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > 22 /*Build.VERSION_CODES.LOLLIPOP_MR1*/);
    }

    protected void askPermissions() {
        String[] permissions = {
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_PHONE_STATE"
        };
        int requestCode = 200;
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    public void appendData(String s) {
        switch (inputFormat) {
            case FORMAT_HEX: {
                update.updateConversationHandler.post(new updateTextThread(text_feedback, "Hex"));
            }
            break;

            case FORMAT_DEC: {
                update.updateConversationHandler.post(new updateTextThread(text_feedback, "Dec"));
            }
            break;

            case FORMAT_ASCII:
            default:
                update.updateConversationHandler.post(new updateTextThread(text_feedback, s.toString()));
                break;
        }
    }

    public void appendData(char[] data, int len) {
        if (len >= 1)
            uartInterface.readSB.append(String.copyValueOf(data, 0, len));

        switch (inputFormat) {
            case FORMAT_HEX: {
                char[] ch = uartInterface.readSB.toString().toCharArray();
                String temp;
                StringBuilder tmpSB = new StringBuilder();
                for (int i = 0; i < ch.length; i++) {
                    temp = String.format("%02x", (int) ch[i]);

                    if (temp.length() == 4) {
                        tmpSB.append(temp.substring(2, 4));
                    } else {
                        tmpSB.append(temp);
                    }

                    if (i + 1 < ch.length) {
                        tmpSB.append(" ");
                    }
                }
                update.updateConversationHandler.post(new updateTextThread(text_feedback, tmpSB.toString()));
                tmpSB.delete(0, tmpSB.length());
            }
            break;

            case FORMAT_DEC: {
                char[] ch = uartInterface.readSB.toString().toCharArray();
                String temp;
                StringBuilder tmpSB = new StringBuilder();
                for (int i = 0; i < ch.length; i++) {
                    temp = Integer.toString((int) (ch[i] & 0xff));

                    for (int j = 0; j < (3 - temp.length()); j++) {
                        tmpSB.append("0");
                    }
                    tmpSB.append(temp);

                    if (i + 1 < ch.length) {
                        tmpSB.append(" ");
                    }
                }
                update.updateConversationHandler.post(new updateTextThread(text_feedback, tmpSB.toString()));
                tmpSB.delete(0, tmpSB.length());
            }
            break;

            case FORMAT_ASCII:
            default:
                update.updateConversationHandler.post(new updateTextThread(text_feedback, uartInterface.readSB.toString()));
            break;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    /** one integer */
    void sendTelemetry(int number, int value) {
        if (protocol == 0x01) {
            //TODO: write method for sending one integer through TCP
        } else {
            serverUDP.send((byte) number, value);
        }
    }

    /** two shorts */
    void sendTelemetry(int number, short short1, short short2) {
        if (protocol == 0x01) {
            serverTCP.send((byte) number, short1, short2);
        } else {
            serverUDP.send((byte) number, short1, short2);
        }
    }

    /** one float */
    void sendTelemetry(int number, float value) {
        if (protocol == 0x01) {
            serverTCP.send((byte) number, value);
        } else {
            serverUDP.send((byte) number, value);
        }
    }
}
