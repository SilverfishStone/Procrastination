package com.workgame.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class WorkGameApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Work Game");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        SetupScreen setup = new SetupScreen(primaryStage, null);
        primaryStage.setScene(setup.buildScene());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
