
import com.pi4j.io.gpio.PinPullResistance;
import net.happybrackets.core.HBAction;
import net.happybrackets.core.HBReset;
import net.happybrackets.core.scheduling.Clock;
import net.happybrackets.device.HB;
import net.happybrackets.device.sensors.gpio.GPIO;
import net.happybrackets.device.sensors.gpio.GPIODigitalOutput;
import net.happybrackets.device.sensors.gpio.GPIOInput;
import net.happybrackets.device.sensors.gpio.GPIOInputListener;


import java.lang.invoke.MethodHandles;

public class buttonTesting implements HBAction {
    private static final int GPIO_ENABLE =  28;
    // Define what outr GPIO Input pin is
    final int GPIO_NUMBER = 25;


    @Override
    public void action(HB hb) {
        //reset
        hb.reset();
        hb.setStatus(this.getClass().getSimpleName() + " Loaded");

        // Reset all our GPIO - Only really necessary if the Pin has been assigned as something other than an input before
        GPIO.resetAllGPIO();

        /* Type gpioDigitalOut to create this code */
        GPIODigitalOutput outputPin = GPIODigitalOutput.getOutputPin(GPIO_ENABLE);
        if (outputPin == null) {
            hb.setStatus("Fail GPIO Digital Out " + GPIO_ENABLE);
        }/* End gpioDigitalOut code */
        else {
            outputPin.setState(true);
        }

        /* Type gpioDigitalIn to create this code*/
        GPIOInput inputPin = GPIOInput.getInputPin(GPIO_NUMBER, PinPullResistance.PULL_UP);
        if (inputPin != null) {
            inputPin.addStateListener((sensor, new_state) -> {/* Write your code below this line */
                hb.setStatus("GPIO State: " + new_state);
                inputPin.stateChanged(sensor, new_state);
                /* Write your code above this line */
            });
        } else {
            hb.setStatus("Fail GPIO Input " + GPIO_NUMBER);
        }/* End gpioDigitalIn code */


    }
}
