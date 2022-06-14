package Taxis;

import hla.rti1516e.exceptions.RTIexception;

public class Taxi {

    public int taxiId;
    public int areaId;
    private static int idenum = 0;
    private boolean isToJoinQueue;

    public Taxi(int areaId) {
        this.taxiId = idenum++;
        this.areaId = areaId;
        isToJoinQueue = true;
    }

    public void updateAreaId(int areaId) throws RTIexception {
        this.areaId = areaId;
        isToJoinQueue = true;
    }

    public void setIsToJoinQueue(boolean isToJoinQueue){
        this.isToJoinQueue = isToJoinQueue;
    }

    public boolean isIsToJoinQueue(){
        return isToJoinQueue;
    }

}
