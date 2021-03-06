package Areas;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.jgroups.util.Triple;
import org.jgroups.util.Tuple;
import util.SimPar;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AreaFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    private RTIambassador rtiamb;
    private AreaFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    private List<Area> areasList;

    double[][] rideTimes = {
            {0.0, 20.0, 25.0, 30.0},
            {20.0, 0.0, 20.0, 25.0},
            {25.0, 20.0, 0.0, 20.0},
            {30.0, 25.0, 20.0, 0.0}
    };

    private List<Tuple<Integer, Integer>> taxisList; //id, currentAreaId
    private List<Triple<Integer, Integer, Integer>> passengersList; //id, originID, destinationID


    protected ObjectClassHandle passengerHandle;
    protected AttributeHandle passengerHandle_originId;
    protected AttributeHandle passengerHandle_directionId;
    protected AttributeHandle passengerHandle_passengerId;
    public ObjectInstanceHandle passengerInstanceHandle;

    protected ObjectClassHandle taxiHandle;
    protected AttributeHandle taxiHandle_areaId;
    protected AttributeHandle taxiHandle_taxiId;
    public ObjectInstanceHandle taxiInstanceHandle;

    protected ObjectClassHandle areaHandle;
    protected AttributeHandle areaHandle_areaId;
    protected AttributeHandle areaHandle_queueLength;
    protected AttributeHandle areaHandle_taxiQueueLength;
    public ObjectInstanceHandle areaInstanceHandle;



    protected InteractionClassHandle executeRide;
    protected InteractionClassHandle publishNumOfAreas;


    private void log( String message )
    {
        System.out.println( "AreaFederate   : " + message );
    }

    private void logwithTime( String message )
    {
        System.out.println( "czas ["+getSimTime()+"] AreaFederate   : " + message );
    }

    private void waitForUser()
    {
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

    private void defineSimulationObjects() throws RTIexception {
        areasList = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            areasList.add(new Area(i));
        }
    }

    private void enableTimePolicy() throws Exception
    {
        HLAfloat64Interval lookahead = timeFactory.makeInterval( fedamb.federateLookahead );
        this.rtiamb.enableTimeRegulation( lookahead );
        while( fedamb.isRegulating == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
        this.rtiamb.enableTimeConstrained();
        while( fedamb.isConstrained == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
    }

    private void publishAndSubscribe() throws RTIexception
    {
        //public own interactions:
        executeRide = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        publishNumOfAreas = rtiamb.getInteractionClassHandle("HLAinteractionRoot.publishNumOfAreas");
        rtiamb.publishInteractionClass(executeRide);
        rtiamb.publishInteractionClass(publishNumOfAreas);
        publishAreaObject();
        subscribeToTaxiObject();
        subscribeToPassengerObject();
    }

    private void subscribeToPassengerObject() throws RTIexception {
        passengerHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Passengers");
        passengerHandle_passengerId = rtiamb.getAttributeHandle(passengerHandle, "passengerId");
        passengerHandle_originId = rtiamb.getAttributeHandle(passengerHandle, "originId");
        passengerHandle_directionId = rtiamb.getAttributeHandle(passengerHandle, "directionId");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(passengerHandle_passengerId);
        attributes.add(passengerHandle_originId);
        attributes.add(passengerHandle_directionId);
        rtiamb.subscribeObjectClassAttributes(passengerHandle, attributes);
    }

    private void subscribeToTaxiObject() throws RTIexception {
        taxiHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Taxis");
        taxiHandle_areaId = rtiamb.getAttributeHandle(taxiHandle, "areaId");
        taxiHandle_taxiId = rtiamb.getAttributeHandle(taxiHandle, "taxiId");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(taxiHandle_areaId);
        attributes.add(taxiHandle_taxiId);
        rtiamb.subscribeObjectClassAttributes(taxiHandle, attributes);
    }

    private void publishAreaObject() throws NameNotFound, FederateNotExecutionMember, NotConnected, RTIinternalError, InvalidObjectClassHandle, AttributeNotDefined, ObjectClassNotDefined, SaveInProgress, RestoreInProgress, ObjectClassNotPublished {
        areaHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Areas");
        areaHandle_areaId = rtiamb.getAttributeHandle(areaHandle, "areaId");
        areaHandle_queueLength = rtiamb.getAttributeHandle(areaHandle, "queueLength");
        areaHandle_taxiQueueLength = rtiamb.getAttributeHandle(areaHandle, "taxiQueueLength");

        AttributeHandleSet attributesToPublic = rtiamb.getAttributeHandleSetFactory().create();
        attributesToPublic.add(areaHandle_areaId);
        attributesToPublic.add(areaHandle_queueLength);
        attributesToPublic.add(areaHandle_taxiQueueLength);
        rtiamb.publishObjectClassAttributes(areaHandle, attributesToPublic);
        areaInstanceHandle = rtiamb.registerObjectInstance(areaHandle);
    }


    private void advanceTime( double timestep ) throws RTIexception
    {
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( time );

        while( fedamb.isAdvancing )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );

        }
    }

    protected double getSimTime() {
        return fedamb.federateTime;
    }

    public void updateInstanceValues( int areaId, int queueLength, int taxiQueueLength) throws RTIexception
    {
        logwithTime(" areaId: " + areaId +" ql: " + queueLength);
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);

        HLAinteger32BE _areaId = encoderFactory.createHLAinteger32BE(areaId);
        HLAinteger32BE _queueLength = encoderFactory.createHLAinteger32BE(queueLength);
        HLAinteger32BE _taxiQueueLength = encoderFactory.createHLAinteger32BE(taxiQueueLength);
        attributes.put( areaHandle_areaId, _areaId.toByteArray() );
        attributes.put( areaHandle_queueLength, _queueLength.toByteArray() );
        attributes.put( areaHandle_taxiQueueLength, _taxiQueueLength.toByteArray() );

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.updateAttributeValues( areaInstanceHandle, attributes, generateTag(), time );
    }

    public void updatePassengerValues(int passengerId, int originId, int destinationId) throws RTIexception {
        boolean wasRemoved = passengersList.removeIf(x -> x.getVal1() == passengerId);
        passengersList.add(new Triple<>(passengerId, originId, destinationId));
        areasList.get(originId).addPassengerToQueue(passengerId);
        logwithTime(wasRemoved
                ?
                "Zaktualizowano dane pasa??era nr " + passengerId
                :
                "Pasa??er nr " + passengerId + " do????czy?? do kolejki w obszarze " + originId + ", chce dojechac do obszaru " + destinationId);
    }

    public void updateTaxiValues(int taxiId, int areaId) throws RTIexception {
        boolean wasRemoved = taxisList.removeIf(x -> x.getVal1() == taxiId);
        taxisList.add(new Tuple<>(taxiId, areaId));
        areasList.get(areaId).addTaxiToQueue(taxiId);
        logwithTime("Taxi nr " + taxiId + " do????czy??o do kolejki w obszarze " + areaId);
    }

    private byte[] generateTag()
    {
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }

    public void runFederate( String federateName ) throws Exception
    {
        /////////////////////////////////////////////////
        // 1 & 2. create the RTIambassador and Connect //
        /////////////////////////////////////////////////
        log( "Creating RTIambassador" );
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log( "Connecting..." );
        fedamb = new AreaFederateAmbassador( this );
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

        ////////////////////////////
        // 4. join the federation //
        ////////////////////////////
        rtiamb.joinFederationExecution( federateName,            // name for the federate
                "Area",   // federate type
                "TaxiSimulation"     // name of federation
        );           // modules we want to add

        log( "Joined Federation as " + federateName );

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();

        ////////////////////////////////
        // 5. announce the sync point //
        ////////////////////////////////
        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );
        // wait until the point is announced
        while( fedamb.isAnnounced == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
        // WAIT FOR USER TO KICK US OFF
        // So that there is time to add other federates, we will wait until the
        // user hits enter before proceeding. That was, you have time to start
        // other federates.
        waitForUser();

        ///////////////////////////////////////////////////////
        // 6. achieve the point and wait for synchronization //
        ///////////////////////////////////////////////////////
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

        simulationLoop();
        ////////////////////////////////////
        // 12. resign from the federation //
        ////////////////////////////////////
        try{
            rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
            log( "Resigned from Federation" );
        }
        catch(Exception e){
            e.printStackTrace();
        }

        ////////////////////////////////////////
        // 13. try and destroy the federation //
        ////////////////////////////////////////
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
    }

    private void simulationLoop() throws RTIexception {
        defineSimulationObjects();
        //handle publishNumbOfAreas
        int numbOfAreas = areasList.size();
        handlePublishNumbOfAreas(numbOfAreas);
        passengersList = new ArrayList<>();
        taxisList = new ArrayList<>();
        while( fedamb.isRunning && getSimTime()< SimPar.simEnd)
        {
            for (Area area : areasList) {
                if (area.passengerQueue.size() > 0) {
                    if (area.taxiQueue.size() > 0) {
                        //jest taxuwa i jest pasazer
                        int passengerToRide = area.passengerQueue.pop();
                        int passengerDestinationId = passengersList.stream().filter(x -> x.getVal1() == passengerToRide).findFirst().get().getVal3();
                        int taxiToGetPassenger = area.taxiQueue.pop();

                        handleExecuteRideInteraction(area.areaId, passengerToRide, passengerDestinationId, taxiToGetPassenger);
                    }
                    else{
                        logwithTime(" W strefie ("+area.areaId+") pasa??er o id ("+area.passengerQueue.peek()+")"+" oczekuje na przyjazd taks??wki");
                    }

                }
//                logwithTime("czas ["+getSimTime()+"] W strefie ("+area.areaId+") nie ma ani pasa??er??w ani taks??wek.");
                updateInstanceValues(area.areaId, area.passengerQueue.size(), area.taxiQueue.size());
            }
            advanceTime(1);
//            logwithTime( "Time Advanced to " + fedamb.federateTime );
        }
    }

    private void handlePublishNumbOfAreas(int numbOfAreas) throws FederateNotExecutionMember, NotConnected, NameNotFound, InvalidInteractionClassHandle, RTIinternalError, InvalidLogicalTime, InteractionClassNotPublished, InteractionParameterNotDefined, InteractionClassNotDefined, SaveInProgress, RestoreInProgress {
        ParameterHandleValueMap params = rtiamb.getParameterHandleValueMapFactory().create(1);
        params.put(
                rtiamb.getParameterHandle(publishNumOfAreas, "numOfAreas"),
                encoderFactory.createHLAinteger32BE(numbOfAreas).toByteArray()
        );
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead);
        rtiamb.sendInteraction(publishNumOfAreas, params, generateTag(), time);
    }

    private double getRideTime(int areaId, int destId){
        return rideTimes[areaId][destId];
    }


    private void handleExecuteRideInteraction(int areaId, int passengerToRide, int passengerDestinationId, int taxiToGetPassenger) throws FederateNotExecutionMember, NotConnected, NameNotFound, InvalidInteractionClassHandle, RTIinternalError, InvalidLogicalTime, InteractionClassNotPublished, InteractionParameterNotDefined, InteractionClassNotDefined, SaveInProgress, RestoreInProgress {
        ParameterHandleValueMap params = rtiamb.getParameterHandleValueMapFactory().create(4);
        params.put(
                rtiamb.getParameterHandle(executeRide, "passengerId"),
                encoderFactory.createHLAinteger32BE(passengerToRide).toByteArray()
        );
        params.put(
                rtiamb.getParameterHandle(executeRide, "taxiId"),
                encoderFactory.createHLAinteger32BE(taxiToGetPassenger).toByteArray()
        );
        params.put(
                rtiamb.getParameterHandle(executeRide, "destinationId"),
                encoderFactory.createHLAinteger32BE(passengerDestinationId).toByteArray()
        );

        params.put(
                rtiamb.getParameterHandle(executeRide, "rideTime"),
                encoderFactory.createHLAfloat64BE(getRideTime(areaId, passengerDestinationId)).toByteArray()
        );
        logwithTime("W strefie ("+ areaId +") pasa??er o id ("+ passengerToRide +")"+" wsiad?? do taks??wki ["+ taxiToGetPassenger +"] i pojecha?? do strefy ["+ passengerDestinationId +"]");
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.sendInteraction(executeRide, params, generateTag(), time);
    }


    public static void main( String[] args )
    {
        // get a federate name, use "exampleFederate" as default
        String federateName = "Area";
        if( args.length != 0 )
        {
            federateName = args[0];
        }

        try
        {
            // run the example federate
            new AreaFederate().runFederate( federateName );
        }
        catch( Exception rtie )
        {
            // an exception occurred, just log the information and exit
            rtie.printStackTrace();
        }
    }
}






















