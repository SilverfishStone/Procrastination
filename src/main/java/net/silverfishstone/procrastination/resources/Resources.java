package net.silverfishstone.procrastination.resources;

import static net.silverfishstone.procrastination.resources.Resources.Dir.ASSETS;

public class Resources {
    public static String getFilePath(String dir, String file) {
        return Resources.class.getClassLoader().getResource(ASSETS.dir + '/' + dir + '/' + file).toString();
    }

    public enum ColumnWidth {
        FIRST(150d),
        MIDDLE(200d),
        BOTH(1024d);

        public final double width;

        ColumnWidth(double width) {
            this.width = width;
        }
    }
    public enum Dir {
        ROOT("./"),
        ASSETS("assets"),
        SOUNDS("sounds"),
        ICONS("icons"),
        FONTS("fonts");

        public final String dir;

        Dir(String dir) {
            this.dir = dir;
        }
    }
}
