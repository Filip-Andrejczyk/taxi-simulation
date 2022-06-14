package Areas;

import java.util.LinkedList;


public class Area {

    public int areaId;
    public LinkedList<Integer> passengerQueue;
    public LinkedList<Integer> taxiQueue;

    public Area(int areaId){
        passengerQueue = new LinkedList<>();
        taxiQueue = new LinkedList<>();
        this.areaId = areaId;
    }

    public void addTaxiToQueue(int taxiId){
        taxiQueue.add(taxiId);
    }
    public void addPassengerToQueue(int passengerId){
        passengerQueue.add(passengerId);
    }
}
