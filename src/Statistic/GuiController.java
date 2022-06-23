package Statistic;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class GuiController {

    @FXML
    private Label passNum1, passNum2, passNum3, passNum4, taxiNum1, taxiNum2, taxiNum3, taxiNum4, ridesNum, waitingNum, timeLabel, waitingMean;

    @FXML
    private Button buttonFederate;

    void handle(ActionEvent e){
        if(e.getSource() == buttonFederate) startFederate();
    }

    public void initialize(){
        buttonFederate.setOnAction(this::handle);
    }

    public void startFederate(){
        new StatisticFederate(passNum1, passNum2, passNum3, passNum4,
                taxiNum1, taxiNum2, taxiNum3, taxiNum4,
                ridesNum, waitingNum, timeLabel, waitingMean).start();
    }
}
