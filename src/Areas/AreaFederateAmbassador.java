package Areas;

import hla.rti1516e.*;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;

public class AreaFederateAmbassador extends NullFederateAmbassador {

    private AreaFederate federate;
    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;
    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean isRunning       = true;

    public AreaFederateAmbassador(AreaFederate federate )
    {
        this.federate = federate;
    }
    private void log( String message )
    {
        System.out.println( "FederateAmbassador: " + message );
    }

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
        if( label.equals(AreaFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(AreaFederate.READY_TO_RUN) )
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
        if (theObjectClass.equals(federate.taxiHandle)){
            federate.taxiInstanceHandle = theObject;
        }
        if (theObjectClass.equals(federate.passengerHandle)){
            federate.passengerInstanceHandle = theObject;
        }
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

//    @Override
//    public void reflectAttributeValues( ObjectInstanceHandle theObject,
//                                        AttributeHandleValueMap theAttributes,
//                                        byte[] tag,
//                                        OrderType sentOrdering,
//                                        TransportationTypeHandle theTransport,
//                                        LogicalTime time,
//                                        OrderType receivedOrdering,
//                                        SupplementalReflectInfo reflectInfo )
//            throws FederateInternalError
//    {
//        StringBuilder builder = new StringBuilder( "Reflection for object:" );
//
//        // print the handle
//        builder.append( " handle=" + theObject );
//        // print the tag
//        builder.append( ", tag=" + new String(tag) );
//        // print the time (if we have it) we'll get null if we are just receiving
//        // a forwarded call from the other reflect callback above
//
//
//        // print the attribute information
//        builder.append( ", attributeCount=" + theAttributes.size() );
//        builder.append( "\n" );
//        for( AttributeHandle attributeHandle : theAttributes.keySet() )
//        {
//            // print the attibute handle
//            builder.append( "\tattributeHandle=" );
//
//            // if we're dealing with Flavor, decode into the appropriate enum value
//
//            builder.append( "\n" );
//        }
//
//        log( builder.toString() );
//    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       LogicalTime time,
                                       OrderType receivedOrdering,
                                       SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        try {
            if(theObject.equals(federate.taxiInstanceHandle)) {

                HLAinteger32BE taxiAreaId = new HLA1516eInteger32BE();
                taxiAreaId.decode(theAttributes.get(federate.taxiHandle_areaId));

                HLAinteger32BE taxiId = new HLA1516eInteger32BE();
                taxiId.decode(theAttributes.get(federate.taxiHandle_taxiId));


                federate.updateTaxiValues(taxiId.getValue(), taxiAreaId.getValue());
            }
            if(theObject.equals(federate.passengerInstanceHandle)) {
                HLAinteger32BE passengerId = new HLA1516eInteger32BE();
                passengerId.decode(theAttributes.get(federate.passengerHandle_passengerId));

                HLAinteger32BE passengerOrigin = new HLA1516eInteger32BE();
                passengerOrigin.decode(theAttributes.get(federate.passengerHandle_originId));

                HLAinteger32BE passengerDestination = new HLA1516eInteger32BE();
                passengerDestination.decode(theAttributes.get(federate.passengerHandle_directionId));

                federate.updatePassengerValues(passengerId.getValue(), passengerOrigin.getValue(), passengerDestination.getValue());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void receiveInteraction( InteractionClassHandle interactionClass,
//                                    ParameterHandleValueMap theParameters,
//                                    byte[] tag,
//                                    OrderType sentOrdering,
//                                    TransportationTypeHandle theTransport,
//                                    SupplementalReceiveInfo receiveInfo )
//            throws FederateInternalError
//    {
//        // just pass it on to the other method for printing purposes
//        // passing null as the time will let the other method know it
//        // it from us, not from the RTI
//        this.receiveInteraction( interactionClass,
//                theParameters,
//                tag,
//                sentOrdering,
//                theTransport,
//                null,
//                sentOrdering,
//                receiveInfo );
//    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime time,
                                   OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo) throws FederateInternalError {

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
//        if( interactionClass.equals(federate.joinPassengerQueueHandle) )
//        {
//            builder.append( " (JoinPassengerQueue)" );
//        }
//        else if( interactionClass.equals(federate.joinTaxiQueueHandle) )
//        {
//            builder.append( " (JoinTaxiQueue)" );
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
//
//            // ????????????????????????????????????????????*****************************
////            if(parameter.equals(federate.countHandle))
////            {
////                builder.append( "\tCOUNT PARAM!" );
////                byte[] bytes = theParameters.get(federate.countHandle);
////                HLAinteger32BE count = new HLA1516eInteger32BE();
////                try {
////                    count.decode(bytes);
////                } catch (DecoderException e) {
////                    e.printStackTrace();
////                }
////                int countValue = count.getValue();
////                builder.append( "\tcount Value=" + countValue );
////                if( interactionClass.equals(federate.addProductsHandle) )
////                {
////                    Storage.getInstance().addTo(countValue);
////                }
////                else if( interactionClass.equals(federate.getProductsHandle) )
////                {
////                    Storage.getInstance().getFrom(countValue);
////                }
////
////
////            }
////            else
////            {
////                // print the parameter handle
////                builder.append( "\tparamHandle=" );
////                builder.append( parameter );
////                // print the parameter value
////                builder.append( ", paramValue=" );
////                builder.append( theParameters.get(parameter).length );
////                builder.append( " bytes" );
////                builder.append( "\n" );
////            }
//        }
//
//        log( builder.toString() );
//    }

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
