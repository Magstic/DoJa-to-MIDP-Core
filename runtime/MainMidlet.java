import javax.microedition.midlet.MIDlet;

public class MainMidlet extends MIDlet {

    private boolean started;

    public void startApp() {
        if (started) {
            com.nttdocomo.ui.IApplication app = com.nttdocomo.ui.IApplication.getCurrentApp();
            if (app != null) app.resume();
            return;
        }
        started = true;
        new Thread(new Boot(this)).start();
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }
}
