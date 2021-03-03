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
    short controllerX1value, controllerX2value, controllerY2value;

    int GIMBAL_Y_MIN = 1440;
    int GIMBAL_Y_MAX = 2200;

    int controllerGimbalXvalue, controllerGimbalYvalue;
    int gimbalXpulse, gimbalYpulse, gimbalStep;

    public int scaleDown(int value) {
        return value / 20 - 25;
    }

    public void startController(MainActivity argActivity) {
        main = argActivity;
        /** rudder */
        controllerX1value = 0;
        /** aileron: -32767 (max left) through 32767 (max right) */
        controllerX2value = 0;
        /** elevator: -32767 (max down) through 32767 (max up) */
        controllerY2value = 0;

        controllerGimbalXvalue = 0;
        controllerGimbalYvalue = 0;

        gimbalXpulse = 1500;
        gimbalYpulse = 1500;
        gimbalStep = 5;

        startGimbal(main);
    }

    private void toggleRecording() {
        if (Build.VERSION.SDK_INT <= 20) {
            if (!main.camObjectKitkat.isRecording) {
                main.camObjectKitkat.captureVideoStart();
            } else {
                main.camObjectKitkat.captureVideoStop();
            }
        } else {
            if (!main.camObjectLolipop.isRecording) {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 1;
                            main.camObjectLolipop.handlerRecord.sendMessage(msg);
                        }
                    }.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
            } else {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 0;
                            main.camObjectLolipop.handlerRecord.sendMessage(msg);
                        }
                    }.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
            }
        }
    }

    private void setProportional(int value) {
        main.autopilot.proportional = main.autopilot.proportional + ((double) value) / 100;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setP(main.autopilot.proportional);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "P: " + main.autopilot.proportional);
        double pArray[] = {main.autopilot.proportional};
        main.sendTelemetry(13, pArray);
    }

    private void setIntegral(int value) {
        main.autopilot.integral = main.autopilot.integral + ((double) value) / 100;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setI(main.autopilot.integral);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "I: " + main.autopilot.integral);
        double iArray[] = {main.autopilot.proportional};
        main.sendTelemetry(14, iArray);
    }

    private void setDerivative(int value) {
        main.autopilot.derivative = main.autopilot.derivative + ((double) value) / 100;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setD(main.autopilot.derivative);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "D: " + main.autopilot.derivative);
        double dArray[] = {main.autopilot.proportional};
        main.sendTelemetry(15, dArray);
    }

    public void startGimbal(final MainActivity argActivity) {
        ScheduledExecutorService executor;
        executor = Executors.newSingleThreadScheduledExecutor();
        main = argActivity;
        final int gimbalChannels[] = {11, 12};
        executor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                boolean gimbalXchanged = false;
                boolean gimbalYchanged = false;

                // DS4: touchpad X
                // numpad 4 and 6
                if (controllerGimbalXvalue > 0) {
                    if (gimbalXpulse >= 500 + gimbalStep) {
                        gimbalXpulse -= gimbalStep;
                        gimbalXchanged = true;
                    }
                } else if (controllerGimbalXvalue < 0) {
                    if (gimbalXpulse <= 2500 - gimbalStep) {
                        gimbalXpulse += gimbalStep;
                        gimbalXchanged = true;
                    }
                }

                /* value == 0 */
                    /*
                    else
                    {gimbalXchanged = false;}
                    */

                // DS4: touchpad Y
                // numpad 8 and 2
                if (controllerGimbalYvalue < 0) {
                    if (gimbalYpulse >= GIMBAL_Y_MIN + gimbalStep) {
                        gimbalYpulse -= gimbalStep;
                        gimbalYchanged = true;
                    }
                } else if (controllerGimbalYvalue > 0) {
                    if (gimbalYpulse <= GIMBAL_Y_MAX - gimbalStep) {
                        gimbalYpulse += gimbalStep;
                        gimbalYchanged = true;
                    }
                }

                /* value == 0 */
                    /*
                    else
                    {gimbalYchanged = false;}
                    */

                if (gimbalXchanged || gimbalYchanged) {
                    main.ch340commObject.SetPositions(gimbalChannels[0], gimbalXpulse, gimbalChannels[1], gimbalYpulse, 100);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback,
                            "values: " + gimbalXpulse + ", " + gimbalYpulse + " changed: " + Boolean.valueOf(gimbalXchanged) + ", " + Boolean.valueOf(gimbalYchanged)));
                }
            }
        }, 20, 20, TimeUnit.MILLISECONDS);
    }

    private void processAileron(short value) {
        /** roll, x-input: L2, DS4: right knob X */

/*
minimum value is -32767 (-2^15 +1)
maximum value is 32767 (2^15 -1)
*/

        controllerX2value = value;
        main.ail = (int) (-value / 32.767) + 1500;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositionPrecisely(main.outputsAileron, controllerX2value, 100, 500, 2500);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsAileron[0], (byte) ((main.ail - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsAileron[0], (main.ail - 500) / 2, (byte) 20);
        }

        /* flying wing */
        mixElevons(value, (short) controllerY2value);

        main.seekbarAIL.setProgress(scaleDown(main.ail));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "AIL (CH" + main.outputsAileron[0] + "): " + Short.toString(value) + ", " + main.ail + " μs"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Short.toString(value)));
    }

    private void processFlaps(short value) {
        //TODO: adjust scale
        main.flaps = (value + 32767) / 150;
        processFlaperons((short) controllerX2value);
        mixElevons(controllerX2value, controllerY2value);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "FLAPS: " + Integer.toString(value, 10) + ", " + main.flaps + " μs"));
    }

    private void processFlaperons(short value) {
        /** roll + flaps, x-input: L2, DS4: right knob X
         * (servos mounted in same direction) */
        int flaperonLeft, flaperonRight;

        controllerX2value = value;
        main.ail = (int) ((-controllerX2value / 32.767) + 1500);

        flaperonLeft = (int) ((-value / 32.767) + 1500) - main.flaps;
        flaperonRight = (int) ((value / 32.767) + 1500) - main.flaps;

        if (flaperonLeft > main.ch340commObject.servoElevatorMax)
            flaperonLeft = main.ch340commObject.servoElevatorMax;
        if (flaperonLeft < main.ch340commObject.servoElevatorMin)
            flaperonLeft = main.ch340commObject.servoElevatorMin;

        if (flaperonRight > main.ch340commObject.servoElevatorMax)
            flaperonRight = main.ch340commObject.servoElevatorMax;
        if (flaperonRight < main.ch340commObject.servoElevatorMin)
            flaperonRight = main.ch340commObject.servoElevatorMin;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositions(main.outputsFlaperonLeft, flaperonLeft, main.outputsFlaperonRight, flaperonRight, 100, 500, 2500);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsFlaperonLeft[0], (byte) ((flaperonLeft - 500) / 20));
            main.pwmInterface.SetDutyCycle((byte) main.outputsFlaperonRight[0], (byte) ((flaperonRight - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsFlaperonLeft[0], (flaperonLeft - 500) / 2, (byte) 20);
            main.sk18commObject.SetPosition((byte) main.outputsFlaperonRight[0], (flaperonRight - 500) / 2, (byte) 20);
        }

        /* flying wing */
        mixElevons(value, (short) controllerY2value);

        main.seekbarAIL.setProgress(scaleDown(main.ail));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "FLAP-AIL-L (CH" + main.outputsFlaperonLeft[0] + "): " + flaperonLeft + "\t" + "FLAP-AIL-R (CH" + main.outputsFlaperonRight[0] + "): " + flaperonRight));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Short.toString(value)));
    }

    private void processElevator(short value) {
        /** pitch, x-input: R2, DS4: right knob Y */

        controllerY2value = value;
        main.ele = (int) (-controllerY2value / 32.767 + 1500);

        if (main.autopilot.hold_pitch == false) {
            if (main.outputMode == main.USC16) {
                main.ch340commObject.SetPositionPrecisely(main.outputsElevator, -controllerY2value, 100, main.ch340commObject.servoElevatorMin, main.ch340commObject.servoElevatorMax);
            } else if (main.outputMode == main.FT311D_PWM) {
                main.pwmInterface.SetDutyCycle((byte) main.outputsElevator[0], (byte) ((main.ele - 500) / 20));
                //main.dutyCycleELEV.setText(Integer.toString(main.channel[main.outputsElevator[0]]) + "\n‰");
            } else if (main.outputMode == main.FT311D_UART) {
                main.sk18commObject.SetPosition((byte) main.outputsElevator[0], (main.ele - 500) / 2, (byte) 20);
                //main.dutyCycleRDR.setText(Integer.toString(main.channel[main.outputsElevator[0]]));
            }

            /* flying wing */
            mixElevons((short) controllerX2value, value);

            main.seekbarELEV.setProgress(scaleDown(main.ele));
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ELE (CH" + main.outputsElevator[0] + ") " + Long.toString(value, 10)));
            main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Short.toString(value)));
        } else {
            /** When autopilot is enabled don't move control surfaces, set target pitch and send feedback */
            main.autopilot.target_pitch = ((double) controllerY2value) / 1000;
            Log.d("input", "Target pitch: " + main.autopilot.target_pitch);
            main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, main.autopilot.target_pitch + "\u00B0"));
            main.sendTelemetry(13, (float) main.autopilot.target_pitch);
        }
    }

    private void processRudder(short value) {
        /** yaw, x-input: X1 */

        controllerX1value = value;
        main.rdr = (int) ((value / 32.767) + 1500);

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositionPrecisely(main.outputsRudder, (int) (value - (main.rudderTrim * 32.767)), 100, main.ch340commObject.servoRudderMin, main.ch340commObject.servoRudderMax);
            //main.ch340commObject.SetPosition(main.outputsRudder, main.rdr - main.rudderTrim, 100, true);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsRudder[0], (byte) ((main.rdr - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsRudder[0], (main.rdr - 500 - main.rudderTrim) / 2, (byte) 20);
        }

        main.seekbarRDR.setProgress(scaleDown(main.rdr));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "RDR (CH" + main.outputsRudder[0] + ") " + Integer.toString(value, 10)));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Short.toString(value)));
    }

    public void mixElevons(short x, short y) {
        int elevonLeft, elevonRight;
        elevonLeft = -x + y;
        elevonRight = x + y;

        if (elevonLeft > 32767)
        {elevonLeft = 32767;}
        if (elevonLeft < -32767)
        {elevonLeft = -32767;}

        if (elevonRight > 32767)
        {elevonRight = 32767;}
        if (elevonRight < -32767)
        {elevonRight = -32767;}

        if (main.outputMode == main.USC16) {
/** one servo at a time */
        /*
            main.ch340commObject.SetPositionPrecisely(main.outputsElevonLeft, elevonLeft, 100, main.ch340commObject.servoElevonMin, main.ch340commObject.servoElevonMax);
            main.ch340commObject.SetPositionPrecisely(main.outputsElevonRight, elevonRight, 100, main.ch340commObject.servoElevonMin, main.ch340commObject.servoElevonMax);
        */
/** both servos at the same time */
            main.ch340commObject.SetPositionsPrecisely(main.outputsElevonLeft, elevonLeft, main.outputsElevonRight, elevonRight, 100, main.ch340commObject.servoElevonMin, main.ch340commObject.servoElevonMax);
        }
           else if (main.outputMode == main.FT311D_UART) {
            //TODO: test
            main.sk18commObject.SetPosition((byte) main.outputsElevonLeft[0], (int) ((elevonLeft / 32.767) + 1500), (byte) 20);
            main.sk18commObject.SetPosition((byte) main.outputsElevonRight[0], (int) ((elevonRight / 32.767) + 1500), (byte) 20);
        }
    }

    /**
     * compensates for USC-16 inaccuracy
     */
    private int compensate(int in) {
        int out = (int) 0.98 * in - 7;
        return out;
    }

    private void mixElevonsOld(int x, int y) {
        /** mixes aileron and elevator */
        int elevonLeft, elevonRight;

        elevonLeft = (int) (((-x + y) / 32.767) + 1500 - main.elevatorTrim);
        elevonRight = (int) (((x + y) / 32.767) + 1500 - main.elevatorTrim);

        if (elevonLeft > main.ch340commObject.servoElevatorMax)
            elevonLeft = main.ch340commObject.servoElevatorMax;
        if (elevonLeft < main.ch340commObject.servoElevatorMin)
            elevonLeft = main.ch340commObject.servoElevatorMin;

        if (elevonRight > main.ch340commObject.servoElevatorMax)
            elevonRight = main.ch340commObject.servoElevatorMax;
        if (elevonRight < main.ch340commObject.servoElevatorMin)
            elevonRight = main.ch340commObject.servoElevatorMin;

        main.seekbarELEV_L.setProgress(scaleDown(elevonLeft));
        main.seekbarELEV_R.setProgress(scaleDown(elevonRight));

        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ELEVON-L (CH" + main.outputsElevonLeft[0] + "): " + elevonLeft + "\t" + "ELEVON-R (CH" + main.outputsElevonRight[0] + "): " + elevonRight));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV_L, scaleDown(elevonLeft) + "%"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV_R, scaleDown(elevonRight) + "%"));

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositions(main.outputsElevonLeft, compensate(elevonLeft), main.outputsElevonRight, compensate(elevonRight), 100, 500, 2500);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsElevonLeft[0], (byte) ((elevonLeft - 500) / 20));
            main.pwmInterface.SetDutyCycle((byte) main.outputsElevonRight[0], (byte) ((elevonRight - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsElevonLeft[0], (elevonLeft - 500) / 2, (byte) 20);
            main.sk18commObject.SetPosition((byte) main.outputsElevonRight[0], (elevonRight - 500) / 2, (byte) 20);
        }
    }

    private void processThrottle(short value) {
        //FIXME: range
        main.thr = (int) (((value + 32767) / main.ch340commObject.throttleDenominator) + main.ch340commObject.throttleMin);

        if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsThrottle[0], (byte) ((main.thr - 500) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPosition((byte) main.outputsThrottle[0], (main.thr - 500) / 2, (byte) 20);
        } else if (main.outputMode == main.USC16) {
            main.ch340commObject.SetThrottle(main.outputsThrottle, 100);
        }

        main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarTHROT, main.inputObject.scaleDown(main.thr)));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleTHROT, Integer.toString(main.thr)));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "THR (CH" + main.outputsThrottle[0] + ") " + Integer.toString(value, 10) + ", " + main.thr + " μs"));
    }

    void processButton(byte data) {
        byte number = (byte) (data / 2);
        if (data % 2 == 1) {
            /* button pressed, value = true */
            switch (number) {
                case 0: {
                    // DS4 "cross" - autopilot feature 1 disabled
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature1, false));
                    main.autopilot.stopAutopilot(main);
                }
                break;
                case 1: {
                    // DS4 "circle" - autopilot feature 2 disabled
                    main.autopilot.stopFollowingWaypoints(main);
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature2, false));
                }
                break;
                case 2: {
                    // DS4 "triangle" - autopilot feature 2 enabled
                    main.autopilot.startFollowingWaypoints(main);
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature2, true));
                }
                break;
                case 3: {
                    // DS4 "square" - autopilot feature 1  enabled
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature1, true));
                    main.autopilot.startAutopilot(main);
                }
                break;
                case 4:
                    // DS4 L1 button
                    toggleRecording();
                    break;
                case 5:
                    // DS4 R1 button
                    /* Camera API 1 */
                    if (Build.VERSION.SDK_INT <= 20 /*Build.VERSION_CODES.LOLLIPOP*/) {
                        /* API≤20 */
//FIXME: double check
/*
if (!main.camAPIobjectKitkat.isRecording)
{main.camAPIobjectKitkat.captureImage();}
*/
                    } else {/* API>20 */
                        //main.camObjectLolipop.captureImage();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Shutter pressed"));
                    }
                    main.sendTelemetry(5, true);
                    break;
                case 6:
                    // not actually DS4 L2 "button"
                    break;
                case 7:
                    // not actually DS4 R2 "button"
                    try {
/*
                        if (main.outputMode == main.USC16) {
                            main.ch340commObject.startPWM(main, main.ch340commObject.throttleMin);
                        }
*/
                        if (main.outputMode == main.FT311D_UART) {
                            main.sk18commObject.startPWM(main, main.ch340commObject.throttleMin);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Button 7 error: " + e.toString()));
                    }
                    break;
                case 8:
                    // DS4 Share button
                    if (Build.VERSION.SDK_INT <= 20 /* Build.VERSION_CODES.KITKAT_WATCH */) {
                        main.camObjectKitkat.turnOnTorch();
                    } else {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 1;
                                    main.camObjectLolipop.handlerTorch.sendMessage(msg);
                                }
                            }.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(8, true);
                    break;
                case 9:
                    // DS4 Options button
                    main.sendTelemetry(9, true);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Arrestor hook retracted"));
                    break;
                case 10:
                    // DS4 Play Station button press
                    break;
                case 11:
                    // DS4 left knob press
                    break;
                case 12:
                    // DS4 right knob press
                    //TODO:
                    //main.sendTelemetry((byte) 12, true);
                    //main.ch340commObject.open();
                    break;
                /** DualShock 4 has 13 buttons (0 through 12) */
                case 13:
                    setProportional(1);
                    break;
                case 14:
                    setIntegral(1);
                    break;
                case 15:
                    setDerivative(1);
                    break;
                default:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " pressed"));
                    break;
            }
        } else {
            /* button released, value = false */
            switch (number) {
                case 4:
                    toggleRecording();
                    break;
                case 5:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Shutter released"));
                    main.sendTelemetry(5, false);
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
                    if (Build.VERSION.SDK_INT <= 20 /* Build.VERSION_CODES.KITKAT_WATCH */) {
                        main.camObjectKitkat.turnOffTorch();
                    } else {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 0;
                                    main.camObjectLolipop.handlerTorch.sendMessage(msg);
                                }
                            }.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(8, false);
                    break;
                case 9:
                    // DS4 Options button
                    main.sendTelemetry(9, false);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Arrestor hook extended"));
                    break;
                case 10:
                    break;
                case 11:
                    break;
                case 12:
                    //TODO:
                    //main.sendTelemetry((byte) 12, false);
                    //main.ch340commObject.close();
                    break;
                /** DualShock 4 has 13 buttons (0 through 12) */
                case 13:
                    setProportional(-1);
                    break;
                case 14:
                    setIntegral(-1);
                    break;
                case 15:
                    setDerivative(-1);
                    break;
                default:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " released"));
                    break;
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
                processElevator(value);
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
                // does nothing
                break;
            case 46:
                // x-input: D-Pad X
                if (value == 0) {
                    // do nothing
                } else {
                    /** set increase or decrease trim */
                    if (value < 0 && main.rudderTrim >= -250) {
                        main.rudderTrim -= 5;
                        main.sendTelemetry(16, (short) main.rudderTrim);
                    }
                    if (value > 0 && main.rudderTrim <= 250) {
                        main.rudderTrim += 5;
                        main.sendTelemetry(16, (short) main.rudderTrim);
                    }

                    /** move rudder after change of trim */
                    if (main.outputMode == main.FT311D_UART) {
                        main.sk18commObject.SetPosition((byte) main.outputsRudder[0], (main.rdr - 500 - main.rudderTrim) / 2, (byte) 20);
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim / 2 + " μs"));
                    } else if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsRudder, (int) (controllerX1value - main.rudderTrim * 32.767), 100, 500, 2500);
                        //main.ch340commObject.SetPosition(main.outputsRudder, main.rdr - main.rudderTrim, 100, 500, 2500);
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim + " μs"));
                    } else {
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim + " μs"));
                    }

                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Short.toString(value)));
                    main.seekbarRDR.setProgress(scaleDown(main.rdr));
                }
                break;
            case 47:
                // x-input: D-Pad Y
                if (value == 0) {
                    // do nothing
                } else {
                    if (value > 0 && main.elevatorTrim <= 245) {
                        main.elevatorTrim += 100;
                        main.sendTelemetry(17, (short) main.elevatorTrim);
                    }
                    if (value < 0 && main.elevatorTrim >= -245) {
                        main.elevatorTrim -= 100;
                        main.sendTelemetry(17, (short) main.elevatorTrim);
                    }
                }
                break;
            /** DualShock 4 has 8 axes (0 through 7) */
            case 48:
                // numpad 4 and 6
                controllerGimbalXvalue = value;
                break;
            case 49:
                // numpad 8 and 2
                controllerGimbalYvalue = value;
                break;
            default:
                break;
        }
    }

    void process(byte data[]) {
        if (data.length == 3 && 0x28 <= data[0] && data[0] <= 0x32) {
            /* three bytes received */
            /* joystick axis */
            main.inputObject.processStick(data);
        } else if (data.length == 1 && data[0] <= 0x1f) {
            /* one byte received */
            /* decimal less or equal 31 - joystick button */
            main.inputObject.processButton(data[0]);
            Log.d("button", "process button: " + data[0]);
        } else if (data[0] == 0x21) {
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
        } else if (data[0] == 0x23) {
            /* decimal 35 - Reset */
            System.out.println(String.format("%05X", data[0] & 0x0FFFFF) + " Reset");
            try {
                main.resetFT311();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Reset FT311D error: " + e);
            }
        } else if (data[0] == 0x32) {
            /* decimal 50 */
            //TODO stop heartheat?
            System.out.println("Pinging remote address stopped");
        }
        else if (data[0] == 0x33) {
            /* decimal 51 */
            //byte[] ipAddr = new byte[]{data[1], data[2], data[3], data[4]};
            //TODO start heartbeat ipAddr?
            System.out.println("Pinging remote machine started, video device: " + data[5]);
        }
        /*
        // decimal 52 - add waypoint
        else if (data[0] == 0x34) {
            byte[] header = new byte[]{data[0], data[1], data[2], data[3]};
            byte[] lat_array = new byte[]{data[4], data[5], data[6], data[7], data[8], data[9], data[10], data[11]};
            byte[] lon_array = new byte[]{data[12], data[13], data[14], data[15], data[16], data[17], data[18], data[19]};
            byte[] ele_array = new byte[]{data[20], data[21], data[22], data[23], data[24], data[25], data[26], data[27]};
            Log.d(TAG, "Waypoint " +
                    Arrays.toString(lat_array) + "\n" +
                    Arrays.toString(lon_array) + "\n" +
                    Arrays.toString(ele_array) + " received");

            double lat = main.locObject.bytesArray2Double(lat_array);
            double lon = main.locObject.bytesArray2Double(lon_array);
            double ele = main.locObject.bytesArray2Double(ele_array);

            main.locObject.addWaypoint(lat, lon, ele);
        }
        */
        /*
        // decimal 53 - skip waypoint
        else if (data[0] == 0x35) {
            byte[] header = new byte[]{data[0], data[1], data[2], data[3]};
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, Arrays.toString(header)));
            main.locObject.nextWaypoint();
        }
        */
        /** add more types of datagrams here */
    }
}
