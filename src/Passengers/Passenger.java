package Passengers;

import hla.rti1516e.exceptions.RTIexception;

public class Passenger {

    private static int idenum = 0;
    public int originId;
    public int directionId;
    public int passengerId;
    PassengerFederate passengerFederate;

    public Passenger(int originId, int directionId, PassengerFederate passengerFederate){
        this.passengerId = idenum++;
        this.originId = originId;
        this.directionId = directionId;
        this.passengerFederate = passengerFederate;
        updateInstane();
    }

    private void updateInstane(){
        try{
            passengerFederate.updateInstanceValues(this.originId, this.directionId, passengerId);
        }
        catch(RTIexception e){
            e.printStackTrace();
        }
    }

}
