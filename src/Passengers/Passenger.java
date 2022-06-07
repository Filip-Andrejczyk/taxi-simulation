package Passengers;

public class Passenger {

    private static int idenum = 0;
    public int originId;
    public int directionId;
    public int passengerId;

    public Passenger(int originId, int directionId){
        this.passengerId = idenum++;
        this.originId = originId;
        this.directionId = directionId;
    }
}
