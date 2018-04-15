package pl.bezzalogowe.PhoneUAV;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by uzytkownik on 2018-03-10.
 * This is a stub. When "autopilot" is enabled, moving elevator sets target pitch between -30 and 30 degrees.
 */

public class Autopilot {
    MainActivity main;

    boolean stabilize_roll = false;
    boolean stabilize_pitch = false;

    double target_roll = 0;
    double target_pitch = 0;

    ScheduledExecutorService executorAutopilot;

    public void startAutopilot(MainActivity activityArgument) {
        main = activityArgument;
    }

    private void stablilizeRoll() {
        /** Stabilizes roll only */

        main.ail = (int) (-main.gravityObject.angle_roll / 0.18) + 1500;

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPosition(main.outputsAileron, main.ail, (byte) 100);

            main.ch340commObject.SetPosition(main.outputsFlaperonLeft, main.ail, (byte) 100);
            main.ch340commObject.SetPosition(main.outputsFlaperonRight, -main.ail + 3000, (byte) 100);
        }

        main.seekbarAIL.setProgress(main.inputObject.scaleDown(main.ail));
    }

    private void stablilizePitch() {
        /** Stabilizes pitch only */

        if (main.gravityObject.angle_pitch > 90) {
            // inverted and climbing
            main.ele = 2500;
        } else if (main.gravityObject.angle_pitch < -90) {
            // inverted and descending
            main.ele = 500;
        } else {
            main.ele = (int) ((main.gravityObject.angle_pitch - target_pitch) / 0.09 + 1500);
        }

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPosition(main.outputsElevator, main.ele, (byte) 100);
        }

        main.seekbarELEV.setProgress(main.inputObject.scaleDown(main.ele));
    }

    public void processGravity() {
        if (stabilize_pitch == true && stabilize_roll == true) {
            /** Stabilizes both pitch and roll */
            stablilizePitch();
            stablilizeRoll();
        } else if (stabilize_pitch == true) {
            stablilizePitch();
        } else if (stabilize_roll == true) {
            stablilizeRoll();
        }
    }

    public void disableAutopilot(MainActivity argActivity) {
        main = argActivity;
        try {
            executorAutopilot.shutdownNow();
            if (executorAutopilot.isShutdown()) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot stopped"));
            }
        } catch (Exception e) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot NOT stopped"));
        }
        executorAutopilot = null;
    }
}
