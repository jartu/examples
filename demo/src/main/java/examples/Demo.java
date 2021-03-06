package examples;

import ev3dev.actuators.Sound;
import ev3dev.actuators.motors.EV3LargeRegulatedMotor;
import ev3dev.sensors.Battery;
import ev3dev.sensors.ev3.EV3IRSensor;
import ev3dev.sensors.slamtec.RPLidarA1;
import ev3dev.sensors.slamtec.RPLidarA1ServiceException;
import ev3dev.sensors.slamtec.RPLidarProviderListener;
import ev3dev.sensors.slamtec.model.Scan;
import ev3dev.sensors.slamtec.model.ScanDistance;
import examples.utils.JarResource;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

public @Slf4j class Demo {

    private static EV3LargeRegulatedMotor mA = null;
    private static EV3LargeRegulatedMotor mB = null;

    private static EV3IRSensor irSensor = null;
    private static SampleProvider spIR = null;

    private static Battery battery;
    private static final float VOLTAGE_THRESHOLD = 7.5f;

    private static final String USBPort = "/dev/ttyUSB0";
    private static RPLidarA1 lidar = null;

    private static final String SOUND_FILE = "R2D2a.wav";

    private static final int ITERATIONS_THRESHOLD = 20;

    public static void main(String[] args) throws Exception {

        JarResource.export("/" + SOUND_FILE);

        configRobot();

        float voltage = battery.getVoltage();
        log.debug("Voltage: {}", voltage);

        int iterations = 0;

        playSound();

        while(voltage >= VOLTAGE_THRESHOLD) {


            final Scan scan = lidar.scan();

            voltage = battery.getVoltage();
            log.debug("Voltage: {}", voltage);

            iterations++;
            if(iterations > ITERATIONS_THRESHOLD){
                break;
            }
        }

        lidar.close();
        JarResource.delete(SOUND_FILE);
        stop();
        log.info("End demo");
        System.exit(0);
    }

    private static boolean isBetterLeft(final Scan scan) {

        final Double leftAverage = scan.getDistances()
                .stream()
                .filter((measure) -> measure.getQuality() > 1)
                .filter((measure) -> (measure.getAngle() >= 181))
                .mapToDouble(ScanDistance::getDistance)
                .average()
                .getAsDouble();
        log.debug("Left Average: {}", leftAverage);

        final Double rightAverage = scan.getDistances()
                .stream()
                .filter((measure) -> measure.getQuality() > 1)
                .filter((measure) -> (measure.getAngle() <= 180))
                .mapToDouble(ScanDistance::getDistance)
                .average()
                .getAsDouble();
        log.debug("Right Average: {}", rightAverage);

        if(leftAverage <= rightAverage){
            return true;
        }
        return false;
    }

    private static void configRobot() throws RPLidarA1ServiceException {
        mA = new EV3LargeRegulatedMotor(MotorPort.A);
        mB = new EV3LargeRegulatedMotor(MotorPort.B);
        mA.setSpeed(300);
        mB.setSpeed(300);

        irSensor = new EV3IRSensor(SensorPort.S2);
        spIR = irSensor.getDistanceMode();
        battery = Battery.getInstance();
        lidar = new RPLidarA1(USBPort);
        lidar.init();
        lidar.addListener(new RPLidarProviderListener() {

            //TODO Review interface.
            @Override
            public Scan scanFinished(Scan scan) {
                log.info("Scan Measures: {}", scan.getDistances().size());


                if (!isSafeForward(scan)) {

                    stop();

                    playSound();

                    backward();

                    if (isBetterLeft(scan)){
                        turnLeft();
                    }else {
                        turnRight();
                    }

                }

                forward();


                return scan;
            }
        });
    }

    private static boolean isSafeForward(final Scan scan){

        final long counter = scan.getDistances()
                .stream()
                .filter((measure) -> measure.getQuality() > 1)
                .filter((measure) -> (measure.getAngle() >= 350 || measure.getAngle() <= 10))
                .filter((measure) -> measure.getDistance() <= 50)
                .count();

        scan.getDistances()
                .stream()
                .filter((measure) -> measure.getQuality() > 1)
                .filter((measure) -> (measure.getAngle() >= 350 || measure.getAngle() <= 10))
                .filter((measure) -> measure.getDistance() <= 50)
                .forEach(System.out::println);

        //TODO Move to another event
        final float[] sample = new float[spIR.sampleSize()];
        spIR.fetchSample(sample, 0);
        final int distanceValue = (int)sample[0];

        log.debug("Counter: {}", counter);
        log.debug("IR Distance: {}", distanceValue);

        if ((counter > 5) || (distanceValue < 50)){
            return false;
        }
        return true;
    }

    private static void playSound(){
        Sound sound = Sound.getInstance();
        sound.playSample(new File(SOUND_FILE));
    }

    private static void stop(){
        log.info("Stop");
        mA.stop();
        mB.stop();
    }

    private static void forward(){
        log.info("Forward");
        mA.forward();
        mB.forward();
        Delay.msDelay(500);
    }

    private static void backward(){
        log.info("Backward");
        mA.backward();
        mB.backward();
        Delay.msDelay(1000);
    }

    private static void turnRight() {
        mA.forward();
        mB.stop();
        Delay.msDelay(1000);
    }

    private static void turnLeft() {
        mA.stop();
        mB.forward();
        Delay.msDelay(1000);
    }

}
