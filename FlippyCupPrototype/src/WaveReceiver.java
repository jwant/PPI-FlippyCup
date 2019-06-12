
import de.sciss.net.OSCMessage;
import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.data.Sample;
import net.beadsproject.beads.data.SampleManager;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.Glide;
import net.beadsproject.beads.ugens.SamplePlayer;
import net.happybrackets.core.HBAction;
import net.happybrackets.core.HBReset;
import net.happybrackets.core.OSCUDPListener;
import net.happybrackets.core.OSCUDPSender;
import net.happybrackets.core.control.*;
import net.happybrackets.core.instruments.WaveModule;
import net.happybrackets.core.scheduling.Clock;
import net.happybrackets.device.HB;
import net.happybrackets.device.sensors.AccelerometerListener;
import net.happybrackets.device.sensors.Sensor;

import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;

/* THis composition will use the accelerometer sensor and map each axis to a different wave Type

 */
public class WaveReceiver implements HBAction, HBReset {
    // Change to the number of audio Channels on your device
    final int NUMBER_AUDIO_CHANNELS = 1;

    final String AUDIO_FILES[] = {
            "data/audio/whackPiano.wav",
            "data/audio/violinsSufjan.wav",
            "data/audio/thundercatDrums.wav",
            "data/audio/taylorPiano.wav",
            "data/audio/pharoTrumpet.wav",
            "data/audio/internetGuitar.wav",
            "data/audio/hakimPiano.wav",
            "data/audio/guitarMac.wav",
            "data/audio/bjorkTrumpet.wav",
            "data/audio/bassPharo.wav"

    };

    // This variable will become true when the composition is reset
    boolean compositionReset = false;

    //sample location
    int sampleLocation = 0;
    final Boolean REPEAT = true;
    Boolean ReturnedFlag = false;
    final Boolean GATE = false;
    final int SAMPLE_CUTS = 4;

    /*Clock Controls*/
    final double RECORDING_TIME = 10000;
    final double RECORDING_INTERVALS = 50;

    float currentX = 0;
    float currentY = 0;
    float currentZ = 0;

    class SensorValue {
        long ticks;
        float x;
        float y;
        float z;
    }
    class SampleEdits {
        int editN;
        int currentEdit;
        SensorValue recordedSensor[];
    }


    @Override
    public void action(HB hb) {


        /***** Type your HBAction code below this line ******/
        // remove this code if you do not want other compositions to run at the same time as this one
//        hb.reset();
        hb.setStatus(this.getClass().getSimpleName() + " Loaded");


        SampleEdits editList = new SampleEdits();
        editList.currentEdit = 0;
        editList.editN = 0;
        editList.recordedSensor = new SensorValue[(int)(RECORDING_TIME/RECORDING_INTERVALS)];





        // +++ Start of receiver code  +++

        // This is where we will display out Message
        TextControl receivedMessageControl = new TextControlSender(this, "Received Message", "");

        /* type osclistener to create this code to listen on port 9000 */
        OSCUDPListener oscudpListener = new OSCUDPListener(9000) {

            @Override
            public void OSCReceived(OSCMessage oscMessage, SocketAddress socketAddress, long l) {

                System.out.println("Received Message");

                switch(oscMessage.getName()) {
                    case "SampleLocation":
                        sampleLocation = (int)oscMessage.getArg(0);
                        break;
                    case "SensorValue":
                        addSensorValue(editList, oscMessage);
                        editList.editN++;
                        break;
                    case "PlaySample":
                        if(!playSample(hb, editList))   {
                            hb.setStatus("Failed sample " + AUDIO_FILES[sampleLocation]);
                        }
                        break;

                }
            }
        };


        if (oscudpListener.getPort() < 0){ //port less than zero is an error
            String error_message =  oscudpListener.getLastError();
            System.out.println("Error opening port " + 9000 + " " + error_message);
        } /** end oscListener code */

        // +++ End of receiver code ++//

        /***** Type your HBAction code above this line ******/
    }


    /**
     * Add any code you need to have occur when a reset occurs
     */
    @Override
    public void doReset() {
        compositionReset = true;
        /***** Type your HBReset code below this line ******/

        /***** Type your HBReset code above this line ******/
    }

    void sampleStart(SamplePlayer samplePlayer, float length) {
        float start = 0;

        // create our looping objects
        Glide loopStart = new Glide(start);
        Glide loopEnd = new Glide(length);
        samplePlayer.setLoopStart(loopStart);
        samplePlayer.setLoopEnd(loopEnd);
        ReturnedFlag = false;
    }

    void manipulateSample(SamplePlayer samplePlayer, float loopLength, Glide sampleSpeed) {
//        if(ReturnedFlag) {
//            // we will only do a change if our yaw is >= 1 or <= -1
//            if (currentZ > 0) {
//                float y_scaled = Sensor.scaleValue(1, -1, 0, SAMPLE_CUTS, currentY);
//                samplePlayer.start(loopLength * (int) y_scaled / SAMPLE_CUTS);
//                ReturnedFlag = false;
//            } else {
//                //must be positive
//            }
//        }
//        else {
//            if(currentZ < -.2) {
//                ReturnedFlag = true;
//                if(GATE) {
//                    samplePlayer.pause(true);
//                }
//            }
//
//        }
        sampleSpeed.setValue(currentZ + 1);
    }

    void updateLastValues(float lastX, float lastY, float lastZ) {
        currentX = lastX;
        currentY = lastY;
        currentZ = lastZ;
    }

    void addSensorValue(SampleEdits sampleEdits, OSCMessage oscMessage) {
        SensorValue sensorValue = new SensorValue();
        sensorValue.ticks = (int)oscMessage.getArg(0);
        sensorValue.x = (float)oscMessage.getArg(1);
        sensorValue.y = (float)oscMessage.getArg(2);
        sensorValue.z = (float)oscMessage.getArg(3);


        sampleEdits.recordedSensor[(int)sensorValue.ticks] = sensorValue;
    }

    Boolean playSample(HB hb, SampleEdits editList) {

        System.out.println("edit number " + editList.editN);

        final float NORMAL_SPEED = 1;        // defined for speed of sample
        final int NUMBER_AUDIO_CHANNELS = 1; // define how many audio channels our device is using

        Sample sample = SampleManager.sample(AUDIO_FILES[sampleLocation]);

        //Sample Creation
        final float INITIAL_VOLUME = 1f; // define how loud we want the sound
        final float HUSHED_VOLUME = INITIAL_VOLUME / 20; // define how loud we want the sound
        Glide audioVolume = new Glide(INITIAL_VOLUME);
        Glide sampleSpeed = new Glide(NORMAL_SPEED);

        if (sample != null) {
            // Create our sample player
            SamplePlayer samplePlayer = new SamplePlayer(sample);
            // Samples are killed by default at end. We will stop this default actions so our sample will stay alive
            samplePlayer.setKillOnEnd(false);

            // Connect our sample player to audio
            Gain gainAmplifier = new Gain(NUMBER_AUDIO_CHANNELS, audioVolume);
            gainAmplifier.addInput(samplePlayer);
            hb.ac.out.addInput(gainAmplifier);

            samplePlayer.setLoopType(SamplePlayer.LoopType.LOOP_FORWARDS);

            float loopLength = (float)sample.getLength();

            sampleStart(samplePlayer,loopLength);

            samplePlayer.setRate(sampleSpeed);

            /*****************************************************************
             * Global Volume:
             * Sets the volume of any non-active sketches
             *****************************************************************/
            /* Type globalBooleanControl to generate this code */
            BooleanControl globalOnOff = new BooleanControl(this, "On / Off", true) {
                @Override
                public void valueChanged(Boolean control_val) { /* Write your DynamicControl code below this line */
                    if (control_val){
                        audioVolume.setValue(INITIAL_VOLUME);
                    }
                    else {
                        audioVolume.setValue(HUSHED_VOLUME);
                    }

                }
            };
            globalOnOff.setControlScope(ControlScope.GLOBAL);

            /*****************************************************************
             * Clock Section:
             * Recording: total recording time of the sample
             * Clock: how often sensor values are stored
             *****************************************************************/

            //Clock recording = hb.createClock(RECORDING_TIME).start();
            Clock clock = hb.createClock(RECORDING_INTERVALS).start();
            // Create a clock with beat interval of CLOCK_INTERVAL ms

            samplePlayer.setToEnd();
            editList.currentEdit = 0;



            clock.addClockTickListener((offset, this_clock) -> {
                if (editList.currentEdit < editList.editN) {
                    updateLastValues(
                            editList.recordedSensor[editList.currentEdit].x,
                            editList.recordedSensor[editList.currentEdit].y,
                            editList.recordedSensor[editList.currentEdit].z
                    );



                    editList.currentEdit++;
                    manipulateSample(samplePlayer, loopLength, sampleSpeed);
                }
                else {
                    if(!REPEAT) {
                        samplePlayer.setLoopType(SamplePlayer.LoopType.NO_LOOP_FORWARDS);
                        samplePlayer.setToEnd();
                        clock.stop();
                        System.out.println("we are done");
                        editList.currentEdit = 0;
                        hb.reset();
                    }
                    else {
                        editList.currentEdit = 0;
                        samplePlayer.setToEnd();
                    }

                }
            });

            return true;
        }
        else {

            return false;
        }
    }


}
