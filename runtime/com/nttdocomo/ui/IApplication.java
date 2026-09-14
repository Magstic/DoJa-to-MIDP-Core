package com.nttdocomo.ui;

import javax.microedition.midlet.MIDlet;
import doja.ApplicationDescriptor;

public class IApplication {
    public static final int LAUNCHED_AS_APPLICATION = 0;
    public static final int LAUNCHED_AS_IA_APPLI = 7;
    public static final int LAUNCHED_FROM_BROWSER = 9;

    private static IApplication instance;
    private static MIDlet midlet;
    private static int launchType = LAUNCHED_AS_APPLICATION;

    public static IApplication getCurrentApp() { return instance; }
    public static void setCurrentApp(IApplication app) { instance = app; }

    public static void setMidlet(MIDlet appMidlet) {
        midlet = appMidlet;
        Display.setMidlet(appMidlet);
    }

    public void start() {}
    public void resume() {}

    public void terminate() { if (midlet != null) midlet.notifyDestroyed(); }
    public int getLaunchType() { return launchType; }

    public String getParameter(String name) {
        if ("AppParam".equals(name)) {
            try { return ApplicationDescriptor.get().getAppParam(); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    public String getSourceURL() {
        try { return ApplicationDescriptor.get().getSourceUrl(); }
        catch (Exception ignored) { return ""; }
    }

    public String[] getArgs() {
        try { return ApplicationDescriptor.get().getArgs(); }
        catch (Exception ignored) { return new String[0]; }
    }

    public void launch(int type, String[] args) {
        if (args != null && args.length > 0 && midlet != null) {
            try { midlet.platformRequest(args[0]); } catch (Exception ignored) {}
        }
    }

    public static MIDlet getMIDlet() { return midlet; }
}
