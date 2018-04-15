package pl.bezzalogowe.PhoneUAV;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

public class Magnetometer {
    public float axisX;
    public float axisY;
    public float axisZ;
    double magnetic_declination;
    double heading = 0;
    double azimuth = 0;

    MainActivity main;
    public Sensor sensorInstanceMag;
    SensorManager magnetometerManager;

    public void calculateMagnetometer(SensorEvent event) {

        float[] magVectorDevice = new float[3];
        float[] magVectorVehicle = new float[3];

        magVectorDevice[0] = event.values[0];
        magVectorDevice[1] = event.values[1];
        magVectorDevice[2] = event.values[2];

        magVectorVehicle = main.accObject.rotateAroundZ(magVectorDevice, main.device_orientation[2]);

        // TODO: doesn't take Z axis into account, should it?
        heading = (((Math.toDegrees(-Math.atan2((double) magVectorVehicle[0], (double) magVectorVehicle[1]))) + 360) % 360) + magnetic_declination;

        try {
            // FIXME throws errors until first GPS lock
            main.magObject.azimuth = (main.locObject.recentLocation.bearingTo(main.locObject.waypointNext) + 360) % 360;
        } catch (Exception e) {
        }
/**
heading - vehicle orientation
azimuth - direction towards next waypoint
*/
        String arrowstring = null;
        if (main.locObject.recentLocation == null)
            arrowstring = "...";
        else {
            double difference = (360 + (main.magObject.heading - main.magObject.azimuth)) % 360;

            if (0 <= difference && difference < 180) {
                if (0 <= difference && difference < 22.5) {
                    arrowstring = "\u276c";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 0, 255, 0));
                } else if (22.5 <= difference && difference < 67.5) {
                    arrowstring = "\u276c\u276c";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 128, 255, 0));
                } else if (67.5 <= difference && difference < 112.5) {
                    arrowstring = "\u276c\u276c\u276c";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 255, 0));
                } else if (112.5 <= difference && difference < 157.5) {
                    arrowstring = "\u276c\u276c\u276c\u276c";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 128, 0));
                } else if (157.5 <= difference && difference < 180) {
                    arrowstring = "\u276c\u276c\u276c\u276c\u276c";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 0, 0));
                }
                // String.format("%.00f", difference)
            } else if (180 <= difference && difference < 360) {
                if (180 <= difference && difference <= 202.5) {
                    arrowstring = "\u276d\u276d\u276d\u276d\u276d";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 0, 0));
                } else if (202.5 < difference && difference <= 247.5) {
                    arrowstring = "\u276d\u276d\u276d\u276d";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 128, 0));
                } else if (247.5 < difference && difference <= 292.5) {
                    arrowstring = "\u276d\u276d\u276d";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 255, 255, 0));
                } else if (292.5 < difference && difference <= 337.5) {
                    arrowstring = "\u276d\u276d";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 128, 255, 0));
                } else if (337.5 < difference && difference < 360) {
                    arrowstring = "\u276d";
                    main.update.updateConversationHandler.post(new updateTextColorThread(main.angle_text_heading, 0, 255, 0));
                }
                // String.format("%.00f", difference)
            }

            //TODO: Add separate variable for toggling rudder autopilot.
            if (main.autopilot.stabilize_roll == true) {

                short rudder = 0;
                if (0 <= difference && difference < 180) {
                    // you're right off course, turn left
                    if (0 <= difference && difference < 22.5) {
                        rudder = 0;
                    } else {
                        rudder = 16384;
                    }

                } else if (180 <= difference && difference < 360) {
                    // you're left off course, turn right
                    if (337.5 < difference && difference < 360) {
                        rudder = 0;
                    } else {
                        rudder = -16384;
                    }
                }

                main.rdr = (rudder / 131) + 500;
                main.seekbarRDR.setProgress(main.rdr / 10);
            }
        }
        main.update.updateConversationHandler.post(new updateTextThread(main.angle_text_heading,
                String.format("%.00f", main.magObject.heading) + "\u00B0\n" +
                String.format("%.00f", main.magObject.azimuth) + "\u00B0" + " " + arrowstring));
    }

    public void startMagnetometer(MainActivity argActivity) {
        main = argActivity;
        magnetometerManager = (SensorManager) main.getSystemService(Context.SENSOR_SERVICE);
        if (magnetometerManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {

            sensorInstanceMag = magnetometerManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            magnetometerManager.registerListener(main, sensorInstanceMag, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.d("onCreate", "Sensor error!");
        }
    }
}