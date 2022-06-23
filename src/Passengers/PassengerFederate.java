package Passengers;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;
import util.SimPar;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PassengerFederate {
    public static final String READY_TO_RUN = "ReadyToRun";

    double nextPassengerTime = -1;
    Random random = new Random();
    List<Passenger> passengersList;

    private RTIambassador rtiamb;
    private PassengerFederateAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    //handle types
    protected InteractionClassHandle executeRideHandle;
    protected ParameterHandle executeRide_destinationId;
    protected ParameterHandle executeRide_passengerId;
    protected ParameterHandle executeRide_taxiId;

    protected InteractionClassHandle publishNumOfAreasHandle;
    protected ParameterHandle publishNumOfAreas_numOfAreas;

    private int numOfAreas = 4;

    protected ObjectClassHandle passengerHandler;
    protected AttributeHandle passengerHandler_originId;
    protected AttributeHandle passengerHandler_destinationId;
    protected AttributeHandle passengerHandler_passengerId;
    protected ObjectInstanceHandle passengerInstanceHandle;


    private void log( String message )
    {
        System.out.println( "PassengerFederate   : " + message );
    }

    private void logwithTime( String message )
    {
        System.out.println( "czas ["+getSimTime()+"] PassengerFederate   : " + message );
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

    private void runFederate(String federateName) throws Exception {
        /////////////////////////////////////////////////
        // 1 & 2. create the RTIambassador and Connect //
        /////////////////////////////////////////////////
        log( "Creating RTIambassador" );
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log( "Connecting..." );
        fedamb = new PassengerFederateAmbassador( this );
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
                "passenger",   // federate type
                "TaxiSimulation"     // name of federation
        );           // modules we want to add

        log( "Joined Federation as " + federateName );
        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();

        ////////////////////////////////
        // 5. announce the sync point //
        ////////////////////////////////
        // announce a sync point to get everyone on the same page. if the point
        // has already been registered, we'll get a callback saying it failed,
        // but we don't care about that, as long as someone registered it
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

        waitForUser();
        simulationLoop();
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
        // NOTE: we won't die if we can't do this because other federates
        //       remain. in that case we'll leave it for them to clean up
        try
        {
            rtiamb.destroyFederationExecution( "TaxiSimulation" );
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

    public void simulationLoop() throws RTIexception {
        while (fedamb.isRunning && getSimTime() < SimPar.simEnd){
            //dołączać do kolejki pasażerów
            handlePassengerSpawn();
        }
    }

    private void publishAndSubscribe() throws RTIexception
    {
        //subscribe to to executeRide
        subscribeToExecuteRideInteraction();
        //subscribe to PublishNumOfareas() z federata Area
        subscribeToPublishNumOfAreasInteraction();
        // do the publication of passenger object
        publishPassengerObject();

    }

    private void publishPassengerObject() throws NameNotFound, FederateNotExecutionMember, NotConnected, RTIinternalError, InvalidObjectClassHandle, AttributeNotDefined, ObjectClassNotDefined, SaveInProgress, RestoreInProgress, ObjectClassNotPublished {
        passengerHandler = rtiamb.getObjectClassHandle("HLAobjectRoot.Passengers");
        passengerHandler_passengerId = rtiamb.getAttributeHandle(passengerHandler, "passengerId");
        passengerHandler_originId = rtiamb.getAttributeHandle(passengerHandler, "originId");
        passengerHandler_destinationId = rtiamb.getAttributeHandle(passengerHandler, "directionId");

        AttributeHandleSet attributesToPublic = rtiamb.getAttributeHandleSetFactory().create();
        attributesToPublic.add(passengerHandler_originId);
        attributesToPublic.add(passengerHandler_passengerId);
        attributesToPublic.add(passengerHandler_destinationId);

        rtiamb.publishObjectClassAttributes(passengerHandler, attributesToPublic);
        passengerInstanceHandle = rtiamb.registerObjectInstance(passengerHandler);
    }

    private void subscribeToExecuteRideInteraction() throws RTIexception {
        executeRideHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        rtiamb.subscribeInteractionClass(executeRideHandle);
        executeRide_destinationId = rtiamb.getParameterHandle(executeRideHandle, "destinationId");
        executeRide_passengerId = rtiamb.getParameterHandle(executeRideHandle, "passengerId");
        executeRide_taxiId = rtiamb.getParameterHandle(executeRideHandle, "taxiId");
    }

    private void subscribeToPublishNumOfAreasInteraction() throws RTIexception {
        publishNumOfAreasHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.publishNumOfAreas");
        rtiamb.subscribeInteractionClass(publishNumOfAreasHandle);
        publishNumOfAreas_numOfAreas = rtiamb.getParameterHandle(publishNumOfAreasHandle, "numOfAreas");
    }


    private void handlePassengerSpawn() throws RTIexception {
        if(getSimTime() >= nextPassengerTime) {
            int numberOfPassengersToSpawn = random.nextInt(20) + 1;
            passengersList = new ArrayList<>();
            for (int i = 0; i < numberOfPassengersToSpawn; i++){
                int originId = random.nextInt(numOfAreas);
                int destinationId = random.nextInt(numOfAreas);
                while (originId == destinationId){
                    destinationId = random.nextInt(numOfAreas);
                }
                Passenger newPassenger = new Passenger(originId, destinationId, this);
                passengersList.add(newPassenger);
                logwithTime("W strefie ("+originId+") pojawił się klient chcący pojechać do strefy ("+destinationId+")");
            }
            nextPassengerTime = getSimTime() + random.nextInt(20) + 10;
        }
        advanceTime(1);
//        logwithTime( "Time Advanced to " + fedamb.federateTime );
    }

    public void handleInteractionExecuteRide(ParameterHandleValueMap theParameters) throws DecoderException {
        HLAinteger32BE buffer = new HLA1516eInteger32BE();
        int passengerId, destinationId;
        buffer.decode(theParameters.get(executeRide_passengerId));
        passengerId = buffer.getValue();
        buffer.decode(theParameters.get(executeRide_destinationId));
        destinationId = buffer.getValue();
        passengersList.removeIf(x -> x.passengerId==passengerId);
        logwithTime("Pasażer #"+passengerId+" zakończył przejazd w obszarze #" + destinationId );
    }

    public void setNumOfAreas(int numOfAreas){
        this.numOfAreas = numOfAreas;
    }

    /**
     * This method will update all the values of the given object instance. It will
     * set the flavour of the soda to a random value from the options specified in
     * the FOM (Cola - 101, Orange - 102, RootBeer - 103, Cream - 104) and it will set
     * the number of cups to the same value as the current time.
     * <p/>
     * Note that we don't actually have to update all the attributes at once, we
     * could update them individually, in groups or not at all!
     */
    public void updateInstanceValues( int originId, int destinationId, int passengerId ) throws RTIexception
    {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);

        HLAinteger32BE _originId = encoderFactory.createHLAinteger32BE(originId);
        HLAinteger32BE _destinationId = encoderFactory.createHLAinteger32BE(destinationId);
        HLAinteger32BE _passengerId = encoderFactory.createHLAinteger32BE(passengerId);
        attributes.put( passengerHandler_originId, _originId.toByteArray() );
        attributes.put( passengerHandler_destinationId, _destinationId.toByteArray() );;
        attributes.put( passengerHandler_passengerId, _passengerId.toByteArray() );

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.updateAttributeValues( passengerInstanceHandle, attributes, generateTag(), time );
    }

    protected double getSimTime() {
        return fedamb.federateTime;
    }

    private void enableTimePolicy() throws Exception
    {
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

    private void advanceTime( double timestep ) throws RTIexception
    {
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

    private byte[] generateTag()
    {
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }


    public static void main( String[] args )
    {
        // get a federate name, use "exampleFederate" as default
        String federateName = "Passenger";
        if( args.length != 0 )
        {
            federateName = args[0];
        }

        try
        {
            // run the example federate
            new PassengerFederate().runFederate( federateName );
        }
        catch( Exception rtie )
        {
            // an exception occurred, just log the information and exit
            rtie.printStackTrace();
        }
    }

}
