package pl.bezzalogowe.PhoneUAV;

import android.location.LocationManager;
import com.stormbots.MiniPID;

/** https://github.com/tekdemo/MiniPID-Java */

public class Autopilot {
    public MiniPID pid;
    MainActivity main;
    boolean hold_roll = false;
    boolean hold_pitch = false;
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
        hold_pitch = true;
        hold_roll = true;
        altitudeHoldThread = new Thread(new AutopilotThread());
        altitudeHoldThread.start();
    }

    public void stopAutopilot(MainActivity argActivity) {
        main = argActivity;
        hold_pitch = false;
        hold_roll = false;
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

            while (altitudeHoldThread != null) {

                short aileronrvalue = 0;
                short elevatorvalue = 0;
                short ruddervalue = 0;

                /** PID target pitch hold */
                if (hold_pitch && main.inputObject.controllerY2value == 0) {

if (main.pressureObject.altitudeBarometricRecent < target_altitude - 0.2)
{
    /* aircraft too low */
    if (main.pressureObject.altitudeBarometricRecent < target_altitude - 50)
    {target_pitch = 30;
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, ">50 m too low"));
    }
    else
    {target_pitch = (target_altitude - (double) main.pressureObject.altitudeBarometricRecent) *3/5;
    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, String.format("%.2f", target_altitude - main.pressureObject.altitudeBarometricRecent) + " m too low"));
    }

}
else if (main.pressureObject.altitudeBarometricRecent > target_altitude + 0.2)
{
    /* aircraft too high */
    if (main.pressureObject.altitudeBarometricRecent > target_altitude + 50)
    {target_pitch = -30;
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, ">50 m too high"));
    }
    else
    {target_pitch = (target_altitude - (double) main.pressureObject.altitudeBarometricRecent) *3/5;
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, String.format("%.2f", main.pressureObject.altitudeBarometricRecent - target_altitude) + " m too high"));
    }
}
else
{
    /* aircraft inside 0.4 meter altitude range */
    target_pitch = 0;
    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "inside 0,4 m range"));
}

                    String pString = String.format("%.2f", proportional);
                    String iString = String.format("%.2f", integral);
                    String dString = String.format("%.2f", derivative);

                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "P= " + pString + "\tI= " + iString + "\tD= " + dString));

                    /** P-- elevator adjustment */
                    //elevatorvalue = (short) ((target_pitch - main.gravityObject.angle_pitch) * 364);
                    elevatorvalue = (short) pid.getOutput((double) main.gravityObject.angle_pitch, target_pitch);

                    if (elevatorvalue > 32767) {
                        elevatorvalue = 32767;
                    }
                    if (elevatorvalue < -32767) {
                        elevatorvalue = -32767;
                    }

                    main.ele = (int) (-elevatorvalue / 32.767 + 1500);

                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarELEV, main.inputObject.scaleDown(main.ele)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Integer.toString(main.inputObject.scaleDown(main.ele)) + "%"));
                    /** prints -32767 to 32767 value*/
                    //main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Integer.toString(elevatorvalue)));

                    if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsElevator, -elevatorvalue, period, main.ch340commObject.servoElevatorMin, main.ch340commObject.servoElevatorMax);
                    }
                }

                /** P-- roll adjustment */
                if (hold_roll && main.inputObject.controllerX2value == 0) {

                    /** turning with ailerons/elevons */
                    if (auto_yaw && main.locObject.waypointNext != null)
                    {
                        double difference = (((360 + (main.magObject.azimuth - main.magObject.heading)) % 360) - 180);
                        main.update.updateConversationHandler.post(new updateTextThread(main.angle_text_heading, (int) main.magObject.heading + "\u00B0\n" + (int) main.magObject.azimuth + "\u00B0\n(" + (int) difference + "\u00B0)"));

                        if (difference > 0)
                        {
                        //heading < azimuth, roll right
                        target_roll = -20;
                        //target_roll = -difference/10;
                        }
                        else if (difference < 0)
                        {
                        //heading > azimuth, roll left
                        target_roll = 20;
                        //target_roll = -difference/10;
                        }
                        else {
                        target_roll = 0;
                        }
                    }

                    double angle_roll_difference = target_roll - main.gravityObject.angle_roll;
                    if (angle_roll_difference > 90) {
                        angle_roll_difference = 90;
                    } else if (angle_roll_difference < -90) {
                        angle_roll_difference = -90;
                    }

                    aileronrvalue = (short) (angle_roll_difference * 364);
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

                /** P-- yaw adjustment */
                if (auto_yaw && main.inputObject.controllerX1value == 0 && main.locObject.waypointNext != null) {
                    /** turning with rudder */
                /*
                    double difference = (((360 + (main.magObject.azimuth - main.magObject.heading)) % 360) - 180);

                    main.update.updateConversationHandler.post(new updateTextThread(main.angle_text_heading, (int) main.magObject.heading + "\u00B0\n" + (int) main.magObject.azimuth + "\u00B0\n" + (int) difference + "\u00B0"));

                    ruddervalue = (short) (difference * 182);

                    if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionPrecisely(main.outputsRudder, ruddervalue, period, main.ch340commObject.servoRudderMin, main.ch340commObject.servoRudderMax);
                    }

                    main.rdr = (int) ((-ruddervalue / 32.767) + 1500);
                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarRDR, main.inputObject.scaleDown(main.rdr)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Integer.toString(main.inputObject.scaleDown(main.rdr)) + "%"));
                */
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
