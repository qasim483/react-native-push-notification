package com.dieam.reactnativepushnotification.modules;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.dieam.reactnativepushnotification.R;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;

import androidx.annotation.Nullable;

public class LKNotificationService extends Service
{
    Context context;
    @Nullable
    @Override
    public IBinder onBind ( Intent intent ) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Bundle data = null;
        if (intent != null) {
            final Notification notification = intent.getParcelableExtra ("notification");
            final int notification_id = intent.getIntExtra ("notificationID", 12);
            final String notificationPayload = intent.getStringExtra ("notificationPayload");
            Long timeoutAfter = intent.getLongExtra ("timeoutAfter", 30000);
            startForeground (notification_id, notification);
            Log.d ("TimeoutAfter", timeoutAfter + "-sec");
            Log.d ("FCMLkNotificationService", notification + "");
    
            //  AFTER 30 SECONDS WE WILL STOP THE FOREGROUND SERVICE. BECAUSE IT CAN NOT RUN FOREVER.
            Runnable runnable = new Runnable ()
            {
                public void run ()
                {
                    Handler handler = new Handler (Looper.getMainLooper ());
                    handler.post (new Runnable ()
                    {
                        public void run ()
                        {
                            // CONSTRUCT AND LOAD OUR NORMAL REACT JS CODE BUNDLE
                            final ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication ()).getReactNativeHost ().getReactInstanceManager ();
                            ReactContext context = mReactInstanceManager.getCurrentReactContext ();
                    
                            // IF IT'S CONSTRUCTED, SEND A NOTIFICATION
                            if (context != null) {
                                sendToReactNative ((ReactApplicationContext) context, "stop_notification", notificationPayload);
                            } else {
                                // Otherwise wait for construction, then send the notification
                                mReactInstanceManager.addReactInstanceEventListener (new ReactInstanceManager.ReactInstanceEventListener ()
                                {
                                    public void onReactContextInitialized (ReactContext context)
                                    {
                                        sendToReactNative ((ReactApplicationContext) context, "stop_notification", notificationPayload);
                                        mReactInstanceManager.removeReactInstanceEventListener (this);
                                    }
                                });
                                if (!mReactInstanceManager.hasStartedCreatingInitialContext ()) {
                                    // Construct it in the background
                                    mReactInstanceManager.createReactContextInBackground ();
                                }
                            }
                        }
                    });
                    stopForeground (true);
                }
            };
            new android.os.Handler ().postDelayed (runnable, timeoutAfter);
        }
        
        // handler.removeCallbacks(runnable);
        //}
        return START_STICKY;
    }
    
    private void sendToReactNative ( ReactApplicationContext context, String command, String notificationPayload )
    {
        RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery (context);
        WritableMap params = Arguments.createMap ();
        params.putString ( "command", command );
        params.putString ( "notificationPayload", notificationPayload );
        jsDelivery.sendEvent("onNewCommand", params);
        //context.getCurrentActivity ().findViewById (com.dieam.reactnativepushnotification.R.layout.notification);
    }
    
    public void setActionButton (String title, PendingIntent pendingIntent)
    {
        final ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication ()).getReactNativeHost ().getReactInstanceManager ();
        ReactContext context = mReactInstanceManager.getCurrentReactContext ();
        Button btn = context.getCurrentActivity ().findViewById (R.id.lkButton1);
        btn.setText (title);
        btn.setVisibility (View.VISIBLE);
    }
}
