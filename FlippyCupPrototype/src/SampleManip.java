

import net.beadsproject.beads.data.Sample;
import net.beadsproject.beads.data.SampleManager;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.Glide;
import net.beadsproject.beads.ugens.SamplePlayer;
import net.happybrackets.core.HBAction;
import net.happybrackets.core.scheduling.Clock;
import net.happybrackets.device.HB;
import net.happybrackets.device.sensors.Accelerometer;
import net.happybrackets.device.sensors.GyroscopeListener;
import net.happybrackets.device.sensors.AccelerometerListener;
import net.happybrackets.device.sensors.Sensor;



public class SampleManip implements HBAction {

    /*Sample Controls*/
    final String AUDIO_FILE = "OurLove.wav";  //sample choice
    final int SAMPLE_CUTS = 6;  //number of cuts in the sample
    final Boolean GATE = true;  //gate control is off or on
    /*End of Controls*/

    /*Clock Controls*/
    final double RECORDING_TIME = 10000;
    final double RECORDING_INTERVALS = 50;
    /*End of Controls*/


    /*accelerometer values*/
    boolean liveFeed = true;
    float currentX = 0;
    float currentY = 0;
    float currentZ = 0;
    /*end of values*/

    final float NORMAL_SPEED = 1;        // defined for speed of sample
    final int NUMBER_AUDIO_CHANNELS = 1; // define how many audio channels our device is using



    @Override
    public void action(HB hb) {
        class SensorValue {
            String Sensor;
            long ticks;
            float x;
            float y;
            float z;
        }
        class SampleEdits {
            String audioFileID;
            int editN;
            int currentEdit;
            SensorValue recordedSensor[];
        }
        // remove this code if you do not want other compositions t8o run at the same time as this one
        hb.reset();
        hb.setStatus(this.getClass().getSimpleName() + " Loaded");

        //Sample Creation
        final float INITIAL_VOLUME = 1f; // define how loud we want the sound
        Glide audioVolume = new Glide(INITIAL_VOLUME);
        Glide sampleSpeed = new Glide(NORMAL_SPEED);

        //create sample edits class
        SampleEdits editList = new SampleEdits();
        editList.audioFileID = AUDIO_FILE;
        editList.editN = 0;
        editList.currentEdit = 0;
        editList.recordedSensor = new SensorValue[(int)(RECORDING_TIME/RECORDING_INTERVALS)];

        // Define our sample name
        final String SAMPLE_NAME = "data/audio/" + editList.audioFileID;

        // create our actual sample
        Sample sample = SampleManager.sample(SAMPLE_NAME);

        // test if we opened the sample successfully
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
            // now set the loop start and end in the actual sample player
            final float LOOP_START = 0;
            final float LOOP_LENGTH = (float)sample.getLength();
            final float LOOP_END = (float)LOOP_LENGTH;

            // create our looping objects
            Glide loopStart = new Glide(LOOP_START);
            Glide loopEnd = new Glide(LOOP_END);
            samplePlayer.setLoopStart(loopStart);
            samplePlayer.setLoopEnd(loopEnd);
            samplePlayer.setRate(sampleSpeed);




            /*****************************************************************
             * Sensor Section:
             * Accelerometer: Currently does nothing
            *****************************************************************/
            new  AccelerometerListener(hb) {

                @Override
                public void sensorUpdated(float xVal, float yVal, float zVal) {
                    if(liveFeed) {
                        updateLastValues(xVal, yVal, zVal);
                        manipulateSample(sampleSpeed);
                    }
                }
            };

            Clock recording = hb.createClock(RECORDING_TIME).start();
            Clock clock = hb.createClock(RECORDING_INTERVALS).start();
            // Create a clock with beat interval of CLOCK_INTERVAL ms
            clock.addClockTickListener((offset, this_clock) -> {
                if(liveFeed) {
                    if (recording.getNumberTicks() > 0) {
                        //Reached the end of the recording time
                        recording.stop();
                        //samplePlayer.setLoopType(SamplePlayer.LoopType.NO_LOOP_FORWARDS);
                        samplePlayer.setToEnd();
                        editList.currentEdit = 0;
                        liveFeed = false;

                    } else if (recording.isRunning()) {
//                    Record the values of the sensor
                        SensorValue values = new SensorValue();
                        values.ticks = clock.getNumberTicks();
                        values.x = currentX;
                        values.y = currentY;
                        values.z = currentZ;
                        editList.recordedSensor[editList.currentEdit] = values;
                        editList.currentEdit++;
                        editList.editN++;
                        System.out.println("Number of ticks " + clock.getNumberTicks());
                    }
                }
                else{
                    if(editList.currentEdit < editList.editN) {
                        updateLastValues(
                                editList.recordedSensor[editList.currentEdit].x,
                                editList.recordedSensor[editList.currentEdit].y,
                                editList.recordedSensor[editList.currentEdit].z
                        );

                        editList.currentEdit++;
                        manipulateSample(sampleSpeed);
                    }
                    else {
                        samplePlayer.setLoopType(SamplePlayer.LoopType.NO_LOOP_FORWARDS);
                        samplePlayer.setToEnd();
                        clock.stop();
                        System.out.println("we are done");
                    }
                }
            });

        } else {
            hb.setStatus("Failed sample " + SAMPLE_NAME);
        }




    }


    void manipulateSample(Glide sampleSpeed) {
        sampleSpeed.setValue(currentX * 2);
    }

    void updateLastValues(float lastX, float lastY, float lastZ) {
        currentX = lastX;
        currentY = lastY;
        currentZ = lastZ;
    }

}
