package com.tkporter.sendsms;

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.net.Uri;
import android.database.Cursor;
import android.os.Looper;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;


import java.util.HashMap;
import java.util.Map;

public class SendSMSObserver extends ContentObserver {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Uri uri = Uri.parse("content://sms/");

    private static final int NO_TIMEOUT = -1;
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_TYPE = "type";
    private static final String[] PROJECTION = { COLUMN_ADDRESS, COLUMN_TYPE };
    private static final int MESSAGE_TYPE_ALL = 0;
    private static final int MESSAGE_TYPE_INBOX = 1;
    private static final int MESSAGE_TYPE_SENT = 2;
    private static final int MESSAGE_TYPE_DRAFT = 3;
    private static final int MESSAGE_TYPE_OUTBOX = 4;
    private static final int MESSAGE_TYPE_FAILED = 5; //failed outgoing messages
    private static final int MESSAGE_TYPE_QUEUED = 6; //queued to send later

    private SendSMSModule module;
    private ContentResolver resolver = null;
    private ReadableArray successTypes;
    private Map<String, Integer> types;
    private boolean isAuthorizedForCallback;
    private double timeout = NO_TIMEOUT;
    private boolean callbackInvoked = false;
    private boolean timedOut = false;


    public SendSMSObserver(Context context, SendSMSModule module, ReadableMap options, double timeout) {
        super(handler);

        types = new HashMap<>();
        types.put("all", MESSAGE_TYPE_ALL);
        types.put("inbox", MESSAGE_TYPE_INBOX);
        types.put("sent", MESSAGE_TYPE_SENT);
        types.put("draft", MESSAGE_TYPE_DRAFT);
        types.put("outbox", MESSAGE_TYPE_OUTBOX);
        types.put("failed", MESSAGE_TYPE_FAILED);
        types.put("queued", MESSAGE_TYPE_QUEUED);

        this.successTypes = getSuccessTypes(options);
        this.module = module;
        this.resolver = context.getContentResolver();
        this.isAuthorizedForCallback = isAuthorizedForCallback(options);
        this.timeout = timeout;
    }

    private ReadableArray getSuccessTypes(ReadableMap options) {
        if (options.hasKey("successTypes")) {
            return options.getArray("successTypes");
        } else {
            throw new IllegalStateException("Must provide successTypes. Read react-native-sms/README.md");
        }
    }

    private boolean isAuthorizedForCallback(ReadableMap options) {
        return options.hasKey("isAuthorizedForCallback") ? options.getBoolean("isAuthorizedForCallback") : false;
    }

    private Runnable runOut = new Runnable() {
        @Override
        public void run() {
            if (!callbackInvoked) {
                timedOut = true;
                messageCancel();
            }
        }
    };


    public void start() {
        if (!this.isAuthorizedForCallback) {
            return;
        }

        if (resolver != null) {
            resolver.registerContentObserver(uri, true, this);
            if (timeout > NO_TIMEOUT) {
                handler.postDelayed(runOut, (long)timeout);
            }
        }
        else {
            throw new IllegalStateException("Current SmsSendObserver instance is invalid");
        }
    }

    public void stop() {
        if (resolver != null) {
            resolver.unregisterContentObserver(this);
        }
    }

    private void messageSuccess() {
        //System.out.println("sentMessage() called");
        //success!
        module.sendCallback(true, false, false);
        callbackInvoked = true ;
        stop();
    }

    private void messageGeneric() {
        //User has not granted READ_SMS permission
        module.sendCallback(false, false, false);
        callbackInvoked = true;
        stop();
    }

    private void messageCancel(){
        module.sendCallback(false, true, false);
        stop();
    }

    private void messageError() {
        //error!
        module.sendCallback(false, false, true);
        callbackInvoked = true ;
        stop();
    }

    @Override
    public void onChange(boolean selfChange) {

        Cursor cursor = null;

        try {
            if (!this.isAuthorizedForCallback) {
                messageGeneric();
                return;
            }

            cursor = resolver.query(uri, PROJECTION, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                final int type = cursor.getInt(cursor.getColumnIndex(COLUMN_TYPE));

                System.out.println("onChange() type: " + type);

                //loop through provided success types
                boolean wasSuccess = false;
                for (int i = 0; i < successTypes.size(); i++) {
                    if (type == types.get(successTypes.getString(i))) {
                        wasSuccess = true;
                        break;
                    }
                }

                if (wasSuccess) {
                    messageSuccess();
                } else {
                    messageError();
                }
            }
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

}
