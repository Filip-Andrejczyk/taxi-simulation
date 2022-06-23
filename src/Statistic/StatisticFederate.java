package Statistic;


import Statistic.monitors.Diagram;
import Statistic.monitors.MonitoredVar;
import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import org.jgroups.util.Triple;
import org.jgroups.util.Tuple;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;
import util.SimPar;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StatisticFederate extends Thread{

    private AnchorPane pane;
    private ArrayList<Label> passQueues;
    private ArrayList<Label> taxiQueues;
    private Label ridesNum, waitingNum, timeLabel, waitingMean;

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private StatisticFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    //subs for interactions:
    protected InteractionClassHandle executeRideHandle;
    protected ParameterHandle executeRide_destinationId;
    protected ParameterHandle executeRide_passengerId;
    protected ParameterHandle executeRide_taxiId;
    protected ParameterHandle executeRide_rideTime;

    protected ObjectClassHandle areaHandle;
    protected AttributeHandle areaHandle_areaId;
    protected AttributeHandle areaHandle_queueLength;
    protected AttributeHandle areaHandle_taxiQueueLength;
    public ObjectInstanceHandle areaInstanceHandle;

    private List<Triple<Integer, MonitoredVar, Integer>> areaLengths;
    private int rideCounter =0;
    private double sumOfWaiting=0;

    public StatisticFederate(Label passNum1, Label passNum2, Label passNum3, Label passNum4,
                             Label taxiNum1, Label taxiNum2, Label taxiNum3, Label taxiNum4,
                             Label ridesNum, Label waitingNum, Label timeLabel, Label waitingMean) {
        passQueues = new ArrayList<>();
        taxiQueues = new ArrayList<>();
        passQueues.add(passNum1);
        passQueues.add(passNum2);
        passQueues.add(passNum3);
        passQueues.add(passNum4);
        taxiQueues.add(taxiNum1);
        taxiQueues.add(taxiNum2);
        taxiQueues.add(taxiNum3);
        taxiQueues.add(taxiNum4);
        this.ridesNum=ridesNum;
        this.waitingNum=waitingNum;
        this.timeLabel = timeLabel;
        this.waitingMean = waitingMean;
    }

    private void log(String message )
    {
        System.out.println( "StatisticFederate   : " + message );
    }

    private void logwithTime( String message ){
        System.out.println( "czas ["+getSimTime()+"] StatisticFederate   : " + message );
    }

    private void waitForUser(){
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try
        {
            reader.readLine();
        }
        catch( Exception e )
        {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }

    private void enableTimePolicy() throws Exception{
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        HLAfloat64Interval lookahead = timeFactory.makeInterval( fedamb.federateLookahead );

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation( lookahead );

        // tick until we get the callback
        while( fedamb.isRegulating == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while( fedamb.isConstrained == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
    }

    private void subscribeToExecuteRideInteraction() throws RTIexception {
        executeRideHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        rtiamb.subscribeInteractionClass(executeRideHandle);
        executeRide_destinationId = rtiamb.getParameterHandle(executeRideHandle, "destinationId");
        executeRide_passengerId = rtiamb.getParameterHandle(executeRideHandle, "passengerId");
        executeRide_taxiId = rtiamb.getParameterHandle(executeRideHandle, "taxiId");
        executeRide_rideTime = rtiamb.getParameterHandle(executeRideHandle, "rideTime");
    }

    private void subscribeToAreaObject() throws RTIexception {
        areaHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Areas");
        areaHandle_areaId = rtiamb.getAttributeHandle(areaHandle, "areaId");
        areaHandle_queueLength = rtiamb.getAttributeHandle(areaHandle, "queueLength");
        areaHandle_taxiQueueLength = rtiamb.getAttributeHandle(areaHandle, "taxiQueueLength");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(areaHandle_areaId);
        attributes.add(areaHandle_queueLength);
        attributes.add(areaHandle_taxiQueueLength);
        rtiamb.subscribeObjectClassAttributes(areaHandle, attributes);
    }

    private void publishAndSubscribe() throws RTIexception{
        subscribeToAreaObject();
        subscribeToExecuteRideInteraction();
    }

    private void advanceTime( double timestep ) throws RTIexception{
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( time );

        // wait for the time advance to be granted. ticking will tell the
        // LRC to start delivering callbacks to the federate
        while( fedamb.isAdvancing )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
    }

    public void updateQueueValue(int areaId, int queueLength, int taxiQueueLength) throws RTIexception {
        Optional o = areaLengths.stream().filter(x -> x.getVal1() == areaId).findFirst();
        if (o.isPresent()){
            ((Triple<Integer, MonitoredVar,Integer>)o.get()).getVal2().setValue(queueLength, getSimTime());
        }
        else{
            MonitoredVar v = new MonitoredVar();
            v.setValue(queueLength, getSimTime());
            areaLengths.add(new Triple<>(areaId, v, taxiQueueLength));
        }
        Platform.runLater(
                () ->{
                    passQueues.get(areaId).setText(queueLength+"");
                    taxiQueues.get(areaId).setText(taxiQueueLength+"");
                    int overall=0;
                    for(Triple<Integer, MonitoredVar, Integer> t : areaLengths){
                        overall+=(int)(t.getVal2().getValue());
                    }
                    waitingNum.setText(overall+"");
                }
        );
    }
    public void handleInteractionExecuteRide(){
        rideCounter++;
        Platform.runLater(
                () ->{
                    ridesNum.setText(rideCounter+"");
                }
        );
    }

    public void runFederate( String federateName ) throws Exception{
        log( "Creating RTIambassador" );
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        log( "Connecting..." );
        fedamb = new StatisticFederateAmbassador( this );
        rtiamb.connect( fedamb, CallbackModel.HLA_EVOKED );


        log( "Creating Federation..." );
        try
        {
            URL[] modules = new URL[]{
                    (new File("foms/TaxiSim.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution( "TaxiSimulation", modules );
            log( "Created Federation" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Didn't create federation, it already existed" );
        }
        catch( MalformedURLException urle )
        {
            log( "Exception loading one of the FOM modules from disk: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        rtiamb.joinFederationExecution( federateName,            // name for the federate
                "Statistic",   // federate type
                "TaxiSimulation"     // name of federation
        );           // modules we want to add

        log( "Joined Federation as " + federateName );

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();
        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );
        // wait until the point is announced
        while( fedamb.isAnnounced == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        enableTimePolicy();
        log( "Time Policy Enabled" );

        publishAndSubscribe();
        log( "Published and Subscribed" );

        areaLengths = new ArrayList<>();
        while( fedamb.isRunning && getSimTime() < SimPar.simEnd)
        {
            int overall=0;
            for(Triple<Integer, MonitoredVar, Integer> t : areaLengths){
                overall+=(int)(t.getVal2().getValue());
            }
            sumOfWaiting+=overall;
            Platform.runLater(
                    () ->{
                        waitingMean.setText("" + (sumOfWaiting/getSimTime()));
                        timeLabel.setText(getSimTime()+"");
                    }
            );
            advanceTime(1);
//            logwithTime( "Time Advanced to " + fedamb.federateTime );
        }
        try
        {
            rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
            log( "Resigned from Federation" );
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
        try
        {
            rtiamb.destroyFederationExecution( "ExampleFederation" );
            log( "Destroyed Federation" );
        }
        catch( FederationExecutionDoesNotExist dne )
        {
            log( "No need to destroy federation, it doesn't exist" );
        }
        catch( FederatesCurrentlyJoined fcj )
        {
            log( "Didn't destroy federation, federates still joined" );
        }
        Diagram d = new Diagram(Diagram.DiagramType.TIME, "Długości kolejek pasażerów w czasie");
        Color[] lista = {Color.GREEN, Color.BLUE, Color.RED, Color.MAGENTA};
        for(Triple<Integer, MonitoredVar, Integer> area : areaLengths){
            d.add(area.getVal2(), lista[area.getVal1()%4], "Długość kolejki pasażerów obszaru " + area.getVal1());
        }
        d.show();
    }

    protected double getSimTime() {
        return fedamb.federateTime;
    }

    private byte[] generateTag(){
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }

    @Override
    public void run(){
        try {
            runFederate( "Statistics" );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public static void main( String[] args )
//    {
//        // get a federate name, use "exampleFederate" as default
//        String federateName = "Statistics";
//        if( args.length != 0 )
//        {
//            federateName = args[0];
//        }
//
//        try
//        {
//            // run the example federate
//            new StatisticFederate().runFederate( federateName );
//        }
//        catch( Exception rtie )
//        {
//            // an exception occurred, just log the information and exit
//            rtie.printStackTrace();
//        }
//    }
}
