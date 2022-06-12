package Statistic;


import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class StatisticFederate{
    public static final String READY_TO_RUN = "ReadyToRun";


    private RTIambassador rtiamb;
    private StatisticFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    //subs for interactions:
    protected InteractionClassHandle executeRideHandle;
    protected ParameterHandle executeRide_time;
    protected ParameterHandle executeRide_destinationId;

    protected InteractionClassHandle joinTaxiQueueHandle;
    protected ParameterHandle joinTaxiQueue_taxiId;
    protected ParameterHandle joinTaxiQueue_areaId;

    protected InteractionClassHandle joinPassengerQueueHandle;
    protected ParameterHandle joinPassengerQueue_passengerId;
    protected ParameterHandle joinPassengerQueue_areaId;

    protected InteractionClassHandle publishNumOfAreasHandle;
    protected ParameterHandle publishNumOfAreas_numOfAreas;


    private void log( String message )
    {
        System.out.println( "ConsumerFederate   : " + message );
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

    private void subscribeToExecuteRideInteraction() throws RTIexception {
        executeRideHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        rtiamb.subscribeInteractionClass(executeRideHandle);
        executeRide_time = rtiamb.getParameterHandle(executeRideHandle, "time");
        executeRide_destinationId = rtiamb.getParameterHandle(executeRideHandle, "destinationId");
    }
    private void subscribeToJoinTaxiQueueInteraction() throws RTIexception {
        joinTaxiQueueHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.joinTaxiQueue");
        rtiamb.subscribeInteractionClass(joinTaxiQueueHandle);
        joinTaxiQueue_taxiId = rtiamb.getParameterHandle(joinTaxiQueueHandle, "taxiId");
        joinTaxiQueue_areaId = rtiamb.getParameterHandle(joinTaxiQueueHandle, "areaId");
    }
    private void subscribeToJoinPassengerQueueInteraction() throws RTIexception {
        joinPassengerQueueHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.joinPassengerQueue");
        rtiamb.subscribeInteractionClass(joinPassengerQueueHandle);
        joinPassengerQueue_passengerId = rtiamb.getParameterHandle(joinPassengerQueueHandle, "passengerId");
        joinPassengerQueue_areaId = rtiamb.getParameterHandle(joinPassengerQueueHandle, "areaId");
    }
    private void subscribeToPublisNumOfAreasInteraction() throws RTIexception {
        publishNumOfAreasHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.publishNumOfAreas");
        rtiamb.subscribeInteractionClass(publishNumOfAreasHandle);
        publishNumOfAreas_numOfAreas = rtiamb.getParameterHandle(publishNumOfAreasHandle, "numOfAreas");
    }

    private void publishAndSubscribe() throws RTIexception
    {
    subscribeToExecuteRideInteraction();
    subscribeToJoinTaxiQueueInteraction();
    subscribeToPublisNumOfAreasInteraction();
    subscribeToJoinPassengerQueueInteraction();
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

    //todooo
    public void runFederate( String federateName ) throws Exception
    {
//        /////////////////////////////////////////////////
//        // 1 & 2. create the RTIambassador and Connect //
//        /////////////////////////////////////////////////
//        log( "Creating RTIambassador" );
//        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
//        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
//
//        // connect
//        log( "Connecting..." );
//        fedamb = new ProducerFederateAmbassador( this );
//        rtiamb.connect( fedamb, CallbackModel.HLA_EVOKED );
//
//        //////////////////////////////
//        // 3. create the federation //
//        //////////////////////////////
//        log( "Creating Federation..." );
//        // We attempt to create a new federation with the first three of the
//        // restaurant FOM modules covering processes, food and drink
//        try
//        {
//            URL[] modules = new URL[]{
//                    (new File("foms/ProducerConsumer.xml")).toURI().toURL(),
//            };
//
//            rtiamb.createFederationExecution( "ProducerConsumerFederation", modules );
//            log( "Created Federation" );
//        }
//        catch( FederationExecutionAlreadyExists exists )
//        {
//            log( "Didn't create federation, it already existed" );
//        }
//        catch( MalformedURLException urle )
//        {
//            log( "Exception loading one of the FOM modules from disk: " + urle.getMessage() );
//            urle.printStackTrace();
//            return;
//        }
//
//        ////////////////////////////
//        // 4. join the federation //
//        ////////////////////////////
//
//        rtiamb.joinFederationExecution( federateName,            // name for the federate
//                "producer",   // federate type
//                "ProducerConsumerFederation"     // name of federation
//        );           // modules we want to add
//
//        log( "Joined Federation as " + federateName );
//
//        // cache the time factory for easy access
//        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();
//
//        ////////////////////////////////
//        // 5. announce the sync point //
//        ////////////////////////////////
//        // announce a sync point to get everyone on the same page. if the point
//        // has already been registered, we'll get a callback saying it failed,
//        // but we don't care about that, as long as someone registered it
//        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );
//        // wait until the point is announced
//        while( fedamb.isAnnounced == false )
//        {
//            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
//        }
//
//        // WAIT FOR USER TO KICK US OFF
//        // So that there is time to add other federates, we will wait until the
//        // user hits enter before proceeding. That was, you have time to start
//        // other federates.
//        waitForUser();
//
//        ///////////////////////////////////////////////////////
//        // 6. achieve the point and wait for synchronization //
//        ///////////////////////////////////////////////////////
//        // tell the RTI we are ready to move past the sync point and then wait
//        // until the federation has synchronized on
//        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
//        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );
//        while( fedamb.isReadyToRun == false )
//        {
//            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
//        }
//
//        /////////////////////////////
//        // 7. enable time policies //
//        /////////////////////////////
//        // in this section we enable/disable all time policies
//        // note that this step is optional!
//        enableTimePolicy();
//        log( "Time Policy Enabled" );
//
//        //////////////////////////////
//        // 8. publish and subscribe //
//        //////////////////////////////
//        // in this section we tell the RTI of all the data we are going to
//        // produce, and all the data we want to know about
//        publishAndSubscribe();
//        log( "Published and Subscribed" );
//
////		// 10. do the main simulation loop //
//        /////////////////////////////////////
//        // here is where we do the meat of our work. in each iteration, we will
//        // update the attribute values of the object we registered, and will
//        // send an interaction.
//        Producer producer = new Producer();
//        while( fedamb.isRunning )
//        {
//            int producedValue = producer.produce();
//            if(storageAvailable + producedValue <= storageMax ) {
//                ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
//                ParameterHandle addProductsCountHandle = rtiamb.getParameterHandle(addProductsHandle, "count");
//                HLAinteger32BE count = encoderFactory.createHLAinteger32BE(producedValue);
//                parameterHandleValueMap.put(addProductsCountHandle, count.toByteArray());
//                rtiamb.sendInteraction(addProductsHandle, parameterHandleValueMap, generateTag());
//            }
//            else
//            {
//                log("Producing canceled because of full storage.");
//            }
//            // 9.3 request a time advance and wait until we get it
//            advanceTime(producer.getTimeToNext());
//            log( "Time Advanced to " + fedamb.federateTime );
//        }
//
//
//        ////////////////////////////////////
//        // 12. resign from the federation //
//        ////////////////////////////////////
//        rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
//        log( "Resigned from Federation" );
//
//        ////////////////////////////////////////
//        // 13. try and destroy the federation //
//        ////////////////////////////////////////
//        // NOTE: we won't die if we can't do this because other federates
//        //       remain. in that case we'll leave it for them to clean up
//        try
//        {
//            rtiamb.destroyFederationExecution( "ExampleFederation" );
//            log( "Destroyed Federation" );
//        }
//        catch( FederationExecutionDoesNotExist dne )
//        {
//            log( "No need to destroy federation, it doesn't exist" );
//        }
//        catch( FederatesCurrentlyJoined fcj )
//        {
//            log( "Didn't destroy federation, federates still joined" );
//        }
    }

    private byte[] generateTag()
    {
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }

    public static void main( String[] args )
    {
        // get a federate name, use "exampleFederate" as default
        String federateName = "Statistics";
        if( args.length != 0 )
        {
            federateName = args[0];
        }

        try
        {
            // run the example federate
            new StatisticFederate().runFederate( federateName );
        }
        catch( Exception rtie )
        {
            // an exception occurred, just log the information and exit
            rtie.printStackTrace();
        }
    }
}
