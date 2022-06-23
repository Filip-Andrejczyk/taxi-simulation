package Taxis;

import hla.rti1516e.exceptions.RTIexception;

public class Taxi {

    public int taxiId;
    public int areaId;
    private static int idenum = 0;
    private boolean isToJoinQueue;
    private double timeToRide=0;

    public Taxi(int areaId) {
        this.taxiId = idenum++;
        this.areaId = areaId;
        isToJoinQueue = true;
    }

    public void updateAreaId(int areaId, double time) throws RTIexception {
        this.areaId = areaId;
        timeToRide=time;
        isToJoinQueue = true;
    }

    public void setIsToJoinQueue(boolean isToJoinQueue){
        this.isToJoinQueue = isToJoinQueue;
    }

    public boolean isIsToJoinQueue( double simTime){
        return isToJoinQueue && simTime>=timeToRide;
    }

}
