package com.nttdocomo.ui;

import doja.MidpFrameHost;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

/** 將 DoJa 的模態對話框接到 MIDP Form。 */
public class Dialog implements CommandListener {
    public static final int DIALOG_INFO = 0;
    public static final int DIALOG_WARNING = 1;
    public static final int DIALOG_ERROR = 2;
    public static final int DIALOG_YESNO = 3;
    public static final int DIALOG_YESNOCANCEL = 4;

    public static final int BUTTON_OK = 1;
    public static final int BUTTON_CANCEL = 2;
    public static final int BUTTON_YES = 4;
    public static final int BUTTON_NO = 8;

    private final int type;
    private final String title;
    private String text = "";
    private Font font;

    private final Object modalLock = new Object();
    private volatile boolean completed;
    private int result;
    private Form form;
    private Command okCommand;
    private Command cancelCommand;
    private Command yesCommand;
    private Command noCommand;
    private javax.microedition.lcdui.Display display;
    private Displayable previous;

    public Dialog(int type, String title) {
        this.type = type;
        this.title = title == null ? "" : title;
    }

    public void setText(String text) { this.text = text == null ? "" : text; }
    public void setFont(Font font) { this.font = font; }

    public int show() {
        if (MidpFrameHost.isEventThread()) {
            throw new UIException(UIException.ILLEGAL_STATE,
                    "Dialog.show cannot block the MIDP event thread");
        }
        display = Display.__midpDisplay();
        if (display == null) return defaultResult();

        form = new Form(title);
        form.append(text);
        addCommands(form);
        form.setCommandListener(this);

        previous = display.getCurrent();
        completed = false;
        result = 0;
        display.setCurrent(form);

        synchronized (modalLock) {
            while (!completed) {
                try {
                    modalLock.wait();
                } catch (InterruptedException interrupted) {
                    result = cancelResult();
                    completed = true;
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (previous != null) display.setCurrent(previous);
        return result;
    }

    public void commandAction(Command command, Displayable source) {
        int selected;
        if (command == yesCommand) selected = BUTTON_YES;
        else if (command == noCommand) selected = BUTTON_NO;
        else if (command == cancelCommand) selected = BUTTON_CANCEL;
        else selected = BUTTON_OK;

        synchronized (modalLock) {
            if (completed) return;
            result = selected;
            completed = true;
            modalLock.notifyAll();
        }
    }

    private void addCommands(Form target) {
        if (type == DIALOG_YESNO || type == DIALOG_YESNOCANCEL) {
            yesCommand = new Command("Yes", Command.OK, 1);
            noCommand = new Command("No", Command.CANCEL, 2);
            target.addCommand(yesCommand);
            target.addCommand(noCommand);
            if (type == DIALOG_YESNOCANCEL) {
                cancelCommand = new Command("Cancel", Command.BACK, 3);
                target.addCommand(cancelCommand);
            }
        } else {
            okCommand = new Command("OK", Command.OK, 1);
            target.addCommand(okCommand);
        }
    }

    private int defaultResult() {
        return type == DIALOG_YESNO || type == DIALOG_YESNOCANCEL ? BUTTON_NO : BUTTON_OK;
    }

    private int cancelResult() {
        return type == DIALOG_YESNOCANCEL ? BUTTON_CANCEL : defaultResult();
    }
}
