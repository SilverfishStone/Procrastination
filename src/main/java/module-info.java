module net.silverfishstone.procrastination {
    requires javafx.controls;
    requires javafx.fxml;


    opens net.silverfishstone.procrastination to javafx.fxml;
    exports net.silverfishstone.procrastination;
}