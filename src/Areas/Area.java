package Areas;

import java.util.ArrayList;
import java.util.List;


public class Area {

    public int areaId;
    public List<Double> rideTimes = new ArrayList<>();

    public Area(int areaId){
        this.areaId = areaId;
    }
}
