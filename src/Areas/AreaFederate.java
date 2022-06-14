package Areas;

import Passengers.Passenger;
import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import org.jgroups.util.Triple;
import org.jgroups.util.Tuple;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;

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
            {0.0, 15.12, 27.67, 43.12},
            {15.12, 0.0, 23.99, 32.11},
            {27.67, 23.99, 0.0, 60.22},
            {43.12, 32.11, 60.22, 0.0}
    };

    private List<Tuple<Integer, Integer>> taxisList; //id, currentAreaId
    private List<Triple<Integer, Integer, Integer>> passengersList; //id, originID, destinationID

    public ObjectInstanceHandle taxiInstanceHandle;
    public ObjectInstanceHandle passengerInstanceHandle;

    protected ObjectClassHandle passengerHandle;
    protected AttributeHandle passengerHandle_originId;
    protected AttributeHandle passengerHandle_directionId;
    protected AttributeHandle passengerHandle_passengerId;

    protected ObjectClassHandle taxiHandle;
    protected AttributeHandle taxiHandle_areaId;
    protected AttributeHandle taxiHandle_taxiId;


    protected InteractionClassHandle executeRide;
    protected InteractionClassHandle publishNumOfAreas;


    private void log( String message )
    {
        System.out.println( "AreaFederate   : " + message );
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

    private void publishAndSubscribe() throws RTIexception
    {
        //public own interactions:
        executeRide = rtiamb.getInteractionClassHandle("HLAinteractionRoot.executeRide");
        publishNumOfAreas = rtiamb.getInteractionClassHandle("HLAinteractionRoot.publishNumOfAreas");
        rtiamb.publishInteractionClass(executeRide);
        rtiamb.publishInteractionClass(publishNumOfAreas);

        //sub for joinTaxiqueue:
//        subscribeToJoinTaxiQueueInteraction();
        //sub for joinPassengerQueue:
        // subscribeToJoinPassengerQueueInteraction();

        //sub for passenger and taxi objects
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

    protected double getSimTime() {
        return fedamb.federateTime;
    }

    public void updatePassengerValues(int passengerId, int originId, int destinationId) throws RTIexception {
        boolean wasRemoved = passengersList.removeIf(x -> x.getVal1() == passengerId);
        passengersList.add(new Triple<>(passengerId, originId, destinationId));
        areasList.get(originId).addPassengerToQueue(passengerId);
        log(wasRemoved
                ?
                "Zaktualizowano dane pasażera nr " + passengerId
                :
                "Pasażer nr " + passengerId + " dołączył do kolejki w obszarze " + originId + ", chce dojechac do obszaru " + destinationId);
    }

    public void updateTaxiValues(int taxiId, int areaId) throws RTIexception {
        boolean wasRemoved = taxisList.removeIf(x -> x.getVal1() == taxiId);
        taxisList.add(new Tuple<>(taxiId, areaId));
        areasList.get(areaId).addTaxiToQueue(taxiId);
        log(wasRemoved
                ?
                "Zaktualizowano dane taxi nr " + taxiId
                :
                "Taxi nr " + taxiId + " dołączyło do kolejki w obszarze " + areaId);
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

        //////////////////////////////
        // 3. create the federation //
        //////////////////////////////
        log( "Creating Federation..." );
        // We attempt to create a new federation with the first three of the
        // restaurant FOM modules covering processes, food and drink
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
        // announce a sync point to get everyone on the same page. if the point
        // has already been registered, we'll get a callback saying it failed,
        // but we don't care about that, as long as someone registered it
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
        // tell the RTI we are ready to move past the sync point and then wait
        // until the federation has synchronized on
        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        /////////////////////////////
        // 7. enable time policies //
        /////////////////////////////
        // in this section we enable/disable all time policies
        // note that this step is optional!
        enableTimePolicy();
        log( "Time Policy Enabled" );

        //////////////////////////////
        // 8. publish and subscribe //
        //////////////////////////////
        // in this section we tell the RTI of all the data we are going to
        // produce, and all the data we want to know about
        publishAndSubscribe();
        log( "Published and Subscribed" );

        /////////////////////////////////////
        // 9. register an object to update //
        /////////////////////////////////////
        // ObjectInstanceHandle objectHandle = rtiamb.registerObjectInstance( areaHandle );
        // log( "Registered Area, handle=" + objectHandle );

        /////////////////////////////////////
        // 10. do the main simulation loop //
        /////////////////////////////////////
        // here is where we do the meat of our work. in each iteration, we will
        // update the attribute values of the object we registered, and will
        // send an interaction.


//        TO DOOOOOOOOOOOOO
        simulationLoop();

        //////////////////////////////////////
        // 11. delete the object we created //
        //////////////////////////////////////
//		deleteObject( objectHandle );
//		log( "Deleted Object, handle=" + objectHandle );

        ////////////////////////////////////
        // 12. resign from the federation //
        ////////////////////////////////////
        rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
        log( "Resigned from Federation" );

        ////////////////////////////////////////
        // 13. try and destroy the federation //
        ////////////////////////////////////////
        // NOTE: we won't die if we can't do this because other federates
        //       remain. in that case we'll leave it for them to clean up
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
        while( fedamb.isRunning )
        {

            for (Area area : areasList) {
                if (area.passengerQueue.size() > 0) {
                    int passengerToRide = area.passengerQueue.pop();
                    if (area.taxiQueue.size() > 0) {
                        //jest taxuwa i jest pasazer
                        int passengerDestinationId = passengersList.stream().filter(x -> x.getVal1() == passengerToRide).findFirst().get().getVal3();
                        int taxiToGetPassenger = area.taxiQueue.pop();

                        handleExecuteRideInteraction(area.areaId, passengerToRide, passengerDestinationId, taxiToGetPassenger);
                    }
                    log("czas ["+getSimTime()+"] W strefie ("+area.areaId+") pasażer o id ("+passengerToRide+")"+" oczekuje na przyjazd taksówki");

                }
//                log("czas ["+getSimTime()+"] W strefie ("+area.areaId+") nie ma ani pasażerów ani taksówek.");
            }
            advanceTime(1);
//            log( "Time Advanced to " + fedamb.federateTime );
        }
    }

    private void handlePublishNumbOfAreas(int numbOfAreas) throws FederateNotExecutionMember, NotConnected, NameNotFound, InvalidInteractionClassHandle, RTIinternalError, InvalidLogicalTime, InteractionClassNotPublished, InteractionParameterNotDefined, InteractionClassNotDefined, SaveInProgress, RestoreInProgress {
        ParameterHandleValueMap params = rtiamb.getParameterHandleValueMapFactory().create(1);
        params.put(
                rtiamb.getParameterHandle(publishNumOfAreas, "numOfAreas"),
                encoderFactory.createHLAinteger32BE(numbOfAreas).toByteArray()
        );
        HLAfloat64Time ttime = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead);
        rtiamb.sendInteraction(publishNumOfAreas, params, generateTag(), ttime);
    }


    private void handleExecuteRideInteraction(int areaId, int passengerToRide, int passengerDestinationId, int taxiToGetPassenger) throws FederateNotExecutionMember, NotConnected, NameNotFound, InvalidInteractionClassHandle, RTIinternalError, InvalidLogicalTime, InteractionClassNotPublished, InteractionParameterNotDefined, InteractionClassNotDefined, SaveInProgress, RestoreInProgress {
        ParameterHandleValueMap params = rtiamb.getParameterHandleValueMapFactory().create(3);
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
        log("czas ["+getSimTime()+"] W strefie ("+ areaId +") pasażer o id ("+ passengerToRide +")"+" wsiadł do taksówki ["+ taxiToGetPassenger +"] i pojechał do strefy + ["+ passengerDestinationId +"]");

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead);
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






















