package Passengers;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class PassengerFederate {
    public static final String READY_TO_RUN = "ReadyToRun";

    double nextPassengerTime = -1;
    Random random = new Random();
    List<Passenger> passengersList;

    private RTIambassador rtiamb;
    private PassengerAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;
    //    public List<Integer> areaIds = new ArrayList<>(Arrays.asList({1, 2, 3, 4, 5}));

    //handle types

    protected InteractionClassHandle joinPassengerQueueHandle;
    protected ParameterHandle joinPassengerQueue_passengerId;

    protected InteractionClassHandle executeRideHandle;
    protected ParameterHandle executeRide_time;
    protected ParameterHandle executeRide_destinationId;

    protected InteractionClassHandle publishNumOfAreasHandle;
    protected ParameterHandle publishNumOfAreas_numOfAreas;

    protected ObjectClassHandle passengerHandler;
    protected AttributeHandle passengerHandler_originId;
    protected AttributeHandle passengerHandler_destinationId;
    protected AttributeHandle passengerHandler_passengerId;


    private void log( String message )
    {
        System.out.println( "PassengerFederate   : " + message );
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
        fedamb = new PassengerAmbassador( this );
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



        // TO DOOOOOOOOOO

//        while (fedamb.isRunning){
//            int consumed = consumer.consume();
//            if(storageAvailable - consumed >= 0 ) {
//                ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
//                ParameterHandle addProductsCountHandle = rtiamb.getParameterHandle(getProductsHandle, "count");
//                HLAinteger32BE count = encoderFactory.createHLAinteger32BE(consumed);
//                parameterHandleValueMap.put(addProductsCountHandle, count.toByteArray());
//                rtiamb.sendInteraction(getProductsHandle, parameterHandleValueMap, generateTag());
//            }
//            else
//            {
//                log("Consuming canceled because of lack of products.");
//            }
//            // 9.3 request a time advance and wait until we get it
//            advanceTime(consumer.getTimeToNext());
//            log( "Time Advanced to " + fedamb.federateTime );
//        }

    }

    public int getRandomWithExclusion(Random rnd, int start, int end, int... exclude) {
        int random = start + rnd.nextInt(end - start + 1 - exclude.length);
        for (int ex : exclude) {
            if (random < ex) {
                break;
            }
            random++;
        }
        return random;
    }

    private void publishAndSubscribe() throws RTIexception
    {
		//publish GetProducts interaction
        joinPassengerQueueHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.joinPassengerQueue");
        rtiamb.publishInteractionClass(joinPassengerQueueHandle);

        //subscribe to to executeRide
        subscribeToExecuteRideInteraction();
        //subscribe to PublishNumOfareas() z federata Area
        subscribeToPublisNumOfAreasInteraction();

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
        //passengerInstanceHandle = rtiamb.registerObjectInstance(passengerHandler);???????????
    }

    private void subscribeToPublisNumOfAreasInteraction() throws RTIexception {
        publishNumOfAreasHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.publishNumOfAreas");
        rtiamb.subscribeInteractionClass(publishNumOfAreasHandle);
        publishNumOfAreas_numOfAreas = rtiamb.getParameterHandle(publishNumOfAreasHandle, "numOfAreas");
    }

    private void subscribeToExecuteRideInteraction() throws RTIexception {
        executeRideHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        rtiamb.subscribeInteractionClass(executeRideHandle);
        executeRide_time = rtiamb.getParameterHandle(executeRideHandle, "time");
        executeRide_destinationId = rtiamb.getParameterHandle(executeRideHandle, "destinationId");
    }


    private void handlePassengerSpawn() throws RTIexception {
        if(getSimTime() >= nextPassengerTime) {
            int numberOfPassengersToSpawn = random.nextInt(3) + 1;

            for (int i = 0; i < numberOfPassengersToSpawn; i++){
                int originId = random.nextInt(5); //we have 4 areas
                int destinationId = random.nextInt(5); //we have 4 areas
                while (originId == destinationId){
                    destinationId = random.nextInt(5);
                }
                Passenger newPassenger = new Passenger(originId, destinationId, this);
                passengersList.add(newPassenger);
                log("czas ["+getSimTime()+"] W strefie ("+originId+") pojawił się klient chcący pojechać do strefy ("+destinationId+")");
            }
            nextPassengerTime = getSimTime() + random.nextInt(20) + 10;
        }
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
