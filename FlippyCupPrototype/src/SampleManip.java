

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
    class SensorValue {
        long ticks;
        float x;
        float y;
        float z;
    }
    class SampleEdits {
        String audioFileID;
        int variationsN;
        int currentEdit;
        SensorValue recordedSensor[];
    }


    /*Sample Controls*/
    final String AUDIO_FILE = "CountingCornelius.wav";  //sample choice
    final int SAMPLE_CUTS = 6;  //number of cuts in the sample
    final Boolean GATE = true;  //gate control is off or on
    /*End of Controls*/

    /*Clock Controls*/
    final double RECORDING_TIME = 10000;
    final double RECORDING_INTERVALS = 500;
    /*End of Controls*/


    /*accelerometer values*/
    float currentX;
    float currentY;
    float currentZ;
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
            int variationsN;
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
        SampleEdits sampleEdits = new SampleEdits();
        sampleEdits.variationsN = 0;
        sampleEdits.audioFileID = AUDIO_FILE;
        sampleEdits.currentEdit = 0;

        // Define our sample name
        final String SAMPLE_NAME = "data/audio/" + sampleEdits.audioFileID;

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




            /*****************************************************************
             * Sensor Section:
             * Accelerometer: Currently does nothing
            *****************************************************************/
            new  AccelerometerListener(hb) {

                @Override
                public void sensorUpdated(float xVal, float yVal, float zVal) {
                    updateLastValues(xVal, yVal, zVal);
                }
            };

            Clock recording = hb.createClock(RECORDING_TIME).start();
            Clock clock = hb.createClock(RECORDING_INTERVALS).start();
            // Create a clock with beat interval of CLOCK_INTERVAL ms
            clock.addClockTickListener((offset, this_clock) -> {
                if(recording.getNumberTicks() > 0) {
                    //Reached the end of the recording time
                    System.out.println("Recording Finished");
                    recording.stop();
                    clock.stop();
                    samplePlayer.setLoopType(SamplePlayer.LoopType.NO_LOOP_FORWARDS);
                    samplePlayer.setToEnd();
                }
                else if(recording.isRunning()) {
                    //Record the values of the sensors
                    System.out.println("number of ticks: " + clock.getNumberTicks());
                    hb.getAccelerometer_X(-1,1);
                    System.out.println(
                            "Value of x: " + currentX + "\n" +
                            "Value of y: " + currentY + "\n" +
                            "Value of z: " + currentZ + "\n"
                            );
                }
                else {
                    //play the sample with the edits
                    //playEdits(sampleSpeed, sampleEdits);
                }
            });

        } else {
            hb.setStatus("Failed sample " + SAMPLE_NAME);
        }




    }

    SensorValue createEdit(long position) {
        SensorValue values = new SensorValue();
        values.ticks = position;
        values.x = currentX;
        values.y = currentY;
        values.z = currentZ;
        return values;
    }

    void addEdit(SampleEdits sampleEdits, long position) {
        sampleEdits.currentEdit++;
        sampleEdits.recordedSensor[sampleEdits.currentEdit] = createEdit(position);
    }

    void playEdits(Glide sampleSpeed, SampleEdits edits) {
        Clock recordingInterval = new Clock(RECORDING_INTERVALS).start();
        recordingInterval.addClockTickListener((offset, this_clock) -> {
           if(edits.variationsN != edits.currentEdit) {
               if(recordingInterval.getNumberTicks() == edits.recordedSensor[edits.currentEdit].ticks) {
                   //put in recording variation samples
                   manipulateSample(sampleSpeed,
                           edits.recordedSensor[edits.currentEdit].x,
                           edits.recordedSensor[edits.currentEdit].y,
                           edits.recordedSensor[edits.currentEdit].z
                   );
                   edits.currentEdit++;
               }
           }
        });
    }

    void manipulateSample(Glide sampleSpeed, float x, float y, float z) {
        sampleSpeed.setValue(x + 1);
    }

    void updateLastValues(float lastX, float lastY, float lastZ) {
        currentX = lastX;
        currentY = lastY;
        currentZ = lastZ;
    }

}
