import javax.microedition.midlet.MIDlet;
import com.nttdocomo.ui.IApplication;
import doja.ApplicationDescriptor;

public final class Boot implements Runnable {
    private final MIDlet midlet;

    public Boot(MIDlet midlet) { this.midlet = midlet; }

    public void run() {
        try {
            IApplication.setMidlet(midlet);
            ApplicationDescriptor descriptor = ApplicationDescriptor.get();
            IApplication app = (IApplication)Class.forName(descriptor.getAppClass()).newInstance();
            IApplication.setCurrentApp(app);
            com.nttdocomo.io.ConnectorProxy.preflightScratchpad();
            app.start();
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
    }
}
