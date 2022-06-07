package Passengers;

import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;

public class PassengerAmbassador extends NullFederateAmbassador {

    private PassengerFederate federate;

    // these variables are accessible in the package
    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean isRunning       = true;

    public PassengerAmbassador(PassengerFederate federate) {this.federate = federate;}
    private void log( String message )
    {
        System.out.println( "FederateAmbassador: " + message );
    }

//    @Override
//    public void receiveInteraction( InteractionClassHandle interactionClass,
//                                    ParameterHandleValueMap theParameters,
//                                    byte[] tag,
//                                    OrderType sentOrdering,
//                                    TransportationTypeHandle theTransport,
//                                    LogicalTime time,
//                                    OrderType receivedOrdering,
//                                    SupplementalReceiveInfo receiveInfo )
//            throws FederateInternalError
//    {
//        StringBuilder builder = new StringBuilder( "Interaction Received:" );
//
//        // print the handle
//        builder.append( " handle=" + interactionClass );
//        if( interactionClass.equals(federate.addProductsHandle) )
//        {
//            builder.append( " (addProductsHandle)" );
//        }
//
//        // print the tag
//        builder.append( ", tag=" + new String(tag) );
//        // print the time (if we have it) we'll get null if we are just receiving
//        // a forwarded call from the other reflect callback above
//        if( time != null )
//        {
//            builder.append( ", time=" + ((HLAfloat64Time)time).getValue() );
//        }
//
//        // print the parameer information
//        builder.append( ", parameterCount=" + theParameters.size() );
//        builder.append( "\n" );
//        for( ParameterHandle parameter : theParameters.keySet() )
//        {
//            // print the parameter handle
//            builder.append( "\tparamHandle=" );
//            builder.append( parameter );
//            // print the parameter value
//            builder.append( ", paramValue=" );
//            builder.append( theParameters.get(parameter).length );
//            builder.append( " bytes" );
//            builder.append( "\n" );
//        }
//
//        log( builder.toString() );
//    }

    @Override
    public void synchronizationPointRegistrationFailed( String label,
                                                        SynchronizationPointFailureReason reason )
    {
        log( "Failed to register sync point: " + label + ", reason="+reason );
    }

    @Override
    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    @Override
    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(PassengerFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(PassengerFederate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    @Override
    public void timeRegulationEnabled( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isAdvancing = false;
    }

    @Override
    public void discoverObjectInstance( ObjectInstanceHandle theObject,
                                        ObjectClassHandle theObjectClass,
                                        String objectName )
            throws FederateInternalError
    {
        log( "Discoverd Object: handle=" + theObject + ", classHandle=" +
                theObjectClass + ", name=" + objectName );
    }

    @Override
    public void reflectAttributeValues( ObjectInstanceHandle theObject,
                                        AttributeHandleValueMap theAttributes,
                                        byte[] tag,
                                        OrderType sentOrder,
                                        TransportationTypeHandle transport,
                                        SupplementalReflectInfo reflectInfo )
            throws FederateInternalError
    {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        reflectAttributeValues( theObject,
                theAttributes,
                tag,
                sentOrder,
                transport,
                null,
                sentOrder,
                reflectInfo );
    }

    @Override
    public void receiveInteraction( InteractionClassHandle interactionClass,
                                    ParameterHandleValueMap theParameters,
                                    byte[] tag,
                                    OrderType sentOrdering,
                                    TransportationTypeHandle theTransport,
                                    SupplementalReceiveInfo receiveInfo )
            throws FederateInternalError
    {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        this.receiveInteraction( interactionClass,
                theParameters,
                tag,
                sentOrdering,
                theTransport,
                null,
                sentOrdering,
                receiveInfo );
    }

    @Override
    public void removeObjectInstance( ObjectInstanceHandle theObject,
                                      byte[] tag,
                                      OrderType sentOrdering,
                                      SupplementalRemoveInfo removeInfo )
            throws FederateInternalError
    {
        log( "Object Removed: handle=" + theObject );
    }
}
