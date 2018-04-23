package pl.bezzalogowe.PhoneUAV;

import android.os.Build;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Input {
    private static final String TAG = "controller";
    MainActivity main;
    int controllerX2value, controllerY2value;
    boolean numpad[] = {false, false, false, false, false, false, false, false, false, false};

    int servoMin = 500;
    int servoMax = 2500;

    boolean gimbalXchanged, gimbalYchanged;
    int GIMBAL_Y_MIN = 1440;
    int GIMBAL_Y_MAX = 2200;

    int gimbalXpulse = 1500;
    int gimbalYpulse = 1500;
    int gimbalStep = 5;

    public int scaleDown(int value) {
        return value / 20 - 25;
    }

    public void startController(MainActivity argActivity) {
        main = argActivity;
        controllerX2value = 0;
        controllerY2value = 0;

        gimbalXchanged = false;
        gimbalYchanged = false;

        startTurret(main);
    }

    private void toggleRecording() {
        if (Build.VERSION.SDK_INT >= 21) {
            if (!main.camObjectLolipop.isRecording) {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 1;
                            main.handlerCamera.sendMessage(msg);
                        }
                    }.start();

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
                main.sendTelemetry(8, 1);
            } else {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 0;
                            main.handlerCamera.sendMessage(msg);
                        }
                    }.start();

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
                main.sendTelemetry(8, 0);
            }
        }
    }

    public void startTurret(final MainActivity argActivity) {
        ScheduledExecutorService executor;
        executor = Executors.newSingleThreadScheduledExecutor();
        main = argActivity;
        executor.scheduleAtFixedRate(new Runnable() {
            public void run() {
/*
                if (gimbalXchanged && gimbalYchanged) {
                    main.ch340commObject.SetPositions(11, gimbalXpulse, 12, gimbalYpulse, (byte) 100);
                } else {
                    if (gimbalXchanged) {
                        main.ch340commObject.SetPosition(11, gimbalXpulse, (byte) 100);
                    }
                    if (gimbalYchanged) {
                        main.ch340commObject.SetPosition(12, gimbalYpulse, (byte) 100);
                    }
                }
*/
                if (gimbalXchanged || gimbalYchanged) {
                    main.ch340commObject.SetPositions(11, gimbalXpulse, 12, gimbalYpulse, (byte) 100);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, gimbalXpulse + ", " + gimbalYpulse + "(" + (gimbalXchanged ? "X" : "") + (gimbalYchanged ? "Y" : "") + ")"));
                }
            }
        }, 20, 20, TimeUnit.MILLISECONDS);
    }

    private void processAileron(short value) {
    /** roll, x-input: L2, DS4: right knob X */

/*
minimum value is -32768 (-2^15)
maximum value is 32767 (inclusive) (2^15 -1)
*/

        controllerX2value = (int) value;
        main.ail = (int) (-value / 32.768) + 1500;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPosition(main.outputsAileron, main.ail, (byte) 100);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) (main.outputsAileron[0] - 1), (byte) ((main.ail - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsAileron[0], (main.ail - 500) / 2, (byte) 20);
        }

        /* flying wing */
        mixElevons(controllerX2value, controllerY2value);

        main.seekbarAIL.setProgress(scaleDown(main.ail));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "AIL (CH" + main.outputsAileron[0] + "): " + Short.toString(value) + ", " + main.ail + " ms"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Short.toString(value)));
    }

    private void processFlaps(short value) {
        //TODO: adjust scale
        main.flaps = (value + 32768) / 150;
        processFlaperons((short) controllerX2value);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "FLAPS: " + Integer.toString(value, 10) + ", " + main.flaps + " ms"));
    }

    private void processFlaperons(short value) {
        /** roll + flaps, x-input: L2, DS4: right knob X
         * (servos mounted in same direction) */
        int flaperonLeft, flaperonRight;

        controllerX2value = (int) value;
        main.ail = (int) (-controllerX2value / 32.768) + 1500;

        flaperonLeft = (int) ((-value / 32.768) + 1500) - main.flaps;
        flaperonRight = (int) ((value / 32.768) + 1500) - main.flaps;

        if (flaperonLeft > servoMax)
            flaperonLeft = servoMax;
        if (flaperonLeft < servoMin)
            flaperonLeft = servoMin;

        if (flaperonRight > servoMax)
            flaperonRight = servoMax;
        if (flaperonRight < servoMin)
            flaperonRight = servoMin;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositions(main.outputsFlaperonLeft, flaperonLeft, main.outputsFlaperonRight, flaperonRight, (byte) 100);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) (main.outputsFlaperonLeft[0] - 1), (byte) ((flaperonLeft - 500) / 20));
            main.pwmInterface.SetDutyCycle((byte) (main.outputsFlaperonRight[0] - 1), (byte) ((flaperonRight - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsFlaperonLeft[0], (flaperonLeft - 500) / 2, (byte) 20);
            main.sk18commObject.SetPosition((byte) main.outputsFlaperonRight[0], (flaperonRight - 500) / 2, (byte) 20);
        }

        /* flying wing */
        mixElevons(controllerX2value, controllerY2value);

        main.seekbarAIL.setProgress(scaleDown(main.ail));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "FLAP-AIL-L (CH" + main.outputsFlaperonLeft[0] + "): " + flaperonLeft + "\t" + "FLAP-AIL-R (CH" + main.outputsFlaperonRight[0] + "): " + flaperonRight));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Short.toString(value)));
    }

    private void processElevator(short value) {
        /** pitch, x-input: R2, DS4: right knob Y */

        controllerY2value = (int) value;
        main.ele = (int) (-controllerY2value / 32.768 + 1500);

        if (main.autopilot.stabilize_pitch == false) {
            if (main.outputMode == main.USC16) {
                main.ch340commObject.SetPosition(main.outputsElevator, main.ele, (byte) 100);
            } else if (main.outputMode == main.FT311D_PWM) {
                main.pwmInterface.SetDutyCycle((byte) (main.outputsElevator[0] - 1), (byte) ((main.ele - 500) / 20));
                //main.dutyCycleELEV.setText(Integer.toString(main.channel[main.outputsElevator[0]]) + "\n‰");
            } else if (main.outputMode == main.FT311D_UART) {
                main.sk18commObject.SetPosition((byte) main.outputsElevator[0], (main.ele - 500) / 2, (byte) 20);
                //main.dutyCycleRDR.setText(Integer.toString(main.channel[main.outputsElevator[0]]));
            }

            /* flying wing */
            mixElevons(controllerX2value, controllerY2value);

            main.seekbarELEV.setProgress(scaleDown(main.ele));
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ELE (CH" + main.outputsElevator[0] + ") " + Long.toString(value, 10) + ", " + main.ele + " ms"));
            main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Short.toString(value)));
        } else {
            /** When autopilot is enabled don't move control surfaces, set target pitch and send feedback */
            main.autopilot.target_pitch = ((double) controllerY2value) / 1000;
            Log.d("input", "Target pitch: " + main.autopilot.target_pitch);
            main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, main.autopilot.target_pitch + "\u00B0"));
            main.sendTelemetry(13, (float) main.autopilot.target_pitch);
        }
    }

    public void mixElevons(int x, int y) {
        /** mixes aileron and elevator */
        int elevonLeft, elevonRight;

        elevonLeft = (int) ((-x + y) / 32.768) + 1500 - main.elevatorTrim;
        elevonRight = (int) ((x + y) / 32.768) + 1500 - main.elevatorTrim;

        if (elevonLeft > servoMax)
            elevonLeft = servoMax;
        if (elevonLeft < servoMin)
            elevonLeft = servoMin;

        if (elevonRight > servoMax)
            elevonRight = servoMax;
        if (elevonRight < servoMin)
            elevonRight = servoMin;

        main.seekbarELEV_L.setProgress(scaleDown(elevonLeft));
        main.seekbarELEV_R.setProgress(scaleDown(elevonRight));

        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ELEVON-L (CH" + main.outputsElevonLeft[0] + "): " + elevonLeft + "\t" + "ELEVON-R (CH" + main.outputsElevonRight[0] + "): " + elevonRight));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV_L, scaleDown(elevonLeft) + "%"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV_R, scaleDown(elevonRight) + "%"));

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositions(main.outputsElevonLeft, elevonLeft, main.outputsElevonRight, elevonRight, (byte) 100);
        }
        else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsElevonLeft[0] - 1, (byte) ((elevonLeft - 500) / 20));
            main.pwmInterface.SetDutyCycle((byte) main.outputsElevonRight[0] - 1, (byte) ((elevonRight - 500) / 20));
        }
        else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsElevonLeft[0], (elevonLeft - 500) / 2, (byte) 20);
            main.sk18commObject.SetPosition((byte) main.outputsElevonRight[0], (elevonRight - 500) / 2, (byte) 20);
        }
    }

    private void processThrottle(short value) {
        main.thr = ((((value + 32768) / main.throttleDenominator) + main.throttleMin));
        main.seekbarTHROT.setProgress(scaleDown(main.thr));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "THR (CH" + main.outputsThrottle[0] + ") " + Integer.toString(value, 10) + ", " + main.thr + " ms"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleTHROT, Short.toString(value)));
    }

    private void processRudder(short value) {
        /** yaw, x-input: X1 */

        main.rdr = (int) (value / 32.768) + 1500;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPosition(main.outputsRudder, main.rdr, (byte) 100);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) (main.outputsRudder[0] - 1), (byte) ((main.rdr - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsRudder[0], (main.rdr - 500) / 2, (byte) 20);
        }

        main.seekbarRDR.setProgress(scaleDown(main.rdr));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "RDR (CH" + main.outputsRudder[0] + ") " + Integer.toString(value, 10) + ", " + main.rdr + " ms"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Short.toString(value)));
    }

    void processButton(byte data) {
        byte number = (byte) ((data / 2) - 1);
        if (data % 2 == 1) {
            /* button pressed, value = true */
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " pressed"));
            switch (number) {
                case 0:
                    // DS4 "square" - pitch stabilization disabled
                    main.autopilot.stabilize_pitch = false;
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkbox_stabilistation_pitch, false));
                    break;
                case 1:
                    // DS4 "cross" - pitch stabilization enabled
                    main.autopilot.stabilize_pitch = true;
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkbox_stabilistation_pitch, true));
                    break;
                case 2:
                    // DS4 "circle" - roll stabilization enabled
                    main.autopilot.stabilize_roll = true;
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkbox_stabilistation_roll, true));
                    break;
                case 3:
                    // DS4 "triangle" - roll stabilization disabled
                    main.autopilot.stabilize_roll = false;
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkbox_stabilistation_roll, false));
                    break;
                case 4:
                    // DS4 L1 button
                    if (Build.VERSION.SDK_INT <= 20 /*Build.VERSION_CODES.LOLLIPOP*/) {
/* API≤20 */
//FIXME: double check
/*
if (main.camAPIobjectKitkat.isRecording)
{Log.d("camera", "Can't take photo while recording yet!");}
else
{main.camAPIobjectKitkat.captureImage();}
*/
                    } else {/* API>20 */
                        main.camObjectLolipop.captureImage();
                        main.sendTelemetry(4, 1);
                    }
                    break;
                case 5:
                    // DS4 R1 button
                    /* Camera API 1 */
                    //main.camObject.camera1.startPreview();
                    //main.camObject.camera1.stopPreview();
                    break;
                case 6:
                    // not actually DS4 L2 "button"
                    break;
                case 7:
                    // not actually DS4 R2 "button"
                    try {
                        //FIXME "Only the original thread that created a view hierarchy can touch its views"
                        if (main.outputMode == main.USC16) {
                            main.ch340commObject.startPWM(main, main.throttleMin);
                        } else if (main.outputMode == main.FT311D_UART) {
                            main.sk18commObject.startPWM(main, main.throttleMin);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Button 7 error: " + e.toString()));
                    }
                    break;
                case 8:
                    // DS4 Share button
                    toggleRecording();
                    break;
                case 9:
                    // DS4 Options button
                    if (Build.VERSION.SDK_INT >= 21) {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 1;
                                    main.handlerTorch.sendMessage(msg);
                                }
                            }.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(9, 1);
                    break;
                case 10:
                    // DS4 left knob press
                    break;
                case 11:
                    // DS4 right knob press
                    break;
                case 12:
                    // DS4 PlayStation button press
                    break;
                case 13:
                    // DS4 touchpad press
                    break;
                case 14:
                    break;
                default:
            }
        } else {
            /* button released, value = false */
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " released"));
            switch (number) {
                case 4:
                    main.sendTelemetry(4, 0);
                    break;
                case 5:
                    break;
                case 6:
                    // not actually DS4 L2 "button"
                    break;
                case 7:
                    // not actually DS4 R2 "button"
                    try {
                        if (main.outputMode == main.USC16) {
                            main.ch340commObject.stopPWM(main);
                        } else if (main.outputMode == main.FT311D_UART) {
                            main.sk18commObject.stopPWM(main);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Button 7 error: " + e.toString()));
                    }
                    break;
                case 8:
                    // DS4 Share button
                    toggleRecording();
                    break;
                case 9:
                    // DS4 Options button
                    if (Build.VERSION.SDK_INT >= 21) {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 0;
                                    main.handlerTorch.sendMessage(msg);
                                }
                            }.start();

                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(9, 0);
                    break;
                case 10:
                    break;
                case 11:
                    break;
                case 12:
                    break;
                case 13:
                    break;
                case 14:
                    break;
                default:
            }
        }
    }

    void processStick(byte data[]) {
        short value;
        value = (short) ((data[1] & 0xFF) + ((data[2] & 0xFF) << 8));
        //value = (short) (data[1] + data[2] * 256);
        switch (data[0]) {
            case 40:
                processRudder(value);
                break;
            case 41:
                // x-input: Y1 (throttle, knob)
                break;
            case 42:
                //processAileron(value);
                processFlaperons(value);
                break;
            case 43:
                // x-input: right knob X, DS4: L2
                processFlaps(value);
                break;
            case 44:
                // x-input: right knob Y, DS4: R2
                // recalculates full analog stick value range to throttleMin - throttleMax range
                processThrottle(value);
                break;
            case 45:
                processElevator(value);
                break;
            case 46:
                // x-input: D-Pad X
                int rdr = (int) calculateValue(value);
                main.seekbarRDR.setProgress(scaleDown(rdr));
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "X1 (CH" + main.outputsRudder[0] + ") [" + data[1] + ", " + data[2] + "] " + Integer.toString(value, 10)));
                break;
            case 47:
                // x-input: D-Pad Y
                if (value == 0) {
                    // do nothing
                } else if (value > 0) {
                    main.elevatorTrim += 10;
                    main.sendTelemetry(14, main.elevatorTrim);
                    mixElevons(controllerX2value, controllerY2value);

                } else if (value < 0) {
                    main.elevatorTrim -= 10;
                    main.sendTelemetry(14, main.elevatorTrim);
                    mixElevons(controllerX2value, controllerY2value);
                }
                break;
            case 48:
                break;
            case 49:
                // DS4: touchpad X
                // numpad 4 and 6
                if (value > 0) {
                    if (gimbalXpulse >= 500 + gimbalStep) {
                        gimbalXpulse -= gimbalStep;
                        gimbalXchanged = true;
                    }
                } else if (value < 0) {
                    if (gimbalXpulse <= 2500 - gimbalStep) {
                        gimbalXpulse += gimbalStep;
                        gimbalXchanged = true;
                    }
                } else /* value == 0 */ {
                    gimbalXchanged = false;
                }
                break;
            case 50:
                // DS4: touchpad Y
                // numpad 8 and 2
                if (value < 0) {
                    if (gimbalYpulse >= GIMBAL_Y_MIN + gimbalStep) {
                        gimbalYpulse -= gimbalStep;
                        gimbalYchanged = true;
                    }
                } else if (value > 0) {
                    if (gimbalYpulse <= GIMBAL_Y_MAX - gimbalStep) {
                        gimbalYpulse += gimbalStep;
                        gimbalYchanged = true;
                    }
                } else /* value == 0 */ {
                    gimbalYchanged = false;
                }
                break;
            default:
                break;
        }
    }

    void process(byte data[]) {
        // one byte received
        if ((data[0] <= 0x20) && (data[0] >= 1)) {
            /* decimal less or equal 32 - joystick button */
            main.inputObject.processButton(data[0]);
            Log.d("button", "process button: " + data[0]);
        } else if (data[0] == 0x23) {
            /* decimal 35 - Reset */
            System.out.println(String.format("%05X", data[0] & 0x0FFFFF) + " Reset");
            try {
                main.resetFT311();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Reset FT311D error: " + e);
            }
        }

        // more than one byte received
        if (data[0] == 0x21) {
            /* decimal 33 - SetPeriod */
            System.out.println(String.format("%05X", data[0] & 0x0FFFFF) + ", " + data[1] + " SetPeriod");
            main.period = (int) (data[1] + 128);
            try {
                main.pwmInterface.SetPeriod(main.period);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error: " + e);
            }
            main.savePeriodPreference();
        } else if (0x28 <= data[0] && data[0] <= 0x32) {
            /* 40 to 50 - SetDutyCycle */
            /* joystick axis */
            main.inputObject.processStick(data);
            System.out.println(data[0] + ", " + data[1] + ", " + data[2] + " SetDutyCycle");
        } else if (data[0] == 0x33 /*51*/) {
            byte[] ipAddr = new byte[]{data[1], data[2], data[3], data[4]};
            System.out.println("Remote address: " + (int) data[1] + " ," + (int) data[2] + " ," + (int) data[3] + " ," + (int) data[4]);
        }
    }

    public double calculateValue(short value) {
        return (value / 65.6) + 500.75;
    }
}
