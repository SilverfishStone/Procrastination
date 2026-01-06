package net.silverfishstone.procrastination.resources;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Images {
    //https://github.com/walteralleyz/Java-Solitaire/tree/master
    public static ImageView getTexture (String type, int number, double scale) {
        String url = Resources.getFilePath("cards", type + '/' + number + ".png");
        Image image = new Image(url);
        ImageView view = new ImageView(image);

        view.setFitHeight(92 * scale);
        view.setFitWidth((Resources.ColumnWidth.BOTH.width / 13d) * scale);

        return view;
    }
}
