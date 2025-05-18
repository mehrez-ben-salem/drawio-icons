package m4z.app.etl.drawio;

import java.util.Set;
import java.util.logging.Logger;

public class MxLibrary {
    private static final Logger logger = Logger.getLogger(MxLibrary.class.getName());

    String name;
    Set<MxIcon> icons;

    public MxLibrary() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<MxIcon> getIcons() {
        return icons;
    }

    public void setIcons(Set<MxIcon> icons) {
        this.icons = icons;
    }


}
