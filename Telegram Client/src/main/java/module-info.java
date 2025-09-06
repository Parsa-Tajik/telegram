module com.telegram.telegrampromium {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    // FXML needs reflection access:
    opens com.telegram.telegrampromium.controller to javafx.fxml;
    opens com.telegram.telegrampromium.controller.cell to javafx.fxml;

    // Public API for launcher package:
    exports com.telegram.telegrampromium.app;
}
