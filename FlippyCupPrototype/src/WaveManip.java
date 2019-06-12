

import com.pi4j.io.gpio.PinPullResistance;
import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.data.Sample;
import net.beadsproject.beads.data.SampleManager;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.Glide;
import net.beadsproject.beads.ugens.SamplePlayer;
import net.happybrackets.core.HBAction;
import net.happybrackets.core.OSCMessageBuilder;
import net.happybrackets.core.OSCUDPSender;
import net.happybrackets.core.control.BooleanControl;
import net.happybrackets.core.control.ControlScope;
import net.happybrackets.core.instruments.WaveModule;
import net.happybrackets.core.scheduling.Clock;
import net.happybrackets.device.HB;
import net.happybrackets.device.sensors.AccelerometerListener;
import net.happybrackets.device.sensors.Sensor;
import net.happybrackets.device.sensors.gpio.GPIO;
import net.happybrackets.device.sensors.gpio.GPIODigitalOutput;
import net.happybrackets.device.sensors.gpio.GPIOInput;


public class WaveManip implements HBAction {

    //    final String SENDING_ADDRESS = "192.168.43.224";
    final String SENDING_ADDRESS = "192.168.43.127";

    /*GPIO Controls*/
    // We need to SET GPIO 28 High to enable Input or Output for GPIO on PiHat board
    private static final int GPIO_ENABLE =  28;
    // Define what outr GPIO Input pin is
    final int GPIO_NUMBER = 25;

    /*Sample Controls*/
    final Boolean REPEAT = true;
    final float INITIAL_VOLUME = 1f; // define how loud we want the sound
    final float HUSHED_VOLUME = INITIAL_VOLUME / 20; // define how loud we want the sound
    int waveSelect = 0; //0-sine, 1-square, 2-saw
    /*End of Controls*/

    /*Clock Controls*/
    final double RECORDING_TIME = 10000;
    final double RECORDING_INTERVALS = 50;
    //0: choose sample(done), 1: manipulate sample(done), 2: recordSample (not complete)
    int SystemState = 0;
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

        // remove this code if you do not want other compositions t8o run at the same time as this one
        //hb.reset();
        hb.setStatus(this.getClass().getSimpleName() + " Loaded");


        Glide audioVolume = new Glide(INITIAL_VOLUME);

        //create sample edits class
        SampleEdits editList = new SampleEdits();
        editList.editN = 0;
        editList.currentEdit = 0;
        editList.recordedSensor = new SensorValue[(int)(RECORDING_TIME/RECORDING_INTERVALS)];

        WaveModule y_player = new WaveModule(500, audioVolume, Buffer.SQUARE);
        y_player.connectTo(hb.ac.out);

        /*****************************************************************
         * Global Volume:
         * Sets the volume of any non-active sketches
         *****************************************************************/
        /* Type globalBooleanControl to generate this code */
        BooleanControl globalOnOff = new BooleanControl(this, "On / Off", true) {
            @Override
            public void valueChanged(Boolean control_val) { /* Write your DynamicControl code below this line */
                if(!liveFeed) {
                    if (control_val){
                        audioVolume.setValue(INITIAL_VOLUME);
                    }
                    else {
                        audioVolume.setValue(HUSHED_VOLUME);
                    }
                }

            }
        };
        globalOnOff.setControlScope(ControlScope.GLOBAL);




        /*****************************************************************
         * Sensor Section:
         * Accelerometer: manipulates sample or chooses what sample to
         * play
         *****************************************************************/
        new  AccelerometerListener(hb) {

            @Override
            public void sensorUpdated(float xVal, float yVal, float zVal) {
                if(liveFeed) {
                    updateLastValues(xVal, yVal, zVal);
                    manipulateSample(y_player, audioVolume);
                }
            }
        };



        /*****************************************************************
         * Clock Section:
         * Recording: total recording time of the sample
         * Clock: how often sensor values are stored
         *****************************************************************/

        Clock recording = hb.createClock(RECORDING_TIME);
        Clock clock = hb.createClock(RECORDING_INTERVALS);
        // Create a clock with beat interval of CLOCK_INTERVAL ms
        clock.addClockTickListener((offset, this_clock) -> {
            if (liveFeed) {
                if (recording.getNumberTicks() > 0) {
                    //Reached the end of the recording time
                    recording.stop();
                    editList.currentEdit = 0;
                    liveFeed = false;
                    System.out.println("Play your manipulations");
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
                }
            }
            else {
                if (editList.currentEdit < editList.editN) {
                    updateLastValues(
                            editList.recordedSensor[editList.currentEdit].x,
                            editList.recordedSensor[editList.currentEdit].y,
                            editList.recordedSensor[editList.currentEdit].z
                    );



                    editList.currentEdit++;
                    manipulateSample(y_player, audioVolume);
                }
                else {
                    if(!REPEAT) {
                        y_player.pause(true);
                        clock.stop();
                        System.out.println("we are done");
                        editList.currentEdit = 0;
                        sendWave(editList);
                    }
                    else {
                        editList.currentEdit = 0;
                        clock.stop();
                        if(sendWave(editList))
                            clock.start();
                    }
                }
            }
        });



        /*****************************************************************
         * Button Section:
         * Input PIN: connect to a grove button
         *****************************************************************/

        // Reset all our GPIO - Only really necessary if the Pin has been assigned as something other than an input before
        GPIO.resetAllGPIO();

        //Start with sample selection
        hb.setStatus("Choose Your Sample");

        /* Type gpioDigitalIn to create this code*/
        GPIOInput inputPin = GPIOInput.getInputPin(GPIO_NUMBER, PinPullResistance.OFF);
        if (inputPin != null) {

            inputPin.addStateListener((sensor, new_state) -> {/* Write your code below this line */
                hb.setStatus("GPIO State: " + new_state);
                if(new_state) {
                    SystemState++;
                    System.out.println("System State: " + SystemState);
                    switch(SystemState) {
                        case 0: //pre working
                            hb.setStatus("hit button");
                            System.out.println("hit button");
                        case 1: //choose sample
                            hb.setStatus("Choose Your Wave");
                            System.out.println("Choose Your Wave");
                            globalOnOff.setValue(false);
                            break;
                        case 2: //manipulate sample
                            hb.setStatus("Manipulate Wave");
                            System.out.println("Manipulate Wave");
                            globalOnOff.setValue(true);
                            break;
                        case 3:
                            hb.setStatus("Record Manipulation");
                            recording.start();
                            clock.start();
                            System.out.println("Record Manipulation");
                        default:
                            hb.setStatus("Reset System");
                    }
                }
                /* Write your code above this line */
            });
        } else {
            hb.setStatus("Fail GPIO Input " + GPIO_NUMBER);
        }/* End gpioDigitalIn code */


        // Enable our GPIO on the PiHat
        GPIODigitalOutput outputPin = GPIODigitalOutput.getOutputPin(GPIO_ENABLE);
        if (outputPin == null) {
            hb.setStatus("Fail GPIO Digital Out " + GPIO_ENABLE);
        }/* End gpioDigitalOut code */
        else {
            outputPin.setState(true);
        }
    }


    void manipulateSample(WaveModule yMod, Glide volume) {

        switch(SystemState) {
            case 0:
                break;
            case 1:             //Choose the sample you would like to play
                chooseWave(yMod);
                break;
            case 2:             //Manipulate the sample
                waveUpdate(yMod, volume);
                break;
            case 3:             //manipulate sample and record
                waveUpdate(yMod, volume);
                break;
            default:
                waveUpdate(yMod, volume);
                break;

        }

    }

    void chooseWave(WaveModule y) {
        if(ReturnedFlag) {
            if (currentY < -.9) {
                waveSelect++;
                if (waveSelect > 5) waveSelect = 0;
            } else if (currentY > .9) {
                waveSelect--;
                if (waveSelect < 0) waveSelect = 5;
            }
            switch(waveSelect) {
                case 0:
                    y.setBuffer(Buffer.SINE);
                    break;
                case 1:
                    y.setBuffer(Buffer.SQUARE);
                    break;
                case 2:
                    y.setBuffer(Buffer.SAW);
                    break;
                case 3:
                    y.setBuffer(Buffer.TRIANGLE);
                    break;
                case 4:
                    y.setBuffer(Buffer.NOISE);
                    break;
                default:
                    break;
            }
            ReturnedFlag = false;

        }
        else {
            if(-.8 < currentY && currentY < .8) {
                ReturnedFlag = true;
            }
        }
    }

    void waveUpdate(WaveModule y, Glide volume) {
        y.setFrequency(Sensor.scaleValue(-1,1,0,1000, currentY));
        volume.setValue(Sensor.scaleValue(1, 0, HUSHED_VOLUME, INITIAL_VOLUME, Math.abs(currentX)));
    }

    void updateLastValues(float lastX, float lastY, float lastZ) {
        currentX = lastX;
        currentY = lastY;
        currentZ = lastZ;
    }

    boolean sendWave(SampleEdits editList) {
        // Create a UDP sender object
        OSCUDPSender oscSender = new OSCUDPSender();
        oscSender.send(HB.createOSCMessage("WaveSelect", waveSelect),SENDING_ADDRESS, 9000);
        for(int x = 0; x <(int)(RECORDING_TIME/RECORDING_INTERVALS) - 1; x++) {
            oscSender.send(HB.createOSCMessage("SensorValue",
                    x,
                    editList.recordedSensor[x].x,
                    editList.recordedSensor[x].y,
                    editList.recordedSensor[x].z
            ), SENDING_ADDRESS, 9000);
        }

        return oscSender.send(HB.createOSCMessage("PlayWave"),SENDING_ADDRESS, 9000);
    }
}
