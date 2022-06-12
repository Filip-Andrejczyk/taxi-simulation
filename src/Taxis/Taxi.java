package Taxis;

import hla.rti1516e.exceptions.RTIexception;

public class Taxi {

    public int taxiId;
    public int areaId;
    private static int idenum = 0;
    TaxiFederate taxiFederate;

    public Taxi(int areaId, TaxiFederate taxiFederate) {
        this.taxiId = idenum++;
        this.areaId = areaId;
        this.taxiFederate = taxiFederate;
        updateInstane();
    }

    public void updateAreaId(int areaId) throws RTIexception {
        this.areaId = areaId;
        updateInstane();
    }

    private void updateInstane(){
        try{
            taxiFederate.updateInstanceValues(this.taxiId, this.areaId);
        }
        catch(RTIexception e){
            e.printStackTrace();
        }
    }

}
