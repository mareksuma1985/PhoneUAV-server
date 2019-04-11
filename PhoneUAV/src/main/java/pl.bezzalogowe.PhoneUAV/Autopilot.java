package pl.bezzalogowe.PhoneUAV;

import android.location.LocationManager;
import com.stormbots.MiniPID;

/** https://github.com/tekdemo/MiniPID-Java */

public class Autopilot {
    public MiniPID pid;
    MainActivity main;
    boolean stabilize_roll = false;
    boolean stabilize_pitch = false;
    boolean auto_yaw = false;
    int period = 100;
    double target_roll = 0;
    double target_pitch = 0;
    double target_altitude;
    boolean fakeAltitude = false;
    Thread altitudeHoldThread;
    double proportional = 0;
    double integral = 0;
    double derivative = 0;

    public void startAutopilot(MainActivity argActivity) {
        main = argActivity;
        stabilize_pitch = true;
        stabilize_roll = true;
        altitudeHoldThread = new Thread(new AutopilotThread());
        altitudeHoldThread.start();
    }

    public void stopAutopilot(MainActivity argActivity) {
        main = argActivity;
        stabilize_pitch = false;
        stabilize_roll = false;
        try {
            altitudeHoldThread.interrupt();
            altitudeHoldThread = null;
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot stopped"));
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot NOT stopped"));
        }
    }

    public void startFollowingWaypoints(MainActivity argActivity) {
        main = argActivity;
        if (main.locObject.waypointNext != null) {
            main.autopilot.auto_yaw = true;
        } else {
            main.locObject.waypointNext = new android.location.Location(LocationManager.GPS_PROVIDER);
            int size = main.locObject.nextWaypoint();
            if (size == 0) {
                main.locObject.waypointNext = null;
                /*
                main.locObject.waypointNext.setLatitude(0);
                main.locObject.waypointNext.setLongitude(0);
                main.locObject.waypointNext.setAltitude(300);
                */
            }
        }
    }

    public void stopFollowingWaypoints(MainActivity argActivity) {
        main = argActivity;
        main.autopilot.auto_yaw = false;
    }

    class AutopilotThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {

            /** sets target altitude to the barometric altitude at the moment of enabling autopilot */
            main.autopilot.target_altitude = main.pressureObject.altitudeBarometric;

            pid = new MiniPID(proportional, integral, derivative);
            /** Now loop forever, waiting to receive packets and printing them. */
            while (altitudeHoldThread != null) {

                target_pitch = pid.getOutput((double) main.pressureObject.altitudeBarometric, target_altitude);

                short aileronrvalue = 0;
                short elevatorvalue = 0;
                short ruddervalue = 0;

                /** PID target pitch hold */
                if (stabilize_pitch && main.inputObject.controllerY2value == 0) {
                    String pString = String.format("%.2f", proportional);
                    String iString = String.format("%.2f", integral);
                    String dString = String.format("%.2f", derivative);
                    String outputString = String.format("%.2f", target_pitch);

                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "P= " + pString + "\tI= " + iString + "\tD= " + dString + "\ttarget_pitch= " + outputString + "\u00B0"));

                    /** P-- elevator adjustment */
                    elevatorvalue = (short) ((target_pitch - main.gravityObject.angle_pitch) * 364);
                    /* target > actual => elevatorvalue > 0
                     * target < actual => elevatorvalue < 0 */

                    if (elevatorvalue > 32767) {
                        elevatorvalue = 32767;
                    }
                    if (elevatorvalue < -32767) {
                        elevatorvalue = -32767;
                    }

                    if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsElevator, -elevatorvalue, period, main.ch340commObject.servoElevatorMin, main.ch340commObject.servoElevatorMax);
                    }

                    main.ele = (int) (-elevatorvalue / 32.767 + 1500);

                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarELEV, main.inputObject.scaleDown(main.ele)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Integer.toString(main.inputObject.scaleDown(main.ele)) + "%"));
                }

                /** P-- roll adjustment */
                if (stabilize_roll && main.inputObject.controllerX2value == 0) {
                    // -32760, 32760
                    double angle_roll_clipped;
                    if (main.gravityObject.angle_roll > 90) {
                        angle_roll_clipped = 90;
                    } else if (main.gravityObject.angle_roll < -90) {
                        angle_roll_clipped = -90;
                    } else {
                        angle_roll_clipped = main.gravityObject.angle_roll;
                    }
                    
                    aileronrvalue = (short) (angle_roll_clipped * 364);
                    main.ail = (int) ((-aileronrvalue / 32.767) + 1500);

                    if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsAileron, aileronrvalue, period, 500, 2500);

                        //TODO flaps
                        main.ch340commObject.SetPosition(main.outputsFlaperonLeft, main.ail, period, 500, 2500);
                        main.ch340commObject.SetPosition(main.outputsFlaperonRight, -main.ail + 3000, period, 500, 2500);
                    }

                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarAIL, main.inputObject.scaleDown(main.ail)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Integer.toString(main.inputObject.scaleDown(main.ail)) + "%"));
                }

                /** P-- yaw (rudder) adjustment */
                if (auto_yaw && main.inputObject.controllerX1value == 0 && main.locObject.waypointNext != null) {
                    double difference = (((360 + (main.magObject.azimuth - main.magObject.heading)) % 360) - 180);

                    main.update.updateConversationHandler.post(new updateTextThread(main.angle_text_heading, (int) main.magObject.heading + "\u00B0\n" + (int) main.magObject.azimuth + "\u00B0\n" + (int) difference + "\u00B0"));

                    ruddervalue = (short) (difference * 182);

                    if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsRudder, ruddervalue, 200 /*headingPeriod*/, main.ch340commObject.servoRudderMin, main.ch340commObject.servoRudderMax);
                    }

                    main.rdr = (int) ((-ruddervalue / 32.767) + 1500);
                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarRDR, main.inputObject.scaleDown(main.rdr)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Integer.toString(main.inputObject.scaleDown(main.rdr)) + "%"));
                }

                /** flying wing */
                if (main.outputMode == main.USC16) {
                    main.main.inputObject.mixElevons(aileronrvalue, elevatorvalue);
                }

                try {
                    Thread.sleep(period);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
