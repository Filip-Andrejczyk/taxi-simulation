package Areas;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


public class Area {

    public int areaId;
    public LinkedList<Integer> passengerQueue;
    public LinkedList<Integer> taxiQueue;

    public Area(int areaId){
        this.areaId = areaId;
    }

    public void addTaxiToQueue(int taxiId){
        taxiQueue.add(taxiId);
    }
    public void addPassengerToQueue(int passengerId){
        passengerQueue.add(passengerId);
    }
}
