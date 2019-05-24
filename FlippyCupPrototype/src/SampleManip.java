

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

    final String AUDIO_FILES[] = {
            "data/audio/OurLove.wav",
            "data/audio/CountingCornelius.wav",
            "data/audio/FlyingLotus.wav",
            "data/audio/DrumsFugazi.wav"

    };


    /*Sample Controls*/
    int audioFile = 3;  //sample choice
    final int SAMPLE_CUTS = 25;  //number of cuts in the sample
    final Boolean GATE = true;  //gate control is off or on
    //defining sample list
    Sample sampleList[] = new Sample[AUDIO_FILES.length];
    /*End of Controls*/

    /*Clock Controls*/
    final double RECORDING_TIME = 10000;
    final double RECORDING_INTERVALS = 50;
    //0: choose sample(done), 1: manipulate sample(done), 2: recordSample (not complete)
    int SYSTEM_STATE = 1;
    /*End of Controls*/


    /*accelerometer values*/
    boolean liveFeed = true;
    boolean ReturnedFlag = false;
    float currentX = 0;
    float currentY = 0;
    float currentZ = 0;
    /*end of values*/

    final float NORMAL_SPEED = 1;        // defined for speed of sample
    final int NUMBER_AUDIO_CHANNELS = 1; // define how many audio channels our device is using



    @Override
    public void action(HB hb) {
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
        // remove this code if you do not want other compositions t8o run at the same time as this one
        //hb.reset();
        hb.setStatus(this.getClass().getSimpleName() + " Loaded");

        //Sample Creation
        final float INITIAL_VOLUME = 1f; // define how loud we want the sound
        Glide audioVolume = new Glide(INITIAL_VOLUME);
        Glide sampleSpeed = new Glide(NORMAL_SPEED);

        //create sample edits class
        SampleEdits editList = new SampleEdits();
        editList.editN = 0;
        editList.currentEdit = 0;
        editList.recordedSensor = new SensorValue[(int)(RECORDING_TIME/RECORDING_INTERVALS)];


        // create our sample
        for(int x = 0; x < AUDIO_FILES.length; x++) {
            sampleList[x] = SampleManager.sample(AUDIO_FILES[x]);
        }

        Sample sample = sampleList[audioFile];

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

            sampleStart(samplePlayer);
            // now set the loop start and end in the actual sample player
            final float LOOP_LENGTH = (float)sampleList[audioFile].getLength();

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
                        System.out.println("not on live feed");

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
                        manipulateSample(samplePlayer, LOOP_LENGTH);

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
                        manipulateSample(samplePlayer, LOOP_LENGTH);
                    }
                    else {
//                        samplePlayer.setLoopType(SamplePlayer.LoopType.NO_LOOP_FORWARDS);
//                        samplePlayer.setToEnd();
//                        clock.stop();
//                        System.out.println("we are done");
                        editList.currentEdit = 0;
                    }
                }
            });

        } else {
            hb.setStatus("Failed sample " + AUDIO_FILES[audioFile]);
        }




    }


    void manipulateSample(SamplePlayer samplePlayer, float loopLength) {

        switch(SYSTEM_STATE) {
            case 0:             //Choose the sample you would like to play
                changeSample(samplePlayer);
                break;
            case 1:             //Manipulate the sample
                sampleCutting(samplePlayer, loopLength);
                break;
            case 2:             //Play the sampel

        }

    }

    void changeSample(SamplePlayer samplePlayer) {
        if(ReturnedFlag) {
            if (currentY > .9) {
                audioFile++;
                if (audioFile == sampleList.length)
                    audioFile = 0;
                samplePlayer.setSample(sampleList[audioFile]);
                sampleStart(samplePlayer);
            } else if (currentY < -.9) {
                audioFile--;
                if (audioFile < 0)
                    audioFile = sampleList.length;
                samplePlayer.setSample(sampleList[audioFile]);
                sampleStart(samplePlayer);
            }

        }
        else {
            if(-.8 < currentY && currentY < .8) {
                ReturnedFlag = true;
            }
        }
    }

    void sampleCutting(SamplePlayer samplePlayer, float loopLength) {
        if(ReturnedFlag) {
            System.out.println("Current y" + currentY);
            // we will only do a change if our yaw is >= 1 or <= -1
            if (currentZ > 0) {
                float y_scaled = Sensor.scaleValue(-1, 1, 0, SAMPLE_CUTS, currentY);
                samplePlayer.start(loopLength * (int) y_scaled / SAMPLE_CUTS);
                ReturnedFlag = false;
            } else {
                //must be positive
            }
        }
        else {
            if(currentZ < 0) {
                ReturnedFlag = true;
                if(GATE) {
                    samplePlayer.pause(true);
                }
            }

        }
    }

    void updateLastValues(float lastX, float lastY, float lastZ) {
        currentX = lastX;
        currentY = lastY;
        currentZ = lastZ;
    }

    void sampleStart(SamplePlayer samplePlayer) {
        float start = 0;
        float length = (float)sampleList[audioFile].getLength();

        // create our looping objects
        Glide loopStart = new Glide(start);
        Glide loopEnd = new Glide(length);
        samplePlayer.setLoopStart(loopStart);
        samplePlayer.setLoopEnd(loopEnd);
        ReturnedFlag = false;
    }
}
