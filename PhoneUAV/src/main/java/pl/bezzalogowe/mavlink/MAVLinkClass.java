package pl.bezzalogowe.mavlink;
import pl.bezzalogowe.PhoneUAV.*;
public class MAVLinkClass {
    private final static String TAG = MAVLinkClass.class.getName();

    /* Used to load the native library on application startup. */
    static {
        System.loadLibrary("mavlink_udp");
    }

    MainActivity main;

    public MAVLinkClass(final MainActivity argActivity) {
        main = argActivity;
    }

    public static native void classInit();

    public native int receiveInit();
    public native int receiveStop();

    public native int heartBeatInit();
    public native int heartBeatStop();

    /* Displays a string from C code */
    public native String stringFromJNI();

    public native int sendProtocol();

    public native int sendHello();

    public native void setHeadingDegrees(double hdg);

    public native void sendAttitude(float roll, float pitch/*, float heading*/);

    public native void setBattery(int voltage, int level);

    public native void sendGlobalPosition(double lat, double lon, double alt, double relativeAlt);

    /* Called from native code. This sets the content of the TextView from the UI thread. */
    private void setMessage(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setButtons(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setAddress(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setLog(final String message) {
        //Log.d(TAG, message);
        System.out.println(message);
    }

    /* pitch, roll, thrust, yaw */
    private void setProgress(short x, short y, short z, short r) {
        main.seekbarELEV.setProgress(x/20 + 50);
        main.seekbarAIL.setProgress(y/20 + 50);
        main.seekbarTHROT.setProgress(z/20 + 50);
        main.seekbarRDR.setProgress(r/20 + 50);
    }

    private void setServos(short x, short y, short z, short r) {
        //TODO: Servos should not be moved using GUI elements, this is not the fastest way
    }
}
