package Taxis;

public class Taxi {

    public int taxiId;
    public int areaId;
    private static int idenum = 0;
    TaxiFederate taxiFederate;

    public Taxi(int areaId, TaxiFederate taxiFederate) {
        this.taxiId = idenum++;
        this.areaId = areaId;
        this.taxiFederate = taxiFederate;
    }

    //to do federata te dwie funkcje
    public void ExecuteRide(Taxi taxi, double time, int destinationId){

    }

    public void joinTaxiQueue(int taxiId){

    }
}
