module com.telegram.telegrampromium {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    opens com.telegram.telegrampromium.controller to javafx.fxml;

    exports com.telegram.telegrampromium.app;
}
