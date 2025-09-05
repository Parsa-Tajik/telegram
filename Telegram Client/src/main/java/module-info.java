module com.telegram.telegrampromiumdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    opens com.telegram.telegrampromiumdemo.controller to javafx.fxml;
}
