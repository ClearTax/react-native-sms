package com.tkporter.sendsms;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.net.Uri;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;

public class SendSMSModule extends ReactContextBaseJavaModule  {

    private final ReactApplicationContext reactContext;
    private Callback callback = null;
    private SendSMSObserver smsObserver = null;

    public SendSMSModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "SendSMS";
    }

    public void sendCallback(Boolean completed, Boolean cancelled, Boolean error) {
        if (callback != null) {
            callback.invoke(completed, cancelled, error);
            callback = null;
        }
    }

    @ReactMethod
    public void send(ReadableMap options, final Callback callback) {
        try {
            this.callback = callback;
            if( smsObserver != null){
                smsObserver.stop();
            }

            smsObserver = new SendSMSObserver(reactContext, this, options);
            smsObserver.start();

            String body = options.hasKey("body") ? options.getString("body") : "";
            ReadableArray recipients = options.hasKey("recipients") ? options.getArray("recipients") : null;

            ReadableMap attachment = null;
            if (options.hasKey("attachment")) {
                attachment = options.getMap("attachment");
            }

            Intent sendIntent;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(reactContext);
                sendIntent = new Intent(Intent.ACTION_SEND);
                if (defaultSmsPackageName != null){
                    sendIntent.setPackage(defaultSmsPackageName);
                }
                sendIntent.setType("text/plain");
            }else {
                sendIntent = new Intent(Intent.ACTION_VIEW);
                sendIntent.setType("vnd.android-dir/mms-sms");
            }

            sendIntent.putExtra("sms_body", body);
            sendIntent.putExtra(sendIntent.EXTRA_TEXT, body);
            sendIntent.putExtra("exit_on_sent", true);

            if (attachment != null) {
                Uri attachmentUrl = Uri.parse(attachment.getString("url"));
                sendIntent.putExtra(Intent.EXTRA_STREAM, attachmentUrl);

                String type = attachment.getString("androidType");
                sendIntent.setType(type);
            }

            //if recipients specified
            if (recipients != null) {
                //Samsung for some reason uses commas and not semicolons as a delimiter
                String separator = ";";
                if(android.os.Build.MANUFACTURER.equalsIgnoreCase("Samsung")){
                    separator = ",";
                }
                String recipientString = "";
                for (int i = 0; i < recipients.size(); i++) {
                    recipientString += recipients.getString(i);
                    recipientString += separator;
                }
                sendIntent.putExtra("address", recipientString);
            }
            
            sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            reactContext.startActivity(sendIntent);
            sendCallback(true, false, false);
        } catch (Exception e) {
            //error!
            sendCallback(false, false, true);
            throw e;
        }
    }

}
