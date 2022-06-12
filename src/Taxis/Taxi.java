package Taxis;

public class Taxi {

    public int taxiId;
    public int areaId;
    private static int idenum = 0;
    TaxiFederateAmbassador taxiFederate;

    public Taxi(int areaId, TaxiFederateAmbassador taxiFederate) {
        this.taxiId = idenum++;
        this.areaId = areaId;
        this.taxiFederate = taxiFederate;
    }

}
